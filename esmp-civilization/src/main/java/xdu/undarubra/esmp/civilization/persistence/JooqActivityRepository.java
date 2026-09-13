package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.jooq.DSLContext;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.activity.ActivityContribution;
import xdu.undarubra.esmp.civilization.activity.ActivityPolicy;
import xdu.undarubra.esmp.civilization.activity.ActivityProjection;
import xdu.undarubra.esmp.civilization.activity.ActivityProjection.Value;
import xdu.undarubra.esmp.civilization.activity.ActivityRepository;
import xdu.undarubra.esmp.civilization.activity.ActivityService.PauseInterval;
import xdu.undarubra.esmp.civilization.activity.ActivityStatus;
import xdu.undarubra.esmp.civilization.activity.LocalActivityPolicy;
import xdu.undarubra.esmp.civilization.activity.LocalActivityView;
import xdu.undarubra.esmp.civilization.api.GovernanceQueries;
import xdu.undarubra.esmp.civilization.persistence.legacy.ObservationLegacyMigrations;
import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;
import xdu.undarubra.esmp.civilization.settlement.ParcelAction;
import xdu.undarubra.esmp.civilization.settlement.ParcelCell;
import xdu.undarubra.esmp.civilization.settlement.Role;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Civilization-owned SQL adapter for credited activity and read-only projections. */
public final class JooqActivityRepository implements ActivityRepository {
    private final UnitOfWork database;
    private final GameClock clock;
    private final ActivityPolicy policy;
    private final Predicate<UUID> administrator;
    private final JooqLocalActivity local;

