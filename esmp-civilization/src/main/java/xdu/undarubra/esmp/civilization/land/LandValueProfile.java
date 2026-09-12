package xdu.undarubra.esmp.civilization.land;

import java.util.Objects;
import java.util.UUID;

public record LandValueProfile(
        UUID worldInstance,
        int chunkX,
        int chunkZ,
        int valuationVersion,
        long baseValue,
        Terrain terrain) {
    public enum Terrain {
        FLAT,
        RUGGED,
        SHORE,
        WATER
    }

    public LandValueProfile {
        Objects.requireNonNull(worldInstance);
        Objects.requireNonNull(terrain);
        if (valuationVersion < 1 || baseValue < 1)
            throw new IllegalArgumentException("Invalid land-value profile");
    }
}
