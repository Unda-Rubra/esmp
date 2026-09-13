package xdu.undarubra.esmp.plugin.repair;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.civilization.repair.*;
import xdu.undarubra.esmp.civilization.repair.RepairService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.plugin.settlement.SettlementRuntime;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Player-data receipt and physical plain-item transfer boundary. No world item drops or NBT escrow.
 */
public final class RepairInventory implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final RepairService service;
    private final Consumer<Throwable> fatal;
    private final NamespacedKey receipt;
    private final Set<UUID> verified = new HashSet<>(), locked = new HashSet<>();
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
    private boolean flight;
    private volatile boolean closed;
    private BukkitTask task;

    public RepairInventory(
            JavaPlugin plugin,
            SettlementRuntime runtime,
            RepairService service,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.service = service;
        this.fatal = fatal;
        receipt = new NamespacedKey(plugin, "repair_inventory_receipt");
    }

    public boolean verified(UUID payer) {
        return verified.contains(payer) && !locked.contains(payer);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (var player : plugin.getServer().getOnlinePlayers()) join(player);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void join(Player player) {
        verified.remove(player.getUniqueId());
        locked.add(player.getUniqueId());
        queue.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void joined(PlayerJoinEvent event) {
        join(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        queue.remove(event.getPlayer().getUniqueId());
        if (locked.remove(event.getPlayer().getUniqueId()))
            verified.remove(event.getPlayer().getUniqueId());
    }

    private boolean locked(org.bukkit.entity.HumanEntity player) {
        return locked.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (locked(event.getWhoClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (locked(event.getWhoClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && locked(p)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
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

    private long saved(Player player) {
        long value =
                player.getPersistentDataContainer()
                        .getOrDefault(receipt, PersistentDataType.LONG, 0L);
        if (value < 0) throw new IllegalStateException("Invalid Repair inventory receipt");
        return value;
    }

    private static RepairService.Item item(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() == 0)
            return RepairService.Item.empty();
        if (stack.hasItemMeta() || stack.getAmount() > 64)
            return new RepairService.Item("opaque", 1);
        return new RepairService.Item(stack.getType().getKey().toString(), stack.getAmount());
    }

    private static ItemStack stack(RepairService.Item item) {
        if (item.count() == 0) return null;
        Material material = Material.matchMaterial(item.material());
        if (material == null || !material.isItem())
            throw new IllegalStateException("Repair material is not a vanilla item");
        return new ItemStack(material, item.count());
    }

    public List<RepairService.Item> contents(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents())
                .map(RepairInventory::item)
                .toList();
    }

    public boolean atStation(Player player, Job job) {
        var view = runtime.buildings().view(job.building());
        if (view == null
                || view.station() == null
                || !view.station().equals(job.station())
                || !view.world().equals(runtime.mainInstance(player.getWorld()))) return false;
        var p = job.station();
        if (!player.getWorld().isChunkLoaded(p.x() >> 4, p.z() >> 4)
                || player.getWorld().getBlockAt(p.x(), p.y(), p.z()).getType()
                        != Material.CRAFTING_TABLE) return false;
        return player.getLocation()
                        .distanceSquared(
                                new Location(player.getWorld(), p.x() + .5, p.y() + .5, p.z() + .5))
                <= 36;
    }

    private enum Applied {
        SAVED,
        DEFERRED,
        CANCEL
    }

    private Applied apply(Player player, Transfer transfer, Job job) {
        if (!player.isOnline()) return Applied.DEFERRED;
        if (saved(player) >= transfer.sequence()) return Applied.SAVED;
        if (player.isDead())
            return transfer.state().equals("PREPARED") ? Applied.CANCEL : Applied.DEFERRED;
        for (var slot : transfer.slots()) {
            var actual = item(player.getInventory().getItem(slot.index()));
            if (!actual.equals(slot.before())) {
                if (transfer.state().equals("PREPARED")) return Applied.CANCEL;
                throw new IllegalStateException(
                        "Saved Repair transfer lacks its player receipt and original slots: "
                                + transfer.id());
            }
        }
        if (transfer.state().equals("PREPARED")
                && transfer.direction() == Direction.SUPPLY
                && (!atStation(player, job) || !Set.of("AWAITING", "PAUSED").contains(job.state())))
            return Applied.CANCEL;
        for (var slot : transfer.slots())
            player.getInventory().setItem(slot.index(), stack(slot.after()));
        player.getPersistentDataContainer()
                .set(receipt, PersistentDataType.LONG, transfer.sequence());
        player.saveData();
        return Applied.SAVED;
    }

    private CompletableFuture<SettlementChange> process(Player player, Transfer transfer, Job job) {
        return main(() -> apply(player, transfer, job))
                .thenCompose(
                        result ->
                                switch (result) {
                                    case SAVED -> service.confirm(transfer.id());
                                    case CANCEL -> service.abort(transfer.id());
                                    case DEFERRED -> service.defer(job.id());
                                });
    }

    private void tick() {
        if (closed || flight || queue.isEmpty() || !runtime.buildingCommitAvailable()) return;
        UUID id = queue.iterator().next();
        Player player = plugin.getServer().getPlayer(id);
        if (player == null) {
            queue.remove(id);
            locked.remove(id);
            return;
        }
        flight = true;
        long marker = saved(player);
        service.transfers(id, marker)
                .whenComplete(
                        (transfers, error) ->
                                main(
                                        () -> {
                                            if (error != null) {
                                                flight = false;
                                                fatal.accept(error);
                                                return null;
                                            }
                                            if (!player.isOnline()) {
                                                flight = false;
                                                queue.remove(id);
                                                locked.remove(id);
                                                return null;
                                            }
                                            if (transfers.isEmpty()) {
                                                flight = false;
                                                queue.remove(id);
                                                locked.remove(id);
                                                verified.add(id);
                                                return null;
                                            }
                                            var transfer = transfers.getFirst();
                                            service.job(transfer.job())
                                                    .whenComplete(
                                                            (job, failure) ->
                                                                    main(
                                                                            () -> {
                                                                                if (failure
                                                                                        != null) {
                                                                                    flight = false;
                                                                                    fatal.accept(
                                                                                            failure);
                                                                                    return null;
                                                                                }
                                                                                if (!runtime
                                                                                                .buildingCommitAvailable()
                                                                                        || runtime.buildings()
                                                                                                .hasPending(
                                                                                                        job
                                                                                                                .settlement())) {
                                                                                    flight = false;
                                                                                    return null;
                                                                                }
                                                                                try {
                                                                                    runtime.mutateRepair(
                                                                                                    id,
                                                                                                    job
                                                                                                            .settlement(),
                                                                                                    () ->
                                                                                                            process(
                                                                                                                    player,
                                                                                                                    transfer,
                                                                                                                    job))
                                                                                            .whenComplete(
                                                                                                    (ignored,
                                                                                                            problem) -> {
                                                                                                        flight =
                                                                                                                false;
                                                                                                        if (problem
                                                                                                                        != null
                                                                                                                && !closed)
                                                                                                            fatal
                                                                                                                    .accept(
                                                                                                                            problem);
                                                                                                    });
                                                                                } catch (
                                                                                        SettlementException
                                                                                                wait) {
                                                                                    flight = false;
                                                                                }
                                                                                return null;
                                                                            }));
                                            return null;
                                        }));
    }

    public void transfer(Player player, UUID jobId, Direction direction) {
        UUID actor = player.getUniqueId();
        if (!verified(actor))
            throw new SettlementException("Repair inventory recovery is still pending");
        runtime.query(
                player,
                () -> service.job(jobId),
                job -> {
                    if (!job.payer().equals(actor))
                        throw new SettlementException(
                                "Only the payer may supply or refund materials");
                    if (direction == Direction.SUPPLY && !atStation(player, job))
                        throw new SettlementException(
                                "Stand within six blocks of the Repair Station");
                    var slots =
                            direction == Direction.SUPPLY
                                    ? RepairMaterials.supply(job, contents(player))
                                    : RepairMaterials.refund(job, contents(player));
                    if (slots.isEmpty()) {
                        if (direction == Direction.SUPPLY
                                && job.stock().stream().allMatch(s -> s.escrow() >= s.needed())) {
                            runtime.mutate(
                                    player,
                                    job.settlement(),
                                    null,
                                    () -> service.start(actor, job.id()));
                            return;
                        }
                        throw new SettlementException(
                                direction == Direction.SUPPLY
                                        ? "Carry required plain block items; named or modified"
                                                + " items are not consumed"
                                        : "No refundable materials fit in your inventory");
                    }
                    UUID transfer = UUID.randomUUID();
                    locked.add(actor);
                    verified.remove(actor);
                    try {
                        runtime.mutateRepair(
                                        actor,
                                        job.settlement(),
                                        () ->
                                                service.transfer(
                                                                transfer, actor, job.id(),
                                                                direction, slots)
                                                        .thenCompose(
                                                                prepared ->
                                                                        process(
                                                                                player, prepared,
                                                                                job)))
                                .whenComplete(
                                        (ignored, failure) -> {
                                            if (failure != null && !closed) {
                                                Throwable cause = failure;
                                                while (cause instanceof CompletionException
                                                        && cause.getCause() != null)
                                                    cause = cause.getCause();
                                                if (cause instanceof SettlementException) {
                                                    locked.remove(actor);
                                                    queue.add(actor);
                                                } else fatal.accept(failure);
                                            } else {
                                                queue.add(actor);
                                                if (!closed && player.isOnline())
                                                    player.sendMessage(
                                                            net.kyori.adventure.text.Component.text(
                                                                    "Repair material transfer"
                                                                        + " recorded. /settlement"
                                                                        + " repair status "
                                                                            + job.id()));
                                            }
                                        });
                    } catch (RuntimeException failure) {
                        locked.remove(actor);
                        queue.add(actor);
                        throw failure;
                    }
                });
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) task.cancel();
        queue.clear();
        verified.clear();
        locked.clear();
    }
}
