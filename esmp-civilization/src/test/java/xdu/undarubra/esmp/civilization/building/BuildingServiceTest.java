package xdu.undarubra.esmp.civilization.building;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.api.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.building.BuildingService.*;
import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionException;

class BuildingServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    SettlementService settlements;
    ParcelService parcels;
    BuildingService buildings;
    LandValueService land;
    UUID source = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            visitor = UUID.randomUUID(),
            admin = UUID.randomUUID(),
            world,
            settlement;
    static final BuildingCell AIR = new BuildingCell("minecraft:air", true, false, false, false),
            STONE = new BuildingCell("minecraft:stone", false, true, false, false),
            CHEST =
                    new BuildingCell(
                            "minecraft:chest[facing=north,type=single,waterlogged=false]",
                            false,
                            true,
                            false,
                            true),
            CRAFT = new BuildingCell("minecraft:crafting_table", false, true, false, false);

    void open() {
        db = new WorldStore(directory.resolve("buildings.db"));
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
                SettlementServices.settlement(
                        db,
                        SettlementPolicy.defaults(),
                        clock,
                        admin::equals,
                        (tx, id) -> {},
                        ClaimPricingPolicy.defaults(),
                        (tx, quote) -> {},
                        (tx, id) -> true);
        settlements.initialize().join();
        land = ObservationServices.land(db, LandValuePolicy.defaults());
        land.initialize().join();
        parcels = SettlementServices.parcel(db, clock, admin::equals);
        buildings = SettlementServices.buildings(db, clock, Policy.defaults(), admin::equals);
        db.finishMigration().join();
    }

    @BeforeEach
    void setup() {
        open();
        capture(0);
        var q = settlements.quoteCreate(owner, world, new ChunkPosition(0, 0)).join();
        settlements.purchase(owner, q.id()).join();
        settlement = q.settlementId();
    }

    @AfterEach
    void close() {
        db.close();
    }

    void capture(int x) {
        land.capture(
                        List.of(
                                new LandValueProfile(
                                        world, x, 0, 1, 100, LandValueProfile.Terrain.FLAT)))
                .join();
    }

    Stamp stamp() {
        var s =
                settlements.initialize().join().stream()
                        .filter(v -> v.id().equals(settlement))
                        .findFirst()
                        .orElseThrow();
        return new Stamp(
                s.worldInstanceId(),
                s.ownershipRevision(),
                s.chunkRevision(),
                parcels.land(settlement).join().securityRevision());
    }

    BuildingBaseline house() {
        var bounds = new BuildingBounds(1, 1, 1, 5, 5, 5);
        var cells = new ArrayList<BuildingCell>();
        for (int i = 0; i < bounds.volume(); i++) {
            var p = bounds.position(i);
            cells.add(
                    p.x() == 1 || p.x() == 5 || p.y() == 1 || p.y() == 5 || p.z() == 1 || p.z() == 5
                            ? STONE
                            : AIR);
        }
        return new BuildingBaseline(bounds, cells)
                .patch(
                        Map.of(
                                new Position(1, 1, 1),
                                CHEST,
                                new Position(3, 1, 3),
                                CRAFT,
                                new Position(1, 2, 3),
                                new BuildingCell(
                                        "minecraft:oak_door[half=lower,open=false]",
                                        false,
                                        true,
                                        true,
                                        false)));
    }

    UUID register() {
        var id = UUID.randomUUID();
        buildings.register(owner, id, settlement, world, "Workshop", house(), stamp()).join();
        return id;
    }

    void reject(Runnable call) {
        assertThrows(CompletionException.class, call::run);
    }

    @Test
    void geometryAndPaletteRetainOccupiedInteriorOpeningsAndSupportWithoutEntityPayload() {
        var b = house();
        assertEquals(b, BuildingBaseline.decode(b.encode()));
        var g = b.geometry();
        assertEquals(98, g.occupied().size());
        assertEquals(27, g.interiorAir().size());
        assertEquals(98, g.exterior().size());
        assertTrue(g.openings().contains(b.bounds().index(new Position(1, 2, 3))));
        assertEquals(
                b.bounds().index(new Position(1, 1, 2)),
                g.supportedBy().get(b.bounds().index(new Position(1, 2, 2))));
        assertFalse(b.at(new Position(1, 1, 1)).automaticallyMutable());
        var id = register();
        assertEquals(b, buildings.inspect(id, 1).join().baseline());
        assertEquals(1, buildings.inspect(id, 0).join().building().revision());
    }

    @Test
    void registrationRejectsWrongGovernanceWorldClaimVolumeAndOverlap() {
        var b = house();
        reject(
                () ->
                        buildings
                                .register(
                                        visitor,
                                        UUID.randomUUID(),
                                        settlement,
                                        world,
                                        "Public",
                                        b,
                                        stamp())
                                .join());
        reject(
                () ->
                        buildings
                                .register(
                                        owner,
                                        UUID.randomUUID(),
                                        settlement,
                                        UUID.randomUUID(),
                                        "Wrong world",
                                        b,
                                        stamp())
                                .join());
        var outside =
                new BuildingBaseline(
                        new BuildingBounds(15, 1, 1, 17, 1, 1), Collections.nCopies(3, STONE));
        reject(
                () ->
                        buildings
                                .register(
                                        owner,
                                        UUID.randomUUID(),
                                        settlement,
                                        world,
                                        "Outside",
                                        outside,
                                        stamp())
                                .join());
        var small = SettlementServices.buildings(db, clock, new Policy(64), admin::equals);
        reject(
                () ->
                        small.register(
                                        owner,
                                        UUID.randomUUID(),
                                        settlement,
                                        world,
                                        "Too large",
                                        b,
                                        stamp())
                                .join());
        assertTrue(buildings.active(settlement).join().isEmpty());
        register();
        reject(
                () ->
                        buildings
                                .register(
                                        owner,
                                        UUID.randomUUID(),
                                        settlement,
                                        world,
                                        "Overlap",
                                        b,
                                        stamp())
                                .join());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BuildingBounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Policy(32769));
    }

    @Test
    void legalDemolitionChangesDesignAndReplayCannotRestoreAnOlderRevisionAfterRestart() {
        var id = register();
        var s = stamp();
        var p = new Position(2, 1, 2);
        UUID op = UUID.randomUUID();
        buildings.edit(op, owner, id, s, ParcelAction.BUILD, Map.of(p, AIR)).join();
        assertEquals(AIR, buildings.inspect(id, 0).join().baseline().at(p));
        assertEquals(STONE, buildings.inspect(id, 1).join().baseline().at(p));
        buildings
                .edit(UUID.randomUUID(), owner, id, s, ParcelAction.BUILD, Map.of(p, CRAFT))
                .join();
        db.close();
        open();
        assertTrue(buildings.recorded(op, owner, id).join());
        buildings.edit(op, owner, id, s, ParcelAction.BUILD, Map.of(p, AIR)).join();
        assertEquals(3, buildings.inspect(id, 0).join().building().revision());
        assertEquals(CRAFT, buildings.inspect(id, 0).join().baseline().at(p));
        assertFalse(buildings.inspect(id, 0).join().building().retired());
        reject(() -> buildings.edit(op, owner, id, s, ParcelAction.BUILD, Map.of(p, STONE)).join());
        assertEquals(3, buildings.history(id).join().size());
    }

    @Test
    void holderPermissionsOverrideOwnerButAllowLegalPublicRenovation() {
        var id = register();
        parcels.add(owner, settlement, "Held", Set.of(new ParcelCell(0, 0, 0)), -64, 320).join();
        var parcel = parcels.land(settlement).join().parcels().values().iterator().next().id();
        parcels.policy(owner, settlement, parcel, ParcelAction.BUILD, Set.of(ParcelRelation.HOLDER))
                .join();
        parcels.holders(owner, settlement, parcel, Set.of(visitor)).join();
        var request = parcels.requests(settlement).join().getFirst();
        parcels.resolve(visitor, settlement, request.id(), true).join();
        var p = new Position(2, 1, 2);
        reject(
                () ->
                        buildings
                                .edit(
                                        UUID.randomUUID(),
                                        owner,
                                        id,
                                        stamp(),
                                        ParcelAction.BUILD,
                                        Map.of(p, AIR))
                                .join());
        buildings
                .edit(UUID.randomUUID(), visitor, id, stamp(), ParcelAction.BUILD, Map.of(p, AIR))
                .join();
        assertEquals(AIR, buildings.inspect(id, 0).join().baseline().at(p));
    }

    @Test
    void staleAndPartlyInvalidEditsDoNotCommitAnyBaselineOrReceipt() {
        var id = register();
        var old = stamp();
        var foreign = new Stamp(UUID.randomUUID(), old.ownership(), old.claims(), old.security());
        reject(
                () ->
                        buildings
                                .edit(
                                        UUID.randomUUID(),
                                        owner,
                                        id,
                                        foreign,
                                        ParcelAction.BUILD,
                                        Map.of(new Position(2, 1, 2), AIR))
                                .join());
        capture(1);
        var q = settlements.quoteClaim(owner, settlement, world, new ChunkPosition(1, 0)).join();
        settlements.purchase(owner, q.id()).join();
        UUID op = UUID.randomUUID();
        var p = new Position(2, 1, 2);
        reject(() -> buildings.edit(op, owner, id, old, ParcelAction.BUILD, Map.of(p, AIR)).join());
        assertFalse(buildings.recorded(op, owner, id).join());
        reject(
                () ->
                        buildings
                                .edit(
                                        op,
                                        owner,
                                        id,
                                        stamp(),
                                        ParcelAction.BUILD,
                                        Map.of(p, AIR, new Position(7, 1, 7), STONE))
                                .join());
        assertEquals(STONE, buildings.inspect(id, 0).join().baseline().at(p));
        assertEquals(1, buildings.inspect(id, 0).join().building().revision());
    }

    @Test
    void stationIsExcludedAndBreakingItClearsOnlyCurrentMarker() {
        var id = register();
        var point = new Position(3, 1, 3);
        buildings.station(owner, id, 1, point).join();
        var detail = buildings.inspect(id, 0).join();
        assertEquals(point, detail.building().station());
        assertFalse(detail.building().permitsAutomaticMutation(point, CRAFT));
        assertFalse(detail.building().permitsAutomaticMutation(new Position(1, 1, 1), CHEST));
        assertTrue(detail.building().permitsAutomaticMutation(new Position(1, 2, 1), STONE));
        buildings
                .edit(UUID.randomUUID(), owner, id, stamp(), ParcelAction.BUILD, Map.of(point, AIR))
                .join();
        assertNull(buildings.inspect(id, 0).join().building().station());
        assertEquals(point, buildings.inspect(id, 2).join().building().station());
        assertNull(buildings.inspect(id, 1).join().building().station());
    }

    @Test
    void resizeRetainsIdentityAndRetirementKeepsHistoryWhileAllowingRelease() {
        var id = register();
        capture(1);
        var claim =
                settlements.quoteClaim(owner, settlement, world, new ChunkPosition(1, 0)).join();
        settlements.purchase(owner, claim.id()).join();
        var enlarged =
                new BuildingBaseline(
                        new BuildingBounds(1, 1, 1, 17, 5, 5), Collections.nCopies(425, STONE));
        buildings.resize(owner, id, 1, enlarged, stamp()).join();
        assertEquals(enlarged.bounds(), buildings.inspect(id, 0).join().building().bounds());
        assertEquals(house(), buildings.inspect(id, 1).join().baseline());
        reject(() -> settlements.unclaim(owner, settlement, new ChunkPosition(1, 0)).join());
        var abandonment =
                settlements
                        .offer(owner, settlement, owner, SettlementOffer.Type.ABANDON)
                        .join()
                        .offer();
        reject(() -> settlements.accept(owner, abandonment.id(), true).join());
        buildings.retire(owner, id, 2).join();
        assertTrue(buildings.active(settlement).join().isEmpty());
        assertEquals(3, buildings.history(id).join().size());
        assertTrue(buildings.inspect(id, 0).join().building().retired());
        settlements.accept(owner, abandonment.id(), true).join();
        assertEquals(enlarged, buildings.inspect(id, 0).join().baseline());
    }
}
