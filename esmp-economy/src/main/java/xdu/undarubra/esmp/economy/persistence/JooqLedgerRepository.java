package xdu.undarubra.esmp.economy.persistence;

import static xdu.undarubra.esmp.economy.LedgerService.STARTING_GRANT_BATCH_SIZE;
import static xdu.undarubra.esmp.economy.generated.Tables.*;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.economy.CurrencyDefinition;
import xdu.undarubra.esmp.economy.LedgerAccount;
import xdu.undarubra.esmp.economy.LedgerException;
import xdu.undarubra.esmp.economy.LedgerPolicy;
import xdu.undarubra.esmp.economy.LedgerRepository;
import xdu.undarubra.esmp.economy.LedgerService.*;
import xdu.undarubra.esmp.economy.persistence.legacy.LegacyLedgerMigrations;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Economy-owned persistence on the shared serial SQL worker. No Paper or foreign-domain tables. */
public final class JooqLedgerRepository implements LedgerRepository {
    private static final Actor STARTING_GRANT_ACTOR = new Actor(null, true);

    private final UnitOfWork database;
    private final GameClock clock;
    private final CurrencyDefinition currency;
    private final LedgerPolicy policy;
    private final TreasuryAuthorizer treasury;
    private volatile boolean initialized;

    public JooqLedgerRepository(
            UnitOfWork database,
            GameClock clock,
            CurrencyDefinition currency,
            LedgerPolicy policy,
            TreasuryAuthorizer treasury) {
        this.database = Objects.requireNonNull(database);
        this.clock = Objects.requireNonNull(clock);
        this.currency = Objects.requireNonNull(currency);
        this.policy = Objects.requireNonNull(policy);
        this.treasury = Objects.requireNonNull(treasury);
    }

    @Override
    public CurrencyDefinition currency() {
        return currency;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return database.transact(
                        transaction -> {
                            DSLContext c = DatabaseAccess.context(transaction);
                            if (DatabaseAccess.legacy(transaction)) {
                                LegacyLedgerMigrations.initialize(
                                        transaction, clock, currency, policy);
                            } else {
                                // Bootstrap owns schema creation. A fresh prepared schema still
                                // needs its configured monetary identity, but never service DDL.
                                if (c.selectFrom(ECONOMY_METADATA)
                                                .where(ECONOMY_METADATA.ID.eq(1L))
                                                .fetchOne()
                                        == null) {
                                    c.insertInto(ECONOMY_METADATA)
                                            .set(ECONOMY_METADATA.ID, 1L)
                                            .set(ECONOMY_METADATA.SCHEMA_VERSION, 2L)
                                            .set(ECONOMY_METADATA.CURRENCY_ID, currency.id())
                                            .set(
                                                    ECONOMY_METADATA.PERIOD_TICKS,
                                                    policy.periodTicks())
                                            .execute();
                                }
                            }
                            validateMetadata(c);
                            return null;
                        })
                .thenRun(() -> initialized = true);
    }

    private void validateMetadata(DSLContext c) throws SQLException {
        var metadata = c.selectFrom(ECONOMY_METADATA).where(ECONOMY_METADATA.ID.eq(1L)).fetchOne();
        if (metadata == null) throw new SQLException("Missing economy metadata");
        long storedVersion = metadata.get(ECONOMY_METADATA.SCHEMA_VERSION);
        if (storedVersion < 1L || storedVersion > 2L)
            throw new SQLException("Unsupported economy schema");
        if (!currency.id().equals(metadata.get(ECONOMY_METADATA.CURRENCY_ID))
                || policy.periodTicks() != metadata.get(ECONOMY_METADATA.PERIOD_TICKS))
            throw new SQLException(
                    "Currency identity or monetary period changed; explicit migration required");
    }

