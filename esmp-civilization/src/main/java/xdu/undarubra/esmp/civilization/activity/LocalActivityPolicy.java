package xdu.undarubra.esmp.civilization.activity;

/** Read-time tuning; stored scores use the existing aggregate half-life and admitted credits. */
public record LocalActivityPolicy(double neighborWeight, double supportThreshold) {
    public static final int VERSION = 1;

    public LocalActivityPolicy {
        if (!Double.isFinite(neighborWeight)
                || neighborWeight < 0
                || neighborWeight > .25
                || !Double.isFinite(supportThreshold)
                || supportThreshold <= 0) {
            throw new IllegalArgumentException(
                    "Local Activity requires neighbor weight 0..0.25 and a positive finite"
                        + " threshold");
        }
    }

    public static LocalActivityPolicy defaults() {
        return new LocalActivityPolicy(.25, 20);
    }
}
