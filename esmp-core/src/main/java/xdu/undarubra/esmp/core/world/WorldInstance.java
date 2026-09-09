package xdu.undarubra.esmp.core.world;

import java.time.Instant;
import java.util.UUID;

public record WorldInstance(
        UUID instanceId,
        String definitionId,
        UUID bukkitWorldId,
        String worldName,
        Instant createdAt) {
}
