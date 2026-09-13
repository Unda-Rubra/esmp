package xdu.undarubra.esmp.plugin.building;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.building.BuildingService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.plugin.settlement.SettlementRuntime;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.*;

/**
 * Main-thread bounded capture and durable chunk observation journals; never mutates world blocks.
 */
public final class BuildingRuntime implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final BuildingService service;
    private final Policy policy;
    private final Consumer<Throwable> fatal;
    private final NamespacedKey journalKey;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<Key, Set<UUID>> spatial = new HashMap<>();
    private final Map<UUID, Set<UUID>> bySettlement = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<Key, LinkedHashMap<UUID, Observation>> journals = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Observation> pending = new LinkedHashMap<>();
    private boolean flight;
    private volatile boolean closed;
    private BukkitTask task;

    private record Key(UUID world, int x, int z) {}

    private record Selection(UUID world, Position first, Position second) {}

    private static final class Observation {
        final UUID id, actor, building, settlement;
        final Key chunk;
        final Stamp stamp;
        final Map<Position, BuildingCell> before;
        Map<Position, BuildingCell> after;
        final int tick;
        boolean checked, probing;

        Observation(
                UUID id,
                UUID actor,
                UUID building,
                UUID settlement,
                Key chunk,
                Stamp stamp,
                Map<Position, BuildingCell> before,
                Map<Position, BuildingCell> after,
                int tick) {
            this.id = id;
            this.actor = actor;
            this.building = building;
            this.settlement = settlement;
            this.chunk = chunk;
            this.stamp = stamp;
            this.before = before;
            this.after = after;
            this.tick = tick;
            this.checked = tick >= 0;
        }
    }

    public BuildingRuntime(
            JavaPlugin plugin,
            SettlementRuntime runtime,
            BuildingService service,
            Policy policy,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.service = service;
        this.policy = policy;
        this.fatal = fatal;
        journalKey = new NamespacedKey(plugin, "building_observations");
    }

    public CompletableFuture<Void> initialize() {
        return service.active(null)
                .thenAccept(
                        list -> {
                            for (var view : list) {
                                views.put(view.id(), view);
                                bySettlement
                                        .computeIfAbsent(
                                                view.settlement(), ignored -> new HashSet<>())
                                        .add(view.id());
                                index(view);
                            }
                        });
    }

    public CompletableFuture<List<View>> active(UUID settlement) {
        return service.active(settlement);
    }

    public void publish(UUID settlement, List<View> list) {
        var ids = new HashSet<UUID>();
        for (var v : list) ids.add(v.id());
        for (var id : bySettlement.getOrDefault(settlement, Set.of()))
            if (!ids.contains(id)) {
                var old = views.remove(id);
                unindex(old);
            }
        for (var v : list) {
            var old = views.put(v.id(), v);
            if (old == null) {
                index(v);
            } else if (!old.bounds().equals(v.bounds()) || !old.world().equals(v.world())) {
                unindex(old);
                index(v);
            }
        }
        if (ids.isEmpty()) bySettlement.remove(settlement);
        else bySettlement.put(settlement, ids);
    }

    private void index(View view) {
        for (var chunk : view.bounds().footprint().keySet())
            spatial.computeIfAbsent(
                            new Key(view.world(), chunk.x(), chunk.z()), ignored -> new HashSet<>())
                    .add(view.id());
    }

    private void unindex(View view) {
        for (var chunk : view.bounds().footprint().keySet()) {
            var key = new Key(view.world(), chunk.x(), chunk.z());
            var ids = spatial.get(key);
            if (ids != null) {
                ids.remove(view.id());
                if (ids.isEmpty()) spatial.remove(key);
            }
        }
    }

    public boolean hasPending(UUID settlement) {
        return settlement != null
                && pending.values().stream().anyMatch(o -> o.settlement.equals(settlement));
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (var world : plugin.getServer().getWorlds())
            if (runtime.isMain(world)) for (var chunk : world.getLoadedChunks()) load(chunk);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private View at(Block block) {
        var ids =
                spatial.get(
                        new Key(
                                runtime.mainInstance(block.getWorld()),
                                block.getX() >> 4,
                                block.getZ() >> 4));
        if (ids == null) return null;
        Position p = position(block);
        for (var id : ids) {
            var v = views.get(id);
            if (v.bounds().contains(p)) return v;
        }
        return null;
    }

    private Stamp stamp(UUID settlement) {
        var s = runtime.index().byId(settlement);
        var land = runtime.land(settlement);
        if (s == null || land == null) throw new SettlementException("Settlement is unavailable");
        return new Stamp(
                s.worldInstanceId(),
                s.ownershipRevision(),
                s.chunkRevision(),
                land.securityRevision());
    }

    private static Position position(Block b) {
        return new Position(b.getX(), b.getY(), b.getZ());
    }

    private static BuildingCell cell(BlockState state) {
        var data = state.getBlockData();
        var type = data.getMaterial();
        return new BuildingCell(
                data.getAsString(),
                type.isAir(),
                !type.isAir() && type.isSolid(),
                data instanceof Openable,
                state instanceof TileState);
    }

    private static BuildingCell cell(Block block) {
        return cell(block.getState(false));
    }

    private Chunk loaded(Key key) {
        World w = plugin.getServer().getWorld(key.world());
        return w != null && w.isChunkLoaded(key.x(), key.z())
                ? w.getChunkAt(key.x(), key.z())
                : null;
    }

    private void observe(Player player, Collection<BlockState> before) {
        if (closed || !runtime.ready()) return;
        try {
            var groups = new LinkedHashMap<View, Map<Key, Map<Position, BuildingCell>>>();
            for (var state : before) {
                var b = state.getBlock();
                var view = at(b);
                if (view == null
                        || !runtime.allows(
                                player.getUniqueId(), b.getLocation(), ParcelAction.BUILD, false))
                    continue;
                var key = new Key(b.getWorld().getUID(), b.getX() >> 4, b.getZ() >> 4);
                groups.computeIfAbsent(view, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                        .put(position(b), cell(state));
            }
            for (var group : groups.entrySet())
                for (var part : group.getValue().entrySet()) {
                    var v = group.getKey();
                    var o =
                            new Observation(
                                    UUID.randomUUID(),
                                    player.getUniqueId(),
                                    v.id(),
                                    v.settlement(),
                                    part.getKey(),
                                    stamp(v.settlement()),
                                    Map.copyOf(part.getValue()),
                                    null,
                                    Bukkit.getCurrentTick());
                    if (pending.size() >= 256)
                        throw new IllegalStateException(
                                "Building observation queue exceeded its safety limit");
                    pending.put(o.id, o);
                    journals.computeIfAbsent(o.chunk, ignored -> new LinkedHashMap<>())
                            .put(o.id, o);
                    save(o.chunk);
                }
        } catch (Throwable failure) {
            fatal.accept(failure);
        }
    }

    private record Admission(UUID building, int x, int z) {}

    private void admit(Cancellable event, Player player, Collection<BlockState> states) {
        var groups = new HashSet<Admission>();
        for (var state : states) {
            var block = state.getBlock();
            var view = at(block);
            if (view != null)
                groups.add(new Admission(view.id(), block.getX() >> 4, block.getZ() >> 4));
        }
        if (!groups.isEmpty() && groups.size() > Math.max(0, 256 - pending.size())) {
            event.setCancelled(true);
            if (player != null)
                player.sendMessage(Component.text("Building history is busy; retry shortly."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void admitPlace(BlockPlaceEvent e) {
        admit(
                e,
                e.getPlayer(),
                e instanceof BlockMultiPlaceEvent m
                        ? m.getReplacedBlockStates()
                        : List.of(e.getBlockReplacedState()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void admitBreak(BlockBreakEvent e) {
        admit(e, e.getPlayer(), neighbors(e.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void admitEmpty(PlayerBucketEmptyEvent e) {
        admit(e, e.getPlayer(), neighbors(e.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void admitFill(PlayerBucketFillEvent e) {
        admit(e, e.getPlayer(), neighbors(e.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void admitInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null) admit(e, e.getPlayer(), neighbors(e.getClickedBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void admitFertilize(BlockFertilizeEvent e) {
        if (e.getPlayer() != null) admit(e, e.getPlayer(), e.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void admitGrowth(StructureGrowEvent e) {
        if (e.getPlayer() != null) admit(e, e.getPlayer(), e.getBlocks());
    }

    private List<BlockState> neighbors(Block block) {
        var states = new ArrayList<BlockState>(27);
        World world = block.getWorld();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    int x = block.getX() + dx, y = block.getY() + dy, z = block.getZ() + dz;
                    if (y < world.getMinHeight()
                            || y >= world.getMaxHeight()
                            || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    var b = world.getBlockAt(x, y, z);
                    if (at(b) != null) states.add(b.getState(false));
                }
        return states;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placed(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent multi)
            observe(event.getPlayer(), multi.getReplacedBlockStates());
        else observe(event.getPlayer(), List.of(event.getBlockReplacedState()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent event) {
        observe(event.getPlayer(), neighbors(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void emptied(PlayerBucketEmptyEvent event) {
        observe(event.getPlayer(), neighbors(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void filled(PlayerBucketFillEvent event) {
        observe(event.getPlayer(), neighbors(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void transformed(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null
                || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getItem() == null) return;
        String material = event.getItem().getType().name();
        if (material.endsWith("_AXE")
                || material.endsWith("_HOE")
                || material.endsWith("_SHOVEL")
                || material.endsWith("_DYE")
                || material.equals("HONEYCOMB"))
            observe(event.getPlayer(), neighbors(event.getClickedBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fertilized(BlockFertilizeEvent event) {
        if (event.getPlayer() != null)
            observe(
                    event.getPlayer(),
                    event.getBlocks().stream().map(s -> s.getBlock().getState(false)).toList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void grown(StructureGrowEvent event) {
        if (event.getPlayer() != null)
            observe(
                    event.getPlayer(),
                    event.getBlocks().stream().map(s -> s.getBlock().getState(false)).toList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joinedChunk(ChunkLoadEvent event) {
        if (runtime.isMain(event.getWorld())) load(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void leavingChunk(ChunkUnloadEvent event) {
        var key =
                new Key(
                        event.getWorld().getUID(),
                        event.getChunk().getX(),
                        event.getChunk().getZ());
        for (var o : new ArrayList<>(pending.values()))
            if (o.chunk.equals(key) && o.after == null) capture(o);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }

    private void capture(Observation o) {
        if (!o.checked) {
            if (closed || flight) return;
            if (!o.probing) {
                o.probing = true;
                flight = true;
                service.recorded(o.id, o.actor, o.building)
                        .whenComplete(
                                (recorded, failure) -> {
                                    if (closed) return;
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        if (closed) return;
                                                        o.probing = false;
                                                        flight = false;
                                                        if (failure != null) {
                                                            fatal.accept(failure);
                                                            return;
                                                        }
                                                        o.checked = true;
                                                        if (recorded) remove(o);
                                                        else capture(o);
                                                    });
                                });
            }
            return;
        }
        Chunk chunk = loaded(o.chunk);
        if (chunk == null) return;
        if (!o.stamp.world().equals(runtime.mainInstance(chunk.getWorld())))
            throw new IllegalStateException("Building journal world instance changed");
        var changed = new LinkedHashMap<Position, BuildingCell>();
        for (var entry : o.before.entrySet()) {
            var p = entry.getKey();
            var now = cell(chunk.getWorld().getBlockAt(p.x(), p.y(), p.z()));
            if (!now.equals(entry.getValue())) changed.put(p, now);
        }
        o.after = Map.copyOf(changed);
        save(o.chunk);
        if (changed.isEmpty()) remove(o);
    }

    private void tick() {
        if (closed || pending.isEmpty()) return;
        try {
            for (var o : new ArrayList<>(pending.values()))
                if (o.after == null && o.tick != Bukkit.getCurrentTick()) capture(o);
            if (flight || !runtime.buildingCommitAvailable()) return;
            for (var o : pending.values())
                if (o.after != null) {
                    if (o.after.isEmpty()) {
                        remove(o);
                        break;
                    }
                    var world = plugin.getServer().getWorld(o.chunk.world());
                    if (world == null) continue;
                    if (!o.stamp.world().equals(runtime.mainInstance(world)))
                        throw new IllegalStateException("Building journal world instance changed");
                    flight = true;
                    runtime.mutateBuildingEdit(
                                    o.actor,
                                    o.settlement,
                                    () ->
                                            service.edit(
                                                    o.id,
                                                    o.actor,
                                                    o.building,
                                                    o.stamp,
                                                    ParcelAction.BUILD,
                                                    o.after))
                            .whenComplete(
                                    (ignored, failure) -> {
                                        flight = false;
                                        if (failure != null) {
                                            fatal.accept(
                                                    new IllegalStateException(
                                                            "Cannot publish durable Building"
                                                                    + " observation "
                                                                    + o.id,
                                                            failure));
                                            return;
                                        }
                                        remove(o);
                                    });
                    break;
                }
        } catch (Throwable failure) {
            fatal.accept(failure);
        }
    }

    private void remove(Observation o) {
        pending.remove(o.id);
        var journal = journals.get(o.chunk);
        if (journal != null) journal.remove(o.id);
        save(o.chunk);
    }

    private void save(Key key) {
        var chunk = loaded(key);
        if (chunk == null) return;
        var journal = journals.get(key);
        if (journal == null || journal.isEmpty()) {
            chunk.getPersistentDataContainer().remove(journalKey);
            journals.remove(key);
            return;
        }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeInt(1);
                out.writeInt(journal.size());
                for (var o : journal.values()) {
                    uuid(out, o.id);
                    uuid(out, o.actor);
                    uuid(out, o.building);
                    uuid(out, o.settlement);
                    uuid(out, o.stamp.world());
                    out.writeLong(o.stamp.ownership());
                    out.writeLong(o.stamp.claims());
                    out.writeLong(o.stamp.security());
                    cells(out, o.before);
                    out.writeBoolean(o.after != null);
                    if (o.after != null) cells(out, o.after);
                }
            }
            if (bytes.size() > 4 * 1024 * 1024)
                throw new IOException("Building journal exceeds size limit");
            chunk.getPersistentDataContainer()
                    .set(journalKey, PersistentDataType.BYTE_ARRAY, bytes.toByteArray());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void load(Chunk chunk) {
        try {
            byte[] compressed =
                    chunk.getPersistentDataContainer()
                            .get(journalKey, PersistentDataType.BYTE_ARRAY);
            if (compressed == null) return;
            if (compressed.length > 4 * 1024 * 1024)
                throw new IOException("Oversized Building journal");
            byte[] bytes;
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                bytes = gzip.readNBytes(16 * 1024 * 1024 + 1);
            }
            if (bytes.length > 16 * 1024 * 1024)
                throw new IOException("Oversized expanded Building journal");
            var key = new Key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
            var journal = journals.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readInt() != 1) throw new IOException("Unknown Building journal version");
                int count = in.readInt();
                if (count < 0 || count > 256)
                    throw new IOException("Invalid Building journal count");
                for (int i = 0; i < count; i++) {
                    UUID id = uuid(in),
                            actor = uuid(in),
                            building = uuid(in),
                            settlement = uuid(in);
                    var stamp = new Stamp(uuid(in), in.readLong(), in.readLong(), in.readLong());
                    var before = cells(in);
                    var after = in.readBoolean() ? cells(in) : null;
                    if (before.isEmpty()
                            || (after != null && !before.keySet().containsAll(after.keySet())))
                        throw new IOException("Invalid Building observation cells");
                    for (var p : before.keySet())
                        if ((p.x() >> 4) != chunk.getX()
                                || (p.z() >> 4) != chunk.getZ()
                                || p.y() < chunk.getWorld().getMinHeight()
                                || p.y() >= chunk.getWorld().getMaxHeight())
                            throw new IOException("Building observation crosses its journal chunk");
                    var o =
                            new Observation(
                                    id, actor, building, settlement, key, stamp, before, after, -1);
                    journal.putIfAbsent(id, o);
                    pending.putIfAbsent(id, journal.get(id));
                }
                if (in.read() != -1) throw new IOException("Trailing Building journal data");
            }
        } catch (Throwable failure) {
            fatal.accept(failure);
        }
    }

    private static void uuid(DataOutput out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(DataInput in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void cells(DataOutput out, Map<Position, BuildingCell> cells)
            throws IOException {
        out.writeInt(cells.size());
        for (var e : cells.entrySet()) {
            out.writeInt(e.getKey().x());
            out.writeInt(e.getKey().y());
            out.writeInt(e.getKey().z());
            BuildingBaseline.writeCell(out, e.getValue());
        }
    }

    private static Map<Position, BuildingCell> cells(DataInput in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > BuildingBounds.HARD_MAX_VOLUME)
            throw new IOException("Invalid observation size");
        var cells = new LinkedHashMap<Position, BuildingCell>();
        for (int i = 0; i < count; i++)
            cells.put(
                    new Position(in.readInt(), in.readInt(), in.readInt()),
                    BuildingBaseline.readCell(in));
        return Map.copyOf(cells);
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) task.cancel();
        for (var o : new ArrayList<>(pending.values())) if (o.after == null) capture(o);
        selections.clear();
    }

    private BuildingBounds selection(Player player) {
        var selected = selections.get(player.getUniqueId());
        if (selected == null
                || selected.second() == null
                || !selected.world().equals(player.getWorld().getUID()))
            throw new SettlementException("Select both Building corners in this world first");
        var a = selected.first();
        var b = selected.second();
        var bounds =
                new BuildingBounds(
                        Math.min(a.x(), b.x()),
                        Math.min(a.y(), b.y()),
                        Math.min(a.z(), b.z()),
                        Math.max(a.x(), b.x()),
                        Math.max(a.y(), b.y()),
                        Math.max(a.z(), b.z()));
        if (bounds.volume() > policy.maxVolume())
            throw new SettlementException(
                    "Building selection exceeds " + policy.maxVolume() + " blocks");
        return bounds;
    }

    private BuildingBaseline scan(Player player, UUID settlement, BuildingBounds bounds) {
        World world = player.getWorld();
        if (bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight())
            throw new SettlementException("Selection exceeds world height");
        var s = runtime.index().byId(settlement);
        if (s == null || !s.worldInstanceId().equals(runtime.mainInstance(world)))
            throw new SettlementException("Building must be in this Settlement's world");
        if (!runtime.administrator(player.getUniqueId())
                && s.role(player.getUniqueId()) != Role.OWNER
                && s.role(player.getUniqueId()) != Role.GOVERNOR)
            throw new SettlementException("Owner or Governor required");
        for (var chunk : bounds.footprint().keySet()) {
            if (!s.chunks().contains(chunk))
                throw new SettlementException(
                        "Every selected chunk must belong to this Settlement");
            if (!world.isChunkLoaded(chunk.x(), chunk.z()))
                throw new SettlementException(
                        "Every selected chunk must already be loaded; visit it first");
        }
        var cells = new ArrayList<BuildingCell>(bounds.volume());
        for (int i = 0; i < bounds.volume(); i++) {
            var p = bounds.position(i);
            var block = world.getBlockAt(p.x(), p.y(), p.z());
            if (!runtime.allows(
                    player.getUniqueId(), block.getLocation(), ParcelAction.BUILD, false))
                throw new SettlementException("Building selection includes space you cannot edit");
            cells.add(cell(block));
        }
        return new BuildingBaseline(bounds, cells);
    }

    private View activeView(String text) {
        var id = UUID.fromString(text);
        var v = views.get(id);
        if (v == null)
            throw new SettlementException(
                    "Active Building not found; use building list or history");
        return v;
    }

    private UUID settlement(Player player, String text) {
        var s = text == null ? runtime.at(player.getLocation()) : runtime.find(text);
        if (s == null) throw new SettlementException("Settlement not found");
        return s.id();
    }

    private static void count(String[] args, int expected, String usage) {
        if (args.length != expected)
            throw new SettlementException("Usage: /settlement building " + usage);
    }

    public void command(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String line :
                    List.of(
                            "pos1|pos2 [x y z] | preview | clear",
                            "register <settlement> <name> | resize <buildingUUID>",
                            "list [settlement] [page] | info <buildingUUID> [revision]",
                            "cell <buildingUUID> <x y z> [revision] | history <buildingUUID>",
                            "retire <buildingUUID> confirm | station <buildingUUID> <x y z>|clear"))
                player.sendMessage(Component.text("/settlement building " + line));
            return;
        }
        UUID actor = player.getUniqueId();
        runtime.rememberAdministrator(actor);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos1", "pos2" -> {
                if (args.length != 1 && args.length != 4)
                    throw new SettlementException("Use pos1|pos2 [x y z]");
                Position point =
                        args.length == 1
                                ? new Position(
                                        player.getLocation().getBlockX(),
                                        player.getLocation().getBlockY(),
                                        player.getLocation().getBlockZ())
                                : new Position(
                                        Integer.parseInt(args[1]),
                                        Integer.parseInt(args[2]),
                                        Integer.parseInt(args[3]));
                var old = selections.get(actor);
                if (args[0].equalsIgnoreCase("pos1"))
                    selections.put(actor, new Selection(player.getWorld().getUID(), point, null));
                else {
                    if (old == null || !old.world().equals(player.getWorld().getUID()))
                        throw new SettlementException("Select pos1 in this world first");
                    selections.put(actor, new Selection(old.world(), old.first(), point));
                }
                player.sendMessage(Component.text("Building corner selected: " + point));
            }
            case "clear" -> {
                count(args, 1, "clear");
                selections.remove(actor);
                player.sendMessage(Component.text("Building selection cleared."));
            }
            case "preview" -> {
                count(args, 1, "preview");
                var b = selection(player);
                player.sendMessage(
                        Component.text(
                                "Building selection: "
                                        + b
                                        + " · "
                                        + b.volume()
                                        + " blocks / limit "
                                        + policy.maxVolume()));
            }
            case "register" -> {
                count(args, 3, "register <settlement> <name>");
                UUID settlement = settlement(player, args[1]);
                var b = selection(player);
                var scan = scan(player, settlement, b);
                var stamp = stamp(settlement);
                UUID id = UUID.randomUUID(), world = runtime.mainInstance(player.getWorld());
                runtime.mutate(
                        player,
                        settlement,
                        null,
                        () -> service.register(actor, id, settlement, world, args[2], scan, stamp));
            }
            case "resize" -> {
                count(args, 2, "resize <buildingUUID>");
                var v = activeView(args[1]);
                var scan = scan(player, v.settlement(), selection(player));
                var stamp = stamp(v.settlement());
                runtime.mutate(
                        player,
                        v.settlement(),
                        null,
                        () -> service.resize(actor, v.id(), v.revision(), scan, stamp));
            }
            case "retire" -> {
                count(args, 3, "retire <buildingUUID> confirm");
                if (!args[2].equalsIgnoreCase("confirm"))
                    throw new SettlementException(
                            "Use confirm to retire this Building without deleting its history");
                var v = activeView(args[1]);
                runtime.mutate(
                        player,
                        v.settlement(),
                        null,
                        () -> service.retire(actor, v.id(), v.revision()));
            }
            case "station" -> {
                if (args.length != 3 && args.length != 5)
                    throw new SettlementException("Use station <buildingUUID> <x y z>|clear");
                var v = activeView(args[1]);
                Position point = null;
                if (args.length == 5) {
                    point =
                            new Position(
                                    Integer.parseInt(args[2]),
                                    Integer.parseInt(args[3]),
                                    Integer.parseInt(args[4]));
                    if (!v.bounds().contains(point)
                            || !v.world().equals(runtime.mainInstance(player.getWorld()))
                            || !player.getWorld().isChunkLoaded(point.x() >> 4, point.z() >> 4))
                        throw new SettlementException("Station must be inside the loaded Building");
                    if (player.getWorld().getBlockAt(point.x(), point.y(), point.z()).getType()
                            != Material.CRAFTING_TABLE)
                        throw new SettlementException(
                                "Place a crafting table for the station first");
                } else if (!args[2].equalsIgnoreCase("clear"))
                    throw new SettlementException("Use clear to remove the station marker");
                Position chosen = point;
                runtime.mutate(
                        player,
                        v.settlement(),
                        null,
                        () -> service.station(actor, v.id(), v.revision(), chosen));
            }
            case "list" -> {
                if (args.length > 3) throw new SettlementException("Use list [settlement] [page]");
                UUID id = settlement(player, args.length >= 2 ? args[1] : null);
                int page = args.length == 3 ? Integer.parseInt(args[2]) : 1;
                if (page < 1) throw new SettlementException("Page must be positive.");
                runtime.query(
                        player,
                        () -> service.active(id),
                        list -> {
                            int pages = Math.max(1, (list.size() + 31) / 32);
                            if (page > pages) throw new SettlementException("Last page: " + pages);
                            player.sendMessage(
                                    Component.text(
                                            "Active Buildings: "
                                                    + list.size()
                                                    + " · page "
                                                    + page
                                                    + "/"
                                                    + pages));
                            for (var v :
                                    list.subList((page - 1) * 32, Math.min(page * 32, list.size())))
                                player.sendMessage(
                                        Component.text(
                                                v.name()
                                                        + " · "
                                                        + v.id()
                                                        + " · revision "
                                                        + v.revision()));
                        });
            }
            case "history" -> {
                count(args, 2, "history <buildingUUID>");
                runtime.query(
                        player,
                        () -> service.history(UUID.fromString(args[1])),
                        list -> {
                            for (var r : list)
                                player.sendMessage(
                                        Component.text(
                                                "Revision "
                                                        + r.revision()
                                                        + " · "
                                                        + r.kind()
                                                        + " · tick "
                                                        + r.tick()
                                                        + " · actor "
                                                        + r.actor()));
                        });
            }
            case "info", "cell" -> {
                boolean cell = args[0].equalsIgnoreCase("cell");
                if (cell
                        ? (args.length != 5 && args.length != 6)
                        : (args.length != 2 && args.length != 3))
                    throw new SettlementException(
                            "Use info <id> [revision] or cell <id> <x y z> [revision]");
                long revision =
                        cell
                                ? (args.length == 6 ? Long.parseLong(args[5]) : 0)
                                : (args.length == 3 ? Long.parseLong(args[2]) : 0);
                UUID id = UUID.fromString(args[1]);
                Position selected =
                        cell
                                ? new Position(
                                        Integer.parseInt(args[2]),
                                        Integer.parseInt(args[3]),
                                        Integer.parseInt(args[4]))
                                : null;
                if (revision < 0) throw new SettlementException("Revision must be nonnegative.");
                runtime.query(
                        player,
                        () -> service.inspect(id, revision),
                        detail -> {
                            var v = detail.building();
                            var g = detail.geometry();
                            if (cell) {
                                var p = selected;
                                if (!detail.baseline().bounds().contains(p))
                                    throw new SettlementException(
                                            "Position is outside this Building revision.");
                                int i = detail.baseline().bounds().index(p);
                                var value = detail.baseline().at(p);
                                player.sendMessage(
                                        Component.text(
                                                value.data()
                                                        + " · entity="
                                                        + value.blockEntity()
                                                        + " · mutable="
                                                        + v.permitsAutomaticMutation(p, value)
                                                        + " · exterior="
                                                        + g.exterior().contains(i)
                                                        + " · interior="
                                                        + g.interiorAir().contains(i)
                                                        + " · opening="
                                                        + g.openings().contains(i)
                                                        + " · support="
                                                        + (g.supportedBy().containsKey(i)
                                                                ? detail.baseline()
                                                                        .bounds()
                                                                        .position(
                                                                                g.supportedBy()
                                                                                        .get(i))
                                                                : "outside/none")));
                            } else
                                player.sendMessage(
                                        Component.text(
                                                v.name()
                                                        + " · "
                                                        + v.id()
                                                        + " · revision "
                                                        + v.revision()
                                                        + " · "
                                                        + (v.retired() ? "RETIRED" : "ACTIVE")
                                                        + " · volume "
                                                        + detail.baseline().bounds().volume()
                                                        + " · occupied "
                                                        + g.occupied().size()
                                                        + " · exterior "
                                                        + g.exterior().size()
                                                        + " · interior "
                                                        + g.interiorAir().size()
                                                        + " · openings "
                                                        + g.openings().size()
                                                        + " · support links "
                                                        + g.supportedBy().size()
                                                        + " · station "
                                                        + v.station()));
                        });
            }
            default -> throw new SettlementException("Unknown Building command; use building help");
        }
    }

    public List<String> complete(String[] args) {
        if (args.length == 0) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1)
            return List.of(
                            "pos1",
                            "pos2",
                            "preview",
                            "clear",
                            "register",
                            "resize",
                            "list",
                            "info",
                            "cell",
                            "history",
                            "retire",
                            "station",
                            "help")
                    .stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        if (args.length == 2
                && Set.of("info", "cell", "history", "resize", "retire", "station")
                        .contains(args[0].toLowerCase(Locale.ROOT)))
            return views.keySet().stream()
                    .map(UUID::toString)
                    .sorted()
                    .filter(s -> s.startsWith(prefix))
                    .limit(50)
                    .toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("retire"))
            return "confirm".startsWith(prefix) ? List.of("confirm") : List.of();
        return List.of();
    }
}
