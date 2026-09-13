package xdu.undarubra.esmp.civilization.settlement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.certification.CertificationPolicy;
import xdu.undarubra.esmp.civilization.certification.CertificationService;
import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionException;

class ParcelServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    LandValueService landValues;
    SettlementService settlements;
    ParcelService parcels;
    UUID source = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            holder = UUID.randomUUID(),
            other = UUID.randomUUID(),
            admin = UUID.randomUUID(),
            world,
            id;
    Set<UUID> admins = new HashSet<>();
    Map<UUID, UUID> creationReceipts = new HashMap<>();

    @BeforeEach
    void open() {
        db = new WorldStore(directory.resolve("parcels.db"));
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
        landValues =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        db, LandValuePolicy.defaults());
        landValues.initialize().join();
        admins.add(admin);
        settlements = governanceSettlements();
        settlements.initialize().join();
        parcels =
                xdu.undarubra.esmp.civilization.api.SettlementServices.parcel(
                        db, clock, admins::contains);
        id = create(owner, world, new ChunkPosition(0, 0)).settlementId();
    }

    void restartForLegacyMigration() {
        // Reopen so DatabaseSchema detects the legacy fixture without Liquibase metadata.
        // Unlike open(), do not initialize settlements or create another settlement/receipt.
        db.close();
        db = new WorldStore(directory.resolve("parcels.db"));
        var reopenedWorld =
                db.initialize(
                                List.of(
                                        new WorldDefinition(
                                                "MAIN", "civilization", WorldEnvironment.NORMAL)),
                                List.of(new WorldBinding("MAIN", source, "world")))
                        .join()
                        .instances()
                        .getFirst()
                        .instanceId();
        assertEquals(world, reopenedWorld);
        clock = new GameClock(db);
        clock.initialize(source, 0, true).join();
        landValues =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        db, LandValuePolicy.defaults());
        landValues.initialize().join();
        settlements = governanceSettlements();
        parcels =
                xdu.undarubra.esmp.civilization.api.SettlementServices.parcel(
                        db, clock, admins::contains);
        // Leave migration unfinished: the test still has to initialize the domain tables.
    }

    SettlementService governanceSettlements() {
        // These tests exercise parcel governance, not currency or activity pricing.
        // Natural profiles and the production quote/purchase path are still required.
        return xdu.undarubra.esmp.civilization.api.SettlementServices.settlement(
                db,
                SettlementPolicy.defaults(),
                clock,
                admins::contains,
                (c, settlement) -> {},
                ClaimPricingPolicy.defaults(),
                (c, quote) -> {},
                (c, settlement) -> true);
    }

    void verify(UUID id) {
        // Test-only zero thresholds isolate naming and geometry from certification eligibility.
        CertificationService service =
                xdu.undarubra.esmp.civilization.api.SettlementServices.certification(
                        db,
                        clock,
                        new CertificationPolicy(0, 0),
                        admins::contains,
                        (tx, settlement) -> true);
        service.apply(owner, id, null).join();
        UUID appId = service.view(id).join().pendingApplication().id();
        service.decide(admin, id, appId, true, "fixture").join();
    }

    void profile(UUID worldInstance, ChunkPosition chunk) {
        // Identical immutable valuation inputs at each distinct requested location.
        // LandValueService.capture also verifies repeated captures are identical.
        landValues
                .capture(
                        List.of(
                                new LandValueProfile(
                                        worldInstance,
                                        chunk.x(),
                                        chunk.z(),
                                        landValues.policy().version(),
                                        landValues.policy().baseValue(),
                                        LandValueProfile.Terrain.values()[0])))
                .join();
    }

    SettlementChange create(UUID actor, UUID worldInstance, ChunkPosition chunk) {
        profile(worldInstance, chunk);
        var quote = settlements.quoteCreate(actor, worldInstance, chunk).join();
        var change = settlements.purchase(actor, quote.id()).join();
        creationReceipts.put(change.settlementId(), quote.id());
        return change;
    }

    SettlementChange claim(UUID actor, UUID settlement, UUID worldInstance, ChunkPosition chunk) {
        profile(worldInstance, chunk);
        var quote = settlements.quoteClaim(actor, settlement, worldInstance, chunk).join();
        return settlements.purchase(actor, quote.id()).join();
    }

    @AfterEach
    void close() {
        db.close();
    }

    ParcelView current() {
        return parcels.land(id).join().parcels().values().iterator().next();
    }

    void create() {
        parcels.add(owner, id, "Home", Set.of(new ParcelCell(0, 0, 0)), -64, 320).join();
    }

    UUID request() {
        return parcels.requests(id).join().getFirst().id();
    }

    static void rejected(Runnable op) {
        var error = assertThrows(CompletionException.class, op::run);
        assertInstanceOf(SettlementException.class, error.getCause());
    }

    void assign(Set<UUID> holders) {
        parcels.holders(owner, id, current().id(), holders).join();
        UUID request = request();
        for (var h : holders) parcels.resolve(h, id, request, true).join();
    }

    @Test
    void spacedNamesTrimEdgesAndSurviveApprovalWithUniquenessIntact() {
        verify(id);
        settlements.rename(admin, id, "  New  Haven  ").join();
        assertEquals("New  Haven", parcels.land(id).join().officialName());
        UUID second = create(owner, world, new ChunkPosition(2, 0)).settlementId();
        verify(second);
        rejected(() -> settlements.rename(admin, second, "new  haven").join());
        parcels.add(owner, id, "  Tool  Store  ", Set.of(new ParcelCell(0, 0, 0)), -64, 320).join();
        assertEquals("Tool  Store", current().name());
        assign(Set.of(holder));
        parcels.rename(owner, id, current().id(), "  Public  Workshop  ").join();
        parcels.resolve(holder, id, request(), true).join();
        assertEquals("Public  Workshop", current().name());
        parcels.add(owner, id, "public  workshop", Set.of(new ParcelCell(1, 0, 0)), -64, 320)
                .join();
        parcels.resolve(holder, id, request(), true).join();
        assertEquals(1, parcels.land(id).join().parcels().size());
        assertEquals(2, current().cells().size());
        assertThrows(SettlementException.class, () -> settlements.rename(admin, id, "   "));
        assertThrows(
                SettlementException.class,
                () -> parcels.rename(owner, id, current().id(), "Bad\\nName"));
    }

    @Test
    void geometryRejectsDiagonalOverlapBoundsAndParentRelease() {
        create();
        UUID parcel = current().id();
        parcels.add(owner, id, "Home", Set.of(new ParcelCell(1, 0, 0)), -64, 320).join();
        rejected(
                () ->
                        parcels.add(owner, id, "Home", Set.of(new ParcelCell(1, 1, 1)), -64, 320)
                                .join());
        rejected(
                () ->
                        parcels.add(owner, id, "Other", Set.of(new ParcelCell(0, 0, 0)), -64, 320)
                                .join());
        rejected(
                () ->
                        parcels.add(owner, id, "Home", Set.of(new ParcelCell(0, -9, 0)), -64, 320)
                                .join());
        assertEquals(2, current().cells().size());
        claim(owner, id, world, new ChunkPosition(1, 0));
        rejected(() -> settlements.unclaim(owner, id, new ChunkPosition(0, 0)).join());
        var warning =
                settlements.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        rejected(() -> settlements.accept(owner, warning.id(), true).join());
        parcels.removeCell(owner, id, parcel, new ParcelCell(1, 0, 0)).join();
        assertEquals(Set.of(new ParcelCell(0, 0, 0)), current().cells());
        assertEquals(new ParcelCell(-1, -1, -1), ParcelCell.atBlock(-1, -1, -1));
        assertEquals(new ChunkPosition(-1, -1), new ParcelCell(-1, -1, -1).chunk());
    }

    @Test
    void allHoldersAndGovernanceMustConsentAndApprovalsSurviveRestart() {
        create();
        assign(Set.of(holder, other));
        UUID parcel = current().id();
        assertEquals(Role.PUBLIC, settlements.initialize().join().getFirst().role(holder));
        parcels.delete(holder, id, parcel).join();
        UUID request = request();
        parcels.resolve(owner, id, request, true).join();
        assertEquals(1, parcels.land(id).join().parcels().size());
        db.close();
        db = new WorldStore(directory.resolve("parcels.db"));
        db.initialize(
                        List.of(
                                new WorldDefinition(
                                        "MAIN", "civilization", WorldEnvironment.NORMAL)),
                        List.of())
                .join();
        clock = new GameClock(db);
        clock.initialize(source, 0, true).join();
        landValues =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        db, LandValuePolicy.defaults());
        landValues.initialize().join();
        settlements = governanceSettlements();
        settlements.initialize().join();
        parcels =
                xdu.undarubra.esmp.civilization.api.SettlementServices.parcel(
                        db, clock, admins::contains);
        assertTrue(settlements.quote(owner, creationReceipts.get(id)).join().purchased());
        assertTrue(parcels.requests(id).join().getFirst().approvals().contains(owner));
        parcels.resolve(other, id, request, true).join();
        assertTrue(parcels.land(id).join().parcels().isEmpty());
        rejected(() -> parcels.resolve(other, id, request, true).join());
        assertTrue(
                settlements.history(id).join().stream()
                        .anyMatch(e -> e.type().equals("PARCEL_DELETE")));
    }

    @Test
    void relationUnionBanAndActivityUseCurrentParcelPolicy() {
        create();
        assign(Set.of(holder));
        UUID parcel = current().id();
        parcels.policy(owner, id, parcel, ParcelAction.BUILD, Set.of(ParcelRelation.HOLDER)).join();
        parcels.resolve(holder, id, request(), true).join();
        var p = current();
        assertTrue(p.allows(holder, Role.PUBLIC, ParcelAction.BUILD));
        assertTrue(p.allows(holder, Role.CITIZEN, ParcelAction.BUILD));
        assertFalse(p.allows(owner, Role.OWNER, ParcelAction.BUILD));
        var activity =
                xdu.undarubra.esmp.civilization.api.ObservationServices.activity(
                        db, clock, ActivityPolicy.defaults(), admins::contains);
        activity.initialize().join();
        long revision = parcels.land(id).join().securityRevision();
        activity.contribute(
                        List.of(
                                new ActivityContribution(
                                        id,
                                        world,
                                        holder,
                                        0,
                                        0,
                                        0,
                                        1,
                                        1,
                                        revision,
                                        ParcelAction.BUILD,
                                        false)))
                .join();
        assertEquals(.02, activity.status(id).join().creditedToday(), 1e-9);
        settlements.ban(owner, id, holder, true).join();
        rejected(() -> parcels.delete(holder, id, parcel).join());
        clock.sample(source, 200, true);
        long bannedRevision = parcels.land(id).join().securityRevision();
        activity.contribute(
                        List.of(
                                new ActivityContribution(
                                        id,
                                        world,
                                        holder,
                                        0,
                                        0,
                                        0,
                                        1,
                                        1,
                                        bannedRevision,
                                        ParcelAction.BUILD,
                                        false)))
                .join();
        assertEquals(.02, activity.status(id).join().creditedToday(), 1e-9);
        rejected(() -> settlements.ban(owner, id, admin, true).join());
        parcels.delete(admin, id, parcel).join();
        assertTrue(parcels.land(id).join().parcels().isEmpty());
    }

    @Test
    void staleAndExpiredConsentCannotApply() {
        create();
        assign(Set.of(holder));
        UUID parcel = current().id();
        parcels.delete(owner, id, parcel).join();
        UUID stale = request();
        claim(owner, id, world, new ChunkPosition(1, 0));
        rejected(() -> parcels.resolve(holder, id, stale, true).join());
        parcels.resolve(owner, id, stale, false).join();
        assertTrue(parcels.requests(id).join().isEmpty());
        parcels.delete(owner, id, parcel).join();
        UUID expired =
                parcels.requests(id).join().stream()
                        .filter(r -> !r.id().equals(stale))
                        .findFirst()
                        .orElseThrow()
                        .id();
        clock.sample(source, 24000, true);
        rejected(() -> parcels.resolve(holder, id, expired, true).join());
        assertEquals(1, parcels.land(id).join().parcels().size());
    }

    @Test
    void pendingRequestsRetainInsertionOrderAndDuplicateApprovalsAreHarmless() {
        create();
        assign(Set.of(holder, other));
        UUID parcel = current().id();
        parcels.rename(owner, id, parcel, "First proposal").join();
        UUID first = request();
        parcels.rename(owner, id, parcel, "Second proposal").join();
        var pending = parcels.requests(id).join();
        assertEquals(2, pending.size());
        UUID second = pending.get(1).id();
        assertNotEquals(first, second);
        assertEquals(
                List.of(first, second), pending.stream().map(ParcelService.Request::id).toList());
        parcels.resolve(owner, id, first, true).join();
        parcels.resolve(owner, id, first, true).join();
        assertEquals(Set.of(owner), parcels.requests(id).join().getFirst().approvals());
        assertEquals("Home", current().name());
        parcels.resolve(holder, id, first, true).join();
        assertEquals("Home", current().name());
        parcels.resolve(other, id, first, true).join();
        assertEquals("First proposal", current().name());
        assertTrue(parcels.requests(id).join().isEmpty());
        rejected(() -> parcels.resolve(holder, id, second, true).join());
    }

    @Test
    void purchasedClaimsPreserveNumberAndAdministratorNamingRequiresCertification() {
        var land = parcels.land(id).join();
        assertEquals(1, land.number());
        assertNull(land.officialName());
        var claimed = claim(owner, id, world, new ChunkPosition(1, 0));
        assertEquals(2, claimed.current().chunks().size());
        rejected(() -> settlements.rename(owner, id, "Home").join());
        rejected(() -> settlements.rename(admin, id, "Home").join());
        verify(id);
        settlements.rename(admin, id, "Home").join();
        assertEquals("Home", parcels.land(id).join().officialName());
        assertEquals(land.number(), parcels.land(id).join().number());
    }

    @Test
    void legacyRolesAndNamesMigrateWithoutDroppingMembersOffersOrClaimReceipts() {
        UUID receiptId = creationReceipts.get(id);
        var receipt = settlements.quote(owner, receiptId).join();
        assertTrue(receipt.purchased());
        var invitation =
                settlements.offer(owner, id, holder, SettlementOffer.Type.INVITE).join().offer();
        settlements.accept(holder, invitation.id(), false).join();
        var promotion =
                settlements.offer(owner, id, holder, SettlementOffer.Type.PROMOTE).join().offer();
        settlements.accept(holder, promotion.id(), false).join();
        var pending =
                settlements.offer(owner, id, other, SettlementOffer.Type.INVITE).join().offer();
        db.transact(
                        c -> {
                            // Deliberate legacy migration fixture: these historical tables cannot
                            // be represented by the current generated schema. Raw SQL stays here.
                            // Forward-phase tables did not exist in a genuine legacy database.
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("DROP TABLE settlement_acquisitions");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute(
                                            "DROP TABLE IF EXISTS"
                                                    + " settlement_certification_applications");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("DROP TABLE IF EXISTS settlement_activity_lifetime");
                            // A true legacy database has no Liquibase metadata; retaining it while
                            // downgrading domain tables would describe corruption, not migration.
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("DROP TABLE IF EXISTS DATABASECHANGELOG");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("PRAGMA user_version=1");
                            // Preserve claim_pricing migration state, progress and receipts: this
                            // exercises legacy governance migration, not a pre-pricing database.
                            for (var table :
                                    List.of(
                                            "parcel_approvals",
                                            "parcel_requests",
                                            "parcel_policy",
                                            "parcel_holders",
                                            "parcel_cells",
                                            "parcels",
                                            "settlement_bans",
                                            "settlement_details"))
                                xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                        .execute("DROP TABLE " + table);
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute(
                                            "ALTER TABLE settlement_members RENAME TO"
                                                    + " migrated_members");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute(
                                            "CREATE TABLE settlement_members(settlement_id TEXT NOT"
                                                + " NULL,player_uuid TEXT NOT NULL,role TEXT NOT"
                                                + " NULL CHECK(role IN"
                                                + " ('MANAGER','MEMBER')),PRIMARY"
                                                + " KEY(settlement_id,player_uuid))");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute(
                                            "INSERT INTO settlement_members SELECT"
                                                    + " settlement_id,player_uuid,CASE role WHEN"
                                                    + " 'GOVERNOR' THEN 'MANAGER' ELSE 'MEMBER' END"
                                                    + " FROM migrated_members");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("DROP TABLE migrated_members");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute(
                                            "UPDATE settlements SET"
                                                    + " name='Legacy',name_key='legacy'");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("UPDATE civilization_schema SET version=2");
                            return null;
                        })
                .join();
        restartForLegacyMigration();
        var restored = settlements.initialize().join().getFirst();
        // Baseline only Phase0, then apply Phase2 before querying the new land snapshot schema.
        db.finishMigration().join();
        assertEquals(id, restored.id());
        assertEquals(Role.GOVERNOR, restored.role(holder));
        assertEquals("Legacy", parcels.land(id).join().officialName());
        assertEquals(pending.id(), settlements.inspectOffer(pending.id()).join().id());
        assertEquals(receipt, settlements.quote(owner, receiptId).join());
        var history = settlements.history(id).join();
        var replay = settlements.purchase(owner, receiptId).join();
        assertEquals(id, replay.settlementId());
        assertEquals(restored.chunks(), replay.current().chunks());
        assertEquals(Role.GOVERNOR, replay.current().role(holder));
        assertEquals(history, settlements.history(id).join());
        settlements.accept(other, pending.id(), false).join();
        assertEquals(Role.CITIZEN, settlements.initialize().join().getFirst().role(other));
    }
}
