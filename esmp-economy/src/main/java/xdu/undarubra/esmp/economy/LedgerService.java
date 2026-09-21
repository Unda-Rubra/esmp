package xdu.undarubra.esmp.economy;

import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public monetary service. Persistence and transaction ownership belong to its repository. */
public final class LedgerService {
    public enum Movement {
        TRANSFER,
        ISSUANCE,
        SINK
    }

    public record Actor(UUID player, boolean administrator) {
        public Actor {
            if (player == null && !administrator)
                throw new IllegalArgumentException("Console requires administrator authority");
        }

        public String key() {
            return player == null ? "CONSOLE" : player.toString();
        }
    }

    public record TreasuryAuthority(
            boolean owner,
            boolean governor,
            long ownershipRevision,
            UUID promotionId,
            boolean historical) {}

    @FunctionalInterface
    public interface TreasuryAuthorizer {
        TreasuryAuthority inspect(Transaction transaction, UUID actor, UUID settlement)
                throws SQLException;
    }

    public record Request(
            UUID id,
            Actor actor,
            Movement movement,
            LedgerAccount source,
            LedgerAccount target,
            long amount,
            String reason,
            String note) {
        public Request {
            Objects.requireNonNull(id);
            Objects.requireNonNull(actor);
            Objects.requireNonNull(movement);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(note);
            if (amount <= 0) throw new LedgerException("Enter a positive whole amount.");
            if (!reason.matches("[A-Z][A-Z0-9_]{0,63}") || note.length() > 256)
                throw new LedgerException("Invalid transaction description.");
            boolean valid =
                    switch (movement) {
                        case TRANSFER -> source != null && target != null && !source.equals(target);
                        case ISSUANCE -> source == null && target != null;
                        case SINK -> source != null && target == null;
                    };
            if (!valid) throw new LedgerException("Invalid account movement.");
        }
    }

    public record Receipt(
            UUID id,
            Movement movement,
            String actor,
            LedgerAccount source,
            LedgerAccount target,
            long amount,
            String reason,
            String note,
            long gameTick,
            Long budgetPeriod,
            Long sourceAfter,
            Long targetAfter,
            boolean replayed) {}

    public record Budget(long period, long limit, long issued, long gameTick, long periodTicks) {
        public long remaining() {
            return limit - issued;
        }

        public long ticksRemaining() {
            return periodTicks - gameTick % periodTicks;
        }
    }

    public record Supply(BigInteger wallets, BigInteger treasuries) {
        public BigInteger total() {
            return wallets.add(treasuries);
        }
    }

    public record AllowanceResult(UUID id, long amount, boolean replayed) {}

    public record StartingGrant(
            UUID player, long amount, boolean paid, boolean issuedNow, Long balanceAfter) {}

    public record StartingGrantBatch(long period, int processed, List<StartingGrant> paid) {
        public StartingGrantBatch {
            paid = List.copyOf(paid);
        }
    }

    public static final int STARTING_GRANT_BATCH_SIZE = 32;

    private final LedgerRepository repository;

    public LedgerService(LedgerRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CurrencyDefinition currency() {
        return repository.currency();
    }

    public CompletableFuture<Void> initialize() {
        return repository.initialize();
    }

    public CompletableFuture<Long> balance(Actor actor, LedgerAccount account) {
        return repository.balance(actor, account);
    }

    public CompletableFuture<Budget> budget(Actor actor) {
        return repository.budget(actor);
    }

    public CompletableFuture<Supply> supply(Actor actor) {
        return repository.supply(actor);
    }

    public CompletableFuture<List<Receipt>> history(Actor actor, LedgerAccount account) {
        return repository.history(actor, account);
    }

    public CompletableFuture<Receipt> post(Request request) {
        return repository.post(request);
    }

    /** Apply in the caller's transaction so related state commits with its payment. */
    public Receipt post(Transaction transaction, Request request) throws SQLException {
        return repository.post(transaction, request);
    }

    /** Eligibility belongs to onboarding; this primitive enforces one grant per currency/player. */
    public CompletableFuture<Receipt> startingFunds(Actor actor, UUID player, long amount) {
        return repository.startingFunds(actor, player, amount);
    }

    public long monetaryPeriod() {
        return repository.monetaryPeriod();
    }

    /**
     * Only the Paper first-join observation may enroll a player. Returning players can resume an
     * existing entitlement, never create one. Its amount is fixed at enrollment. Returns null when
     * this player has no entitlement.
     */
    public CompletableFuture<StartingGrant> startingGrant(
            UUID player, boolean firstJoin, long amount) {
        return repository.startingGrant(player, firstJoin, amount);
    }

    /** Bounded due work, including offline players; no scans of player files or settlements. */
    public CompletableFuture<StartingGrantBatch> retryStartingGrants() {
        return repository.retryStartingGrants();
    }

    public CompletableFuture<AllowanceResult> setAllowance(
            Actor actor, UUID settlement, UUID governor, long amount, UUID id) {
        return repository.setAllowance(actor, settlement, governor, amount, id);
    }

    public CompletableFuture<Long> allowance(Actor actor, UUID settlement, UUID governor) {
        return repository.allowance(actor, settlement, governor);
    }

    public void requireEmptyTreasury(Transaction transaction, UUID settlement) throws SQLException {
        repository.requireEmptyTreasury(transaction, settlement);
    }
}
