package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.civilization.activity.ActivityService.PauseInterval;
import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistence port for policy identity, credited observations, and activity projections. */
public interface ActivityRepository {
    CompletableFuture<Void> initialize();

    /** Credits an immutable, bounded batch atomically against current governance revisions. */
    CompletableFuture<Void> creditObservations(List<ActivityContribution> observations);

    /** Uses the supplied transaction; does not start another unit of work. */
    boolean isActive(Transaction transaction, UUID settlement) throws SQLException;

    CompletableFuture<ActivityStatus> status(UUID settlement);

    CompletableFuture<List<PauseInterval>> pauseIntervals(UUID settlement);

    CompletableFuture<LocalActivityView> localActivity(UUID settlement);

    CompletableFuture<Double> localSupport(
            UUID settlement, UUID world, Map<ChunkPosition, Integer> footprint, long gameTick);

    double localSupport(
            Transaction transaction,
            UUID settlement,
            UUID world,
            Map<ChunkPosition, Integer> footprint,
            long gameTick)
            throws SQLException;
}
