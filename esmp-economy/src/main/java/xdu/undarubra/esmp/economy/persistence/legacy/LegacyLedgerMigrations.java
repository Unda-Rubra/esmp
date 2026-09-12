package xdu.undarubra.esmp.economy.persistence.legacy;

import static xdu.undarubra.esmp.economy.generated.Tables.ECONOMY_METADATA;

import org.jooq.DSLContext;

import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.SqlMigrations;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.economy.CurrencyDefinition;
import xdu.undarubra.esmp.economy.LedgerPolicy;

import java.sql.SQLException;

/** Original economy migration sequence, exclusively for databases awaiting the legacy handoff. */
public final class LegacyLedgerMigrations {
    private LegacyLedgerMigrations() {}

    public static void initialize(
            Transaction transaction,
            GameClock clock,
            CurrencyDefinition currency,
            LedgerPolicy policy)
            throws SQLException {
        if (!DatabaseAccess.legacy(transaction)) return;
        DSLContext c = DatabaseAccess.context(transaction);
        // Only metadata may be created before checking version and monetary identity.
        SqlMigrations.run(c, "db/economy/V000__metadata.sql");
        var metadata = c.selectFrom(ECONOMY_METADATA).where(ECONOMY_METADATA.ID.eq(1L)).fetchOne();
        if (metadata != null) {
            long storedVersion = metadata.get(ECONOMY_METADATA.SCHEMA_VERSION);
            if (storedVersion < 1L || storedVersion > 2L)
                throw new SQLException("Unsupported economy schema");
            int version = Math.toIntExact(storedVersion);
            if (!currency.id().equals(metadata.get(ECONOMY_METADATA.CURRENCY_ID))
                    || policy.periodTicks() != metadata.get(ECONOMY_METADATA.PERIOD_TICKS))
                throw new SQLException(
                        "Currency identity or monetary period changed; explicit migration"
                            + " required");
            if (version == 1) initializeStartingGrants(c, transaction, clock);
            return;
        }
        c.insertInto(ECONOMY_METADATA)
                .set(ECONOMY_METADATA.ID, 1L)
                .set(ECONOMY_METADATA.SCHEMA_VERSION, 1L)
                .set(ECONOMY_METADATA.CURRENCY_ID, currency.id())
                .set(ECONOMY_METADATA.PERIOD_TICKS, policy.periodTicks())
                .execute();
        SqlMigrations.run(c, "db/economy/V001__ledger.sql");
        initializeStartingGrants(c, transaction, clock);
    }

    private static void initializeStartingGrants(
            DSLContext c, Transaction transaction, GameClock clock) throws SQLException {
        SqlMigrations.run(c, "db/economy/V002__starting_grants.sql");
        clock.checkpoint(transaction);
    }
}
