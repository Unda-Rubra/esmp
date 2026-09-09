package xdu.undarubra.esmp.civilization.activity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.*;
import xdu.undarubra.esmp.civilization.settlement.*;

class ActivityServiceTest {
    @TempDir Path directory;
    WorldStore db;
    GameClock clock;
    SettlementService settlements;
    ActivityService activity;
    UUID source = UUID.randomUUID(), owner = UUID.randomUUID(), world, id;
    long raw;

    @BeforeEach void open() {
        db = new WorldStore(directory.resolve("activity.db"));
        world = db.initialize(List.of(new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL)),
                List.of(new WorldBinding("MAIN", source, "world"))).join().instances().getFirst().instanceId();
        clock = new GameClock(db); clock.initialize(source, 0, true).join();
        settlements = new SettlementService(db, SettlementPolicy.defaults(), clock); settlements.initialize().join();
        activity = new ActivityService(db, clock, ActivityPolicy.defaults()); activity.initialize().join();
        id = settlements.create(owner, world, new ChunkPosition(0, 0), "Home").join().settlementId();
    }
    @AfterEach void close() { db.close(); }
    void advance(long ticks) { raw += ticks; clock.sample(source, raw, true); }
    ActivityContribution action(UUID player, ActivityContribution.Category category) {
        return new ActivityContribution(id, world, player, 0, 0, 1, 1, category);
    }
    @Test void lazyDecayStartsAtFoundingAndHasExactGraceBoundary() {
        advance(7 * 24000);
        var state = activity.status(id).join();
        assertEquals(15, state.score(), 1e-9);
        assertEquals(ActivityStatus.State.INACTIVE, state.state());
        long crossing = (long)Math.floor(7 * 24000 * Math.log(1.5) / Math.log(2)) + 1;
        assertEquals(crossing, state.inactiveSinceTick());
        assertFalse(state.productionPaused());
        advance(crossing + 72000 - clock.ticks());
        assertTrue(activity.status(id).join().productionPaused());
        assertEquals(crossing + 72000, activity.pauseIntervals(id).join().getFirst().fromTick());
        var other = settlements.create(owner, world, new ChunkPosition(1, 0), "Other").join().settlementId();
        assertEquals(30, activity.status(other).join().score(), 1e-9);
    }
    @Test void freezeAndRestartPreserveScoresAndPersistedCaps() {
        var build = action(owner, ActivityContribution.Category.BUILD);
        activity.contribute(List.of(build, build, build)).join();
        assertEquals(1, activity.status(id).join().creditedToday());
        clock.sample(source, raw, false);
        clock.sample(source, raw + 24000, false); raw += 24000;
        activity.contribute(List.of(build)).join();
        assertEquals(31, activity.status(id).join().score());
        clock.flush().join();
        db.close();
        db = new WorldStore(directory.resolve("activity.db"));
        db.initialize(List.of(new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL)), List.of()).join();
        clock = new GameClock(db); clock.initialize(source, raw + 1_000_000, true).join();
        activity = new ActivityService(db, clock, ActivityPolicy.defaults()); activity.initialize().join();
        activity.contribute(List.of(build)).join();
        assertEquals(31, activity.status(id).join().score());
        assertEquals(1, activity.status(id).join().creditedToday());
    }
    @Test void roleAndRevisionRevalidationRejectsStaleOrUnauthorizedCredit() {
        UUID visitor = UUID.randomUUID();
        activity.contribute(List.of(action(visitor, ActivityContribution.Category.BUILD))).join();
        assertEquals(30, activity.status(id).join().score());
        activity.contribute(List.of(action(visitor, ActivityContribution.Category.INTERACTION))).join();
        assertEquals(30.02, activity.status(id).join().score(), 1e-9);
        settlements.claim(owner, id, world, new ChunkPosition(1, 0)).join();
        activity.contribute(List.of(action(owner, ActivityContribution.Category.BUILD))).join();
        assertEquals(.02, activity.status(id).join().creditedToday(), 1e-9);
    }
    @Test void dailyCapAndRecoveryPreserveClosedPauseIntervals() {
        advance(8 * 24000);
        assertTrue(activity.status(id).join().productionPaused());
        for (int day = 0; day < 10 && activity.status(id).join().state() == ActivityStatus.State.INACTIVE; day++) {
            for (int n = 0; n < 10; n++) {
                activity.contribute(List.of(action(owner, ActivityContribution.Category.BUILD))).join();
                advance(200);
            }
            assertTrue(activity.status(id).join().creditedToday() <= 5);
            if (activity.status(id).join().state() == ActivityStatus.State.INACTIVE) { advance(24000 - raw % 24000); }
        }
        assertEquals(ActivityStatus.State.ACTIVE, activity.status(id).join().state());
        assertFalse(activity.status(id).join().productionPaused());
        assertNotNull(activity.pauseIntervals(id).join().getFirst().untilTick());
    }
}
