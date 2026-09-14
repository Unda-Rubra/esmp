package xdu.undarubra.esmp.plugin.repair;

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
import xdu.undarubra.esmp.civilization.decay.DecayPlan;
import xdu.undarubra.esmp.civilization.decay.DecayService.Change;
import xdu.undarubra.esmp.civilization.repair.*;
import xdu.undarubra.esmp.civilization.repair.RepairService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.plugin.settlement.SettlementRuntime;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/**
 * Material-verified, whole-Building repair with per-cell conflict retirement and saved-world
 * replay.
 */
public final class RepairRuntime implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final RepairService service;
    private final RepairInventory inventory;
    private final Consumer<Throwable> fatal;

    private record Key(UUID world, int x, int z) {}

    private record Result(
            boolean waiting,
            boolean attempted,
            Map<Position, BuildingCell> actual,
            Set<Position> conflicts) {}

    private final LinkedHashMap<Key, Long> recovery = new LinkedHashMap<>();
    private Set<UUID> reserved = Set.of();
    private boolean busy, turn;
    private volatile boolean closed;
    private BukkitTask task;

    public RepairRuntime(
            JavaPlugin plugin,
            SettlementRuntime runtime,
            RepairService service,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.service = service;
        this.fatal = fatal;
        inventory = new RepairInventory(plugin, runtime, service, fatal);
    }

    public CompletableFuture<Void> initialize() {
        return service.initialize()
                .thenCompose(ignored -> service.reservations())
                .thenAccept(values -> reserved = values);
    }

    public boolean reserved(UUID settlement) {
        return settlement != null && reserved.contains(settlement);
    }

    public void start() {
        inventory.start();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (var world : plugin.getServer().getWorlds())
            if (runtime.isMain(world)) for (var chunk : world.getLoadedChunks()) enqueue(chunk);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(ChunkLoadEvent event) {
        if (runtime.isMain(event.getWorld())) enqueue(event.getChunk());
    }

    private void enqueue(Chunk chunk) {
        if (runtime.buildings()
                .containsChunk(runtime.mainInstance(chunk.getWorld()), chunk.getX(), chunk.getZ()))
            recovery.putIfAbsent(
                    new Key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()), 0L);
    }

    private NamespacedKey key(UUID building) {
        return new NamespacedKey(plugin, "repair/" + building);
    }

    private long marker(Chunk chunk, UUID building) {
        long[] data =
                chunk.getPersistentDataContainer()
                        .get(key(building), PersistentDataType.LONG_ARRAY);
        if (data == null) return 0;
        if (data.length != 2 || data[0] != 1 || data[1] < 1)
            throw new IllegalStateException("Invalid Repair world receipt");
        return data[1];
    }

    private void mark(Chunk chunk, Operation op) {
        chunk.getPersistentDataContainer()
                .set(
                        key(op.building()),
                        PersistentDataType.LONG_ARRAY,
                        new long[] {1, Math.max(marker(chunk, op.building()), op.sequence())});
    }

    private <T> CompletableFuture<T> main(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        if (closed) {
            result.completeExceptionally(new CancellationException());
            return result;
        }
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (closed) {
                                result.completeExceptionally(new CancellationException());
                                return;
                            }
                            try {
                                result.complete(action.get());
                            } catch (Throwable error) {
                                result.completeExceptionally(error);
                            }
                        });
        return result;
    }

    private CompletableFuture<SettlementChange> published(
            CompletableFuture<SettlementChange> change) {
        return change.thenCompose(
                value ->
                        service.reservations()
                                .thenCompose(
                                        values ->
                                                main(
                                                        () -> {
                                                            reserved = values;
                                                            return value;
                                                        })));
    }

    private World world(UUID logical) {
        for (var w : plugin.getServer().getWorlds())
            if (logical.equals(runtime.mainInstance(w))) return w;
        return null;
    }

    private static BuildingCell cell(Block block) {
        var state = block.getState(false);
        var data = state.getBlockData();
        return new BuildingCell(
                data.getAsString(),
                data.getMaterial().isAir(),
                !data.getMaterial().isAir() && data.getMaterial().isSolid(),
                data instanceof Openable,
                state instanceof TileState);
    }

    private BuildingCell read(World world, Position p) {
        return p.y() < world.getMinHeight()
                        || p.y() >= world.getMaxHeight()
                        || !world.isChunkLoaded(p.x() >> 4, p.z() >> 4)
                ? null
                : cell(world.getBlockAt(p.x(), p.y(), p.z()));
    }

    private int safe(World world, Position p) {
        for (int[] d :
                new int[][] {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}}) {
            long x = (long) p.x() + d[0], y = (long) p.y() + d[1], z = (long) p.z() + d[2];
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
            if (x < Integer.MIN_VALUE
                    || x > Integer.MAX_VALUE
                    || z < Integer.MIN_VALUE
                    || z > Integer.MAX_VALUE) return -1;
            var value = read(world, new Position((int) x, (int) y, (int) z));
            if (value == null) return 0;
            if (!DecayPlan.safeNeighbor(value)) return -1;
        }
        return 1;
    }

    private boolean station(World world, Job job) {
        var v = runtime.buildings().view(job.building());
        var p = job.station();
        return v != null
                && Objects.equals(v.station(), p)
                && world.isChunkLoaded(p.x() >> 4, p.z() >> 4)
                && world.getBlockAt(p.x(), p.y(), p.z()).getType() == Material.CRAFTING_TABLE;
    }

    private boolean recovering(Job job) {
        var view = runtime.buildings().view(job.building());
        if (view == null) return false;
        for (var k : recovery.keySet()) {
            var world = plugin.getServer().getWorld(k.world());
            if (world != null
                    && job.world().equals(runtime.mainInstance(world))
                    && view.bounds().footprint().containsKey(new ChunkPosition(k.x(), k.z())))
                return true;
        }
        return false;
    }

    private void tick() {
        if (closed || busy || !runtime.clock().advancing() || !runtime.buildingCommitAvailable())
            return;
        turn = !turn;
        if (turn && !recovery.isEmpty()) {
            recover();
            return;
        }
        busy = true;
        service.due()
                .whenComplete(
                        (jobs, error) ->
                                main(
                                        () -> {
                                            if (error != null) {
                                                busy = false;
                                                fatal.accept(error);
                                                return null;
                                            }
                                            for (var job : jobs) {
                                                if (runtime.buildings().hasPending(job.settlement())
                                                        || recovering(job)) continue;
                                                try {
                                                    runtime.mutateRepair(
                                                                    job.payer(),
                                                                    job.settlement(),
                                                                    () ->
                                                                            main(() ->
                                                                                            inventory
                                                                                                    .verified(
                                                                                                            job
                                                                                                                    .payer()))
                                                                                    .thenCompose(
                                                                                            verified ->
                                                                                                    verified
                                                                                                            ? run(
                                                                                                                    job
                                                                                                                            .id())
                                                                                                            : service
                                                                                                                    .defer(
                                                                                                                            job
                                                                                                                                    .id())))
                                                            .whenComplete(
                                                                    (ignored, failure) -> {
                                                                        busy = false;
                                                                        if (failure != null
                                                                                && !closed)
                                                                            fatal.accept(failure);
                                                                    });
                                                    return null;
                                                } catch (SettlementException retry) {
                                                    break;
                                                }
                                            }
                                            busy = false;
                                            return null;
                                        }));
    }

    private CompletableFuture<SettlementChange> run(UUID job) {
        return published(
                service.work(job)
                        .thenCompose(
                                work -> {
                                    if (work.pending() != null)
                                        return execute(work.pending().id(), new AtomicBoolean());
                                    if (work.offer() == null)
                                        return CompletableFuture.completedFuture(work.current());
                                    return main(() -> capture(work.offer()))
                                            .thenCompose(
                                                    cells -> {
                                                        if (cells == null)
                                                            return service.defer(job);
                                                        return service.intent(work.offer(), cells)
                                                                .thenCompose(
                                                                        op ->
                                                                                main(
                                                                                        () -> {
                                                                                            var
                                                                                                    copy =
                                                                                                            new HashSet<>(
                                                                                                                    reserved);
                                                                                            copy
                                                                                                    .add(
                                                                                                            op
                                                                                                                    .settlement());
                                                                                            reserved =
                                                                                                    Set
                                                                                                            .copyOf(
                                                                                                                    copy);
                                                                                            return op;
                                                                                        }))
                                                                .thenCompose(
                                                                        op ->
                                                                                execute(
                                                                                        op.id(),
                                                                                        new AtomicBoolean()));
                                                    });
                                }));
    }

    private List<Change> capture(Offer offer) {
        World world = world(offer.job().world());
        if (world == null || !station(world, offer.job())) return null;
        var result = new ArrayList<Change>();
        for (var planned : offer.cells()) {
            var value = read(world, planned.position());
            if (value == null) return null;
            result.add(
                    new Change(
                            planned.position(),
                            value.data().equals(planned.target())
                                    ? planned.target()
                                    : planned.expected(),
                            planned.target()));
        }
        return List.copyOf(result);
    }

    private CompletableFuture<SettlementChange> execute(UUID id, AtomicBoolean waiting) {
        return service.authorize(id)
                .thenCompose(
                        permit ->
                                service.job(permit.operation().job())
                                        .thenCompose(
                                                job ->
                                                        main(() -> apply(permit, job))
                                                                .thenCompose(
                                                                        result -> {
                                                                            waiting.set(
                                                                                    result
                                                                                            .waiting());
                                                                            return result.waiting()
                                                                                    ? service.defer(
                                                                                            job
                                                                                                    .id())
                                                                                    : service
                                                                                            .finish(
                                                                                                    id,
                                                                                                    result
                                                                                                            .actual(),
                                                                                                    result
                                                                                                            .attempted(),
                                                                                                    result
                                                                                                            .conflicts());
                                                                        })));
    }

    private Result apply(Permit permit, Job job) {
        var op = permit.operation();
        World world = world(op.world());
        if (world == null || !world.isChunkLoaded(op.chunkX(), op.chunkZ()))
            return new Result(true, false, Map.of(), Set.of());
        var chunk = world.getChunkAt(op.chunkX(), op.chunkZ());
        var actual = new LinkedHashMap<Position, BuildingCell>();
        for (var change : op.cells()) {
            var p = change.position();
            if ((p.x() >> 4) != op.chunkX() || (p.z() >> 4) != op.chunkZ())
                throw new IllegalStateException("Repair batch crosses its journal chunk");
            var value = read(world, p);
            if (value == null) return new Result(true, false, Map.of(), Set.of());
            actual.put(p, value);
        }
        if (marker(chunk, op.building()) >= op.sequence())
            return new Result(false, true, actual, Set.of());
        if (!inventory.verified(op.actor())) return new Result(true, false, Map.of(), Set.of());
        if (!permit.valid() || !runtime.clock().advancing())
            return new Result(false, false, actual, Set.of());
        if (!station(world, job) && op.state().equals("PREPARED"))
            return new Result(true, false, Map.of(), Set.of());
        var building = runtime.buildings().view(op.building());
        if (building == null) return new Result(false, false, actual, Set.of());
        var conflicts = new HashSet<Position>();
        var apply = new ArrayList<Change>();
        for (var change : op.cells()) {
            var p = change.position();
            if (!permit.cells().contains(p)) continue;
            var value = actual.get(p);
            if (value.data().equals(change.target())) continue;
            if (value.blockEntity()
                    || !building.permitsAutomaticMutation(p, value)
                    || !value.data().equals(change.expected())) {
                conflicts.add(p);
                continue;
            }
            int neighbors = safe(world, p);
            if (neighbors == 0) return new Result(true, false, Map.of(), Set.of());
            if (neighbors < 0) {
                conflicts.add(p);
                continue;
            }
            if (!change.target().equals("minecraft:air")
                    && value.air()
                    && !world.getNearbyEntities(
                                    new BoundingBox(
                                            p.x() + .001,
                                            p.y() + .001,
                                            p.z() + .001,
                                            p.x() + .999,
                                            p.y() + .999,
                                            p.z() + .999))
                            .isEmpty()) continue;
            RepairMaterials.material(change.target());
            apply.add(change);
        }
        for (var change : apply) {
            var p = change.position();
            world.getBlockAt(p.x(), p.y(), p.z())
                    .setBlockData(Bukkit.createBlockData(change.target()), false);
        }
        mark(chunk, op);
        for (var p : actual.keySet()) actual.put(p, cell(world.getBlockAt(p.x(), p.y(), p.z())));
        return new Result(false, true, actual, Set.copyOf(conflicts));
    }

    private void recover() {
        var k = recovery.keySet().iterator().next();
        World world = plugin.getServer().getWorld(k.world());
        if (world == null || !world.isChunkLoaded(k.x(), k.z())) {
            recovery.remove(k);
            return;
        }
        busy = true;
        service.chunk(runtime.mainInstance(world), k.x(), k.z(), recovery.get(k))
                .whenComplete(
                        (ops, error) ->
                                main(
                                        () -> {
                                            if (error != null) {
                                                busy = false;
                                                fatal.accept(error);
                                                return null;
                                            }
                                            if (!world.isChunkLoaded(k.x(), k.z())) {
                                                recovery.remove(k);
                                                busy = false;
                                                return null;
                                            }
                                            var chunk = world.getChunkAt(k.x(), k.z());
                                            for (var op : ops) {
                                                if (op.state().equals("RESOLVED")
                                                        && marker(chunk, op.building())
                                                                >= op.sequence()) {
                                                    recovery.put(k, op.sequence());
                                                    continue;
                                                }
                                                if (!inventory.verified(op.actor())
                                                        || !runtime.buildingCommitAvailable()
                                                        || runtime.buildings()
                                                                .hasPending(op.settlement())) {
                                                    long cursor = recovery.remove(k);
                                                    recovery.put(k, cursor);
                                                    busy = false;
                                                    return null;
                                                }
                                                var waiting = new AtomicBoolean();
                                                try {
                                                    runtime.mutateRepair(
                                                                    op.actor(),
                                                                    op.settlement(),
                                                                    () ->
                                                                            published(
                                                                                    execute(
                                                                                            op.id(),
                                                                                            waiting)))
                                                            .whenComplete(
                                                                    (ignored, failure) -> {
                                                                        busy = false;
                                                                        if (failure != null
                                                                                && !closed) {
                                                                            fatal.accept(failure);
                                                                            return;
                                                                        }
                                                                        if (waiting.get()) {
                                                                            long cursor =
                                                                                    recovery.remove(
                                                                                            k);
                                                                            recovery.put(k, cursor);
                                                                        } else
                                                                            recovery.put(
                                                                                    k,
                                                                                    op.sequence());
                                                                    });
                                                } catch (SettlementException retry) {
                                                    busy = false;
                                                }
                                                return null;
                                            }
                                            if (ops.size() < 32) recovery.remove(k);
                                            busy = false;
                                            return null;
                                        }));
    }

    public void command(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String text :
                    List.of(
                            "inspect <buildingUUID> | jobs <buildingUUID>",
                            "prepare <buildingUUID>",
                            "supply <jobUUID> | start <jobUUID>",
                            "status <jobUUID> | cancel <jobUUID> | refund <jobUUID>"))
                player.sendMessage(Component.text("/settlement repair " + text));
            return;
        }
        if (args.length != 2) throw new SettlementException("Use /settlement repair help");
        UUID actor = player.getUniqueId(), id = UUID.fromString(args[1]);
        runtime.rememberAdministrator(actor);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "inspect" ->
                    runtime.query(
                            player,
                            () -> service.inspect(id),
                            quote -> {
                                player.sendMessage(
                                        Component.text(
                                                "Repair · "
                                                        + quote.condition()
                                                        + " · Era "
                                                        + quote.era()
                                                        + " · "
                                                        + quote.cells()
                                                        + " affected cells"));
                                quote.materials()
                                        .forEach(
                                                (material, amount) ->
                                                        player.sendMessage(
                                                                Component.text(
                                                                        material + ": " + amount)));
                            });
            case "prepare" -> {
                var view = runtime.buildings().view(id);
                if (view == null || view.station() == null)
                    throw new SettlementException("Active Building needs a Repair Station");
                var point = view.station();
                if (!view.world().equals(runtime.mainInstance(player.getWorld()))
                        || player.getLocation()
                                        .distanceSquared(
                                                new Location(
                                                        player.getWorld(),
                                                        point.x() + .5,
                                                        point.y() + .5,
                                                        point.z() + .5))
                                > 36)
                    throw new SettlementException("Stand within six blocks of the Repair Station");
                UUID job = UUID.randomUUID();
                runtime.mutate(
                        player, view.settlement(), null, () -> service.prepare(job, actor, id));
            }
            case "supply" -> inventory.transfer(player, id, Direction.SUPPLY);
            case "refund" -> inventory.transfer(player, id, Direction.REFUND);
            case "start" ->
                    runtime.query(
                            player,
                            () -> service.job(id),
                            job -> {
                                if (!inventory.verified(actor) || !inventory.atStation(player, job))
                                    throw new SettlementException(
                                            "Verify inventory and stand at the Repair Station");
                                runtime.mutate(
                                        player,
                                        job.settlement(),
                                        null,
                                        () -> service.start(actor, id));
                            });
            case "cancel" ->
                    runtime.query(
                            player,
                            () -> service.job(id),
                            job ->
                                    runtime.mutateRepair(
                                            actor,
                                            job.settlement(),
                                            () -> published(service.cancel(actor, id))));
            case "jobs" ->
                    runtime.query(
                            player,
                            () -> service.jobs(id),
                            jobs -> {
                                for (var job : jobs)
                                    player.sendMessage(
                                            Component.text(
                                                    job.id()
                                                            + " · "
                                                            + job.state()
                                                            + " · payer "
                                                            + job.payer()));
                            });
            case "status" ->
                    runtime.query(
                            player,
                            () -> service.job(id),
                            job -> {
                                player.sendMessage(
                                        Component.text(
                                                "Repair "
                                                        + job.id()
                                                        + " · "
                                                        + job.state()
                                                        + " · "
                                                        + job.remaining()
                                                        + " cells · Era "
                                                        + job.era()
                                                        + " · next tick "
                                                        + job.nextTick()));
                                for (var s : job.stock())
                                    player.sendMessage(
                                            Component.text(
                                                    s.material()
                                                            + " · remaining "
                                                            + s.needed()
                                                            + " · reserved "
                                                            + s.escrow()
                                                            + " · consumed "
                                                            + s.consumed()
                                                            + " · refunded "
                                                            + s.refunded()));
                            });
            default -> throw new SettlementException("Unknown Repair command; use repair help");
        }
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) task.cancel();
        inventory.close();
        recovery.clear();
    }
}
