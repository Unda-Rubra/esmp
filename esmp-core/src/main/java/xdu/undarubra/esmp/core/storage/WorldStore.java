package xdu.undarubra.esmp.core.storage;

import static xdu.undarubra.esmp.core.generated.Tables.WORLD_DEFINITIONS;
import static xdu.undarubra.esmp.core.generated.Tables.WORLD_INSTANCES;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import xdu.undarubra.esmp.core.generated.tables.records.WorldInstancesRecord;
import xdu.undarubra.esmp.core.persistence.JooqClockRepository;
import xdu.undarubra.esmp.core.persistence.legacy.LegacyWorldMigration;
import xdu.undarubra.esmp.core.time.ClockRepository;
import xdu.undarubra.esmp.core.world.StoreSnapshot;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;
import xdu.undarubra.esmp.core.world.WorldInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Serial, asynchronous SQLite ownership. Never accepts or reads Paper objects. */
public final class WorldStore implements UnitOfWork, AutoCloseable {
    private static final int SCHEMA_VERSION = 1;
    private final Path databasePath;
    private final ExecutorService worker;
    private final Semaphore admission = new Semaphore(256);
    private final ClockRepository clocks = new JooqClockRepository();
    private volatile StoreSnapshot snapshot = new StoreSnapshot(0, List.of(), List.of());
    private volatile Thread workerThread;
    private Connection connection;
    private DSLContext baseDSL;
    private Set<String> activeDefinitionIds = Set.of();
    private boolean initializationRequested;
    private boolean ready;
    private boolean legacy;
    private CompletableFuture<Void> closing;

    public WorldStore(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
        worker =
                Executors.newSingleThreadExecutor(
                        task -> {
                            Thread thread = new Thread(task, "esmp-sqlite");
                            thread.setDaemon(true);
                            workerThread = thread;
                            return thread;
                        });
    }

    public ClockRepository clocks() {
        return clocks;
    }

