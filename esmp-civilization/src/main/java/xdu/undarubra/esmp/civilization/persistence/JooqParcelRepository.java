package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;
import static xdu.undarubra.esmp.civilization.settlement.ParcelRules.*;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.*;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.civilization.settlement.ParcelService.Request;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Parcel SQL adapter sharing the Civilization transaction and publication boundary. */
public final class JooqParcelRepository implements ParcelService.Repository {
    private final UnitOfWork database;
    private final GameClock clock;
    private final Predicate<UUID> administrator;

    public JooqParcelRepository(
            UnitOfWork database, GameClock clock, Predicate<UUID> administrator) {
        this.database = database;
        this.clock = clock;
        this.administrator = administrator;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(DSLContext context) throws Exception;
    }

    private <T> CompletableFuture<T> transact(SqlWork<T> work) {
        return database.transact(tx -> work.run(DatabaseAccess.context(tx)));
    }

    public CompletableFuture<LandSnapshot> snapshot() {
        return transact(
                c -> {
                    var result = new HashMap<UUID, LandSnapshot.Land>();
                    for (var row :
                            c.select(SETTLEMENTS.SETTLEMENT_ID)
                                    .from(SETTLEMENTS)
                                    .where(SETTLEMENTS.STATUS.eq("OWNED"))
                                    .fetch()) {
                        UUID id = row.get(SETTLEMENTS.SETTLEMENT_ID);
                        result.put(id, loadLand(c, id));
                    }
                    return new LandSnapshot(result);
                });
    }

    public CompletableFuture<LandSnapshot.Land> land(UUID id) {
        return transact(c -> loadLand(c, id));
    }

    private static LandSnapshot.Land loadLand(DSLContext c, UUID id) throws SQLException {
        var d = SETTLEMENT_DETAILS.as("d");
        var s = SETTLEMENTS.as("s");
        var metadata =
                c.select(d.NUMBER, d.OFFICIAL_NAME, d.CERTIFIED, d.SECURITY_REVISION)
                        .from(d)
                        .join(s)
                        .on(d.SETTLEMENT_ID.eq(s.SETTLEMENT_ID))
                        .where(d.SETTLEMENT_ID.eq(id).and(s.STATUS.eq("OWNED")))
                        .fetchOne();
        if (metadata == null) return null;
        long number = metadata.get(d.NUMBER);
        String official = metadata.get(d.OFFICIAL_NAME);
        boolean certified = metadata.get(d.CERTIFIED) != 0L;
        long security = metadata.get(d.SECURITY_REVISION);
        var bans = new HashSet<UUID>();
        var parcels = new HashMap<UUID, ParcelView>();
        var cells = new HashMap<ParcelCell, ParcelView>();
        for (var row :
                c.select(SETTLEMENT_BANS.PLAYER_UUID)
                        .from(SETTLEMENT_BANS)
                        .where(SETTLEMENT_BANS.SETTLEMENT_ID.eq(id))
                        .fetch()) bans.add(row.get(SETTLEMENT_BANS.PLAYER_UUID));
        for (var row :
                c.select(PARCELS.PARCEL_ID)
                        .from(PARCELS)
                        .where(PARCELS.SETTLEMENT_ID.eq(id))
                        .fetch()) {
            var p = read(c, row.get(PARCELS.PARCEL_ID));
            parcels.put(p.id(), p);
            for (var cell : p.cells()) cells.put(cell, p);
        }
        return new LandSnapshot.Land(number, official, certified, security, bans, parcels, cells);
    }

    public static ParcelView at(Transaction transaction, UUID id, ParcelCell cell)
            throws SQLException {
        return at(DatabaseAccess.context(transaction), id, cell);
    }

    public static boolean banned(Transaction transaction, UUID id, UUID actor) throws SQLException {
        return banned(DatabaseAccess.context(transaction), id, actor);
    }

    public static ParcelView read(Transaction transaction, UUID id) throws SQLException {
        return read(DatabaseAccess.context(transaction), id);
    }

