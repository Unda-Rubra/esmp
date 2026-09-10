package xdu.undarubra.esmp.civilization.settlement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.activity.*;
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
        admins.add(admin);
        settlements =
                new SettlementService(db, SettlementPolicy.defaults(), clock, admins::contains);
        settlements.initialize().join();
        parcels = new ParcelService(db, clock, admins::contains);
        id = settlements.create(owner, world, new ChunkPosition(0, 0)).join().settlementId();
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
        settlements.claim(owner, id, world, new ChunkPosition(1, 0)).join();
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
        settlements =
                new SettlementService(db, SettlementPolicy.defaults(), clock, admins::contains);
        settlements.initialize().join();
        parcels = new ParcelService(db, clock, admins::contains);
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
        var activity = new ActivityService(db, clock, ActivityPolicy.defaults(), admins::contains);
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
        settlements.claim(owner, id, world, new ChunkPosition(1, 0)).join();
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
    void numericPricesAndAdministratorNamingDoNotInventCurrency() {
        var land = parcels.land(id).join();
        assertEquals(1, land.number());
        assertNull(land.officialName());
        var pricing = new SettlementPolicy(true, 64, 24000, 1200, 24000, 1200, 7, 3);
        assertEquals(7, pricing.claimPrice(0));
        assertEquals(10, pricing.claimPrice(1));
        var claimed = settlements.claim(owner, id, world, new ChunkPosition(1, 0)).join();
        assertEquals(2, claimed.current().chunks().size());
        rejected(() -> settlements.rename(owner, id, "Home").join());
        rejected(() -> settlements.rename(admin, id, "Home").join());
        settlements.certify(admin, id, true).join();
        settlements.rename(admin, id, "Home").join();
        assertEquals("Home", parcels.land(id).join().officialName());
        assertEquals(land.number(), parcels.land(id).join().number());
        assertTrue(
                settlements.history(id).join().stream()
                        .anyMatch(
                                e ->
                                        e.detail()
                                                .contains(
                                                        "numeric_price="
                                                                + settlements.claimPrice(1)
                                                                + ";charged=false")));
    }

    @Test
    void legacyRolesAndNamesMigrateWithoutDroppingMembersOrOffers() {
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
                            try (var q = c.createStatement()) {
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
                                    q.executeUpdate("DROP TABLE " + table);
                                q.executeUpdate(
                                        "ALTER TABLE settlement_members RENAME TO"
                                                + " migrated_members");
                                q.executeUpdate(
                                        "CREATE TABLE settlement_members(settlement_id TEXT NOT"
                                            + " NULL,player_uuid TEXT NOT NULL,role TEXT NOT NULL"
                                            + " CHECK(role IN ('MANAGER','MEMBER')),PRIMARY"
                                            + " KEY(settlement_id,player_uuid))");
                                q.executeUpdate(
                                        "INSERT INTO settlement_members SELECT"
                                            + " settlement_id,player_uuid,CASE role WHEN 'GOVERNOR'"
                                            + " THEN 'MANAGER' ELSE 'MEMBER' END FROM"
                                            + " migrated_members");
                                q.executeUpdate("DROP TABLE migrated_members");
                                q.executeUpdate(
                                        "UPDATE settlements SET name='Legacy',name_key='legacy'");
                                q.executeUpdate("UPDATE civilization_schema SET version=2");
                            }
                            return null;
                        })
                .join();
        var restored = settlements.initialize().join().getFirst();
        assertEquals(id, restored.id());
        assertEquals(Role.GOVERNOR, restored.role(holder));
        assertEquals("Legacy", parcels.land(id).join().officialName());
        assertEquals(pending.id(), settlements.inspectOffer(pending.id()).join().id());
        settlements.accept(other, pending.id(), false).join();
        assertEquals(Role.CITIZEN, settlements.initialize().join().getFirst().role(other));
    }
}
