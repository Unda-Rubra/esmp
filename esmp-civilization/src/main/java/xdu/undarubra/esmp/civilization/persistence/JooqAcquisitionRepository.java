package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.LAND_VALUE_PROFILES;
import static xdu.undarubra.esmp.civilization.generated.Tables.PARCEL_REQUESTS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENTS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_ACQUISITIONS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_CHUNKS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_DETAILS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_EVENTS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_MEMBERS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_OFFERS;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.require;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.acquisition.Acquisition;
import xdu.undarubra.esmp.civilization.acquisition.Acquisition.State;
import xdu.undarubra.esmp.civilization.acquisition.AcquisitionPolicy;
import xdu.undarubra.esmp.civilization.acquisition.AcquisitionService;
import xdu.undarubra.esmp.civilization.settlement.Role;
import xdu.undarubra.esmp.civilization.settlement.SettlementChange;
import xdu.undarubra.esmp.civilization.settlement.SettlementView;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.GameClock;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Frozen Phase 3 acquisition persistence. The serial UnitOfWork protects the active-proposal
 * invariant. Payment, ownership, invalidations, audit and the clock checkpoint commit together. No
 * appraisal, economy SQL, certification, treasury or activity writes occur here.
 */
public final class JooqAcquisitionRepository implements AcquisitionService.Repository {
    private final UnitOfWork database;
    private final GameClock clock;
    private final AcquisitionPolicy policy;
    private final Predicate<UUID> administrators;
    private final AcquisitionService.Payment payment;