    static ParcelView at(DSLContext c, UUID id, ParcelCell cell) throws SQLException {
        var row =
                c.select(PARCEL_CELLS.PARCEL_ID)
                        .from(PARCEL_CELLS)
                        .where(
                                PARCEL_CELLS
                                        .SETTLEMENT_ID
                                        .eq(id)
                                        .and(PARCEL_CELLS.X.eq((long) cell.x()))
                                        .and(PARCEL_CELLS.Y.eq((long) cell.y()))
                                        .and(PARCEL_CELLS.Z.eq((long) cell.z())))
                        .fetchOne();
        return row == null ? null : read(c, row.get(PARCEL_CELLS.PARCEL_ID));
    }

    static boolean banned(DSLContext c, UUID id, UUID actor) throws SQLException {
        return c.fetchExists(
                c.selectOne()
                        .from(SETTLEMENT_BANS)
                        .where(
                                SETTLEMENT_BANS
                                        .SETTLEMENT_ID
                                        .eq(id)
                                        .and(SETTLEMENT_BANS.PLAYER_UUID.eq(actor))));
    }

    static ParcelView read(DSLContext c, UUID id) throws SQLException {
        var parcel = c.selectFrom(PARCELS).where(PARCELS.PARCEL_ID.eq(id)).fetchOne();
        if (parcel == null) throw new SettlementException("Parcel no longer exists");
        UUID settlement = parcel.get(PARCELS.SETTLEMENT_ID);
        String name = parcel.get(PARCELS.NAME);
        long revision = parcel.get(PARCELS.REVISION);
        var cells = new HashSet<ParcelCell>();
        var holders = new HashSet<UUID>();
        var policy = new EnumMap<ParcelAction, Set<ParcelRelation>>(ParcelAction.class);
        for (var row :
                c.select(PARCEL_CELLS.X, PARCEL_CELLS.Y, PARCEL_CELLS.Z)
                        .from(PARCEL_CELLS)
                        .where(PARCEL_CELLS.PARCEL_ID.eq(id))
                        .fetch())
            cells.add(
                    new ParcelCell(
                            Math.toIntExact(row.get(PARCEL_CELLS.X)),
                            Math.toIntExact(row.get(PARCEL_CELLS.Y)),
                            Math.toIntExact(row.get(PARCEL_CELLS.Z))));
        for (var row :
                c.select(PARCEL_HOLDERS.PLAYER_UUID)
                        .from(PARCEL_HOLDERS)
                        .where(PARCEL_HOLDERS.PARCEL_ID.eq(id))
                        .fetch()) holders.add(row.get(PARCEL_HOLDERS.PLAYER_UUID));
        for (var a : ParcelAction.values()) policy.put(a, EnumSet.noneOf(ParcelRelation.class));
        for (var row :
                c.select(PARCEL_POLICY.ACTION, PARCEL_POLICY.RELATION)
                        .from(PARCEL_POLICY)
                        .where(PARCEL_POLICY.PARCEL_ID.eq(id))
                        .fetch())
            policy.get(ParcelAction.valueOf(row.get(PARCEL_POLICY.ACTION)))
                    .add(ParcelRelation.valueOf(row.get(PARCEL_POLICY.RELATION)));
        return new ParcelView(id, settlement, name, revision, cells, holders, policy);
    }

