package xdu.undarubra.esmp.civilization.repair;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.api.*;
import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.decay.*;
import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.civilization.repair.RepairService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionException;

class RepairServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    SettlementService settlements;
    BuildingService buildings;
    DecayService decay;
    RepairService repair;
    ParcelService parcels;
    UUID source = UUID.randomUUID(), owner = UUID.randomUUID(), world, settlement, building;
    long raw;
    BuildingBaseline baseline;
    Map<Position, BuildingCell> physical;
    static final BuildingCell AIR = new BuildingCell("minecraft:air", true, false, false, false),
            STONE = new BuildingCell("minecraft:stone_bricks", false, true, false, false),
            CRAFT = new BuildingCell("minecraft:crafting_table", false, true, false, false),
            GOLD = new BuildingCell("minecraft:gold_block", false, true, false, false);
    final DecayService.Policy decayPolicy = new DecayService.Policy(0, 1, 1, .25, .5, .5);

    void open() {
        db = new WorldStore(directory.resolve("repair.db"));
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
                        ignored -> false,
                        (tx, id) -> {},
                        ClaimPricingPolicy.defaults(),
                        (tx, q) -> {},
                        (tx, id) -> true);
        settlements.initialize().join();
        parcels = SettlementServices.parcel(db, clock, ignored -> false);
        buildings =
                SettlementServices.buildings(
                        db, clock, BuildingService.Policy.defaults(), ignored -> false);
        decay =
                SettlementServices.decay(
                        db,
                        clock,
                        ActivityPolicy.defaults(),
                        new LocalActivityPolicy(0, 20),
                        decayPolicy);
        repair =
                SettlementServices.repair(
                        db,
                        clock,
                        ActivityPolicy.defaults(),
                        new LocalActivityPolicy(0, 20),
                        decayPolicy,
                        new Policy(10),
                        ignored -> false);
        db.finishMigration().join();
        decay.initialize().join();
        repair.initialize().join();
    }

    @BeforeEach
    void setup() {
        open();
        var land = ObservationServices.land(db, LandValuePolicy.defaults());
        land.initialize().join();
        land.capture(
                        List.of(
                                new LandValueProfile(
                                        world, 0, 0, 1, 100, LandValueProfile.Terrain.FLAT)))
                .join();
        var q = settlements.quoteCreate(owner, world, new ChunkPosition(0, 0)).join();
        settlements.purchase(owner, q.id()).join();
        settlement = q.settlementId();
        baseline =
                new BuildingBaseline(
                        new BuildingBounds(1, 1, 1, 3, 2, 2),
                        List.of(
                                AIR, AIR, AIR, AIR, AIR, AIR, STONE, STONE, STONE, STONE, STONE,
                                CRAFT));
        building = UUID.randomUUID();
        buildings
                .register(owner, building, settlement, world, "RepairRoof", baseline, stamp())
                .join();
        buildings.station(owner, building, 1, new Position(3, 2, 2)).join();
        physical = new HashMap<>();
        for (int i = 0; i < baseline.cells().size(); i++)
            physical.put(baseline.bounds().position(i), baseline.cells().get(i));
    }

    @AfterEach
    void close() {
        db.close();
    }

    BuildingService.Stamp stamp() {
        var s =
                settlements.initialize().join().stream()
                        .filter(v -> v.id().equals(settlement))
                        .findFirst()
                        .orElseThrow();
        return new BuildingService.Stamp(
                world,
                s.ownershipRevision(),
                s.chunkRevision(),
                parcels.land(settlement).join().securityRevision());
    }

    void advance(long ticks) {
        raw += ticks;
        clock.sample(source, raw, true);
    }

    void damage(DecayService.Kind kind) {
        for (int i = 0; i < 4; i++) {
            var w = decay.work(building).join();
            if (w.offer() != null && w.offer().kind() == kind) {
                var op = decay.prepare(w.offer(), DecayPlan.plan(w.offer(), physical)).join();
                var actual = new HashMap<Position, BuildingCell>();
                for (var c : op.changes()) {
                    var value =
                            new BuildingCell(
                                    c.target(),
                                    c.target().equals("minecraft:air"),
                                    !c.target().equals("minecraft:air"),
                                    false,
                                    false);
                    actual.put(c.position(), value);
                    physical.put(c.position(), value);
                }
                decay.finish(op.id(), DecayService.Outcome.APPLIED, actual).join();
                return;
            }
            decay.defer(building, true).join();
            advance(1);
        }
        throw new AssertionError("No Decay offer");
    }

    UUID prepare() {
        UUID id = UUID.randomUUID();
        repair.prepare(id, owner, building).join();
        return id;
    }

    Transfer supply(UUID job, int amount) {
        var slots = List.of(new Slot(0, new Item("minecraft:stone_bricks", amount), Item.empty()));
        return repair.transfer(UUID.randomUUID(), owner, job, Direction.SUPPLY, slots).join();
    }

    Operation intent(UUID job) {
        advance(10);
        var work = repair.work(job).join();
        assertNotNull(work.offer());
        return repair.intent(work.offer(), work.offer().cells()).join();
    }

    Map<Position, BuildingCell> apply(Operation op) {
        var result = new HashMap<Position, BuildingCell>();
        for (var c : op.cells()) {
            var value =
                    new BuildingCell(
                            c.target(),
                            c.target().equals("minecraft:air"),
                            !c.target().equals("minecraft:air"),
                            false,
                            false);
            result.put(c.position(), value);
            physical.put(c.position(), value);
        }
        return result;
    }

    void reject(Runnable work) {
        assertThrows(CompletionException.class, work::run);
    }

    @Test
    void partialSupplyRequiresAllMaterialsAndDuplicateConfirmationCannotMintEscrow() {
        damage(DecayService.Kind.CAVE_IN);
        var quote = repair.inspect(building).join();
        assertEquals(4, quote.materials().get("minecraft:stone_bricks"));
        UUID job = prepare();
        var first = supply(job, 2);
        assertEquals("PREPARED", first.state());
        assertEquals(0, repair.job(job).join().stock().getFirst().escrow());
        repair.confirm(first.id()).join();
        repair.confirm(first.id()).join();
        assertEquals(2, repair.job(job).join().stock().getFirst().escrow());
        advance(100);
        assertNull(repair.work(job).join().offer());
        reject(() -> repair.start(owner, job).join());
        var second = supply(job, 2);
        repair.confirm(second.id()).join();
        assertEquals("FUNDED", repair.job(job).join().state());
        assertNull(repair.work(job).join().offer());
    }

    @Test
    void completeRuinRepairConsumesOnceAndAdvancesEraOnceAcrossRestart() {
        damage(DecayService.Kind.CAVE_IN);
        assertEquals(DecayService.Condition.RUINED, decay.status(building).join().condition());
        UUID job = prepare();
        var transfer = supply(job, 4);
        repair.confirm(transfer.id()).join();
        var op = intent(job);
        assertFalse(decay.status(building).join().eligible());
        assertTrue(repair.authorize(op.id()).join().valid());
        var result = apply(op);
        repair.finish(op.id(), result, true, Set.of()).join();
        assertEquals("COMPLETED", repair.job(job).join().state());
        assertEquals(1, repair.job(job).join().era());
        assertEquals(4, repair.job(job).join().stock().getFirst().consumed());
        assertEquals(0, repair.job(job).join().stock().getFirst().escrow());
        assertEquals(DecayService.Condition.INTACT, decay.status(building).join().condition());
        assertEquals(baseline, buildings.inspect(building, 0).join().baseline());
        db.close();
        open();
        repair.confirm(transfer.id()).join();
        repair.finish(op.id(), result, true, Set.of()).join();
        assertEquals(1, repair.job(job).join().era());
        assertEquals(4, repair.job(job).join().stock().getFirst().consumed());
        var facts = settlements.mvpHistory(settlement).join();
        assertEquals(
                1, facts.stream().filter(f -> f.kind() == MvpHistory.Kind.ERA_ADVANCED).count());
        assertTrue(facts.stream().anyMatch(f -> f.kind() == MvpHistory.Kind.REPAIR_STARTED));
        assertTrue(facts.stream().anyMatch(f -> f.kind() == MvpHistory.Kind.REPAIR_COMPLETED));
    }

    @Test
    void nonRuinedRepairAndManualCompletionDoNotAwardEra() {
        damage(DecayService.Kind.WEATHER);
        UUID job = prepare();
        repair.confirm(supply(job, 1).id()).join();
        var op = intent(job);
        repair.finish(op.id(), apply(op), true, Set.of()).join();
        assertEquals(0, repair.job(job).join().era());
        advance(1);
        damage(DecayService.Kind.CAVE_IN);
        UUID manual = prepare();
        repair.confirm(supply(manual, 4).id()).join();
        var changes = new HashMap<Position, BuildingCell>();
        for (var e : physical.entrySet())
            if (!baseline.at(e.getKey()).equals(e.getValue()))
                changes.put(e.getKey(), baseline.at(e.getKey()));
        buildings
                .edit(UUID.randomUUID(), owner, building, stamp(), ParcelAction.BUILD, changes)
                .join();
        advance(10);
        repair.work(manual).join();
        assertEquals("COMPLETED", repair.job(manual).join().state());
        assertEquals(0, repair.job(manual).join().era());
        assertEquals(4, repair.job(manual).join().stock().getFirst().escrow());
    }

    @Test
    void cancellationAndPartialInventoryRefundRemainConservative() {
        damage(DecayService.Kind.CAVE_IN);
        UUID job = prepare();
        var supply = supply(job, 2);
        repair.confirm(supply.id()).join();
        repair.cancel(owner, job).join();
        assertEquals("CANCELLED", repair.job(job).join().state());
        var refund =
                repair.transfer(
                                UUID.randomUUID(),
                                owner,
                                job,
                                Direction.REFUND,
                                List.of(
                                        new Slot(
                                                0,
                                                new Item("minecraft:stone_bricks", 63),
                                                new Item("minecraft:stone_bricks", 64))))
                        .join();
        assertEquals(1, repair.job(job).join().stock().getFirst().escrow());
        repair.abort(refund.id()).join();
        assertEquals(2, repair.job(job).join().stock().getFirst().escrow());
        var paid =
                repair.transfer(
                                UUID.randomUUID(),
                                owner,
                                job,
                                Direction.REFUND,
                                List.of(
                                        new Slot(
                                                0,
                                                Item.empty(),
                                                new Item("minecraft:stone_bricks", 2))))
                        .join();
        repair.confirm(paid.id()).join();
        repair.confirm(paid.id()).join();
        assertEquals(0, repair.job(job).join().stock().getFirst().escrow());
        assertEquals(2, repair.job(job).join().stock().getFirst().refunded());
    }

    @Test
    void pendingWorldIntentBlocksRefundAndMetadataUntilReconciled() {
        damage(DecayService.Kind.CAVE_IN);
        UUID job = prepare();
        repair.confirm(supply(job, 4).id()).join();
        var op = intent(job);
        assertTrue(repair.reservations().join().contains(settlement));
        reject(
                () ->
                        buildings
                                .retire(
                                        owner,
                                        building,
                                        buildings.inspect(building, 0).join().building().revision())
                                .join());
        repair.cancel(owner, job).join();
        assertEquals("CANCELLING", repair.job(job).join().state());
        reject(
                () ->
                        repair.transfer(
                                        UUID.randomUUID(),
                                        owner,
                                        job,
                                        Direction.REFUND,
                                        List.of(
                                                new Slot(
                                                        0,
                                                        Item.empty(),
                                                        new Item("minecraft:stone_bricks", 4))))
                                .join());
        repair.finish(op.id(), apply(op), true, Set.of()).join();
        assertEquals("CANCELLED", repair.job(job).join().state());
        assertEquals(4, repair.job(job).join().stock().getFirst().consumed());
        assertEquals(0, repair.job(job).join().era());
        assertTrue(repair.reservations().join().isEmpty());
    }

    @Test
    void conflictRetiresOnlyItsCellAndRefundsUnusedMaterial() {
        damage(DecayService.Kind.CAVE_IN);
        UUID job = prepare();
        repair.confirm(supply(job, 4).id()).join();
        var op = intent(job);
        var actual = apply(op);
        Position conflict =
                op.cells().stream()
                        .filter(c -> !c.target().equals("minecraft:air"))
                        .findFirst()
                        .orElseThrow()
                        .position();
        actual.put(conflict, GOLD);
        repair.finish(op.id(), actual, true, Set.of(conflict)).join();
        assertEquals("COMPLETED", repair.job(job).join().state());
        assertEquals(3, repair.job(job).join().stock().getFirst().consumed());
        assertEquals(1, repair.job(job).join().stock().getFirst().escrow());
        assertEquals(0, repair.job(job).join().era());
        assertEquals(1, decay.status(building).join().damagedCells());
    }

    @Test
    void renovationReplansUnaffectedCellsAndNeverRestoresObsoleteTarget() {
        damage(DecayService.Kind.CAVE_IN);
        UUID job = prepare();
        repair.confirm(supply(job, 4).id()).join();
        Position p =
                physical.entrySet().stream()
                        .filter(e -> e.getValue().air() && !baseline.at(e.getKey()).air())
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        buildings
                .edit(
                        UUID.randomUUID(),
                        owner,
                        building,
                        stamp(),
                        ParcelAction.BUILD,
                        Map.of(p, GOLD))
                .join();
        physical.put(p, GOLD);
        var op = intent(job);
        assertTrue(op.cells().stream().noneMatch(c -> c.position().equals(p)));
        repair.finish(op.id(), apply(op), true, Set.of()).join();
        assertEquals(GOLD, buildings.inspect(building, 0).join().baseline().at(p));
        assertEquals(3, repair.job(job).join().stock().getFirst().consumed());
        assertEquals(1, repair.job(job).join().stock().getFirst().escrow());
    }

    @Test
    void transferCodecAndInventoryCapacityPreserveOnlyPlainMaterials() {
        var j =
                new Job(
                        UUID.randomUUID(),
                        building,
                        settlement,
                        world,
                        owner,
                        "AWAITING",
                        1,
                        new Position(3, 2, 2),
                        null,
                        0,
                        100,
                        0,
                        List.of(new Stock("minecraft:stone_bricks", 100, 0, 0, 0)));
        var inventory = new ArrayList<Item>(Collections.nCopies(36, new Item("opaque", 1)));
        inventory.set(0, new Item("minecraft:stone_bricks", 64));
        var plan = RepairMaterials.supply(j, inventory);
        assertEquals(
                64, RepairMaterials.amounts(Direction.SUPPLY, plan).get("minecraft:stone_bricks"));
        assertEquals(plan, RepairMaterials.decode(RepairMaterials.encode(plan)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RepairMaterials.amounts(
                                Direction.SUPPLY,
                                List.of(new Slot(1, new Item("opaque", 1), Item.empty()))));
    }

    @Test
    void newRuinCycleCanAdvanceAgainButOldReceiptCannot() {
        damage(DecayService.Kind.CAVE_IN);
        UUID first = prepare();
        repair.confirm(supply(first, 4).id()).join();
        var previous = intent(first);
        var previousResult = apply(previous);
        repair.finish(previous.id(), previousResult, true, Set.of()).join();
        advance(10);
        damage(DecayService.Kind.CAVE_IN);
        UUID second = prepare();
        repair.confirm(supply(second, 4).id()).join();
        var current = intent(second);
        repair.finish(current.id(), apply(current), true, Set.of()).join();
        assertEquals(2, repair.job(second).join().era());
        repair.finish(previous.id(), previousResult, true, Set.of()).join();
        assertEquals(2, repair.job(second).join().era());
        assertEquals(
                2,
                settlements.mvpHistory(settlement).join().stream()
                        .filter(f -> f.kind() == MvpHistory.Kind.ERA_ADVANCED)
                        .count());
    }

    @Test
    void unsavedTransferCancellationAndAheadPlayerReceiptCannotCreateMaterials() {
        damage(DecayService.Kind.CAVE_IN);
        UUID job = prepare();
        var transfer = supply(job, 2);
        repair.abort(transfer.id()).join();
        assertEquals(0, repair.job(job).join().stock().getFirst().escrow());
        reject(() -> repair.confirm(transfer.id()).join());
        reject(() -> repair.transfers(owner, transfer.sequence() + 1).join());
        assertEquals("AWAITING", repair.job(job).join().state());
    }

    @Test
    void phaseSixUpgradeStartsEraZeroWithoutInventingPastRepairs() throws Exception {
        damage(DecayService.Kind.CAVE_IN);
        var original = buildings.inspect(building, 0).join().baseline();
        db.close();
        try (var connection =
                        java.sql.DriverManager.getConnection(
                                "jdbc:sqlite:" + directory.resolve("repair.db"));
                var statement = connection.createStatement()) {
            for (String table :
                    List.of(
                            "repair_era_awards",
                            "repair_operations",
                            "repair_transfers",
                            "repair_materials",
                            "repair_cells",
                            "repair_jobs",
                            "building_eras")) statement.executeUpdate("DROP TABLE " + table);
            statement.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='phase7-repair'");
            statement.executeUpdate(
                    "DELETE FROM settlement_events WHERE type IN"
                            + " ('BUILDING_RUINED','DECAY_STARTED')");
        }
        open();
        assertEquals(original, buildings.inspect(building, 0).join().baseline());
        assertEquals(0, repair.inspect(building).join().era());
        assertEquals("RUINED", repair.inspect(building).join().condition());
        assertTrue(repair.jobs(building).join().isEmpty());
        assertTrue(
                settlements.mvpHistory(settlement).join().stream()
                        .noneMatch(
                                f ->
                                        f.kind() == MvpHistory.Kind.ERA_ADVANCED
                                                || f.kind() == MvpHistory.Kind.REPAIR_COMPLETED));
    }

    @Test
    void permissionPauseRetainsQualifyingCycleWhileUnaffectedWorkResumes() {
        damage(DecayService.Kind.CAVE_IN);
        UUID job = prepare();
        repair.confirm(supply(job, 4).id()).join();
        advance(10);
        var offer = repair.work(job).join().offer();
        var sources =
                offer.cells().stream()
                        .filter(c -> !c.target().equals("minecraft:air"))
                        .limit(2)
                        .toList();
        var selected = new HashSet<Position>();
        for (var source : sources) {
            selected.add(source.position());
            selected.add(
                    new Position(
                            source.position().x(),
                            source.position().y() - 1,
                            source.position().z()));
        }
        var first =
                repair.intent(
                                offer,
                                offer.cells().stream()
                                        .filter(c -> selected.contains(c.position()))
                                        .toList())
                        .join();
        repair.finish(first.id(), apply(first), true, Set.of()).join();
        assertEquals(DecayService.Condition.DAMAGED, decay.status(building).join().condition());
        UUID cycle = repair.job(job).join().cycle();
        parcels.add(owner, settlement, "Permission", Set.of(new ParcelCell(0, 0, 0)), -64, 320)
                .join();
        UUID parcel = parcels.land(settlement).join().parcels().values().iterator().next().id();
        parcels.policy(owner, settlement, parcel, ParcelAction.BUILD, Set.of()).join();
        advance(10);
        repair.work(job).join();
        assertEquals("PAUSED", repair.job(job).join().state());
        parcels.policy(owner, settlement, parcel, ParcelAction.BUILD, Set.of(ParcelRelation.OWNER))
                .join();
        repair.start(owner, job).join();
        assertEquals(cycle, repair.job(job).join().cycle());
        var next = intent(job);
        repair.finish(next.id(), apply(next), true, Set.of()).join();
        assertEquals(1, repair.job(job).join().era());
    }
}
