package xdu.undarubra.esmp.civilization.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;

import java.util.*;

class CycleTopologyTest {
    @Test
    void rejectsHolesAndDisconnectedRemnantsButAllowsPeripheralAndTerminalGroups() {
        var square = new HashSet<ChunkPosition>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) square.add(new ChunkPosition(x, z));
        assertFalse(JooqCycleRepository.safeTopology(square, Set.of(new ChunkPosition(1, 1))));
        assertFalse(
                JooqCycleRepository.safeTopology(
                        square,
                        Set.of(
                                new ChunkPosition(1, 0),
                                new ChunkPosition(1, 1),
                                new ChunkPosition(1, 2))));
        assertTrue(JooqCycleRepository.safeTopology(square, Set.of(new ChunkPosition(0, 0))));
        assertTrue(JooqCycleRepository.safeTopology(square, square));
    }
}
