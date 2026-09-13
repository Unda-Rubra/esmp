package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.jooq.DSLContext;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;

import java.sql.SQLException;
import java.util.*;

/** Transaction-local storage helper; no scheduler, world access or independent write pathway. */
final class JooqLocalActivity {
    private final ActivityPolicy activity;
    private final LocalActivityPolicy local;

    JooqLocalActivity(ActivityPolicy activity, LocalActivityPolicy local) {
        this.activity = activity;
        this.local = Objects.requireNonNull(local);
    }

    void credit(DSLContext c, ActivityContribution observation, double amount, long tick)
            throws SQLException {
        var a = SETTLEMENT_LOCAL_ACTIVITY;
        long x = Math.floorDiv(observation.blockX(), 16),
                z = Math.floorDiv(observation.blockZ(), 16);
        var key =
                a.SETTLEMENT_ID
                        .eq(observation.settlementId())
                        .and(a.WORLD_INSTANCE_ID.eq(observation.worldInstanceId()))
                        .and(a.CHUNK_X.eq(x))
                        .and(a.CHUNK_Z.eq(z));
        var previous = c.selectFrom(a).where(key).fetchOne();
        double score = previous == null ? 0 : project(previous, tick);
        score = Math.min(activity.maxScore(), score + amount);
        c.insertInto(a)
                .set(a.SETTLEMENT_ID, observation.settlementId())
                .set(a.WORLD_INSTANCE_ID, observation.worldInstanceId())
                .set(a.CHUNK_X, x)
                .set(a.CHUNK_Z, z)
                .set(a.SCORE_AT_ANCHOR, score)
                .set(a.ANCHOR_GAME_TICK, tick)
                .set(a.POLICY_VERSION, (long) LocalActivityPolicy.VERSION)
                .onConflict(a.SETTLEMENT_ID, a.WORLD_INSTANCE_ID, a.CHUNK_X, a.CHUNK_Z)
                .doUpdate()
                .set(a.SCORE_AT_ANCHOR, score)
                .set(a.ANCHOR_GAME_TICK, tick)
                .execute();
    }

    LocalActivityView view(DSLContext c, UUID settlement, long tick) throws SQLException {
        UUID world = world(c, settlement);
        Map<ChunkPosition, Double> raw = scores(c, settlement, world, null, tick);
        Map<ChunkPosition, Double> support = new HashMap<>();
        for (var chunk : raw.keySet())
            support.put(
                    chunk, LocalActivityProjection.support(raw, chunk, local, activity.maxScore()));
        return new LocalActivityView(
                settlement,
                world,
                tick,
                local.supportThreshold(),
                raw,
                support,
                LocalActivityProjection.regions(support, local.supportThreshold()));
    }

    double support(
            DSLContext c,
            UUID settlement,
            UUID world,
            Map<ChunkPosition, Integer> footprint,
            long tick)
            throws SQLException {
        if (!world(c, settlement).equals(world))
            throw new SettlementException("Footprint is in a different world instance");
        Map<ChunkPosition, Double> raw = scores(c, settlement, world, footprint.keySet(), tick);
        double weighted = 0;
        long area = 0;
        for (var entry : footprint.entrySet()) {
            if (!raw.containsKey(entry.getKey()))
                throw new SettlementException(
                        "Every footprint chunk must be claimed by this Settlement");
            weighted +=
                    LocalActivityProjection.support(raw, entry.getKey(), local, activity.maxScore())
                            * entry.getValue();
            area += entry.getValue();
        }
        return weighted / area;
    }

    private UUID world(DSLContext c, UUID settlement) {
        UUID world =
                c.select(SETTLEMENTS.WORLD_INSTANCE_ID)
                        .from(SETTLEMENTS)
                        .where(
                                SETTLEMENTS
                                        .SETTLEMENT_ID
                                        .eq(settlement)
                                        .and(SETTLEMENTS.STATUS.eq("OWNED")))
                        .fetchOne(SETTLEMENTS.WORLD_INSTANCE_ID);
        if (world == null)
            throw new SettlementException("Settlement is no longer owned or does not exist");
        return world;
    }

