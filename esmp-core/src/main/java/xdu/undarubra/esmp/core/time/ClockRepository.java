package xdu.undarubra.esmp.core.time;

import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.util.UUID;

/** Clock persistence port. Every operation uses the caller's opaque owning transaction. */
public interface ClockRepository {
    record State(UUID source, long raw, long ticks) {}

    /**
     * Prepares legacy storage if necessary, validates the source identity, and returns the stored
     * checkpoint, or null if no checkpoint exists.
     */
    State initialize(Transaction transaction, UUID source) throws Exception;

    /** Returns the stored checkpoint, or null if none exists. */
    State load(Transaction transaction);

    void save(Transaction transaction, State state);
}
