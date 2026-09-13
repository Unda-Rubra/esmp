package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.core.time.GameClock;

/** Pure elapsed-game-time projection shared by activity persistence reads and credits. */
public final class ActivityProjection {
    private ActivityProjection() {}

    public record Value(
            double score,
            long anchor,
            Long inactive,
            long day,
            double credited,
            Long last,
            String category) {}

    public static Value project(Value value, long now, ActivityPolicy policy) {
        double score = decay(value.score(), value.anchor(), now, policy.halfLifeDays());
        Long inactive = value.inactive();
        if (inactive == null && score < policy.inactiveThreshold()) {
            double crossing =
                    policy.halfLifeDays()
                            * GameClock.TICKS_PER_DAY
                            * (Math.log(value.score() / policy.inactiveThreshold()) / Math.log(2));
            long offset = Math.max(0, (long) Math.floor(crossing) + 1);
            inactive = Math.min(now, Math.addExact(value.anchor(), offset));
        }
        return new Value(
                score,
                now,
                inactive,
                value.day(),
                value.credited(),
                value.last(),
                value.category());
    }

    public static double decay(double score, long anchor, long now, double halfLifeDays) {
        long elapsed = Math.max(0, now - anchor);
        return score * Math.pow(.5, elapsed / (halfLifeDays * GameClock.TICKS_PER_DAY));
    }

    public static boolean paused(Long inactive, long now, ActivityPolicy policy) {
        return inactive != null && now - inactive >= policy.graceTicks();
    }
}
