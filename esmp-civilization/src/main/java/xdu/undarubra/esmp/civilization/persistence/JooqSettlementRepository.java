package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.*;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

import xdu.undarubra.esmp.civilization.persistence.legacy.LegacySettlementMigrations;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.civilization.settlement.SettlementService.*;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Civilization-owned SQL adapter. All SQL and callbacks use the same worker transaction. */
public final class JooqSettlementRepository implements SettlementService.Repository {
    private static final Field<Long> SQLITE_ROWID = DSL.field(DSL.name("rowid"), Long.class);
    private final UnitOfWork database;
    private final SettlementPolicy policy;
    private final GameClock clock;
    private final Predicate<UUID> administrator;
    private final ReleaseGuard releaseGuard;
    private final ClaimPricing pricing;
    private final ClaimPayment payment;
    private final JooqBuildingRepository buildings;

    public JooqSettlementRepository(
            UnitOfWork database,
            SettlementPolicy policy,
            GameClock clock,
            Predicate<UUID> administrator,
            ReleaseGuard releaseGuard,
            ClaimPricingPolicy pricingPolicy,
            ClaimPayment payment,
            ClaimActivity activity) {
        this.database = database;
        this.policy = policy;
        this.clock = clock;
        this.administrator = administrator;
        this.releaseGuard = Objects.requireNonNull(releaseGuard);
        this.payment = Objects.requireNonNull(payment);
        this.buildings =
                new JooqBuildingRepository(
                        database,
                        clock,
                        new xdu.undarubra.esmp.civilization.building.BuildingService.Policy(
                                xdu.undarubra.esmp.civilization.building.BuildingBounds
                                        .HARD_MAX_VOLUME),
                        administrator);
        this.pricing =
                new ClaimPricing(
                        clock,
                        Objects.requireNonNull(pricingPolicy),
                        Objects.requireNonNull(activity));
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(DSLContext context) throws Exception;
    }

    private <T> CompletableFuture<T> transact(SqlWork<T> work) {
        return database.transact(tx -> work.run(DatabaseAccess.context(tx)));
    }

    public CompletableFuture<List<SettlementView>> initialize() {
        return transact(
                c -> {
                    Transaction tx = DatabaseAccess.transaction(c);
                    if (DatabaseAccess.legacy(tx)) LegacySettlementMigrations.migrate(c, clock);
                    Long version =
                            c.select(CIVILIZATION_SCHEMA.VERSION)
                                    .from(CIVILIZATION_SCHEMA)
                                    .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                                    .fetchOne(CIVILIZATION_SCHEMA.VERSION);
                    if (version == null || version != 3L)
                        throw new SQLException("Unsupported Civilization schema: " + version);
                    pricing.initialize(c);
                    clock.checkpoint(tx);
                    return loadAll(c);
                });
    }

    public TreasuryPermission treasuryPermission(Transaction transaction, UUID actor, UUID id)
            throws SQLException {
        DSLContext c = DatabaseAccess.context(transaction);
        var s = SETTLEMENTS.as("s");
        var m = SETTLEMENT_MEMBERS.as("m");
        Record row =
                c.select(s.OWNER_UUID, s.OWNERSHIP_REVISION, m.ROLE)
                        .from(s)
                        .leftJoin(m)
                        .on(m.SETTLEMENT_ID.eq(s.SETTLEMENT_ID).and(m.PLAYER_UUID.eq(actor)))
                        .where(s.SETTLEMENT_ID.eq(id).and(s.STATUS.eq("OWNED")))
                        .fetchOne();
        if (row == null) {
            var terminal =
                    c.selectFrom(CYCLE_TERMINAL)
                            .where(CYCLE_TERMINAL.SETTLEMENT_ID.eq(id))
                            .fetchOne();
            require(terminal != null, "Settlement is no longer owned.");
            return new TreasuryPermission(
                    actor != null && actor.equals(terminal.get(CYCLE_TERMINAL.OWNER_UUID))
                            ? Role.OWNER
                            : Role.PUBLIC,
                    0,
                    null,
                    true);
        }
        require(row != null, "Settlement is no longer owned.");
        if (actor != null)
            require(
                    administrator.test(actor) || !JooqParcelRepository.banned(c, id, actor),
                    "You are banned from this Settlement.");
        Role role =
                actor != null && actor.equals(row.get(s.OWNER_UUID))
                        ? Role.OWNER
                        : row.get(m.ROLE) == null ? Role.PUBLIC : Role.valueOf(row.get(m.ROLE));
        UUID promotion = null;
        if (role == Role.GOVERNOR) {
            promotion =
                    c.select(SETTLEMENT_OFFERS.OFFER_ID)
                            .from(SETTLEMENT_OFFERS)
                            .where(
                                    SETTLEMENT_OFFERS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(SETTLEMENT_OFFERS.RECIPIENT_UUID.eq(actor))
                                            .and(
                                                    SETTLEMENT_OFFERS.TYPE.eq(
                                                            SettlementOffer.Type.PROMOTE.name()))
                                            .and(SETTLEMENT_OFFERS.STATE.eq("ACCEPTED")))
                            .orderBy(SQLITE_ROWID.desc())
                            .limit(1)
                            .fetchOne(SETTLEMENT_OFFERS.OFFER_ID);
        }
        return new TreasuryPermission(role, row.get(s.OWNERSHIP_REVISION), promotion, false);
    }

    public CompletableFuture<ClaimQuote> quoteCreate(UUID actor, UUID world, ChunkPosition chunk) {
        return transact(
                c -> {
                    requireClaimTarget(c, actor, null, world, chunk);
                    return pricing.quote(c, actor, null, world, chunk);
                });
    }

    public CompletableFuture<ClaimQuote> quoteClaim(
            UUID actor, UUID id, UUID world, ChunkPosition chunk) {
        return transact(
                c -> {
                    SettlementView settlement = owned(c, id, actor);
                    requireClaimTarget(c, actor, settlement, world, chunk);
                    return pricing.quote(c, actor, settlement, world, chunk);
                });
    }

