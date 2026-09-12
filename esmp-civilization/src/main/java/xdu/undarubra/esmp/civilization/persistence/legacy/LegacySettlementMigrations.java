package xdu.undarubra.esmp.civilization.persistence.legacy;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.jooq.DSLContext;

import xdu.undarubra.esmp.core.storage.SqlMigrations;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;

/** Original pre-handoff migrations. Call only while DatabaseAccess.legacy(tx) is true. */
public final class LegacySettlementMigrations {
    private LegacySettlementMigrations() {}

    public static void migrate(DSLContext c, GameClock clock) throws SQLException {
        SqlMigrations.run(c, "db/civilization/V000__metadata.sql");
        Long stored =
                c.select(CIVILIZATION_SCHEMA.VERSION)
                        .from(CIVILIZATION_SCHEMA)
                        .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                        .fetchOne(CIVILIZATION_SCHEMA.VERSION);
        int version = stored == null ? 0 : Math.toIntExact(stored);
        if (version == 3) return;
        if (version < 0 || version > 3) {
            throw new SQLException("Unsupported Civilization schema: " + version);
        }
        if (version == 0) {
            SqlMigrations.run(c, "db/civilization/V001__settlements.sql");
            version = 1;
        }
        if (version == 1) {
            migrateClock(c, clock);
        }
        SqlMigrations.run(c, "db/civilization/V003__governance.sql");
        migrateParcels(c);
        c.update(CIVILIZATION_SCHEMA)
                .set(CIVILIZATION_SCHEMA.VERSION, 3L)
                .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                .execute();
    }

    private static void migrateClock(DSLContext c, GameClock clock) throws SQLException {
        SqlMigrations.run(c, "db/civilization/V002__clock.sql");
        c.update(SETTLEMENTS).set(SETTLEMENTS.FOUNDED_TICK, clock.ticks()).execute();
        // One-time conversion of every legacy absolute deadline, including resolved offers.
        long cutoverMillis = System.currentTimeMillis();
        for (var row :
                c.select(SETTLEMENT_OFFERS.OFFER_ID, SETTLEMENT_OFFERS.EXPIRES_AT)
                        .from(SETTLEMENT_OFFERS)
                        .fetch()) {
            long remaining =
                    Math.max(
                            0L,
                            Math.subtractExact(
                                    row.get(SETTLEMENT_OFFERS.EXPIRES_AT), cutoverMillis));
            long ticks =
                    Math.addExact(
                            clock.ticks(),
                            Math.addExact(remaining / 50L, remaining % 50L == 0L ? 0L : 1L));
            c.update(SETTLEMENT_OFFERS)
                    .set(SETTLEMENT_OFFERS.EXPIRES_AT, ticks)
                    .where(SETTLEMENT_OFFERS.OFFER_ID.eq(row.get(SETTLEMENT_OFFERS.OFFER_ID)))
                    .execute();
        }
        c.update(CIVILIZATION_SCHEMA)
                .set(CIVILIZATION_SCHEMA.VERSION, 2L)
                .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                .execute();
    }

    public static void migrateParcels(DSLContext c) throws SQLException {
        SqlMigrations.run(c, "db/civilization/V003__parcels.sql");
    }

    public static void migrateClaimPricing(DSLContext c) {
        SqlMigrations.run(c, "db/civilization/V000__claim_metadata.sql");
        Long version =
                c.select(CLAIM_PRICING_SCHEMA.VERSION)
                        .from(CLAIM_PRICING_SCHEMA)
                        .where(CLAIM_PRICING_SCHEMA.ID.eq(1L))
                        .fetchOne(CLAIM_PRICING_SCHEMA.VERSION);
        if (version == null) SqlMigrations.run(c, "db/civilization/V001__claim_pricing.sql");
        else if (version != 1) throw new IllegalStateException("Unsupported Claim pricing schema");
    }
}
