package xdu.undarubra.esmp.economy;

import static org.junit.jupiter.api.Assertions.*;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletionException;

class PaidClaimTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    SettlementService settlements;
    LedgerService ledger;
    LandValueService land;
    UUID source = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            governor = UUID.randomUUID(),
            other = UUID.randomUUID(),
            world;
    boolean failAfterDebit;
    boolean active = true;

    void open() {
        db = new WorldStore(directory.resolve("paid.db"));
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
        clock.initialize(source, 0, true).join();
        settlements =
                xdu.undarubra.esmp.civilization.api.SettlementServices.settlement(
                        db,
                        SettlementPolicy.defaults(),
                        clock,
                        id -> false,
                        (tx, treasuryId) -> ledger.requireEmptyTreasury(tx, treasuryId),
                        ClaimPricingPolicy.defaults(),
                        (c, q) -> {
                            ledger.post(
                                    c,
                                    new LedgerService.Request(
                                            q.id(),
                                            new LedgerService.Actor(q.actor(), false),
                                            LedgerService.Movement.SINK,
                                            q.payer() == ClaimQuote.Payer.WALLET
                                                    ? LedgerAccount.wallet(q.payerId())
                                                    : LedgerAccount.treasury(q.payerId()),
                                            null,
                                            q.amount(),
                                            q.founding() ? "SETTLEMENT_FOUNDING" : "CLAIM_PURCHASE",
                                            ""));
                            if (failAfterDebit)
                                throw new SQLException("Injected owning-operation failure");
                        },
                        (c, id) -> active);
        settlements.initialize().join();
        land =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        db, LandValuePolicy.defaults());
        land.initialize().join();
        ledger =
                xdu.undarubra.esmp.economy.api.EconomyServices.ledger(
                        db,
                        clock,
                        CurrencyDefinition.defaults(),
                        new LedgerPolicy(168000, 10000),
                        (c, actor, id) -> {
                            var p = settlements.treasuryPermission(c, actor, id);
                            return new LedgerService.TreasuryAuthority(
                                    p.role() == Role.OWNER,
                                    p.role() == Role.GOVERNOR,
                                    p.ownershipRevision(),
                                    p.promotionId());
                        });
        ledger.initialize().join();
    }

    @BeforeEach
    void setup() {
        open();
        grant(owner, 1000);
    }

    @AfterEach
    void close() {
        db.close();
    }

    void grant(UUID player, long value) {
        ledger.post(
                        new LedgerService.Request(
                                UUID.randomUUID(),
                                new LedgerService.Actor(null, true),
                                LedgerService.Movement.ISSUANCE,
                                null,
                                LedgerAccount.wallet(player),
                                value,
                                "ADMIN_GRANT",
                                ""))
                .join();
    }

    long balance(UUID player) {
        return ledger.balance(new LedgerService.Actor(player, false), LedgerAccount.wallet(player))
                .join();
    }

    void deposit(UUID settlement, long amount) {
        ledger.post(
                        new LedgerService.Request(
                                UUID.randomUUID(),
                                new LedgerService.Actor(owner, false),
                                LedgerService.Movement.TRANSFER,
                                LedgerAccount.wallet(owner),
                                LedgerAccount.treasury(settlement),
                                amount,
                                "SETTLEMENT_DONATION",
                                ""))
                .join();
    }

    long treasury(UUID settlement) {
        return ledger.balance(
                        new LedgerService.Actor(owner, false), LedgerAccount.treasury(settlement))
                .join();
    }

    void promote(UUID settlement) {
        var invite =
                settlements
                        .offer(owner, settlement, governor, SettlementOffer.Type.INVITE)
                        .join()
                        .offer();
        settlements.accept(governor, invite.id(), false).join();
        var offer =
                settlements
                        .offer(owner, settlement, governor, SettlementOffer.Type.PROMOTE)
                        .join()
                        .offer();
        settlements.accept(governor, offer.id(), false).join();
    }

    void capture(int x, int z) {
        land.capture(
                        List.of(
                                new LandValueProfile(
                                        world, x, z, 1, 100, LandValueProfile.Terrain.FLAT)))
                .join();
    }

    ClaimQuote founding(UUID actor, int x) {
        capture(x, 0);
        return settlements.quoteCreate(actor, world, new ChunkPosition(x, 0)).join();
    }

    ClaimQuote expansion(UUID actor, UUID s, int x) {
        capture(x, 0);
        return settlements.quoteClaim(actor, s, world, new ChunkPosition(x, 0)).join();
    }

    long used(UUID player) {
        return db.transact(
                        c ->
                                xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                        .select(PLAYER_LAND_PROGRESS.CLAIMS_USED)
                                        .from(PLAYER_LAND_PROGRESS)
                                        .where(PLAYER_LAND_PROGRESS.PLAYER_UUID.eq(player))
                                        .fetchOptional(PLAYER_LAND_PROGRESS.CLAIMS_USED)
                                        .orElse(0L))
                .join();
    }

    void rejected(Runnable action) {
        assertThrows(CompletionException.class, action::run);
    }

    UUID create() {
        var q = founding(owner, 0);
        settlements.purchase(owner, q.id()).join();
        return q.settlementId();
    }

    @Test
    void firstFoundingExpansionAndReplayCommitExactlyOnceAcrossRestart() {
        var q = founding(owner, 0);
        assertEquals(25, q.amount());
        assertEquals(1000, balance(owner));
        settlements.purchase(owner, q.id()).join();
        assertEquals(975, balance(owner));
        assertEquals(1, used(owner));
        deposit(q.settlementId(), 50);
        var e = expansion(owner, q.settlementId(), 1);
        assertEquals(50, e.amount());
        settlements.purchase(owner, e.id()).join();
        assertEquals(925, balance(owner));
        db.close();
        open();
        settlements.purchase(owner, q.id()).join();
        settlements.purchase(owner, e.id()).join();
        assertEquals(925, balance(owner));
        assertEquals(2, used(owner));
        assertEquals(0, treasury(q.settlementId()));
    }

    @Test
    void rollbackAndInsufficientFundsPreserveClaimAndSubsidyAndPermitRetry() {
        var q = founding(owner, 0);
        failAfterDebit = true;
        rejected(() -> settlements.purchase(owner, q.id()).join());
        assertEquals(1000, balance(owner));
        assertEquals(0, used(owner));
        assertFalse(settlements.quote(owner, q.id()).join().purchased());
        failAfterDebit = false;
        settlements.purchase(owner, q.id()).join();
        var poor = founding(other, 5);
        rejected(() -> settlements.purchase(other, poor.id()).join());
        assertEquals(0, used(other));
        grant(other, 25);
        settlements.purchase(other, poor.id()).join();
        assertEquals(0, balance(other));
        assertEquals(1, used(other));
    }

    @Test
    void missingLandExpiredQuotesAndActivityChangesDoNotDebit() {
        rejected(() -> settlements.quoteCreate(owner, world, new ChunkPosition(80, 0)).join());
        assertEquals(1000, balance(owner));
        var q = founding(owner, 0);
        clock.sample(source, 1200, true);
        rejected(() -> settlements.purchase(owner, q.id()).join());
        assertEquals(0, used(owner));
        UUID s = create();
        var expansion = expansion(owner, s, 1);
        active = false;
        rejected(() -> settlements.purchase(owner, expansion.id()).join());
        assertEquals(975, balance(owner));
    }

    @Test
    void governorsUseTreasuryAllowanceAndOwnersSubsidyNotTheirOwn() {
        UUID s = create();
        var invite =
                settlements.offer(owner, s, governor, SettlementOffer.Type.INVITE).join().offer();
        settlements.accept(governor, invite.id(), false).join();
        var promote =
                settlements.offer(owner, s, governor, SettlementOffer.Type.PROMOTE).join().offer();
        settlements.accept(governor, promote.id(), false).join();
        ledger.post(
                        new LedgerService.Request(
                                UUID.randomUUID(),
                                new LedgerService.Actor(owner, false),
                                LedgerService.Movement.TRANSFER,
                                LedgerAccount.wallet(owner),
                                LedgerAccount.treasury(s),
                                200,
                                "SETTLEMENT_DONATION",
                                ""))
                .join();
        var q = expansion(governor, s, 1);
        assertEquals(ClaimQuote.Payer.TREASURY, q.payer());
        assertEquals(owner, q.owner());
        assertEquals(50, q.amount());
        rejected(() -> settlements.purchase(governor, q.id()).join());
        assertEquals(1, used(owner));
        ledger.setAllowance(
                        new LedgerService.Actor(owner, false), s, governor, 50, UUID.randomUUID())
                .join();
        settlements.purchase(governor, q.id()).join();
        assertEquals(2, used(owner));
        assertEquals(0, used(governor));
        assertEquals(
                0, ledger.allowance(new LedgerService.Actor(governor, false), s, governor).join());
        assertEquals(775, balance(owner));
    }

    @Test
    void unclaimAndAbandonDoNotResetProgressAndSeparateFoundingPaysProximity() {
        UUID s = create();
        deposit(s, 50);
        var e = expansion(owner, s, 1);
        settlements.purchase(owner, e.id()).join();
        settlements.unclaim(owner, s, new ChunkPosition(1, 0)).join();
        var again = expansion(owner, s, 1);
        assertEquals(65, again.amount());
        var nearby = founding(owner, 2);
        assertEquals(130, nearby.amount());
        var warning =
                settlements.offer(owner, s, owner, SettlementOffer.Type.ABANDON).join().offer();
        settlements.accept(owner, warning.id(), true).join();
        var fresh = founding(owner, 0);
        assertEquals(65, fresh.amount());
        assertEquals(2, used(owner));
    }

    @Test
    void topologyOwnershipAndActorChangesCannotReuseQuote() {
        UUID s = create();
        var q = expansion(owner, s, 1);
        rejected(() -> settlements.purchase(other, q.id()).join());
        var invite = settlements.offer(owner, s, other, SettlementOffer.Type.INVITE).join().offer();
        settlements.accept(other, invite.id(), false).join();
        UUID reviewer = UUID.randomUUID();
        var acquisitions =
                xdu.undarubra.esmp.civilization.api.SettlementServices.acquisition(
                        db,
                        clock,
                        xdu.undarubra.esmp.civilization.acquisition.AcquisitionPolicy.defaults(),
                        reviewer::equals,
                        (tx, acquisition) ->
                                ledger.post(
                                        tx,
                                        new LedgerService.Request(
                                                acquisition.id(),
                                                new LedgerService.Actor(
                                                        acquisition.recipient(), false),
                                                LedgerService.Movement.SINK,
                                                LedgerAccount.wallet(acquisition.recipient()),
                                                null,
                                                acquisition.fee(),
                                                "SETTLEMENT_ACQUISITION_FEE",
                                                "")));
        acquisitions.propose(owner, s, other).join();
        var transfer = acquisitions.history(s).join().getFirst();
        grant(other, transfer.fee());
        acquisitions.accept(other, transfer.id(), transfer.fee()).join();
        acquisitions.review(reviewer, transfer.id(), true, "approved").join();
        rejected(() -> settlements.purchase(owner, q.id()).join());
        assertEquals(975, balance(owner));
        var incoming = expansion(other, s, 1);
        assertEquals(100, incoming.amount());
        assertEquals(0, used(other));
    }

    @Test
    void ownerExpansionRequiresTreasuryEvenWithEnoughPersonalMoney() {
        UUID s = create();
        var q = expansion(owner, s, 1);
        assertEquals(ClaimQuote.Payer.TREASURY, q.payer());
        assertEquals(s, q.payerId());
        rejected(() -> settlements.purchase(owner, q.id()).join());
        assertEquals(975, balance(owner));
        assertEquals(1, used(owner));
        assertFalse(settlements.quote(owner, q.id()).join().purchased());
        deposit(s, 50);
        settlements.purchase(owner, q.id()).join();
        assertEquals(925, balance(owner));
        assertEquals(0, treasury(s));
        assertEquals(2, used(owner));
    }

    @Test
    void governorQuoteRejectsOwnerPortfolioChangeWithoutSpendingAllowance() {
        UUID s = create();
        promote(s);
        deposit(s, 100);
        ledger.setAllowance(
                        new LedgerService.Actor(owner, false), s, governor, 100, UUID.randomUUID())
                .join();
        var q = expansion(governor, s, 1);
        var separate = founding(owner, 10);
        settlements.purchase(owner, separate.id()).join();
        rejected(() -> settlements.purchase(governor, q.id()).join());
        assertEquals(100, treasury(s));
        assertEquals(
                100,
                ledger.allowance(new LedgerService.Actor(governor, false), s, governor).join());
        assertEquals(0, used(governor));
    }

    @Test
    void claimThenUnclaimCannotReviveAnOldSameAreaQuote() {
        UUID s = create();
        promote(s);
        deposit(s, 100);
        ledger.setAllowance(
                        new LedgerService.Actor(owner, false), s, governor, 100, UUID.randomUUID())
                .join();
        var stale = expansion(governor, s, 1);
        var otherEdge = expansion(owner, s, -1);
        settlements.purchase(owner, otherEdge.id()).join();
        settlements.unclaim(owner, s, new ChunkPosition(-1, 0)).join();
        rejected(() -> settlements.purchase(governor, stale.id()).join());
        assertEquals(50, treasury(s));
        assertEquals(2, used(owner));
        assertEquals(65, expansion(owner, s, 1).amount());
    }

    @Test
    void oldUnpurchasedWalletQuoteRejectsButPaidReceiptStillReplaysAfterRestart() {
        var first = founding(owner, 0);
        settlements.purchase(owner, first.id()).join();
        deposit(first.settlementId(), 100);
        var pending = expansion(owner, first.settlementId(), 1);
        // Recreate the actual pre-Phase-1 unversioned context and Owner-wallet payer.
        db.transact(
                        tx -> {
                            var c = xdu.undarubra.esmp.core.storage.DatabaseAccess.context(tx);
                            for (var q : List.of(first, pending)) {
                                String legacy =
                                        q.context()
                                                .substring(q.context().indexOf('|') + 1)
                                                .replace(
                                                        "|TREASURY|" + first.settlementId(),
                                                        "|WALLET|" + owner);
                                c.update(CLAIM_QUOTES)
                                        .set(CLAIM_QUOTES.CONTEXT, legacy)
                                        .set(CLAIM_QUOTES.PAYER_KIND, "WALLET")
                                        .set(CLAIM_QUOTES.PAYER_UUID, owner)
                                        .where(CLAIM_QUOTES.QUOTE_ID.eq(q.id()))
                                        .execute();
                            }
                            return null;
                        })
                .join();
        db.close();
        open();
        settlements.purchase(owner, first.id()).join();
        rejected(() -> settlements.purchase(owner, pending.id()).join());
        assertEquals(875, balance(owner));
        assertEquals(100, treasury(first.settlementId()));
        assertEquals(1, used(owner));
    }

    @Test
    void pricingRoundsOnlyOnceAndUsesCheckedExactArithmetic() {
        var p = ClaimPricingPolicy.defaults();
        assertEquals(53, p.price(100, 1, 1, 1, false, false, 0, 1, true));
        assertEquals(1, p.price(1, 1, 1, 1, true, false, 0, 1, true));
        assertEquals(25, p.price(Long.MAX_VALUE, 0, 0, 0, true, true, Long.MAX_VALUE, 0, false));
        assertThrows(
                SettlementException.class,
                () -> p.price(Long.MAX_VALUE, 4, 128, 32, false, false, 0, 9, true));
    }
}
