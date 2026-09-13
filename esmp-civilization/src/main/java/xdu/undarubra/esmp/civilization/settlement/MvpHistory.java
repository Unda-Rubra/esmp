package xdu.undarubra.esmp.civilization.settlement;

import java.util.*;

/**
 * Stable semantic read catalog over committed History rows; never a chat archive or authority API.
 */
public final class MvpHistory {
    private MvpHistory() {}

    public enum Kind {
        SETTLEMENT_FOUNDED,
        CERTIFICATION_APPROVED,
        CERTIFICATION_DENIED,
        OFFICIAL_NAME_CHANGED,
        CLAIM_ACQUIRED,
        CLAIM_RELEASED,
        OWNERSHIP_TRANSFERRED,
        BUILDING_REGISTERED,
        BUILDING_RETIRED,
        DECAY_STARTED,
        DECAY_APPLIED,
        BUILDING_RUINED,
        REPAIR_STARTED,
        REPAIR_COMPLETED,
        ERA_ADVANCED
    }

    public record Fact(Kind kind, SettlementEvent event) {}

    private static final Map<String, Kind> TYPES =
            Map.ofEntries(
                    Map.entry("FOUNDED", Kind.SETTLEMENT_FOUNDED),
                    Map.entry("CERTIFICATION_APPROVED", Kind.CERTIFICATION_APPROVED),
                    Map.entry("CERTIFICATION_DENIED", Kind.CERTIFICATION_DENIED),
                    Map.entry("RENAMED", Kind.OFFICIAL_NAME_CHANGED),
                    Map.entry("CHUNK_CLAIMED", Kind.CLAIM_ACQUIRED),
                    Map.entry("CHUNK_UNCLAIMED", Kind.CLAIM_RELEASED),
                    Map.entry("RELEASED", Kind.CLAIM_RELEASED),
                    Map.entry("OWNERSHIP_TRANSFERRED", Kind.OWNERSHIP_TRANSFERRED),
                    Map.entry("BUILDING_REGISTERED", Kind.BUILDING_REGISTERED),
                    Map.entry("BUILDING_RETIRED", Kind.BUILDING_RETIRED),
                    Map.entry("DECAY_STARTED", Kind.DECAY_STARTED),
                    Map.entry("DECAY_APPLIED", Kind.DECAY_APPLIED),
                    Map.entry("BUILDING_RUINED", Kind.BUILDING_RUINED),
                    Map.entry("REPAIR_STARTED", Kind.REPAIR_STARTED),
                    Map.entry("REPAIR_COMPLETED", Kind.REPAIR_COMPLETED),
                    Map.entry("ERA_ADVANCED", Kind.ERA_ADVANCED));

    public static List<Fact> facts(List<SettlementEvent> events) {
        var result = new ArrayList<Fact>();
        for (var event : events) {
            var kind = TYPES.get(event.type());
            if (kind != null) result.add(new Fact(kind, event));
        }
        return List.copyOf(result);
    }

    public static Map<String, Kind> catalog() {
        return TYPES;
    }
}
