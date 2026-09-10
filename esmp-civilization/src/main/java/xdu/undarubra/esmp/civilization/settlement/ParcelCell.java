package xdu.undarubra.esmp.civilization.settlement;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ParcelCell(int x, int y, int z) {
    public static final int SIZE = 8;
    public static final int LIMIT = 4096;

    public static ParcelCell atBlock(int x, int y, int z) {
        return new ParcelCell(
                Math.floorDiv(x, SIZE), Math.floorDiv(y, SIZE), Math.floorDiv(z, SIZE));
    }

    public ChunkPosition chunk() {
        return new ChunkPosition(Math.floorDiv(x, 2), Math.floorDiv(z, 2));
    }

    public List<ParcelCell> neighbors() {
        return List.of(
                new ParcelCell(x + 1, y, z),
                new ParcelCell(x - 1, y, z),
                new ParcelCell(x, y + 1, z),
                new ParcelCell(x, y - 1, z),
                new ParcelCell(x, y, z + 1),
                new ParcelCell(x, y, z - 1));
    }

    public static Set<ParcelCell> cuboid(ParcelCell a, ParcelCell b) {
        long dx = Math.abs((long) a.x - b.x) + 1,
                dy = Math.abs((long) a.y - b.y) + 1,
                dz = Math.abs((long) a.z - b.z) + 1;
        if (dx > LIMIT || dy > LIMIT || dz > LIMIT || dx * dy * dz > LIMIT) {
            throw new SettlementException("Selection exceeds 4096 cells");
        }
        var result = new HashSet<ParcelCell>();
        for (long x = Math.min(a.x, b.x); x <= Math.max(a.x, b.x); x++)
            for (long y = Math.min(a.y, b.y); y <= Math.max(a.y, b.y); y++)
                for (long z = Math.min(a.z, b.z); z <= Math.max(a.z, b.z); z++)
                    result.add(new ParcelCell((int) x, (int) y, (int) z));
        return Set.copyOf(result);
    }

    public static boolean connected(Set<ParcelCell> cells) {
        if (cells.isEmpty()) return false;
        var pending = new ArrayDeque<ParcelCell>();
        var seen = new HashSet<ParcelCell>();
        pending.add(cells.iterator().next());
        while (!pending.isEmpty()) {
            var cell = pending.removeFirst();
            if (!seen.add(cell)) continue;
            for (var n : cell.neighbors())
                if (cells.contains(n) && !seen.contains(n)) pending.add(n);
        }
        return seen.size() == cells.size();
    }
}
