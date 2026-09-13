package xdu.undarubra.esmp.civilization.certification;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.api.*;
import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionException;

class CertificationServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    SettlementService settlements;
    ParcelService parcels;
    ActivityService activity;
    CertificationService certification;
    LandValueService land;
    UUID source = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            admin = UUID.randomUUID(),
            publicPlayer = UUID.randomUUID(),
            world,
            id;
    List<UUID> team;
    long raw;
    int actionIndex;

    void open() {
        db = new WorldStore(directory.resolve("certification.db"));
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
        activity =
                ObservationServices.activity(
                        db,
                        clock,
                        ActivityPolicy.defaults(),
                        xdu.undarubra.esmp.civilization.activity.LocalActivityPolicy.defaults(),
                        xdu.undarubra.esmp.civilization.decay.DecayService.Policy.defaults(),
                        admin::equals);
        settlements =
                SettlementServices.settlement(
                        db,
                        SettlementPolicy.defaults(),
                        clock,
                        admin::equals,
                        (tx, s) -> {},
                        ClaimPricingPolicy.defaults(),
                        (tx, q) -> {},
                        activity::isActive);
        settlements.initialize().join();
        activity.initialize().join();
        parcels = SettlementServices.parcel(db, clock, admin::equals);
        land = ObservationServices.land(db, LandValuePolicy.defaults());
        land.initialize().join();
        certification =
                SettlementServices.certification(
                        db,
                        clock,
                        CertificationPolicy.defaults(),
                        admin::equals,
                        activity::isActive);
        db.finishMigration().join();
    }

    @BeforeEach
    void setup() {
        open();
        id = create(0);
        team = List.of(owner, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (UUID member : team.subList(1, 4)) {
            var invite =
                    settlements
                            .offer(owner, id, member, SettlementOffer.Type.INVITE)
                            .join()
                            .offer();
            settlements.accept(member, invite.id(), false).join();
        }
    }

    @AfterEach
    void close() {
        db.close();
    }

    UUID create(int x) {
        land.capture(
                        List.of(
                                new LandValueProfile(
                                        world, x, 0, 1, 100, LandValueProfile.Terrain.FLAT)))
                .join();
        var q = settlements.quoteCreate(owner, world, new ChunkPosition(x, 0)).join();
        return settlements.purchase(owner, q.id()).join().settlementId();
    }

    SettlementView current() {
        return settlements.initialize().join().stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    void advance(long ticks) {
        raw += ticks;
        clock.sample(source, raw, true);
    }

    ActivityContribution action(UUID actor) {
        var s = current();
        return new ActivityContribution(
                id,
                world,
                actor,
                0,
                0,
                0,
                s.ownershipRevision(),
                s.chunkRevision(),
                parcels.land(id).join().securityRevision(),
                ParcelAction.BUILD,
                true);
    }

    void credit(int count) {
        for (int i = 0; i < count; i++) {
            advance(201);
            activity.contribute(List.of(action(team.get(actionIndex++ % 4)))).join();
        }
    }

    void eligible() {
        credit(20);
        if (raw < 72000) advance(72000 - raw);
        assertTrue(certification.view(id).join().eligible());
    }

    UUID apply(String name) {
        certification.apply(owner, id, name).join();
        return certification.view(id).join().pendingApplication().id();
    }

    void rejected(Runnable work) {
        assertThrows(CompletionException.class, work::run);
    }

    @Test
    void defaultGateRequiresAgeCurrentActivityAndActualLifetimeCredit() {
        assertEquals(CertificationStatus.UNVERIFIED, certification.view(id).join().status());
        rejected(() -> certification.apply(owner, id, "Early").join());
        credit(19);
        assertEquals(19, certification.view(id).join().lifetimeCredit());
        advance(72000 - raw);
        rejected(() -> certification.apply(owner, id, "Still Early").join());
        credit(1);
        assertTrue(certification.view(id).join().eligible());
        UUID app = apply("  New  Haven  ");
        assertEquals(CertificationStatus.UNVERIFIED, certification.view(id).join().status());
        rejected(() -> certification.decide(owner, id, app, true, "ok").join());
        certification.decide(admin, id, app, true, "ok").join();
        assertEquals(CertificationStatus.VERIFIED, parcels.land(id).join().certification());
        assertEquals("New  Haven", parcels.land(id).join().officialName());
        assertTrue(
                certification.verifiedSettlements().join().stream()
                        .anyMatch(s -> s.id().equals(id) && s.officialName().equals("New  Haven")));
    }

    @Test
    void onlyAcceptedCreditAccumulatesAndLifetimeNeverDecaysOrCatchesUp() {
        advance(201);
        var event = action(owner);
        activity.contribute(List.of(event, event, action(publicPlayer))).join();
        assertEquals(1, certification.view(id).join().lifetimeCredit());
        clock.sample(source, raw, false);
        raw += 240000;
        clock.sample(source, raw, false);
        activity.contribute(List.of(event)).join();
        assertEquals(1, certification.view(id).join().lifetimeCredit());
        clock.sample(source, raw, true);
        advance(240000);
        assertEquals(1, certification.view(id).join().lifetimeCredit());
        assertFalse(certification.view(id).join().active());
        db.close();
        open();
        assertEquals(1, certification.view(id).join().lifetimeCredit());
    }

    @Test
    void denialPreservesOwnershipClaimsAndCreditAndAllowsAnotherApplication() {
        eligible();
        var before = current();
        UUID app = apply(null);
        certification.decide(admin, id, app, false, "Needs review").join();
        assertEquals(before, current());
        assertEquals(20, certification.view(id).join().lifetimeCredit());
        assertEquals(CertificationStatus.UNVERIFIED, certification.view(id).join().status());
        var decision = certification.applications(id).join().getFirst();
        assertEquals(CertificationApplication.State.DENIED, decision.state());
        assertEquals(admin, decision.decidedBy());
        assertEquals("Needs review", decision.decisionNote());
        assertNotEquals(app, apply("Second request"));
    }

    @Test
    void repeatedApprovalDoesNotOverwriteConcurrentRenameOrDuplicateHistory() {
        eligible();
        UUID app = apply("Original Name");
        certification.apply(owner, id, "Original Name").join();
        assertEquals(1, certification.applications(id).join().size());
        rejected(() -> settlements.rename(admin, id, "Before Approval").join());
        var approval = certification.decide(admin, id, app, true, "approved");
        var rename = settlements.rename(admin, id, "New Name");
        approval.join();
        rename.join();
        certification.decide(admin, id, app, true, "approved").join();
        assertEquals("New Name", current().name());
        assertEquals(
                1,
                settlements.history(id).join().stream()
                        .filter(e -> e.type().equals("CERTIFICATION_APPROVED"))
                        .count());
        db.close();
        open();
        certification.decide(admin, id, app, true, "approved").join();
        assertEquals("New Name", current().name());
        advance(40 * 24000L);
        assertEquals(CertificationStatus.VERIFIED, certification.view(id).join().status());
        assertFalse(certification.view(id).join().active());
    }

    @Test
    void reviewerRechecksEligibilityAndApplicantGovernance() {
        eligible();
        UUID app = apply(null);
        advance(30 * 24000L);
        rejected(() -> certification.decide(admin, id, app, true, "late").join());
        assertEquals(
                CertificationApplication.State.PENDING,
                certification.view(id).join().pendingApplication().state());
        certification.decide(admin, id, app, false, "inactive").join();
        rejected(() -> certification.apply(publicPlayer, id, null).join());
    }

    @Test
    void applicantDemotionInvalidatesApprovalButAdminCanDenyStaleApplication() {
        eligible();
        UUID governor = team.get(1);
        var offer =
                settlements.offer(owner, id, governor, SettlementOffer.Type.PROMOTE).join().offer();
        settlements.accept(governor, offer.id(), false).join();
        certification.apply(governor, id, null).join();
        UUID app = certification.view(id).join().pendingApplication().id();
        settlements.demote(owner, id, governor).join();
        rejected(() -> certification.decide(admin, id, app, true, "stale").join());
        assertEquals(CertificationStatus.UNVERIFIED, certification.view(id).join().status());
        certification.decide(admin, id, app, false, "applicant demoted").join();
        assertEquals(
                CertificationApplication.State.DENIED,
                certification.applications(id).join().getFirst().state());
    }

    @Test
    void competingOfficialNameCannotPartiallyVerifyApplicant() {
        eligible();
        UUID app = apply("Shared Name");
        UUID second = create(2);
        var fixturePolicy =
                SettlementServices.certification(
                        db,
                        clock,
                        new CertificationPolicy(0, 0),
                        admin::equals,
                        activity::isActive);
        fixturePolicy.apply(owner, second, "Shared Name").join();
        UUID other = fixturePolicy.view(second).join().pendingApplication().id();
        fixturePolicy.decide(admin, second, other, true, "ok").join();
        rejected(() -> certification.decide(admin, id, app, true, "ok").join());
        assertEquals(CertificationStatus.UNVERIFIED, certification.view(id).join().status());
        assertNull(certification.view(id).join().officialName());
        assertEquals(app, certification.view(id).join().pendingApplication().id());
    }
}