    public CompletableFuture<SettlementChange> add(
            UUID actor,
            UUID settlement,
            String name,
            Set<ParcelCell> selection,
            int minHeight,
            int maxHeight) {
        String valid = normalize(name);
        Set<ParcelCell> captured = Set.copyOf(selection);
        return transact(
                c -> {
                    var s = owned(c, settlement);
                    ParcelView old = null;
                    var row =
                            c.select(PARCELS.PARCEL_ID)
                                    .from(PARCELS)
                                    .where(
                                            PARCELS.SETTLEMENT_ID
                                                    .eq(settlement)
                                                    .and(
                                                            PARCELS.NAME_KEY.eq(
                                                                    valid.toLowerCase(
                                                                            Locale.ROOT))))
                                    .fetchOne();
                    if (row != null) old = read(c, row.get(PARCELS.PARCEL_ID));
                    var combined = new HashSet<ParcelCell>();
                    if (old != null) combined.addAll(old.cells());
                    require(
                            !captured.isEmpty() && captured.size() <= ParcelCell.LIMIT,
                            "Invalid selection size");
                    for (var cell : captured) {
                        require(
                                (long) cell.y() * 8 >= minHeight
                                        && (long) cell.y() * 8 + 8 <= maxHeight,
                                "Cell exceeds world height");
                        require(
                                s.chunks().contains(cell.chunk()),
                                "Every selected cell must lie inside this Settlement");
                        if (at(c, settlement, cell) == null) combined.add(cell);
                    }
                    require(
                            old == null || combined.size() > old.cells().size(),
                            "No unparcelled cells to add");
                    if (old == null) {
                        require(
                                s.role(actor).canManage() || administrator.test(actor),
                                "Only governance creates Parcels");
                        int count =
                                c.selectCount()
                                        .from(PARCELS)
                                        .where(PARCELS.SETTLEMENT_ID.eq(settlement))
                                        .fetchSingle()
                                        .value1();
                        require(count < 128, "Settlement Parcel limit reached");
                    }
                    var policy = old == null ? defaults() : old.policy();
                    var next =
                            new ParcelView(
                                    old == null ? UUID.randomUUID() : old.id(),
                                    settlement,
                                    valid,
                                    old == null ? 1L : old.revision() + 1L,
                                    combined,
                                    old == null ? Set.of() : old.holders(),
                                    policy);
                    validate(c, s, next);
                    return requestOrApply(c, actor, s, old, next, "GEOMETRY");
                });
    }

    public CompletableFuture<SettlementChange> removeCell(
            UUID actor, UUID settlement, UUID parcel, ParcelCell cell) {
        return transact(
                c -> {
                    var s = owned(c, settlement);
                    var old = read(c, parcel);
                    require(old.settlementId().equals(settlement), "Wrong Settlement");
                    require(old.cells().contains(cell), "Cell not in Parcel");
                    require(
                            cell.neighbors().stream().anyMatch(n -> !old.cells().contains(n)),
                            "Only surface cells may be removed");
                    var cells = new HashSet<>(old.cells());
                    cells.remove(cell);
                    require(!cells.isEmpty(), "Use delete to remove the last cell");
                    var next =
                            new ParcelView(
                                    parcel,
                                    settlement,
                                    old.name(),
                                    old.revision() + 1L,
                                    cells,
                                    old.holders(),
                                    old.policy());
                    validate(c, s, next);
                    return requestOrApply(c, actor, s, old, next, "GEOMETRY");
                });
    }

    public CompletableFuture<SettlementChange> delete(UUID actor, UUID settlement, UUID parcel) {
        return transact(
                c -> {
                    var s = owned(c, settlement);
                    var old = read(c, parcel);
                    require(old.settlementId().equals(settlement), "Wrong Settlement");
                    return requestOrApply(c, actor, s, old, null, "DELETE");
                });
    }

    public CompletableFuture<SettlementChange> holders(
            UUID actor, UUID settlement, UUID parcel, Set<UUID> holders) {
        Set<UUID> captured = Set.copyOf(holders);
        require(captured.size() <= 32, "At most 32 Holders");
        return transact(
                c -> {
                    var s = owned(c, settlement);
                    var old = read(c, parcel);
                    require(old.settlementId().equals(settlement), "Wrong Settlement");
                    return requestOrApply(
                            c,
                            actor,
                            s,
                            old,
                            new ParcelView(
                                    parcel,
                                    settlement,
                                    old.name(),
                                    old.revision() + 1L,
                                    old.cells(),
                                    captured,
                                    old.policy()),
                            "HOLDERS");
                });
    }

