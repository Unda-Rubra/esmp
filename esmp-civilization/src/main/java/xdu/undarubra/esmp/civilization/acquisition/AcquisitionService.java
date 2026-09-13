package xdu.undarubra.esmp.civilization.acquisition;

import xdu.undarubra.esmp.civilization.settlement.SettlementChange;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AcquisitionService {
    /** Called only after fee-bound recipient consent and administrator approval revalidation. */
    @FunctionalInterface
    public interface Payment {
        void debit(Transaction transaction, Acquisition accepted) throws SQLException;
    }

    public interface Repository {
        CompletableFuture<Acquisition> view(UUID id);

        CompletableFuture<List<Acquisition>> history(UUID settlement);

        CompletableFuture<SettlementChange> propose(UUID actor, UUID settlement, UUID recipient);

        CompletableFuture<SettlementChange> accept(UUID actor, UUID id, long expectedFee);

        CompletableFuture<SettlementChange> review(
                UUID actor, UUID id, boolean approve, String note);

        CompletableFuture<SettlementChange> cancel(UUID actor, UUID id);
    }

    private final Repository repository;

    public AcquisitionService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<Acquisition> view(UUID id) {
        return repository.view(id);
    }

    public CompletableFuture<List<Acquisition>> history(UUID settlement) {
        return repository.history(settlement);
    }

    public CompletableFuture<SettlementChange> propose(
            UUID actor, UUID settlement, UUID recipient) {
        return repository.propose(actor, settlement, recipient);
    }

    public CompletableFuture<SettlementChange> accept(UUID actor, UUID id, long expectedFee) {
        return repository.accept(actor, id, expectedFee);
    }

    public CompletableFuture<SettlementChange> review(
            UUID actor, UUID id, boolean approve, String note) {
        return repository.review(actor, id, approve, note);
    }

    public CompletableFuture<SettlementChange> cancel(UUID actor, UUID id) {
        return repository.cancel(actor, id);
    }
}