    @Override
    public CompletableFuture<Long> balance(Actor actor, LedgerAccount account) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    authorizeRead(transaction, actor, account);
                    return balanceIn(c, account);
                });
    }

    @Override
    public CompletableFuture<Budget> budget(Actor actor) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    require(actor.administrator(), "Administrator required.");
                    return budgetIn(c, transaction, clock.ticks());
                });
    }

    @Override
    public CompletableFuture<Supply> supply(Actor actor) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    requireReady();
                    require(actor.administrator(), "Administrator required.");
                    BigInteger wallets = BigInteger.ZERO, treasuries = BigInteger.ZERO;
                    // Sum in Java: SQLite's signed INTEGER aggregate cannot represent total supply.
                    try (var rows =
                            c.select(ECONOMY_ACCOUNTS.KIND, ECONOMY_ACCOUNTS.BALANCE)
                                    .from(ECONOMY_ACCOUNTS)
                                    .fetchLazy()) {
                        for (var r : rows) {
                            var amount = BigInteger.valueOf(r.get(ECONOMY_ACCOUNTS.BALANCE));
                            if (LedgerAccount.Kind.valueOf(r.get(ECONOMY_ACCOUNTS.KIND))
                                    == LedgerAccount.Kind.WALLET) wallets = wallets.add(amount);
                            else treasuries = treasuries.add(amount);
                        }
                    }
                    return new Supply(wallets, treasuries);
                });
    }

    @Override
    public CompletableFuture<List<Receipt>> history(Actor actor, LedgerAccount account) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    authorizeRead(transaction, actor, account);
                    var result = new ArrayList<Receipt>();
                    // rowid preserves insertion order even for equal ticks.
                    for (var r :
                            c.selectFrom(ECONOMY_TRANSACTIONS)
                                    .where(
                                            ECONOMY_TRANSACTIONS
                                                    .SOURCE_ACCOUNT
                                                    .eq(account.key())
                                                    .or(
                                                            ECONOMY_TRANSACTIONS.TARGET_ACCOUNT.eq(
                                                                    account.key())))
                                    .orderBy(DSL.field(DSL.name("rowid"), Long.class).desc())
                                    .limit(20)
                                    .fetch()) {
                        result.add(receipt(r, false));
                    }
                    return List.copyOf(result);
                });
    }

    @Override
    public CompletableFuture<Receipt> post(Request request) {
        return database.transact(transaction -> post(transaction, request));
    }

    @Override
    public CompletableFuture<Receipt> startingFunds(Actor actor, UUID player, long amount) {
        return post(
                new Request(
                        startingFundsId(player),
                        actor,
                        Movement.ISSUANCE,
                        null,
                        LedgerAccount.wallet(player),
                        amount,
                        "STARTING_FUNDS",
                        ""));
    }

    private UUID startingFundsId(UUID player) {
        return UUID.nameUUIDFromBytes(
                ("STARTING_FUNDS:" + currency.id() + ":" + player)
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public long monetaryPeriod() {
        return clock.ticks() / policy.periodTicks();
    }

    @Override
    public CompletableFuture<StartingGrant> startingGrant(
            UUID player, boolean firstJoin, long amount) {
        Objects.requireNonNull(player);
        require(amount > 0, "Starting funds must be positive.");
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    requireReady();
                    if (firstJoin) {
                        c.insertInto(ECONOMY_STARTING_GRANTS)
                                .set(ECONOMY_STARTING_GRANTS.PLAYER_UUID, player)
                                .set(ECONOMY_STARTING_GRANTS.AMOUNT, amount)
                                .set(ECONOMY_STARTING_GRANTS.ELIGIBLE_TICK, clock.ticks())
                                .set(ECONOMY_STARTING_GRANTS.NEXT_PERIOD, monetaryPeriod())
                                .onDuplicateKeyIgnore()
                                .execute();
                        clock.checkpoint(transaction);
                    }
                    return settleStartingGrant(c, transaction, player, monetaryPeriod());
                });
    }

    @Override
    public CompletableFuture<StartingGrantBatch> retryStartingGrants() {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    requireReady();
                    long period = monetaryPeriod();
                    // Keep the original queue's rowid tie-breaker and bounded batch.
                    var players =
                            c.select(ECONOMY_STARTING_GRANTS.PLAYER_UUID)
                                    .from(ECONOMY_STARTING_GRANTS)
                                    .where(
                                            ECONOMY_STARTING_GRANTS
                                                    .TRANSACTION_ID
                                                    .isNull()
                                                    .and(
                                                            ECONOMY_STARTING_GRANTS.NEXT_PERIOD.le(
                                                                    period)))
                                    .orderBy(
                                            ECONOMY_STARTING_GRANTS.NEXT_PERIOD.asc(),
                                            ECONOMY_STARTING_GRANTS.ELIGIBLE_TICK.asc(),
                                            DSL.field(DSL.name("rowid"), Long.class).asc())
                                    .limit(STARTING_GRANT_BATCH_SIZE)
                                    .fetch(ECONOMY_STARTING_GRANTS.PLAYER_UUID);
                    var paid = new ArrayList<StartingGrant>();
                    for (UUID player : players) {
                        var grant = settleStartingGrant(c, transaction, player, period);
                        if (grant.issuedNow()) paid.add(grant);
                    }
                    return new StartingGrantBatch(period, players.size(), paid);
                });
    }

    private StartingGrant settleStartingGrant(
            DSLContext c, Transaction transaction, UUID player, long period) throws SQLException {
        var entitlement =
                c.select(
                                ECONOMY_STARTING_GRANTS.AMOUNT,
                                ECONOMY_STARTING_GRANTS.NEXT_PERIOD,
                                ECONOMY_STARTING_GRANTS.TRANSACTION_ID)
                        .from(ECONOMY_STARTING_GRANTS)
                        .where(ECONOMY_STARTING_GRANTS.PLAYER_UUID.eq(player))
                        .fetchOne();
        if (entitlement == null) return null;
        long amount = entitlement.get(ECONOMY_STARTING_GRANTS.AMOUNT);
        long nextPeriod = entitlement.get(ECONOMY_STARTING_GRANTS.NEXT_PERIOD);
        if (entitlement.get(ECONOMY_STARTING_GRANTS.TRANSACTION_ID) != null)
            return new StartingGrant(player, amount, true, false, null);
        UUID id = startingFundsId(player);
        // Reconcile the one-time primitive without minting again.
        Receipt previous = find(c, id);
        if (previous != null) {
            require(
                    previous.movement() == Movement.ISSUANCE
                            && previous.reason().equals("STARTING_FUNDS")
                            && LedgerAccount.wallet(player).equals(previous.target()),
                    "Starting grant identity conflicts with another transaction.");
            c.update(ECONOMY_STARTING_GRANTS)
                    .set(ECONOMY_STARTING_GRANTS.AMOUNT, previous.amount())
                    .set(ECONOMY_STARTING_GRANTS.TRANSACTION_ID, id)
                    .where(ECONOMY_STARTING_GRANTS.PLAYER_UUID.eq(player))
                    .execute();
            return new StartingGrant(player, previous.amount(), true, false, null);
        }
        if (nextPeriod > period) return new StartingGrant(player, amount, false, false, null);
        var budget = budgetIn(c, transaction, clock.ticks());
        if (amount > budget.remaining()
                || amount > Long.MAX_VALUE - balanceIn(c, LedgerAccount.wallet(player))) {
            c.update(ECONOMY_STARTING_GRANTS)
                    .set(ECONOMY_STARTING_GRANTS.NEXT_PERIOD, Math.addExact(budget.period(), 1L))
                    .where(ECONOMY_STARTING_GRANTS.PLAYER_UUID.eq(player))
                    .execute();
            return new StartingGrant(player, amount, false, false, null);
        }
        Receipt receipt =
                postAt(
                        c,
                        transaction,
                        new Request(
                                id,
                                STARTING_GRANT_ACTOR,
                                Movement.ISSUANCE,
                                null,
                                LedgerAccount.wallet(player),
                                amount,
                                "STARTING_FUNDS",
                                ""),
                        budget.gameTick());
        c.update(ECONOMY_STARTING_GRANTS)
                .set(ECONOMY_STARTING_GRANTS.TRANSACTION_ID, id)
                .where(ECONOMY_STARTING_GRANTS.PLAYER_UUID.eq(player))
                .execute();
        return new StartingGrant(player, amount, true, !receipt.replayed(), receipt.targetAfter());
    }

    @Override
    public Receipt post(Transaction transaction, Request request) throws SQLException {
        DSLContext c = DatabaseAccess.context(transaction);
        return postAt(c, transaction, request, clock.ticks());
    }

    private Receipt postAt(DSLContext c, Transaction transaction, Request request, long now)
            throws SQLException {
        requireReady();
        requireTransaction(c);
        if (request.movement() == Movement.ISSUANCE)
            require(request.actor().administrator(), "Administrator required to issue currency.");
        if (request.reason().equals("STARTING_FUNDS")) {
            require(
                    request.movement() == Movement.ISSUANCE
                            && request.target() != null
                            && request.target().kind() == LedgerAccount.Kind.WALLET
                            && request.id().equals(startingFundsId(request.target().owner())),
                    "Starting funds require the player's one-time grant ID.");
        }
        var previous = find(c, request.id());
        if (previous != null) {
            require(
                    previous.actor().equals(request.actor().key())
                            && previous.movement() == request.movement()
                            && Objects.equals(previous.source(), request.source())
                            && Objects.equals(previous.target(), request.target())
                            && previous.amount() == request.amount()
                            && previous.reason().equals(request.reason())
                            && previous.note().equals(request.note()),
                    "That transaction ID belongs to a different request.");
            return new Receipt(
                    previous.id(),
                    previous.movement(),
                    previous.actor(),
                    previous.source(),
                    previous.target(),
                    previous.amount(),
                    previous.reason(),
                    previous.note(),
                    previous.gameTick(),
                    previous.budgetPeriod(),
                    previous.sourceAfter(),
                    previous.targetAfter(),
                    true);
        }
        require(
                !c.fetchExists(
                        c.selectOne()
                                .from(ECONOMY_ALLOWANCE_EVENTS)
                                .where(ECONOMY_ALLOWANCE_EVENTS.OPERATION_ID.eq(request.id()))),
                "That ID belongs to a budget change.");
        if (request.target() != null && request.target().kind() == LedgerAccount.Kind.TREASURY)
            require(
                    !treasury.inspect(
                                    transaction, request.actor().player(), request.target().owner())
                            .historical(),
                    "Historical Treasury accepts collection only, not deposits or grants");
        TreasuryAuthority authority = null;
        if (request.source() != null)
            authority =
                    authorizeDebit(
                            transaction, request.actor(), request.source(), request.reason());
        if (authority != null && authority.historical())
            require(
                    request.reason().equals("TREASURY_WITHDRAWAL")
                            && request.movement() == Movement.TRANSFER
                            && LedgerAccount.wallet(request.actor().player())
                                    .equals(request.target()),
                    "Collect historical Treasury into the entitled Owner's Wallet");
        Long sourceAfter = null, targetAfter = null, period = null;
        if (request.source() != null) {
            long available = balanceIn(c, request.source());
            require(request.amount() <= available, "Insufficient funds.");
            sourceAfter = available - request.amount();
        }
        if (request.target() != null) {
            try {
                targetAfter = Math.addExact(balanceIn(c, request.target()), request.amount());
            } catch (ArithmeticException overflow) {
                throw new LedgerException("The receiving balance is too large.");
            }
        }
        if (request.movement() == Movement.ISSUANCE) {
            var budget = budgetIn(c, transaction, now);
            require(request.amount() <= budget.remaining(), "The issuance budget is exhausted.");
            period = budget.period();
            c.update(ECONOMY_BUDGETS)
                    .set(ECONOMY_BUDGETS.ISSUED, budget.issued() + request.amount())
                    .where(ECONOMY_BUDGETS.PERIOD.eq(period))
                    .execute();
        }
        if (authority != null && !authority.owner())
            consumeAllowance(
                    c,
                    request.source().owner(),
                    request.actor().player(),
                    authority,
                    request.amount());
        if (request.source() != null) {
            ensureAccount(c, request.source());
            c.update(ECONOMY_ACCOUNTS)
                    .set(ECONOMY_ACCOUNTS.BALANCE, sourceAfter)
                    .where(ECONOMY_ACCOUNTS.ACCOUNT_KEY.eq(request.source().key()))
                    .execute();
        }
        if (request.target() != null) {
            ensureAccount(c, request.target());
            c.update(ECONOMY_ACCOUNTS)
                    .set(ECONOMY_ACCOUNTS.BALANCE, targetAfter)
                    .where(ECONOMY_ACCOUNTS.ACCOUNT_KEY.eq(request.target().key()))
                    .execute();
        }
        c.insertInto(ECONOMY_TRANSACTIONS)
                .set(ECONOMY_TRANSACTIONS.TRANSACTION_ID, request.id())
                .set(ECONOMY_TRANSACTIONS.CURRENCY_ID, currency.id())
                .set(ECONOMY_TRANSACTIONS.MOVEMENT, request.movement().name())
                .set(ECONOMY_TRANSACTIONS.ACTOR, request.actor().key())
                .set(ECONOMY_TRANSACTIONS.SOURCE_ACCOUNT, key(request.source()))
                .set(ECONOMY_TRANSACTIONS.TARGET_ACCOUNT, key(request.target()))
                .set(ECONOMY_TRANSACTIONS.AMOUNT, request.amount())
                .set(ECONOMY_TRANSACTIONS.REASON, request.reason())
                .set(ECONOMY_TRANSACTIONS.NOTE, request.note())
                .set(ECONOMY_TRANSACTIONS.GAME_TICK, now)
                .set(ECONOMY_TRANSACTIONS.BUDGET_PERIOD, period)
                .set(ECONOMY_TRANSACTIONS.SOURCE_AFTER, sourceAfter)
                .set(ECONOMY_TRANSACTIONS.TARGET_AFTER, targetAfter)
                .execute();
        clock.checkpoint(transaction);
        return new Receipt(
                request.id(),
                request.movement(),
                request.actor().key(),
                request.source(),
                request.target(),
                request.amount(),
                request.reason(),
                request.note(),
                now,
                period,
                sourceAfter,
                targetAfter,
                false);
    }

    @Override
    public CompletableFuture<AllowanceResult> setAllowance(
            Actor actor, UUID settlement, UUID governor, long amount, UUID id) {
        requireReady();
        if (amount < 0) throw new LedgerException("Enter a non-negative budget.");
        Objects.requireNonNull(id);
        Objects.requireNonNull(governor);
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    var r =
                            c.selectFrom(ECONOMY_ALLOWANCE_EVENTS)
                                    .where(ECONOMY_ALLOWANCE_EVENTS.OPERATION_ID.eq(id))
                                    .fetchOne();
                    if (r != null) {
                        require(
                                r.get(ECONOMY_ALLOWANCE_EVENTS.ACTOR).equals(actor.key())
                                        && r.get(ECONOMY_ALLOWANCE_EVENTS.TREASURY_UUID)
                                                .equals(settlement)
                                        && r.get(ECONOMY_ALLOWANCE_EVENTS.GOVERNOR_UUID)
                                                .equals(governor)
                                        && r.get(ECONOMY_ALLOWANCE_EVENTS.AMOUNT) == amount,
                                "That ID belongs to a different budget change.");
                        return new AllowanceResult(id, amount, true);
                    }
                    require(find(c, id) == null, "That ID belongs to a monetary transaction.");
                    var owner = treasury.inspect(transaction, actor.player(), settlement);
                    require(!owner.historical(), "Historical Treasury has no governance budgets");
                    require(owner.owner(), "Only the Owner may set treasury budgets.");
                    var delegate = treasury.inspect(transaction, governor, settlement);
                    require(delegate.governor(), "Choose a current Governor.");
                    c.insertInto(ECONOMY_ALLOWANCES)
                            .set(ECONOMY_ALLOWANCES.TREASURY_UUID, settlement)
                            .set(ECONOMY_ALLOWANCES.GOVERNOR_UUID, governor)
                            .set(
                                    ECONOMY_ALLOWANCES.OWNERSHIP_REVISION,
                                    delegate.ownershipRevision())
                            .set(ECONOMY_ALLOWANCES.PROMOTION_ID, delegate.promotionId())
                            .set(ECONOMY_ALLOWANCES.REMAINING, amount)
                            .onConflict(
                                    ECONOMY_ALLOWANCES.TREASURY_UUID,
                                    ECONOMY_ALLOWANCES.GOVERNOR_UUID)
                            .doUpdate()
                            .set(
                                    ECONOMY_ALLOWANCES.OWNERSHIP_REVISION,
                                    delegate.ownershipRevision())
                            .set(ECONOMY_ALLOWANCES.PROMOTION_ID, delegate.promotionId())
                            .set(ECONOMY_ALLOWANCES.REMAINING, amount)
                            .execute();
                    c.insertInto(ECONOMY_ALLOWANCE_EVENTS)
                            .set(ECONOMY_ALLOWANCE_EVENTS.OPERATION_ID, id)
                            .set(ECONOMY_ALLOWANCE_EVENTS.ACTOR, actor.key())
                            .set(ECONOMY_ALLOWANCE_EVENTS.TREASURY_UUID, settlement)
                            .set(ECONOMY_ALLOWANCE_EVENTS.GOVERNOR_UUID, governor)
                            .set(ECONOMY_ALLOWANCE_EVENTS.AMOUNT, amount)
                            .set(
                                    ECONOMY_ALLOWANCE_EVENTS.OWNERSHIP_REVISION,
                                    delegate.ownershipRevision())
                            .set(ECONOMY_ALLOWANCE_EVENTS.PROMOTION_ID, delegate.promotionId())
                            .set(ECONOMY_ALLOWANCE_EVENTS.GAME_TICK, clock.ticks())
                            .execute();
                    clock.checkpoint(transaction);
                    return new AllowanceResult(id, amount, false);
                });
    }

    @Override
    public CompletableFuture<Long> allowance(Actor actor, UUID settlement, UUID governor) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    requireReady();
                    var reader = treasury.inspect(transaction, actor.player(), settlement);
                    require(
                            reader.owner()
                                    || (reader.governor()
                                            && Objects.equals(actor.player(), governor)),
                            "Treasury budget access denied.");
                    var current = treasury.inspect(transaction, governor, settlement);
                    if (!current.governor()) return 0L;
                    return remainingAllowance(c, settlement, governor, current);
                });
    }

    @Override
    public void requireEmptyTreasury(Transaction transaction, UUID settlement) throws SQLException {
        DSLContext c = DatabaseAccess.context(transaction);
        if (balanceIn(c, LedgerAccount.treasury(settlement)) != 0L)
            throw new LedgerException("Withdraw the treasury funds before abandoning.");
    }

    private void authorizeRead(Transaction transaction, Actor actor, LedgerAccount account)
            throws SQLException {
        requireReady();
        Objects.requireNonNull(actor);
        Objects.requireNonNull(account);
        if (actor.administrator()) return;
        if (account.kind() == LedgerAccount.Kind.WALLET) {
            require(account.owner().equals(actor.player()), "That wallet is private.");
            return;
        }
        var authority = treasury.inspect(transaction, actor.player(), account.owner());
        require(
                authority.owner() || authority.governor(),
                "Treasury access requires Owner or Governor.");
    }

    private TreasuryAuthority authorizeDebit(
            Transaction transaction, Actor actor, LedgerAccount account, String reason)
            throws SQLException {
        if (account.kind() == LedgerAccount.Kind.WALLET) {
            require(
                    account.owner().equals(actor.player()),
                    "Only the wallet owner may spend these funds.");
            return null;
        }
        var authority = treasury.inspect(transaction, actor.player(), account.owner());
        require(
                authority.owner() || authority.governor(),
                "Treasury spending requires Owner or Governor.");
        if (reason.equals("TREASURY_WITHDRAWAL"))
            require(authority.owner(), "Only the Owner may withdraw treasury funds.");
        return authority;
    }

    private static long remainingAllowance(
            DSLContext c, UUID settlement, UUID governor, TreasuryAuthority authority)
            throws SQLException {
        var r =
                c.selectFrom(ECONOMY_ALLOWANCES)
                        .where(
                                ECONOMY_ALLOWANCES
                                        .TREASURY_UUID
                                        .eq(settlement)
                                        .and(ECONOMY_ALLOWANCES.GOVERNOR_UUID.eq(governor)))
                        .fetchOne();
        if (r == null
                || r.get(ECONOMY_ALLOWANCES.OWNERSHIP_REVISION) != authority.ownershipRevision()
                || !Objects.equals(r.get(ECONOMY_ALLOWANCES.PROMOTION_ID), authority.promotionId()))
            return 0L;
        return r.get(ECONOMY_ALLOWANCES.REMAINING);
    }

    private static void consumeAllowance(
            DSLContext c, UUID settlement, UUID governor, TreasuryAuthority authority, long amount)
            throws SQLException {
        long remaining = remainingAllowance(c, settlement, governor, authority);
        require(amount <= remaining, "Ask the Owner for a treasury spending budget.");
        c.update(ECONOMY_ALLOWANCES)
                .set(ECONOMY_ALLOWANCES.REMAINING, remaining - amount)
                .where(
                        ECONOMY_ALLOWANCES
                                .TREASURY_UUID
                                .eq(settlement)
                                .and(ECONOMY_ALLOWANCES.GOVERNOR_UUID.eq(governor)))
                .execute();
    }

    private Budget budgetIn(DSLContext c, Transaction transaction, long now) throws SQLException {
        requireReady();
        long period = now / policy.periodTicks();
        c.insertInto(ECONOMY_BUDGETS)
                .set(ECONOMY_BUDGETS.PERIOD, period)
                .set(ECONOMY_BUDGETS.BUDGET_LIMIT, policy.issuanceLimit())
                .set(ECONOMY_BUDGETS.ISSUED, 0L)
                .onDuplicateKeyIgnore()
                .execute();
        var r =
                c.select(ECONOMY_BUDGETS.BUDGET_LIMIT, ECONOMY_BUDGETS.ISSUED)
                        .from(ECONOMY_BUDGETS)
                        .where(ECONOMY_BUDGETS.PERIOD.eq(period))
                        .fetchOne();
        if (r == null) throw new SQLException("Missing monetary period");
        clock.checkpoint(transaction);
        return new Budget(
                period,
                r.get(ECONOMY_BUDGETS.BUDGET_LIMIT),
                r.get(ECONOMY_BUDGETS.ISSUED),
                now,
                policy.periodTicks());
    }

    private void ensureAccount(DSLContext c, LedgerAccount account) throws SQLException {
        c.insertInto(ECONOMY_ACCOUNTS)
                .set(ECONOMY_ACCOUNTS.ACCOUNT_KEY, account.key())
                .set(ECONOMY_ACCOUNTS.CURRENCY_ID, currency.id())
                .set(ECONOMY_ACCOUNTS.KIND, account.kind().name())
                .set(ECONOMY_ACCOUNTS.OWNER_UUID, account.owner())
                .set(ECONOMY_ACCOUNTS.BALANCE, 0L)
                .onDuplicateKeyIgnore()
                .execute();
    }

    private static long balanceIn(DSLContext c, LedgerAccount account) throws SQLException {
        var r =
                c.select(ECONOMY_ACCOUNTS.BALANCE)
                        .from(ECONOMY_ACCOUNTS)
                        .where(ECONOMY_ACCOUNTS.ACCOUNT_KEY.eq(account.key()))
                        .fetchOne();
        return r == null ? 0L : r.get(ECONOMY_ACCOUNTS.BALANCE);
    }

    private static Receipt find(DSLContext c, UUID id) throws SQLException {
        var r =
                c.selectFrom(ECONOMY_TRANSACTIONS)
                        .where(ECONOMY_TRANSACTIONS.TRANSACTION_ID.eq(id))
                        .fetchOne();
        return r == null ? null : receipt(r, false);
    }

    private static Receipt receipt(Record r, boolean replayed) {
        return new Receipt(
                r.get(ECONOMY_TRANSACTIONS.TRANSACTION_ID),
                Movement.valueOf(r.get(ECONOMY_TRANSACTIONS.MOVEMENT)),
                r.get(ECONOMY_TRANSACTIONS.ACTOR),
                LedgerAccount.fromKey(r.get(ECONOMY_TRANSACTIONS.SOURCE_ACCOUNT)),
                LedgerAccount.fromKey(r.get(ECONOMY_TRANSACTIONS.TARGET_ACCOUNT)),
                r.get(ECONOMY_TRANSACTIONS.AMOUNT),
                r.get(ECONOMY_TRANSACTIONS.REASON),
                r.get(ECONOMY_TRANSACTIONS.NOTE),
                r.get(ECONOMY_TRANSACTIONS.GAME_TICK),
                r.get(ECONOMY_TRANSACTIONS.BUDGET_PERIOD),
                r.get(ECONOMY_TRANSACTIONS.SOURCE_AFTER),
                r.get(ECONOMY_TRANSACTIONS.TARGET_AFTER),
                replayed);
    }

    private static String key(LedgerAccount account) {
        return account == null ? null : account.key();
    }

    private static void requireTransaction(DSLContext c) throws SQLException {
        // Inspect the owning connection; never start or commit a transaction here.
        if (c.connectionResult(connection -> connection.getAutoCommit()))
            throw new IllegalStateException("Ledger operations require an owning transaction");
    }

    private void requireReady() {
        if (!initialized) throw new IllegalStateException("Ledger is not initialized");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new LedgerException(message);
    }
}
