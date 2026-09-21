package xdu.undarubra.esmp.civilization.api;

import xdu.undarubra.esmp.civilization.acquisition.AcquisitionPolicy;
import xdu.undarubra.esmp.civilization.acquisition.AcquisitionService;
import xdu.undarubra.esmp.civilization.certification.CertificationPolicy;
import xdu.undarubra.esmp.civilization.certification.CertificationService;
import xdu.undarubra.esmp.civilization.persistence.JooqAcquisitionRepository;
import xdu.undarubra.esmp.civilization.persistence.JooqCertificationRepository;
import xdu.undarubra.esmp.civilization.persistence.JooqParcelRepository;
import xdu.undarubra.esmp.civilization.persistence.JooqSettlementRepository;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.time.GameClock;

import java.util.UUID;
import java.util.function.Predicate;

/** Composition entry points for the Settlement and Parcel services only. */
public final class SettlementServices {
    private SettlementServices() {}

    public static xdu.undarubra.esmp.civilization.cycle.CycleService cycle(
            UnitOfWork db,
            GameClock clock,
            xdu.undarubra.esmp.civilization.activity.ActivityPolicy activity,
            xdu.undarubra.esmp.civilization.activity.LocalActivityPolicy local,
            xdu.undarubra.esmp.civilization.cycle.CycleService.Policy policy) {
        return new xdu.undarubra.esmp.civilization.cycle.CycleService(
                new xdu.undarubra.esmp.civilization.persistence.JooqCycleRepository(
                        db, clock, activity, local, policy));
    }

    public static SettlementService settlement(
            UnitOfWork database,
            SettlementPolicy policy,
            GameClock clock,
            Predicate<UUID> administrator,
            SettlementService.ReleaseGuard releaseGuard,
            ClaimPricingPolicy pricingPolicy,
            SettlementService.ClaimPayment payment,
            SettlementService.ClaimActivity activity) {
        return new SettlementService(
                new JooqSettlementRepository(
                        database,
                        policy,
                        clock,
                        administrator,
                        releaseGuard,
                        pricingPolicy,
                        payment,
                        activity));
    }

    public static ParcelService parcel(
            UnitOfWork database, GameClock clock, Predicate<UUID> administrator) {
        return new ParcelService(new JooqParcelRepository(database, clock, administrator));
    }

    public static CertificationService certification(
            UnitOfWork database,
            GameClock clock,
            CertificationPolicy policy,
            Predicate<UUID> administrator,
            SettlementService.ClaimActivity activity) {
        return new CertificationService(
                new JooqCertificationRepository(database, clock, policy, administrator, activity));
    }

    public static AcquisitionService acquisition(
            UnitOfWork database,
            GameClock clock,
            AcquisitionPolicy policy,
            Predicate<UUID> administrator,
            AcquisitionService.Payment payment) {
        return new AcquisitionService(
                new JooqAcquisitionRepository(database, clock, policy, administrator, payment));
    }

    public static xdu.undarubra.esmp.civilization.building.BuildingService buildings(
            UnitOfWork database,
            GameClock clock,
            xdu.undarubra.esmp.civilization.building.BuildingService.Policy policy,
            Predicate<UUID> administrator) {
        return new xdu.undarubra.esmp.civilization.building.BuildingService(
                new xdu.undarubra.esmp.civilization.persistence.JooqBuildingRepository(
                        database, clock, policy, administrator));
    }

    public static xdu.undarubra.esmp.civilization.decay.DecayService decay(
            UnitOfWork database,
            GameClock clock,
            xdu.undarubra.esmp.civilization.activity.ActivityPolicy activity,
            xdu.undarubra.esmp.civilization.activity.LocalActivityPolicy local,
            xdu.undarubra.esmp.civilization.decay.DecayService.Policy policy) {
        return new xdu.undarubra.esmp.civilization.decay.DecayService(
                new xdu.undarubra.esmp.civilization.persistence.JooqDecayRepository(
                        database, clock, activity, local, policy));
    }

    public static xdu.undarubra.esmp.civilization.repair.RepairService repair(
            UnitOfWork database,
            GameClock clock,
            xdu.undarubra.esmp.civilization.activity.ActivityPolicy activity,
            xdu.undarubra.esmp.civilization.activity.LocalActivityPolicy local,
            xdu.undarubra.esmp.civilization.decay.DecayService.Policy decay,
            xdu.undarubra.esmp.civilization.repair.RepairService.Policy policy,
            Predicate<UUID> administrator) {
        return new xdu.undarubra.esmp.civilization.repair.RepairService(
                new xdu.undarubra.esmp.civilization.persistence.JooqRepairRepository(
                        database, clock, activity, local, decay, policy, administrator));
    }
}
