package xdu.undarubra.esmp.core.time;

import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.storage.WorldStore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Neutral monotonic source-delta clock. The adapter supplies source identity and advancement
 * policy.
 */
public final class GameClock {
    public static final long TICKS_PER_DAY = 24_000;

    private record Position(UUID source, long raw, long ticks, boolean advancing) {}

    private final WorldStore database;
    private final ClockRepository repository;
    private volatile Position position;

    public GameClock(WorldStore database) {
        this.database = database;
        this.repository = database.clocks();
    }

    public CompletableFuture<Void> initialize(UUID source, long rawTime, boolean advancing) {
        return database.transact(
                transaction -> {
                    ClockRepository.State stored = repository.initialize(transaction, source);
                    // Re-anchor on boot; downtime is never inferred from wall time or unsaved
                    // world time. The repository validates source identity even on fresh schemas.
                    long ticks = stored == null ? 0 : stored.ticks();
                    position = new Position(source, rawTime, ticks, advancing);
                    checkpoint(transaction);
                    return null;
                });
    }

    public void sample(UUID source, long rawTime, boolean advancing) {
        Position old = requirePosition();
        if (!old.source().equals(source)) {
            throw new IllegalStateException("Reckoning source identity changed");
        }
        long delta =
                advancing && old.advancing() && rawTime > old.raw()
                        ? Math.subtractExact(rawTime, old.raw())
                        : 0;
        position = new Position(source, rawTime, Math.addExact(old.ticks(), delta), advancing);
    }

    public long ticks() {
        Position current = position;
        return current == null ? 0 : current.ticks();
    }

    public boolean advancing() {
        Position current = position;
        return current != null && current.advancing();
    }

    public void checkpoint(Transaction transaction) {
        Position current = requirePosition();
        repository.save(
                transaction,
                new ClockRepository.State(current.source(), current.raw(), current.ticks()));
    }

    public CompletableFuture<Void> flush() {
        return database.transact(
                transaction -> {
                    checkpoint(transaction);
                    return null;
                });
    }

    private Position requirePosition() {
        Position current = position;
        if (current == null) {
            throw new IllegalStateException("Reckoning clock is not initialized");
        }
        return current;
    }
}