    public CompletableFuture<ClaimQuote> quote(UUID actor, UUID quoteId) {
        return transact(
                c -> {
                    ClaimQuote quote = loadQuote(c, actor, quoteId);
                    if (!quote.purchased()) validateQuote(c, quote);
                    return quote;
                });
    }

    public CompletableFuture<SettlementChange> purchase(
            UUID actor,
            UUID quoteId,
            List<xdu.undarubra.esmp.civilization.building.BuildingService.Capture> captures) {
        return transact(
                c -> {
                    ClaimQuote quote = loadQuote(c, actor, quoteId);
                    UUID id = quote.settlementId();
                    if (quote.purchased()) {
                        // Replay must not debit, checkpoint, complete or publish again.
                        return changed(
                                c,
                                id,
                                null,
                                "Purchase already completed. Paid " + quote.amount() + ".");
                    }
                    SettlementView settlement = validateQuote(c, quote);
                    UUID world = quote.worldInstance();
                    ChunkPosition chunk = quote.chunk();
                    if (quote.founding()) {
                        long number =
                                c.select(
                                                DSL.coalesce(DSL.max(SETTLEMENT_DETAILS.NUMBER), 0L)
                                                        .add(1L))
                                        .from(SETTLEMENT_DETAILS)
                                        .fetchOne()
                                        .value1();
                        String name = "S#" + number;
                        payment.debit(DatabaseAccess.transaction(c), quote);
                        c.insertInto(SETTLEMENTS)
                                .set(SETTLEMENTS.SETTLEMENT_ID, id)
                                .set(SETTLEMENTS.WORLD_INSTANCE_ID, world)
                                .set(SETTLEMENTS.NAME, name)
                                .set(SETTLEMENTS.NAME_KEY, key(name))
                                .set(SETTLEMENTS.OWNER_UUID, actor)
                                .set(SETTLEMENTS.CREATED_AT, Instant.now().toEpochMilli())
                                .set(SETTLEMENTS.OWNERSHIP_REVISION, 1L)
                                .set(SETTLEMENTS.CHUNK_REVISION, 1L)
                                .set(SETTLEMENTS.STATUS, "OWNED")
                                .execute();
                        c.update(SETTLEMENTS)
                                .set(SETTLEMENTS.FOUNDED_TICK, clock.ticks())
                                .where(SETTLEMENTS.SETTLEMENT_ID.eq(id))
                                .execute();
                        c.insertInto(SETTLEMENT_DETAILS)
                                .set(SETTLEMENT_DETAILS.SETTLEMENT_ID, id)
                                .set(SETTLEMENT_DETAILS.NUMBER, number)
                                .setNull(SETTLEMENT_DETAILS.OFFICIAL_NAME)
                                .set(SETTLEMENT_DETAILS.CERTIFICATION_STATUS, "UNVERIFIED")
                                .set(SETTLEMENT_DETAILS.CERTIFICATION_REVISION, 0L)
                                .set(SETTLEMENT_DETAILS.SECURITY_REVISION, 0L)
                                .execute();
                        insertChunk(c, id, world, chunk);
                        JooqLocalActivity.claimed(c, world, chunk);
                        buildings.takeover(c, actor, id, world, chunk, captures);
                        pricing.complete(c, quote);
                        event(
                                c,
                                id,
                                actor,
                                "FOUNDED",
                                name + " at " + chunk.x() + "," + chunk.z() + paymentDetail(quote));
                        return changed(
                                c, id, null, "Created " + name + ". Paid " + quote.amount() + ".");
                    }
                    payment.debit(DatabaseAccess.transaction(c), quote);
                    insertChunk(c, id, world, chunk);
                    JooqLocalActivity.claimed(c, world, chunk);
                    c.update(SETTLEMENTS)
                            .set(SETTLEMENTS.CHUNK_REVISION, SETTLEMENTS.CHUNK_REVISION.add(1L))
                            .where(SETTLEMENTS.SETTLEMENT_ID.eq(id))
                            .execute();
                    buildings.takeover(c, actor, id, world, chunk, captures);
                    pricing.complete(c, quote);
                    event(
                            c,
                            id,
                            actor,
                            "CHUNK_CLAIMED",
                            chunk.x() + "," + chunk.z() + paymentDetail(quote));
                    return changed(
                            c,
                            id,
                            null,
                            "Claimed chunk for "
                                    + settlement.name()
                                    + ". Paid "
                                    + quote.amount()
                                    + ".");
                });
    }

    private ClaimQuote loadQuote(DSLContext c, UUID actor, UUID quoteId) throws SQLException {
        ClaimQuote quote = pricing.load(c, actor, quoteId);
        require(quote != null, "Claim quote not found.");
        require(
                quote.id().equals(quoteId) && quote.actor().equals(actor),
                "Claim quote not found.");
        require(
                quote.purchased() || clock.ticks() < quote.expiresTick(),
                "Claim quote expired; request a new one.");
        return quote;
    }

    private SettlementView validateQuote(DSLContext c, ClaimQuote quote) throws SQLException {
        SettlementView settlement =
                quote.founding() ? null : owned(c, quote.settlementId(), quote.actor());
        requireClaimTarget(c, quote.actor(), settlement, quote.worldInstance(), quote.chunk());
        pricing.validate(c, quote, settlement);
        return settlement;
    }

    private void requireClaimTarget(
            DSLContext c, UUID actor, SettlementView settlement, UUID world, ChunkPosition chunk)
            throws SQLException {
        JooqBuildingRepository.claimBoundary(c, world, chunk);
        if (settlement == null) {
            requireMainWorld(c, world);
            requireUnclaimed(c, world, chunk);
            return;
        }
        require(
                settlement.role(actor).canManage(),
                "Only the Owner or a Governor may claim chunks.");
        require(settlement.worldInstanceId().equals(world), "Claims cannot cross world instances.");
        requireMainWorld(c, world);
        requireUnclaimed(c, world, chunk);
        require(
                !policy.chunkCapEnabled() || settlement.chunks().size() < policy.maxChunks(),
                "Settlement chunk cap reached.");
        require(
                neighbors(chunk).stream().anyMatch(settlement.chunks()::contains),
                "The new chunk must share an edge with this Settlement.");
    }

