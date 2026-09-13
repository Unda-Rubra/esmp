package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;

import java.util.*;

/** One-hop cardinal support. The input contains only currently claimed chunks of one Settlement. */
public final class LocalActivityProjection {
    private LocalActivityProjection() {}

    private static final int[] DX = {-1, 0, 0, 1};
    private static final int[] DZ = {0, -1, 1, 0};

    public static double support(
            Map<ChunkPosition, Double> raw,
            ChunkPosition chunk,
            LocalActivityPolicy policy,
            double maximum) {
        double score = raw.getOrDefault(chunk, 0.0);
        if (policy.neighborWeight() != 0) {
            for (int direction = 0; direction < 4; direction++) {
                var neighbor = neighbor(chunk, direction);
                if (neighbor != null)
                    score += policy.neighborWeight() * raw.getOrDefault(neighbor, 0.0);
            }
        }
        return Math.min(maximum, score);
    }

    public static List<Set<ChunkPosition>> regions(
            Map<ChunkPosition, Double> support, double threshold) {
        var pending =
                new TreeSet<ChunkPosition>(
                        Comparator.comparingInt(ChunkPosition::x)
                                .thenComparingInt(ChunkPosition::z));
        support.forEach(
                (chunk, score) -> {
                    if (score >= threshold) pending.add(chunk);
                });
        var regions = new ArrayList<Set<ChunkPosition>>();
        var queue = new ArrayDeque<ChunkPosition>();
        while (!pending.isEmpty()) {
            var component = new HashSet<ChunkPosition>();
            queue.add(pending.pollFirst());
            while (!queue.isEmpty()) {
                var chunk = queue.removeFirst();
                component.add(chunk);
                for (int direction = 0; direction < 4; direction++) {
                    var neighbor = neighbor(chunk, direction);
                    if (neighbor != null && pending.remove(neighbor)) queue.addLast(neighbor);
                }
            }
            regions.add(Set.copyOf(component));
        }
        return List.copyOf(regions);
    }

    private static ChunkPosition neighbor(ChunkPosition chunk, int direction) {
        long x = (long) chunk.x() + DX[direction], z = (long) chunk.z() + DZ[direction];
        return x < Integer.MIN_VALUE
                        || x > Integer.MAX_VALUE
                        || z < Integer.MIN_VALUE
                        || z > Integer.MAX_VALUE
                ? null
                : new ChunkPosition((int) x, (int) z);
    }
}