    public CompletableFuture<SettlementChange> policy(
            UUID actor,
            UUID settlement,
            UUID parcel,
            ParcelAction action,
            Set<ParcelRelation> relations) {
        var captured = Set.copyOf(relations);
        return transact(
                c -> {
                    var s = owned(c, settlement);
                    var old = read(c, parcel);
                    require(old.settlementId().equals(settlement), "Wrong Settlement");
                    var policy = new EnumMap<ParcelAction, Set<ParcelRelation>>(old.policy());
                    policy.put(action, captured);
                    return requestOrApply(
                            c,
                            actor,
                            s,
                            old,
                            new ParcelView(
                                    parcel,
                                    settlement,
                                    old.name(),
                                    old.revision() + 1L,
                                    old.cells(),
                                    old.holders(),
                                    policy),
                            "POLICY");
                });
    }

    public CompletableFuture<SettlementChange> rename(
            UUID actor, UUID settlement, UUID parcel, String name) {
        String valid = normalize(name);
        return transact(
                c -> {
                    var s = owned(c, settlement);
                    var old = read(c, parcel);
                    require(old.settlementId().equals(settlement), "Wrong Settlement");
                    var next =
                            new ParcelView(
                                    parcel,
                                    settlement,
                                    valid,
                                    old.revision() + 1L,
                                    old.cells(),
                                    old.holders(),
                                    old.policy());
                    validate(c, s, next);
                    return requestOrApply(c, actor, s, old, next, "RENAME");
                });
    }

    private SettlementChange requestOrApply(
            DSLContext c,
            UUID actor,
            SettlementView s,
            ParcelView old,
            ParcelView next,
            String kind)
            throws SQLException {
        require(
                administrator.test(actor) || !banned(c, s.id(), actor),
                "You are banned from this Settlement");
        require(
                administrator.test(actor)
                        || s.role(actor).canManage()
                        || (old != null && old.holders().contains(actor)),
                "Governance or current Holder required");
        var required = new HashSet<UUID>();
        if (old != null) required.addAll(old.holders());
        if (next != null) required.addAll(next.holders());
        if (administrator.test(actor) || (required.isEmpty() && s.role(actor).canManage())) {
            apply(c, actor, s, old, next, kind);
            return change(c, s.id(), parcelMessage(kind, old, next));
        }
        c.update(PARCEL_REQUESTS)
                .set(PARCEL_REQUESTS.STATE, "EXPIRED")
                .where(
                        PARCEL_REQUESTS
                                .STATE
                                .eq("PENDING")
                                .and(PARCEL_REQUESTS.EXPIRES_TICK.le(clock.ticks())))
                .execute();
        int count =
                c.selectCount()
                        .from(PARCEL_REQUESTS)
                        .where(
                                PARCEL_REQUESTS
                                        .SETTLEMENT_ID
                                        .eq(s.id())
                                        .and(PARCEL_REQUESTS.STATE.eq("PENDING")))
                        .fetchSingle()
                        .value1();
        require(count < 64, "Too many pending Parcel proposals");
        UUID request = UUID.randomUUID();
        c.insertInto(PARCEL_REQUESTS)
                .set(PARCEL_REQUESTS.REQUEST_ID, request)
                .set(PARCEL_REQUESTS.SETTLEMENT_ID, s.id())
                .set(PARCEL_REQUESTS.PARCEL_ID, old.id())
                .set(PARCEL_REQUESTS.OPERATION, kind)
                .set(PARCEL_REQUESTS.PAYLOAD, next == null ? "" : encode(next))
                .set(PARCEL_REQUESTS.PARCEL_REVISION, old.revision())
                .set(PARCEL_REQUESTS.OWNERSHIP_REVISION, s.ownershipRevision())
                .set(PARCEL_REQUESTS.CHUNK_REVISION, s.chunkRevision())
                .set(PARCEL_REQUESTS.SECURITY_REVISION, security(c, s.id()))
                .set(PARCEL_REQUESTS.ISSUER, actor.toString())
                .set(PARCEL_REQUESTS.EXPIRES_TICK, Math.addExact(clock.ticks(), 24000))
                .set(PARCEL_REQUESTS.STATE, "PENDING")
                .execute();
        c.insertInto(PARCEL_APPROVALS, PARCEL_APPROVALS.REQUEST_ID, PARCEL_APPROVALS.PLAYER_UUID)
                .values(request, actor)
                .execute();
        event(c, s.id(), actor, "PARCEL_PROPOSED", request + ";" + kind + ";" + old.name());
        return change(
                c,
                s.id(),
                "Approval requested. /settlement parcel approve " + s.id() + " " + request);
    }

