package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.*;

import org.jooq.DSLContext;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.api.GovernanceQueries;
import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.building.BuildingService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.*;
import xdu.undarubra.esmp.core.time.GameClock;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class JooqBuildingRepository implements BuildingService.Repository {
    private final UnitOfWork db;
    private final GameClock clock;
    private final Policy policy;
    private final Predicate<UUID> admins;

    public JooqBuildingRepository(
            UnitOfWork db, GameClock clock, Policy policy, Predicate<UUID> admins) {
        this.db = db;
        this.clock = clock;
        this.policy = policy;
        this.admins = admins;
    }

    @Override
    public CompletableFuture<Boolean> recorded(UUID operation, UUID actor, UUID building) {
        return db.transact(
                tx -> {
                    var r =
                            DatabaseAccess.context(tx)
                                    .selectFrom(BUILDING_OPERATIONS)
                                    .where(BUILDING_OPERATIONS.OPERATION_ID.eq(operation))
                                    .fetchOne();
                    if (r == null) return false;
                    require(
                            r.get(BUILDING_OPERATIONS.ACTOR_UUID).equals(actor)
                                    && r.get(BUILDING_OPERATIONS.BUILDING_ID).equals(building),
                            "Building journal identity changed");
                    return true;
                });
    }

    @Override
    public CompletableFuture<List<View>> active(UUID settlement) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var condition = BUILDINGS.STATUS.eq("ACTIVE");
                    if (settlement != null)
                        condition = condition.and(BUILDINGS.SETTLEMENT_ID.eq(settlement));
                    return c.selectFrom(BUILDINGS)
                            .where(condition)
                            .orderBy(BUILDINGS.BUILDING_ID)
                            .fetch(JooqBuildingRepository::view);
                });
    }

    @Override
    public CompletableFuture<Detail> inspect(UUID id, long revision) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var v = current(c, id);
                    long wanted = revision == 0 ? v.revision() : revision;
                    var r = BUILDING_REVISIONS;
                    var row =
                            c.selectFrom(r)
                                    .where(r.BUILDING_ID.eq(id).and(r.REVISION.eq(wanted)))
                                    .fetchOne();
                    require(row != null, "Building revision not found");
                    var b = BuildingBaseline.decode(row.get(r.BASELINE));
                    var point =
                            row.get(r.STATION_X) == null
                                    ? null
                                    : new Position(
                                            Math.toIntExact(row.get(r.STATION_X)),
                                            Math.toIntExact(row.get(r.STATION_Y)),
                                            Math.toIntExact(row.get(r.STATION_Z)));
                    return new Detail(
                            new View(
                                    v.id(),
                                    v.settlement(),
                                    v.world(),
                                    v.name(),
                                    b.bounds(),
                                    wanted,
                                    row.get(r.KIND).equals("RETIRED"),
                                    point),
                            b,
                            b.geometry());
                });
    }

    @Override
    public CompletableFuture<List<Revision>> history(UUID id) {
        return db.transact(
                tx -> {
                    var r = BUILDING_REVISIONS;
                    return DatabaseAccess.context(tx)
                            .select(r.REVISION, r.ACTOR_UUID, r.GAME_TICK, r.KIND)
                            .from(r)
                            .where(r.BUILDING_ID.eq(id))
                            .orderBy(r.REVISION.desc())
                            .limit(50)
                            .fetch(
                                    row ->
                                            new Revision(
                                                    row.get(r.REVISION),
                                                    row.get(r.ACTOR_UUID),
                                                    row.get(r.GAME_TICK),
                                                    row.get(r.KIND)));
                });
    }

    @Override
    public CompletableFuture<SettlementChange> register(
            UUID actor,
            UUID id,
            UUID settlement,
            UUID world,
            String rawName,
            BuildingBaseline scan,
            Stamp stamp) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var s = authorized(tx, actor, settlement, stamp, true);
                    require(s.worldInstanceId().equals(world), "Building world instance changed");
                    validateVolume(tx, c, actor, s, scan, null);
                    String name = normalize(rawName);
                    require(
                            !c.fetchExists(
                                    c.selectOne()
                                            .from(BUILDINGS)
                                            .where(
                                                    BUILDINGS
                                                            .SETTLEMENT_ID
                                                            .eq(settlement)
                                                            .and(BUILDINGS.STATUS.eq("ACTIVE"))
                                                            .and(
                                                                    BUILDINGS.NAME_KEY.eq(
                                                                            name.toLowerCase(
                                                                                    Locale
                                                                                            .ROOT))))),
                            "An active Building already has that name");
                    require(
                            scan.cells().stream().anyMatch(cell -> !cell.air()),
                            "Building selection contains only air");
                    var b = scan.bounds();
                    var t = BUILDINGS;
                    c.insertInto(t)
                            .set(t.BUILDING_ID, id)
                            .set(t.SETTLEMENT_ID, settlement)
                            .set(t.WORLD_INSTANCE_ID, world)
                            .set(t.NAME, name)
                            .set(t.NAME_KEY, name.toLowerCase(Locale.ROOT))
                            .set(t.MIN_X, (long) b.minX())
                            .set(t.MIN_Y, (long) b.minY())
                            .set(t.MIN_Z, (long) b.minZ())
                            .set(t.MAX_X, (long) b.maxX())
                            .set(t.MAX_Y, (long) b.maxY())
                            .set(t.MAX_Z, (long) b.maxZ())
                            .set(t.REVISION, 1L)
                            .set(t.STATUS, "ACTIVE")
                            .set(t.CREATED_TICK, clock.ticks())
                            .execute();
                    record(tx, c, actor, id, 1, "REGISTERED", scan);
                    return change(c, settlement, "Registered Building " + name + " (" + id + ").");
                });
    }

    @Override
    public CompletableFuture<SettlementChange> resize(
            UUID actor, UUID id, long expected, BuildingBaseline scan, Stamp stamp) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var v = live(c, id, expected);
                    var s = authorized(tx, actor, v.settlement(), stamp, true);
                    validateVolume(tx, c, actor, s, scan, id);
                    validatePermission(tx, actor, s, v.bounds());
                    var b = scan.bounds();
                    c.update(BUILDINGS)
                            .set(BUILDINGS.MIN_X, (long) b.minX())
                            .set(BUILDINGS.MIN_Y, (long) b.minY())
                            .set(BUILDINGS.MIN_Z, (long) b.minZ())
                            .set(BUILDINGS.MAX_X, (long) b.maxX())
                            .set(BUILDINGS.MAX_Y, (long) b.maxY())
                            .set(BUILDINGS.MAX_Z, (long) b.maxZ())
                            .where(BUILDINGS.BUILDING_ID.eq(id))
                            .execute();
                    clearInvalidStation(c, v, scan);
                    long next = Math.addExact(expected, 1);
                    advance(c, id, next);
                    record(tx, c, actor, id, next, "RESIZED", scan);
                    return change(c, v.settlement(), "Building bounds and baseline updated.");
                });
    }

    @Override
    public CompletableFuture<SettlementChange> retire(UUID actor, UUID id, long expected) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var v = live(c, id, expected);
                    var s = authorized(tx, actor, v.settlement(), null, true);
                    validatePermission(tx, actor, s, v.bounds());
                    long next = Math.addExact(expected, 1);
                    c.update(BUILDINGS)
                            .set(BUILDINGS.STATUS, "RETIRED")
                            .set(BUILDINGS.REVISION, next)
                            .where(BUILDINGS.BUILDING_ID.eq(id))
                            .execute();
                    record(tx, c, actor, id, next, "RETIRED", baseline(c, id, expected));
                    return change(
                            c,
                            v.settlement(),
                            "Building retired; its baseline and history remain.");
                });
    }

    @Override
    public CompletableFuture<SettlementChange> station(
            UUID actor, UUID id, long expected, Position point) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var v = live(c, id, expected);
                    var s = authorized(tx, actor, v.settlement(), null, true);
                    var base = baseline(c, id, expected);
                    if (point != null) {
                        require(v.bounds().contains(point), "Station must be inside the Building");
                        require(
                                base.at(point).data().equals("minecraft:crafting_table")
                                        && !base.at(point).blockEntity(),
                                "Station must be a captured crafting table");
                        allow(tx, actor, s, point, ParcelAction.BUILD);
                    }
                    c.update(BUILDINGS)
                            .set(BUILDINGS.STATION_X, point == null ? null : (long) point.x())
                            .set(BUILDINGS.STATION_Y, point == null ? null : (long) point.y())
                            .set(BUILDINGS.STATION_Z, point == null ? null : (long) point.z())
                            .where(BUILDINGS.BUILDING_ID.eq(id))
                            .execute();
                    long next = Math.addExact(expected, 1);
                    advance(c, id, next);
                    record(tx, c, actor, id, next, "STATION", base);
                    return change(
                            c,
                            v.settlement(),
                            point == null
                                    ? "Station marker cleared."
                                    : "Repair station location recorded.");
                });
    }

    @Override
    public CompletableFuture<SettlementChange> edit(
            UUID operation,
            UUID actor,
            UUID id,
            Stamp stamp,
            ParcelAction action,
            Map<Position, BuildingCell> changes) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    String fingerprint = fingerprint(id, actor, stamp, action, changes);
                    var receipt =
                            c.selectFrom(BUILDING_OPERATIONS)
                                    .where(BUILDING_OPERATIONS.OPERATION_ID.eq(operation))
                                    .fetchOne();
                    var v = current(c, id);
                    if (receipt != null) {
                        require(
                                receipt.get(BUILDING_OPERATIONS.FINGERPRINT).equals(fingerprint),
                                "Building edit identity has different content");
                        return change(c, v.settlement(), "Building edit already recorded.");
                    }
                    require(!v.retired(), "Building is retired");
                    require(
                            action == ParcelAction.BUILD || action == ParcelAction.INTERACT,
                            "Unsupported Building edit action");
                    var s = authorized(tx, actor, v.settlement(), stamp, false);
                    var old = baseline(c, id, v.revision());
                    var actual = new HashMap<Position, BuildingCell>();
                    boolean resolvedDamage = false;
                    for (var entry : changes.entrySet()) {
                        require(
                                v.bounds().contains(entry.getKey()),
                                "Building bounds changed before edit publication");
                        allow(tx, actor, s, entry.getKey(), action);
                        var position = entry.getKey();
                        resolvedDamage |=
                                c.deleteFrom(DECAY_DAMAGE)
                                                .where(
                                                        DECAY_DAMAGE
                                                                .BUILDING_ID
                                                                .eq(id)
                                                                .and(
                                                                        DECAY_DAMAGE.X.eq(
                                                                                (long)
                                                                                        position
                                                                                                .x()))
                                                                .and(
                                                                        DECAY_DAMAGE.Y.eq(
                                                                                (long)
                                                                                        position
                                                                                                .y()))
                                                                .and(
                                                                        DECAY_DAMAGE.Z.eq(
                                                                                (long)
                                                                                        position
                                                                                                .z())))
                                                .execute()
                                        > 0;
                        if (!old.at(entry.getKey()).equals(entry.getValue()))
                            actual.put(entry.getKey(), entry.getValue());
                    }
                    c.insertInto(BUILDING_OPERATIONS)
                            .set(BUILDING_OPERATIONS.OPERATION_ID, operation)
                            .set(BUILDING_OPERATIONS.BUILDING_ID, id)
                            .set(BUILDING_OPERATIONS.ACTOR_UUID, actor)
                            .set(BUILDING_OPERATIONS.FINGERPRINT, fingerprint)
                            .set(BUILDING_OPERATIONS.GAME_TICK, clock.ticks())
                            .execute();
                    if (!actual.isEmpty() || resolvedDamage) {
                        var nextBase = actual.isEmpty() ? old : old.patch(actual);
                        clearInvalidStation(c, v, nextBase);
                        long next = Math.addExact(v.revision(), 1);
                        advance(c, id, next);
                        record(tx, c, actor, id, next, "EDITED", nextBase);
                    } else clock.checkpoint(tx);
                    return change(c, v.settlement(), "Building baseline updated.");
                });
    }

    private SettlementView authorized(
            UnitOfWork.Transaction tx, UUID actor, UUID id, Stamp stamp, boolean governance)
            throws SQLException {
        var c = DatabaseAccess.context(tx);
        var s = JooqSettlementRepository.load(c, id);
        require(s != null, "Settlement is no longer owned");
        Objects.requireNonNull(actor);
        boolean admin = admins.test(actor);
        require(
                admin || !GovernanceQueries.banned(tx, id, actor),
                "Player is banned from this Settlement");
        if (governance)
            require(
                    admin || s.role(actor) == Role.OWNER || s.role(actor) == Role.GOVERNOR,
                    "Owner or Governor required");
        if (stamp != null) {
            long security =
                    c.select(SETTLEMENT_DETAILS.SECURITY_REVISION)
                            .from(SETTLEMENT_DETAILS)
                            .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                            .fetchSingle(SETTLEMENT_DETAILS.SECURITY_REVISION);
            require(
                    stamp.world().equals(s.worldInstanceId())
                            && stamp.ownership() == s.ownershipRevision()
                            && stamp.claims() == s.chunkRevision()
                            && stamp.security() == security,
                    "Building authorization changed; retry with current state");
        }
        return s;
    }

    private void validateVolume(
            UnitOfWork.Transaction tx,
            DSLContext c,
            UUID actor,
            SettlementView s,
            BuildingBaseline scan,
            UUID except)
            throws SQLException {
        require(
                scan.bounds().volume() <= policy.maxVolume(),
                "Building exceeds configured volume limit");
        for (var chunk : scan.bounds().footprint().keySet())
            require(
                    s.chunks().contains(chunk),
                    "Every Building chunk must belong to this Settlement");
        for (var row :
                c.selectFrom(BUILDINGS)
                        .where(
                                BUILDINGS
                                        .SETTLEMENT_ID
                                        .eq(s.id())
                                        .and(BUILDINGS.STATUS.eq("ACTIVE")))
                        .fetch()) {
            var other = view(row);
            require(
                    other.id().equals(except) || !scan.bounds().intersects(other.bounds()),
                    "Building selections cannot overlap");
        }
        validatePermission(tx, actor, s, scan.bounds());
    }

    private void validatePermission(
            UnitOfWork.Transaction tx, UUID actor, SettlementView s, BuildingBounds b)
            throws SQLException {
        for (int x = Math.floorDiv(b.minX(), 8); x <= Math.floorDiv(b.maxX(), 8); x++)
            for (int y = Math.floorDiv(b.minY(), 8); y <= Math.floorDiv(b.maxY(), 8); y++)
                for (int z = Math.floorDiv(b.minZ(), 8); z <= Math.floorDiv(b.maxZ(), 8); z++)
                    allow(
                            tx,
                            actor,
                            s,
                            new Position(
                                    Math.max(b.minX(), x * 8),
                                    Math.max(b.minY(), y * 8),
                                    Math.max(b.minZ(), z * 8)),
                            ParcelAction.BUILD);
    }

    private void allow(
            UnitOfWork.Transaction tx,
            UUID actor,
            SettlementView s,
            Position p,
            ParcelAction action)
            throws SQLException {
        require(
                s.chunks()
                        .contains(
                                new ChunkPosition(
                                        Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16))),
                "Building Claim no longer exists");
        if (admins.test(actor)) return;
        var parcel = GovernanceQueries.at(tx, s.id(), ParcelCell.atBlock(p.x(), p.y(), p.z()));
        require(
                parcel != null
                        ? parcel.allows(actor, s.role(actor), action)
                        : s.role(actor).canBuild(),
                "Building edit is not permitted here");
    }

    static View current(DSLContext c, UUID id) {
        var r = c.selectFrom(BUILDINGS).where(BUILDINGS.BUILDING_ID.eq(id)).fetchOne();
        require(r != null, "Building not found");
        return view(r);
    }

    private static View live(DSLContext c, UUID id, long expected) {
        var v = current(c, id);
        require(
                !v.retired() && v.revision() == expected,
                "Building is retired or changed; inspect it again");
        return v;
    }

    private static View view(Record r) {
        var b = BUILDINGS;
        Position station =
                r.get(b.STATION_X) == null
                        ? null
                        : new Position(
                                Math.toIntExact(r.get(b.STATION_X)),
                                Math.toIntExact(r.get(b.STATION_Y)),
                                Math.toIntExact(r.get(b.STATION_Z)));
        return new View(
                r.get(b.BUILDING_ID),
                r.get(b.SETTLEMENT_ID),
                r.get(b.WORLD_INSTANCE_ID),
                r.get(b.NAME),
                new BuildingBounds(
                        Math.toIntExact(r.get(b.MIN_X)),
                        Math.toIntExact(r.get(b.MIN_Y)),
                        Math.toIntExact(r.get(b.MIN_Z)),
                        Math.toIntExact(r.get(b.MAX_X)),
                        Math.toIntExact(r.get(b.MAX_Y)),
                        Math.toIntExact(r.get(b.MAX_Z))),
                r.get(b.REVISION),
                r.get(b.STATUS).equals("RETIRED"),
                station);
    }

    static BuildingBaseline baseline(DSLContext c, UUID id, long revision) {
        byte[] bytes =
                c.select(BUILDING_REVISIONS.BASELINE)
                        .from(BUILDING_REVISIONS)
                        .where(
                                BUILDING_REVISIONS
                                        .BUILDING_ID
                                        .eq(id)
                                        .and(BUILDING_REVISIONS.REVISION.eq(revision)))
                        .fetchOne(BUILDING_REVISIONS.BASELINE);
        require(bytes != null, "Building revision not found");
        return BuildingBaseline.decode(bytes);
    }

    private static void advance(DSLContext c, UUID id, long revision) {
        c.update(BUILDINGS)
                .set(BUILDINGS.REVISION, revision)
                .where(BUILDINGS.BUILDING_ID.eq(id))
                .execute();
    }

    private static void clearInvalidStation(DSLContext c, View v, BuildingBaseline next) {
        if (v.station() != null
                && (!next.bounds().contains(v.station())
                        || !next.at(v.station()).data().equals("minecraft:crafting_table")))
            c.update(BUILDINGS)
                    .setNull(BUILDINGS.STATION_X)
                    .setNull(BUILDINGS.STATION_Y)
                    .setNull(BUILDINGS.STATION_Z)
                    .where(BUILDINGS.BUILDING_ID.eq(v.id()))
                    .execute();
    }

    private void record(
            UnitOfWork.Transaction tx,
            DSLContext c,
            UUID actor,
            UUID id,
            long revision,
            String kind,
            BuildingBaseline baseline)
            throws SQLException {
        long tick = clock.ticks();
        var v = current(c, id);
        var point = v.station();
        c.insertInto(BUILDING_REVISIONS)
                .set(BUILDING_REVISIONS.BUILDING_ID, id)
                .set(BUILDING_REVISIONS.REVISION, revision)
                .set(BUILDING_REVISIONS.ACTOR_UUID, actor)
                .set(BUILDING_REVISIONS.GAME_TICK, tick)
                .set(BUILDING_REVISIONS.KIND, kind)
                .set(BUILDING_REVISIONS.STATION_X, point == null ? null : (long) point.x())
                .set(BUILDING_REVISIONS.STATION_Y, point == null ? null : (long) point.y())
                .set(BUILDING_REVISIONS.STATION_Z, point == null ? null : (long) point.z())
                .set(BUILDING_REVISIONS.BASELINE, baseline.encode())
                .execute();
        JooqDecayRepository.rebase(c, id, baseline, tick);
        c.insertInto(SETTLEMENT_EVENTS)
                .set(SETTLEMENT_EVENTS.EVENT_ID, UUID.randomUUID())
                .set(SETTLEMENT_EVENTS.SETTLEMENT_ID, v.settlement())
                .set(SETTLEMENT_EVENTS.ACTOR_UUID, actor)
                .set(SETTLEMENT_EVENTS.TYPE, "BUILDING_" + kind)
                .set(
                        SETTLEMENT_EVENTS.DETAIL,
                        "building="
                                + id
                                + ";revision="
                                + revision
                                + ";station="
                                + v.station()
                                + ";administrator="
                                + admins.test(actor))
                .set(SETTLEMENT_EVENTS.OCCURRED_AT, Instant.now().toEpochMilli())
                .set(SETTLEMENT_EVENTS.GAME_TICK, tick)
                .set(SETTLEMENT_EVENTS.DELIVERED, 0L)
                .execute();
        clock.checkpoint(tx);
    }

    private static SettlementChange change(DSLContext c, UUID id, String message)
            throws SQLException {
        return new SettlementChange(id, JooqSettlementRepository.load(c, id), null, message);
    }

    private static String fingerprint(
            UUID id,
            UUID actor,
            Stamp stamp,
            ParcelAction action,
            Map<Position, BuildingCell> changes) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeUTF(id.toString());
                out.writeUTF(actor.toString());
                out.writeInt(1);
                out.writeUTF(stamp.world().toString());
                out.writeLong(stamp.ownership());
                out.writeLong(stamp.claims());
                out.writeLong(stamp.security());
                out.writeUTF(action.name());
                for (var p :
                        changes.keySet().stream()
                                .sorted(
                                        Comparator.comparingInt(Position::x)
                                                .thenComparingInt(Position::y)
                                                .thenComparingInt(Position::z))
                                .toList()) {
                    out.writeInt(p.x());
                    out.writeInt(p.y());
                    out.writeInt(p.z());
                    BuildingBaseline.writeCell(out, changes.get(p));
                }
            }
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
