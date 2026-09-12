package xdu.undarubra.esmp.civilization.settlement;

import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public Settlement operations; persistence and transaction ownership belong to the repository. */
public final class SettlementService {
    @FunctionalInterface
    public interface ReleaseGuard {
        void check(Transaction transaction, UUID settlement) throws SQLException;
    }

    @FunctionalInterface
    public interface ClaimPayment {
        void debit(Transaction transaction, ClaimQuote quote) throws SQLException;
    }

    @FunctionalInterface
    public interface ClaimActivity {
        boolean active(Transaction transaction, UUID settlement) throws SQLException;
    }

    public record TreasuryPermission(Role role, long ownershipRevision, UUID promotionId) {}

    /** Domain port. Transaction-taking operations participate in the caller's transaction. */
    public interface Repository {
        CompletableFuture<List<SettlementView>> initialize();

        TreasuryPermission treasuryPermission(Transaction transaction, UUID actor, UUID id)
                throws SQLException;

        CompletableFuture<ClaimQuote> quoteCreate(UUID actor, UUID world, ChunkPosition chunk);

        CompletableFuture<ClaimQuote> quoteClaim(
                UUID actor, UUID id, UUID world, ChunkPosition chunk);

        CompletableFuture<ClaimQuote> quote(UUID actor, UUID quoteId);

        CompletableFuture<SettlementChange> purchase(UUID actor, UUID quoteId);

        CompletableFuture<SettlementChange> unclaim(UUID actor, UUID id, ChunkPosition chunk);

        CompletableFuture<SettlementChange> rename(UUID actor, UUID id, String rawName);

        CompletableFuture<SettlementChange> ban(UUID actor, UUID id, UUID target, boolean banned);

        CompletableFuture<SettlementChange> offer(
                UUID actor, UUID id, UUID recipient, SettlementOffer.Type type);

        CompletableFuture<SettlementChange> accept(
                UUID actor, UUID offerId, boolean abandonmentConfirmation);

        CompletableFuture<SettlementChange> resolveOffer(UUID actor, UUID offerId, boolean decline);

        CompletableFuture<SettlementChange> remove(
                UUID actor, UUID id, UUID target, boolean voluntary);

        CompletableFuture<SettlementChange> demote(UUID actor, UUID id, UUID target);

        CompletableFuture<List<SettlementOffer>> offers(UUID actor);

        CompletableFuture<SettlementOffer> inspectOffer(UUID id);

        CompletableFuture<List<SettlementEvent>> history(UUID settlementId);

        CompletableFuture<List<SettlementEvent>> pendingEvents();

        CompletableFuture<Void> acknowledgeEvents(List<UUID> ids);
    }

    private final Repository repository;

    public SettlementService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<List<SettlementView>> initialize() {
        return repository.initialize();
    }

    public TreasuryPermission treasuryPermission(Transaction transaction, UUID actor, UUID id)
            throws SQLException {
        return repository.treasuryPermission(transaction, actor, id);
    }

    public CompletableFuture<ClaimQuote> quoteCreate(UUID actor, UUID world, ChunkPosition chunk) {
        return repository.quoteCreate(actor, world, chunk);
    }

    public CompletableFuture<ClaimQuote> quoteClaim(
            UUID actor, UUID id, UUID world, ChunkPosition chunk) {
        return repository.quoteClaim(actor, id, world, chunk);
    }

    public CompletableFuture<ClaimQuote> quote(UUID actor, UUID quoteId) {
        return repository.quote(actor, quoteId);
    }

    public CompletableFuture<SettlementChange> purchase(UUID actor, UUID quoteId) {
        return repository.purchase(actor, quoteId);
    }

    public CompletableFuture<SettlementChange> unclaim(UUID actor, UUID id, ChunkPosition chunk) {
        return repository.unclaim(actor, id, chunk);
    }

    public CompletableFuture<SettlementChange> rename(UUID actor, UUID id, String rawName) {
        return repository.rename(actor, id, rawName);
    }

    public CompletableFuture<SettlementChange> ban(
            UUID actor, UUID id, UUID target, boolean banned) {
        return repository.ban(actor, id, target, banned);
    }

    public CompletableFuture<SettlementChange> offer(
            UUID actor, UUID id, UUID recipient, SettlementOffer.Type type) {
        return repository.offer(actor, id, recipient, type);
    }

    public CompletableFuture<SettlementChange> accept(
            UUID actor, UUID offerId, boolean abandonmentConfirmation) {
        return repository.accept(actor, offerId, abandonmentConfirmation);
    }

    public CompletableFuture<SettlementChange> resolveOffer(
            UUID actor, UUID offerId, boolean decline) {
        return repository.resolveOffer(actor, offerId, decline);
    }

    public CompletableFuture<SettlementChange> remove(
            UUID actor, UUID id, UUID target, boolean voluntary) {
        return repository.remove(actor, id, target, voluntary);
    }

    public CompletableFuture<SettlementChange> demote(UUID actor, UUID id, UUID target) {
        return repository.demote(actor, id, target);
    }

    public CompletableFuture<List<SettlementOffer>> offers(UUID actor) {
        return repository.offers(actor);
    }

    public CompletableFuture<SettlementOffer> inspectOffer(UUID id) {
        return repository.inspectOffer(id);
    }

    public CompletableFuture<List<SettlementEvent>> history(UUID settlementId) {
        return repository.history(settlementId);
    }

    public CompletableFuture<List<SettlementEvent>> pendingEvents() {
        return repository.pendingEvents();
    }

    public CompletableFuture<Void> acknowledgeEvents(List<UUID> ids) {
        return repository.acknowledgeEvents(ids);
    }
}