    public CompletableFuture<List<Request>> requests(UUID settlement) {
        return transact(
                c -> {
                    var result = new ArrayList<Request>();
                    var rowid = DSL.field(DSL.name("rowid"), Long.class);
                    for (var row :
                            c.selectFrom(PARCEL_REQUESTS)
                                    .where(
                                            PARCEL_REQUESTS
                                                    .SETTLEMENT_ID
                                                    .eq(settlement)
                                                    .and(PARCEL_REQUESTS.STATE.eq("PENDING"))
                                                    .and(
                                                            PARCEL_REQUESTS.EXPIRES_TICK.gt(
                                                                    clock.ticks())))
                                    .orderBy(rowid.asc())
                                    .limit(64)
                                    .fetch()) result.add(request(c, row));
                    return List.copyOf(result);
                });
    }

    private Request request(DSLContext c, Record row) throws SQLException {
        UUID parcel = row.get(PARCEL_REQUESTS.PARCEL_ID);
        var old = read(c, parcel);
        var required = new HashSet<>(old.holders());
        String payload = row.get(PARCEL_REQUESTS.PAYLOAD);
        if (!payload.isEmpty())
            required.addAll(
                    decode(parcel, old.settlementId(), old.revision() + 1L, payload).holders());
        UUID id = row.get(PARCEL_REQUESTS.REQUEST_ID);
        var approvals = new HashSet<UUID>();
        for (var approval :
                c.select(PARCEL_APPROVALS.PLAYER_UUID)
                        .from(PARCEL_APPROVALS)
                        .where(PARCEL_APPROVALS.REQUEST_ID.eq(id))
                        .fetch()) approvals.add(approval.get(PARCEL_APPROVALS.PLAYER_UUID));
        return new Request(
                id,
                old.settlementId(),
                parcel,
                row.get(PARCEL_REQUESTS.OPERATION),
                UUID.fromString(row.get(PARCEL_REQUESTS.ISSUER)),
                row.get(PARCEL_REQUESTS.EXPIRES_TICK),
                Set.copyOf(required),
                Set.copyOf(approvals));
    }