    public JooqAcquisitionRepository(
            UnitOfWork db,
            GameClock clock,
            AcquisitionPolicy policy,
            Predicate<UUID> admins,
            AcquisitionService.Payment payment) {
        this.database = Objects.requireNonNull(db);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
        this.administrators = Objects.requireNonNull(admins);
        this.payment = Objects.requireNonNull(payment);
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Transaction transaction, DSLContext context) throws Exception;
    }

    private <T> CompletableFuture<T> transact(Work<T> work) {
        return database.transact(tx -> work.run(tx, DatabaseAccess.context(tx)));
    }

    @Override
    public CompletableFuture<Acquisition> view(UUID id) {
        return transact((tx, c) -> find(c, Objects.requireNonNull(id)));
    }

    @Override
    public CompletableFuture<List<Acquisition>> history(UUID settlementId) {
        return transact(
                (tx, c) -> {
                    Objects.requireNonNull(settlementId);
                    var a = SETTLEMENT_ACQUISITIONS;
                    var result = new ArrayList<Acquisition>();
                    for (Record row :
                            c.selectFrom(a)
                                    .where(a.SETTLEMENT_ID.eq(settlementId))
                                    .orderBy(a.PROPOSED_TICK.desc(), a.ACQUISITION_ID.asc())
                                    .limit(50)
                                    .fetch()) {
                        result.add(acquisition(row));
                    }
                    return List.copyOf(result);
                });
    }

    @Override
    public CompletableFuture<SettlementChange> propose(
            UUID actor, UUID settlementId, UUID recipient) {
        return transact(
                (tx, c) -> {
                    SettlementView settlement = owned(c, Objects.requireNonNull(settlementId));
                    require(
                            actor != null && actor.equals(settlement.owner()),
                            "Only the current Owner may propose an acquisition.");
                    requireParties(tx, settlement, actor, recipient);
                    long tick = clock.ticks();
                    Quote quote = quote(c, settlement, recipient);
                    var a = SETTLEMENT_ACQUISITIONS;
                    List<Acquisition> active =
                            c.selectFrom(a)
                                    .where(
                                            a.SETTLEMENT_ID
                                                    .eq(settlementId)
                                                    .and(
                                                            a.STATE.in(
                                                                    State.PROPOSED.name(),
                                                                    State.ACCEPTED.name()))
                                                    .and(a.EXPIRES_TICK.gt(tick)))
                                    .orderBy(a.PROPOSED_TICK.asc(), a.ACQUISITION_ID.asc())
                                    .limit(2)
                                    .fetch(JooqAcquisitionRepository::acquisition);
                    require(
                            active.size() <= 1,
                            "Multiple active acquisitions require cancellation.");
                    if (!active.isEmpty()) {
                        Acquisition existing = active.get(0);
                        require(
                                existing.owner().equals(actor)
                                        && existing.recipient().equals(recipient)
                                        && existing.context().equals(quote.context())
                                        && existing.fee() == quote.fee()
                                        && existing.baseTotal().equals(quote.baseTotal()),
                                "An acquisition is already active; cancel it before proposing"
                                    + " another.");
                        return receipt(c, existing);
                    }

                    long expires = Math.addExact(tick, policy.offerTicks());
                    require(expires > tick, "Acquisition expiry must be after its proposal.");
                    UUID id = UUID.randomUUID();
                    c.insertInto(a)
                            .set(a.ACQUISITION_ID, id)
                            .set(a.SETTLEMENT_ID, settlementId)
                            .set(a.OWNER_UUID, actor)
                            .set(a.RECIPIENT_UUID, recipient)
                            .set(a.STATE, State.PROPOSED.name())
                            .set(a.FEE, quote.fee())
                            .set(a.BASE_TOTAL, quote.baseTotal().toString())
                            .set(a.CONTEXT, quote.context())
                            .set(a.PROPOSED_TICK, tick)
                            .set(a.EXPIRES_TICK, expires)
                            .setNull(a.ACCEPTED_TICK)
                            .setNull(a.DECIDED_BY_UUID)
                            .setNull(a.DECIDED_TICK)
                            .setNull(a.DECISION_NOTE)
                            .execute();
                    Acquisition proposed = required(c, id);
                    event(c, proposed, actor, "ACQUISITION_PROPOSED", "", tick);
                    clock.checkpoint(tx);
                    return receipt(c, proposed);
                });
    }

    @Override
    public CompletableFuture<SettlementChange> accept(UUID actor, UUID id, long expectedFee) {
        return transact(
                (tx, c) -> {
                    Acquisition current = required(c, id);
                    require(
                            actor != null && actor.equals(current.recipient()),
                            "Only the proposed recipient may accept.");
                    require(
                            expectedFee == current.fee(),
                            "The disclosed acquisition fee must be confirmed exactly.");
                    if (current.state() == State.ACCEPTED || current.state() == State.APPROVED) {
                        // Consent replay neither renews the expiry nor revalidates a historical
                        // quote.
                        return receipt(c, current);
                    }
                    require(
                            current.state() == State.PROPOSED,
                            "Acquisition cannot be accepted in this state.");
                    long tick = clock.ticks();
                    revalidate(tx, c, current, tick);
                    var a = SETTLEMENT_ACQUISITIONS;
                    int updated =
                            c.update(a)
                                    .set(a.STATE, State.ACCEPTED.name())
                                    .set(a.ACCEPTED_TICK, tick)
                                    .where(
                                            a.ACQUISITION_ID
                                                    .eq(id)
                                                    .and(a.STATE.eq(State.PROPOSED.name())))
                                    .execute();
                    require(updated == 1, "Acquisition changed during acceptance.");
                    Acquisition accepted = required(c, id);
                    event(c, accepted, actor, "ACQUISITION_ACCEPTED", "", tick);
                    clock.checkpoint(tx);
                    return receipt(c, accepted);
                });
    }

    @Override
    public CompletableFuture<SettlementChange> review(
            UUID admin, UUID id, boolean approve, String note) {
        return transact(
                (tx, c) -> {
                    require(admin != null && administrators.test(admin), "Administrator required.");
                    String normalized = normalizedNote(note);
                    Acquisition current = required(c, id);
                    State outcome = approve ? State.APPROVED : State.DENIED;
                    if (!unresolved(current)) {
                        require(
                                current.state() == outcome
                                        && admin.equals(current.decidedBy())
                                        && normalized.equals(current.decisionNote()),
                                "Acquisition already has a different decision.");
                        // Deliberately precedes time, ownership, party and quote validation.
                        return receipt(c, current);
                    }

                    long tick = clock.ticks();
                    if (approve) {
                        require(
                                current.state() == State.ACCEPTED,
                                "Recipient acceptance is required.");
                        require(
                                current.acceptedTick() != null,
                                "Acquisition has no recorded acceptance.");
                        SettlementView settlement = revalidate(tx, c, current, tick);
                        long previousRevision = settlement.ownershipRevision();
                        long nextRevision = Math.addExact(previousRevision, 1L);

                        // First write of approval. The callback uses the recipient wallet and
                        // proposal
                        // ID as receipt ID; the reviewing administrator is only the audit actor.
                        // Any subsequent failure rolls this debit back with the entire UnitOfWork.
                        payment.debit(tx, current);
                        transfer(c, current, previousRevision, nextRevision);
                        invalidatePending(c, current.settlementId());
                        resolve(c, current, admin, State.APPROVED, tick, normalized);
                        event(
                                c,
                                current,
                                admin,
                                "OWNERSHIP_TRANSFERRED",
                                ";admin=" + admin + ";note=" + escaped(normalized),
                                tick);
                    } else {
                        // A denial resolves either unresolved state even after expiry or release.
                        resolve(c, current, admin, State.DENIED, tick, normalized);
                        event(
                                c,
                                current,
                                admin,
                                "ACQUISITION_DENIED",
                                ";note=" + escaped(normalized),
                                tick);
                    }
                    clock.checkpoint(tx);
                    return receipt(c, required(c, id));
                });
    }

    @Override
    public CompletableFuture<SettlementChange> cancel(UUID actor, UUID id) {
        return transact(
                (tx, c) -> {
                    require(actor != null, "Cancellation actor required.");
                    Acquisition current = required(c, id);
                    if (current.state() == State.CANCELLED) {
                        require(
                                actor.equals(current.decidedBy()),
                                "Acquisition was cancelled by a different actor.");
                        return receipt(c, current);
                    }
                    require(
                            unresolved(current),
                            "Only an unresolved acquisition may be cancelled.");
                    SettlementView settlement =
                            JooqSettlementRepository.load(c, current.settlementId());
                    require(
                            actor.equals(current.owner())
                                    || actor.equals(current.recipient())
                                    || (settlement != null && actor.equals(settlement.owner()))
                                    || administrators.test(actor),
                            "Only an acquisition party, the current Owner or an administrator may"
                                + " cancel.");
                    long tick = clock.ticks();
                    resolve(c, current, actor, State.CANCELLED, tick, "");
                    event(c, current, actor, "ACQUISITION_CANCELLED", "", tick);
                    clock.checkpoint(tx);
                    return receipt(c, required(c, id));
                });
    }

    private SettlementView revalidate(
            Transaction tx, DSLContext c, Acquisition acquisition, long tick) throws SQLException {
        require(tick < acquisition.expiresTick(), "Acquisition has expired.");
        SettlementView settlement = owned(c, acquisition.settlementId());
        requireParties(tx, settlement, acquisition.owner(), acquisition.recipient());
        Quote current = quote(c, settlement, acquisition.recipient());
        require(
                current.context().equals(acquisition.context())
                        && current.fee() == acquisition.fee()
                        && current.baseTotal().equals(acquisition.baseTotal()),
                "Acquisition quote is stale; cancel it and obtain a new proposal.");
        return settlement;
    }

    private void requireParties(
            Transaction tx, SettlementView settlement, UUID owner, UUID recipient)
            throws SQLException {
        require(
                owner != null && owner.equals(settlement.owner()),
                "The proposed Owner is no longer Owner.");
        require(
                recipient != null && !owner.equals(recipient),
                "Recipient must be a different player.");
        Role role = settlement.role(recipient);
        require(
                role == Role.CITIZEN || role == Role.GOVERNOR,
                "Recipient must currently be a Citizen or Governor.");
        require(
                administrators.test(owner)
                        || !JooqParcelRepository.banned(tx, settlement.id(), owner),
                "Owner is locally banned.");
        require(
                administrators.test(recipient)
                        || !JooqParcelRepository.banned(tx, settlement.id(), recipient),
                "Recipient is locally banned.");
    }

    private record Quote(BigInteger baseTotal, long fee, String context) {}

    private Quote quote(DSLContext c, SettlementView settlement, UUID recipient)
            throws SQLException {
        long securityRevision =
                c.select(SETTLEMENT_DETAILS.SECURITY_REVISION)
                        .from(SETTLEMENT_DETAILS)
                        .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(settlement.id()))
                        .fetchSingle(SETTLEMENT_DETAILS.SECURITY_REVISION);
        require(
                settlement.ownershipRevision() >= 0 && settlement.chunkRevision() >= 0,
                "Invalid Settlement revision.");
        MessageDigest digest = sha256();
        snapshot(digest, "settlement-acquisition-phase3-v1");
        snapshot(digest, policy.offerTicks());
        snapshot(digest, policy.feeBasisPoints());
        snapshot(digest, settlement.id());
        snapshot(digest, settlement.worldInstanceId());
        snapshot(digest, settlement.owner());
        snapshot(digest, recipient);
        snapshot(digest, settlement.ownershipRevision());
        snapshot(digest, settlement.chunkRevision());
        snapshot(digest, securityRevision);

        var claims = SETTLEMENT_CHUNKS;
        var profiles = LAND_VALUE_PROFILES;
        Field<UUID> claimSettlement = claims.SETTLEMENT_ID;
        Field<UUID> claimWorld = claims.WORLD_INSTANCE_ID;
        Field<Long> claimX = claims.CHUNK_X;
        Field<Long> claimZ = claims.CHUNK_Z;
        Field<UUID> profileWorld = profiles.WORLD_INSTANCE_ID;
        Field<Long> profileX = profiles.CHUNK_X;
        Field<Long> profileZ = profiles.CHUNK_Z;
        Field<Long> version = profiles.VALUATION_VERSION;
        Field<Long> base = profiles.BASE_VALUE;
        BigInteger total = BigInteger.ZERO;
        long count = 0;
        Long previousX = null;
        Long previousZ = null;
        for (Record row :
                c.select(
                                claimWorld,
                                claimX,
                                claimZ,
                                profileWorld,
                                profileX,
                                profileZ,
                                version,
                                base)
                        .from(claims)
                        .leftJoin(profiles)
                        .on(
                                profileWorld
                                        .eq(claimWorld)
                                        .and(profileX.eq(claimX))
                                        .and(profileZ.eq(claimZ)))
                        .where(claimSettlement.eq(settlement.id()))
                        .orderBy(claimX.asc(), claimZ.asc())
                        .fetch()) {
            UUID world = row.get(claimWorld);
            Long x = row.get(claimX);
            Long z = row.get(claimZ);
            Long value = row.get(base);
            Object valuationVersion = row.get(version);
            require(
                    world != null
                            && world.equals(settlement.worldInstanceId())
                            && x != null
                            && z != null,
                    "Claim has an invalid world or coordinates.");
            require(
                    row.get(profileWorld) != null
                            && row.get(profileX) != null
                            && row.get(profileZ) != null
                            && valuationVersion != null
                            && value != null,
                    "Every claimed chunk must have a stored land-value profile.");
            require(
                    !Objects.equals(previousX, x) || !Objects.equals(previousZ, z),
                    "Duplicate claimed chunk or land-value profile.");
            require(value > 0, "Land-value profile must have a positive base value.");
            previousX = x;
            previousZ = z;
            count = Math.addExact(count, 1L);
            total = total.add(BigInteger.valueOf(value));
            snapshot(digest, world);
            snapshot(digest, x);
            snapshot(digest, z);
            snapshot(digest, valuationVersion);
            snapshot(digest, value);
        }
        snapshot(digest, count);
        snapshot(digest, total);
        long fee = policy.fee(total);
        require(fee > 0, "Acquisition fee must be positive.");
        snapshot(digest, fee);
        return new Quote(total, fee, "sha256:" + HexFormat.of().formatHex(digest.digest()));
    }

    private static void transfer(
            DSLContext c, Acquisition acquisition, long previousRevision, long nextRevision) {
        UUID id = acquisition.settlementId();
        var members = SETTLEMENT_MEMBERS;
        int removed =
                c.deleteFrom(members)
                        .where(
                                members.SETTLEMENT_ID
                                        .eq(id)
                                        .and(members.PLAYER_UUID.eq(acquisition.recipient()))
                                        .and(
                                                members.ROLE.in(
                                                        Role.CITIZEN.name(), Role.GOVERNOR.name())))
                        .execute();
        require(removed == 1, "Recipient membership changed.");
        int changed =
                c.update(SETTLEMENTS)
                        .set(SETTLEMENTS.OWNER_UUID, acquisition.recipient())
                        .set(SETTLEMENTS.OWNERSHIP_REVISION, nextRevision)
                        .where(
                                SETTLEMENTS
                                        .SETTLEMENT_ID
                                        .eq(id)
                                        .and(SETTLEMENTS.STATUS.eq("OWNED"))
                                        .and(SETTLEMENTS.OWNER_UUID.eq(acquisition.owner()))
                                        .and(SETTLEMENTS.OWNERSHIP_REVISION.eq(previousRevision)))
                        .execute();
        require(changed == 1, "Settlement ownership changed.");
        c.insertInto(members)
                .set(members.SETTLEMENT_ID, id)
                .set(members.PLAYER_UUID, acquisition.owner())
                .set(members.ROLE, Role.CITIZEN.name())
                .execute();
    }

    private static void invalidatePending(DSLContext c, UUID settlementId) {
        c.update(SETTLEMENT_OFFERS)
                .set(SETTLEMENT_OFFERS.STATE, "CANCELLED")
                .where(
                        SETTLEMENT_OFFERS
                                .SETTLEMENT_ID
                                .eq(settlementId)
                                .and(SETTLEMENT_OFFERS.STATE.eq("PENDING")))
                .execute();
        c.update(PARCEL_REQUESTS)
                .set(PARCEL_REQUESTS.STATE, "STALE")
                .where(
                        PARCEL_REQUESTS
                                .SETTLEMENT_ID
                                .eq(settlementId)
                                .and(PARCEL_REQUESTS.STATE.eq("PENDING")))
                .execute();
    }

    private static boolean unresolved(Acquisition acquisition) {
        return acquisition.state() == State.PROPOSED || acquisition.state() == State.ACCEPTED;
    }

    private static Acquisition find(DSLContext c, UUID id) {
        var a = SETTLEMENT_ACQUISITIONS;
        Record row = c.selectFrom(a).where(a.ACQUISITION_ID.eq(id)).fetchOne();
        return row == null ? null : acquisition(row);
    }

    private static Acquisition required(DSLContext c, UUID id) {
        require(id != null, "Acquisition ID required.");
        Acquisition acquisition = find(c, id);
        require(acquisition != null, "Acquisition not found.");
        return acquisition;
    }

    private static Acquisition acquisition(Record row) {
        var a = SETTLEMENT_ACQUISITIONS;
        return new Acquisition(
                row.get(a.ACQUISITION_ID),
                row.get(a.SETTLEMENT_ID),
                row.get(a.OWNER_UUID),
                row.get(a.RECIPIENT_UUID),
                State.valueOf(row.get(a.STATE)),
                row.get(a.FEE),
                new BigInteger(row.get(a.BASE_TOTAL)),
                row.get(a.CONTEXT),
                row.get(a.PROPOSED_TICK),
                row.get(a.EXPIRES_TICK),
                row.get(a.ACCEPTED_TICK),
                row.get(a.DECIDED_BY_UUID),
                row.get(a.DECIDED_TICK),
                row.get(a.DECISION_NOTE));
    }

    private static void resolve(
            DSLContext c, Acquisition current, UUID actor, State outcome, long tick, String note) {
        var a = SETTLEMENT_ACQUISITIONS;
        int updated =
                c.update(a)
                        .set(a.STATE, outcome.name())
                        .set(a.DECIDED_BY_UUID, actor)
                        .set(a.DECIDED_TICK, tick)
                        .set(a.DECISION_NOTE, note)
                        .where(
                                a.ACQUISITION_ID
                                        .eq(current.id())
                                        .and(a.STATE.eq(current.state().name())))
                        .execute();
        require(updated == 1, "Acquisition changed during resolution.");
    }

    private static SettlementView owned(DSLContext c, UUID id) throws SQLException {
        SettlementView settlement = JooqSettlementRepository.load(c, id);
        require(settlement != null, "Settlement is no longer owned or does not exist.");
        return settlement;
    }

    private static SettlementChange receipt(DSLContext c, Acquisition acquisition)
            throws SQLException {
        UUID id = acquisition.settlementId();
        String message =
                "Acquisition "
                        + acquisition.id()
                        + " "
                        + acquisition.state().name()
                        + ". Fee: "
                        + acquisition.fee()
                        + ".";
        if (acquisition.state() == State.PROPOSED) {
            message +=
                    " Recipient: /settlement accept-transfer "
                            + acquisition.id()
                            + " "
                            + acquisition.fee();
        } else if (acquisition.state() == State.ACCEPTED) {
            message += " Awaiting administrator approval.";
        }
        return new SettlementChange(id, JooqSettlementRepository.load(c, id), null, message);
    }

    private static String normalizedNote(String raw) {
        String note = raw == null ? "" : raw.trim();
        require(note.length() <= 256, "Decision note must be at most 256 characters.");
        return note;
    }

    private static String escaped(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static void event(
            DSLContext c,
            Acquisition acquisition,
            UUID actor,
            String type,
            String extra,
            long tick) {
        var e = SETTLEMENT_EVENTS;
        String detail =
                "acquisition="
                        + acquisition.id()
                        + ";receipt="
                        + acquisition.id()
                        + ";oldOwner="
                        + acquisition.owner()
                        + ";newOwner="
                        + acquisition.recipient()
                        + ";fee="
                        + acquisition.fee()
                        + ";baseTotal="
                        + acquisition.baseTotal()
                        + ";context="
                        + acquisition.context()
                        + ";proposedTick="
                        + acquisition.proposedTick()
                        + ";expiresTick="
                        + acquisition.expiresTick()
                        + ";acceptedTick="
                        + acquisition.acceptedTick()
                        + extra;
        c.insertInto(e)
                .set(e.EVENT_ID, UUID.randomUUID())
                .set(e.SETTLEMENT_ID, acquisition.settlementId())
                .set(e.ACTOR_UUID, actor)
                .set(e.TYPE, type)
                .set(e.DETAIL, detail)
                .set(e.OCCURRED_AT, Instant.now().toEpochMilli())
                .set(e.GAME_TICK, tick)
                .set(e.DELIVERED, 0L)
                .execute();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Length-prefix every UTF-8 component, avoiding ambiguous concatenated snapshots. */
    private static void snapshot(MessageDigest digest, Object value) {
        byte[] bytes = Objects.requireNonNull(value).toString().getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        digest.update(bytes);
    }
}
