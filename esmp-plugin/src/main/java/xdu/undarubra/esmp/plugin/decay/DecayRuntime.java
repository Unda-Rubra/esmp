package xdu.undarubra.esmp.plugin.decay;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.decay.*;
import xdu.undarubra.esmp.civilization.decay.DecayService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.plugin.settlement.SettlementRuntime;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** One reserved, bounded batch at a time. SQL intent precedes every physical mutation. */
public final class DecayRuntime implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final DecayService service;
    private final Consumer<Throwable> fatal;

    private record ChunkKey(UUID world, int x, int z) {}

    private record Applied(boolean waiting, Outcome outcome, Map<Position, BuildingCell> actual) {}

    private final LinkedHashMap<ChunkKey, Long> recovery = new LinkedHashMap<>();
    private boolean busy, recoveryTurn;
    private volatile boolean closed;
    private BukkitTask task;

    public DecayRuntime(
            JavaPlugin plugin,
            SettlementRuntime runtime,
            DecayService service,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.service = service;
        this.fatal = fatal;
    }

    public CompletableFuture<Void> initialize() {
        return service.initialize();
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (var w : plugin.getServer().getWorlds())
            if (runtime.isMain(w)) for (var chunk : w.getLoadedChunks()) enqueue(chunk);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(ChunkLoadEvent event) {
        if (runtime.isMain(event.getWorld())) enqueue(event.getChunk());
    }

    private NamespacedKey marker(UUID building) {
        return new NamespacedKey(plugin, "decay/" + building);
    }

    private long saved(Chunk chunk, UUID building, long revision) {
        long[] data =
                chunk.getPersistentDataContainer()
                        .get(marker(building), PersistentDataType.LONG_ARRAY);
        if (data == null) return 0;
        if (data.length != 3 || data[0] != 1 || data[1] < 1 || data[2] < 1)
            throw new IllegalStateException("Invalid Decay world-save marker");
        return data[1] == revision ? data[2] : 0;
    }

    private void mark(Chunk chunk, Operation op) {
        long prior = saved(chunk, op.building(), op.baselineRevision());
        chunk.getPersistentDataContainer()
                .set(
                        marker(op.building()),
                        PersistentDataType.LONG_ARRAY,
                        new long[] {1, op.baselineRevision(), Math.max(prior, op.sequence())});
    }

    private void enqueue(Chunk chunk) {
        for (var key : new HashSet<>(chunk.getPersistentDataContainer().getKeys()))
            if (key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))
                    && key.getKey().startsWith("decay/")) {
                UUID id = UUID.fromString(key.getKey().substring(6));
                if (runtime.buildings().view(id) == null)
                    chunk.getPersistentDataContainer().remove(key);
            }
        if (runtime.buildings()
                .containsChunk(runtime.mainInstance(chunk.getWorld()), chunk.getX(), chunk.getZ()))
            recovery.putIfAbsent(
                    new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()), 0L);
    }

    private <T> CompletableFuture<T> main(Supplier<T> work) {
        var result = new CompletableFuture<T>();
        if (closed) {
            result.completeExceptionally(new CancellationException("Decay stopped"));
            return result;
        }
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (closed) {
                                result.completeExceptionally(
                                        new CancellationException("Decay stopped"));
                                return;
                            }
                            try {
                                result.complete(work.get());
                            } catch (Throwable failure) {
                                result.completeExceptionally(failure);
                            }
                        });
        return result;
    }

    private void fail(Throwable failure) {
        if (!closed) fatal.accept(failure);
    }

    private void tick() {
        if (closed || busy || !runtime.clock().advancing() || !runtime.buildingCommitAvailable())
            return;
        recoveryTurn = !recoveryTurn;
        if (recoveryTurn && !recovery.isEmpty()) {
            recover();
            return;
        }
        busy = true;
        service.due()
                .whenComplete(
                        (ids, error) ->
                                main(
                                        () -> {
                                            if (error != null) {
                                                busy = false;
                                                fail(error);
                                                return null;
                                            }
                                            for (var id : ids) {
                                                var v = runtime.buildings().view(id);
                                                if (v != null
                                                        && !recovering(v)
                                                        && !runtime.buildings()
                                                                .hasPending(
                                                                        v.publicationSettlement())
                                                        && !runtime.repair()
                                                                .reserved(v.publicationSettlement())
                                                        && runtime.buildingCommitAvailable()) {
                                                    automatic(v);
                                                    return null;
                                                }
                                            }
                                            busy = false;
                                            return null;
                                        }));
    }

    private boolean recovering(BuildingService.View view) {
        for (var key : recovery.keySet()) {
            var world = plugin.getServer().getWorld(key.world());
            if (world != null
                    && view.world().equals(runtime.mainInstance(world))
                    && view.bounds().footprint().containsKey(new ChunkPosition(key.x(), key.z())))
                return true;
        }
        return false;
    }

    private void automatic(BuildingService.View view) {
        try {
            runtime.mutateDecay(
                            view.publicationSettlement(),
                            () ->
                                    service.work(view.id())
                                            .thenCompose(
                                                    work -> {
                                                        if (work.pending() != null)
                                                            return execute(
                                                                    work.pending().id(),
                                                                    new AtomicBoolean());
                                                        if (work.offer() == null)
                                                            return service.defer(view.id(), false);
                                                        return main(() -> capture(work.offer()))
                                                                .thenCompose(
                                                                        changes -> {
                                                                            if (changes == null)
                                                                                return service
                                                                                        .defer(
                                                                                                view
                                                                                                        .id(),
                                                                                                false);
                                                                            if (changes.isEmpty())
                                                                                return service
                                                                                        .defer(
                                                                                                view
                                                                                                        .id(),
                                                                                                true);
                                                                            return service.prepare(
                                                                                            work
                                                                                                    .offer(),
                                                                                            changes)
                                                                                    .handle(
                                                                                            (op,
                                                                                                    error) -> {
                                                                                                if (error
                                                                                                        == null)
                                                                                                    return op;
                                                                                                Throwable
                                                                                                        cause =
                                                                                                                unwrap(
                                                                                                                        error);
                                                                                                if (cause
                                                                                                        instanceof
                                                                                                        SettlementException)
                                                                                                    return null;
                                                                                                throw new CompletionException(
                                                                                                        cause);
                                                                                            })
                                                                                    .thenCompose(
                                                                                            op ->
                                                                                                    op
                                                                                                                    == null
                                                                                                            ? service
                                                                                                                    .defer(
                                                                                                                            view
                                                                                                                                    .id(),
                                                                                                                            true)
                                                                                                            : execute(
                                                                                                                    op
                                                                                                                            .id(),
                                                                                                                    new AtomicBoolean()));
                                                                        });
                                                    }))
                    .whenComplete(
                            (ignored, error) -> {
                                busy = false;
                                if (error != null) fail(error);
                            });
        } catch (SettlementException retry) {
            busy = false;
        }
    }

    private void recover() {
        var key = recovery.keySet().iterator().next();
        World world = plugin.getServer().getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x(), key.z())) {
            recovery.remove(key);
            return;
        }
        long cursor = recovery.get(key);
        busy = true;
        service.chunk(runtime.mainInstance(world), key.x(), key.z(), cursor)
                .whenComplete(
                        (rows, error) ->
                                main(
                                        () -> {
                                            if (error != null) {
                                                busy = false;
                                                fail(error);
                                                return null;
                                            }
                                            if (!world.isChunkLoaded(key.x(), key.z())) {
                                                recovery.remove(key);
                                                busy = false;
                                                return null;
                                            }
                                            Chunk chunk = world.getChunkAt(key.x(), key.z());
                                            for (var op : rows) {
                                                var view = runtime.buildings().view(op.building());
                                                if (view == null
                                                        || (view.revision() == op.baselineRevision()
                                                                && op.state().equals("APPLIED")
                                                                && saved(
                                                                                chunk,
                                                                                op.building(),
                                                                                op
                                                                                        .baselineRevision())
                                                                        >= op.sequence())) {
                                                    recovery.put(key, op.sequence());
                                                    continue;
                                                }
                                                if (!runtime.buildingCommitAvailable()
                                                        || runtime.buildings()
                                                                .hasPending(op.settlement())) {
                                                    long retained = recovery.remove(key);
                                                    recovery.put(key, retained);
                                                    busy = false;
                                                    return null;
                                                }
                                                var waiting = new AtomicBoolean();
                                                try {
                                                    runtime.mutateDecay(
                                                                    op.settlement(),
                                                                    () -> execute(op.id(), waiting))
                                                            .whenComplete(
                                                                    (ignored, failure) -> {
                                                                        busy = false;
                                                                        if (failure != null) {
                                                                            fail(failure);
                                                                            return;
                                                                        }
                                                                        if (!waiting.get())
                                                                            recovery.put(
                                                                                    key,
                                                                                    op.sequence());
                                                                        else {
                                                                            long retained =
                                                                                    recovery.remove(
                                                                                            key);
                                                                            recovery.put(
                                                                                    key, retained);
                                                                        }
                                                                    });
                                                } catch (SettlementException retry) {
                                                    long retained = recovery.remove(key);
                                                    recovery.put(key, retained);
                                                    busy = false;
                                                }
                                                return null;
                                            }
                                            if (rows.size() < 32) recovery.remove(key);
                                            busy = false;
                                            return null;
                                        }));
    }

    private CompletableFuture<SettlementChange> execute(UUID id, AtomicBoolean waiting) {
        return service.authorize(id)
                .thenCompose(
                        permit ->
                                main(() -> apply(permit))
                                        .thenCompose(
                                                result -> {
                                                    waiting.set(result.waiting());
                                                    return result.waiting()
                                                            ? service.defer(
                                                                    permit.operation().building(),
                                                                    false)
                                                            : service.finish(
                                                                    id,
                                                                    result.outcome(),
                                                                    result.actual());
                                                }));
    }

    private World world(UUID logical) {
        for (var world : plugin.getServer().getWorlds())
            if (logical.equals(runtime.mainInstance(world))) return world;
        return null;
    }

    private static BuildingCell cell(Block block) {
        var state = block.getState(false);
        var data = state.getBlockData();
        var type = data.getMaterial();
        return new BuildingCell(
                data.getAsString(),
                type.isAir(),
                !type.isAir() && type.isSolid(),
                data instanceof Openable,
                state instanceof TileState);
    }

    private BuildingCell loadedCell(World world, Position p) {
        if (p.y() < world.getMinHeight()
                || p.y() >= world.getMaxHeight()
                || !world.isChunkLoaded(p.x() >> 4, p.z() >> 4)) return null;
        return cell(world.getBlockAt(p.x(), p.y(), p.z()));
    }

    // 0: unloaded; 1: safe; -1: attachment/fluid/gravity/entity hazard. No physics cascade is
    // started.
    private int safeAround(World world, Position p) {
        for (int[] d :
                new int[][] {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}}) {
            long x = (long) p.x() + d[0], y = (long) p.y() + d[1], z = (long) p.z() + d[2];
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
            if (x < Integer.MIN_VALUE
                    || x > Integer.MAX_VALUE
                    || z < Integer.MIN_VALUE
                    || z > Integer.MAX_VALUE) return -1;
            var n = new Position((int) x, (int) y, (int) z);
            var neighbor = loadedCell(world, n);
            if (neighbor == null) return 0;
            if (!DecayPlan.safeNeighbor(neighbor)) return -1;
        }
        return 1;
    }

    private List<Change> capture(Offer offer) {
        World world = world(offer.building().world());
        if (world == null) return null;
        var samples = new HashMap<Position, BuildingCell>();
        boolean loaded = false;
        for (var p : offer.candidates()) {
            var source = loadedCell(world, p);
            if (source == null) continue;
            loaded = true;
            var below = new Position(p.x(), p.y() - 1, p.z());
            if (safeAround(world, p) != 1) continue;
            if (offer.kind() != Kind.WEATHER && safeAround(world, below) != 1) continue;
            samples.put(p, source);
            if (offer.baseline().bounds().contains(below)) {
                var target = loadedCell(world, below);
                if (target != null) samples.put(below, target);
            }
        }
        return !loaded && !offer.candidates().isEmpty() ? null : DecayPlan.plan(offer, samples);
    }

    private Applied apply(Permit permit) {
        Operation op = permit.operation();
        var view = runtime.buildings().view(op.building());
        if (view == null || !view.world().equals(op.world()))
            return new Applied(false, Outcome.CANCELLED, Map.of());
        World world = world(op.world());
        if (world == null || !world.isChunkLoaded(op.chunkX(), op.chunkZ()))
            return new Applied(true, null, Map.of());
        var actual = new LinkedHashMap<Position, BuildingCell>();
        for (var change : op.changes()) {
            var p = change.position();
            if ((p.x() >> 4) != op.chunkX() || (p.z() >> 4) != op.chunkZ())
                throw new IllegalStateException("Decay intent crosses its chunk");
            if (!view.bounds().contains(p)) {
                if (view.revision() != op.baselineRevision()) continue;
                throw new IllegalStateException("Decay intent crosses its Building");
            }
            var current = loadedCell(world, p);
            if (current == null) return new Applied(true, null, Map.of());
            actual.put(p, current);
        }
        if (view.revision() != op.baselineRevision())
            return new Applied(false, Outcome.CANCELLED, actual);
        Chunk chunk = world.getChunkAt(op.chunkX(), op.chunkZ());
        boolean allTarget =
                op.changes().stream()
                        .allMatch(c -> actual.get(c.position()).data().equals(c.target()));
        if (saved(chunk, op.building(), op.baselineRevision()) >= op.sequence() || allTarget) {
            if (allTarget) mark(chunk, op);
            return new Applied(false, Outcome.APPLIED, actual);
        }
        if (!permit.valid() || !runtime.clock().advancing())
            return new Applied(false, Outcome.CANCELLED, actual);
        if (view.settlement() == null) {
            if (!op.owner().equals(DecayService.SYSTEM_ACTOR)
                    || op.ownershipRevision() != view.ownershipRevision()
                    || op.claimRevision() != 0
                    || op.securityRevision() != 0)
                return new Applied(false, Outcome.CANCELLED, actual);
            for (var key : view.bounds().footprint().keySet())
                if (runtime.index().at(view.world(), key.x(), key.z()) != null)
                    return new Applied(false, Outcome.CANCELLED, actual);
        } else {
            var settlement = runtime.index().byId(view.settlement());
            var land = runtime.land(view.settlement());
            if (settlement == null
                    || land == null
                    || !view.settlement().equals(op.settlement())
                    || !settlement.owner().equals(op.owner())
                    || settlement.ownershipRevision() != op.ownershipRevision()
                    || settlement.chunkRevision() != op.claimRevision()
                    || land.securityRevision() != op.securityRevision())
                return new Applied(false, Outcome.CANCELLED, actual);
        }
        if (runtime.buildings().hasPending(op.settlement()))
            return new Applied(true, null, Map.of());
        for (var change : op.changes()) {
            var p = change.position();
            var current = actual.get(p);
            if (current.blockEntity()
                    || !view.permitsAutomaticMutation(p, current)
                    || (!current.data().equals(change.expected())
                            && !current.data().equals(change.target())))
                return new Applied(false, Outcome.CONFLICT, actual);
            int neighbors = safeAround(world, p);
            if (neighbors == 0) return new Applied(true, null, Map.of());
            if (neighbors < 0) return new Applied(false, Outcome.CONFLICT, actual);
            if (!change.target().equals("minecraft:air")
                    && current.air()
                    && !world.getNearbyEntities(
                                    new BoundingBox(
                                            p.x() + .001,
                                            p.y() + .001,
                                            p.z() + .001,
                                            p.x() + .999,
                                            p.y() + .999,
                                            p.z() + .999))
                            .isEmpty()) return new Applied(true, null, Map.of());
        }
        // Destinations precede source removals. Partial before/target mixtures are replay-safe.
        for (var change : op.changes())
            if (!actual.get(change.position()).data().equals(change.target())) {
                var p = change.position();
                world.getBlockAt(p.x(), p.y(), p.z())
                        .setBlockData(Bukkit.createBlockData(change.target()), false);
            }
        mark(chunk, op);
        for (var p : actual.keySet()) actual.put(p, cell(world.getBlockAt(p.x(), p.y(), p.z())));
        return new Applied(false, Outcome.APPLIED, actual);
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null)
            failure = failure.getCause();
        return failure;
    }

    public void inspect(Player player, String[] args) {
        if (args.length != 1)
            throw new SettlementException("Usage: /settlement decay <buildingUUID>");
        UUID id = UUID.fromString(args[0]);
        runtime.query(
                player,
                () -> service.status(id),
                state ->
                        player.sendMessage(
                                Component.text(
                                        "Decay · "
                                                + state.condition()
                                                + " · support "
                                                + String.format(
                                                        Locale.ROOT, "%.3f", state.support())
                                                + " · inactive since "
                                                + state.inactiveSince()
                                                + " · eligible "
                                                + state.eligible()
                                                + " · damaged "
                                                + state.damagedCells()
                                                + " · lost "
                                                + state.lostCells()
                                                + " · next check "
                                                + state.nextCheck())));
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) task.cancel();
        recovery.clear();
    }
}
