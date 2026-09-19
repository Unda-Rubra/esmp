package xdu.undarubra.esmp.civilization.activity;

import static org.junit.jupiter.api.Assertions.*;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.land.LandValuePolicy;
import xdu.undarubra.esmp.civilization.land.LandValueProfile;
import xdu.undarubra.esmp.civilization.land.LandValueService;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

class ActivityServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    LandValueService landValues;
    SettlementService settlements;
    ActivityService activity;
    UUID source = UUID.randomUUID(), owner = UUID.randomUUID(), world, id;
    long raw;

    @BeforeEach
    void open() {
        db = new WorldStore(directory.resolve("activity.db"));
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
        activity =
                xdu.undarubra.esmp.civilization.api.ObservationServices.activity(
                        db,
                        clock,
                        ActivityPolicy.defaults(),
                        LocalActivityPolicy.defaults(),
                        xdu.undarubra.esmp.civilization.decay.DecayService.Policy.defaults(),
                        ignored -> false);
        settlements =
                xdu.undarubra.esmp.civilization.api.SettlementServices.settlement(
                        db,
                        SettlementPolicy.defaults(),
                        clock,
                        ignored -> false,
                        (c, settlement) -> {},
                        ClaimPricingPolicy.defaults(),
                        // These activity/governance fixtures do not exercise money transfers.
                        (c, quote) -> {},
                        activity::isActive);
        settlements.initialize().join();
        activity.initialize().join();
        id = create(owner, world, 0, 0).settlementId();
    }

    @AfterEach
    void close() {
        db.close();
    }

    void captureProfile(UUID worldInstance, int x, int z) {
        var policy = LandValuePolicy.defaults();
        // Every location uses the same immutable valuation. Repeated capture is idempotent.
        landValues
                .capture(
                        List.of(
                                new LandValueProfile(
                                        worldInstance,
                                        x,
                                        z,
                                        policy.version(),
                                        policy.baseValue(),
                                        LandValueProfile.Terrain.values()[0])))
                .join();
    }

    SettlementChange create(UUID actor, UUID worldInstance, int x, int z) {
        captureProfile(worldInstance, x, z);
        var quote = settlements.quoteCreate(actor, worldInstance, new ChunkPosition(x, z)).join();
        return settlements.purchase(actor, quote.id(), java.util.List.of()).join();
    }

    SettlementChange claim(UUID actor, UUID settlement, UUID worldInstance, int x, int z) {
        captureProfile(worldInstance, x, z);
        var quote =
                settlements
                        .quoteClaim(actor, settlement, worldInstance, new ChunkPosition(x, z))
                        .join();
        return settlements.purchase(actor, quote.id(), java.util.List.of()).join();
    }

    void advance(long ticks) {
        raw += ticks;
        clock.sample(source, raw, true);
    }

    ActivityContribution action(UUID player, ActivityContribution.Category category) {
        return new ActivityContribution(
                id,
                world,
                player,
                0,
                0,
                0,
                1,
                1,
                0,
                category == ActivityContribution.Category.BUILD
                        ? ParcelAction.BUILD
                        : ParcelAction.INTERACT,
                true,
                null);
    }

    @Test
    void lazyDecayStartsAtFoundingAndHasExactGraceBoundary() {
        advance(7 * 24000);
        var state = activity.status(id).join();
        assertEquals(15, state.score(), 1e-9);
        assertEquals(ActivityStatus.State.INACTIVE, state.state());
        long crossing = (long) Math.floor(7 * 24000 * Math.log(1.5) / Math.log(2)) + 1;
        assertEquals(crossing, state.inactiveSinceTick());
        assertFalse(state.productionPaused());
        advance(crossing + 72000 - clock.ticks());
        assertTrue(activity.status(id).join().productionPaused());
        assertEquals(crossing + 72000, activity.pauseIntervals(id).join().getFirst().fromTick());
        var other = create(owner, world, 1, 0).settlementId();
        assertEquals(30, activity.status(other).join().score(), 1e-9);
    }

    @Test
    void freezeAndRestartPreserveScoresAndPersistedCaps() {
        var build = action(owner, ActivityContribution.Category.BUILD);
        activity.contribute(List.of(build, build, build)).join();
        assertEquals(1, activity.status(id).join().creditedToday());
        assertEquals(
                1.0, activity.localActivity(id).join().rawScores().get(new ChunkPosition(0, 0)));
        clock.sample(source, raw, false);
        clock.sample(source, raw + 24000, false);
        raw += 24000;
        activity.contribute(List.of(build)).join();
        assertEquals(31, activity.status(id).join().score());
        clock.flush().join();
        db.close();
        db = new WorldStore(directory.resolve("activity.db"));
        db.initialize(
                        List.of(
                                new WorldDefinition(
                                        "MAIN", "civilization", WorldEnvironment.NORMAL)),
                        List.of())
                .join();
        clock = new GameClock(db);
        clock.initialize(source, raw + 1_000_000, true).join();
        activity =
                xdu.undarubra.esmp.civilization.api.ObservationServices.activity(
                        db,
                        clock,
                        ActivityPolicy.defaults(),
                        LocalActivityPolicy.defaults(),
                        xdu.undarubra.esmp.civilization.decay.DecayService.Policy.defaults(),
                        ignored -> false);
        activity.initialize().join();
        activity.contribute(List.of(build)).join();
        assertEquals(31, activity.status(id).join().score());
        assertEquals(1, activity.status(id).join().creditedToday());
        assertEquals(
                1.0, activity.localActivity(id).join().rawScores().get(new ChunkPosition(0, 0)));
    }

    @Test
    void roleAndRevisionRevalidationRejectsStaleOrUnauthorizedCredit() {
        UUID visitor = UUID.randomUUID();
        activity.contribute(List.of(action(visitor, ActivityContribution.Category.BUILD))).join();
        assertEquals(30, activity.status(id).join().score());
        assertEquals(
                0.0, activity.localActivity(id).join().rawScores().get(new ChunkPosition(0, 0)));
        activity.contribute(List.of(action(visitor, ActivityContribution.Category.INTERACTION)))
                .join();
        assertEquals(30.02, activity.status(id).join().score(), 1e-9);
        claim(owner, id, world, 1, 0);
        activity.contribute(List.of(action(owner, ActivityContribution.Category.BUILD))).join();
        assertEquals(.02, activity.status(id).join().creditedToday(), 1e-9);
        assertEquals(
                .02,
                activity.localActivity(id).join().rawScores().get(new ChunkPosition(0, 0)),
                1e-9);
    }

    @Test
    void dailyCapAndRecoveryPreserveClosedPauseIntervals() {
        advance(8 * 24000);
        assertTrue(activity.status(id).join().productionPaused());
        for (int day = 0;
                day < 10 && activity.status(id).join().state() == ActivityStatus.State.INACTIVE;
                day++) {
            for (int n = 0; n < 10; n++) {
                activity.contribute(List.of(action(owner, ActivityContribution.Category.BUILD)))
                        .join();
                advance(200);
            }
            assertTrue(activity.status(id).join().creditedToday() <= 5);
            if (activity.status(id).join().state() == ActivityStatus.State.INACTIVE) {
                advance(24000 - raw % 24000);
            }
        }
        assertEquals(ActivityStatus.State.ACTIVE, activity.status(id).join().state());
        assertFalse(activity.status(id).join().productionPaused());
        assertNotNull(activity.pauseIntervals(id).join().getFirst().untilTick());
    }

    @Test
    void legacyVisitorPolicyNameIsNormalizedWithoutChangingScoresOrCaps() {
        activity.contribute(List.of(action(owner, ActivityContribution.Category.BUILD))).join();
        var before = activity.status(id).join();
        String current = ActivityPolicy.defaults().toString();
        String legacy = current.replace("publicCredit=", "visitorCredit=");
        assertNotEquals(current, legacy);
        db.transact(
                        c -> {
                            assertEquals(
                                    1,
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .update(ACTIVITY_POLICY)
                                            .set(ACTIVITY_POLICY.DEFINITION, legacy)
                                            .where(ACTIVITY_POLICY.ID.eq(1L))
                                            .execute());
                            return null;
                        })
                .join();
        activity.initialize().join();
        activity.initialize().join();
        assertEquals(before, activity.status(id).join());
        assertEquals(
                current,
                db.transact(
                                c ->
                                        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                                .select(ACTIVITY_POLICY.DEFINITION)
                                                .from(ACTIVITY_POLICY)
                                                .where(ACTIVITY_POLICY.ID.eq(1L))
                                                .fetchSingle(ACTIVITY_POLICY.DEFINITION))
                        .join());
    }

    @Test
    void incompatiblePersistedPolicyIsRejectedWithoutOverwritingIt() {
        String incompatible = ActivityPolicy.defaults() + ";incompatible";
        db.transact(
                        c -> {
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .update(ACTIVITY_POLICY)
                                    .set(ACTIVITY_POLICY.DEFINITION, incompatible)
                                    .where(ACTIVITY_POLICY.ID.eq(1L))
                                    .execute();
                            return null;
                        })
                .join();
        var failure = assertThrows(CompletionException.class, () -> activity.initialize().join());
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals(
                incompatible,
                db.transact(
                                c ->
                                        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                                .select(ACTIVITY_POLICY.DEFINITION)
                                                .from(ACTIVITY_POLICY)
                                                .where(ACTIVITY_POLICY.ID.eq(1L))
                                                .fetchSingle(ACTIVITY_POLICY.DEFINITION))
                        .join());
        assertEquals(30, activity.status(id).join().score());
    }

    ActivityContribution atBlock(int x, int z) {
        var view =
                settlements.initialize().join().stream()
                        .filter(s -> s.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        return new ActivityContribution(
                id,
                world,
                owner,
                x,
                0,
                z,
                view.ownershipRevision(),
                view.chunkRevision(),
                0,
                ParcelAction.BUILD,
                false,
                null);
    }

    ActivityService localPolicy(double weight, double threshold) {
        return xdu.undarubra.esmp.civilization.api.ObservationServices.activity(
                db,
                clock,
                ActivityPolicy.defaults(),
                new LocalActivityPolicy(weight, threshold),
                xdu.undarubra.esmp.civilization.decay.DecayService.Policy.defaults(),
                ignored -> false);
    }

    @Test
    void localCreditUsesExactNegativeChunkAndOnlyCardinalSameSettlementNeighbors() {
        claim(owner, id, world, -1, 0);
        claim(owner, id, world, 1, 0);
        claim(owner, id, world, 1, 1);
        var other = create(owner, world, 0, -1).settlementId();
        activity.contribute(List.of(atBlock(0, 0))).join();
        var view = activity.localActivity(id).join();
        assertEquals(.25, view.support().get(new ChunkPosition(-1, 0)));
        assertEquals(0, view.support().get(new ChunkPosition(1, 1)));
        assertEquals(
                0, activity.localActivity(other).join().support().get(new ChunkPosition(0, -1)));
        advance(200);
        activity.contribute(List.of(atBlock(-1, 0))).join();
        assertEquals(
                1, activity.localActivity(id).join().rawScores().get(new ChunkPosition(-1, 0)));
        var disabled = localPolicy(0, 20).localActivity(id).join();
        assertEquals(disabled.rawScores(), disabled.support());
        assertThrows(IllegalArgumentException.class, () -> new LocalActivityPolicy(.251, 20));
    }

    @Test
    void footprintSupportIsAreaWeightedReadOnlyAndRejectsForeignOrUnclaimedGeometry() {
        claim(owner, id, world, 1, 0);
        activity.contribute(List.of(atBlock(0, 0))).join();
        var footprint = Map.of(new ChunkPosition(0, 0), 256, new ChunkPosition(1, 0), 64);
        assertEquals(.85, activity.localSupport(id, world, footprint, clock.ticks()).join(), 1e-9);
        assertEquals(
                .425,
                activity.localSupport(id, world, footprint, clock.ticks() + 168000).join(),
                1e-9);
        assertEquals(
                .85,
                db.transact(tx -> activity.localSupport(tx, id, world, footprint, clock.ticks()))
                        .join(),
                1e-9);
        assertEquals(1, activity.localActivity(id).join().rawScores().get(new ChunkPosition(0, 0)));
        assertThrows(
                CompletionException.class,
                () ->
                        activity.localSupport(id, UUID.randomUUID(), footprint, clock.ticks())
                                .join());
        assertThrows(
                CompletionException.class,
                () ->
                        activity.localSupport(
                                        id,
                                        world,
                                        Map.of(new ChunkPosition(2, 0), 1),
                                        clock.ticks())
                                .join());
        assertThrows(
                IllegalArgumentException.class,
                () -> activity.localSupport(id, world, Map.of(), clock.ticks()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        activity.localSupport(
                                id, world, Map.of(new ChunkPosition(0, 0), 257), clock.ticks()));
        advance(200);
        activity.contribute(List.of(atBlock(0, 0))).join();
        assertThrows(
                CompletionException.class,
                () -> activity.localSupport(id, world, footprint, 0).join());
    }

    @Test
    void regionsSplitWithLocalDecayAndMergeOnRecoveryWithoutReleasingClaims() {
        claim(owner, id, world, 1, 0);
        claim(owner, id, world, 2, 0);
        var local = localPolicy(0, .5);
        activity.contribute(List.of(atBlock(16, 0))).join();
        advance(200);
        activity.contribute(List.of(atBlock(0, 0))).join();
        advance(200);
        activity.contribute(List.of(atBlock(32, 0))).join();
        assertEquals(1, local.localActivity(id).join().regions().size());
        advance(168001 - clock.ticks());
        assertEquals(
                List.of(Set.of(new ChunkPosition(0, 0)), Set.of(new ChunkPosition(2, 0))),
                local.localActivity(id).join().regions());
        assertEquals(3, local.localActivity(id).join().rawScores().size());
        activity.contribute(List.of(atBlock(16, 0))).join();
        assertEquals(
                List.of(
                        Set.of(
                                new ChunkPosition(0, 0),
                                new ChunkPosition(1, 0),
                                new ChunkPosition(2, 0))),
                local.localActivity(id).join().regions());
    }

    @Test
    void unclaimAndAbandonCannotLeaveSupportForAReclaimedChunk() {
        claim(owner, id, world, 1, 0);
        activity.contribute(List.of(atBlock(16, 0))).join();
        settlements.unclaim(owner, id, new ChunkPosition(1, 0)).join();
        claim(owner, id, world, 1, 0);
        assertEquals(0, activity.localActivity(id).join().rawScores().get(new ChunkPosition(1, 0)));
        advance(200);
        activity.contribute(List.of(atBlock(0, 0))).join();
        var offer =
                settlements.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        settlements.accept(owner, offer.id(), true).join();
        assertEquals(
                0,
                db.transact(
                                tx ->
                                        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(tx)
                                                .fetchCount(
                                                        SETTLEMENT_LOCAL_ACTIVITY,
                                                        SETTLEMENT_LOCAL_ACTIVITY.SETTLEMENT_ID.eq(
                                                                id)))
                        .join());
        var next = create(owner, world, 0, 0).settlementId();
        assertEquals(
                0, activity.localActivity(next).join().rawScores().get(new ChunkPosition(0, 0)));
    }

    @Test
    void unsupportedBucketVersionRejectsCreditWithoutPartialAggregateAwards() {
        activity.contribute(List.of(atBlock(0, 0))).join();
        advance(200);
        var before = activity.status(id).join();
        db.transact(
                        tx ->
                                xdu.undarubra.esmp.core.storage.DatabaseAccess.context(tx)
                                        .update(SETTLEMENT_LOCAL_ACTIVITY)
                                        .set(SETTLEMENT_LOCAL_ACTIVITY.POLICY_VERSION, 2L)
                                        .execute())
                .join();
        var observation = atBlock(0, 0);
        assertThrows(
                CompletionException.class, () -> activity.contribute(List.of(observation)).join());
        assertEquals(before, activity.status(id).join());
    }
}
