package xdu.undarubra.esmp.civilization.settlement;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ParcelView(
        UUID id,
        UUID settlementId,
        String name,
        long revision,
        Set<ParcelCell> cells,
        Set<UUID> holders,
        Map<ParcelAction, Set<ParcelRelation>> policy) {
    public ParcelView {
        cells = Set.copyOf(cells);
        holders = Set.copyOf(holders);
        var copy = new EnumMap<ParcelAction, Set<ParcelRelation>>(ParcelAction.class);
        for (var action : ParcelAction.values())
            copy.put(action, Set.copyOf(policy.getOrDefault(action, Set.of())));
        policy = Map.copyOf(copy);
    }

    public boolean allows(UUID actor, Role role, ParcelAction action) {
        var allowed = policy.get(action);
        return allowed.contains(ParcelRelation.valueOf(role.name()))
                || (actor != null
                        && holders.contains(actor)
                        && allowed.contains(ParcelRelation.HOLDER));
    }

    public boolean internalAutomation() {
        return policy.get(ParcelAction.AUTOMATION)
                .contains(holders.isEmpty() ? ParcelRelation.OWNER : ParcelRelation.HOLDER);
    }
}
