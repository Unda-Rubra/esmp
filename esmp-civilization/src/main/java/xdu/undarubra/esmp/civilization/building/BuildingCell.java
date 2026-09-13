package xdu.undarubra.esmp.civilization.building;

import java.util.Objects;

/**
 * BlockData and geometry traits only. Never contains inventory, NBT or other block-entity payload.
 */
public record BuildingCell(
        String data, boolean air, boolean solid, boolean opening, boolean blockEntity) {
    public BuildingCell {
        Objects.requireNonNull(data);
        if (data.isBlank() || data.length() > 4096 || !data.startsWith("minecraft:"))
            throw new IllegalArgumentException("Invalid vanilla block data");
        if (air && (solid || opening || blockEntity))
            throw new IllegalArgumentException("Air cannot carry structural/entity traits");
    }

    public boolean automaticallyMutable() {
        return !blockEntity;
    }
}
