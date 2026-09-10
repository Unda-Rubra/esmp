package xdu.undarubra.esmp.civilization.settlement;

import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Parcel transactions share the owning Civilization database and publication boundary. */
public final class ParcelService {
    private final WorldStore database;
    private final GameClock clock;
    private final Predicate<UUID> administrator;

    public record Request(
            UUID id,
            UUID settlementId,
            UUID parcelId,
            String operation,
            UUID issuer,
            long expiresTick,
            Set<UUID> requiredHolders,
            Set<UUID> approvals) {}

    public ParcelService(WorldStore database, GameClock clock, Predicate<UUID> administrator) {
        this.database = database;
        this.clock = clock;
        this.administrator = administrator;
    }

    static void migrate(Connection c) throws SQLException {
        sql(
                c,
                "CREATE TABLE settlement_bans(settlement_id TEXT NOT NULL REFERENCES"
                        + " settlements(settlement_id),player_uuid TEXT NOT NULL,banned_by TEXT NOT"
                        + " NULL,created_tick INTEGER NOT NULL,PRIMARY"
                        + " KEY(settlement_id,player_uuid))");
        sql(
                c,
                "CREATE TABLE parcels(parcel_id TEXT PRIMARY KEY,settlement_id TEXT NOT NULL"
                    + " REFERENCES settlements(settlement_id),name TEXT NOT NULL,name_key TEXT NOT"
                    + " NULL,revision INTEGER NOT NULL,UNIQUE(settlement_id,name_key))");
        sql(
                c,
                "CREATE TABLE parcel_cells(parcel_id TEXT NOT NULL REFERENCES parcels(parcel_id) ON"
                    + " DELETE CASCADE,settlement_id TEXT NOT NULL REFERENCES"
                    + " settlements(settlement_id),x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER"
                    + " NOT NULL,PRIMARY KEY(settlement_id,x,y,z))");
        sql(c, "CREATE INDEX parcel_cells_owner ON parcel_cells(parcel_id)");
        sql(
                c,
                "CREATE TABLE parcel_holders(parcel_id TEXT NOT NULL REFERENCES parcels(parcel_id)"
                        + " ON DELETE CASCADE,player_uuid TEXT NOT NULL,PRIMARY"
                        + " KEY(parcel_id,player_uuid))");
        sql(
                c,
                "CREATE TABLE parcel_policy(parcel_id TEXT NOT NULL REFERENCES parcels(parcel_id)"
                        + " ON DELETE CASCADE,action TEXT NOT NULL,relation TEXT NOT NULL,PRIMARY"
                        + " KEY(parcel_id,action,relation))");
        sql(
                c,
                "CREATE TABLE parcel_requests(request_id TEXT PRIMARY KEY,settlement_id TEXT NOT"
                        + " NULL,parcel_id TEXT NOT NULL,operation TEXT NOT NULL,payload TEXT NOT"
                        + " NULL,parcel_revision INTEGER NOT NULL,ownership_revision INTEGER NOT"
                        + " NULL,chunk_revision INTEGER NOT NULL,security_revision INTEGER NOT"
                        + " NULL,issuer TEXT NOT NULL,expires_tick INTEGER NOT NULL,state TEXT NOT"
                        + " NULL)");
        sql(
                c,
                "CREATE TABLE parcel_approvals(request_id TEXT NOT NULL REFERENCES"
                        + " parcel_requests(request_id),player_uuid TEXT NOT NULL,PRIMARY"
                        + " KEY(request_id,player_uuid))");
    }

    public CompletableFuture<LandSnapshot> snapshot() {
        return database.transact(
                c -> {
                    var result = new HashMap<UUID, LandSnapshot.Land>();
                    try (var q = c.createStatement();
                            var rows =
                                    q.executeQuery(
                                            "SELECT settlement_id FROM settlements WHERE"
                                                    + " status='OWNED'")) {
                        while (rows.next()) {
                            UUID id = UUID.fromString(rows.getString(1));
                            result.put(id, loadLand(c, id));
                        }
                    }
                    return new LandSnapshot(result);
                });
    }

