package xdu.undarubra.esmp.core.persistence;

import static xdu.undarubra.esmp.core.generated.Tables.GAME_CLOCK;

import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.SqlMigrations;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.ClockRepository;

import java.sql.SQLException;
import java.util.UUID;

/** SQLite clock adapter; all queries use the caller's transaction-local context. */
public final class JooqClockRepository implements ClockRepository {
    @Override
    public State initialize(Transaction transaction, UUID source) throws Exception {
        if (DatabaseAccess.legacy(transaction)) {
            initializeLegacy(transaction);
        }
        State stored = load(transaction);
        if (stored != null && !source.equals(stored.source())) {
            throw new SQLException(
                    "Reckoning source changed; restore the matching Main"
                            + " World/database checkpoint before starting");
        }
        return stored;
    }

    /** Only pre-Liquibase databases need the retained, idempotent clock DDL. */
    private void initializeLegacy(Transaction transaction) throws Exception {
        if (!DatabaseAccess.legacy(transaction)) {
            throw new IllegalStateException("Legacy clock migration requires a legacy database");
        }
        SqlMigrations.run(DatabaseAccess.context(transaction), "db/core/V001__clock.sql");
    }

    @Override
    public State load(Transaction transaction) {
        var stored =
                DatabaseAccess.context(transaction)
                        .selectFrom(GAME_CLOCK)
                        .where(GAME_CLOCK.ID.eq(1L))
                        .fetchOne();
        return stored == null
                ? null
                : new State(stored.getSourceUuid(), stored.getRawTick(), stored.getGameTick());
    }

    @Override
    public void save(Transaction transaction, State state) {
        DatabaseAccess.context(transaction)
                .insertInto(GAME_CLOCK)
                .set(GAME_CLOCK.ID, 1L)
                .set(GAME_CLOCK.SOURCE_UUID, state.source())
                .set(GAME_CLOCK.RAW_TICK, state.raw())
                .set(GAME_CLOCK.GAME_TICK, state.ticks())
                .onConflict(GAME_CLOCK.ID)
                .doUpdate()
                .set(GAME_CLOCK.SOURCE_UUID, state.source())
                .set(GAME_CLOCK.RAW_TICK, state.raw())
                .set(GAME_CLOCK.GAME_TICK, state.ticks())
                .execute();
    }
}
