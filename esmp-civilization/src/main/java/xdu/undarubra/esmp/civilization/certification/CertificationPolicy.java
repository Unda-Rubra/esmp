package xdu.undarubra.esmp.civilization.certification;

public record CertificationPolicy(long minimumAgeTicks, double minimumLifetimeCredit) {
    public CertificationPolicy {
        if (minimumAgeTicks < 0
                || !Double.isFinite(minimumLifetimeCredit)
                || minimumLifetimeCredit < 0)
            throw new IllegalArgumentException("Invalid certification eligibility policy");
    }

    public static CertificationPolicy defaults() {
        return new CertificationPolicy(72000, 20.0);
    }
}
