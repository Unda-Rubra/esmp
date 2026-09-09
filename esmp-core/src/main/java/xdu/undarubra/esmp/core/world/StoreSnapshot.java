package xdu.undarubra.esmp.core.world;

import java.util.List;

public record StoreSnapshot(
        int schemaVersion,
        List<WorldDefinition> definitions,
        List<WorldInstance> instances) {
    public StoreSnapshot {
        definitions = List.copyOf(definitions);
        instances = List.copyOf(instances);
    }
}
