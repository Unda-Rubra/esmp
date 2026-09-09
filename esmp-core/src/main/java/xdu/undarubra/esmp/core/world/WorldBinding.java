package xdu.undarubra.esmp.core.world;

import java.util.Objects;
import java.util.UUID;

/** A main-thread capture of a configured physical world, without a Paper object. */
public record WorldBinding(String definitionId, UUID bukkitWorldId, String worldName) {
    public WorldBinding {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(bukkitWorldId, "bukkitWorldId");
        Objects.requireNonNull(worldName, "worldName");
        if (definitionId.isBlank() || worldName.isBlank()) {
            throw new IllegalArgumentException("World binding values must not be blank");
        }
    }
}