    public CompletableFuture<SettlementChange> resolve(
            UUID actor, UUID settlement, UUID id, boolean approve) {
        return transact(
                c -> {
                    var s = owned(c, settlement);
                    require(
                            administrator.test(actor) || !banned(c, settlement, actor),
                            "You are banned");
                    var row =
                            c.selectFrom(PARCEL_REQUESTS)
                                    .where(
                                            PARCEL_REQUESTS
                                                    .REQUEST_ID
                                                    .eq(id)
                                                    .and(
                                                            PARCEL_REQUESTS.SETTLEMENT_ID.eq(
                                                                    settlement))
                                                    .and(PARCEL_REQUESTS.STATE.eq("PENDING")))
                                    .fetchOne();
                    require(row != null, "Proposal not pending");
                    require(
                            clock.ticks() < row.get(PARCEL_REQUESTS.EXPIRES_TICK),
                            "Proposal expired");
                    var r = request(c, row);
                    var old = read(c, r.parcelId());
                    require(
                            administrator.test(actor)
                                    || s.role(actor).canManage()
                                    || r.requiredHolders().contains(actor)
                                    || r.issuer().equals(actor),
                            "Not a proposal participant");
                    if (!approve) {
                        c.update(PARCEL_REQUESTS)
                                .set(PARCEL_REQUESTS.STATE, "REJECTED")
                                .where(PARCEL_REQUESTS.REQUEST_ID.eq(id))
                                .execute();
                        event(c, settlement, actor, "PARCEL_REJECTED", id.toString());
                        return change(c, settlement, "Proposal rejected");
                    }
                    require(
                            old.revision() == row.get(PARCEL_REQUESTS.PARCEL_REVISION)
                                    && s.ownershipRevision()
                                            == row.get(PARCEL_REQUESTS.OWNERSHIP_REVISION)
                                    && s.chunkRevision() == row.get(PARCEL_REQUESTS.CHUNK_REVISION)
                                    && security(c, settlement)
                                            == row.get(PARCEL_REQUESTS.SECURITY_REVISION),
                            "Proposal is stale; request a fresh preview");
                    c.insertInto(
                                    PARCEL_APPROVALS,
                                    PARCEL_APPROVALS.REQUEST_ID,
                                    PARCEL_APPROVALS.PLAYER_UUID)
                            .values(id, actor)
                            .onDuplicateKeyIgnore()
                            .execute();
                    var approvals = new HashSet<>(r.approvals());
                    approvals.add(actor);
                    boolean governance = false;
                    for (var who : approvals)
                        if (s.role(who).canManage() && !banned(c, settlement, who))
                            governance = true;
                    boolean holders = approvals.containsAll(r.requiredHolders());
                    for (var who : r.requiredHolders())
                        if (banned(c, settlement, who) && !administrator.test(who)) holders = false;
                    if (administrator.test(actor) || (governance && holders)) {
                        String payload = row.get(PARCEL_REQUESTS.PAYLOAD);
                        ParcelView next =
                                payload.isEmpty()
                                        ? null
                                        : decode(
                                                old.id(), settlement, old.revision() + 1L, payload);
                        if (next != null) validate(c, s, next);
                        c.update(PARCEL_REQUESTS)
                                .set(PARCEL_REQUESTS.STATE, "APPLIED")
                                .where(PARCEL_REQUESTS.REQUEST_ID.eq(id))
                                .execute();
                        apply(c, actor, s, old, next, r.operation());
                        return change(c, settlement, "Changes approved.");
                    }
                    event(c, settlement, actor, "PARCEL_APPROVED", id.toString());
                    return change(c, settlement, "Approval saved. Waiting for the others.");
                });
    }

