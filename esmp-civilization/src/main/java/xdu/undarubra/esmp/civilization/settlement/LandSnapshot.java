package xdu.undarubra.esmp.civilization.settlement;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record LandSnapshot(Map<UUID, Land> settlements) {
    public static final LandSnapshot EMPTY = new LandSnapshot(Map.of());

    public LandSnapshot {
        settlements = Map.copyOf(settlements);
    }

    public record Land(
            long number,
            String officialName,
            boolean certified,
            long securityRevision,
            Set<UUID> bans,
            Map<UUID, ParcelView> parcels,
            Map<ParcelCell, ParcelView> cells) {
        public Land {
            bans = Set.copyOf(bans);
            parcels = Map.copyOf(parcels);
            cells = Map.copyOf(cells);
        }
    }

    public ParcelView at(UUID settlement, int x, int y, int z) {
        Land land = settlements.get(settlement);
        return land == null ? null : land.cells().get(ParcelCell.atBlock(x, y, z));
    }

    public boolean banned(UUID settlement, UUID actor) {
        Land land = settlements.get(settlement);
        return actor != null && land != null && land.bans().contains(actor);
    }
}