    public JooqActivityRepository(
            UnitOfWork database,
            GameClock clock,
            ActivityPolicy policy,
            LocalActivityPolicy localPolicy,
            Predicate<UUID> administrator) {
        this.database = database;
        this.clock = clock;
        this.policy = policy;
        this.administrator = administrator;
        this.local = new JooqLocalActivity(policy, localPolicy);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    if (DatabaseAccess.legacy(transaction)) {
                        ObservationLegacyMigrations.activity(c);
                    }
                    Record row =
                            c.select(ACTIVITY_POLICY.DEFINITION)
                                    .from(ACTIVITY_POLICY)
                                    .where(ACTIVITY_POLICY.ID.eq(1L))
                                    .fetchOne();
                    if (row != null
                            && !row.get(ACTIVITY_POLICY.DEFINITION)
                                    .replace("visitorCredit=", "publicCredit=")
                                    .equals(policy.toString())) {
                        throw new SQLException(
                                "Activity policy changed: existing scores require an explicit"
                                        + " policy migration; restore prior settings");
                    }
                    c.insertInto(ACTIVITY_POLICY)
                            .columns(ACTIVITY_POLICY.ID, ACTIVITY_POLICY.DEFINITION)
                            .values(1L, policy.toString())
                            .onConflict(ACTIVITY_POLICY.ID)
                            .doUpdate()
                            .set(ACTIVITY_POLICY.DEFINITION, policy.toString())
                            .execute();
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> creditObservations(List<ActivityContribution> observations) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    if (!clock.advancing()) {
                        return null;
                    }
                    long now = clock.ticks();
                    for (ActivityContribution action : observations) {
                        credit(transaction, c, action, now);
                    }
                    clock.checkpoint(transaction);
                    return null;
                });
    }

    @Override
    public boolean isActive(Transaction transaction, UUID settlement) throws SQLException {
        DSLContext c = DatabaseAccess.context(transaction);
        long now = clock.ticks();
        return ActivityProjection.project(load(c, settlement), now, policy).inactive() == null;
    }

    @Override
    public CompletableFuture<ActivityStatus> status(UUID id) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    long now = clock.ticks();
                    Value value = ActivityProjection.project(load(c, id), now, policy);
                    return new ActivityStatus(
                            value.score(),
                            value.inactive() == null
                                    ? ActivityStatus.State.ACTIVE
                                    : ActivityStatus.State.INACTIVE,
                            value.inactive(),
                            ActivityProjection.paused(value.inactive(), now, policy),
                            value.inactive() == null
                                    ? 0
                                    : Math.max(0, policy.recoveryThreshold() - value.score()),
                            value.last(),
                            value.day() == now / GameClock.TICKS_PER_DAY ? value.credited() : 0,
                            now,
                            value.category());
                });
    }

    @Override
    public CompletableFuture<List<PauseInterval>> pauseIntervals(UUID id) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    long now = clock.ticks();
                    Value value = ActivityProjection.project(load(c, id), now, policy);
                    var result = new ArrayList<PauseInterval>();
                    for (Record row :
                            c.select(ACTIVITY_PAUSES.FROM_TICK, ACTIVITY_PAUSES.UNTIL_TICK)
                                    .from(ACTIVITY_PAUSES)
                                    .where(ACTIVITY_PAUSES.SETTLEMENT_ID.eq(id))
                                    .orderBy(ACTIVITY_PAUSES.FROM_TICK)
                                    .fetch()) {
                        result.add(
                                new PauseInterval(
                                        row.get(ACTIVITY_PAUSES.FROM_TICK),
                                        row.get(ACTIVITY_PAUSES.UNTIL_TICK)));
                    }
                    if (ActivityProjection.paused(value.inactive(), now, policy)) {
                        result.add(
                                new PauseInterval(
                                        Math.addExact(value.inactive(), policy.graceTicks()),
                                        null));
                    }
                    return List.copyOf(result);
                });
    }

    @Override
    public CompletableFuture<LocalActivityView> localActivity(UUID settlement) {
        return database.transact(
                tx -> local.view(DatabaseAccess.context(tx), settlement, clock.ticks()));
    }

    @Override
    public CompletableFuture<Double> localSupport(
            UUID settlement, UUID world, Map<ChunkPosition, Integer> footprint, long gameTick) {
        return database.transact(tx -> localSupport(tx, settlement, world, footprint, gameTick));
    }

    @Override
    public double localSupport(
            Transaction tx,
            UUID settlement,
            UUID world,
            Map<ChunkPosition, Integer> footprint,
            long gameTick)
            throws SQLException {
        return local.support(DatabaseAccess.context(tx), settlement, world, footprint, gameTick);
    }

    private void credit(Transaction transaction, DSLContext c, ActivityContribution a, long now)
            throws SQLException {
        var s = SETTLEMENTS.as("s");
        var d = SETTLEMENT_DETAILS.as("d");
        var ch = SETTLEMENT_CHUNKS.as("ch");
        var m = SETTLEMENT_MEMBERS.as("m");
        Record membership =
                c.select(s.OWNER_UUID, m.ROLE)
                        .from(s)
                        .join(d)
                        .on(
                                d.SETTLEMENT_ID
                                        .eq(s.SETTLEMENT_ID)
                                        .and(d.SECURITY_REVISION.eq(a.securityRevision())))
                        .join(ch)
                        .on(
                                ch.SETTLEMENT_ID
                                        .eq(s.SETTLEMENT_ID)
                                        .and(ch.WORLD_INSTANCE_ID.eq(a.worldInstanceId()))
                                        .and(ch.CHUNK_X.eq((long) Math.floorDiv(a.blockX(), 16)))
                                        .and(ch.CHUNK_Z.eq((long) Math.floorDiv(a.blockZ(), 16))))
                        .leftJoin(m)
                        .on(m.SETTLEMENT_ID.eq(s.SETTLEMENT_ID).and(m.PLAYER_UUID.eq(a.actor())))
                        .where(s.SETTLEMENT_ID.eq(a.settlementId()))
                        .and(s.WORLD_INSTANCE_ID.eq(a.worldInstanceId()))
                        .and(s.STATUS.eq("OWNED"))
                        .and(s.OWNERSHIP_REVISION.eq(a.ownershipRevision()))
                        .and(s.CHUNK_REVISION.eq(a.chunkRevision()))
                        .fetchOne();
        if (membership == null) return;
        Role role =
                a.actor().equals(membership.get(s.OWNER_UUID))
                        ? Role.OWNER
                        : membership.get(m.ROLE) == null
                                ? Role.PUBLIC
                                : Role.valueOf(membership.get(m.ROLE));
        if (GovernanceQueries.banned(transaction, a.settlementId(), a.actor())
                && !administrator.test(a.actor())) return;
        var parcel =
                GovernanceQueries.at(
                        transaction,
                        a.settlementId(),
                        ParcelCell.atBlock(a.blockX(), a.blockY(), a.blockZ()));
        if (parcel != null) {
            if (!parcel.allows(a.actor(), role, a.action())) return;
        } else if (role == Role.PUBLIC && !(a.action() == ParcelAction.INTERACT && a.safePublic()))
            return;
        boolean publicActor = role == Role.PUBLIC;
        long day = now / GameClock.TICKS_PER_DAY;
        double actorCredit = 0;
        Long build = null, interaction = null;
        Record actor =
                c.select(
                                ACTIVITY_ACTORS.DAY,
                                ACTIVITY_ACTORS.CREDITED,
                                ACTIVITY_ACTORS.BUILD_TICK,
                                ACTIVITY_ACTORS.INTERACTION_TICK)
                        .from(ACTIVITY_ACTORS)
                        .where(ACTIVITY_ACTORS.SETTLEMENT_ID.eq(a.settlementId()))
                        .and(ACTIVITY_ACTORS.ACTOR_UUID.eq(a.actor()))
                        .fetchOne();
        if (actor != null) {
            actorCredit =
                    actor.get(ACTIVITY_ACTORS.DAY) == day ? actor.get(ACTIVITY_ACTORS.CREDITED) : 0;
            build = actor.get(ACTIVITY_ACTORS.BUILD_TICK);
            interaction = actor.get(ACTIVITY_ACTORS.INTERACTION_TICK);
        }
        Long last = a.category() == ActivityContribution.Category.BUILD ? build : interaction;
        if (last != null && now - last < policy.actionCooldownTicks()) {
            return;
        }
        Value value = ActivityProjection.project(load(c, a.settlementId()), now, policy);
        double daily = value.day() == day ? value.credited() : 0;
        double amount =
                publicActor
                        ? policy.publicCredit()
                        : a.category() == ActivityContribution.Category.BUILD
                                ? policy.buildCredit()
                                : policy.interactionCredit();
        double actorCap = publicActor ? policy.playerDailyCap() / 20 : policy.playerDailyCap();
        amount =
                Math.min(
                        amount,
                        Math.min(
                                actorCap - actorCredit,
                                Math.min(
                                        policy.settlementDailyCap() - daily,
                                        policy.maxScore() - value.score())));
        if (amount <= 0) {
            return;
        }
        // Count only the amount that passed authorization, cooldowns and all caps.
        local.credit(c, a, amount, now);
        c.insertInto(SETTLEMENT_ACTIVITY_LIFETIME)
                .set(SETTLEMENT_ACTIVITY_LIFETIME.SETTLEMENT_ID, a.settlementId())
                .set(SETTLEMENT_ACTIVITY_LIFETIME.CREDITED, amount)
                .onConflict(SETTLEMENT_ACTIVITY_LIFETIME.SETTLEMENT_ID)
                .doUpdate()
                .set(
                        SETTLEMENT_ACTIVITY_LIFETIME.CREDITED,
                        SETTLEMENT_ACTIVITY_LIFETIME.CREDITED.add(amount))
                .execute();
        double nextScore = value.score() + amount;
        Long inactive = value.inactive();
        if (inactive != null && nextScore >= policy.recoveryThreshold()) {
            if (ActivityProjection.paused(inactive, now, policy)) {
                c.insertInto(ACTIVITY_PAUSES)
                        .columns(
                                ACTIVITY_PAUSES.SETTLEMENT_ID,
                                ACTIVITY_PAUSES.FROM_TICK,
                                ACTIVITY_PAUSES.UNTIL_TICK)
                        .values(a.settlementId(), Math.addExact(inactive, policy.graceTicks()), now)
                        .onDuplicateKeyIgnore()
                        .execute();
            }
            inactive = null;
        }
        c.insertInto(SETTLEMENT_ACTIVITY)
                .columns(
                        SETTLEMENT_ACTIVITY.SETTLEMENT_ID,
                        SETTLEMENT_ACTIVITY.SCORE,
                        SETTLEMENT_ACTIVITY.ANCHOR_TICK,
                        SETTLEMENT_ACTIVITY.INACTIVE_TICK,
                        SETTLEMENT_ACTIVITY.DAY,
                        SETTLEMENT_ACTIVITY.CREDITED,
                        SETTLEMENT_ACTIVITY.LAST_TICK,
                        SETTLEMENT_ACTIVITY.CATEGORY)
                .values(
                        a.settlementId(),
                        nextScore,
                        now,
                        inactive,
                        day,
                        daily + amount,
                        now,
                        a.category().name())
                .onConflict(SETTLEMENT_ACTIVITY.SETTLEMENT_ID)
                .doUpdate()
                .set(SETTLEMENT_ACTIVITY.SCORE, nextScore)
                .set(SETTLEMENT_ACTIVITY.ANCHOR_TICK, now)
                .set(SETTLEMENT_ACTIVITY.INACTIVE_TICK, inactive)
                .set(SETTLEMENT_ACTIVITY.DAY, day)
                .set(SETTLEMENT_ACTIVITY.CREDITED, daily + amount)
                .set(SETTLEMENT_ACTIVITY.LAST_TICK, now)
                .set(SETTLEMENT_ACTIVITY.CATEGORY, a.category().name())
                .execute();
        if (a.category() == ActivityContribution.Category.BUILD) {
            build = now;
        } else {
            interaction = now;
        }
        c.insertInto(ACTIVITY_ACTORS)
                .columns(
                        ACTIVITY_ACTORS.SETTLEMENT_ID,
                        ACTIVITY_ACTORS.ACTOR_UUID,
                        ACTIVITY_ACTORS.DAY,
                        ACTIVITY_ACTORS.CREDITED,
                        ACTIVITY_ACTORS.BUILD_TICK,
                        ACTIVITY_ACTORS.INTERACTION_TICK)
                .values(a.settlementId(), a.actor(), day, actorCredit + amount, build, interaction)
                .onConflict(ACTIVITY_ACTORS.SETTLEMENT_ID, ACTIVITY_ACTORS.ACTOR_UUID)
                .doUpdate()
                .set(ACTIVITY_ACTORS.DAY, day)
                .set(ACTIVITY_ACTORS.CREDITED, actorCredit + amount)
                .set(ACTIVITY_ACTORS.BUILD_TICK, build)
                .set(ACTIVITY_ACTORS.INTERACTION_TICK, interaction)
                .execute();
    }

    private Value load(DSLContext c, UUID id) throws SQLException {
        var s = SETTLEMENTS.as("s");
        var a = SETTLEMENT_ACTIVITY.as("a");
        Record row =
                c.select(
                                s.FOUNDED_TICK,
                                a.SETTLEMENT_ID,
                                a.SCORE,
                                a.ANCHOR_TICK,
                                a.INACTIVE_TICK,
                                a.DAY,
                                a.CREDITED,
                                a.LAST_TICK,
                                a.CATEGORY)
                        .from(s)
                        .leftJoin(a)
                        .on(a.SETTLEMENT_ID.eq(s.SETTLEMENT_ID))
                        .where(s.SETTLEMENT_ID.eq(id))
                        .and(s.STATUS.eq("OWNED"))
                        .fetchOne();
        if (row == null) {
            throw new SettlementException("Settlement is no longer owned or does not exist");
        }
        if (row.get(a.SETTLEMENT_ID) == null) {
            return new Value(
                    policy.initialScore(), row.get(s.FOUNDED_TICK), null, -1, 0, null, "none");
        }
        return new Value(
                row.get(a.SCORE),
                row.get(a.ANCHOR_TICK),
                row.get(a.INACTIVE_TICK),
                row.get(a.DAY),
                row.get(a.CREDITED),
                row.get(a.LAST_TICK),
                row.get(a.CATEGORY));
    }
}
