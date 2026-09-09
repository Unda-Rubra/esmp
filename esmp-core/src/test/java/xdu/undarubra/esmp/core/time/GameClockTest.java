package xdu.undarubra.esmp.core.time;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.world.*;

class GameClockTest {
    @TempDir Path directory;
    @Test void forwardJumpsRewindsFreezeAndRestartNeverUseWallTime() {
        UUID source = UUID.randomUUID();
        try (var db = new WorldStore(directory.resolve("clock.db"))) {
            db.initialize(List.of(new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL)), List.of()).join();
            var clock = new GameClock(db); clock.initialize(source, 1000, true).join();
            clock.sample(source, 25000, true);
            assertEquals(24000, clock.ticks());
            clock.sample(source, 100, true);
            clock.sample(source, 200, true);
            assertEquals(24100, clock.ticks());
            clock.sample(source, 200, false);
            clock.sample(source, 99999, false);
            assertEquals(24100, clock.ticks());
            clock.sample(source, 99999, true);
            clock.sample(source, 100000, true);
            assertEquals(24101, clock.ticks());
            clock.flush().join();
        }
        try (var db = new WorldStore(directory.resolve("clock.db"))) {
            db.initialize(List.of(new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL)), List.of()).join();
            var clock = new GameClock(db); clock.initialize(source, 999999, true).join();
            assertEquals(24101, clock.ticks());
            clock.sample(source, 1000000, true);
            assertEquals(24102, clock.ticks());
            assertThrows(IllegalStateException.class, () -> clock.sample(UUID.randomUUID(), 1000001, true));
        }
    }
    @Test void equalElapsedGameTimeDoesNotDependOnSamplingFrequency() {
        UUID source = UUID.randomUUID();
        try (var db = new WorldStore(directory.resolve("pace.db"))) {
            db.initialize(List.of(new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL)), List.of()).join();
            var fine = new GameClock(db); fine.initialize(source, 0, true).join();
            var coarse = new GameClock(db); coarse.initialize(source, 0, true).join();
            for (long tick = 1; tick <= 24000; tick++) { fine.sample(source, tick, true); }
            coarse.sample(source, 24000, true);
            assertEquals(fine.ticks(), coarse.ticks());
        }
    }
}