    private Map<ChunkPosition, Double> scores(
            DSLContext c, UUID settlement, UUID world, Set<ChunkPosition> footprint, long tick)
            throws SQLException {
        if (tick < 0) throw new IllegalArgumentException("Game tick must be nonnegative");
        var ch = SETTLEMENT_CHUNKS;
        var a = SETTLEMENT_LOCAL_ACTIVITY;
        var condition = ch.SETTLEMENT_ID.eq(settlement).and(ch.WORLD_INSTANCE_ID.eq(world));
        // A bounded spatial query omits unrelated far-away Claims without calculating Regions.
        Set<ChunkPosition> needed = null;
        if (footprint != null) {
            needed = new HashSet<>(footprint);
            long minX = Integer.MAX_VALUE,
                    maxX = Integer.MIN_VALUE,
                    minZ = Integer.MAX_VALUE,
                    maxZ = Integer.MIN_VALUE;
            for (var chunk : footprint) {
                minX = Math.min(minX, (long) chunk.x() - 1);
                maxX = Math.max(maxX, (long) chunk.x() + 1);
                minZ = Math.min(minZ, (long) chunk.z() - 1);
                maxZ = Math.max(maxZ, (long) chunk.z() + 1);
                if (local.neighborWeight() != 0) {
                    if (chunk.x() > Integer.MIN_VALUE)
                        needed.add(new ChunkPosition(chunk.x() - 1, chunk.z()));
                    if (chunk.x() < Integer.MAX_VALUE)
                        needed.add(new ChunkPosition(chunk.x() + 1, chunk.z()));
                    if (chunk.z() > Integer.MIN_VALUE)
                        needed.add(new ChunkPosition(chunk.x(), chunk.z() - 1));
                    if (chunk.z() < Integer.MAX_VALUE)
                        needed.add(new ChunkPosition(chunk.x(), chunk.z() + 1));
                }
            }
            condition =
                    condition
                            .and(ch.CHUNK_X.between(minX, maxX))
                            .and(ch.CHUNK_Z.between(minZ, maxZ));
        }
        var result = new HashMap<ChunkPosition, Double>();
        for (Record row :
                c.select(
                                ch.CHUNK_X,
                                ch.CHUNK_Z,
                                a.SETTLEMENT_ID,
                                a.SCORE_AT_ANCHOR,
                                a.ANCHOR_GAME_TICK,
                                a.POLICY_VERSION)
                        .from(ch)
                        .leftJoin(a)
                        .on(
                                a.SETTLEMENT_ID
                                        .eq(ch.SETTLEMENT_ID)
                                        .and(a.WORLD_INSTANCE_ID.eq(ch.WORLD_INSTANCE_ID))
                                        .and(a.CHUNK_X.eq(ch.CHUNK_X))
                                        .and(a.CHUNK_Z.eq(ch.CHUNK_Z)))
                        .where(condition)
                        .fetch()) {
            var chunk =
                    new ChunkPosition(
                            Math.toIntExact(row.get(ch.CHUNK_X)),
                            Math.toIntExact(row.get(ch.CHUNK_Z)));
            if (needed != null && !needed.contains(chunk)) continue;
            result.put(chunk, row.get(a.SETTLEMENT_ID) == null ? 0.0 : project(row, tick));
        }
        return result;
    }

    private double project(Record row, long tick) throws SQLException {
        var a = SETTLEMENT_LOCAL_ACTIVITY;
        long version = row.get(a.POLICY_VERSION), anchor = row.get(a.ANCHOR_GAME_TICK);
        double score = row.get(a.SCORE_AT_ANCHOR);
        if (version != LocalActivityPolicy.VERSION
                || anchor < 0
                || !Double.isFinite(score)
                || score < 0
                || score > activity.maxScore())
            throw new SQLException(
                    "Invalid or unsupported Local Activity bucket; explicit migration required");
        if (tick < anchor)
            throw new SettlementException(
                    "Cannot project Local Activity before its recorded anchor");
        return ActivityProjection.decay(score, anchor, tick, activity.halfLifeDays());
    }
}
