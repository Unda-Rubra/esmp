package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.jooq.DSLContext;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.cycle.CycleService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class JooqCycleRepository implements Repository {
    private static final int MAX_CLAIMS = 1024;
    private final UnitOfWork db;
    private final GameClock clock;
    private final Policy policy;
    private final LocalActivityPolicy localPolicy;
    private final JooqLocalActivity local;
    private final JooqBuildingRepository buildings;
    private UUID cursor;

    public JooqCycleRepository(
            UnitOfWork db,
            GameClock clock,
            ActivityPolicy activity,
            LocalActivityPolicy local,
            Policy policy) {
        this.db = db;
        this.clock = clock;
        this.policy = policy;
        this.localPolicy = local;
        this.local = new JooqLocalActivity(activity, local);
        buildings =
                new JooqBuildingRepository(
                        db,
                        clock,
                        new BuildingService.Policy(BuildingBounds.HARD_MAX_VOLUME),
                        p -> false);
    }

    public CompletableFuture<List<UUID>> due() {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var ids =
                            c.select(SETTLEMENTS.SETTLEMENT_ID)
                                    .from(SETTLEMENTS)
                                    .where(SETTLEMENTS.STATUS.eq("OWNED"))
                                    .and(
                                            cursor == null
                                                    ? org.jooq.impl.DSL.noCondition()
                                                    : SETTLEMENTS.SETTLEMENT_ID.gt(cursor))
                                    .orderBy(SETTLEMENTS.SETTLEMENT_ID)
                                    .limit(1)
                                    .fetch(SETTLEMENTS.SETTLEMENT_ID);
                    cursor = ids.isEmpty() ? null : ids.getFirst();
                    return ids;
                });
    }

    public CompletableFuture<Notice> status(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var r =
                            c.selectFrom(CYCLE_NOTICES)
                                    .where(CYCLE_NOTICES.SETTLEMENT_ID.eq(id))
                                    .fetchOne();
                    if (r != null)
                        return new Notice(
                                id,
                                r.get(CYCLE_NOTICES.DUE_TICK),
                                decode(r.get(CYCLE_NOTICES.CHUNKS)).size(),
                                "NOTICE",
                                r.get(CYCLE_NOTICES.CHUNKS));
                    return new Notice(
                            id,
                            0,
                            0,
                            c.fetchExists(CYCLE_TERMINAL, CYCLE_TERMINAL.SETTLEMENT_ID.eq(id))
                                    ? "ENDED"
                                    : "MONITORING",
                            "");
                });
    }

    public CompletableFuture<List<Notice>> notices(UUID actor) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    return c.select(CYCLE_NOTICES.fields())
                            .from(CYCLE_NOTICES)
                            .join(SETTLEMENTS)
                            .on(SETTLEMENTS.SETTLEMENT_ID.eq(CYCLE_NOTICES.SETTLEMENT_ID))
                            .where(
                                    SETTLEMENTS
                                            .OWNER_UUID
                                            .eq(actor)
                                            .or(
                                                    org.jooq.impl.DSL.exists(
                                                            c.selectOne()
                                                                    .from(SETTLEMENT_MEMBERS)
                                                                    .where(
                                                                            SETTLEMENT_MEMBERS
                                                                                    .SETTLEMENT_ID
                                                                                    .eq(
                                                                                            SETTLEMENTS
                                                                                                    .SETTLEMENT_ID))
                                                                    .and(
                                                                            SETTLEMENT_MEMBERS
                                                                                    .PLAYER_UUID.eq(
                                                                                    actor)))))
                            .limit(64)
                            .fetch(
                                    r ->
                                            new Notice(
                                                    r.get(CYCLE_NOTICES.SETTLEMENT_ID),
                                                    r.get(CYCLE_NOTICES.DUE_TICK),
                                                    decode(r.get(CYCLE_NOTICES.CHUNKS)).size(),
                                                    "NOTICE",
                                                    r.get(CYCLE_NOTICES.CHUNKS)));
                });
    }

    public CompletableFuture<SettlementChange> advance(UUID id) {
        return db.transact(
                tx -> {
                    if (!clock.advancing()) return null;
                    var c = DatabaseAccess.context(tx);
                    var s = JooqSettlementRepository.load(c, id);
                    if (s == null || s.chunks().isEmpty()) return null;
                    var old =
                            c.selectFrom(CYCLE_NOTICES)
                                    .where(CYCLE_NOTICES.SETTLEMENT_ID.eq(id))
                                    .fetchOne();
                    var candidate =
                            candidate(
                                    c,
                                    s,
                                    old == null
                                            ? null
                                            : decode(old.get(CYCLE_NOTICES.CHUNKS)).stream()
                                                    .min(
                                                            Comparator.comparingInt(
                                                                            ChunkPosition::x)
                                                                    .thenComparingInt(
                                                                            ChunkPosition::z))
                                                    .orElseThrow());
                    if (candidate == null) {
                        if (old == null) return null;
                        c.deleteFrom(CYCLE_NOTICES)
                                .where(CYCLE_NOTICES.SETTLEMENT_ID.eq(id))
                                .execute();
                        return event(
                                c,
                                s,
                                "CYCLE_NOTICE_CANCELLED",
                                "Release notice cancelled; land remains protected.");
                    }
                    String fingerprint =
                            candidate.fingerprint()
                                    + ";policy="
                                    + policy
                                    + ";support="
                                    + localPolicy;
                    if (old == null || !fingerprint.equals(old.get(CYCLE_NOTICES.FINGERPRINT))) {
                        long due = Math.addExact(clock.ticks(), policy.graceTicks());
                        c.insertInto(CYCLE_NOTICES)
                                .set(CYCLE_NOTICES.SETTLEMENT_ID, id)
                                .set(CYCLE_NOTICES.FINGERPRINT, fingerprint)
                                .set(CYCLE_NOTICES.CHUNKS, encode(candidate.chunks()))
                                .set(CYCLE_NOTICES.DUE_TICK, due)
                                .onConflict(CYCLE_NOTICES.SETTLEMENT_ID)
                                .doUpdate()
                                .set(CYCLE_NOTICES.FINGERPRINT, fingerprint)
                                .set(CYCLE_NOTICES.CHUNKS, encode(candidate.chunks()))
                                .set(CYCLE_NOTICES.DUE_TICK, due)
                                .execute();
                        return event(
                                c,
                                s,
                                "CYCLE_NOTICE_STARTED",
                                "Land release notice: "
                                        + encode(candidate.chunks())
                                        + "; due game tick "
                                        + due
                                        + ". Move stored items or restore local activity.");
                    }
                    if (clock.ticks() < old.get(CYCLE_NOTICES.DUE_TICK)) return null;
                    // The candidate is reconstructed in this same transaction; stale
                    // rights/geometry never apply.
                    buildings.release(c, s.owner(), id, candidate.chunks());
                    for (UUID parcel : candidate.parcels()) archiveParcel(c, id, parcel);
                    for (var chunk : candidate.chunks()) {
                        c.deleteFrom(SETTLEMENT_LOCAL_ACTIVITY)
                                .where(SETTLEMENT_LOCAL_ACTIVITY.SETTLEMENT_ID.eq(id))
                                .and(SETTLEMENT_LOCAL_ACTIVITY.CHUNK_X.eq((long) chunk.x()))
                                .and(SETTLEMENT_LOCAL_ACTIVITY.CHUNK_Z.eq((long) chunk.z()))
                                .execute();
                        c.deleteFrom(SETTLEMENT_CHUNKS)
                                .where(SETTLEMENT_CHUNKS.SETTLEMENT_ID.eq(id))
                                .and(SETTLEMENT_CHUNKS.CHUNK_X.eq((long) chunk.x()))
                                .and(SETTLEMENT_CHUNKS.CHUNK_Z.eq((long) chunk.z()))
                                .execute();
                    }
                    boolean ended = candidate.chunks().size() == s.chunks().size();
                    c.update(SETTLEMENTS)
                            .set(SETTLEMENTS.CHUNK_REVISION, SETTLEMENTS.CHUNK_REVISION.add(1L))
                            .where(SETTLEMENTS.SETTLEMENT_ID.eq(id))
                            .execute();
                    if (ended) {
                        c.insertInto(CYCLE_TERMINAL)
                                .set(CYCLE_TERMINAL.SETTLEMENT_ID, id)
                                .set(CYCLE_TERMINAL.OWNER_UUID, s.owner())
                                .set(CYCLE_TERMINAL.TERMINAL_TICK, clock.ticks())
                                .execute();
                        c.deleteFrom(SETTLEMENT_MEMBERS)
                                .where(SETTLEMENT_MEMBERS.SETTLEMENT_ID.eq(id))
                                .execute();
                        c.update(SETTLEMENTS)
                                .setNull(SETTLEMENTS.OWNER_UUID)
                                .set(SETTLEMENTS.STATUS, "RELEASED")
                                .set(
                                        SETTLEMENTS.OWNERSHIP_REVISION,
                                        SETTLEMENTS.OWNERSHIP_REVISION.add(1L))
                                .where(SETTLEMENTS.SETTLEMENT_ID.eq(id))
                                .execute();
                        c.update(SETTLEMENT_OFFERS)
                                .set(SETTLEMENT_OFFERS.STATE, "CANCELLED")
                                .where(SETTLEMENT_OFFERS.SETTLEMENT_ID.eq(id))
                                .and(SETTLEMENT_OFFERS.STATE.eq("PENDING"))
                                .execute();
                        c.update(SETTLEMENT_ACQUISITIONS)
                                .set(SETTLEMENT_ACQUISITIONS.STATE, "CANCELLED")
                                .where(SETTLEMENT_ACQUISITIONS.SETTLEMENT_ID.eq(id))
                                .and(SETTLEMENT_ACQUISITIONS.STATE.in("PROPOSED", "ACCEPTED"))
                                .execute();
                        c.update(SETTLEMENT_CERTIFICATION_APPLICATIONS)
                                .set(SETTLEMENT_CERTIFICATION_APPLICATIONS.STATE, "CANCELLED")
                                .where(SETTLEMENT_CERTIFICATION_APPLICATIONS.SETTLEMENT_ID.eq(id))
                                .and(SETTLEMENT_CERTIFICATION_APPLICATIONS.STATE.eq("PENDING"))
                                .execute();
                        c.update(PARCEL_REQUESTS)
                                .set(PARCEL_REQUESTS.STATE, "CANCELLED")
                                .where(PARCEL_REQUESTS.SETTLEMENT_ID.eq(id))
                                .and(PARCEL_REQUESTS.STATE.eq("PENDING"))
                                .execute();
                    }
                    c.deleteFrom(CYCLE_NOTICES).where(CYCLE_NOTICES.SETTLEMENT_ID.eq(id)).execute();
                    return event(
                            c,
                            s,
                            ended ? "CYCLE_TERMINATED" : "CYCLE_RELEASED",
                            ended
                                    ? "Settlement "
                                            + id
                                            + " land released; history and collection rights"
                                            + " remain. Last Owner: /esmp treasury "
                                            + id
                                            + " withdraw <amount> [transaction-id]"
                                    : "Released "
                                            + candidate.chunks().size()
                                            + " peripheral chunks.");
                });
    }

    private record Candidate(Set<ChunkPosition> chunks, Set<UUID> parcels, String fingerprint) {}

    private Candidate candidate(DSLContext c, SettlementView s, ChunkPosition preferred)
            throws SQLException {
        if (s.chunks().size() > MAX_CLAIMS) return null;
        var support = local.view(c, s.id(), clock.ticks()).support();
        var allBuildings =
                c.select(BUILDINGS.BUILDING_ID)
                        .from(BUILDINGS)
                        .where(BUILDINGS.SETTLEMENT_ID.eq(s.id()))
                        .and(BUILDINGS.STATUS.eq("ACTIVE"))
                        .orderBy(BUILDINGS.BUILDING_ID)
                        .limit(1025)
                        .fetch(BUILDINGS.BUILDING_ID);
        if (allBuildings.size() > 1024) return null;
        var footprint = new LinkedHashMap<UUID, Set<ChunkPosition>>();
        var signature =
                new StringBuilder()
                        .append(s.ownershipRevision())
                        .append(':')
                        .append(s.chunkRevision());
        for (var id : allBuildings) {
            var v = JooqBuildingRepository.current(c, id);
            footprint.put(id, v.bounds().footprint().keySet());
            signature.append(';').append(id).append(':').append(v.revision());
        }
        var parcelFootprint = new LinkedHashMap<UUID, Set<ChunkPosition>>();
        var cells =
                c.selectFrom(PARCEL_CELLS)
                        .where(PARCEL_CELLS.SETTLEMENT_ID.eq(s.id()))
                        .limit(32769)
                        .fetch();
        if (cells.size() > 32768) return null;
        for (var r : cells)
            parcelFootprint
                    .computeIfAbsent(r.get(PARCEL_CELLS.PARCEL_ID), k -> new HashSet<>())
                    .add(
                            new ChunkPosition(
                                    Math.toIntExact(Math.floorDiv(r.get(PARCEL_CELLS.X), 2)),
                                    Math.toIntExact(Math.floorDiv(r.get(PARCEL_CELLS.Z), 2))));
        var holders =
                new HashSet<>(
                        c.select(PARCEL_HOLDERS.PARCEL_ID)
                                .from(PARCEL_HOLDERS)
                                .join(PARCELS)
                                .on(PARCELS.PARCEL_ID.eq(PARCEL_HOLDERS.PARCEL_ID))
                                .where(PARCELS.SETTLEMENT_ID.eq(s.id()))
                                .fetch(PARCEL_HOLDERS.PARCEL_ID));
        for (var r :
                c.selectFrom(PARCELS)
                        .where(PARCELS.SETTLEMENT_ID.eq(s.id()))
                        .orderBy(PARCELS.PARCEL_ID)
                        .fetch())
            signature
                    .append(';')
                    .append(r.get(PARCELS.PARCEL_ID))
                    .append(':')
                    .append(r.get(PARCELS.REVISION));
        var seeds =
                new ArrayList<>(
                        s.chunks().stream()
                                .sorted(
                                        Comparator.comparingInt(ChunkPosition::x)
                                                .thenComparingInt(ChunkPosition::z))
                                .toList());
        Collections.rotate(
                seeds, -Math.floorMod(clock.ticks() / policy.checkTicks(), seeds.size()));
        if (preferred != null) {
            seeds.remove(preferred);
            seeds.addFirst(preferred);
        }
        var checkedRuins = new HashMap<UUID, Boolean>();
        for (var seed : seeds.subList(0, Math.min(16, seeds.size()))) {
            if (support.getOrDefault(seed, 0.0) >= localPolicy.supportThreshold()) continue;
            var group = new HashSet<ChunkPosition>();
            group.add(seed);
            boolean changed;
            do {
                changed = false;
                for (var set : footprint.values())
                    if (!Collections.disjoint(group, set)) changed |= group.addAll(set);
                for (var set : parcelFootprint.values())
                    if (!Collections.disjoint(group, set)) changed |= group.addAll(set);
            } while (changed);
            if (!s.chunks().containsAll(group)
                    || group.stream()
                            .anyMatch(
                                    p ->
                                            support.getOrDefault(p, 0.0)
                                                    >= localPolicy.supportThreshold())) continue;
            var affectedParcels = new HashSet<UUID>();
            boolean blocked = false;
            for (var e : parcelFootprint.entrySet())
                if (!Collections.disjoint(group, e.getValue())) {
                    affectedParcels.add(e.getKey());
                    if (holders.contains(e.getKey())) blocked = true;
                }
            if (blocked || !safeTopology(s.chunks(), group)) continue;
            for (var e : footprint.entrySet())
                if (!Collections.disjoint(group, e.getValue())) {
                    Boolean ruined = checkedRuins.get(e.getKey());
                    if (ruined == null) {
                        ruined =
                                JooqDecayRepository.ruined(
                                        c, JooqBuildingRepository.current(c, e.getKey()));
                        checkedRuins.put(e.getKey(), ruined);
                    }
                    if (!ruined) {
                        blocked = true;
                        break;
                    }
                }
            if (blocked) continue;
            return new Candidate(
                    Set.copyOf(group),
                    Set.copyOf(affectedParcels),
                    signature + ";" + encode(group));
        }
        return null;
    }

    // Bounded complement flood proves every removed cell is reachable from outside, not a new hole.
    static boolean safeTopology(Set<ChunkPosition> all, Set<ChunkPosition> remove) {
        var retained = new HashSet<>(all);
        retained.removeAll(remove);
        if (!retained.isEmpty() && !connected(retained)) return false;
        long minX = (long) all.stream().mapToInt(ChunkPosition::x).min().orElseThrow() - 1,
                maxX = (long) all.stream().mapToInt(ChunkPosition::x).max().orElseThrow() + 1;
        long minZ = (long) all.stream().mapToInt(ChunkPosition::z).min().orElseThrow() - 1,
                maxZ = (long) all.stream().mapToInt(ChunkPosition::z).max().orElseThrow() + 1;
        if (minX < Integer.MIN_VALUE
                || maxX > Integer.MAX_VALUE
                || minZ < Integer.MIN_VALUE
                || maxZ > Integer.MAX_VALUE
                || (maxX - minX + 1) * (maxZ - minZ + 1) > 65536) return false;
        var seen = new HashSet<ChunkPosition>();
        var queue = new ArrayDeque<ChunkPosition>();
        queue.add(new ChunkPosition((int) minX, (int) minZ));
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            if (p.x() < minX
                    || p.x() > maxX
                    || p.z() < minZ
                    || p.z() > maxZ
                    || retained.contains(p)
                    || !seen.add(p)) continue;
            for (var n : neighbors(p)) queue.add(n);
        }
        return seen.containsAll(remove);
    }

    private static boolean connected(Set<ChunkPosition> cells) {
        var seen = new HashSet<ChunkPosition>();
        var q = new ArrayDeque<ChunkPosition>();
        q.add(cells.iterator().next());
        while (!q.isEmpty()) {
            var p = q.remove();
            if (!cells.contains(p) || !seen.add(p)) continue;
            for (var n : neighbors(p)) q.add(n);
        }
        return seen.size() == cells.size();
    }

    private static List<ChunkPosition> neighbors(ChunkPosition p) {
        return List.of(
                new ChunkPosition(p.x() - 1, p.z()),
                new ChunkPosition(p.x() + 1, p.z()),
                new ChunkPosition(p.x(), p.z() - 1),
                new ChunkPosition(p.x(), p.z() + 1));
    }

    private static String encode(Set<ChunkPosition> chunks) {
        return chunks.stream()
                .sorted(
                        Comparator.comparingInt(ChunkPosition::x)
                                .thenComparingInt(ChunkPosition::z))
                .map(p -> p.x() + "," + p.z())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private static Set<ChunkPosition> decode(String value) {
        var r = new HashSet<ChunkPosition>();
        for (var pair : value.split(";")) {
            var xy = pair.split(",");
            r.add(new ChunkPosition(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
        }
        return r;
    }

    private void archiveParcel(DSLContext c, UUID settlement, UUID id) {
        var p = c.selectFrom(PARCELS).where(PARCELS.PARCEL_ID.eq(id)).fetchSingle();
        var cells = c.selectFrom(PARCEL_CELLS).where(PARCEL_CELLS.PARCEL_ID.eq(id)).fetch();
        var policies = c.selectFrom(PARCEL_POLICY).where(PARCEL_POLICY.PARCEL_ID.eq(id)).fetch();
        c.insertInto(CYCLE_PARCEL_HISTORY)
                .set(CYCLE_PARCEL_HISTORY.PARCEL_ID, id)
                .set(CYCLE_PARCEL_HISTORY.SETTLEMENT_ID, settlement)
                .set(CYCLE_PARCEL_HISTORY.NAME, p.get(PARCELS.NAME))
                .set(CYCLE_PARCEL_HISTORY.REVISION, p.get(PARCELS.REVISION))
                .set(CYCLE_PARCEL_HISTORY.CELLS, cells.formatJSON())
                .set(CYCLE_PARCEL_HISTORY.POLICY, policies.formatJSON())
                .set(CYCLE_PARCEL_HISTORY.ENDED_TICK, clock.ticks())
                .execute();
        c.update(PARCEL_REQUESTS)
                .set(PARCEL_REQUESTS.STATE, "CANCELLED")
                .where(PARCEL_REQUESTS.PARCEL_ID.eq(id))
                .and(PARCEL_REQUESTS.STATE.eq("PENDING"))
                .execute();
        c.deleteFrom(PARCELS).where(PARCELS.PARCEL_ID.eq(id)).execute();
    }

    private SettlementChange event(DSLContext c, SettlementView before, String type, String detail)
            throws SQLException {
        c.insertInto(SETTLEMENT_EVENTS)
                .set(SETTLEMENT_EVENTS.EVENT_ID, UUID.randomUUID())
                .set(SETTLEMENT_EVENTS.SETTLEMENT_ID, before.id())
                .set(SETTLEMENT_EVENTS.ACTOR_UUID, before.owner())
                .set(SETTLEMENT_EVENTS.TYPE, type)
                .set(SETTLEMENT_EVENTS.DETAIL, detail)
                .set(SETTLEMENT_EVENTS.OCCURRED_AT, Instant.now().toEpochMilli())
                .set(SETTLEMENT_EVENTS.GAME_TICK, clock.ticks())
                .set(SETTLEMENT_EVENTS.DELIVERED, 0L)
                .execute();
        clock.checkpoint(DatabaseAccess.transaction(c));
        return new SettlementChange(
                before.id(), JooqSettlementRepository.load(c, before.id()), null, detail);
    }

    static void invalidateRecovered(
            DSLContext c, UUID settlement, JooqLocalActivity local, long tick) throws SQLException {
        var notice =
                c.selectFrom(CYCLE_NOTICES)
                        .where(CYCLE_NOTICES.SETTLEMENT_ID.eq(settlement))
                        .fetchOne();
        if (notice == null) return;
        var view = local.view(c, settlement, tick);
        if (decode(notice.get(CYCLE_NOTICES.CHUNKS)).stream()
                .anyMatch(p -> view.support().getOrDefault(p, 0.0) >= view.threshold()))
            c.deleteFrom(CYCLE_NOTICES).where(CYCLE_NOTICES.SETTLEMENT_ID.eq(settlement)).execute();
    }
}
