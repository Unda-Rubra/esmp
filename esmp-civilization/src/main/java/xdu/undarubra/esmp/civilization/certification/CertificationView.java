package xdu.undarubra.esmp.civilization.certification;

import java.util.UUID;

public record CertificationView(
        UUID settlementId,
        long number,
        CertificationStatus status,
        String officialName,
        long ageTicks,
        double lifetimeCredit,
        boolean active,
        boolean eligible,
        long minimumAgeTicks,
        double minimumLifetimeCredit,
        CertificationApplication pendingApplication) {}
