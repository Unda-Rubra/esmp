package xdu.undarubra.esmp.economy;

import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.economy.LedgerService.Actor;
import xdu.undarubra.esmp.economy.LedgerService.AllowanceResult;
import xdu.undarubra.esmp.economy.LedgerService.Budget;
import xdu.undarubra.esmp.economy.LedgerService.Receipt;
import xdu.undarubra.esmp.economy.LedgerService.Request;
import xdu.undarubra.esmp.economy.LedgerService.StartingGrant;
import xdu.undarubra.esmp.economy.LedgerService.StartingGrantBatch;
import xdu.undarubra.esmp.economy.LedgerService.Supply;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Monetary persistence port. Asynchronous operations own a unit of work; transaction-taking
 * operations participate in the caller's unit of work without starting or completing another one.
 */
public interface LedgerRepository {
    CurrencyDefinition currency();

    CompletableFuture<Void> initialize();

    CompletableFuture<Long> balance(Actor actor, LedgerAccount account);

    CompletableFuture<Budget> budget(Actor actor);

    CompletableFuture<Supply> supply(Actor actor);

    CompletableFuture<List<Receipt>> history(Actor actor, LedgerAccount account);

    CompletableFuture<Receipt> post(Request request);

    Receipt post(Transaction transaction, Request request) throws SQLException;

    CompletableFuture<Receipt> startingFunds(Actor actor, UUID player, long amount);

    long monetaryPeriod();

    CompletableFuture<StartingGrant> startingGrant(UUID player, boolean firstJoin, long amount);

    CompletableFuture<StartingGrantBatch> retryStartingGrants();

    CompletableFuture<AllowanceResult> setAllowance(
            Actor actor, UUID settlement, UUID governor, long amount, UUID id);

    CompletableFuture<Long> allowance(Actor actor, UUID settlement, UUID governor);

    void requireEmptyTreasury(Transaction transaction, UUID settlement) throws SQLException;
}
