package xdu.undarubra.esmp.plugin.architecture;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.core.storage.*;
import xdu.undarubra.esmp.core.world.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.CompletionException;

class SchemaMigrationTest {
    @TempDir Path directory;
    private final UUID source = UUID.randomUUID();

    private void initialize(WorldStore store) {
        store.initialize(
                        List.of(
                                new WorldDefinition(
                                        "MAIN", "civilization", WorldEnvironment.NORMAL)),
                        List.of(new WorldBinding("MAIN", source, "world")))
                .join();
    }

    private void legacy(Path file, boolean badCheck) throws Exception {
        String[] resources = {
            "core/V001__world.sql",
            "core/V001__clock.sql",
            "civilization/V000__metadata.sql",
            "civilization/V001__settlements.sql",
            "civilization/V002__clock.sql",
            "civilization/V003__governance.sql",
            "civilization/V003__parcels.sql",
            "civilization/V001__activity.sql",
            "civilization/V001__land_value.sql",
            "civilization/V000__claim_metadata.sql",
            "civilization/V001__claim_pricing.sql",
            "economy/V000__metadata.sql",
            "economy/V001__ledger.sql",
            "economy/V002__starting_grants.sql"
        };
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement()) {
            for (String resource : resources)
                try (var input = getClass().getResourceAsStream("/db/" + resource)) {
                    assertNotNull(input, resource);
                    String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    if (badCheck)
                        sql = sql.replace("CHECK(typeof(balance)='integer' AND balance>=0)", "");
                    s.executeUpdate(sql);
                }
            s.executeUpdate("UPDATE civilization_schema SET version=3; PRAGMA user_version=1;");
            s.executeUpdate("INSERT INTO economy_metadata VALUES(1,2,'esmp:coin',168000);");
            s.executeUpdate(
                    "INSERT INTO economy_accounts VALUES('WALLET:kept','esmp:coin','WALLET','"
                            + UUID.randomUUID()
                            + "',9223372036854775807);");
            UUID world = UUID.randomUUID();
            s.executeUpdate("INSERT INTO world_definitions VALUES('MAIN','civilization','NORMAL')");
            s.executeUpdate(
                    "INSERT INTO world_instances VALUES('"
                            + world
                            + "','MAIN','"
                            + source
                            + "','world','1970-01-01T00:00:00Z')");
            for (int i = 1; i <= 3; i++) {
                UUID settlement = UUID.randomUUID();
                s.executeUpdate(
                        "INSERT INTO"
                            + " settlements(settlement_id,world_instance_id,name,name_key,owner_uuid,created_at,ownership_revision,chunk_revision,status,founded_tick)"
                            + " VALUES('"
                                + settlement
                                + "','"
                                + world
                                + "','Legacy"
                                + i
                                + "','legacy"
                                + i
                                + "','"
                                + UUID.randomUUID()
                                + "',0,1,1,'OWNED',0)");
                s.executeUpdate(
                        "INSERT INTO settlement_chunks VALUES('"
                                + world
                                + "',"
                                + i
                                + ",0,'"
                                + settlement
                                + "')");
                s.executeUpdate(
                        "INSERT INTO settlement_details VALUES('"
                                + settlement
                                + "',"
                                + i
                                + ","
                                + (i == 1 ? "'Legacy1'" : "NULL")
                                + ","
                                + (i == 2 ? 1 : 0)
                                + ",0)");
            }
        }
    }

    @Test
    void emptyDatabaseAndReopenUseLiquibaseWithoutLegacyBridge() throws Exception {
        Path file = directory.resolve("empty.db");
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            assertPhaseTwoSchema(c);
        }
    }

    @Test
    void verifiedLegacyBaselinePreservesLongBalancesAndBecomesLiquibaseOwned() throws Exception {
        Path file = directory.resolve("legacy.db");
        legacy(file, false);
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement()) {
            try (var r =
                    s.executeQuery(
                            "SELECT balance FROM economy_accounts WHERE"
                                    + " account_key='WALLET:kept'")) {
                assertTrue(r.next());
                assertEquals(Long.MAX_VALUE, r.getLong(1));
            }
            assertPhaseTwoSchema(c);
            try (var r =
                    s.executeQuery(
                            "SELECT number,certification_status,official_name FROM"
                                    + " settlement_details ORDER BY number")) {
                assertTrue(r.next());
                assertEquals("VERIFIED", r.getString(2));
                assertEquals("Legacy1", r.getString(3));
                assertTrue(r.next());
                assertEquals("VERIFIED", r.getString(2));
                assertNull(r.getString(3));
                assertTrue(r.next());
                assertEquals("UNVERIFIED", r.getString(2));
            }
            assertThrows(
                    java.sql.SQLException.class,
                    () -> s.executeUpdate("UPDATE economy_accounts SET balance=-1"));
        }
    }

    @Test
    void alteredLegacyConstraintIsRejectedBeforeAnyBaselineMarking() throws Exception {
        Path file = directory.resolve("corrupt.db");
        legacy(file, true);
        try (var store = new WorldStore(file)) {
            initialize(store);
            assertThrows(CompletionException.class, () -> store.finishMigration().join());
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement();
                var r =
                        s.executeQuery(
                                "SELECT count(*) FROM sqlite_master WHERE"
                                        + " lower(name)='databasechangelog'")) {
            assertTrue(r.next());
            assertEquals(0, r.getInt(1));
        }
    }

    @Test
    void previousPhaseCancelsOnlyPendingTransfersAndPreservesAcceptedHistory() throws Exception {
        Path file = directory.resolve("phase2.db");
        legacy(file, false);
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        // Reconstruct the exact previous forward schema, retaining its Liquibase history.
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement()) {
            s.executeUpdate("DROP TABLE settlement_acquisitions");
            s.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='phase3-acquisition'");
            s.executeUpdate("DROP TABLE settlement_local_activity");
            s.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='phase4-local-activity'");
            for (String table : List.of("building_operations", "building_revisions", "buildings"))
                s.executeUpdate("DROP TABLE " + table);
            s.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='phase5-buildings'");
            for (String state : List.of("PENDING", "ACCEPTED")) {
                s.executeUpdate(
                        "INSERT INTO settlement_offers"
                            + "(offer_id,settlement_id,type,issuer_uuid,recipient_uuid,expires_at,ownership_revision,chunk_revision,state)"
                            + " SELECT '"
                                + UUID.randomUUID()
                                + "',settlement_id,'TRANSFER',owner_uuid,"
                                + "owner_uuid,36000,ownership_revision,chunk_revision,'"
                                + state
                                + "' FROM settlements WHERE name='Legacy1'");
            }
            s.executeUpdate(
                    "INSERT INTO settlement_offers"
                            + "(offer_id,settlement_id,type,issuer_uuid,recipient_uuid,expires_at,"
                            + "ownership_revision,chunk_revision,state)"
                            + " SELECT '"
                            + UUID.randomUUID()
                            + "',settlement_id,'INVITE',owner_uuid,"
                            + "owner_uuid,36000,ownership_revision,chunk_revision,'PENDING'"
                            + " FROM settlements WHERE name='Legacy1'");
        }
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement();
                var rows =
                        s.executeQuery(
                                "SELECT type,state FROM settlement_offers ORDER BY type,state")) {
            assertTrue(rows.next());
            assertEquals("INVITE", rows.getString(1));
            assertEquals("PENDING", rows.getString(2));
            assertTrue(rows.next());
            assertEquals("TRANSFER", rows.getString(1));
            assertEquals("ACCEPTED", rows.getString(2));
            assertTrue(rows.next());
            assertEquals("TRANSFER", rows.getString(1));
            assertEquals("CANCELLED", rows.getString(2));
            assertFalse(rows.next());
        }
    }

    @Test
    void phaseThreeUpgradeDoesNotInventSpatialHistoryOrChangeAggregateScores() throws Exception {
        Path file = directory.resolve("phase3.db");
        legacy(file, false);
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement()) {
            s.executeUpdate("DROP TABLE settlement_local_activity");
            s.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='phase4-local-activity'");
            for (String table : List.of("building_operations", "building_revisions", "buildings"))
                s.executeUpdate("DROP TABLE " + table);
            s.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='phase5-buildings'");
            s.executeUpdate(
                    "INSERT INTO"
                        + " settlement_activity(settlement_id,score,anchor_tick,day,credited,category)"
                        + " SELECT settlement_id,42.5,1000,0,2.5,'BUILD' FROM settlements");
        }
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT count(*) FROM settlement_local_activity")) {
                assertTrue(r.next());
                assertEquals(0, r.getInt(1));
            }
            try (var r =
                    s.executeQuery("SELECT score,anchor_tick,credited FROM settlement_activity")) {
                int count = 0;
                while (r.next()) {
                    count++;
                    assertEquals(42.5, r.getDouble(1));
                    assertEquals(1000, r.getLong(2));
                    assertEquals(2.5, r.getDouble(3));
                }
                assertEquals(3, count);
            }
        }
    }

    @Test
    void phaseFourUpgradePreservesLocalActivityWithoutInventingBuildings() throws Exception {
        Path file = directory.resolve("phase4.db");
        legacy(file, false);
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement()) {
            for (String table : List.of("building_operations", "building_revisions", "buildings"))
                s.executeUpdate("DROP TABLE " + table);
            s.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='phase5-buildings'");
            s.executeUpdate(
                    "INSERT INTO settlement_local_activity SELECT"
                            + " settlement_id,world_instance_id,chunk_x,chunk_z,17.5,1000,1 FROM"
                            + " settlement_chunks");
        }
        try (var store = new WorldStore(file)) {
            initialize(store);
            store.finishMigration().join();
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT count(*) FROM buildings")) {
                assertTrue(r.next());
                assertEquals(0, r.getInt(1));
            }
            try (var r =
                    s.executeQuery(
                            "SELECT count(*) FROM settlement_local_activity WHERE"
                                    + " score_at_anchor=17.5 AND anchor_game_tick=1000 AND"
                                    + " policy_version=1")) {
                assertTrue(r.next());
                assertEquals(3, r.getInt(1));
            }
        }
    }

    private static void assertPhaseTwoSchema(java.sql.Connection c) throws Exception {
        try (var old = c.getMetaData().getColumns(null, null, "settlement_details", "certified")) {
            assertFalse(old.next(), "The retired toggle column must not remain writable.");
        }
        try (var column =
                c.getMetaData()
                        .getColumns(null, null, "settlement_details", "certification_status")) {
            assertTrue(column.next());
            assertEquals(java.sql.DatabaseMetaData.columnNoNulls, column.getInt("NULLABLE"));
            assertTrue(column.getString("COLUMN_DEF").contains("UNVERIFIED"));
        }
        try (var s = c.createStatement();
                var r = s.executeQuery("SELECT count(*) FROM settlement_activity_lifetime")) {
            assertTrue(r.next());
            assertEquals(0, r.getInt(1), "Migration must not fabricate lifetime credit.");
        }
        try (var s = c.createStatement();
                var r =
                        s.executeQuery(
                                "SELECT ID FROM DATABASECHANGELOG WHERE"
                                        + " ID='phase2-certification'")) {
            assertTrue(r.next(), "Forward migration must execute after baseline synchronization.");
        }
    }

    @Test
    void opaqueTransactionCannotBeUsedAfterCommitOrOnAnotherThread() {
        try (var store = new WorldStore(directory.resolve("scope.db"))) {
            initialize(store);
            var tx = store.transact(transaction -> transaction).join();
            assertThrows(IllegalStateException.class, () -> DatabaseAccess.context(tx));
            store.transact(
                            transaction -> {
                                var failed =
                                        java.util.concurrent.CompletableFuture.runAsync(
                                                () -> DatabaseAccess.context(transaction));
                                assertThrows(CompletionException.class, failed::join);
                                assertThrows(
                                        CompletionException.class,
                                        () -> store.transact(nested -> null).join());
                                return null;
                            })
                    .join();
        }
    }
}
