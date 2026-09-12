package xdu.undarubra.esmp.plugin.settlement;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.civilization.land.*;
import xdu.undarubra.esmp.core.storage.WorldStore;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Main-thread natural sampling plus a chunk-PDC recovery journal. Never samples old chunks. */
public final class LandValueRuntime implements Listener, AutoCloseable {
    private record Key(int x, int z) {}

    private static final int PENDING_LIMIT = 4096;
    private final JavaPlugin plugin;
    private final World main;
    private final WorldStore registry;
    private final LandValueService service;
    private final Consumer<Throwable> fatal;
    private final NamespacedKey journalKey;
    private final LinkedHashMap<Key, LandValueProfile> pending = new LinkedHashMap<>();
    private UUID instance;
    private BukkitTask task;
    private boolean writing, warned;
    private volatile boolean closed;

    public LandValueRuntime(
            JavaPlugin plugin,
            World main,
            WorldStore registry,
            LandValueService service,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.main = Objects.requireNonNull(main);
        this.registry = registry;
        this.service = service;
        this.fatal = fatal;
        journalKey = new NamespacedKey(plugin, "natural_land_value");
    }

    public void start() {
        instance =
                registry.snapshot().instances().stream()
                        .filter(
                                i ->
                                        i.definitionId().equals("MAIN")
                                                && i.bukkitWorldId().equals(main.getUID()))
                        .findFirst()
                        .orElseThrow()
                        .instanceId();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Recover only existing journals; never infer pristine terrain from loaded player builds.
        for (Chunk chunk : main.getLoadedChunks()) recover(chunk);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, 1, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void populated(ChunkPopulateEvent event) {
        if (closed || event.getWorld() != main) return;
        try {
            if (!Bukkit.isPrimaryThread())
                throw new IllegalStateException(
                        "Natural land capture requires the Paper main thread");
            var chunk = event.getChunk();
            var existing = journal(chunk);
            if (existing != null) {
                enqueue(existing);
                return;
            }
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, water = 0;
            for (int x = 2; x < 16; x += 4)
                for (int z = 2; z < 16; z += 4) {
                    var block =
                            main.getHighestBlockAt(
                                    chunk.getX() * 16 + x,
                                    chunk.getZ() * 16 + z,
                                    HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    min = Math.min(min, block.getY());
                    max = Math.max(max, block.getY());
                    if (block.getType() == Material.WATER
                            || block.getType() == Material.BUBBLE_COLUMN) water++;
                }
            var policy = service.policy();
            var profile =
                    new LandValueProfile(
                            instance,
                            chunk.getX(),
                            chunk.getZ(),
                            policy.version(),
                            policy.value(max - min, water),
                            policy.terrain(max - min, water));
            chunk.getPersistentDataContainer()
                    .set(
                            journalKey,
                            PersistentDataType.STRING,
                            "1;"
                                    + instance
                                    + ";"
                                    + chunk.getX()
                                    + ";"
                                    + chunk.getZ()
                                    + ";"
                                    + profile.valuationVersion()
                                    + ";"
                                    + profile.baseValue()
                                    + ";"
                                    + profile.terrain());
            enqueue(profile);
        } catch (RuntimeException failure) {
            fatal.accept(failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(ChunkLoadEvent event) {
        if (!closed && event.getWorld() == main) recover(event.getChunk());
    }

    private void recover(Chunk chunk) {
        try {
            var profile = journal(chunk);
            if (profile != null) enqueue(profile);
        } catch (RuntimeException failure) {
            fatal.accept(failure);
        }
    }

    private LandValueProfile journal(Chunk chunk) {
        String text = chunk.getPersistentDataContainer().get(journalKey, PersistentDataType.STRING);
        if (text == null) return null;
        var parts = text.split(";", -1);
        if (parts.length != 7 || !parts[0].equals("1"))
            throw new IllegalStateException("Unsupported natural land journal");
        var profile =
                new LandValueProfile(
                        UUID.fromString(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        Long.parseLong(parts[5]),
                        LandValueProfile.Terrain.valueOf(parts[6]));
        if (!profile.worldInstance().equals(instance)
                || profile.chunkX() != chunk.getX()
                || profile.chunkZ() != chunk.getZ()) {
            throw new IllegalStateException(
                    "Natural land journal belongs to another world instance or chunk");
        }
        return profile;
    }

    private void enqueue(LandValueProfile profile) {
        var key = new Key(profile.chunkX(), profile.chunkZ());
        if (pending.containsKey(key) || pending.size() < PENDING_LIMIT)
            pending.putIfAbsent(key, profile);
        else if (!warned) {
            warned = true;
            plugin.getLogger()
                    .warning(
                            "Land capture queue full; chunk journals will recover on load or"
                                + " inspection.");
        }
    }

    private void flush() {
        if (closed || writing || pending.isEmpty()) return;
        writing = true;
        var batch = pending.values().stream().limit(32).toList();
        complete(
                service.capture(batch),
                ignored -> {
                    writing = false;
                    for (var p : batch) pending.remove(new Key(p.chunkX(), p.chunkZ()), p);
                    if (pending.size() < PENDING_LIMIT) warned = false;
                },
                () -> writing = false);
    }

    public void inspect(Player player) {
        if (closed || instance == null) {
            player.sendMessage(Component.text("Land values are loading."));
            return;
        }
        if (player.getWorld() != main) {
            player.sendMessage(Component.text("Land values are only captured in the Main World."));
            return;
        }
        var chunk = player.getLocation().getChunk();
        int x = chunk.getX(), z = chunk.getZ();
        UUID viewer = player.getUniqueId();
        LandValueProfile marker;
        try {
            marker = journal(chunk);
        } catch (RuntimeException failure) {
            fatal.accept(failure);
            return;
        }
        CompletableFuture<LandValueProfile> result =
                marker == null
                        ? service.find(instance, x, z)
                        : service.capture(List.of(marker))
                                .thenCompose(ignored -> service.find(instance, x, z));
        complete(
                result,
                profile -> {
                    var online = plugin.getServer().getPlayer(viewer);
                    if (online == null) return;
                    online.sendMessage(
                            Component.text(
                                    profile == null
                                            ? "Land value ["
                                                    + x
                                                    + ","
                                                    + z
                                                    + "]: unknown (no generation capture). Existing"
                                                    + " terrain is not resampled."
                                            : "Land value ["
                                                    + x
                                                    + ","
                                                    + z
                                                    + "]: "
                                                    + profile.baseValue()
                                                    + " · "
                                                    + profile.terrain()
                                                    + " · valuation v"
                                                    + profile.valuationVersion()));
                },
                () -> {
                    var online = plugin.getServer().getPlayer(viewer);
                    if (online != null)
                        online.sendMessage(
                                Component.text("Land value storage is busy. Try again."));
                });
    }

    private <T> void complete(CompletableFuture<T> future, Consumer<T> success, Runnable rejected) {
        future.whenComplete(
                (value, failure) -> {
                    if (closed) return;
                    try {
                        plugin.getServer()
                                .getScheduler()
                                .runTask(
                                        plugin,
                                        () -> {
                                            if (closed) return;
                                            Throwable cause = failure;
                                            while (cause instanceof CompletionException
                                                    && cause.getCause() != null)
                                                cause = cause.getCause();
                                            if (cause == null) success.accept(value);
                                            else if (cause instanceof RejectedExecutionException)
                                                rejected.run();
                                            else fatal.accept(cause);
                                        });
                    } catch (IllegalPluginAccessException stopping) {
                        if (!closed)
                            plugin.getLogger()
                                    .warning("Land value feedback interrupted by shutdown");
                    }
                });
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) task.cancel();
        var remaining = List.copyOf(pending.values());
        for (int i = 0; i < remaining.size(); i += 32)
            service.capture(remaining.subList(i, Math.min(i + 32, remaining.size()))).join();
        pending.clear();
    }
}
