package xdu.undarubra.esmp.plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xdu.undarubra.esmp.core.time.GameClock;

/** Main-thread adapter: never counts wall time or scheduler callbacks as game time. */
public final class GameTimeRuntime implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final World source;
    private final java.util.UUID sourceId;
    private final long initialRaw;
    private final boolean initialAdvancing;
    private final GameClock clock;
    private final Consumer<Throwable> fatal;
    private BukkitTask task;
    private boolean started;
    private boolean flushing;
    private long checkpointAt;

    public GameTimeRuntime(JavaPlugin plugin, World source, GameClock clock, Consumer<Throwable> fatal) {
        if (source == null) { throw new IllegalArgumentException("Configured MAIN world must be loaded to supply the reckoning clock"); }
        this.plugin = plugin; this.source = source; this.clock = clock; this.fatal = fatal;
        sourceId = source.getUID();
        initialRaw = source.getFullTime();
        initialAdvancing = advances();
    }
    public CompletableFuture<Void> initialize() {
        return clock.initialize(sourceId, initialRaw, initialAdvancing);
    }
    public void start() {
        started = true;
        checkpointAt = clock.ticks();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            sample();
            if (!flushing && clock.ticks() - checkpointAt >= 200) {
                flushing = true;
                checkpointAt = clock.ticks();
                clock.flush().whenComplete((ignored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    flushing = false;
                    if (failure != null) { fatal.accept(failure); }
                }));
            }
        }, 1, 1);
    }
    public void sample() {
        if (!started) { return; }
        try { clock.sample(source.getUID(), source.getFullTime(), advances()); }
        catch (RuntimeException failure) { fatal.accept(failure); }
    }
    private boolean advances() { return Boolean.TRUE.equals(source.getGameRuleValue(GameRules.ADVANCE_TIME)); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClockSkip(ClockTimeSkipEvent event) { skip(event); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldSkip(TimeSkipEvent event) {
        if (event.getWorld().getUID().equals(source.getUID())) { skip(event); }
    }
    private void skip(ClockTimeSkipEvent event) {
        if (!started) { return; }
        try {
            boolean advancing = advances();
            long raw = source.getFullTime();
            clock.sample(source.getUID(), raw, advancing);
            clock.sample(source.getUID(), Math.addExact(raw, event.getSkipAmount()), advancing);
        } catch (RuntimeException failure) { fatal.accept(failure); }
    }
    @Override public void close() {
        sample();
        started = false;
        if (task != null) { task.cancel(); }
        clock.flush().join();
    }
}