    public CompletableFuture<SettlementChange> unclaim(UUID actor, UUID id, ChunkPosition chunk) {
        return transact(
                c -> {
                    SettlementView settlement = owned(c, id, actor);
                    require(
                            settlement.role(actor).canManage(),
                            "Only the Owner or a Governor may unclaim chunks.");
                    require(
                            settlement.chunks().contains(chunk),
                            "This chunk does not belong to that Settlement.");
                    require(
                            settlement.chunks().size() > 1,
                            "The last chunk requires the Owner's explicit abandon confirmation.");
                    for (var row :
                            c.select(PARCEL_CELLS.X, PARCEL_CELLS.Z)
                                    .from(PARCEL_CELLS)
                                    .where(PARCEL_CELLS.SETTLEMENT_ID.eq(id))
                                    .fetch()) {
                        require(
                                Math.floorDiv(Math.toIntExact(row.get(PARCEL_CELLS.X)), 2)
                                                != chunk.x()
                                        || Math.floorDiv(
                                                        Math.toIntExact(row.get(PARCEL_CELLS.Z)), 2)
                                                != chunk.z(),
                                "Remove affected Parcels through their consent workflow before"
                                        + " unclaiming");
                    }
                    Set<ChunkPosition> remaining = new HashSet<>(settlement.chunks());
                    remaining.remove(chunk);
                    require(
                            connected(remaining),
                            "Unclaiming this chunk would split the Settlement.");
                    buildings.release(c, actor, id, Set.of(chunk));
                    c.deleteFrom(SETTLEMENT_LOCAL_ACTIVITY)
                            .where(
                                    SETTLEMENT_LOCAL_ACTIVITY
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(
                                                    SETTLEMENT_LOCAL_ACTIVITY.WORLD_INSTANCE_ID.eq(
                                                            settlement.worldInstanceId()))
                                            .and(
                                                    SETTLEMENT_LOCAL_ACTIVITY.CHUNK_X.eq(
                                                            (long) chunk.x()))
                                            .and(
                                                    SETTLEMENT_LOCAL_ACTIVITY.CHUNK_Z.eq(
                                                            (long) chunk.z())))
                            .execute();
                    c.deleteFrom(SETTLEMENT_CHUNKS)
                            .where(
                                    SETTLEMENT_CHUNKS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(SETTLEMENT_CHUNKS.CHUNK_X.eq((long) chunk.x()))
                                            .and(SETTLEMENT_CHUNKS.CHUNK_Z.eq((long) chunk.z())))
                            .execute();
                    c.update(SETTLEMENTS)
                            .set(SETTLEMENTS.CHUNK_REVISION, SETTLEMENTS.CHUNK_REVISION.add(1L))
                            .where(SETTLEMENTS.SETTLEMENT_ID.eq(id))
                            .execute();
                    event(c, id, actor, "CHUNK_UNCLAIMED", chunk.x() + "," + chunk.z());
                    return changed(c, id, null, "Unclaimed chunk from " + settlement.name() + ".");
                });
    }

    public CompletableFuture<SettlementChange> rename(UUID actor, UUID id, String rawName) {
        String name = normalize(rawName);
        return transact(
                c -> {
                    SettlementView settlement = owned(c, id, actor);
                    require(
                            administrator.test(actor),
                            "Only a server administrator may approve an official name");
                    String status =
                            c.select(SETTLEMENT_DETAILS.CERTIFICATION_STATUS)
                                    .from(SETTLEMENT_DETAILS)
                                    .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                                    .fetchOne(SETTLEMENT_DETAILS.CERTIFICATION_STATUS);
                    require("VERIFIED".equals(status), "Verified certification is required");
                    requireNameAvailable(c, name, id);
                    c.update(SETTLEMENTS)
                            .set(SETTLEMENTS.NAME, name)
                            .set(SETTLEMENTS.NAME_KEY, key(name))
                            .where(SETTLEMENTS.SETTLEMENT_ID.eq(id))
                            .execute();
                    c.update(SETTLEMENT_DETAILS)
                            .set(SETTLEMENT_DETAILS.OFFICIAL_NAME, name)
                            .set(
                                    SETTLEMENT_DETAILS.CERTIFICATION_REVISION,
                                    SETTLEMENT_DETAILS.CERTIFICATION_REVISION.add(1L))
                            .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                            .execute();
                    event(c, id, actor, "RENAMED", settlement.name() + " -> " + name);
                    return changed(c, id, null, "Renamed Settlement to " + name + ".");
                });
    }

