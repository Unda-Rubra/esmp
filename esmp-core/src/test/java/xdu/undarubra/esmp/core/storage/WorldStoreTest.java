package xdu.undarubra.esmp.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;
import xdu.undarubra.esmp.core.world.WorldInstance;

@Timeout(15)
class WorldStoreTest {
    private static final WorldDefinition MAIN = new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL);
    private static final WorldDefinition RESOURCE = new WorldDefinition("RESOURCE_OVERWORLD", "resource", WorldEnvironment.NORMAL);

    @TempDir
    Path directory;

    @Test
    void identitiesSurviveRestartButReplacementWorldGetsNewIdentity() {
        Path file = directory.resolve("worlds.db");
        WorldBinding original = new WorldBinding(MAIN.id(), UUID.randomUUID(), "world");
        WorldInstance first;
        try (WorldStore store = new WorldStore(file)) {
            first = store.initialize(List.of(MAIN), List.of(original)).join().instances().getFirst();
        }
        try (WorldStore store = new WorldStore(file)) {
            var restarted = store.initialize(List.of(MAIN), List.of(original)).join();
            assertEquals(List.of(first), restarted.instances());
            assertEquals(first, store.register(original).join());
            WorldInstance replacement = store.register(new WorldBinding(MAIN.id(), UUID.randomUUID(), "world")).join();
            assertNotEquals(first.instanceId(), replacement.instanceId());
            assertTrue(store.snapshot().instances().containsAll(List.of(first, replacement)));
        }
    }

    @Test
    void conflictingPhysicalWorldRollsBackNewDefinitions() {
        Path file = directory.resolve("worlds.db");
        UUID physicalId = UUID.randomUUID();
        try (WorldStore store = new WorldStore(file)) {
            store.initialize(List.of(MAIN), List.of(new WorldBinding(MAIN.id(), physicalId, "world"))).join();
        }
        try (WorldStore conflicting = new WorldStore(file)) {
            assertThrows(CompletionException.class, () -> conflicting.initialize(
                    List.of(MAIN, RESOURCE), List.of(new WorldBinding(RESOURCE.id(), physicalId, "world"))).join());
        }
        try (WorldStore reopened = new WorldStore(file)) {
            var snapshot = reopened.initialize(List.of(MAIN), List.of()).join();
            assertEquals(List.of(MAIN), snapshot.definitions());
            assertEquals(MAIN.id(), snapshot.instances().getFirst().definitionId());
            assertEquals(physicalId, snapshot.instances().getFirst().bukkitWorldId());
        }
    }

    @Test
    void newerSchemaIsRejectedWithoutDowngradingIt() throws Exception {
        Path file = directory.resolve("worlds.db");
        try (Connection connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file, new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }
        try (WorldStore store = new WorldStore(file)) {
            assertThrows(CompletionException.class, () -> store.initialize(List.of(MAIN), List.of()).join());
        }
        try (Connection connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file, new Properties());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(result.next());
            assertEquals(99, result.getInt(1));
        }
    }

    @Test
    void shutdownDrainsAcceptedRegistrationsAndRejectsLaterWork() {
        Path file = directory.resolve("worlds.db");
        WorldStore store = new WorldStore(file);
        store.initialize(List.of(RESOURCE), List.of()).join();
        var first = store.register(new WorldBinding(RESOURCE.id(), UUID.randomUUID(), "resource"));
        var second = store.register(new WorldBinding(RESOURCE.id(), UUID.randomUUID(), "resource"));
        store.close();
        assertThrows(CompletionException.class,
                () -> store.register(new WorldBinding(RESOURCE.id(), UUID.randomUUID(), "resource")).join());
        try (WorldStore reopened = new WorldStore(file)) {
            var snapshot = reopened.initialize(List.of(RESOURCE), List.of()).join();
            assertEquals(2, snapshot.instances().size());
            assertTrue(snapshot.instances().containsAll(List.of(first.join(), second.join())));
        }
    }
}
