package xdu.undarubra.esmp.core.persistence.legacy;

import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.SqlMigrations;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.sql.SQLException;

/**
 * Compatibility bridge for existing databases without Liquibase metadata.
 *
 * <p>The old world schema used SQLite user_version as its sole version marker. Reading and updating
 * that marker is the narrow legacy-only exception to avoiding raw PRAGMA statements. Fresh and
 * already Liquibase-managed databases never run this bridge; normal connection settings are
 * configured through SQLiteConfig, and Liquibase owns subsequent schema history.
 */
public final class LegacyWorldMigration {
    private static final int SCHEMA_VERSION = 1;

    private LegacyWorldMigration() {}

    public static void migrate(Transaction transaction) throws Exception {
        if (!DatabaseAccess.legacy(transaction)) {
            throw new IllegalStateException("Legacy world migration requires a legacy database");
        }
        var context = DatabaseAccess.context(transaction);
        var result = context.fetch("PRAGMA user_version");
        if (result.size() != 1) {
            throw new SQLException("Could not read legacy ESMP schema version");
        }
        Integer version = result.get(0).get(0, Integer.class);
        if (version == null || version < 0 || version > SCHEMA_VERSION) {
            throw new SQLException("Unsupported ESMP schema version: " + version);
        }
        if (version == 0) {
            SqlMigrations.run(context, "db/core/V001__world.sql");
            context.execute("PRAGMA user_version = 1");
        }
    }
}