    public CompletableFuture<SettlementChange> ban(
            UUID actor, UUID id, UUID target, boolean banned) {
        return transact(
                c -> {
                    var settlement = owned(c, id, actor);
                    require(
                            administrator.test(actor) || settlement.role(actor).canManage(),
                            "Owner or Governor required");
                    require(
                            !banned || !administrator.test(target),
                            "Server administrators cannot be banned");
                    if (banned) {
                        long tick = clock.ticks();
                        c.insertInto(SETTLEMENT_BANS)
                                .set(SETTLEMENT_BANS.SETTLEMENT_ID, id)
                                .set(SETTLEMENT_BANS.PLAYER_UUID, target)
                                .set(SETTLEMENT_BANS.BANNED_BY, actor)
                                .set(SETTLEMENT_BANS.CREATED_TICK, tick)
                                .onConflict(
                                        SETTLEMENT_BANS.SETTLEMENT_ID, SETTLEMENT_BANS.PLAYER_UUID)
                                .doUpdate()
                                .set(SETTLEMENT_BANS.BANNED_BY, actor)
                                .set(SETTLEMENT_BANS.CREATED_TICK, tick)
                                .execute();
                    } else {
                        c.deleteFrom(SETTLEMENT_BANS)
                                .where(
                                        SETTLEMENT_BANS
                                                .SETTLEMENT_ID
                                                .eq(id)
                                                .and(SETTLEMENT_BANS.PLAYER_UUID.eq(target)))
                                .execute();
                    }
                    c.update(SETTLEMENT_DETAILS)
                            .set(
                                    SETTLEMENT_DETAILS.SECURITY_REVISION,
                                    SETTLEMENT_DETAILS.SECURITY_REVISION.add(1L))
                            .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                            .execute();
                    c.update(PARCEL_REQUESTS)
                            .set(PARCEL_REQUESTS.STATE, "STALE")
                            .where(
                                    PARCEL_REQUESTS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(PARCEL_REQUESTS.STATE.eq("PENDING")))
                            .execute();
                    event(c, id, actor, banned ? "BANNED" : "UNBANNED", target.toString());
                    return changed(c, id, null, banned ? "Player banned." : "Ban removed.");
                });
    }

    public CompletableFuture<SettlementChange> offer(
            UUID actor, UUID id, UUID recipient, SettlementOffer.Type type) {
        return transact(
                c -> {
                    SettlementView settlement = owned(c, id, actor);
                    require(
                            administrator.test(recipient)
                                    || !JooqParcelRepository.banned(c, id, recipient),
                            "Recipient is banned");
                    Role issuerRole = settlement.role(actor);
                    Role targetRole = settlement.role(recipient);
                    require(
                            type == SettlementOffer.Type.INVITE
                                    ? issuerRole.canManage()
                                    : issuerRole == Role.OWNER,
                            "You do not have authority for this offer.");
                    switch (type) {
                        case INVITE ->
                                require(
                                        targetRole == Role.PUBLIC,
                                        "That player is already in the Settlement.");
                        case PROMOTE ->
                                require(
                                        targetRole == Role.CITIZEN,
                                        "Only an existing Citizen can be promoted.");
                        case ABANDON ->
                                require(
                                        actor.equals(recipient),
                                        "Only the Owner may confirm abandonment.");
                    }
                    c.update(SETTLEMENT_OFFERS)
                            .set(SETTLEMENT_OFFERS.STATE, "EXPIRED")
                            .where(
                                    SETTLEMENT_OFFERS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(SETTLEMENT_OFFERS.STATE.eq("PENDING"))
                                            .and(SETTLEMENT_OFFERS.EXPIRES_AT.le(clock.ticks())))
                            .execute();
                    if (type == SettlementOffer.Type.ABANDON) {
                        c.update(SETTLEMENT_OFFERS)
                                .set(SETTLEMENT_OFFERS.STATE, "CANCELLED")
                                .where(
                                        SETTLEMENT_OFFERS
                                                .SETTLEMENT_ID
                                                .eq(id)
                                                .and(
                                                        SETTLEMENT_OFFERS.TYPE.eq(
                                                                SettlementOffer.Type.ABANDON
                                                                        .name()))
                                                .and(SETTLEMENT_OFFERS.STATE.eq("PENDING")))
                                .execute();
                    }
                    require(
                            !c.fetchExists(
                                    c.selectOne()
                                            .from(SETTLEMENT_OFFERS)
                                            .where(
                                                    SETTLEMENT_OFFERS
                                                            .SETTLEMENT_ID
                                                            .eq(id)
                                                            .and(
                                                                    SETTLEMENT_OFFERS.RECIPIENT_UUID
                                                                            .eq(recipient))
                                                            .and(
                                                                    SETTLEMENT_OFFERS.TYPE.eq(
                                                                            type.name()))
                                                            .and(
                                                                    SETTLEMENT_OFFERS.STATE.eq(
                                                                            "PENDING")))),
                            "A pending offer already exists; inspect /settlement offers.");
                    SettlementOffer offer =
                            new SettlementOffer(
                                    UUID.randomUUID(),
                                    id,
                                    type,
                                    actor,
                                    recipient,
                                    Math.addExact(clock.ticks(), policy.lifetimeTicks(type)),
                                    settlement.ownershipRevision(),
                                    settlement.chunkRevision());
                    c.insertInto(SETTLEMENT_OFFERS)
                            .set(SETTLEMENT_OFFERS.OFFER_ID, offer.id())
                            .set(SETTLEMENT_OFFERS.SETTLEMENT_ID, id)
                            .set(SETTLEMENT_OFFERS.TYPE, type.name())
                            .set(SETTLEMENT_OFFERS.ISSUER_UUID, actor)
                            .set(SETTLEMENT_OFFERS.RECIPIENT_UUID, recipient)
                            .set(SETTLEMENT_OFFERS.EXPIRES_AT, offer.expiresAtTick())
                            .set(SETTLEMENT_OFFERS.OWNERSHIP_REVISION, offer.ownershipRevision())
                            .set(SETTLEMENT_OFFERS.CHUNK_REVISION, offer.chunkRevision())
                            .set(SETTLEMENT_OFFERS.STATE, "PENDING")
                            .execute();
                    event(
                            c,
                            id,
                            actor,
                            type + "_OFFERED",
                            "recipient=" + recipient + ";offer=" + offer.id());
                    return new SettlementChange(
                            id,
                            settlement,
                            offer,
                            type == SettlementOffer.Type.ABANDON
                                    ? "WARNING: abandonment removes all claim protection for "
                                            + settlement.name()
                                            + "."
                                    : type + " offer created for " + settlement.name() + ".");
                });
    }

