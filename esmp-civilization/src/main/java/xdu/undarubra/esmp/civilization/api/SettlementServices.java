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
}
