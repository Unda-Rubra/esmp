package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.require;

import org.jooq.DSLContext;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.api.GovernanceQueries;
import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.decay.*;
import xdu.undarubra.esmp.civilization.decay.DecayService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.*;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class JooqDecayRepository implements DecayService.Repository {
    public static final UUID SYSTEM = new UUID(0, 0);
    private final UnitOfWork db;
    private final GameClock clock;
    private final Policy policy;
    private final JooqLocalActivity local;
    private final double halfLifeTicks;
    private final String context;

    public JooqDecayRepository(
            UnitOfWork db,
            GameClock clock,
            ActivityPolicy activity,
            LocalActivityPolicy localPolicy,
            Policy policy) {
        this.db = db;
        this.clock = clock;
        this.policy = policy;
        this.local = new JooqLocalActivity(activity, localPolicy);
        halfLifeTicks = activity.halfLifeDays() * GameClock.TICKS_PER_DAY;
        context = "decay-v1;" + policy + ";" + localPolicy + ";halfLife=" + halfLifeTicks;
    }

    static void seed(DSLContext c, UUID id, long tick) {
        var s = DECAY_STATE;
        c.insertInto(s)
                .set(s.BUILDING_ID, id)
                .set(s.NEXT_CHECK_TICK, tick)
                .set(s.NEXT_OPERATION_TICK, tick)
                .set(s.OBSERVED_TICK, tick)
                .set(s.OBSERVED_SUPPORT, 0.0)
                .set(s.ACTIVITY_REVISION, 0L)
                .set(s.CURSOR, 0L)
                .set(s.CONDITION, "INTACT")
                .set(s.POLICY_CONTEXT, "")
                .onConflict(s.BUILDING_ID)
                .doNothing()
                .execute();
    }

    static void rebase(DSLContext c, UUID id, BuildingBaseline baseline, long tick) {
        seed(c, id, tick);
        var o = DECAY_OPERATIONS;
        c.update(o)
                .set(o.STATE, "CANCELLED")
                .set(o.COMPLETED_TICK, tick)
                .set(o.DETAIL, "Building baseline changed")
                .where(o.BUILDING_ID.eq(id).and(o.STATE.eq("PREPARED")))
                .execute();
        var d = DECAY_DAMAGE;
        for (var row : c.selectFrom(d).where(d.BUILDING_ID.eq(id)).fetch()) {
            var p =
                    new Position(
                            Math.toIntExact(row.get(d.X)),
                            Math.toIntExact(row.get(d.Y)),
                            Math.toIntExact(row.get(d.Z)));
            if (!baseline.bounds().contains(p)
                    || !baseline.at(p).data().equals(row.get(d.BASELINE_STATE)))
                c.deleteFrom(d)
                        .where(
                                d.BUILDING_ID
                                        .eq(id)
                                        .and(d.X.eq((long) p.x()))
                                        .and(d.Y.eq((long) p.y()))
                                        .and(d.Z.eq((long) p.z())))
                        .execute();
        }
        if (!c.fetchExists(c.selectOne().from(d).where(d.BUILDING_ID.eq(id)))) {
            String previous =
                    c.select(DECAY_STATE.CONDITION)
                            .from(DECAY_STATE)
                            .where(DECAY_STATE.BUILDING_ID.eq(id))
                            .fetchSingle(DECAY_STATE.CONDITION);
            c.update(DECAY_STATE)
                    .set(DECAY_STATE.CONDITION, Condition.INTACT.name())
                    .where(DECAY_STATE.BUILDING_ID.eq(id))
                    .execute();
            if (!previous.equals(Condition.INTACT.name()))
                event(
                        c,
                        UUID.randomUUID(),
                        JooqBuildingRepository.current(c, id).settlement(),
                        "BUILDING_CONDITION",
                        "building=" + id + ";condition=INTACT;cause=player-design",
                        tick);
            JooqRepairRepository.condition(c, id, Condition.INTACT, tick);
        }
        c.update(DECAY_STATE)
                .set(DECAY_STATE.NEXT_CHECK_TICK, tick)
                .where(DECAY_STATE.BUILDING_ID.eq(id))
                .execute();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    for (var id :
                            c.select(BUILDINGS.BUILDING_ID)
                                    .from(BUILDINGS)
                                    .where(BUILDINGS.STATUS.eq("ACTIVE"))
                                    .fetch(BUILDINGS.BUILDING_ID)) seed(c, id, clock.ticks());
                    return null;
                });
    }

    @Override
    public CompletableFuture<List<UUID>> due() {
        return db.transact(
                tx ->
                        DatabaseAccess.context(tx)
                                .select(DECAY_STATE.BUILDING_ID)
                                .from(DECAY_STATE)
                                .join(BUILDINGS)
                                .on(BUILDINGS.BUILDING_ID.eq(DECAY_STATE.BUILDING_ID))
                                .where(
                                        BUILDINGS
                                                .STATUS
                                                .eq("ACTIVE")
                                                .and(DECAY_STATE.NEXT_CHECK_TICK.le(clock.ticks())))
                                .orderBy(DECAY_STATE.NEXT_CHECK_TICK, DECAY_STATE.BUILDING_ID)
                                .limit(8)
                                .fetch(DECAY_STATE.BUILDING_ID));
    }

    @Override
    public CompletableFuture<State> status(UUID id) {
        return db.transact(tx -> assess(tx, id, false, clock.ticks()));
    }

    State refresh(UnitOfWork.Transaction transaction, UUID building) throws SQLException {
        return assess(transaction, building, true, clock.ticks());
    }

    State projection(UnitOfWork.Transaction transaction, UUID building) throws SQLException {
        return assess(transaction, building, false, clock.ticks());
    }

    private State assess(UnitOfWork.Transaction tx, UUID id, boolean write, long now)
            throws SQLException {
        var c = DatabaseAccess.context(tx);
        var v = JooqBuildingRepository.current(c, id);
        var baseline = JooqBuildingRepository.baseline(c, id, v.revision());
        var t = DECAY_STATE;
        var row = c.selectFrom(t).where(t.BUILDING_ID.eq(id)).fetchOne();
        require(row != null, "Decay state not initialized");
        double support =
                v.retired()
                        ? 0
                        : local.support(c, v.settlement(), v.world(), v.bounds().footprint(), now);
        Long since = row.get(t.INACTIVE_SINCE_TICK);
        long previousTick = row.get(t.OBSERVED_TICK);
        double previousSupport = row.get(t.OBSERVED_SUPPORT);
        boolean policyChanged = !context.equals(row.get(t.POLICY_CONTEXT));
        if (policyChanged) {
            since = null;
            previousTick = now;
            previousSupport = support;
        }
        if (support >= policy.recoveryThreshold()) since = null;
        else if (support < policy.inactiveThreshold() && since == null) {
            long crossing = previousTick;
            if (previousSupport >= policy.inactiveThreshold()) {
                double offset =
                        halfLifeTicks
                                * Math.log(previousSupport / policy.inactiveThreshold())
                                / Math.log(2);
                crossing =
                        offset > Long.MAX_VALUE - previousTick
                                ? now
                                : Math.addExact(
                                        previousTick, Math.max(0, (long) Math.floor(offset) + 1));
            }
            since = Math.min(now, crossing);
        }
        boolean eligible =
                !v.retired()
                        && since != null
                        && now - since >= policy.graceTicks()
                        && !JooqRepairRepository.protects(c, id);
        long next;
        if (eligible)
            next =
                    Math.max(
                            Math.addExact(now, policy.checkTicks()),
                            row.get(t.NEXT_OPERATION_TICK));
        else if (since != null)
            next = Math.max(Math.addExact(now, 1), Math.addExact(since, policy.graceTicks()));
        else {
            double delta =
                    support > policy.inactiveThreshold()
                            ? halfLifeTicks
                                    * Math.log(support / policy.inactiveThreshold())
                                    / Math.log(2)
                            : policy.checkTicks();
            next =
                    Math.addExact(
                            now,
                            Math.max(
                                    policy.checkTicks(),
                                    (long)
                                            Math.min(
                                                    delta,
                                                    Long.MAX_VALUE - now - policy.checkTicks())));
        }
        int damaged = c.fetchCount(DECAY_DAMAGE, DECAY_DAMAGE.BUILDING_ID.eq(id));
        int lost =
                c.fetchCount(
                        DECAY_DAMAGE,
                        DECAY_DAMAGE.BUILDING_ID.eq(id).and(DECAY_DAMAGE.LOST.eq(1L)));
        long occupied = 0;
        for (int i = 0; i < baseline.cells().size(); i++)
            if (!baseline.cells().get(i).air()
                    && v.permitsAutomaticMutation(
                            baseline.bounds().position(i), baseline.cells().get(i))) occupied++;
        Condition condition =
                damaged == 0
                        ? Condition.INTACT
                        : lost > 0
                                        && occupied > 0
                                        && lost >= Math.ceil(occupied * policy.ruinedFraction())
                                ? Condition.RUINED
                                : Condition.DAMAGED;
        if (write) {
            c.update(t)
                    .set(t.INACTIVE_SINCE_TICK, since)
                    .set(t.OBSERVED_TICK, now)
                    .set(t.OBSERVED_SUPPORT, support)
                    .set(t.NEXT_CHECK_TICK, next)
                    .set(t.POLICY_CONTEXT, context)
                    .set(t.CONDITION, condition.name())
                    .where(t.BUILDING_ID.eq(id))
                    .execute();
            JooqRepairRepository.condition(c, id, condition, now);
            if (!condition.name().equals(row.get(t.CONDITION)))
                event(
                        c,
                        UUID.randomUUID(),
                        v.settlement(),
                        "BUILDING_CONDITION",
                        "building="
                                + id
                                + ";condition="
                                + condition
                                + ";damaged="
                                + damaged
                                + ";lost="
                                + lost,
                        now);
        }
        return new State(
                id,
                v.settlement(),
                v.world(),
                support,
                since,
                next,
                row.get(t.ACTIVITY_REVISION),
                condition,
                damaged,
                lost,
                eligible);
    }

    /** Called in the same transaction immediately after an admitted local credit. */
    void contribution(UnitOfWork.Transaction tx, UUID settlement, int x, int z, long tick)
            throws SQLException {
        var c = DatabaseAccess.context(tx);
        for (var row :
                c.selectFrom(BUILDINGS)
                        .where(
                                BUILDINGS
                                        .SETTLEMENT_ID
                                        .eq(settlement)
                                        .and(BUILDINGS.STATUS.eq("ACTIVE")))
                        .fetch()) {
            UUID id = row.get(BUILDINGS.BUILDING_ID);
            int minX = Math.floorDiv(Math.toIntExact(row.get(BUILDINGS.MIN_X)), 16),
                    maxX = Math.floorDiv(Math.toIntExact(row.get(BUILDINGS.MAX_X)), 16),
                    minZ = Math.floorDiv(Math.toIntExact(row.get(BUILDINGS.MIN_Z)), 16),
                    maxZ = Math.floorDiv(Math.toIntExact(row.get(BUILDINGS.MAX_Z)), 16);
            long dx = x < minX ? (long) minX - x : x > maxX ? (long) x - maxX : 0,
                    dz = z < minZ ? (long) minZ - z : z > maxZ ? (long) z - maxZ : 0;
            if (dx + dz > 1) continue;
            seed(c, id, tick);
            c.update(DECAY_STATE)
                    .set(DECAY_STATE.ACTIVITY_REVISION, DECAY_STATE.ACTIVITY_REVISION.add(1L))
                    .where(DECAY_STATE.BUILDING_ID.eq(id))
                    .execute();
            assess(tx, id, true, tick);
        }
    }

    @Override
    public CompletableFuture<Work> work(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var state = assess(tx, id, true, clock.ticks());
                    clock.checkpoint(tx);
                    var o = DECAY_OPERATIONS;
                    var pending =
                            c.selectFrom(o)
                                    .where(o.BUILDING_ID.eq(id).and(o.STATE.eq("PREPARED")))
                                    .orderBy(o.SEQUENCE)
                                    .fetchOne();
                    if (pending != null) return new Work(state, null, operation(pending));
                    var row =
                            c.selectFrom(DECAY_STATE)
                                    .where(DECAY_STATE.BUILDING_ID.eq(id))
                                    .fetchSingle();
                    if (!state.eligible()
                            || !clock.advancing()
                            || clock.ticks() < row.get(DECAY_STATE.NEXT_OPERATION_TICK))
                        return new Work(state, null, null);
                    var v = JooqBuildingRepository.current(c, id);
                    var base = JooqBuildingRepository.baseline(c, id, v.revision());
                    var s = JooqSettlementRepository.load(c, v.settlement());
                    if (s == null) return new Work(state, null, null);
                    long security = security(c, s.id()), cursor = row.get(DECAY_STATE.CURSOR);
                    Kind kind = Kind.values()[Math.floorMod(cursor, 3)];
                    return new Work(
                            state,
                            new Offer(
                                    v,
                                    base,
                                    new BuildingService.Stamp(
                                            v.world(),
                                            s.ownershipRevision(),
                                            s.chunkRevision(),
                                            security),
                                    s.owner(),
                                    state.activityRevision(),
                                    cursor,
                                    kind,
                                    DecayPlan.candidates(v, base, kind, cursor),
                                    expected(c, id)),
                            null);
                });
    }

    private static Map<Position, String> expected(DSLContext c, UUID id) {
        var result = new HashMap<Position, String>();
        for (var r : c.selectFrom(DECAY_DAMAGE).where(DECAY_DAMAGE.BUILDING_ID.eq(id)).fetch())
            result.put(
                    new Position(
                            Math.toIntExact(r.get(DECAY_DAMAGE.X)),
                            Math.toIntExact(r.get(DECAY_DAMAGE.Y)),
                            Math.toIntExact(r.get(DECAY_DAMAGE.Z))),
                    r.get(DECAY_DAMAGE.CURRENT_STATE));
        return result;
    }

    @Override
    public CompletableFuture<Operation> prepare(Offer offer, List<Change> changes) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    UUID id = offer.building().id();
                    var v = JooqBuildingRepository.current(c, id);
                    var state = assess(tx, id, true, clock.ticks());
                    var row =
                            c.selectFrom(DECAY_STATE)
                                    .where(DECAY_STATE.BUILDING_ID.eq(id))
                                    .fetchSingle();
                    require(
                            clock.advancing()
                                    && state.eligible()
                                    && clock.ticks() >= row.get(DECAY_STATE.NEXT_OPERATION_TICK),
                            "Building is not eligible for Decay");
                    require(
                            !v.retired()
                                    && v.revision() == offer.building().revision()
                                    && state.activityRevision() == offer.activityRevision()
                                    && row.get(DECAY_STATE.CURSOR) == offer.cursor(),
                            "Decay planning context changed");
                    require(
                            authority(tx, v, offer.stamp(), offer.owner(), changes),
                            "Decay owner or region authority changed");
                    validate(offer, changes);
                    require(
                            !c.fetchExists(
                                    c.selectOne()
                                            .from(DECAY_OPERATIONS)
                                            .where(
                                                    DECAY_OPERATIONS
                                                            .BUILDING_ID
                                                            .eq(id)
                                                            .and(
                                                                    DECAY_OPERATIONS.STATE.eq(
                                                                            "PREPARED")))),
                            "A Decay intent is already pending");
                    var o = DECAY_OPERATIONS;
                    UUID operation = UUID.randomUUID();
                    Position first = changes.getFirst().position();
                    c.insertInto(o)
                            .set(o.OPERATION_ID, operation)
                            .set(o.BUILDING_ID, id)
                            .set(o.SETTLEMENT_ID, v.settlement())
                            .set(o.WORLD_INSTANCE_ID, v.world())
                            .set(o.BASELINE_REVISION, v.revision())
                            .set(o.OWNER_UUID, offer.owner())
                            .set(o.OWNERSHIP_REVISION, offer.stamp().ownership())
                            .set(o.CHUNK_REVISION, offer.stamp().claims())
                            .set(o.SECURITY_REVISION, offer.stamp().security())
                            .set(o.ACTIVITY_REVISION, offer.activityRevision())
                            .set(o.POLICY_CONTEXT, context)
                            .set(o.KIND, offer.kind().name())
                            .set(o.STATE, "PREPARED")
                            .set(o.CHUNK_X, (long) (first.x() >> 4))
                            .set(o.CHUNK_Z, (long) (first.z() >> 4))
                            .set(o.PREPARED_TICK, clock.ticks())
                            .set(o.PAYLOAD, DecayPlan.encode(changes))
                            .set(o.DETAIL, "")
                            .execute();
                    long next = Math.addExact(clock.ticks(), policy.intervalTicks());
                    c.update(DECAY_STATE)
                            .set(DECAY_STATE.CURSOR, Math.addExact(offer.cursor(), 1))
                            .set(DECAY_STATE.NEXT_OPERATION_TICK, next)
                            .set(DECAY_STATE.NEXT_CHECK_TICK, next)
                            .where(DECAY_STATE.BUILDING_ID.eq(id))
                            .execute();
                    clock.checkpoint(tx);
                    return operation(
                            c.selectFrom(o).where(o.OPERATION_ID.eq(operation)).fetchSingle());
                });
    }

    private void validate(Offer offer, List<Change> changes) {
        var unique = new HashMap<Position, Change>();
        var first = changes.getFirst().position();
        for (var change : changes) {
            var p = change.position();
            require(
                    offer.baseline().bounds().contains(p)
                            && offer.building().permitsAutomaticMutation(p, offer.baseline().at(p)),
                    "Decay target is outside mutable Building design");
            require(
                    (p.x() >> 4) == (first.x() >> 4)
                            && (p.z() >> 4) == (first.z() >> 4)
                            && unique.put(p, change) == null,
                    "Decay batch must be disjoint and inside one chunk");
            require(
                    change.expected()
                            .equals(
                                    offer.expected()
                                            .getOrDefault(p, offer.baseline().at(p).data())),
                    "Unattributed world edit conflicts with Decay");
        }
        if (offer.kind() == Kind.WEATHER) {
            require(
                    changes.size() == 1
                            && offer.candidates().contains(first)
                            && Objects.equals(
                                    DecayPlan.WEATHER.get(changes.getFirst().expected()),
                                    changes.getFirst().target()),
                    "Invalid WEATHER substitution");
            return;
        }
        int sources = 0;
        var positions = new HashSet<Position>();
        for (var change : changes)
            if (change.target().equals("minecraft:air")) {
                sources++;
                var p = change.position();
                require(
                        offer.candidates().contains(p)
                                && DecayPlan.STRUCTURAL.contains(change.expected()),
                        "Invalid collapse source");
                var destination = unique.get(new Position(p.x(), p.y() - 1, p.z()));
                require(
                        destination != null
                                && air(destination.expected())
                                && destination.target().equals(change.expected()),
                        "Collapse must move one block vertically into air");
                positions.add(p);
            }
        require(
                sources * 2 == changes.size()
                        && sources > 0
                        && sources <= (offer.kind() == Kind.DROP ? 1 : 4),
                "Invalid collapse patch size");
        var todo = new ArrayDeque<Position>();
        todo.add(positions.iterator().next());
        var visited = new HashSet<Position>();
        while (!todo.isEmpty()) {
            var p = todo.removeFirst();
            if (!visited.add(p)) continue;
            for (var n : positions)
                if (n.y() == p.y()
                        && Math.abs((long) n.x() - p.x()) + Math.abs((long) n.z() - p.z()) == 1)
                    todo.add(n);
        }
        require(visited.size() == sources, "CAVE_IN must be a connected horizontal patch");
    }

    private boolean authority(
            UnitOfWork.Transaction tx,
            BuildingService.View v,
            BuildingService.Stamp stamp,
            UUID owner,
            List<Change> changes)
            throws SQLException {
        var c = DatabaseAccess.context(tx);
        var s = JooqSettlementRepository.load(c, v.settlement());
        if (s == null
                || !s.owner().equals(owner)
                || !s.worldInstanceId().equals(stamp.world())
                || s.ownershipRevision() != stamp.ownership()
                || s.chunkRevision() != stamp.claims()
                || security(c, s.id()) != stamp.security()
                || GovernanceQueries.banned(tx, s.id(), owner)) return false;
        for (var change : changes) {
            var p = change.position();
            if (!s.chunks().contains(new ChunkPosition(p.x() >> 4, p.z() >> 4))) return false;
            var parcel = GovernanceQueries.at(tx, s.id(), ParcelCell.atBlock(p.x(), p.y(), p.z()));
            if (parcel != null && !parcel.allows(owner, s.role(owner), ParcelAction.BUILD))
                return false;
        }
        return true;
    }

    private void validateStored(DSLContext c, BuildingService.View view, Operation operation) {
        var baseline = JooqBuildingRepository.baseline(c, view.id(), view.revision());
        var exterior = baseline.geometry().exterior();
        var sources = new ArrayList<Position>();
        var expected = new HashMap<Position, String>();
        for (var change : operation.changes()) {
            require(
                    baseline.bounds().contains(change.position()),
                    "Stored Decay intent is outside its baseline");
            expected.put(change.position(), change.expected());
            if (operation.kind() == Kind.WEATHER || change.target().equals("minecraft:air")) {
                var original = baseline.at(change.position());
                require(
                        exterior.contains(baseline.bounds().index(change.position()))
                                && (operation.kind() == Kind.WEATHER
                                        ? DecayPlan.WEATHER.containsKey(original.data())
                                        : DecayPlan.STRUCTURAL.contains(original.data())),
                        "Stored Decay intent is outside its material profile");
                sources.add(change.position());
            }
        }
        validate(
                new Offer(
                        view,
                        baseline,
                        new BuildingService.Stamp(
                                view.world(),
                                operation.ownershipRevision(),
                                operation.claimRevision(),
                                operation.securityRevision()),
                        operation.owner(),
                        operation.activityRevision(),
                        0,
                        operation.kind(),
                        sources,
                        expected),
                operation.changes());
    }

    @Override
    public CompletableFuture<Permit> authorize(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var o = required(c, id);
                    var v = JooqBuildingRepository.current(c, o.building());
                    boolean current =
                            !v.retired()
                                    && v.revision() == o.baselineRevision()
                                    && v.world().equals(o.world());
                    if (!current)
                        return new Permit(o, false, "Building revision or lifecycle changed");
                    validateStored(c, v, o);
                    var state = assess(tx, o.building(), true, clock.ticks());
                    boolean valid =
                            (o.state().equals("PREPARED") || o.state().equals("APPLIED"))
                                    && context.equals(o.policy())
                                    && clock.advancing()
                                    && state.eligible()
                                    && state.activityRevision() == o.activityRevision()
                                    && authority(
                                            tx,
                                            v,
                                            new BuildingService.Stamp(
                                                    o.world(),
                                                    o.ownershipRevision(),
                                                    o.claimRevision(),
                                                    o.securityRevision()),
                                            o.owner(),
                                            o.changes());
                    return new Permit(o, valid, valid ? "" : "Activity or authority changed");
                });
    }

    @Override
    public CompletableFuture<List<Operation>> chunk(UUID world, int x, int z, long after) {
        return db.transact(
                tx -> {
                    var o = DECAY_OPERATIONS;
                    return DatabaseAccess.context(tx)
                            .selectFrom(o)
                            .where(
                                    o.WORLD_INSTANCE_ID
                                            .eq(world)
                                            .and(o.CHUNK_X.eq((long) x))
                                            .and(o.CHUNK_Z.eq((long) z))
                                            .and(o.SEQUENCE.gt(after))
                                            .and(o.STATE.in("PREPARED", "APPLIED")))
                            .orderBy(o.SEQUENCE)
                            .limit(32)
                            .fetch(JooqDecayRepository::operation);
                });
    }

    @Override
    public CompletableFuture<SettlementChange> finish(
            UUID id, Outcome outcome, Map<Position, BuildingCell> actual) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var op = required(c, id);
                    if (!op.state().equals("PREPARED") && !op.state().equals("APPLIED"))
                        return change(c, op.settlement(), "Decay operation already resolved.");
                    var v = JooqBuildingRepository.current(c, op.building());
                    long now = clock.ticks();
                    boolean same = !v.retired() && v.revision() == op.baselineRevision();
                    int previousDamage =
                            c.fetchCount(DECAY_DAMAGE, DECAY_DAMAGE.BUILDING_ID.eq(v.id()));
                    if (!same) {
                        if (op.state().equals("APPLIED") && !v.retired()) {
                            var base = JooqBuildingRepository.baseline(c, v.id(), v.revision());
                            for (var entry : actual.entrySet())
                                if (base.bounds().contains(entry.getKey())
                                        && base.at(entry.getKey())
                                                .data()
                                                .equals(entry.getValue().data())) {
                                    var p = entry.getKey();
                                    c.deleteFrom(DECAY_DAMAGE)
                                            .where(
                                                    DECAY_DAMAGE
                                                            .BUILDING_ID
                                                            .eq(v.id())
                                                            .and(
                                                                    DECAY_DAMAGE.OPERATION_ID.eq(
                                                                            op.id()))
                                                            .and(DECAY_DAMAGE.X.eq((long) p.x()))
                                                            .and(DECAY_DAMAGE.Y.eq((long) p.y()))
                                                            .and(DECAY_DAMAGE.Z.eq((long) p.z())))
                                            .execute();
                                }
                            assess(tx, v.id(), true, now);
                        } else outcomeHolder(c, op, Outcome.CANCELLED, "Baseline superseded", now);
                    } else {
                        var base = JooqBuildingRepository.baseline(c, v.id(), v.revision());
                        var d = DECAY_DAMAGE;
                        for (var change : op.changes()) {
                            var cell = actual.get(change.position());
                            if (cell == null
                                    || (op.state().equals("PREPARED")
                                            && outcome != Outcome.APPLIED
                                            && !cell.data().equals(change.target()))) continue;
                            var p = change.position();
                            var original = base.at(p);
                            var key =
                                    d.BUILDING_ID
                                            .eq(v.id())
                                            .and(d.X.eq((long) p.x()))
                                            .and(d.Y.eq((long) p.y()))
                                            .and(d.Z.eq((long) p.z()));
                            if (original.data().equals(cell.data()))
                                c.deleteFrom(d).where(key).execute();
                            else
                                c.insertInto(d)
                                        .set(d.BUILDING_ID, v.id())
                                        .set(d.X, (long) p.x())
                                        .set(d.Y, (long) p.y())
                                        .set(d.Z, (long) p.z())
                                        .set(d.BASELINE_STATE, original.data())
                                        .set(d.CURRENT_STATE, cell.data())
                                        .set(d.LOST, !original.air() && cell.air() ? 1L : 0L)
                                        .set(d.OPERATION_ID, id)
                                        .onConflict(d.BUILDING_ID, d.X, d.Y, d.Z)
                                        .doUpdate()
                                        .set(d.BASELINE_STATE, original.data())
                                        .set(d.CURRENT_STATE, cell.data())
                                        .set(d.LOST, !original.air() && cell.air() ? 1L : 0L)
                                        .set(d.OPERATION_ID, id)
                                        .execute();
                        }
                        outcomeHolder(c, op, outcome, outcome.name(), now);
                        assess(tx, v.id(), true, now);
                    }
                    if (same
                            && previousDamage == 0
                            && c.fetchCount(DECAY_DAMAGE, DECAY_DAMAGE.BUILDING_ID.eq(v.id())) > 0)
                        event(
                                c,
                                UUID.nameUUIDFromBytes(
                                        ("decay-start:" + id)
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                op.settlement(),
                                "DECAY_STARTED",
                                "building=" + v.id() + ";operation=" + id,
                                now);
                    if (op.state().equals("PREPARED"))
                        event(
                                c,
                                id,
                                op.settlement(),
                                "DECAY_" + outcome,
                                "principal=DECAY;building="
                                        + op.building()
                                        + ";kind="
                                        + op.kind()
                                        + ";cells="
                                        + op.changes().size(),
                                now);
                    else if (op.state().equals("APPLIED") && outcome != Outcome.APPLIED)
                        event(
                                c,
                                UUID.nameUUIDFromBytes(
                                        ("decay-reconcile:" + id + ":" + outcome)
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                op.settlement(),
                                "DECAY_RECONCILED",
                                "operation=" + id + ";outcome=" + outcome,
                                now);
                    clock.checkpoint(tx);
                    return change(c, op.settlement(), "Decay outcome recorded.");
                });
    }

    private static void outcomeHolder(
            DSLContext c, Operation op, Outcome outcome, String detail, long tick) {
        c.update(DECAY_OPERATIONS)
                .set(DECAY_OPERATIONS.STATE, outcome.name())
                .set(DECAY_OPERATIONS.DETAIL, detail)
                .set(DECAY_OPERATIONS.COMPLETED_TICK, tick)
                .where(DECAY_OPERATIONS.OPERATION_ID.eq(op.id()))
                .execute();
    }

    @Override
    public CompletableFuture<SettlementChange> defer(UUID id, boolean rotate) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var v = JooqBuildingRepository.current(c, id);
                    long next =
                            Math.addExact(
                                    clock.ticks(),
                                    rotate ? policy.intervalTicks() : policy.checkTicks());
                    var update = c.update(DECAY_STATE).set(DECAY_STATE.NEXT_CHECK_TICK, next);
                    if (rotate) update.set(DECAY_STATE.CURSOR, DECAY_STATE.CURSOR.add(1L));
                    update.where(DECAY_STATE.BUILDING_ID.eq(id)).execute();
                    clock.checkpoint(tx);
                    return change(
                            c, v.settlement(), "Decay waits for an eligible loaded candidate.");
                });
    }

    private static boolean air(String data) {
        return Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").contains(data);
    }

    private static long security(DSLContext c, UUID id) {
        return c.select(SETTLEMENT_DETAILS.SECURITY_REVISION)
                .from(SETTLEMENT_DETAILS)
                .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                .fetchSingle(SETTLEMENT_DETAILS.SECURITY_REVISION);
    }

    private static Operation required(DSLContext c, UUID id) {
        var r =
                c.selectFrom(DECAY_OPERATIONS)
                        .where(DECAY_OPERATIONS.OPERATION_ID.eq(id))
                        .fetchOne();
        require(r != null, "Decay operation not found");
        return operation(r);
    }

    private static Operation operation(Record r) {
        var o = DECAY_OPERATIONS;
        return new Operation(
                r.get(o.SEQUENCE),
                r.get(o.OPERATION_ID),
                r.get(o.BUILDING_ID),
                r.get(o.SETTLEMENT_ID),
                r.get(o.WORLD_INSTANCE_ID),
                r.get(o.BASELINE_REVISION),
                r.get(o.OWNER_UUID),
                r.get(o.OWNERSHIP_REVISION),
                r.get(o.CHUNK_REVISION),
                r.get(o.SECURITY_REVISION),
                r.get(o.ACTIVITY_REVISION),
                r.get(o.POLICY_CONTEXT),
                Kind.valueOf(r.get(o.KIND)),
                r.get(o.STATE),
                Math.toIntExact(r.get(o.CHUNK_X)),
                Math.toIntExact(r.get(o.CHUNK_Z)),
                DecayPlan.decode(r.get(o.PAYLOAD)));
    }

    private static SettlementChange change(DSLContext c, UUID id, String text) throws SQLException {
        return new SettlementChange(id, JooqSettlementRepository.load(c, id), null, text);
    }

    private static void event(
            DSLContext c, UUID id, UUID settlement, String kind, String detail, long tick) {
        c.insertInto(SETTLEMENT_EVENTS)
                .set(SETTLEMENT_EVENTS.EVENT_ID, id)
                .set(SETTLEMENT_EVENTS.SETTLEMENT_ID, settlement)
                .set(SETTLEMENT_EVENTS.ACTOR_UUID, SYSTEM)
                .set(SETTLEMENT_EVENTS.TYPE, kind)
                .set(SETTLEMENT_EVENTS.DETAIL, detail)
                .set(SETTLEMENT_EVENTS.OCCURRED_AT, Instant.now().toEpochMilli())
                .set(SETTLEMENT_EVENTS.GAME_TICK, tick)
                .set(SETTLEMENT_EVENTS.DELIVERED, 0L)
                .onConflict(SETTLEMENT_EVENTS.EVENT_ID)
                .doNothing()
                .execute();
    }
}
