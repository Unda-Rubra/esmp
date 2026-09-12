package xdu.undarubra.esmp.civilization.land;

public record LandValuePolicy(
        int version, long baseValue, long reliefPenalty, long maxReliefPenalty, long shoreBonus) {
    public LandValuePolicy {
        if (version < 1
                || baseValue < 1
                || reliefPenalty < 0
                || maxReliefPenalty < 0
                || maxReliefPenalty >= baseValue
                || shoreBonus < 0) {
            throw new IllegalArgumentException("Invalid land-value policy");
        }
        Math.addExact(baseValue, shoreBonus);
    }

    public static LandValuePolicy defaults() {
        return new LandValuePolicy(1, 100, 2, 50, 25);
    }

    public long value(int relief, int waterSamples) {
        if (relief < 0 || waterSamples < 0 || waterSamples > 16)
            throw new IllegalArgumentException("Invalid natural land samples");
        long penalty =
                reliefPenalty == 0
                        ? 0
                        : relief > maxReliefPenalty / reliefPenalty
                                ? maxReliefPenalty
                                : relief * reliefPenalty;
        return baseValue - penalty + (waterSamples > 0 && waterSamples < 16 ? shoreBonus : 0);
    }

    public LandValueProfile.Terrain terrain(int relief, int waterSamples) {
        if (relief < 0 || waterSamples < 0 || waterSamples > 16)
            throw new IllegalArgumentException("Invalid natural land samples");
        if (waterSamples == 16) return LandValueProfile.Terrain.WATER;
        if (waterSamples > 0) return LandValueProfile.Terrain.SHORE;
        return relief <= 8 ? LandValueProfile.Terrain.FLAT : LandValueProfile.Terrain.RUGGED;
    }
}
