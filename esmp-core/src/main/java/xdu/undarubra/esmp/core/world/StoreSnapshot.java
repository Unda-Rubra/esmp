package xdu.undarubra.esmp.core.world;

import java.util.List;

/** Current module definitions and their instance history; retired definitions remain stored but are excluded. */
public record StoreSnapshot(
        int schemaVersion,
        List<WorldDefinition> definitions,
        List<WorldInstance> instances) {
    public StoreSnapshot {
        definitions = List.copyOf(definitions);
        instances = List.copyOf(instances);
    }
}
