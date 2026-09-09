package xdu.undarubra.esmp.resource;

import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;
import java.util.List;

/** World roles owned by the resource domain; gameplay is not enabled yet. */
public final class ResourceWorlds {
    private static final List<WorldDefinition> DEFINITIONS = List.of(
            new WorldDefinition("RESOURCE_OVERWORLD", "resource", WorldEnvironment.NORMAL),
            new WorldDefinition("RESOURCE_NETHER", "resource", WorldEnvironment.NETHER),
            new WorldDefinition("RESOURCE_END", "resource", WorldEnvironment.THE_END));

    private ResourceWorlds() {
    }

    public static List<WorldDefinition> definitions() {
        return DEFINITIONS;
    }
}