    public synchronized CompletableFuture<StoreSnapshot> initialize(
            List<WorldDefinition> definitions, List<WorldBinding> bindings) {
        if (initializationRequested || closing != null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Store already initialized or closing"));
        }
        List<WorldDefinition> capturedDefinitions = List.copyOf(definitions);
        List<WorldBinding> capturedBindings = List.copyOf(bindings);
        initializationRequested = true;
        return enqueue(
                () -> {
                    try {
                        validateInput(capturedDefinitions, capturedBindings);
                        activeDefinitionIds =
                                capturedDefinitions.stream()
                                        .map(WorldDefinition::id)
                                        .collect(Collectors.toUnmodifiableSet());
                        Files.createDirectories(databasePath.getParent());
                        SQLiteConfig config = new SQLiteConfig();
                        config.enforceForeignKeys(true);
                        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
                        config.setBusyTimeout(5000);
                        SQLiteDataSource dataSource = new SQLiteDataSource(config);
                        dataSource.setUrl("jdbc:sqlite:" + databasePath);
                        connection = dataSource.getConnection();
                        baseDSL =
                                DSL.using(
                                        connection,
                                        SQLDialect.SQLITE,
                                        new Settings()
                                                .withExecuteLogging(false)
                                                .withRenderSchema(false));

                        // Liquibase owns its transactions; do not invoke it inside
                        // transactionResult.
                        legacy = DatabaseSchema.open(connection);
                        transaction(
                                tx -> {
                                    if (legacy) {
                                        LegacyWorldMigration.migrate(tx);
                                    }
                                    DSLContext context = DatabaseAccess.context(tx);
                                    for (WorldDefinition definition : capturedDefinitions) {
                                        persistDefinition(context, definition);
                                    }
                                    for (WorldBinding binding : capturedBindings) {
                                        persistBinding(context, binding);
                                    }
                                    return null;
                                });
                        snapshot = transaction(tx -> readSnapshot(DatabaseAccess.context(tx)));
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
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Store not initialized or closing"));
        }
        return enqueue(
                () -> {
                    requireReady();
                    WorldInstance instance =
                            transaction(tx -> persistBinding(DatabaseAccess.context(tx), binding));
                    snapshot = transaction(tx -> readSnapshot(DatabaseAccess.context(tx)));
                    return instance;
                });
    }

    public StoreSnapshot snapshot() {
        return snapshot;
    }

    /** Executes an owning-domain transaction on the same bounded SQLite worker. */
    @Override
    public synchronized <T> CompletableFuture<T> transact(Work<T> work) {
        if (!initializationRequested || closing != null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Store not initialized or closing"));
        }
        return enqueue(
                () -> {
                    requireReady();
                    return transaction(work);
                });
    }

    /**
     * Called after all domain initialization and before Paper is marked ready. The jOOQ transaction
     * provider has restored autocommit before this task runs.
     */
    public synchronized CompletableFuture<Void> finishMigration() {
        if (!initializationRequested || closing != null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Store not initialized or closing"));
        }
        return enqueue(
                () -> {
                    requireReady();
                    // In particular, never wrap Liquibase's finish operation in a jOOQ transaction.
                    DatabaseSchema.finish(connection, legacy);
                    legacy = false;
                    return null;
                });
    }

    private void requireReady() {
        if (!ready) {
            throw new IllegalStateException("Store initialization did not succeed");
        }
    }

    private static void validateInput(
            List<WorldDefinition> definitions, List<WorldBinding> bindings) {
        var ids = new HashSet<String>();
        for (WorldDefinition definition : definitions) {
            if (!ids.add(definition.id())) {
                throw new IllegalArgumentException(
                        "Duplicate world definition: " + definition.id());
            }
        }
        var boundDefinitions = new HashSet<String>();
        var worldIds = new HashSet<UUID>();
        for (WorldBinding binding : bindings) {
            if (!ids.contains(binding.definitionId())
                    || !boundDefinitions.add(binding.definitionId())
                    || !worldIds.add(binding.bukkitWorldId())) {
                throw new IllegalArgumentException(
                        "Unknown or duplicate world binding: " + binding.definitionId());
            }
        }
    }

    private void persistDefinition(DSLContext context, WorldDefinition definition)
            throws SQLException {
        var existing =
                context.selectFrom(WORLD_DEFINITIONS)
                        .where(WORLD_DEFINITIONS.WORLD_DEFINITION_ID.eq(definition.id()))
                        .fetchOne();
        if (existing != null) {
            if (!definition.moduleId().equals(existing.getModuleId())
                    || !definition.environment().name().equals(existing.getEnvironment())) {
                throw new SQLException("World definition changed identity: " + definition.id());
            }
            return;
        }
        context.insertInto(WORLD_DEFINITIONS)
                .set(WORLD_DEFINITIONS.WORLD_DEFINITION_ID, definition.id())
                .set(WORLD_DEFINITIONS.MODULE_ID, definition.moduleId())
                .set(WORLD_DEFINITIONS.ENVIRONMENT, definition.environment().name())
                .execute();
    }

    private WorldInstance persistBinding(DSLContext context, WorldBinding binding)
            throws SQLException {
        if (!activeDefinitionIds.contains(binding.definitionId())) {
            throw new SQLException("World definition is not active: " + binding.definitionId());
        }
        var record =
                context.selectFrom(WORLD_INSTANCES)
                        .where(WORLD_INSTANCES.BUKKIT_WORLD_UUID.eq(binding.bukkitWorldId()))
                        .fetchOne();
        if (record != null) {
            var existing = instanceFrom(record);
            if (!existing.definitionId().equals(binding.definitionId())) {
                throw new SQLException(
                        "Physical world is already bound to " + existing.definitionId());
            }
            return existing;
        }
        WorldInstance instance =
                new WorldInstance(
                        UUID.randomUUID(),
                        binding.definitionId(),
                        binding.bukkitWorldId(),
                        binding.worldName(),
                        Instant.now());
        context.insertInto(WORLD_INSTANCES)
                .set(WORLD_INSTANCES.WORLD_INSTANCE_ID, instance.instanceId())
                .set(WORLD_INSTANCES.WORLD_DEFINITION_ID, instance.definitionId())
                .set(WORLD_INSTANCES.BUKKIT_WORLD_UUID, instance.bukkitWorldId())
                .set(WORLD_INSTANCES.WORLD_NAME, instance.worldName())
                .set(WORLD_INSTANCES.CREATED_AT, instance.createdAt().toString())
                .execute();
        return instance;
    }

    private StoreSnapshot readSnapshot(DSLContext context) {
        var definitions =
                context.selectFrom(WORLD_DEFINITIONS)
                        .where(WORLD_DEFINITIONS.WORLD_DEFINITION_ID.in(activeDefinitionIds))
                        .orderBy(WORLD_DEFINITIONS.WORLD_DEFINITION_ID)
                        .fetch(
                                r ->
                                        new WorldDefinition(
                                                r.getWorldDefinitionId(),
                                                r.getModuleId(),
                                                WorldEnvironment.valueOf(r.getEnvironment())));
        var instances =
                context.selectFrom(WORLD_INSTANCES)
                        .where(WORLD_INSTANCES.WORLD_DEFINITION_ID.in(activeDefinitionIds))
                        .orderBy(WORLD_INSTANCES.WORLD_DEFINITION_ID, WORLD_INSTANCES.CREATED_AT)
                        .fetch(WorldStore::instanceFrom);
        return new StoreSnapshot(SCHEMA_VERSION, definitions, instances);
    }

    private static WorldInstance instanceFrom(WorldInstancesRecord record) {
        return new WorldInstance(
                record.getWorldInstanceId(),
                record.getWorldDefinitionId(),
                record.getBukkitWorldUuid(),
                record.getWorldName(),
                Instant.parse(record.getCreatedAt()));
    }

    private <T> T transaction(Work<T> work) throws Exception {
        Exception[] callbackFailure = new Exception[1];
        try {
            return baseDSL.transactionResult(
                    config -> {
                        try (DatabaseAccess.Scope scope =
                                new DatabaseAccess.Scope(config.dsl(), legacy)) {
                            try {
                                return work.run(scope);
                            } catch (Exception failure) {
                                callbackFailure[0] = failure;
                                throw failure;
                            }
                        }
                    });
        } catch (DataAccessException failure) {
            // jOOQ wraps checked callback failures. Complete the future with the original
            // exception, not the wrapper, so domain rejections retain their identity.
            Exception original = callbackFailure[0];
            if (original != null && original != failure) {
                for (Throwable cause = failure.getCause();
                        cause != null;
                        cause = cause.getCause()) {
                    if (cause == original) {
                        for (Throwable suppressed : failure.getSuppressed()) {
                            if (suppressed != original) {
                                original.addSuppressed(suppressed);
                            }
                        }
                        throw original;
                    }
                }
            }
            throw failure;
        }
    }

    private <T> CompletableFuture<T> enqueue(Callable<T> action) {
        return enqueue(action, false);
    }

    private <T> CompletableFuture<T> enqueue(Callable<T> action, boolean shutdown) {
        if (Thread.currentThread() == workerThread && DatabaseAccess.inTransaction()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Cannot enqueue SQLite work from its worker thread; use the current"
                                + " transaction"));
        }
        if (!shutdown && !admission.tryAcquire()) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("SQLite queue capacity reached"));
        }
        var result = new CompletableFuture<T>();
        try {
            worker.execute(
                    () -> {
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
        baseDSL = null;
    }

    /** Drains pending I/O; must not be called from a store completion callback. */
    @Override
    public void close() {
        if (Thread.currentThread() == workerThread) {
            throw new IllegalStateException(
                    "Cannot wait for SQLite shutdown from its worker thread");
        }
        CompletableFuture<Void> completion;
        synchronized (this) {
            if (closing == null) {
                closing =
                        enqueue(
                                () -> {
                                    ready = false;
                                    if (connection != null) {
                                        connection.close();
                                        connection = null;
                                    }
                                    baseDSL = null;
                                    return null;
                                },
                                true);
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
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("Could not close SQLite cleanly", failure);
        }
    }
}
