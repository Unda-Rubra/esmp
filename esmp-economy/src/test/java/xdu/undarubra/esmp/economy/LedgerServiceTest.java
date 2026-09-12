package xdu.undarubra.esmp.economy;

import static org.junit.jupiter.api.Assertions.*;

import static xdu.undarubra.esmp.economy.generated.Tables.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.land.LandValuePolicy;
import xdu.undarubra.esmp.civilization.land.LandValueProfile;
import xdu.undarubra.esmp.civilization.land.LandValueService;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.math.BigInteger;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Timeout(30)
class LedgerServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    LandValueService landValues;
    SettlementService settlements;
    LedgerService ledger;
    UUID source = UUID.randomUUID(),
            alice = UUID.randomUUID(),
            bob = UUID.randomUUID(),
            governor = UUID.randomUUID(),
            world,
            settlement;
    final LedgerService.Actor admin = new LedgerService.Actor(null, true);
    long raw;

    @BeforeEach
    void open() {
        openStore();
    }

    void openStore() {
        openStoreBeforeLedgerInitialization();
        ledger.initialize().join();
    }

    void restartBeforeLedgerInitialization() {
        // Reopen so DatabaseSchema detects the legacy fixture, rather than treating a
        // downgrade of an already-open managed store as a migration. Keep source and
        // settlement identities, and leave ledger initialization to the test.
        db.close();
        openStoreBeforeLedgerInitialization();
    }

    void openStoreBeforeLedgerInitialization() {
        db = new WorldStore(directory.resolve("money.db"));
        world =
                db.initialize(
                                List.of(
                                        new WorldDefinition(
                                                "MAIN", "civilization", WorldEnvironment.NORMAL)),
                                List.of(new WorldBinding("MAIN", source, "world")))
                        .join()
                        .instances()
                        .getFirst()
                        .instanceId();
        clock = new GameClock(db);
        clock.initialize(source, raw, true).join();
        landValues =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        db, LandValuePolicy.defaults());
        landValues.initialize().join();
        settlements =
                xdu.undarubra.esmp.civilization.api.SettlementServices.settlement(
                        db,
                        SettlementPolicy.defaults(),
                        clock,
                        ignored -> false,
                        (tx, treasuryId) -> ledger.requireEmptyTreasury(tx, treasuryId),
                        ClaimPricingPolicy.defaults(),
                        (c, quote) -> {
                            // Governance fixture only: establish the treasury before ledger
                            // initialization. These tests exercise ledger operations separately,
                            // not claim debits; keep their balances and issuance budgets isolated.
                        },
                        (c, id) -> true);
        settlements.initialize().join();
        if (settlement == null) {
            settlement = create(alice, world, 0, 0).settlementId();
        }
        ledger = newLedger(CurrencyDefinition.defaults(), new LedgerPolicy(24000, 500));
        // Do not finishMigration here: the ledger baseline has not been initialized yet.
    }

    SettlementChange create(UUID actor, UUID worldInstance, int x, int z) {
        // Each location has the same immutable fixture valuation. Capture is idempotent;
        // no generated-table inserts or production profile/pricing bypasses are used.
        landValues
                .capture(
                        List.of(
                                new LandValueProfile(
                                        worldInstance,
                                        x,
                                        z,
                                        landValues.policy().version(),
                                        landValues.policy().baseValue(),
                                        LandValueProfile.Terrain.values()[0])))
                .join();
        var quote = settlements.quoteCreate(actor, worldInstance, new ChunkPosition(x, z)).join();
        return settlements.purchase(actor, quote.id()).join();
    }

    LedgerService newLedger(CurrencyDefinition definition, LedgerPolicy policy) {
        return xdu.undarubra.esmp.economy.api.EconomyServices.ledger(
                db,
                clock,
                definition,
                policy,
                (c, actor, id) -> {
                    var p = settlements.treasuryPermission(c, actor, id);
                    return new LedgerService.TreasuryAuthority(
                            p.role() == Role.OWNER,
                            p.role() == Role.GOVERNOR,
                            p.ownershipRevision(),
                            p.promotionId());
                });
    }

    @AfterEach
    void close() {
        db.close();
    }

    LedgerService.Actor actor(UUID id) {
        return new LedgerService.Actor(id, false);
    }

    LedgerAccount wallet(UUID id) {
        return LedgerAccount.wallet(id);
    }

    LedgerAccount treasury() {
        return LedgerAccount.treasury(settlement);
    }

    LedgerService.Request issue(UUID id, LedgerAccount target, long amount) {
        return new LedgerService.Request(
                id,
                admin,
                LedgerService.Movement.ISSUANCE,
                null,
                target,
                amount,
                "ADMIN_GRANT",
                "");
    }

    LedgerService.Request transfer(
            UUID id, UUID who, LedgerAccount from, LedgerAccount to, long amount, String reason) {
        return new LedgerService.Request(
                id, actor(who), LedgerService.Movement.TRANSFER, from, to, amount, reason, "");
    }

    void grant(LedgerAccount target, long amount) {
        ledger.post(issue(UUID.randomUUID(), target, amount)).join();
    }

    long balance(LedgerAccount account) {
        return ledger.balance(admin, account).join();
    }

    void rejected(Runnable operation) {
        var failure = assertThrows(CompletionException.class, operation::run);
        assertTrue(
                failure.getCause() instanceof LedgerException
                        || failure.getCause() instanceof SettlementException,
                failure.toString());
    }

    void join(UUID player) {
        var offer =
                settlements
                        .offer(alice, settlement, player, SettlementOffer.Type.INVITE)
                        .join()
                        .offer();
        settlements.accept(player, offer.id(), false).join();
    }

    void promote() {
        var offer =
                settlements
                        .offer(alice, settlement, governor, SettlementOffer.Type.PROMOTE)
                        .join()
                        .offer();
        settlements.accept(governor, offer.id(), false).join();
    }

    @Test
    void transfersConserveSupplyAndSinksDestroyIt() {
        grant(wallet(alice), 200);
        ledger.post(
                        transfer(
                                UUID.randomUUID(),
                                alice,
                                wallet(alice),
                                wallet(bob),
                                60,
                                "PLAYER_TRANSFER"))
                .join();
        ledger.post(
                        transfer(
                                UUID.randomUUID(),
                                alice,
                                wallet(alice),
                                treasury(),
                                40,
                                "SETTLEMENT_DONATION"))
                .join();
        assertEquals(100, balance(wallet(alice)));
        assertEquals(60, balance(wallet(bob)));
        assertEquals(40, balance(treasury()));
        assertEquals(BigInteger.valueOf(200), ledger.supply(admin).join().total());
        ledger.post(
                        new LedgerService.Request(
                                UUID.randomUUID(),
                                actor(alice),
                                LedgerService.Movement.SINK,
                                wallet(alice),
                                null,
                                10,
                                "SYSTEM_SERVICE",
                                ""))
                .join();
        assertEquals(90, balance(wallet(alice)));
        assertEquals(BigInteger.valueOf(190), ledger.supply(admin).join().total());
        assertEquals(200, ledger.budget(admin).join().issued());
        assertEquals(
                Set.of(
                        LedgerService.Movement.TRANSFER,
                        LedgerService.Movement.ISSUANCE,
                        LedgerService.Movement.SINK),
                new HashSet<>(
                        ledger.history(actor(alice), wallet(alice)).join().stream()
                                .map(LedgerService.Receipt::movement)
                                .toList()));
    }

    @Test
    void replaySurvivesRestartAndRejectsChangedPayload() {
        UUID id = UUID.randomUUID();
        var request = issue(id, wallet(alice), 100);
        assertFalse(ledger.post(request).join().replayed());
        assertTrue(ledger.post(request).join().replayed());
        db.close();
        openStore();
        assertTrue(ledger.post(request).join().replayed());
        assertEquals(100, balance(wallet(alice)));
        assertEquals(100, ledger.budget(admin).join().issued());
        assertEquals(1, ledger.history(actor(alice), wallet(alice)).join().size());
        rejected(() -> ledger.post(issue(id, wallet(alice), 101)).join());
        rejected(() -> ledger.post(issue(id, wallet(bob), 100)).join());
        assertEquals(100, balance(wallet(alice)));
        assertEquals(0, balance(wallet(bob)));
    }

    @Test
    void onlyFirstJoinsEnrollAndConcurrentReconnectsCannotRepeatFunding() {
        assertNull(ledger.startingGrant(bob, false, 100).join());
        assertEquals(0, balance(wallet(bob)));
        var first = ledger.startingGrant(alice, true, 100);
        var duplicate = ledger.startingGrant(alice, true, 100);
        assertTrue(first.join().issuedNow());
        assertFalse(duplicate.join().issuedNow());
        assertFalse(ledger.startingGrant(alice, false, 200).join().issuedNow());
        assertEquals(100, balance(wallet(alice)));
        assertEquals(100, ledger.budget(admin).join().issued());
        assertEquals(
                "STARTING_FUNDS",
                ledger.history(actor(alice), wallet(alice)).join().getFirst().reason());
    }

    @Test
    void pendingStartingFundsSurviveRestartAndKeepTheirOriginalAmount() {
        grant(wallet(bob), 500);
        assertFalse(ledger.startingGrant(alice, true, 100).join().paid());
        db.close();
        openStore();
        assertEquals(100, ledger.startingGrant(alice, false, 250).join().amount());
        assertEquals(0, ledger.retryStartingGrants().join().processed());
        raw = 24000;
        clock.sample(source, raw, true);
        var paid = ledger.retryStartingGrants().join();
        assertEquals(alice, paid.paid().getFirst().player());
        assertEquals(100, balance(wallet(alice)));
        assertEquals(100, ledger.budget(admin).join().issued());
        assertEquals(0, ledger.retryStartingGrants().join().processed());
        db.close();
        openStore();
        assertTrue(ledger.startingGrant(alice, false, 250).join().paid());
        assertEquals(100, balance(wallet(alice)));
    }

    @Test
    void unaffordablePendingGrantCannotBlockOtherPlayersOrAccumulateSkippedBudgets() {
        grant(wallet(bob), 500);
        ledger.startingGrant(alice, true, 600).join();
        ledger.startingGrant(governor, true, 100).join();
        raw = 240000;
        clock.sample(source, raw, true);
        var batch = ledger.retryStartingGrants().join();
        assertEquals(2, batch.processed());
        assertEquals(
                List.of(governor),
                batch.paid().stream().map(LedgerService.StartingGrant::player).toList());
        assertEquals(0, balance(wallet(alice)));
        assertEquals(100, balance(wallet(governor)));
        assertEquals(400, ledger.budget(admin).join().remaining());
        assertEquals(0, ledger.retryStartingGrants().join().processed());
    }

    @Test
    void startingFundsCannotBeRepeatedWithAnotherTransactionIdOrAfterRestart() {
        ledger.startingFunds(admin, alice, 100).join();
        db.close();
        openStore();
        assertTrue(ledger.startingFunds(admin, alice, 100).join().replayed());
        rejected(() -> ledger.startingFunds(admin, alice, 101).join());
        rejected(
                () ->
                        ledger.post(
                                        new LedgerService.Request(
                                                UUID.randomUUID(),
                                                admin,
                                                LedgerService.Movement.ISSUANCE,
                                                null,
                                                wallet(alice),
                                                100,
                                                "STARTING_FUNDS",
                                                ""))
                                .join());
        assertEquals(100, balance(wallet(alice)));
        assertEquals(100, ledger.budget(admin).join().issued());
    }

    @Test
    void budgetsFreezeAndSkippedPeriodsDoNotAccumulate() {
        grant(wallet(alice), 500);
        rejected(() -> grant(wallet(bob), 1));
        clock.sample(source, 100000, false);
        raw = 100000;
        assertEquals(0, ledger.budget(admin).join().remaining());
        clock.sample(source, raw, true);
        raw += 240000;
        clock.sample(source, raw, true);
        var next = ledger.budget(admin).join();
        assertEquals(10, next.period());
        assertEquals(500, next.remaining());
        assertEquals(BigInteger.valueOf(500), ledger.supply(admin).join().total());
        grant(wallet(bob), 1);
        assertEquals(499, ledger.budget(admin).join().remaining());
    }

    @Test
    void concurrentSpendingCannotOverdrawOrPartiallyCommit() {
        grant(wallet(alice), 50);
        var payments = new ArrayList<CompletableFuture<LedgerService.Receipt>>();
        for (int i = 0; i < 10; i++)
            payments.add(
                    ledger.post(
                            transfer(
                                    UUID.randomUUID(),
                                    alice,
                                    wallet(alice),
                                    wallet(bob),
                                    10,
                                    "PLAYER_TRANSFER")));
        int success = 0;
        for (var payment : payments) {
            try {
                payment.join();
                success++;
            } catch (CompletionException e) {
                assertInstanceOf(LedgerException.class, e.getCause());
            }
        }
        assertEquals(5, success);
        assertEquals(0, balance(wallet(alice)));
        assertEquals(50, balance(wallet(bob)));
        assertEquals(BigInteger.valueOf(50), ledger.supply(admin).join().total());
        rejected(
                () ->
                        ledger.post(
                                        transfer(
                                                UUID.randomUUID(),
                                                bob,
                                                wallet(alice),
                                                wallet(bob),
                                                1,
                                                "PLAYER_TRANSFER"))
                                .join());
    }

    @Test
    void accountOverflowIsRejectedAndLargeAggregateSupplyRemainsExact() {
        grant(wallet(alice), 5);
        ledger = newLedger(CurrencyDefinition.defaults(), new LedgerPolicy(24000, Long.MAX_VALUE));
        ledger.initialize().join();
        raw = 24000;
        clock.sample(source, raw, true);
        grant(wallet(bob), Long.MAX_VALUE);
        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5)),
                ledger.supply(admin).join().total());
        rejected(
                () ->
                        ledger.post(
                                        transfer(
                                                UUID.randomUUID(),
                                                alice,
                                                wallet(alice),
                                                wallet(bob),
                                                1,
                                                "PLAYER_TRANSFER"))
                                .join());
        assertEquals(5, balance(wallet(alice)));
        assertEquals(Long.MAX_VALUE, balance(wallet(bob)));
        assertThrows(LedgerException.class, () -> issue(UUID.randomUUID(), wallet(alice), -1));
    }

    @Test
    void owningTransactionRollbackRestoresBalancesBudgetAndTransactionIdentity() {
        var request = issue(UUID.randomUUID(), wallet(alice), 100);
        var failure =
                assertThrows(
                        CompletionException.class,
                        () ->
                                db.transact(
                                                c -> {
                                                    ledger.post(c, request);
                                                    throw new SQLException(
                                                            "Owning operation failed");
                                                })
                                        .join());
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals(0, balance(wallet(alice)));
        assertEquals(0, ledger.budget(admin).join().issued());
        assertTrue(ledger.history(actor(alice), wallet(alice)).join().isEmpty());
        assertFalse(ledger.post(request).join().replayed());
        assertEquals(100, balance(wallet(alice)));
    }

    @Test
    void governorAllowancesAreAuditedAndBoundToOwnerAndPromotion() {
        join(governor);
        promote();
        join(bob);
        grant(treasury(), 200);
        UUID budgetId = UUID.randomUUID();
        ledger.setAllowance(actor(alice), settlement, governor, 60, budgetId).join();
        ledger.post(
                        transfer(
                                UUID.randomUUID(),
                                governor,
                                treasury(),
                                wallet(bob),
                                50,
                                "TREASURY_PAYMENT"))
                .join();
        assertTrue(
                ledger.setAllowance(actor(alice), settlement, governor, 60, budgetId)
                        .join()
                        .replayed());
        assertEquals(10, ledger.allowance(actor(governor), settlement, governor).join());
        rejected(
                () ->
                        ledger.post(
                                        transfer(
                                                UUID.randomUUID(),
                                                governor,
                                                treasury(),
                                                wallet(bob),
                                                11,
                                                "TREASURY_PAYMENT"))
                                .join());
        rejected(
                () ->
                        ledger.post(
                                        transfer(
                                                UUID.randomUUID(),
                                                governor,
                                                treasury(),
                                                wallet(governor),
                                                1,
                                                "TREASURY_WITHDRAWAL"))
                                .join());
        rejected(() -> ledger.post(issue(budgetId, wallet(alice), 1)).join());
        settlements.demote(alice, settlement, governor).join();
        promote();
        assertEquals(0, ledger.allowance(actor(governor), settlement, governor).join());
        ledger.setAllowance(actor(alice), settlement, governor, 80, UUID.randomUUID()).join();
        var transfer =
                settlements
                        .offer(alice, settlement, bob, SettlementOffer.Type.TRANSFER)
                        .join()
                        .offer();
        settlements.accept(bob, transfer.id(), false).join();
        rejected(
                () ->
                        ledger.post(
                                        transfer(
                                                UUID.randomUUID(),
                                                governor,
                                                treasury(),
                                                wallet(alice),
                                                1,
                                                "TREASURY_PAYMENT"))
                                .join());
        rejected(
                () ->
                        ledger.post(
                                        transfer(
                                                UUID.randomUUID(),
                                                alice,
                                                treasury(),
                                                wallet(alice),
                                                1,
                                                "TREASURY_WITHDRAWAL"))
                                .join());
        assertEquals(150, balance(treasury()));
        ledger.post(
                        transfer(
                                UUID.randomUUID(),
                                bob,
                                treasury(),
                                wallet(bob),
                                40,
                                "TREASURY_WITHDRAWAL"))
                .join();
        assertEquals(110, balance(treasury()));
    }

    @Test
    void localBansAndFundedTreasuriesRespectSettlementLifecycle() {
        grant(wallet(bob), 100);
        rejected(() -> ledger.balance(actor(bob), treasury()).join());
        ledger.post(
                        transfer(
                                UUID.randomUUID(),
                                bob,
                                wallet(bob),
                                treasury(),
                                20,
                                "SETTLEMENT_DONATION"))
                .join();
        settlements.ban(alice, settlement, bob, true).join();
        rejected(
                () ->
                        ledger.post(
                                        transfer(
                                                UUID.randomUUID(),
                                                bob,
                                                wallet(bob),
                                                treasury(),
                                                1,
                                                "SETTLEMENT_DONATION"))
                                .join());
        ledger.post(
                        transfer(
                                UUID.randomUUID(),
                                bob,
                                wallet(bob),
                                wallet(alice),
                                5,
                                "PLAYER_TRANSFER"))
                .join();
        var warning =
                settlements
                        .offer(alice, settlement, alice, SettlementOffer.Type.ABANDON)
                        .join()
                        .offer();
        rejected(() -> settlements.accept(alice, warning.id(), true).join());
        ledger.post(
                        transfer(
                                UUID.randomUUID(),
                                alice,
                                treasury(),
                                wallet(alice),
                                20,
                                "TREASURY_WITHDRAWAL"))
                .join();
        settlements.accept(alice, warning.id(), true).join();
        rejected(() -> grant(treasury(), 1));
        assertEquals(0, balance(treasury()));
        assertFalse(ledger.history(admin, treasury()).join().isEmpty());
    }

    @Test
    void displayChangesPreserveIdentityAndPeriodChangesRequireMigration() {
        grant(wallet(alice), 100);
        var renamed =
                newLedger(
                        new CurrencyDefinition(
                                "esmp:coin", "crown", "crowns", "C", "{symbol}{amount} {name}"),
                        new LedgerPolicy(24000, 1000));
        renamed.initialize().join();
        assertEquals(100, renamed.balance(admin, wallet(alice)).join());
        assertEquals("C1 crown", renamed.currency().display(1));
        assertEquals("C2 crowns", renamed.currency().display(2));
        assertEquals(500, renamed.budget(admin).join().limit());
        raw = 24000;
        clock.sample(source, raw, true);
        assertEquals(1000, renamed.budget(admin).join().limit());
        assertThrows(
                CompletionException.class,
                () ->
                        newLedger(CurrencyDefinition.defaults(), new LedgerPolicy(1, 500))
                                .initialize()
                                .join());
        assertThrows(
                CompletionException.class,
                () ->
                        newLedger(
                                        new CurrencyDefinition(
                                                "esmp:other", "unit", "units", "", "{amount}"),
                                        new LedgerPolicy(24000, 500))
                                .initialize()
                                .join());
    }

    @Test
    void historyUsesInsertionOrderRatherThanTickOrUuidOrderAndKeepsTwenty() {
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 25; i++) {
            UUID id = new UUID(0L, 100L - i);
            ids.add(id);
            ledger.post(issue(id, wallet(alice), 1)).join();
        }
        var history = ledger.history(admin, wallet(alice)).join();
        var expected = new ArrayList<>(ids.subList(5, 25));
        Collections.reverse(expected);
        assertEquals(expected, history.stream().map(LedgerService.Receipt::id).toList());
        assertEquals(1, history.stream().map(LedgerService.Receipt::gameTick).distinct().count());
        assertTrue(history.stream().allMatch(r -> r.sourceAfter() == null));
        assertEquals(Long.valueOf(25L), history.getFirst().targetAfter());
        assertEquals(Long.valueOf(0L), history.getFirst().budgetPeriod());
        assertEquals("CONSOLE", history.getFirst().actor());
    }

    @Test
    void dueGrantBatchIsBoundedAndBreaksEqualTickTiesByInsertionOrder() {
        grant(wallet(bob), 500);
        var players = new ArrayList<UUID>();
        for (int i = 0; i < LedgerService.STARTING_GRANT_BATCH_SIZE + 1; i++) {
            UUID player = new UUID(1L, 100L - i);
            players.add(player);
            assertFalse(ledger.startingGrant(player, true, 1).join().paid());
        }
        raw = 24000;
        clock.sample(source, raw, true);
        var first = ledger.retryStartingGrants().join();
        assertEquals(LedgerService.STARTING_GRANT_BATCH_SIZE, first.processed());
        assertEquals(
                players.subList(0, LedgerService.STARTING_GRANT_BATCH_SIZE),
                first.paid().stream().map(LedgerService.StartingGrant::player).toList());
        var second = ledger.retryStartingGrants().join();
        assertEquals(1, second.processed());
        assertEquals(players.getLast(), second.paid().getFirst().player());
        assertEquals(33, ledger.budget(admin).join().issued());
        assertEquals(0, ledger.retryStartingGrants().join().processed());
    }

    @Test
    void schemaOneUpgradePreservesLedgerAllowancesBudgetsAndOneTimeGrantIdentity() {
        join(governor);
        promote();
        var request = issue(UUID.randomUUID(), treasury(), 200);
        ledger.post(request).join();
        var oldGrant = ledger.startingFunds(admin, alice, 100).join();
        UUID allowanceId = UUID.randomUUID();
        ledger.setAllowance(actor(alice), settlement, governor, 60, allowanceId).join();
        // Deliberate pre-Liquibase fixture: remove LB metadata and only V002's
        // additions from canonical economy schema 2. Civilization's claim-pricing
        // schema, progress and purchased receipts remain intact.
        db.transact(
                        c -> {
                            var context = xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c);
                            context.execute("DROP TABLE IF EXISTS DATABASECHANGELOG");
                            context.execute("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK");
                            context.execute("PRAGMA user_version=1");
                            context.execute("DROP TABLE economy_starting_grants");
                            context.update(ECONOMY_METADATA)
                                    .set(ECONOMY_METADATA.SCHEMA_VERSION, 1L)
                                    .where(ECONOMY_METADATA.ID.eq(1L))
                                    .execute();
                            return null;
                        })
                .join();
        restartBeforeLedgerInitialization();
        ledger.initialize().join();
        long version =
                db.transact(
                                c ->
                                        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                                .select(ECONOMY_METADATA.SCHEMA_VERSION)
                                                .from(ECONOMY_METADATA)
                                                .where(ECONOMY_METADATA.ID.eq(1L))
                                                .fetchSingle(ECONOMY_METADATA.SCHEMA_VERSION))
                        .join();
        assertEquals(2L, version);
        assertTrue(ledger.post(request).join().replayed());
        assertTrue(ledger.startingFunds(admin, alice, 100).join().replayed());
        assertEquals(oldGrant.id(), ledger.history(admin, wallet(alice)).join().getFirst().id());
        assertEquals(200, balance(treasury()));
        assertEquals(100, balance(wallet(alice)));
        assertEquals(300, ledger.budget(admin).join().issued());
        assertEquals(60, ledger.allowance(actor(governor), settlement, governor).join());
        assertTrue(
                ledger.setAllowance(actor(alice), settlement, governor, 60, allowanceId)
                        .join()
                        .replayed());
        var reconciled = ledger.startingGrant(alice, true, 250).join();
        assertTrue(reconciled.paid());
        assertFalse(reconciled.issuedNow());
        assertEquals(100, reconciled.amount());
        assertEquals(300, ledger.budget(admin).join().issued());
        assertTrue(ledger.startingGrant(bob, true, 25).join().issuedNow());
        ledger.initialize().join();
        assertEquals(325, ledger.budget(admin).join().issued());
    }

    @Test
    void schemaOneIdentityChecksPrecedeUpgradeAndFailedInitializationIsNotReady() {
        // Deliberate pre-Liquibase fixture; no second copy of schema DDL is maintained
        // in tests. Downgrade economy only, retaining civilization's claim-pricing state.
        db.transact(
                        c -> {
                            var context = xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c);
                            context.execute("DROP TABLE IF EXISTS DATABASECHANGELOG");
                            context.execute("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK");
                            context.execute("PRAGMA user_version=1");
                            context.execute("DROP TABLE economy_starting_grants");
                            context.update(ECONOMY_METADATA)
                                    .set(ECONOMY_METADATA.SCHEMA_VERSION, 1L)
                                    .where(ECONOMY_METADATA.ID.eq(1L))
                                    .execute();
                            return null;
                        })
                .join();
        restartBeforeLedgerInitialization();
        var wrongPeriod = newLedger(CurrencyDefinition.defaults(), new LedgerPolicy(1, 500));
        var wrongCurrency =
                newLedger(
                        new CurrencyDefinition("esmp:other", "unit", "units", "", "{amount}"),
                        new LedgerPolicy(24000, 500));
        for (var invalid : List.of(wrongPeriod, wrongCurrency)) {
            var failure =
                    assertThrows(CompletionException.class, () -> invalid.initialize().join());
            assertInstanceOf(SQLException.class, failure.getCause());
            assertEquals(
                    "Currency identity or monetary period changed; explicit migration required",
                    failure.getCause().getMessage());
            var notReady =
                    assertThrows(
                            CompletionException.class,
                            () -> invalid.balance(admin, wallet(alice)).join());
            assertInstanceOf(IllegalStateException.class, notReady.getCause());
        }
        db.transact(
                        c -> {
                            // Raw queries here inspect the deliberate migration fixture's SQLite
                            // catalog.
                            assertEquals(
                                    0,
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .fetch(
                                                    "SELECT name FROM sqlite_master WHERE"
                                                            + " type='table' AND"
                                                            + " name='economy_starting_grants'")
                                            .size());
                            assertEquals(
                                    Long.valueOf(1L),
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .select(ECONOMY_METADATA.SCHEMA_VERSION)
                                            .from(ECONOMY_METADATA)
                                            .where(ECONOMY_METADATA.ID.eq(1L))
                                            .fetchSingle(ECONOMY_METADATA.SCHEMA_VERSION));
                            return null;
                        })
                .join();
        var valid = newLedger(CurrencyDefinition.defaults(), new LedgerPolicy(24000, 500));
        valid.initialize().join();
        assertTrue(valid.startingGrant(alice, true, 100).join().issuedNow());
    }

    @Test
    void unsupportedSchemaVersionsAreRejectedWithoutNarrowingOrBecomingReady() {
        for (long version : new long[] {0L, 3L, Long.MAX_VALUE}) {
            // Deliberate unsupported legacy fixture, not corruption of a managed schema.
            db.transact(
                            c -> {
                                var context =
                                        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c);
                                context.execute("DROP TABLE IF EXISTS DATABASECHANGELOG");
                                context.execute("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK");
                                context.execute("PRAGMA user_version=1");
                                context.update(ECONOMY_METADATA)
                                        .set(ECONOMY_METADATA.SCHEMA_VERSION, version)
                                        .where(ECONOMY_METADATA.ID.eq(1L))
                                        .execute();
                                return null;
                            })
                    .join();
            restartBeforeLedgerInitialization();
            var invalid = newLedger(CurrencyDefinition.defaults(), new LedgerPolicy(24000, 500));
            var failure =
                    assertThrows(CompletionException.class, () -> invalid.initialize().join());
            assertInstanceOf(SQLException.class, failure.getCause());
            assertEquals("Unsupported economy schema", failure.getCause().getMessage());
            var notReady =
                    assertThrows(
                            CompletionException.class, () -> invalid.retryStartingGrants().join());
            assertInstanceOf(IllegalStateException.class, notReady.getCause());
            long stored =
                    db.transact(
                                    c ->
                                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(
                                                            c)
                                                    .select(ECONOMY_METADATA.SCHEMA_VERSION)
                                                    .from(ECONOMY_METADATA)
                                                    .where(ECONOMY_METADATA.ID.eq(1L))
                                                    .fetchSingle(ECONOMY_METADATA.SCHEMA_VERSION))
                            .join();
            assertEquals(version, stored);
        }
    }

    @Test
    void allReadAndWriteEntryPointsRetainReadinessChecks() {
        var uninitialized = newLedger(CurrencyDefinition.defaults(), new LedgerPolicy(24000, 500));
        List<Runnable> asynchronous =
                List.of(
                        () -> uninitialized.balance(admin, wallet(alice)).join(),
                        () -> uninitialized.budget(admin).join(),
                        () -> uninitialized.supply(admin).join(),
                        () -> uninitialized.history(admin, wallet(alice)).join(),
                        () -> uninitialized.post(issue(UUID.randomUUID(), wallet(alice), 1)).join(),
                        () -> uninitialized.startingFunds(admin, alice, 1).join(),
                        () -> uninitialized.startingGrant(alice, true, 1).join(),
                        () -> uninitialized.retryStartingGrants().join(),
                        () -> uninitialized.allowance(actor(alice), settlement, governor).join());
        for (var operation : asynchronous) {
            var failure = assertThrows(CompletionException.class, operation::run);
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals("Ledger is not initialized", failure.getCause().getMessage());
        }
        assertThrows(
                IllegalStateException.class,
                () ->
                        uninitialized.setAllowance(
                                actor(alice), settlement, governor, 1, UUID.randomUUID()));
        assertEquals(0, balance(wallet(alice)));
    }
}