    public CompletableFuture<SettlementChange> accept(
            UUID actor, UUID offerId, boolean abandonmentConfirmation) {
        return transact(
                c -> {
                    SettlementOffer offer = pendingOffer(c, offerId);
                    require(
                            offer.recipient().equals(actor),
                            "Only the nominated recipient may accept this offer.");
                    require(
                            (offer.type() == SettlementOffer.Type.ABANDON)
                                    == abandonmentConfirmation,
                            "Use /settlement confirm for abandonment, and /settlement accept for"
                                    + " other offers.");
                    SettlementView settlement = owned(c, offer.settlementId(), actor);
                    require(
                            administrator.test(offer.issuer())
                                    || !JooqParcelRepository.banned(
                                            c, settlement.id(), offer.issuer()),
                            "Issuer is banned");
                    Role issuerRole = settlement.role(offer.issuer());
                    Role targetRole = settlement.role(actor);
                    if (offer.type() == SettlementOffer.Type.INVITE) {
                        require(issuerRole.canManage(), "The inviter no longer has authority.");
                        require(
                                targetRole == Role.PUBLIC,
                                "Existing membership cannot be overwritten by an invitation.");
                    } else {
                        require(
                                issuerRole == Role.OWNER
                                        && settlement.ownershipRevision()
                                                == offer.ownershipRevision(),
                                "Ownership changed; request a fresh offer.");
                    }
                    switch (offer.type()) {
                        case INVITE -> addCitizen(c, settlement.id(), actor, Role.CITIZEN);
                        case PROMOTE -> {
                            require(
                                    targetRole == Role.CITIZEN,
                                    "The recipient is no longer a Citizen.");
                            c.update(SETTLEMENT_MEMBERS)
                                    .set(SETTLEMENT_MEMBERS.ROLE, Role.GOVERNOR.name())
                                    .where(
                                            SETTLEMENT_MEMBERS
                                                    .SETTLEMENT_ID
                                                    .eq(settlement.id())
                                                    .and(SETTLEMENT_MEMBERS.PLAYER_UUID.eq(actor)))
                                    .execute();
                        }
                        case ABANDON -> {
                            require(
                                    targetRole == Role.OWNER
                                            && settlement.chunkRevision() == offer.chunkRevision(),
                                    "Ownership or claims changed; request a fresh abandonment"
                                            + " warning.");
                            require(
                                    !c.fetchExists(
                                            c.selectOne()
                                                    .from(PARCELS)
                                                    .where(
                                                            PARCELS.SETTLEMENT_ID.eq(
                                                                    settlement.id()))),
                                    "Remove Parcels through their consent workflow before"
                                            + " abandoning");
                            releaseGuard.check(DatabaseAccess.transaction(c), settlement.id());
                            buildings.release(c, actor, settlement.id(), settlement.chunks());
                            c.deleteFrom(SETTLEMENT_LOCAL_ACTIVITY)
                                    .where(
                                            SETTLEMENT_LOCAL_ACTIVITY.SETTLEMENT_ID.eq(
                                                    settlement.id()))
                                    .execute();
                            c.deleteFrom(SETTLEMENT_CHUNKS)
                                    .where(SETTLEMENT_CHUNKS.SETTLEMENT_ID.eq(settlement.id()))
                                    .execute();
                            c.deleteFrom(SETTLEMENT_MEMBERS)
                                    .where(SETTLEMENT_MEMBERS.SETTLEMENT_ID.eq(settlement.id()))
                                    .execute();
                            c.update(SETTLEMENTS)
                                    .setNull(SETTLEMENTS.OWNER_UUID)
                                    .set(SETTLEMENTS.STATUS, "RELEASED")
                                    .set(
                                            SETTLEMENTS.OWNERSHIP_REVISION,
                                            SETTLEMENTS.OWNERSHIP_REVISION.add(1L))
                                    .set(
                                            SETTLEMENTS.CHUNK_REVISION,
                                            SETTLEMENTS.CHUNK_REVISION.add(1L))
                                    .where(SETTLEMENTS.SETTLEMENT_ID.eq(settlement.id()))
                                    .execute();
                        }
                    }
                    c.update(SETTLEMENT_OFFERS)
                            .set(SETTLEMENT_OFFERS.STATE, "ACCEPTED")
                            .where(
                                    SETTLEMENT_OFFERS
                                            .OFFER_ID
                                            .eq(offerId)
                                            .and(SETTLEMENT_OFFERS.STATE.eq("PENDING")))
                            .execute();
                    if (offer.type() == SettlementOffer.Type.ABANDON) {
                        c.update(SETTLEMENT_OFFERS)
                                .set(SETTLEMENT_OFFERS.STATE, "CANCELLED")
                                .where(
                                        SETTLEMENT_OFFERS
                                                .SETTLEMENT_ID
                                                .eq(settlement.id())
                                                .and(SETTLEMENT_OFFERS.STATE.eq("PENDING")))
                                .execute();
                    }
                    String eventType =
                            switch (offer.type()) {
                                case INVITE -> "CITIZEN_JOINED";
                                case PROMOTE -> "GOVERNOR_PROMOTED";
                                case ABANDON -> "RELEASED";
                            };
                    event(
                            c,
                            settlement.id(),
                            actor,
                            eventType,
                            "issuer=" + offer.issuer() + ";recipient=" + actor);
                    return changed(
                            c,
                            settlement.id(),
                            null,
                            offer.type() == SettlementOffer.Type.ABANDON
                                    ? "Released "
                                            + settlement.name()
                                            + "; claim protection removed."
                                    : "Accepted "
                                            + offer.type()
                                            + " for "
                                            + settlement.name()
                                            + ".");
                });
    }

