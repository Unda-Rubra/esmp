package xdu.undarubra.esmp.civilization.settlement;

import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.require;

import java.util.*;
import java.util.stream.Collectors;

/** Pure Parcel defaults, proposal values and messages. */
public final class ParcelRules {
    private ParcelRules() {}

    public static String parcelMessage(String kind, ParcelView old, ParcelView next) {
        return switch (kind) {
            case "DELETE" -> "Deleted " + old.name() + ".";
            case "HOLDERS" -> "Holders updated.";
            case "POLICY" -> "Permissions updated.";
            case "RENAME" -> "Renamed to " + next.name() + ".";
            default -> (old == null ? "Created " : "Updated ") + next.name() + ".";
        };
    }

    public static Map<ParcelAction, Set<ParcelRelation>> defaults() {
        var p = new EnumMap<ParcelAction, Set<ParcelRelation>>(ParcelAction.class);
        for (var a : ParcelAction.values())
            p.put(
                    a,
                    EnumSet.of(
                            ParcelRelation.OWNER,
                            ParcelRelation.GOVERNOR,
                            ParcelRelation.CITIZEN,
                            ParcelRelation.HOLDER));
        return p;
    }

    public static String encode(ParcelView p) {
        var parts = new ArrayList<String>();
        parts.add(p.name());
        parts.add(
                p.cells().stream()
                        .map(c -> c.x() + "," + c.y() + "," + c.z())
                        .sorted()
                        .collect(Collectors.joining(";")));
        parts.add(
                p.holders().stream().map(UUID::toString).sorted().collect(Collectors.joining(";")));
        for (var a : ParcelAction.values())
            parts.add(
                    p.policy().get(a).stream()
                            .map(Enum::name)
                            .sorted()
                            .collect(Collectors.joining(",")));
        return String.join("\n", parts);
    }

    public static ParcelView decode(UUID id, UUID settlement, long revision, String payload) {
        var parts = payload.split("\n", -1);
        require(parts.length == 7, "Invalid persisted proposal");
        var cells = new HashSet<ParcelCell>();
        for (var text : parts[1].split(";")) {
            var xyz = text.split(",");
            cells.add(
                    new ParcelCell(
                            Integer.parseInt(xyz[0]),
                            Integer.parseInt(xyz[1]),
                            Integer.parseInt(xyz[2])));
        }
        var holders = new HashSet<UUID>();
        if (!parts[2].isEmpty())
            for (var text : parts[2].split(";")) holders.add(UUID.fromString(text));
        var policy = new EnumMap<ParcelAction, Set<ParcelRelation>>(ParcelAction.class);
        int i = 3;
        for (var a : ParcelAction.values()) {
            var allowed = EnumSet.noneOf(ParcelRelation.class);
            if (!parts[i].isEmpty())
                for (var text : parts[i].split(",")) allowed.add(ParcelRelation.valueOf(text));
            policy.put(a, allowed);
            i++;
        }
        return new ParcelView(
                id, settlement, LandName.normalize(parts[0]), revision, cells, holders, policy);
    }
}
