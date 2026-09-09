package xdu.undarubra.esmp.civilization.activity;

public record ActivityStatus(double score, State state, Long inactiveSinceTick, boolean productionPaused,
        double recoveryNeeded, Long lastContributionTick, double creditedToday, long measuredAtTick,
        String lastCategory) {
    public enum State { ACTIVE, INACTIVE }
}
