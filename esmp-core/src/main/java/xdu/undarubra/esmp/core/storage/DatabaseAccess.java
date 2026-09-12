package xdu.undarubra.esmp.core.storage;

import static xdu.undarubra.esmp.core.generated.Tables.WORLD_INSTANCES;

import org.jooq.DSLContext;

import java.util.UUID;

/** Persistence-adapter SPI only; ArchUnit forbids use from application/API/Paper code. */
public final class DatabaseAccess {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    static boolean inTransaction() {
        return CURRENT.get() != null;
    }

    private DatabaseAccess() {}

    static final class Scope implements UnitOfWork.Transaction, AutoCloseable {
        final DSLContext context;
        final Thread owner = Thread.currentThread();
        final boolean legacy;
        boolean active = true;

        Scope(DSLContext context, boolean legacy) {
            if (CURRENT.get() != null)
                throw new IllegalStateException("Nested owning UnitOfWork is not permitted");
            this.context = context;
            this.legacy = legacy;
            CURRENT.set(this);
        }

        @Override
        public void close() {
            active = false;
            CURRENT.remove();
        }
    }

    private static Scope scope(UnitOfWork.Transaction transaction) {
        if (!(transaction instanceof Scope value)
                || !value.active
                || value.owner != Thread.currentThread()
                || CURRENT.get() != value)
            throw new IllegalStateException(
                    "Transaction scope is inactive or belongs to another thread");
        return value;
    }

    public static DSLContext context(UnitOfWork.Transaction transaction) {
        return scope(transaction).context;
    }

    public static UnitOfWork.Transaction transaction(DSLContext context) {
        Scope scope = CURRENT.get();
        if (scope == null || scope(scope).context != context)
            throw new IllegalStateException(
                    "DSL context does not belong to the current UnitOfWork");
        return scope;
    }

    public static boolean legacy(UnitOfWork.Transaction transaction) {
        return scope(transaction).legacy;
    }

    public static String worldDefinition(UnitOfWork.Transaction transaction, UUID world) {
        return context(transaction)
                .select(WORLD_INSTANCES.WORLD_DEFINITION_ID)
                .from(WORLD_INSTANCES)
                .where(WORLD_INSTANCES.WORLD_INSTANCE_ID.eq(world))
                .fetchOne(WORLD_INSTANCES.WORLD_DEFINITION_ID);
    }
}
