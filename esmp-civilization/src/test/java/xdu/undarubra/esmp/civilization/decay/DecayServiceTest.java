package xdu.undarubra.esmp.civilization.decay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.api.*;
import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.decay.DecayService.*;
import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionException;

class DecayServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    SettlementService settlements;
    BuildingService buildings;
    ActivityService activity;
    DecayService decay;
    ParcelService parcels;
    UUID source = UUID.randomUUID(), owner = UUID.randomUUID(), world, settlement, building;
    long raw;
    final Policy policy = new Policy(100, 10, 1, .25, .5, .5);
    static final BuildingCell AIR = new BuildingCell("minecraft:air", true, false, false, false),
            STONE = new BuildingCell("minecraft:stone_bricks", false, true, false, false),
            GOLD = new BuildingCell("minecraft:gold_block", false, true, false, false);
    BuildingBaseline baseline;
    Map<Position, BuildingCell> worldCells;

    void open() {
        db = new WorldStore(directory.resolve("decay.db"));
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
                        new LocalActivityPolicy(0, 20),
                        policy,
                        ignored -> false);
        settlements =
                SettlementServices.settlement(
                        db,
                        SettlementPolicy.defaults(),
                        clock,
                        ignored -> false,
                        (tx, id) -> {},
                        ClaimPricingPolicy.defaults(),
                        (tx, q) -> {},
                        activity::isActive);
        settlements.initialize().join();
        activity.initialize().join();
        buildings =
                SettlementServices.buildings(
                        db, clock, BuildingService.Policy.defaults(), ignored -> false);
        parcels = SettlementServices.parcel(db, clock, ignored -> false);
        decay =
                SettlementServices.decay(
                        db,
                        clock,
                        ActivityPolicy.defaults(),
                        new LocalActivityPolicy(0, 20),
                        policy);
        db.finishMigration().join();
        decay.initialize().join();
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
        settlements.purchase(owner, q.id(), java.util.List.of()).join();
        settlement = q.settlementId();
        baseline =
                new BuildingBaseline(
                        new BuildingBounds(1, 1, 1, 2, 2, 2),
                        List.of(AIR, AIR, AIR, AIR, STONE, STONE, STONE, STONE));
        building = UUID.randomUUID();
        buildings.register(owner, building, settlement, world, "Roof", baseline, stamp()).join();
        worldCells = new HashMap<>();
        for (int i = 0; i < baseline.cells().size(); i++)
            worldCells.put(baseline.bounds().position(i), baseline.cells().get(i));
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

    void credit() {
        var s = stamp();
        activity.contribute(
                        List.of(
                                new ActivityContribution(
                                        settlement,
                                        world,
                                        owner,
                                        0,
                                        0,
                                        0,
                                        s.ownership(),
                                        s.claims(),
                                        s.security(),
                                        ParcelAction.BUILD,
                                        false,
                                        null)))
                .join();
    }

    Offer offer(Kind kind) {
        decay.work(building).join();
        advance(policy.graceTicks());
        for (int i = 0; i < 4; i++) {
            var w = decay.work(building).join();
            if (w.offer() != null && w.offer().kind() == kind) return w.offer();
            decay.defer(building, true).join();
            advance(policy.intervalTicks());
        }
        throw new AssertionError("No requested eligible offer");
    }

    Operation prepare(Kind kind) {
        var o = offer(kind);
        var changes = DecayPlan.plan(o, worldCells);
        assertFalse(changes.isEmpty());
        return decay.prepare(o, changes).join();
    }

    Map<Position, BuildingCell> actual(Operation op, boolean apply) {
        var result = new HashMap<Position, BuildingCell>();
        for (var c : op.changes()) {
            String data = apply ? c.target() : c.expected();
            var value =
                    new BuildingCell(
                            data,
                            data.equals("minecraft:air"),
                            !data.equals("minecraft:air"),
                            false,
                            false);
            result.put(c.position(), value);
            if (apply) worldCells.put(c.position(), value);
        }
        return result;
    }

    void rejected(Runnable call) {
        assertThrows(CompletionException.class, call::run);
    }

    @Test
    void graceFreezeAndRecoveryAreLocalAndDoNotAccrueDowntime() {
        assertNull(decay.work(building).join().offer());
        advance(99);
        assertFalse(decay.status(building).join().eligible());
        clock.sample(source, raw, false);
        clock.sample(source, raw + 100000, false);
        assertNull(decay.work(building).join().offer());
        clock.flush().join();
        db.close();
        raw += 100000;
        open();
        assertEquals(99, clock.ticks());
        assertFalse(decay.status(building).join().eligible());
        advance(1);
        assertNotNull(decay.work(building).join().offer());
        credit();
        assertFalse(decay.status(building).join().eligible());
        assertNull(decay.work(building).join().offer());
        long recovered = clock.ticks();
        advance(336000);
        assertNull(decay.work(building).join().offer());
        advance(1);
        var declined = decay.work(building).join();
        assertEquals(recovered + 336001, declined.state().inactiveSince());
        assertFalse(declined.state().eligible());
        advance(100);
        assertTrue(decay.status(building).join().eligible());
    }

    @Test
    void weatherIsWhitelistedDurableAndNeverRewritesIntendedDesign() {
        var op = prepare(Kind.WEATHER);
        assertEquals("PREPARED", op.state());
        assertEquals(op.changes(), DecayPlan.decode(DecayPlan.encode(op.changes())));
        assertEquals("minecraft:cracked_stone_bricks", op.changes().getFirst().target());
        assertTrue(decay.authorize(op.id()).join().valid());
        decay.finish(op.id(), Outcome.APPLIED, actual(op, true)).join();
        assertEquals(Condition.DAMAGED, decay.status(building).join().condition());
        assertEquals(0, decay.status(building).join().lostCells());
        assertEquals(baseline, buildings.inspect(building, 0).join().baseline());
        decay.finish(op.id(), Outcome.APPLIED, actual(op, true)).join();
        assertEquals(
                1,
                settlements.history(settlement).join().stream()
                        .filter(e -> e.type().equals("DECAY_APPLIED"))
                        .count());
    }

    @Test
    void verticalDropAndConnectedCaveInHaveBoundedDeterministicTargets() {
        var o = offer(Kind.CAVE_IN);
        var changes = DecayPlan.plan(o, worldCells);
        assertEquals(8, changes.size());
        assertEquals(changes, DecayPlan.plan(o, worldCells));
        assertEquals(4, changes.stream().filter(c -> c.target().equals("minecraft:air")).count());
        var op = decay.prepare(o, changes).join();
        decay.finish(op.id(), Outcome.APPLIED, actual(op, true)).join();
        assertEquals(Condition.RUINED, decay.status(building).join().condition());
        assertEquals(4, decay.status(building).join().lostCells());
        assertEquals(baseline, buildings.inspect(building, 0).join().baseline());
    }

    @Test
    void recoveryStopsPreparedWorkButDoesNotRepairCommittedDamage() {
        var first = prepare(Kind.DROP);
        assertEquals(2, first.changes().size());
        decay.finish(first.id(), Outcome.APPLIED, actual(first, true)).join();
        advance(10);
        var next = decay.work(building).join().offer();
        var pending = decay.prepare(next, DecayPlan.plan(next, worldCells)).join();
        int damage = decay.status(building).join().damagedCells();
        credit();
        assertFalse(decay.authorize(pending.id()).join().valid());
        decay.finish(pending.id(), Outcome.CANCELLED, actual(pending, false)).join();
        assertEquals(damage, decay.status(building).join().damagedCells());
        assertEquals(Condition.DAMAGED, decay.status(building).join().condition());
        assertNull(decay.work(building).join().offer());
    }

    @Test
    void aSavedTargetCanFinalizeAfterRecoveryAndAnUnsavedOutcomeCanReconcile() {
        var op = prepare(Kind.DROP);
        var targets = actual(op, true);
        credit();
        assertFalse(decay.authorize(op.id()).join().valid());
        decay.finish(op.id(), Outcome.APPLIED, targets).join();
        assertEquals(Condition.DAMAGED, decay.status(building).join().condition());
        db.close();
        open();
        assertFalse(decay.authorize(op.id()).join().valid());
        assertEquals(op.id(), decay.chunk(world, 0, 0, 0).join().getFirst().id());
        // Independent world-save rollback: original cells are actually present, not repaired by
        // Activity.
        decay.finish(op.id(), Outcome.CANCELLED, actual(op, false)).join();
        assertEquals(Condition.INTACT, decay.status(building).join().condition());
        assertEquals(
                1,
                settlements.history(settlement).join().stream()
                        .filter(e -> e.type().equals("DECAY_APPLIED"))
                        .count());
    }

    @Test
    void playerRenovationAndChangedAuthorityInvalidateOldIntent() {
        var op = prepare(Kind.WEATHER);
        var point = op.changes().getFirst().position();
        buildings
                .edit(
                        UUID.randomUUID(),
                        owner,
                        building,
                        stamp(),
                        ParcelAction.BUILD,
                        Map.of(point, GOLD))
                .join();
        assertFalse(decay.authorize(op.id()).join().valid());
        decay.finish(op.id(), Outcome.CANCELLED, Map.of(point, GOLD)).join();
        assertEquals(GOLD, buildings.inspect(building, 0).join().baseline().at(point));
        assertEquals(Condition.INTACT, decay.status(building).join().condition());
    }

    @Test
    void destinationParcelAndBlockEntityStationExclusionsCannotBeBypassed() {
        assertFalse(
                DecayPlan.safeNeighbor(
                        new BuildingCell("minecraft:torch", false, false, false, false)));
        assertFalse(
                DecayPlan.safeNeighbor(
                        new BuildingCell("minecraft:chest", false, true, false, true)));
        var point = new Position(1, 2, 1);
        var crafted =
                baseline.patch(
                        Map.of(
                                point,
                                new BuildingCell(
                                        "minecraft:crafting_table", false, true, false, false),
                                new Position(2, 2, 2),
                                new BuildingCell("minecraft:chest", false, true, false, true)));
        buildings.resize(owner, building, 1, crafted, stamp()).join();
        buildings.station(owner, building, 2, point).join();
        var offer = offer(Kind.DROP);
        assertFalse(offer.candidates().contains(point));
        assertFalse(offer.candidates().contains(new Position(2, 2, 2)));
        var bad =
                List.of(
                        new Change(new Position(1, 1, 1), "minecraft:air", "minecraft:gold_block"),
                        new Change(
                                new Position(1, 2, 1),
                                "minecraft:crafting_table",
                                "minecraft:air"));
        rejected(() -> decay.prepare(offer, bad).join());
        buildings.station(owner, building, 3, null).join();
        baseline =
                new BuildingBaseline(
                        new BuildingBounds(1, 7, 1, 2, 8, 2),
                        List.of(AIR, AIR, AIR, AIR, STONE, STONE, STONE, STONE));
        buildings.resize(owner, building, 4, baseline, stamp()).join();
        worldCells.clear();
        for (int i = 0; i < baseline.cells().size(); i++)
            worldCells.put(baseline.bounds().position(i), baseline.cells().get(i));
        parcels.add(owner, settlement, "Destination", Set.of(new ParcelCell(0, 0, 0)), -64, 320)
                .join();
        var parcel = parcels.land(settlement).join().parcels().values().iterator().next().id();
        parcels.policy(owner, settlement, parcel, ParcelAction.BUILD, Set.of(ParcelRelation.HOLDER))
                .join();
        var denied = offer(Kind.DROP);
        var deniedPlan = DecayPlan.plan(denied, worldCells);
        assertEquals(2, deniedPlan.size());
        rejected(() -> decay.prepare(denied, deniedPlan).join());
        assertTrue(decay.chunk(world, 0, 0, 0).join().isEmpty());
    }

    @Test
    void partialConflictRecordsOnlyAlreadyAppliedCellsAndKeepsOtherSourceBlocks() {
        var op = prepare(Kind.CAVE_IN);
        var partial = actual(op, false);
        var destination = op.changes().getFirst();
        partial.put(
                destination.position(),
                new BuildingCell(destination.target(), false, true, false, false));
        decay.finish(op.id(), Outcome.CONFLICT, partial).join();
        assertEquals(1, decay.status(building).join().damagedCells());
        assertEquals(0, decay.status(building).join().lostCells());
        assertEquals(Condition.DAMAGED, decay.status(building).join().condition());
        decay.finish(op.id(), Outcome.APPLIED, actual(op, true)).join();
        assertEquals(1, decay.status(building).join().damagedCells());
    }

    @Test
    void storedIntentMaterialCorruptionIsRejectedBeforeWorldAuthorization() {
        var op = prepare(Kind.WEATHER);
        var original = op.changes().getFirst();
        var forged =
                List.of(new Change(original.position(), original.expected(), "minecraft:lava"));
        var table = xdu.undarubra.esmp.civilization.generated.Tables.DECAY_OPERATIONS;
        db.transact(
                        tx ->
                                xdu.undarubra.esmp.core.storage.DatabaseAccess.context(tx)
                                        .update(table)
                                        .set(table.PAYLOAD, DecayPlan.encode(forged))
                                        .where(table.OPERATION_ID.eq(op.id()))
                                        .execute())
                .join();
        rejected(() -> decay.authorize(op.id()).join());
        assertEquals(Condition.INTACT, decay.status(building).join().condition());
    }

    @Test
    void manualRestorationClearsDamageEvenWhenIntendedBlockDataIsUnchanged() {
        var op = prepare(Kind.CAVE_IN);
        decay.finish(op.id(), Outcome.APPLIED, actual(op, true)).join();
        assertEquals(Condition.RUINED, decay.status(building).join().condition());
        var restored = new HashMap<Position, BuildingCell>();
        for (var change : op.changes())
            restored.put(change.position(), baseline.at(change.position()));
        buildings
                .edit(UUID.randomUUID(), owner, building, stamp(), ParcelAction.BUILD, restored)
                .join();
        assertEquals(2, buildings.inspect(building, 0).join().building().revision());
        assertEquals(baseline, buildings.inspect(building, 0).join().baseline());
        assertEquals(Condition.INTACT, decay.status(building).join().condition());
        assertEquals(0, decay.status(building).join().damagedCells());
    }
}
