package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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

    public CompletableFuture<LocalActivityView> localActivity(UUID settlement) {
        return repository.localActivity(Objects.requireNonNull(settlement));
    }

    /**
     * Read-only area-weighted support at shared game time. Each weight is 1..256 footprint block
     * columns in a claimed chunk. Future projection is allowed; pre-anchor history is not inferred.
     * Every chunk must belong to this Settlement and world instance.
     */
    public CompletableFuture<Double> localSupport(
            UUID settlement, UUID world, Map<ChunkPosition, Integer> footprint, long gameTick) {
        validateFootprint(settlement, world, footprint, gameTick);
        return repository.localSupport(settlement, world, Map.copyOf(footprint), gameTick);
    }

    /** Same projection inside the consumer's existing transaction, without a nested UnitOfWork. */
    public double localSupport(
            Transaction transaction,
            UUID settlement,
            UUID world,
            Map<ChunkPosition, Integer> footprint,
            long gameTick)
            throws SQLException {
        validateFootprint(settlement, world, footprint, gameTick);
        return repository.localSupport(transaction, settlement, world, footprint, gameTick);
    }

    private static void validateFootprint(
            UUID settlement, UUID world, Map<ChunkPosition, Integer> footprint, long gameTick) {
        Objects.requireNonNull(settlement);
        Objects.requireNonNull(world);
        Objects.requireNonNull(footprint);
        if (gameTick < 0 || footprint.isEmpty())
            throw new IllegalArgumentException(
                    "Nonempty footprint and nonnegative game tick required");
        footprint.forEach(
                (chunk, weight) -> {
                    if (chunk == null || weight == null || weight < 1 || weight > 256)
                        throw new IllegalArgumentException(
                                "Footprint areas must be 1..256 block columns per chunk");
                });
    }
}
