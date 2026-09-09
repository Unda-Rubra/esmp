package xdu.undarubra.esmp.core.storage;

import xdu.undarubra.esmp.core.world.StoreSnapshot;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;
import xdu.undarubra.esmp.core.world.WorldInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Serial, asynchronous SQLite ownership. Never accepts or reads Paper objects. */
public final class WorldStore implements AutoCloseable {
    private static final int SCHEMA_VERSION = 1;
    private final Path databasePath;
    private final ExecutorService worker;
    private final Semaphore admission = new Semaphore(256);
    private volatile StoreSnapshot snapshot = new StoreSnapshot(0, List.of(), List.of());
    private volatile Thread workerThread;
    private Connection connection;
    private boolean initializationRequested;
    private boolean ready;
    private CompletableFuture<Void> closing;

    public WorldStore(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "esmp-sqlite");
            thread.setDaemon(true);
            workerThread = thread;
            return thread;
        });
    }

    public synchronized CompletableFuture<StoreSnapshot> initialize(
            List<WorldDefinition> definitions, List<WorldBinding> bindings) {
        if (initializationRequested || closing != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Store already initialized or closing"));
        }
        List<WorldDefinition> capturedDefinitions = List.copyOf(definitions);
        List<WorldBinding> capturedBindings = List.copyOf(bindings);
        initializationRequested = true;
        return enqueue(() -> {
            try {
                validateInput(capturedDefinitions, capturedBindings);
                Files.createDirectories(databasePath.getParent());
                connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + databasePath, new java.util.Properties());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                    statement.execute("PRAGMA busy_timeout = 5000");
                    statement.execute("PRAGMA journal_mode = WAL");
                }
                transaction(() -> {
                    migrate();
                    for (WorldDefinition definition : capturedDefinitions) {
                        persistDefinition(definition);
                    }
                    for (WorldBinding binding : capturedBindings) {
                        persistBinding(binding);
                    }
                    return null;
                });
                snapshot = readSnapshot();
                ready = true;
                return snapshot;
            } catch (Exception failure) {
                closeConnection(failure);
                throw failure;
            }
        });
    }

    public synchronized CompletableFuture<WorldInstance> register(WorldBinding binding) {
        if (!initializationRequested || closing != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Store not initialized or closing"));
        }
        return enqueue(() -> {
            if (!ready) {
                throw new IllegalStateException("Store initialization did not succeed");
            }
            WorldInstance instance = transaction(() -> persistBinding(binding));
            snapshot = readSnapshot();
            return instance;
        });
    }

    public StoreSnapshot snapshot() {
        return snapshot;
    }

    private static void validateInput(List<WorldDefinition> definitions, List<WorldBinding> bindings) {
        var ids = new HashSet<String>();
        for (WorldDefinition definition : definitions) {
            if (!ids.add(definition.id())) {
                throw new IllegalArgumentException("Duplicate world definition: " + definition.id());
            }
        }
        var boundDefinitions = new HashSet<String>();
        var worldIds = new HashSet<UUID>();
        for (WorldBinding binding : bindings) {
            if (!ids.contains(binding.definitionId())
                    || !boundDefinitions.add(binding.definitionId())
                    || !worldIds.add(binding.bukkitWorldId())) {
                throw new IllegalArgumentException("Unknown or duplicate world binding: " + binding.definitionId());
            }
        }
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            version = result.getInt(1);
        }
        if (version < 0 || version > SCHEMA_VERSION) {
            throw new SQLException("Unsupported ESMP schema version: " + version);
        }
        if (version == 0) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE world_definitions (
                            world_definition_id TEXT PRIMARY KEY,
                            module_id TEXT NOT NULL,
                            environment TEXT NOT NULL CHECK (environment IN ('NORMAL', 'NETHER', 'THE_END'))
                        )
                        """);
                statement.execute("""
                        CREATE TABLE world_instances (
                            world_instance_id TEXT PRIMARY KEY,
                            world_definition_id TEXT NOT NULL REFERENCES world_definitions(world_definition_id),
                            bukkit_world_uuid TEXT NOT NULL UNIQUE,
                            world_name TEXT NOT NULL,
                            created_at TEXT NOT NULL
                        )
                        """);
                statement.execute("CREATE INDEX world_instances_definition ON world_instances(world_definition_id)");
                statement.execute("PRAGMA user_version = 1");
            }
        }
    }

    private void persistDefinition(WorldDefinition definition) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT module_id, environment FROM world_definitions WHERE world_definition_id = ?")) {
            query.setString(1, definition.id());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    if (!definition.moduleId().equals(result.getString(1))
                            || !definition.environment().name().equals(result.getString(2))) {
                        throw new SQLException("World definition changed identity: " + definition.id());
                    }
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO world_definitions VALUES (?, ?, ?)")) {
            insert.setString(1, definition.id());
            insert.setString(2, definition.moduleId());
            insert.setString(3, definition.environment().name());
            insert.executeUpdate();
        }
    }

    private WorldInstance persistBinding(WorldBinding binding) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM world_instances WHERE bukkit_world_uuid = ?")) {
            query.setString(1, binding.bukkitWorldId().toString());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    WorldInstance existing = instanceFrom(result);
                    if (!existing.definitionId().equals(binding.definitionId())) {
                        throw new SQLException("Physical world is already bound to " + existing.definitionId());
                    }
                    return existing;
                }
            }
        }
        WorldInstance instance = new WorldInstance(UUID.randomUUID(), binding.definitionId(),
                binding.bukkitWorldId(), binding.worldName(), Instant.now());
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO world_instances VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, instance.instanceId().toString());
            insert.setString(2, instance.definitionId());
            insert.setString(3, instance.bukkitWorldId().toString());
            insert.setString(4, instance.worldName());
            insert.setString(5, instance.createdAt().toString());
            insert.executeUpdate();
        }
        return instance;
    }

    private StoreSnapshot readSnapshot() throws SQLException {
        var definitions = new ArrayList<WorldDefinition>();
        var instances = new ArrayList<WorldInstance>();
        try (Statement query = connection.createStatement();
                ResultSet result = query.executeQuery("SELECT * FROM world_definitions ORDER BY world_definition_id")) {
            while (result.next()) {
                definitions.add(new WorldDefinition(result.getString("world_definition_id"),
                        result.getString("module_id"), WorldEnvironment.valueOf(result.getString("environment"))));
            }
        }
        try (Statement query = connection.createStatement();
                ResultSet result = query.executeQuery("SELECT * FROM world_instances ORDER BY world_definition_id, created_at")) {
            while (result.next()) {
                instances.add(instanceFrom(result));
            }
        }
        return new StoreSnapshot(SCHEMA_VERSION, definitions, instances);
    }

    private static WorldInstance instanceFrom(ResultSet result) throws SQLException {
        return new WorldInstance(UUID.fromString(result.getString("world_instance_id")),
                result.getString("world_definition_id"), UUID.fromString(result.getString("bukkit_world_uuid")),
                result.getString("world_name"), Instant.parse(result.getString("created_at")));
    }

    private <T> T transaction(Callable<T> action) throws Exception {
        connection.setAutoCommit(false);
        try {
            T result = action.call();
            connection.commit();
            return result;
        } catch (Exception failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private <T> CompletableFuture<T> enqueue(Callable<T> action) {
        return enqueue(action, false);
    }

    private <T> CompletableFuture<T> enqueue(Callable<T> action, boolean shutdown) {
        if (!shutdown && !admission.tryAcquire()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("SQLite queue capacity reached"));
        }
        var result = new CompletableFuture<T>();
        try {
            worker.execute(() -> {
                try {
                    result.complete(action.call());
                } catch (Exception failure) {
                    result.completeExceptionally(failure);
                } finally {
                    if (!shutdown) {
                        admission.release();
                    }
                }
            });
        } catch (RejectedExecutionException failure) {
            if (!shutdown) {
                admission.release();
            }
            result.completeExceptionally(failure);
        }
        return result;
    }

    private void closeConnection(Exception original) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                original.addSuppressed(closeFailure);
            }
            connection = null;
        }
    }

    /** Drains pending I/O; must not be called from a store completion callback. */
    @Override
    public void close() {
        if (Thread.currentThread() == workerThread) {
            throw new IllegalStateException("Cannot wait for SQLite shutdown from its worker thread");
        }
        CompletableFuture<Void> completion;
        synchronized (this) {
            if (closing == null) {
                closing = enqueue(() -> {
                    ready = false;
                    if (connection != null) {
                        connection.close();
                        connection = null;
                    }
                    return null;
                }, true);
                worker.shutdown();
            }
            completion = closing;
        }
        try {
            completion.get(10, TimeUnit.SECONDS);
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("SQLite worker did not stop");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing SQLite", interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("Could not close SQLite cleanly", failure);
        }
    }
}
