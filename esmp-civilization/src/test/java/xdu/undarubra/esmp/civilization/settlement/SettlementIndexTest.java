package xdu.undarubra.esmp.civilization.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementIndexTest {
    @Test
    void negativeCoordinatesDoNotCollideAndWorldsRemainIsolated() {
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        SettlementView west = view(firstWorld, "West", -1, -1);
        SettlementView east = view(firstWorld, "East", 1, -1);
        SettlementView elsewhere = view(secondWorld, "Elsewhere", -1, -1);
        var index = new SettlementIndex();
        index.load(List.of(west, east, elsewhere));
        assertEquals(west.id(), index.at(firstWorld, -1, -1).id());
        assertEquals(east.id(), index.at(firstWorld, 1, -1).id());
        assertEquals(elsewhere.id(), index.at(secondWorld, -1, -1).id());
        assertNull(index.at(secondWorld, 1, -1));
    }

    @Test
    void publishedReplacementRemovesStaleNameAndChunksWithoutAffectingNeighbors() {
        UUID world = UUID.randomUUID();
        SettlementView original = view(world, "Original", 0, 0);
        SettlementView neighbor = view(world, "Neighbor", 1, 0);
        var index = new SettlementIndex();
        index.load(List.of(original, neighbor));
        SettlementView updated = new SettlementView(original.id(), world, "Renamed", original.owner(),
                Instant.EPOCH, 1, 2, Map.of(), Set.of(new ChunkPosition(0, 1)));
        index.replace(original.id(), updated);
        assertNull(index.byName("Original"));
        assertNull(index.at(world, 0, 0));
        assertEquals(original.id(), index.byName("RENAMED").id());
        assertEquals(original.id(), index.at(world, 0, 1).id());
        index.replace(original.id(), null);
        assertNull(index.at(world, 0, 1));
        assertNull(index.byName("Renamed"));
        assertEquals(neighbor.id(), index.at(world, 1, 0).id());
    }

    private static SettlementView view(UUID world, String name, int x, int z) {
        return new SettlementView(UUID.randomUUID(), world, name, UUID.randomUUID(), Instant.EPOCH,
                1, 1, Map.of(), Set.of(new ChunkPosition(x, z)));
    }
}