    public CompletableFuture<SettlementChange> resolveOffer(
            UUID actor, UUID offerId, boolean decline) {
        return transact(
                c -> {
                    SettlementOffer offer = pendingOffer(c, offerId);
                    SettlementView settlement = owned(c, offer.settlementId(), actor);
                    if (decline) {
                        require(offer.recipient().equals(actor), "Only the recipient may decline.");
                    } else {
                        Role role = settlement.role(actor);
                        require(
                                offer.type() == SettlementOffer.Type.INVITE
                                        ? role.canManage()
                                        : role == Role.OWNER,
                                "You cannot cancel this offer.");
                    }
                    String state = decline ? "DECLINED" : "CANCELLED";
                    c.update(SETTLEMENT_OFFERS)
                            .set(SETTLEMENT_OFFERS.STATE, state)
                            .where(SETTLEMENT_OFFERS.OFFER_ID.eq(offerId))
                            .execute();
                    event(
                            c,
                            settlement.id(),
                            actor,
                            "OFFER_" + state,
                            offer.type() + ":" + offer.id());
                    return new SettlementChange(
                            settlement.id(),
                            settlement,
                            null,
                            "Offer " + state.toLowerCase(Locale.ROOT) + ".");
                });
    }

    public CompletableFuture<SettlementChange> remove(
            UUID actor, UUID id, UUID target, boolean voluntary) {
        return transact(
                c -> {
                    SettlementView settlement = owned(c, id, actor);
                    Role actorRole = settlement.role(actor);
                    Role targetRole = settlement.role(target);
                    require(
                            targetRole == Role.CITIZEN || targetRole == Role.GOVERNOR,
                            "Only Citizens or Governors can be removed; an Owner must transfer or"
                                    + " abandon.");
                    if (voluntary)
                        require(actor.equals(target), "You may only leave for yourself.");
                    else
                        require(
                                actorRole == Role.OWNER
                                        || (actorRole == Role.GOVERNOR
                                                && targetRole == Role.CITIZEN),
                                "You do not have permission to remove this player.");
                    c.deleteFrom(SETTLEMENT_MEMBERS)
                            .where(
                                    SETTLEMENT_MEMBERS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(SETTLEMENT_MEMBERS.PLAYER_UUID.eq(target)))
                            .execute();
                    c.update(SETTLEMENT_OFFERS)
                            .set(SETTLEMENT_OFFERS.STATE, "CANCELLED")
                            .where(
                                    SETTLEMENT_OFFERS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(SETTLEMENT_OFFERS.STATE.eq("PENDING"))
                                            .and(
                                                    SETTLEMENT_OFFERS
                                                            .ISSUER_UUID
                                                            .eq(target)
                                                            .or(
                                                                    SETTLEMENT_OFFERS.RECIPIENT_UUID
                                                                            .eq(target))))
                            .execute();
                    event(
                            c,
                            id,
                            actor,
                            voluntary ? "CITIZEN_LEFT" : "CITIZEN_REMOVED",
                            target.toString());
                    return changed(
                            c, id, null, "Player is now a Public of " + settlement.name() + ".");
                });
    }

    public CompletableFuture<SettlementChange> demote(UUID actor, UUID id, UUID target) {
        return transact(
                c -> {
                    SettlementView settlement = owned(c, id, actor);
                    require(
                            settlement.role(actor) == Role.OWNER,
                            "Only the Owner may demote Governors.");
                    require(
                            settlement.role(target) == Role.GOVERNOR,
                            "That player is not a Governor.");
                    c.update(SETTLEMENT_MEMBERS)
                            .set(SETTLEMENT_MEMBERS.ROLE, Role.CITIZEN.name())
                            .where(
                                    SETTLEMENT_MEMBERS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(SETTLEMENT_MEMBERS.PLAYER_UUID.eq(target)))
                            .execute();
                    c.update(SETTLEMENT_OFFERS)
                            .set(SETTLEMENT_OFFERS.STATE, "CANCELLED")
                            .where(
                                    SETTLEMENT_OFFERS
                                            .SETTLEMENT_ID
                                            .eq(id)
                                            .and(SETTLEMENT_OFFERS.ISSUER_UUID.eq(target))
                                            .and(SETTLEMENT_OFFERS.STATE.eq("PENDING")))
                            .execute();
                    event(c, id, actor, "GOVERNOR_DEMOTED", target.toString());
                    return changed(c, id, null, "Governor demoted to Citizen.");
                });
    }

    public CompletableFuture<List<SettlementOffer>> offers(UUID actor) {
        return transact(
                c -> {
                    var o = SETTLEMENT_OFFERS.as("o");
                    var s = SETTLEMENTS.as("s");
                    var m = SETTLEMENT_MEMBERS.as("m");
                    var result = new ArrayList<SettlementOffer>();
                    var managesInvitation =
                            o.TYPE.eq(SettlementOffer.Type.INVITE.name())
                                    .and(
                                            DSL.exists(
                                                    c.selectOne()
                                                            .from(m)
                                                            .where(
                                                                    m.SETTLEMENT_ID
                                                                            .eq(s.SETTLEMENT_ID)
                                                                            .and(
                                                                                    m.PLAYER_UUID
                                                                                            .eq(
                                                                                                    actor))
                                                                            .and(
                                                                                    m.ROLE.eq(
                                                                                            Role
                                                                                                    .GOVERNOR
                                                                                                    .name())))));
                    var visible =
                            o.RECIPIENT_UUID
                                    .eq(actor)
                                    .or(o.ISSUER_UUID.eq(actor))
                                    .or(s.OWNER_UUID.eq(actor))
                                    .or(managesInvitation);
                    for (Record row :
                            c.select(o.fields())
                                    .from(o)
                                    .join(s)
                                    .on(s.SETTLEMENT_ID.eq(o.SETTLEMENT_ID))
                                    .where(
                                            o.STATE
                                                    .eq("PENDING")
                                                    .and(o.EXPIRES_AT.gt(clock.ticks()))
                                                    .and(s.STATUS.eq("OWNED"))
                                                    .and(visible))
                                    .orderBy(o.EXPIRES_AT)
                                    .limit(50)
                                    .fetch()) {
                        result.add(offerFrom(row.into(SETTLEMENT_OFFERS)));
                    }
                    return List.copyOf(result);
                });
    }

    public CompletableFuture<SettlementOffer> inspectOffer(UUID id) {
        return transact(c -> pendingOffer(c, id));
    }

