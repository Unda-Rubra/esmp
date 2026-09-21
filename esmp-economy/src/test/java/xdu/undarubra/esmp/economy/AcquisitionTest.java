package xdu.undarubra.esmp.economy;

import static org.junit.jupiter.api.Assertions.*;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.acquisition.*;
import xdu.undarubra.esmp.civilization.api.*;
import xdu.undarubra.esmp.civilization.certification.*;
import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.*;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.math.BigInteger;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletionException;

class AcquisitionTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    SettlementService settlements;
    LedgerService ledger;
    LandValueService land;
    AcquisitionService acquisitions;
    UUID source = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            buyer = UUID.randomUUID(),
            admin = UUID.randomUUID(),
            outsider = UUID.randomUUID(),
            world,
            settlement,
            invitation;
    long raw;
    boolean failAfterDebit;

    void open() {
        db = new WorldStore(directory.resolve("acquisition.db"));
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
        settlements =
                SettlementServices.settlement(
                        db,
                        SettlementPolicy.defaults(),
                        clock,
                        admin::equals,
                        (tx, id) -> ledger.requireEmptyTreasury(tx, id),
                        ClaimPricingPolicy.defaults(),
                        (tx, q) ->
                                ledger.post(
                                        tx,
                                        new LedgerService.Request(
                                                q.id(),
                                                actor(q.actor()),
                                                LedgerService.Movement.SINK,
                                                q.payer() == ClaimQuote.Payer.WALLET
                                                        ? LedgerAccount.wallet(q.payerId())
                                                        : LedgerAccount.treasury(q.payerId()),
                                                null,
                                                q.amount(),
                                                q.founding()
                                                        ? "SETTLEMENT_FOUNDING"
                                                        : "CLAIM_PURCHASE",
                                                "")),
                        (tx, id) -> true);
        settlements.initialize().join();
        land = ObservationServices.land(db, LandValuePolicy.defaults());
        land.initialize().join();
        ledger =
                xdu.undarubra.esmp.economy.api.EconomyServices.ledger(
                        db,
                        clock,
                        CurrencyDefinition.defaults(),
                        new LedgerPolicy(168000, 20000),
                        (tx, who, id) -> {
                            var p = settlements.treasuryPermission(tx, who, id);
                            return new LedgerService.TreasuryAuthority(
                                    p.role() == Role.OWNER,
                                    p.role() == Role.GOVERNOR,
                                    p.ownershipRevision(),
                                    p.promotionId(),
                                    p.historical());
                        });
        ledger.initialize().join();
        acquisitions = acquisitions(AcquisitionPolicy.defaults());
        db.finishMigration().join();
    }

    AcquisitionService acquisitions(AcquisitionPolicy policy) {
        return SettlementServices.acquisition(
                db,
                clock,
                policy,
                admin::equals,
                (tx, q) -> {
                    ledger.post(
                            tx,
                            new LedgerService.Request(
                                    q.id(),
                                    actor(q.recipient()),
                                    LedgerService.Movement.SINK,
                                    LedgerAccount.wallet(q.recipient()),
                                    null,
                                    q.fee(),
                                    "SETTLEMENT_ACQUISITION_FEE",
                                    ""));
                    if (failAfterDebit) throw new SQLException("Injected post-debit failure");
                });
    }

    @BeforeEach
    void setup() {
        open();
        grant(LedgerAccount.wallet(owner), 1000);
        capture(0);
        var q = settlements.quoteCreate(owner, world, new ChunkPosition(0, 0)).join();
        settlements.purchase(owner, q.id(), java.util.List.of()).join();
        settlement = q.settlementId();
        var offer =
                settlements
                        .offer(owner, settlement, buyer, SettlementOffer.Type.INVITE)
                        .join()
                        .offer();
        invitation = offer.id();
        settlements.accept(buyer, offer.id(), false).join();
    }

    @AfterEach
    void close() {
        db.close();
    }

    LedgerService.Actor actor(UUID player) {
        return new LedgerService.Actor(player, false);
    }

    void grant(LedgerAccount target, long value) {
        ledger.post(
                        new LedgerService.Request(
                                UUID.randomUUID(),
                                new LedgerService.Actor(null, true),
                                LedgerService.Movement.ISSUANCE,
                                null,
                                target,
                                value,
                                "ADMIN_GRANT",
                                ""))
                .join();
    }

    long balance(UUID player) {
        return ledger.balance(actor(player), LedgerAccount.wallet(player)).join();
    }

    long treasury() {
        return ledger.balance(
                        new LedgerService.Actor(null, true), LedgerAccount.treasury(settlement))
                .join();
    }

    void capture(int x) {
        land.capture(
                        List.of(
                                new LandValueProfile(
                                        world, x, 0, 1, 100, LandValueProfile.Terrain.FLAT)))
                .join();
    }

    SettlementView current() {
        return settlements.initialize().join().stream()
                .filter(s -> s.id().equals(settlement))
                .findFirst()
                .orElseThrow();
    }

    Acquisition proposal() {
        acquisitions.propose(owner, settlement, buyer).join();
        return acquisitions.history(settlement).join().stream()
                .filter(q -> q.state() == Acquisition.State.PROPOSED)
                .findFirst()
                .orElseThrow();
    }

    void rejected(Runnable work) {
        assertThrows(CompletionException.class, work::run);
    }

    void advance(long ticks) {
        raw += ticks;
        clock.sample(source, raw, true);
    }

    @Test
    void acceptanceBindsFeeButOnlyAdminApprovalTransfersAndTreasuryRemains() {
        grant(LedgerAccount.wallet(buyer), 10);
        grant(LedgerAccount.treasury(settlement), 123);
        var certification =
                SettlementServices.certification(
                        db, clock, new CertificationPolicy(0, 0), admin::equals, (tx, id) -> true);
        certification.apply(owner, settlement, "Existing Name").join();
        var app = certification.view(settlement).join().pendingApplication();
        certification.decide(admin, settlement, app.id(), true, "fixture").join();
        var q = proposal();
        assertEquals(10, q.fee());
        assertEquals(BigInteger.valueOf(100), q.baseTotal());
        rejected(() -> acquisitions.review(admin, q.id(), true, "").join());
        rejected(() -> acquisitions.accept(buyer, q.id(), 9).join());
        rejected(() -> acquisitions.accept(outsider, q.id(), 10).join());
        acquisitions.accept(buyer, q.id(), 10).join();
        assertEquals(owner, current().owner());
        assertEquals(10, balance(buyer));
        rejected(() -> acquisitions.review(owner, q.id(), true, "").join());
        acquisitions.review(admin, q.id(), true, "approved").join();
        assertEquals(buyer, current().owner());
        assertEquals(Role.CITIZEN, current().role(owner));
        assertFalse(current().members().containsKey(buyer));
        assertEquals(0, balance(buyer));
        assertEquals(123, treasury());
        assertEquals("Existing Name", current().name());
        assertEquals(
                "ACCEPTED",
                db.transact(
                                tx ->
                                        DatabaseAccess.context(tx)
                                                .select(SETTLEMENT_OFFERS.STATE)
                                                .from(SETTLEMENT_OFFERS)
                                                .where(SETTLEMENT_OFFERS.OFFER_ID.eq(invitation))
                                                .fetchSingle(SETTLEMENT_OFFERS.STATE))
                        .join());
        assertEquals(CertificationStatus.VERIFIED, certification.view(settlement).join().status());
    }

    @Test
    void insufficientFundsAndPostDebitFailureRollbackAllOwnershipEffects() {
        var q = proposal();
        acquisitions.accept(buyer, q.id(), q.fee()).join();
        rejected(() -> acquisitions.review(admin, q.id(), true, "approved").join());
        assertEquals(owner, current().owner());
        assertEquals(Acquisition.State.ACCEPTED, acquisitions.view(q.id()).join().state());
        grant(LedgerAccount.wallet(buyer), 10);
        failAfterDebit = true;
        rejected(() -> acquisitions.review(admin, q.id(), true, "approved").join());
        assertEquals(10, balance(buyer));
        assertEquals(owner, current().owner());
        assertEquals(Role.CITIZEN, current().role(buyer));
        assertTrue(
                ledger.history(actor(buyer), LedgerAccount.wallet(buyer)).join().stream()
                        .noneMatch(r -> r.reason().equals("SETTLEMENT_ACQUISITION_FEE")));
        failAfterDebit = false;
        acquisitions.review(admin, q.id(), true, "approved").join();
        assertEquals(0, balance(buyer));
        assertEquals(buyer, current().owner());
    }

    @Test
    void committedApprovalReplaysAcrossRestartWithoutMovingMoneyOrMembershipAgain() {
        grant(LedgerAccount.wallet(buyer), 20);
        var q = proposal();
        acquisitions.accept(buyer, q.id(), q.fee()).join();
        acquisitions.review(admin, q.id(), true, "approved").join();
        long revision = current().ownershipRevision();
        db.close();
        open();
        acquisitions.review(admin, q.id(), true, "approved").join();
        acquisitions.accept(buyer, q.id(), q.fee()).join();
        assertEquals(10, balance(buyer));
        assertEquals(revision, current().ownershipRevision());
        assertEquals(buyer, current().owner());
        assertEquals(Role.CITIZEN, current().role(owner));
        assertEquals(
                1,
                settlements.history(settlement).join().stream()
                        .filter(e -> e.type().equals("OWNERSHIP_TRANSFERRED"))
                        .count());
    }

    @Test
    void changedClaimSetInvalidatesRecipientConsentEvenWhenOwnerStillMatches() {
        grant(LedgerAccount.wallet(buyer), 100);
        var q = proposal();
        acquisitions.accept(buyer, q.id(), q.fee()).join();
        capture(1);
        grant(LedgerAccount.treasury(settlement), 50);
        var claim =
                settlements.quoteClaim(owner, settlement, world, new ChunkPosition(1, 0)).join();
        settlements.purchase(owner, claim.id(), java.util.List.of()).join();
        rejected(() -> acquisitions.review(admin, q.id(), true, "changed").join());
        assertEquals(owner, current().owner());
        assertEquals(100, balance(buyer));
        acquisitions.cancel(owner, q.id()).join();
        var next = proposal();
        assertEquals(20, next.fee());
        assertEquals(BigInteger.valueOf(200), next.baseTotal());
    }

    @Test
    void missingProfilesAndProfileOrPolicyChangesNeverInventAnAcquisitionPrice() {
        grant(LedgerAccount.wallet(buyer), 100);
        var q = proposal();
        acquisitions.accept(buyer, q.id(), q.fee()).join();
        db.transact(
                        tx -> {
                            DatabaseAccess.context(tx)
                                    .update(LAND_VALUE_PROFILES)
                                    .set(LAND_VALUE_PROFILES.BASE_VALUE, 101L)
                                    .where(LAND_VALUE_PROFILES.WORLD_INSTANCE_ID.eq(world))
                                    .execute();
                            return null;
                        })
                .join();
        rejected(() -> acquisitions.review(admin, q.id(), true, "").join());
        assertEquals(100, balance(buyer));
        acquisitions.cancel(owner, q.id()).join();
        var changed = proposal();
        acquisitions.accept(buyer, changed.id(), changed.fee()).join();
        var newPolicy = acquisitions(new AcquisitionPolicy(36000, 2000));
        rejected(() -> newPolicy.review(admin, changed.id(), true, "").join());
        acquisitions.cancel(owner, changed.id()).join();
        db.transact(
                        tx -> {
                            DatabaseAccess.context(tx)
                                    .deleteFrom(LAND_VALUE_PROFILES)
                                    .where(LAND_VALUE_PROFILES.WORLD_INSTANCE_ID.eq(world))
                                    .execute();
                            return null;
                        })
                .join();
        rejected(() -> acquisitions.propose(owner, settlement, buyer).join());
        assertEquals(owner, current().owner());
    }

    @Test
    void expiryAndRecipientBanBlockApprovalWhileDenialPreservesEverything() {
        grant(LedgerAccount.wallet(buyer), 10);
        var q = proposal();
        acquisitions.accept(buyer, q.id(), q.fee()).join();
        settlements.ban(owner, settlement, buyer, true).join();
        rejected(() -> acquisitions.review(admin, q.id(), true, "").join());
        acquisitions.review(admin, q.id(), false, "banned").join();
        assertEquals(owner, current().owner());
        assertEquals(10, balance(buyer));
        settlements.ban(owner, settlement, buyer, false).join();
        var expired = proposal();
        acquisitions.accept(buyer, expired.id(), expired.fee()).join();
        advance(36000);
        rejected(() -> acquisitions.review(admin, expired.id(), true, "").join());
        assertEquals(owner, current().owner());
        assertEquals(10, balance(buyer));
    }

    @Test
    void feeRoundingUsesWideIntegerSumWithoutIntermediateOverflow() {
        var p = AcquisitionPolicy.defaults();
        assertEquals(11, p.fee(BigInteger.valueOf(101)));
        assertEquals(1, p.fee(BigInteger.ONE));
        assertEquals(
                Long.MAX_VALUE, p.fee(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN)));
        assertThrows(
                SettlementException.class,
                () -> p.fee(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(11))));
    }
}