    public CompletableFuture<LandSnapshot.Land> land(UUID id) {
        return database.transact(c -> loadLand(c, id));
    }

    private static LandSnapshot.Land loadLand(Connection c, UUID id) throws SQLException {
        long number, security;
        String official;
        boolean certified;
        try (var q =
                        prepare(
                                c,
                                "SELECT d.* FROM settlement_details d JOIN settlements s"
                                        + " USING(settlement_id) WHERE d.settlement_id=? AND"
                                        + " s.status='OWNED'",
                                id);
                var r = q.executeQuery()) {
            if (!r.next()) return null;
            number = r.getLong("number");
            official = r.getString("official_name");
            certified = r.getInt("certified") != 0;
            security = r.getLong("security_revision");
        }
        var bans = new HashSet<UUID>();
        var parcels = new HashMap<UUID, ParcelView>();
        var cells = new HashMap<ParcelCell, ParcelView>();
        try (var q =
                        prepare(
                                c,
                                "SELECT player_uuid FROM settlement_bans WHERE settlement_id=?",
                                id);
                var r = q.executeQuery()) {
            while (r.next()) bans.add(UUID.fromString(r.getString(1)));
        }
        try (var q = prepare(c, "SELECT parcel_id FROM parcels WHERE settlement_id=?", id);
                var r = q.executeQuery()) {
            while (r.next()) {
                var p = read(c, UUID.fromString(r.getString(1)));
                parcels.put(p.id(), p);
                for (var cell : p.cells()) cells.put(cell, p);
            }
        }
        return new LandSnapshot.Land(number, official, certified, security, bans, parcels, cells);
    }

    public static ParcelView at(Connection c, UUID id, ParcelCell cell) throws SQLException {
        try (var q =
                        prepare(
                                c,
                                "SELECT parcel_id FROM parcel_cells WHERE settlement_id=? AND x=?"
                                        + " AND y=? AND z=?",
                                id,
                                cell.x(),
                                cell.y(),
                                cell.z());
                var r = q.executeQuery()) {
            return r.next() ? read(c, UUID.fromString(r.getString(1))) : null;
        }
    }

    public static boolean banned(Connection c, UUID id, UUID actor) throws SQLException {
        try (var q =
                        prepare(
                                c,
                                "SELECT 1 FROM settlement_bans WHERE settlement_id=? AND"
                                        + " player_uuid=?",
                                id,
                                actor);
                var r = q.executeQuery()) {
            return r.next();
        }
    }

    public static ParcelView read(Connection c, UUID id) throws SQLException {
        UUID settlement;
        String name;
        long revision;
        try (var q = prepare(c, "SELECT * FROM parcels WHERE parcel_id=?", id);
                var r = q.executeQuery()) {
            if (!r.next()) throw new SettlementException("Parcel no longer exists");
            settlement = UUID.fromString(r.getString("settlement_id"));
            name = r.getString("name");
            revision = r.getLong("revision");
        }
        var cells = new HashSet<ParcelCell>();
        var holders = new HashSet<UUID>();
        var policy = new EnumMap<ParcelAction, Set<ParcelRelation>>(ParcelAction.class);
        try (var q = prepare(c, "SELECT x,y,z FROM parcel_cells WHERE parcel_id=?", id);
                var r = q.executeQuery()) {
            while (r.next()) cells.add(new ParcelCell(r.getInt(1), r.getInt(2), r.getInt(3)));
        }
        try (var q = prepare(c, "SELECT player_uuid FROM parcel_holders WHERE parcel_id=?", id);
                var r = q.executeQuery()) {
            while (r.next()) holders.add(UUID.fromString(r.getString(1)));
        }
        for (var a : ParcelAction.values()) policy.put(a, EnumSet.noneOf(ParcelRelation.class));
        try (var q = prepare(c, "SELECT action,relation FROM parcel_policy WHERE parcel_id=?", id);
                var r = q.executeQuery()) {
            while (r.next())
                policy.get(ParcelAction.valueOf(r.getString(1)))
                        .add(ParcelRelation.valueOf(r.getString(2)));
        }
        return new ParcelView(id, settlement, name, revision, cells, holders, policy);
    }

