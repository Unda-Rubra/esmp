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
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = c.createStatement();
                var r = s.executeQuery("SELECT count(*) FROM DATABASECHANGELOG")) {
            assertTrue(r.next());
            assertEquals(3, r.getInt(1));
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
            try (var r = s.executeQuery("SELECT count(*) FROM DATABASECHANGELOG")) {
                assertTrue(r.next());
                assertEquals(3, r.getInt(1));
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
