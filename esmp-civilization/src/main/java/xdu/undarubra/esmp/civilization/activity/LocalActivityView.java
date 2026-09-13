package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Ephemeral supported components, not identities or player-managed districts. */
public record LocalActivityView(
        UUID settlementId,
        UUID worldInstanceId,
        long measuredAtTick,
        double threshold,
        Map<ChunkPosition, Double> rawScores,
        Map<ChunkPosition, Double> support,
        List<Set<ChunkPosition>> regions) {
    public LocalActivityView {
        rawScores = Map.copyOf(rawScores);
        support = Map.copyOf(support);
        regions = regions.stream().map(Set::copyOf).toList();
    }
}