    public CompletableFuture<SettlementChange> add(
            UUID actor,
            UUID settlement,
            String name,
            Set<ParcelCell> selection,
            int minHeight,
            int maxHeight) {
        String valid = name(name);
        Set<ParcelCell> captured = Set.copyOf(selection);
        return database.transact(
                c -> {
                    var s = owned(c, settlement);
                    ParcelView old = null;
                    try (var q =
                                    prepare(
                                            c,
                                            "SELECT parcel_id FROM parcels WHERE settlement_id=?"
                                                    + " AND name_key=?",
                                            settlement,
                                            valid.toLowerCase(Locale.ROOT));
                            var r = q.executeQuery()) {
                        if (r.next()) old = read(c, UUID.fromString(r.getString(1)));
                    }
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
                        try (var q =
                                        prepare(
                                                c,
                                                "SELECT count(*) FROM parcels WHERE"
                                                        + " settlement_id=?",
                                                settlement);
                                var r = q.executeQuery()) {
                            require(
                                    r.next() && r.getInt(1) < 128,
                                    "Settlement Parcel limit reached");
                        }
                    }
                    var policy = old == null ? defaults() : old.policy();
                    var next =
                            new ParcelView(
                                    old == null ? UUID.randomUUID() : old.id(),
                                    settlement,
                                    valid,
                                    old == null ? 1 : old.revision() + 1,
                                    combined,
                                    old == null ? Set.of() : old.holders(),
                                    policy);
                    validate(c, s, next);
                    return requestOrApply(c, actor, s, old, next, "GEOMETRY");
                });
    }

    public CompletableFuture<SettlementChange> removeCell(
            UUID actor, UUID settlement, UUID parcel, ParcelCell cell) {
        return database.transact(
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
                                    old.revision() + 1,
                                    cells,
                                    old.holders(),
                                    old.policy());
                    validate(c, s, next);
                    return requestOrApply(c, actor, s, old, next, "GEOMETRY");
                });
    }

    public CompletableFuture<SettlementChange> delete(UUID actor, UUID settlement, UUID parcel) {
        return database.transact(
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
        return database.transact(
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
                                    old.revision() + 1,
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
        return database.transact(
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
                                    old.revision() + 1,
                                    old.cells(),
                                    old.holders(),
                                    policy),
                            "POLICY");
                });
    }

    public CompletableFuture<SettlementChange> rename(
            UUID actor, UUID settlement, UUID parcel, String name) {
        String valid = name(name);
        return database.transact(
                c -> {
                    var s = owned(c, settlement);
                    var old = read(c, parcel);
                    require(old.settlementId().equals(settlement), "Wrong Settlement");
                    var next =
                            new ParcelView(
                                    parcel,
                                    settlement,
                                    valid,
                                    old.revision() + 1,
                                    old.cells(),
                                    old.holders(),
                                    old.policy());
                    validate(c, s, next);
                    return requestOrApply(c, actor, s, old, next, "RENAME");
                });
    }

    private SettlementChange requestOrApply(
            Connection c,
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
            return change(
                    c,
                    s.id(),
                    "Parcel "
                            + kind
                            + " committed"
                            + (administrator.test(actor) ? " (administrator override)" : ""));
        }
        sql(
                c,
                "UPDATE parcel_requests SET state='EXPIRED' WHERE state='PENDING' AND"
                        + " expires_tick<=?",
                clock.ticks());
        try (var q =
                        prepare(
                                c,
                                "SELECT count(*) FROM parcel_requests WHERE settlement_id=? AND"
                                        + " state='PENDING'",
                                s.id());
                var r = q.executeQuery()) {
            require(r.next() && r.getInt(1) < 64, "Too many pending Parcel proposals");
        }
        UUID request = UUID.randomUUID();
        sql(
                c,
                "INSERT INTO parcel_requests VALUES(?,?,?,?,?,?,?,?,?,?,?,'PENDING')",
                request,
                s.id(),
                old.id(),
                kind,
                next == null ? "" : encode(next),
                old.revision(),
                s.ownershipRevision(),
                s.chunkRevision(),
                security(c, s.id()),
                actor,
                Math.addExact(clock.ticks(), 24000));
        sql(c, "INSERT INTO parcel_approvals VALUES(?,?)", request, actor);
        event(c, s.id(), actor, "PARCEL_PROPOSED", request + ";" + kind + ";" + old.name());
        return change(
                c,
                s.id(),
                "Proposal "
                        + request
                        + " requires governance and all current/new Holders; use /settlement parcel"
                        + " approve "
                        + s.name()
                        + " "
                        + request);
    }

    public CompletableFuture<List<Request>> requests(UUID settlement) {
        return database.transact(
                c -> {
                    var result = new ArrayList<Request>();
                    try (var q =
                                    prepare(
                                            c,
                                            "SELECT * FROM parcel_requests WHERE settlement_id=?"
                                                + " AND state='PENDING' AND expires_tick>? ORDER BY"
                                                + " rowid LIMIT 64",
                                            settlement,
                                            clock.ticks());
                            var rows = q.executeQuery()) {
                        while (rows.next()) result.add(request(c, rows));
                    }
                    return List.copyOf(result);
                });
    }

    private Request request(Connection c, ResultSet row) throws SQLException {
        UUID parcel = UUID.fromString(row.getString("parcel_id"));
        var old = read(c, parcel);
        var required = new HashSet<>(old.holders());
        String payload = row.getString("payload");
        if (!payload.isEmpty())
            required.addAll(
                    decode(parcel, old.settlementId(), old.revision() + 1, payload).holders());
        UUID id = UUID.fromString(row.getString("request_id"));
        var approvals = new HashSet<UUID>();
        try (var q = prepare(c, "SELECT player_uuid FROM parcel_approvals WHERE request_id=?", id);
                var r = q.executeQuery()) {
            while (r.next()) approvals.add(UUID.fromString(r.getString(1)));
        }
        return new Request(
                id,
                old.settlementId(),
                parcel,
                row.getString("operation"),
                UUID.fromString(row.getString("issuer")),
                row.getLong("expires_tick"),
                Set.copyOf(required),
                Set.copyOf(approvals));
    }

    public CompletableFuture<SettlementChange> resolve(
            UUID actor, UUID settlement, UUID id, boolean approve) {
        return database.transact(
                c -> {
                    var s = owned(c, settlement);
                    require(
                            administrator.test(actor) || !banned(c, settlement, actor),
                            "You are banned");
                    try (var q =
                                    prepare(
                                            c,
                                            "SELECT * FROM parcel_requests WHERE request_id=? AND"
                                                    + " settlement_id=? AND state='PENDING'",
                                            id,
                                            settlement);
                            var row = q.executeQuery()) {
                        require(row.next(), "Proposal not pending");
                        require(clock.ticks() < row.getLong("expires_tick"), "Proposal expired");
                        var r = request(c, row);
                        var old = read(c, r.parcelId());
                        require(
                                administrator.test(actor)
                                        || s.role(actor).canManage()
                                        || r.requiredHolders().contains(actor)
                                        || r.issuer().equals(actor),
                                "Not a proposal participant");
                        if (!approve) {
                            sql(
                                    c,
                                    "UPDATE parcel_requests SET state='REJECTED' WHERE"
                                            + " request_id=?",
                                    id);
                            event(c, settlement, actor, "PARCEL_REJECTED", id.toString());
                            return change(c, settlement, "Proposal rejected");
                        }
                        require(
                                old.revision() == row.getLong("parcel_revision")
                                        && s.ownershipRevision()
                                                == row.getLong("ownership_revision")
                                        && s.chunkRevision() == row.getLong("chunk_revision")
                                        && security(c, settlement)
                                                == row.getLong("security_revision"),
                                "Proposal is stale; request a fresh preview");
                        sql(c, "INSERT OR IGNORE INTO parcel_approvals VALUES(?,?)", id, actor);
                        var approvals = new HashSet<>(r.approvals());
                        approvals.add(actor);
                        boolean governance = false;
                        for (var who : approvals)
                            if (s.role(who).canManage() && !banned(c, settlement, who))
                                governance = true;
                        boolean holders = approvals.containsAll(r.requiredHolders());
                        for (var who : r.requiredHolders())
                            if (banned(c, settlement, who) && !administrator.test(who))
                                holders = false;
                        if (administrator.test(actor) || (governance && holders)) {
                            String payload = row.getString("payload");
                            ParcelView next =
                                    payload.isEmpty()
                                            ? null
                                            : decode(
                                                    old.id(),
                                                    settlement,
                                                    old.revision() + 1,
                                                    payload);
                            if (next != null) validate(c, s, next);
                            sql(
                                    c,
                                    "UPDATE parcel_requests SET state='APPLIED' WHERE request_id=?",
                                    id);
                            apply(c, actor, s, old, next, r.operation());
                            return change(
                                    c,
                                    settlement,
                                    "Proposal applied"
                                            + (administrator.test(actor)
                                                    ? " (administrator override)"
                                                    : ""));
                        }
                        event(c, settlement, actor, "PARCEL_APPROVED", id.toString());
                        return change(
                                c,
                                settlement,
                                "Approval recorded; awaiting governance/all Holders");
                    }
                });
    }

    private void apply(
            Connection c,
            UUID actor,
            SettlementView s,
            ParcelView old,
            ParcelView next,
            String kind)
            throws SQLException {
        UUID id = old == null ? next.id() : old.id();
        if (next == null) sql(c, "DELETE FROM parcels WHERE parcel_id=?", id);
        else {
            validate(c, s, next);
            sql(
                    c,
                    "INSERT INTO parcels VALUES(?,?,?,?,?) ON CONFLICT(parcel_id) DO UPDATE SET"
                        + " name=excluded.name,name_key=excluded.name_key,revision=excluded.revision",
                    id,
                    s.id(),
                    next.name(),
                    next.name().toLowerCase(Locale.ROOT),
                    next.revision());
            sql(c, "DELETE FROM parcel_cells WHERE parcel_id=?", id);
            sql(c, "DELETE FROM parcel_holders WHERE parcel_id=?", id);
            sql(c, "DELETE FROM parcel_policy WHERE parcel_id=?", id);
            for (var cell : next.cells())
                sql(
                        c,
                        "INSERT INTO parcel_cells VALUES(?,?,?,?,?)",
                        id,
                        s.id(),
                        cell.x(),
                        cell.y(),
                        cell.z());
            for (var holder : next.holders())
                sql(c, "INSERT INTO parcel_holders VALUES(?,?)", id, holder);
            for (var a : ParcelAction.values())
                for (var relation : next.policy().get(a))
                    sql(c, "INSERT INTO parcel_policy VALUES(?,?,?)", id, a, relation);
        }
        sql(
                c,
                "UPDATE settlement_details SET security_revision=security_revision+1 WHERE"
                        + " settlement_id=?",
                s.id());
        sql(
                c,
                "UPDATE parcel_requests SET state='STALE' WHERE settlement_id=? AND"
                        + " state='PENDING'",
                s.id());
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

    private static void validate(Connection c, SettlementView s, ParcelView next)
            throws SQLException {
        require(
                next.cells().size() <= ParcelCell.LIMIT && ParcelCell.connected(next.cells()),
                "Parcel must be nonempty, six-face connected, and at most 4096 cells");
        try (var q =
                        prepare(
                                c,
                                "SELECT 1 FROM parcels WHERE settlement_id=? AND name_key=? AND"
                                        + " parcel_id<>?",
                                s.id(),
                                next.name().toLowerCase(Locale.ROOT),
                                next.id());
                var r = q.executeQuery()) {
            require(!r.next(), "Parcel name already exists");
        }
        for (var cell : next.cells()) {
            require(s.chunks().contains(cell.chunk()), "Parcel cell outside Settlement");
            try (var q =
                            prepare(
                                    c,
                                    "SELECT parcel_id FROM parcel_cells WHERE settlement_id=? AND"
                                            + " x=? AND y=? AND z=?",
                                    s.id(),
                                    cell.x(),
                                    cell.y(),
                                    cell.z());
                    var r = q.executeQuery()) {
                require(
                        !r.next() || next.id().toString().equals(r.getString(1)),
                        "Parcel cells overlap");
            }
        }
    }

    private static Map<ParcelAction, Set<ParcelRelation>> defaults() {
        var p = new EnumMap<ParcelAction, Set<ParcelRelation>>(ParcelAction.class);
        for (var a : ParcelAction.values())
            p.put(
                    a,
                    EnumSet.of(
                            ParcelRelation.OWNER,
                            ParcelRelation.GOVERNOR,
                            ParcelRelation.CITIZEN,
                            ParcelRelation.HOLDER));
        return p;
    }

    private static String name(String name) {
        require(
                name != null && name.matches("[\\p{L}\\p{N}_-]{1,32}"),
                "Use 1-32 letters/digits/underscore/hyphen for Parcel name");
        return name;
    }

    private static String encode(ParcelView p) {
        var parts = new ArrayList<String>();
        parts.add(p.name());
        parts.add(
                p.cells().stream()
                        .map(c -> c.x() + "," + c.y() + "," + c.z())
                        .sorted()
                        .collect(Collectors.joining(";")));
        parts.add(
                p.holders().stream().map(UUID::toString).sorted().collect(Collectors.joining(";")));
        for (var a : ParcelAction.values())
            parts.add(
                    p.policy().get(a).stream()
                            .map(Enum::name)
                            .sorted()
                            .collect(Collectors.joining(",")));
        return String.join("\n", parts);
    }

    private static ParcelView decode(UUID id, UUID settlement, long revision, String payload) {
        var parts = payload.split("\n", -1);
        require(parts.length == 7, "Invalid persisted proposal");
        var cells = new HashSet<ParcelCell>();
        for (var text : parts[1].split(";")) {
            var xyz = text.split(",");
            cells.add(
                    new ParcelCell(
                            Integer.parseInt(xyz[0]),
                            Integer.parseInt(xyz[1]),
                            Integer.parseInt(xyz[2])));
        }
        var holders = new HashSet<UUID>();
        if (!parts[2].isEmpty())
            for (var text : parts[2].split(";")) holders.add(UUID.fromString(text));
        var policy = new EnumMap<ParcelAction, Set<ParcelRelation>>(ParcelAction.class);
        int i = 3;
        for (var a : ParcelAction.values()) {
            var allowed = EnumSet.noneOf(ParcelRelation.class);
            if (!parts[i].isEmpty())
                for (var text : parts[i].split(",")) allowed.add(ParcelRelation.valueOf(text));
            policy.put(a, allowed);
            i++;
        }
        return new ParcelView(id, settlement, name(parts[0]), revision, cells, holders, policy);
    }

    static long security(Connection c, UUID id) throws SQLException {
        try (var q =
                        prepare(
                                c,
                                "SELECT security_revision FROM settlement_details WHERE"
                                        + " settlement_id=?",
                                id);
                var r = q.executeQuery()) {
            if (!r.next()) throw new SQLException("Missing Settlement metadata");
            return r.getLong(1);
        }
    }

    private static SettlementView owned(Connection c, UUID id) throws SQLException {
        var s = SettlementService.load(c, id);
        require(s != null, "Settlement no longer owned");
        return s;
    }

    private static SettlementChange change(Connection c, UUID id, String message)
            throws SQLException {
        return new SettlementChange(id, SettlementService.load(c, id), null, message);
    }

    private void event(Connection c, UUID id, UUID actor, String type, String detail)
            throws SQLException {
        sql(
                c,
                "INSERT INTO settlement_events VALUES(?,?,?,?,?,?,0,?)",
                UUID.randomUUID(),
                id,
                actor,
                type,
                detail,
                Instant.now().toEpochMilli(),
                clock.ticks());
        clock.checkpoint(c);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new SettlementException(message);
    }

    static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
        var q = c.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++)
                q.setObject(
                        i + 1,
                        args[i] instanceof UUID || args[i] instanceof Enum<?>
                                ? args[i].toString()
                                : args[i]);
            return q;
        } catch (SQLException failure) {
            q.close();
            throw failure;
        }
    }

    static void sql(Connection c, String sql, Object... args) throws SQLException {
        try (var q = prepare(c, sql, args)) {
            q.executeUpdate();
        }
    }
}
