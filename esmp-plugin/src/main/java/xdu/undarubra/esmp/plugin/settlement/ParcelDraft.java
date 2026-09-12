package xdu.undarubra.esmp.plugin.settlement;

import xdu.undarubra.esmp.civilization.settlement.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Read-only preview; application transactions remain authoritative at confirmation. */
public final class ParcelDraft {
    public record Result(
            Set<ParcelCell> cells, Set<ParcelCell> blocked, UUID parcel, int added, String error) {
        public Result {
            cells = Set.copyOf(cells);
            blocked = Set.copyOf(blocked);
        }

        public boolean valid() {
            return error == null;
        }
    }

    private ParcelDraft() {}

    public static Result add(
            SettlementView settlement,
            LandSnapshot.Land land,
            UUID actor,
            boolean admin,
            String name,
            ParcelCell first,
            ParcelCell last,
            int minY,
            int maxY) {
        Set<ParcelCell> cells;
        try {
            cells = ParcelCell.cuboid(first, last);
        } catch (SettlementException tooLarge) {
            return new Result(Set.of(), Set.of(), null, 0, "Select at most 4096 cells.");
        }
        if (settlement == null || land == null)
            return fail(cells, "Choose land inside a settlement.");
        if (name == null || name.isBlank())
            return fail(cells, "Hold a named tag in your off hand.");
        try {
            name = LandName.normalize(name);
        } catch (SettlementException invalid) {
            return fail(cells, invalid.getMessage());
        }
        ParcelView existing = null;
        for (var parcel : land.parcels().values())
            if (parcel.name().equalsIgnoreCase(name)) {
                existing = parcel;
                break;
            }
        if (!admin && land.bans().contains(actor)) return fail(cells, "You are banned here.");
        if (!admin
                && !settlement.role(actor).canManage()
                && (existing == null || !existing.holders().contains(actor))) {
            return fail(cells, "Ask an owner or governor to create this parcel.");
        }
        if (existing == null && land.parcels().size() >= 128)
            return fail(cells, "This settlement has reached its parcel limit.");
        var combined = new HashSet<ParcelCell>();
        if (existing != null) combined.addAll(existing.cells());
        var blocked = new HashSet<ParcelCell>();
        String error = null;
        int added = 0;
        for (var cell : cells) {
            if (!settlement.chunks().contains(cell.chunk())) {
                blocked.add(cell);
                error = "Keep the selection inside this settlement.";
                continue;
            }
            if ((long) cell.y() * 8 < minY || (long) cell.y() * 8 + 8 > maxY) {
                blocked.add(cell);
                error = "Keep the selection within the world's height.";
                continue;
            }
            var occupied = land.cells().get(cell);
            if (occupied == null) {
                combined.add(cell);
                added++;
            } else if (existing == null || !occupied.id().equals(existing.id())) blocked.add(cell);
        }
        if (error == null && added == 0) error = "Choose unassigned cells to add.";
        if (error == null && combined.size() > ParcelCell.LIMIT)
            error = "A parcel can contain at most 4096 cells.";
        if (error == null && !ParcelCell.connected(combined))
            error = "Join the cells along a face.";
        return new Result(cells, blocked, existing == null ? null : existing.id(), added, error);
    }

    private static Result fail(Set<ParcelCell> cells, String error) {
        return new Result(cells, Set.of(), null, 0, error);
    }
}
