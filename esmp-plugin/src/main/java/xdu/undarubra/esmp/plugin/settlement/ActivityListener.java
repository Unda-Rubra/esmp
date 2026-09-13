package xdu.undarubra.esmp.plugin.settlement;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.civilization.activity.ActivityContribution;
import xdu.undarubra.esmp.civilization.activity.ActivityContribution.Category;
import xdu.undarubra.esmp.civilization.settlement.ParcelAction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Bounded coalescing of meaningful successful actions; no movement/idle/automation credit. */
public final class ActivityListener implements Listener, AutoCloseable {
    private record Key(UUID settlement, UUID actor, Category category) {}

    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final Consumer<Throwable> fatal;
    private final LinkedHashMap<Key, ActivityContribution> pending = new LinkedHashMap<>();
    private CompletableFuture<Void> flight = CompletableFuture.completedFuture(null);
    private BukkitTask task;
    private boolean closed;

    public ActivityListener(
            JavaPlugin plugin, SettlementRuntime runtime, Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.fatal = fatal;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!closed && flight.isDone()) {
                                        flush();
                                    }
                                },
                                1,
                                20);
    }

    private void observe(
            Player player, Location location, ParcelAction action, boolean safePublic) {
        Category category = action == ParcelAction.BUILD ? Category.BUILD : Category.INTERACTION;
        if (closed
                || !runtime.ready()
                || !runtime.clock().advancing()
                || !runtime.available(location)) {
            return;
        }
        var settlement = runtime.at(location);
        if (settlement == null
                || !runtime.allows(player.getUniqueId(), location, action, safePublic)) {
            return;
        }
        Key key = new Key(settlement.id(), player.getUniqueId(), category);
        if (pending.size() >= 4096 && !pending.containsKey(key)) {
            return;
        }
        pending.putIfAbsent(
                key,
                new ActivityContribution(
                        settlement.id(),
                        settlement.worldInstanceId(),
                        player.getUniqueId(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ(),
                        settlement.ownershipRevision(),
                        settlement.chunkRevision(),
                        runtime.land(settlement.id()).securityRevision(),
                        action,
                        safePublic));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.canBuild()) {
            observe(
                    event.getPlayer(),
                    event.getBlockPlaced().getLocation(),
                    ParcelAction.BUILD,
                    false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        observe(event.getPlayer(), event.getBlock().getLocation(), ParcelAction.BUILD, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        for (Location location : ClaimProtection.inventoryLocations(event.getInventory())) {
            if (runtime.allows(player.getUniqueId(), location, ParcelAction.CONTAINER, false)) {
                observe(player, location, ParcelAction.CONTAINER, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPublicUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY
                || event.getClickedBlock() == null) {
            return;
        }
        if (ClaimProtection.publicControl(event.getClickedBlock().getType())) {
            observe(
                    event.getPlayer(),
                    event.getClickedBlock().getLocation(),
                    ParcelAction.INTERACT,
                    true);
        }
    }

    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        var batch = new ArrayList<ActivityContribution>(Math.min(256, pending.size()));
        var iterator = pending.values().iterator();
        while (iterator.hasNext() && batch.size() < 256) {
            batch.add(iterator.next());
            iterator.remove();
        }
        flight = runtime.activity().contribute(batch);
        flight.whenComplete(
                (ignored, failure) -> {
                    if (failure != null && !closed && plugin.isEnabled()) {
                        plugin.getServer()
                                .getScheduler()
                                .runTask(plugin, () -> fatal.accept(failure));
                    }
                });
    }

    /** Drains admitted observations for a now-reserved Settlement before Decay authorization. */
    public CompletableFuture<Void> barrier(UUID settlement) {
        var batches = new ArrayList<java.util.List<ActivityContribution>>();
        var batch = new ArrayList<ActivityContribution>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            var observation = iterator.next();
            if (!observation.settlementId().equals(settlement)) continue;
            batch.add(observation);
            iterator.remove();
            if (batch.size() == 256) {
                batches.add(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) batches.add(batch);
        var result = flight;
        for (var observations : batches)
            result = result.thenCompose(ignored -> runtime.activity().contribute(observations));
        flight = result;
        return result;
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) {
            task.cancel();
        }
        flight.join();
        while (!pending.isEmpty()) {
            flush();
            flight.join();
        }
    }
}
