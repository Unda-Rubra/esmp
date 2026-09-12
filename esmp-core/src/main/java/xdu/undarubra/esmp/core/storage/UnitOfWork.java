package xdu.undarubra.esmp.core.storage;

import java.util.concurrent.CompletableFuture;

/** Application-visible transaction scope; persistence handles never escape through this API. */
public interface UnitOfWork {
    interface Transaction {}

    @FunctionalInterface
    interface Work<T> {
        T run(Transaction transaction) throws Exception;
    }

    <T> CompletableFuture<T> transact(Work<T> work);
}