    public CompletableFuture<List<SettlementEvent>> history(UUID settlementId) {
        return transact(
                c ->
                        events(
                                c.selectFrom(SETTLEMENT_EVENTS)
                                        .where(SETTLEMENT_EVENTS.SETTLEMENT_ID.eq(settlementId))
                                        .orderBy(
                                                SETTLEMENT_EVENTS.OCCURRED_AT.desc(),
                                                SQLITE_ROWID.desc())
                                        .limit(50)
                                        .fetch()));
    }

    public CompletableFuture<List<SettlementEvent>> pendingEvents() {
        return transact(
                c ->
                        events(
                                c.selectFrom(SETTLEMENT_EVENTS)
                                        .where(SETTLEMENT_EVENTS.DELIVERED.eq(0L))
                                        .orderBy(SQLITE_ROWID)
                                        .limit(128)
                                        .fetch()));
    }

    public CompletableFuture<Void> acknowledgeEvents(List<UUID> ids) {
        List<UUID> captured = List.copyOf(ids);
        return transact(
                c -> {
                    for (UUID id : captured)
                        c.update(SETTLEMENT_EVENTS)
                                .set(SETTLEMENT_EVENTS.DELIVERED, 1L)
                                .where(SETTLEMENT_EVENTS.EVENT_ID.eq(id))
                                .execute();
                    return null;
                });
    }

    private SettlementChange changed(DSLContext c, UUID id, SettlementOffer offer, String message)
            throws SQLException {
        return new SettlementChange(id, load(c, id), offer, message);
    }

    private void event(DSLContext c, UUID id, UUID actor, String type, String detail)
            throws SQLException {
        c.insertInto(SETTLEMENT_EVENTS)
                .set(SETTLEMENT_EVENTS.EVENT_ID, UUID.randomUUID())
                .set(SETTLEMENT_EVENTS.SETTLEMENT_ID, id)
                .set(SETTLEMENT_EVENTS.ACTOR_UUID, actor)
                .set(SETTLEMENT_EVENTS.TYPE, type)
                .set(SETTLEMENT_EVENTS.DETAIL, detail)
                .set(SETTLEMENT_EVENTS.OCCURRED_AT, Instant.now().toEpochMilli())
                .set(SETTLEMENT_EVENTS.DELIVERED, 0L)
                .set(SETTLEMENT_EVENTS.GAME_TICK, clock.ticks())
                .execute();
        clock.checkpoint(DatabaseAccess.transaction(c));
    }

    private static List<SettlementEvent> events(Iterable<? extends Record> rows) {
        var events = new ArrayList<SettlementEvent>();
        for (Record row : rows)
            events.add(
                    new SettlementEvent(
                            row.get(SETTLEMENT_EVENTS.EVENT_ID),
                            row.get(SETTLEMENT_EVENTS.SETTLEMENT_ID),
                            row.get(SETTLEMENT_EVENTS.ACTOR_UUID),
                            row.get(SETTLEMENT_EVENTS.TYPE),
                            row.get(SETTLEMENT_EVENTS.DETAIL),
                            Instant.ofEpochMilli(row.get(SETTLEMENT_EVENTS.OCCURRED_AT)),
                            row.get(SETTLEMENT_EVENTS.GAME_TICK)));
        return List.copyOf(events);
    }

    private SettlementOffer pendingOffer(DSLContext c, UUID id) throws SQLException {
        Record row =
                c.selectFrom(SETTLEMENT_OFFERS)
                        .where(
                                SETTLEMENT_OFFERS
                                        .OFFER_ID
                                        .eq(id)
                                        .and(SETTLEMENT_OFFERS.STATE.eq("PENDING")))
                        .fetchOne();
        require(row != null, "Offer not found or already resolved.");
        SettlementOffer offer = offerFrom(row);
        require(clock.ticks() < offer.expiresAtTick(), "Offer expired; request a new one.");
        return offer;
    }

    private static SettlementOffer offerFrom(Record row) {
        return new SettlementOffer(
                row.get(SETTLEMENT_OFFERS.OFFER_ID),
                row.get(SETTLEMENT_OFFERS.SETTLEMENT_ID),
                SettlementOffer.Type.valueOf(row.get(SETTLEMENT_OFFERS.TYPE)),
                row.get(SETTLEMENT_OFFERS.ISSUER_UUID),
                row.get(SETTLEMENT_OFFERS.RECIPIENT_UUID),
                row.get(SETTLEMENT_OFFERS.EXPIRES_AT),
                row.get(SETTLEMENT_OFFERS.OWNERSHIP_REVISION),
                row.get(SETTLEMENT_OFFERS.CHUNK_REVISION));
    }

    private static void addCitizen(DSLContext c, UUID id, UUID player, Role role)
            throws SQLException {
        c.insertInto(SETTLEMENT_MEMBERS)
                .set(SETTLEMENT_MEMBERS.SETTLEMENT_ID, id)
                .set(SETTLEMENT_MEMBERS.PLAYER_UUID, player)
                .set(SETTLEMENT_MEMBERS.ROLE, role.name())
                .execute();
    }

    private static void insertChunk(DSLContext c, UUID id, UUID world, ChunkPosition chunk)
            throws SQLException {
        c.insertInto(SETTLEMENT_CHUNKS)
                .set(SETTLEMENT_CHUNKS.WORLD_INSTANCE_ID, world)
                .set(SETTLEMENT_CHUNKS.CHUNK_X, (long) chunk.x())
                .set(SETTLEMENT_CHUNKS.CHUNK_Z, (long) chunk.z())
                .set(SETTLEMENT_CHUNKS.SETTLEMENT_ID, id)
                .execute();
    }

    private static void requireMainWorld(DSLContext c, UUID world) throws SQLException {
        require(
                "MAIN".equals(DatabaseAccess.worldDefinition(DatabaseAccess.transaction(c), world)),
                "Settlements may only claim registered Main World instances.");
    }

