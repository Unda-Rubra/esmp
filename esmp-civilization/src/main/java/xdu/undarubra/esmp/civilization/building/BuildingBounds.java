package xdu.undarubra.esmp.civilization.building;

import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;

import java.util.*;

public record BuildingBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final int HARD_MAX_VOLUME = 32768;

    public record Position(int x, int y, int z) {}

    public BuildingBounds {
        long x = (long) maxX - minX + 1, y = (long) maxY - minY + 1, z = (long) maxZ - minZ + 1;
        if (x <= 0
                || y <= 0
                || z <= 0
                || x > HARD_MAX_VOLUME
                || y > HARD_MAX_VOLUME
                || z > HARD_MAX_VOLUME
                || x * y * z > HARD_MAX_VOLUME)
            throw new IllegalArgumentException(
                    "Building selection exceeds the hard volume limit or has invalid bounds");
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public int volume() {
        return sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(Position p) {
        return p.x >= minX
                && p.x <= maxX
                && p.y >= minY
                && p.y <= maxY
                && p.z >= minZ
                && p.z <= maxZ;
    }

    public boolean intersects(BuildingBounds b) {
        return minX <= b.maxX
                && maxX >= b.minX
                && minY <= b.maxY
                && maxY >= b.minY
                && minZ <= b.maxZ
                && maxZ >= b.minZ;
    }

    public int index(Position p) {
        if (!contains(p)) throw new IllegalArgumentException("Position outside Building");
        return ((p.y - minY) * sizeZ() + p.z - minZ) * sizeX() + p.x - minX;
    }

    public Position position(int index) {
        if (index < 0 || index >= volume())
            throw new IllegalArgumentException("Invalid Building cell");
        return new Position(
                minX + index % sizeX(),
                minY + index / (sizeX() * sizeZ()),
                minZ + (index / sizeX()) % sizeZ());
    }

    public Map<ChunkPosition, Integer> footprint() {
        var result = new LinkedHashMap<ChunkPosition, Integer>();
        for (int x = Math.floorDiv(minX, 16); x <= Math.floorDiv(maxX, 16); x++)
            for (int z = Math.floorDiv(minZ, 16); z <= Math.floorDiv(maxZ, 16); z++) {
                int width =
                        (int)
                                (Math.min(maxX, (long) x * 16 + 15)
                                        - Math.max(minX, (long) x * 16)
                                        + 1);
                int depth =
                        (int)
                                (Math.min(maxZ, (long) z * 16 + 15)
                                        - Math.max(minZ, (long) z * 16)
                                        + 1);
                result.put(new ChunkPosition(x, z), width * depth);
            }
        return Map.copyOf(result);
    }
}
