package xdu.undarubra.esmp.civilization;

import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;
import java.util.List;

/** World roles owned by the civilization domain; gameplay is not enabled yet. */
public final class CivilizationWorlds {
    private static final List<WorldDefinition> DEFINITIONS = List.of(
            new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL));

    private CivilizationWorlds() {
    }

    public static List<WorldDefinition> definitions() {
        return DEFINITIONS;
    }
}
