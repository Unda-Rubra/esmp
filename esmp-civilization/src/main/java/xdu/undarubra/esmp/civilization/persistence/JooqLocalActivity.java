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

    double wildSupport(DSLContext c, UUID world, Map<ChunkPosition, Integer> footprint, long tick)
            throws SQLException {
        var needed = new HashSet<>(footprint.keySet());
        for (var chunk : footprint.keySet()) {
            if (chunk.x() > Integer.MIN_VALUE)
                needed.add(new ChunkPosition(chunk.x() - 1, chunk.z()));
            if (chunk.x() < Integer.MAX_VALUE)
                needed.add(new ChunkPosition(chunk.x() + 1, chunk.z()));
            if (chunk.z() > Integer.MIN_VALUE)
                needed.add(new ChunkPosition(chunk.x(), chunk.z() - 1));
            if (chunk.z() < Integer.MAX_VALUE)
                needed.add(new ChunkPosition(chunk.x(), chunk.z() + 1));
        }
        var a = WILD_ACTIVITY;
        var raw = new HashMap<ChunkPosition, Double>();
        long minX = needed.stream().mapToLong(ChunkPosition::x).min().orElseThrow(),
                maxX = needed.stream().mapToLong(ChunkPosition::x).max().orElseThrow(),
                minZ = needed.stream().mapToLong(ChunkPosition::z).min().orElseThrow(),
                maxZ = needed.stream().mapToLong(ChunkPosition::z).max().orElseThrow();
        for (var row :
                c.selectFrom(a)
                        .where(a.WORLD_INSTANCE_ID.eq(world))
                        .and(a.CHUNK_X.between(minX, maxX))
                        .and(a.CHUNK_Z.between(minZ, maxZ))
                        .andNotExists(
                                c.selectOne()
                                        .from(SETTLEMENT_CHUNKS)
                                        .where(
                                                SETTLEMENT_CHUNKS.WORLD_INSTANCE_ID.eq(
                                                        a.WORLD_INSTANCE_ID))
                                        .and(SETTLEMENT_CHUNKS.CHUNK_X.eq(a.CHUNK_X))
                                        .and(SETTLEMENT_CHUNKS.CHUNK_Z.eq(a.CHUNK_Z)))
                        .fetch()) {
            var chunk =
                    new ChunkPosition(
                            Math.toIntExact(row.get(a.CHUNK_X)),
                            Math.toIntExact(row.get(a.CHUNK_Z)));
            if (needed.contains(chunk)) raw.put(chunk, projectWild(row, tick));
        }
        double weighted = 0;
        long area = 0;
        for (var entry : footprint.entrySet()) {
            weighted +=
                    LocalActivityProjection.support(raw, entry.getKey(), local, activity.maxScore())
                            * entry.getValue();
            area += entry.getValue();
        }
        return weighted / area;
    }

    private double projectWild(Record row, long tick) throws SQLException {
        double score = row.get(WILD_ACTIVITY.SCORE);
        long anchor = row.get(WILD_ACTIVITY.ANCHOR_TICK);
        if (!Double.isFinite(score)
                || score < 0
                || score > activity.maxScore()
                || anchor < 0
                || tick < anchor) throw new SQLException("Invalid wild Activity state");
        return ActivityProjection.decay(score, anchor, tick, activity.halfLifeDays());
    }

    static void claimed(DSLContext c, UUID world, ChunkPosition chunk) {
        c.deleteFrom(WILD_ACTIVITY)
                .where(WILD_ACTIVITY.WORLD_INSTANCE_ID.eq(world))
                .and(WILD_ACTIVITY.CHUNK_X.eq((long) chunk.x()))
                .and(WILD_ACTIVITY.CHUNK_Z.eq((long) chunk.z()))
                .execute();
        c.deleteFrom(WILD_ACTIVITY_ACTORS)
                .where(WILD_ACTIVITY_ACTORS.WORLD_INSTANCE_ID.eq(world))
                .and(WILD_ACTIVITY_ACTORS.CHUNK_X.eq((long) chunk.x()))
                .and(WILD_ACTIVITY_ACTORS.CHUNK_Z.eq((long) chunk.z()))
                .execute();
    }

    boolean creditWild(DSLContext c, ActivityContribution observation, long now)
            throws SQLException {
        if (observation.action()
                == xdu.undarubra.esmp.civilization.settlement.ParcelAction.AUTOMATION) return false;
        var b = JooqBuildingRepository.current(c, observation.wildBuilding());
        var p =
                new xdu.undarubra.esmp.civilization.building.BuildingBounds.Position(
                        observation.blockX(), observation.blockY(), observation.blockZ());
        if (b.retired()
                || b.settlement() != null
                || !b.world().equals(observation.worldInstanceId())
                || !b.historySettlement().equals(observation.settlementId())
                || b.ownershipRevision() != observation.ownershipRevision()
                || observation.chunkRevision() != 0
                || observation.securityRevision() != 0
                || !b.bounds().contains(p)
                || !JooqBuildingRepository.unclaimed(c, b.world(), b.bounds())) return false;
        long x = observation.blockX() >> 4, z = observation.blockZ() >> 4;
        long day = now / xdu.undarubra.esmp.core.time.GameClock.TICKS_PER_DAY;
        var a = WILD_ACTIVITY;
        var u = WILD_ACTIVITY_ACTORS;
        var key = a.WORLD_INSTANCE_ID.eq(b.world()).and(a.CHUNK_X.eq(x)).and(a.CHUNK_Z.eq(z));
        var actorKey =
                u.WORLD_INSTANCE_ID
                        .eq(b.world())
                        .and(u.CHUNK_X.eq(x))
                        .and(u.CHUNK_Z.eq(z))
                        .and(u.ACTOR_UUID.eq(observation.actor()));
        var row = c.selectFrom(a).where(key).fetchOne();
        var actor = c.selectFrom(u).where(actorKey).fetchOne();
        Long build = actor == null ? null : actor.get(u.BUILD_TICK);
        Long interaction = actor == null ? null : actor.get(u.INTERACTION_TICK);
        boolean building = observation.category() == ActivityContribution.Category.BUILD;
        Long last = building ? build : interaction;
        if (last != null && now - last < activity.actionCooldownTicks()) return false;
        double actorCredit = actor != null && actor.get(u.DAY) == day ? actor.get(u.CREDITED) : 0;
        double daily = row != null && row.get(a.DAY) == day ? row.get(a.CREDITED) : 0;
        double score = row == null ? 0 : projectWild(row, now);
        double amount =
                Math.min(
                        building ? activity.buildCredit() : activity.interactionCredit(),
                        Math.min(
                                activity.playerDailyCap() - actorCredit,
                                Math.min(
                                        activity.settlementDailyCap() - daily,
                                        activity.maxScore() - score)));
        if (amount <= 0) return false;
        if (building) build = now;
        else interaction = now;
        c.insertInto(a)
                .set(a.WORLD_INSTANCE_ID, b.world())
                .set(a.CHUNK_X, x)
                .set(a.CHUNK_Z, z)
                .set(a.SCORE, score + amount)
                .set(a.ANCHOR_TICK, now)
                .set(a.DAY, day)
                .set(a.CREDITED, daily + amount)
                .onConflict(a.WORLD_INSTANCE_ID, a.CHUNK_X, a.CHUNK_Z)
                .doUpdate()
                .set(a.SCORE, score + amount)
                .set(a.ANCHOR_TICK, now)
                .set(a.DAY, day)
                .set(a.CREDITED, daily + amount)
                .execute();
        c.insertInto(u)
                .set(u.WORLD_INSTANCE_ID, b.world())
                .set(u.CHUNK_X, x)
                .set(u.CHUNK_Z, z)
                .set(u.ACTOR_UUID, observation.actor())
                .set(u.DAY, day)
                .set(u.CREDITED, actorCredit + amount)
                .set(u.BUILD_TICK, build)
                .set(u.INTERACTION_TICK, interaction)
                .onConflict(u.WORLD_INSTANCE_ID, u.CHUNK_X, u.CHUNK_Z, u.ACTOR_UUID)
                .doUpdate()
                .set(u.DAY, day)
                .set(u.CREDITED, actorCredit + amount)
                .set(u.BUILD_TICK, build)
                .set(u.INTERACTION_TICK, interaction)
                .execute();
        return true;
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