    private void apply(
            DSLContext c,
            UUID actor,
            SettlementView s,
            ParcelView old,
            ParcelView next,
            String kind)
            throws SQLException {
        UUID id = old == null ? next.id() : old.id();
        if (next == null) {
            c.deleteFrom(PARCELS).where(PARCELS.PARCEL_ID.eq(id)).execute();
        } else {
            validate(c, s, next);
            c.insertInto(
                            PARCELS,
                            PARCELS.PARCEL_ID,
                            PARCELS.SETTLEMENT_ID,
                            PARCELS.NAME,
                            PARCELS.NAME_KEY,
                            PARCELS.REVISION)
                    .values(
                            id,
                            s.id(),
                            next.name(),
                            next.name().toLowerCase(Locale.ROOT),
                            next.revision())
                    .onConflict(PARCELS.PARCEL_ID)
                    .doUpdate()
                    .set(PARCELS.NAME, next.name())
                    .set(PARCELS.NAME_KEY, next.name().toLowerCase(Locale.ROOT))
                    .set(PARCELS.REVISION, next.revision())
                    .execute();
            c.deleteFrom(PARCEL_CELLS).where(PARCEL_CELLS.PARCEL_ID.eq(id)).execute();
            c.deleteFrom(PARCEL_HOLDERS).where(PARCEL_HOLDERS.PARCEL_ID.eq(id)).execute();
            c.deleteFrom(PARCEL_POLICY).where(PARCEL_POLICY.PARCEL_ID.eq(id)).execute();
            for (var cell : next.cells())
                c.insertInto(
                                PARCEL_CELLS,
                                PARCEL_CELLS.PARCEL_ID,
                                PARCEL_CELLS.SETTLEMENT_ID,
                                PARCEL_CELLS.X,
                                PARCEL_CELLS.Y,
                                PARCEL_CELLS.Z)
                        .values(id, s.id(), (long) cell.x(), (long) cell.y(), (long) cell.z())
                        .execute();
            for (var holder : next.holders())
                c.insertInto(PARCEL_HOLDERS, PARCEL_HOLDERS.PARCEL_ID, PARCEL_HOLDERS.PLAYER_UUID)
                        .values(id, holder)
                        .execute();
            for (var a : ParcelAction.values())
                for (var relation : next.policy().get(a))
                    c.insertInto(
                                    PARCEL_POLICY,
                                    PARCEL_POLICY.PARCEL_ID,
                                    PARCEL_POLICY.ACTION,
                                    PARCEL_POLICY.RELATION)
                            .values(id, a.name(), relation.name())
                            .execute();
        }
        c.update(SETTLEMENT_DETAILS)
                .set(
                        SETTLEMENT_DETAILS.SECURITY_REVISION,
                        SETTLEMENT_DETAILS.SECURITY_REVISION.add(1L))
                .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(s.id()))
                .execute();
        c.update(PARCEL_REQUESTS)
                .set(PARCEL_REQUESTS.STATE, "STALE")
                .where(
                        PARCEL_REQUESTS
                                .SETTLEMENT_ID
                                .eq(s.id())
                                .and(PARCEL_REQUESTS.STATE.eq("PENDING")))
                .execute();
        event(
                c,
                s.id(),
                actor,
                "PARCEL_" + kind,
                (administrator.test(actor) ? "admin;" : "")
                        + id
                        + ";"
                        + (next == null ? "deleted" : next.name()));
    }

    private static void validate(DSLContext c, SettlementView s, ParcelView next)
            throws SQLException {
        require(
                next.cells().size() <= ParcelCell.LIMIT && ParcelCell.connected(next.cells()),
                "Parcel must be nonempty, six-face connected, and at most 4096 cells");
        require(
                !c.fetchExists(
                        c.selectOne()
                                .from(PARCELS)
                                .where(
                                        PARCELS.SETTLEMENT_ID
                                                .eq(s.id())
                                                .and(
                                                        PARCELS.NAME_KEY.eq(
                                                                next.name()
                                                                        .toLowerCase(Locale.ROOT)))
                                                .and(PARCELS.PARCEL_ID.ne(next.id())))),
                "Parcel name already exists");
        for (var cell : next.cells()) {
            require(s.chunks().contains(cell.chunk()), "Parcel cell outside Settlement");
            var row =
                    c.select(PARCEL_CELLS.PARCEL_ID)
                            .from(PARCEL_CELLS)
                            .where(
                                    PARCEL_CELLS
                                            .SETTLEMENT_ID
                                            .eq(s.id())
                                            .and(PARCEL_CELLS.X.eq((long) cell.x()))
                                            .and(PARCEL_CELLS.Y.eq((long) cell.y()))
                                            .and(PARCEL_CELLS.Z.eq((long) cell.z())))
                            .fetchOne();
            require(
                    row == null || next.id().equals(row.get(PARCEL_CELLS.PARCEL_ID)),
                    "Parcel cells overlap");
        }
    }

    static long security(DSLContext c, UUID id) throws SQLException {
        var row =
                c.select(SETTLEMENT_DETAILS.SECURITY_REVISION)
                        .from(SETTLEMENT_DETAILS)
                        .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                        .fetchOne();
        if (row == null) throw new SQLException("Missing Settlement metadata");
        return row.get(SETTLEMENT_DETAILS.SECURITY_REVISION);
    }

    private static SettlementView owned(DSLContext c, UUID id) throws SQLException {
        var s = JooqSettlementRepository.load(c, id);
        require(s != null, "Settlement no longer owned");
        return s;
    }

    private static SettlementChange change(DSLContext c, UUID id, String message)
            throws SQLException {
        return new SettlementChange(id, JooqSettlementRepository.load(c, id), null, message);
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
}
