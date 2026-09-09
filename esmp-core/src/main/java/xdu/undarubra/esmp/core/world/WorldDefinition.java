package xdu.undarubra.esmp.core.world;

import java.util.Objects;

public record WorldDefinition(String id, String moduleId, WorldEnvironment environment) {
    public WorldDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(environment, "environment");
        if (!id.matches("[A-Z][A-Z0-9_]*") || !moduleId.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid world definition or module ID");
        }
    }
}
