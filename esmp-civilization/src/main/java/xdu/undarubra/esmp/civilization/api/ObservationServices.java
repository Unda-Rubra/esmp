package xdu.undarubra.esmp.civilization.api;

import xdu.undarubra.esmp.civilization.activity.ActivityPolicy;
import xdu.undarubra.esmp.civilization.activity.ActivityService;
import xdu.undarubra.esmp.civilization.activity.LocalActivityPolicy;
import xdu.undarubra.esmp.civilization.land.LandValuePolicy;
import xdu.undarubra.esmp.civilization.land.LandValueService;
import xdu.undarubra.esmp.civilization.persistence.JooqActivityRepository;
import xdu.undarubra.esmp.civilization.persistence.JooqLandValueRepository;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;

import java.util.UUID;
import java.util.function.Predicate;

/** Public composition entry points for civilization observation services. */
public final class ObservationServices {
    private ObservationServices() {}

    public static ActivityService activity(
            WorldStore database,
            GameClock clock,
            ActivityPolicy policy,
            LocalActivityPolicy localPolicy,
            xdu.undarubra.esmp.civilization.decay.DecayService.Policy decayPolicy,
            Predicate<UUID> administrator) {
        return new ActivityService(
                new JooqActivityRepository(
                        database, clock, policy, localPolicy, decayPolicy, administrator));
    }

    public static LandValueService land(WorldStore database, LandValuePolicy policy) {
        return new LandValueService(new JooqLandValueRepository(database, policy));
    }
}
