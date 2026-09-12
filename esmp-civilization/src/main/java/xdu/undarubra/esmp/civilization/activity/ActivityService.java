package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public activity facade; only credited actions write scores. */
public final class ActivityService {
    private final ActivityRepository repository;

    public record PauseInterval(long fromTick, Long untilTick) {}

    public ActivityService(ActivityRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<Void> initialize() {
        return repository.initialize();
    }

    public CompletableFuture<Void> contribute(List<ActivityContribution> observations) {
        if (observations.size() > 256) {
            throw new IllegalArgumentException("Activity batch exceeds 256 observations");
        }
        return repository.creditObservations(List.copyOf(observations));
    }

    /** Projects activity in the caller's transaction without writing scores or checkpoints. */
    public boolean isActive(Transaction transaction, UUID settlement) throws SQLException {
        return repository.isActive(transaction, settlement);
    }

    public CompletableFuture<ActivityStatus> status(UUID id) {
        return repository.status(id);
    }

    /**
     * Closed and current pause intervals; future production must exclude them, not just inspect
     * today's boolean.
     */
    public CompletableFuture<List<PauseInterval>> pauseIntervals(UUID id) {
        return repository.pauseIntervals(id);
    }
}
