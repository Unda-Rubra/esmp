package xdu.undarubra.esmp.civilization.activity;

public record ActivityPolicy(
        double maxScore,
        double halfLifeDays,
        double inactiveThreshold,
        double recoveryThreshold,
        double initialScore,
        double buildCredit,
        double interactionCredit,
        double publicCredit,
        double playerDailyCap,
        double settlementDailyCap,
        long graceTicks,
        long actionCooldownTicks) {
    public ActivityPolicy {
        for (double value :
                new double[] {
                    maxScore,
                    halfLifeDays,
                    inactiveThreshold,
                    recoveryThreshold,
                    initialScore,
                    buildCredit,
                    interactionCredit,
                    publicCredit,
                    playerDailyCap,
                    settlementDailyCap
                }) {
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("Activity values must be finite and positive");
            }
        }
        if (inactiveThreshold >= recoveryThreshold
                || recoveryThreshold > maxScore
                || initialScore > maxScore
                || initialScore < recoveryThreshold
                || graceTicks < 0
                || actionCooldownTicks <= 0
                || publicCredit >= interactionCredit
                || playerDailyCap > settlementDailyCap) {
            throw new IllegalArgumentException("Invalid Activity thresholds, credits or timing");
        }
    }

    public static ActivityPolicy defaults() {
        return new ActivityPolicy(100, 7, 20, 30, 30, 1, .25, .02, 5, 20, 72_000, 200);
    }
}
