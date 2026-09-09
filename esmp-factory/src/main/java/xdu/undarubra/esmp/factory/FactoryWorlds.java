package xdu.undarubra.esmp.factory;

import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;
import java.util.List;

/** World roles owned by the factory domain; gameplay is not enabled yet. */
public final class FactoryWorlds {
    private static final List<WorldDefinition> DEFINITIONS = List.of(
            new WorldDefinition("FACTORY", "factory", WorldEnvironment.NORMAL));

    private FactoryWorlds() {
    }

    public static List<WorldDefinition> definitions() {
        return DEFINITIONS;
    }
}