    private static void requireUnclaimed(DSLContext c, UUID world, ChunkPosition chunk)
            throws SQLException {
        require(
                !c.fetchExists(
                        c.selectOne()
                                .from(SETTLEMENT_CHUNKS)
                                .where(
                                        SETTLEMENT_CHUNKS
                                                .WORLD_INSTANCE_ID
                                                .eq(world)
                                                .and(SETTLEMENT_CHUNKS.CHUNK_X.eq((long) chunk.x()))
                                                .and(
                                                        SETTLEMENT_CHUNKS.CHUNK_Z.eq(
                                                                (long) chunk.z())))),
                "This chunk is already claimed.");
    }

    private static void requireNameAvailable(DSLContext c, String name, UUID self)
            throws SQLException {
        UUID found =
                c.select(SETTLEMENTS.SETTLEMENT_ID)
                        .from(SETTLEMENTS)
                        .where(
                                SETTLEMENTS
                                        .NAME_KEY
                                        .eq(key(name))
                                        .and(SETTLEMENTS.STATUS.eq("OWNED")))
                        .fetchOne(SETTLEMENTS.SETTLEMENT_ID);
        require(found == null || found.equals(self), "An owned Settlement already uses that name.");
    }

    private SettlementView owned(DSLContext c, UUID id, UUID actor) throws SQLException {
        SettlementView result = load(c, id);
        require(result != null, "Settlement is no longer owned or does not exist.");
        require(
                administrator.test(actor) || !JooqParcelRepository.banned(c, id, actor),
                "You are banned from this Settlement");
        return result;
    }

    static SettlementView load(DSLContext c, UUID id) throws SQLException {
        Record row =
                c.selectFrom(SETTLEMENTS)
                        .where(SETTLEMENTS.SETTLEMENT_ID.eq(id).and(SETTLEMENTS.STATUS.eq("OWNED")))
                        .fetchOne();
        if (row == null) return null;
        var members = new HashMap<UUID, Role>();
        var chunks = new HashSet<ChunkPosition>();
        for (var member :
                c.selectFrom(SETTLEMENT_MEMBERS)
                        .where(SETTLEMENT_MEMBERS.SETTLEMENT_ID.eq(id))
                        .fetch())
            members.put(
                    member.get(SETTLEMENT_MEMBERS.PLAYER_UUID),
                    Role.valueOf(member.get(SETTLEMENT_MEMBERS.ROLE)));
        for (var chunk :
                c.select(SETTLEMENT_CHUNKS.CHUNK_X, SETTLEMENT_CHUNKS.CHUNK_Z)
                        .from(SETTLEMENT_CHUNKS)
                        .where(SETTLEMENT_CHUNKS.SETTLEMENT_ID.eq(id))
                        .fetch())
            chunks.add(
                    new ChunkPosition(
                            Math.toIntExact(chunk.get(SETTLEMENT_CHUNKS.CHUNK_X)),
                            Math.toIntExact(chunk.get(SETTLEMENT_CHUNKS.CHUNK_Z))));
        return viewFrom(row, members, chunks);
    }

    private static List<SettlementView> loadAll(DSLContext c) throws SQLException {
        var members = new HashMap<UUID, Map<UUID, Role>>();
        var chunks = new HashMap<UUID, Set<ChunkPosition>>();
        try (var rows = c.selectFrom(SETTLEMENT_MEMBERS).fetchLazy()) {
            for (var row : rows)
                members.computeIfAbsent(
                                row.get(SETTLEMENT_MEMBERS.SETTLEMENT_ID),
                                ignored -> new HashMap<>())
                        .put(
                                row.get(SETTLEMENT_MEMBERS.PLAYER_UUID),
                                Role.valueOf(row.get(SETTLEMENT_MEMBERS.ROLE)));
        }
        try (var rows = c.selectFrom(SETTLEMENT_CHUNKS).fetchLazy()) {
            for (var row : rows)
                chunks.computeIfAbsent(
                                row.get(SETTLEMENT_CHUNKS.SETTLEMENT_ID),
                                ignored -> new HashSet<>())
                        .add(
                                new ChunkPosition(
                                        Math.toIntExact(row.get(SETTLEMENT_CHUNKS.CHUNK_X)),
                                        Math.toIntExact(row.get(SETTLEMENT_CHUNKS.CHUNK_Z))));
        }
        var result = new ArrayList<SettlementView>();
        try (var rows =
                c.selectFrom(SETTLEMENTS)
                        .where(SETTLEMENTS.STATUS.eq("OWNED"))
                        .orderBy(SETTLEMENTS.NAME_KEY)
                        .fetchLazy()) {
            for (var row : rows) {
                UUID id = row.get(SETTLEMENTS.SETTLEMENT_ID);
                Set<ChunkPosition> claimed = chunks.getOrDefault(id, Set.of());
                if (claimed.isEmpty() || !connected(claimed))
                    throw new SQLException("Invalid persisted Settlement topology: " + id);
                Map<UUID, Role> team = members.getOrDefault(id, Map.of());
                if (team.containsKey(row.get(SETTLEMENTS.OWNER_UUID)))
                    throw new SQLException("Owner is duplicated in membership: " + id);
                result.add(viewFrom(row, team, claimed));
            }
        }
        return List.copyOf(result);
    }

    private static SettlementView viewFrom(
            Record row, Map<UUID, Role> members, Set<ChunkPosition> chunks) {
        return new SettlementView(
                row.get(SETTLEMENTS.SETTLEMENT_ID),
                row.get(SETTLEMENTS.WORLD_INSTANCE_ID),
                row.get(SETTLEMENTS.NAME),
                row.get(SETTLEMENTS.OWNER_UUID),
                Instant.ofEpochMilli(row.get(SETTLEMENTS.CREATED_AT)),
                row.get(SETTLEMENTS.OWNERSHIP_REVISION),
                row.get(SETTLEMENTS.CHUNK_REVISION),
                members,
                chunks);
    }
}
