package xdu.undarubra.esmp.plugin.settlement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.civilization.activity.ActivityPolicy;
import xdu.undarubra.esmp.civilization.activity.ActivityService;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;
import xdu.undarubra.esmp.civilization.settlement.SettlementChange;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.civilization.settlement.SettlementIndex;
import xdu.undarubra.esmp.civilization.settlement.SettlementPolicy;
import xdu.undarubra.esmp.civilization.settlement.SettlementService;
import xdu.undarubra.esmp.civilization.settlement.SettlementView;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Server-thread publication boundary between committed SQL and synchronous protection. */
public final class SettlementRuntime implements AutoCloseable {
    public record Reservation(UUID world, ChunkPosition chunk) {}

    private final JavaPlugin plugin;
    private final WorldStore database;
    private final String mainWorldName;
    private final Consumer<Throwable> fatal;
    private final SettlementService service;
    private final ParcelService parcels;
    private LandSnapshot land = LandSnapshot.EMPTY;
    private long publicationVersion;
    private final Set<UUID> administrators = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private record Publication(SettlementChange change, LandSnapshot.Land land) {}

    private ManagementTools tools;
    private final GameClock clock;
    private final ActivityService activity;
    private final ActivityListener activityListener;
    private final SettlementIndex index = new SettlementIndex();
    private final Set<UUID> bypass = new HashSet<>();
    private final Set<UUID> querying = new HashSet<>();
    private final ClaimVisualization visualization;
    private boolean ready;
    private boolean mutationInFlight;
    private boolean outboxInFlight;
    private UUID frozenSettlement;
    private Reservation reservation;
    private volatile boolean closed;
    private BukkitTask outboxTask;

    public SettlementRuntime(
            JavaPlugin plugin,
            WorldStore database,
            String mainWorldName,
            SettlementPolicy policy,
            GameClock clock,
            ActivityPolicy activityPolicy,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.database = database;
        this.mainWorldName = mainWorldName;
        this.fatal = fatal;
        this.clock = clock;
        service = new SettlementService(database, policy, clock, administrators::contains);
        parcels = new ParcelService(database, clock, administrators::contains);
        activity = new ActivityService(database, clock, activityPolicy, administrators::contains);
        activityListener = new ActivityListener(plugin, this, fatal);
        visualization = new ClaimVisualization(plugin, this);
    }

    public CompletableFuture<java.util.List<SettlementView>> initialize() {
        return service.initialize()
                .thenCompose(
                        views ->
                                activity.initialize()
                                        .thenCompose(ignored -> parcels.snapshot())
                                        .thenApply(
                                                snapshot -> {
                                                    land = snapshot;
                                                    return views;
                                                }));
    }

    public void activate(java.util.List<SettlementView> settlements) {
        index.load(settlements);
        ready = true;
        tools = new ManagementTools(plugin, this);
        tools.start();
        activityListener.start();
        visualization.start();
        outboxTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, this::dispatchOutbox, 1L, 20L);
    }

    public SettlementService service() {
        return service;
    }

    public ParcelService parcels() {
        return parcels;
    }

    public LandSnapshot.Land land(UUID id) {
        return land.settlements().get(id);
    }

    public long publicationVersion() {
        return publicationVersion;
    }

    public ManagementTools tools() {
        return tools;
    }

    public boolean administrator(UUID actor) {
        rememberAdministrator(actor);
        return administrators.contains(actor);
    }

    public void rememberAdministrator(UUID actor) {
        if (actor == null) return;
        var player = plugin.getServer().getPlayer(actor);
        boolean admin =
                player != null
                        ? player.hasPermission("esmp.admin")
                        : plugin.getServer().getOfflinePlayer(actor).isOp();
        if (admin) administrators.add(actor);
        else administrators.remove(actor);
    }

    public SettlementView find(String reference) {
        var found = index.byName(reference);
        if (found != null) return found;
        try {
            return index.byId(UUID.fromString(reference));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            long number =
                    Long.parseLong(reference.startsWith("#") ? reference.substring(1) : reference);
            for (var e : land.settlements().entrySet())
                if (e.getValue().number() == number) return index.byId(e.getKey());
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    public SettlementIndex index() {
        return index;
    }

    public ClaimVisualization visualization() {
        return visualization;
    }

    public GameClock clock() {
        return clock;
    }

    public ActivityService activity() {
        return activity;
    }

    public boolean ready() {
        return ready && !closed;
    }

    public void lockdown() {
        ready = false;
    }

    public boolean isMain(World world) {
        return world != null
                && (world.getName().equals(mainWorldName) || mainInstance(world) != null);
    }

    public UUID mainInstance(World world) {
        if (world == null) {
            return null;
        }
        for (var instance : database.snapshot().instances()) {
            if (instance.definitionId().equals("MAIN")
                    && instance.bukkitWorldId().equals(world.getUID())) {
                return instance.instanceId();
            }
        }
        return null;
    }

    public SettlementView at(Location location) {
        UUID world = mainInstance(location.getWorld());
        return world == null
                ? null
                : index.at(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public boolean available(Location location) {
        if (!ready()) {
            return false;
        }
        if (!isMain(location.getWorld())) {
            return true;
        }
        UUID world = mainInstance(location.getWorld());
        if (world == null) {
            return false;
        }
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        if (reservation != null
                && reservation.world().equals(world)
                && reservation.chunk().x() == x
                && reservation.chunk().z() == z) {
            return false;
        }
        SettlementView settlement = index.at(world, x, z);
        return settlement == null || !settlement.id().equals(frozenSettlement);
    }

    public boolean allows(UUID actor, Location location, ParcelAction action, boolean safePublic) {
        if (!ready() || !available(location)) return false;
        if (!isMain(location.getWorld())) return true;
        boolean admin = actor != null && administrator(actor);
        if (admin && bypass.contains(actor)) return true;
        var settlement = at(location);
        if (settlement == null) return true;
        if (!admin && land.banned(settlement.id(), actor)) return false;
        var parcel =
                land.at(
                        settlement.id(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ());
        if (parcel != null) return parcel.allows(actor, settlement.role(actor), action);
        return settlement.role(actor).canBuild() || (action == ParcelAction.INTERACT && safePublic);
    }

    public boolean sameRegion(Location source, Location destination) {
        if (source == null || destination == null) {
            Location known = source == null ? destination : source;
            return known == null || (available(known) && at(known) == null);
        }
        if (!available(source) || !available(destination)) return false;
        var from = at(source);
        var to = at(destination);
        var a =
                from == null
                        ? null
                        : land.at(
                                from.id(),
                                source.getBlockX(),
                                source.getBlockY(),
                                source.getBlockZ());
        var b =
                to == null
                        ? null
                        : land.at(
                                to.id(),
                                destination.getBlockX(),
                                destination.getBlockY(),
                                destination.getBlockZ());
        boolean sameSettlement =
                from == null ? to == null : to != null && from.id().equals(to.id());
        if (sameSettlement && (a == null ? b == null : b != null && a.id().equals(b.id())))
            return a == null || a.internalAutomation();
        // Unattributed boundary-crossing mechanics require explicit PUBLIC automation on both
        // protected sides.
        return (from == null
                        || (a != null
                                && a.policy()
                                        .get(ParcelAction.AUTOMATION)
                                        .contains(ParcelRelation.PUBLIC)))
                && (to == null
                        || (b != null
                                && b.policy()
                                        .get(ParcelAction.AUTOMATION)
                                        .contains(ParcelRelation.PUBLIC)));
    }

    public boolean bypass(Player player, boolean enabled) {
        if (!player.hasPermission("esmp.admin")) {
            throw new SettlementException("Administrator permission required.");
        }
        if (enabled) {
            bypass.add(player.getUniqueId());
        } else {
            bypass.remove(player.getUniqueId());
        }
        plugin.getLogger()
                .warning(player.getName() + " set explicit claim protection bypass=" + enabled);
        return enabled;
    }

    public void forget(Player player) {
        bypass.remove(player.getUniqueId());
        querying.remove(player.getUniqueId());
        visualization.forget(player.getUniqueId());
        administrators.remove(player.getUniqueId());
        if (tools != null) tools.forget(player.getUniqueId());
    }

    public void mutate(
            Player player,
            UUID settlementId,
            Reservation reserved,
            Supplier<CompletableFuture<SettlementChange>> operation) {
        if (!ready()) {
            throw new SettlementException(
                    "Settlement protection is still initializing or unavailable.");
        }
        if (mutationInFlight) {
            throw new SettlementException(
                    "Another Settlement change is committing; retry shortly.");
        }
        mutationInFlight = true;
        frozenSettlement = settlementId;
        reservation = reserved;
        UUID actor = player.getUniqueId();
        rememberAdministrator(actor);
        try {
            operation
                    .get()
                    .thenCompose(
                            change ->
                                    parcels.land(change.settlementId())
                                            .thenApply(value -> new Publication(change, value)))
                    .whenComplete(
                            (publication, failure) ->
                                    main(
                                            () -> {
                                                SettlementChange change =
                                                        publication == null
                                                                ? null
                                                                : publication.change();
                                                if (failure == null) {
                                                    var updated =
                                                            new java.util.HashMap<>(
                                                                    land.settlements());
                                                    if (publication.land() == null)
                                                        updated.remove(change.settlementId());
                                                    else
                                                        updated.put(
                                                                change.settlementId(),
                                                                publication.land());
                                                    land = new LandSnapshot(updated);
                                                    publicationVersion++;
                                                    index.replace(
                                                            change.settlementId(),
                                                            change.current());
                                                }
                                                mutationInFlight = false;
                                                frozenSettlement = null;
                                                reservation = null;
                                                Player currentPlayer =
                                                        plugin.getServer().getPlayer(actor);
                                                if (failure != null) {
                                                    report(currentPlayer, failure);
                                                    return;
                                                }
                                                closeUnauthorizedInventories();
                                                if (currentPlayer != null) {
                                                    currentPlayer.sendMessage(
                                                            Component.text(change.message()));
                                                    if (change.current() != null
                                                            && change.current()
                                                                    .worldInstanceId()
                                                                    .equals(
                                                                            mainInstance(
                                                                                    currentPlayer
                                                                                            .getWorld()))) {
                                                        visualization.show(
                                                                currentPlayer, change.current());
                                                    }
                                                }
                                                if (change.offer() != null) {
                                                    var offer = change.offer();
                                                    Player recipient =
                                                            plugin.getServer()
                                                                    .getPlayer(offer.recipient());
                                                    String verb =
                                                            offer.type()
                                                                            == xdu.undarubra.esmp
                                                                                    .civilization
                                                                                    .settlement
                                                                                    .SettlementOffer
                                                                                    .Type.ABANDON
                                                                    ? "confirm"
                                                                    : "accept";
                                                    Component action =
                                                            Component.text(
                                                                            "/settlement "
                                                                                    + verb
                                                                                    + " "
                                                                                    + offer.id())
                                                                    .clickEvent(
                                                                            ClickEvent.runCommand(
                                                                                    "/settlement "
                                                                                            + verb
                                                                                            + " "
                                                                                            + offer
                                                                                                    .id()));
                                                    if (recipient != null) {
                                                        recipient.sendMessage(
                                                                Component.text(
                                                                                offer.type()
                                                                                        + " for "
                                                                                        + change.current()
                                                                                                .name()
                                                                                        + "; expires"
                                                                                        + " at game"
                                                                                        + " tick "
                                                                                        + offer
                                                                                                .expiresAtTick()
                                                                                        + ". ")
                                                                        .append(action));
                                                    }
                                                    if (currentPlayer != null
                                                            && (recipient == null
                                                                    || !recipient
                                                                            .getUniqueId()
                                                                            .equals(actor))) {
                                                        currentPlayer.sendMessage(
                                                                Component.text(
                                                                        "Offer ID: " + offer.id()));
                                                    }
                                                }
                                            }));
        } catch (RuntimeException failure) {
            mutationInFlight = false;
            frozenSettlement = null;
            reservation = null;
            report(player, failure);
        }
    }

    public <T> void query(
            Player player, Supplier<CompletableFuture<T>> operation, Consumer<T> result) {
        if (!ready()) {
            throw new SettlementException("Settlements are not ready.");
        }
        UUID actor = player.getUniqueId();
        if (!querying.add(actor)) {
            throw new SettlementException("Your previous query is still running.");
        }
        try {
            operation
                    .get()
                    .whenComplete(
                            (value, failure) ->
                                    main(
                                            () -> {
                                                querying.remove(actor);
                                                Player current =
                                                        plugin.getServer().getPlayer(actor);
                                                if (failure != null) {
                                                    report(current, failure);
                                                } else if (current != null) {
                                                    try {
                                                        result.accept(value);
                                                    } catch (SettlementException rejected) {
                                                        current.sendMessage(
                                                                Component.text(
                                                                        rejected.getMessage()));
                                                    }
                                                }
                                            }));
        } catch (RuntimeException failure) {
            querying.remove(actor);
            report(player, failure);
        }
    }

    private void closeUnauthorizedInventories() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (Location location :
                    ClaimProtection.inventoryLocations(
                            player.getOpenInventory().getTopInventory())) {
                if (!allows(player.getUniqueId(), location, ParcelAction.CONTAINER, false)) {
                    player.closeInventory();
                    break;
                }
            }
        }
    }

    private void dispatchOutbox() {
        if (!ready() || outboxInFlight) {
            return;
        }
        outboxInFlight = true;
        service.pendingEvents()
                .whenComplete(
                        (events, failure) ->
                                main(
                                        () -> {
                                            if (failure != null) {
                                                outboxInFlight = false;
                                                report(null, failure);
                                                return;
                                            }
                                            for (var event : events) {
                                                plugin.getServer()
                                                        .getPluginManager()
                                                        .callEvent(
                                                                new SettlementCommittedEvent(
                                                                        event));
                                            }
                                            if (events.isEmpty()) {
                                                outboxInFlight = false;
                                                return;
                                            }
                                            service.acknowledgeEvents(
                                                            events.stream()
                                                                    .map(
                                                                            xdu.undarubra.esmp
                                                                                            .civilization
                                                                                            .settlement
                                                                                            .SettlementEvent
                                                                                    ::id)
                                                                    .toList())
                                                    .whenComplete(
                                                            (ignored, ackFailure) ->
                                                                    main(
                                                                            () -> {
                                                                                outboxInFlight =
                                                                                        false;
                                                                                if (ackFailure
                                                                                        != null) {
                                                                                    report(
                                                                                            null,
                                                                                            ackFailure);
                                                                                }
                                                                            }));
                                        }));
    }

    private void report(Player player, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof SettlementException || cause instanceof RejectedExecutionException) {
            if (player != null) {
                player.sendMessage(Component.text(cause.getMessage()));
            }
        } else {
            ready = false;
            fatal.accept(cause);
        }
    }

    private void main(Runnable task) {
        if (closed) {
            return;
        }
        try {
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (!closed) {
                                    task.run();
                                }
                            });
        } catch (IllegalPluginAccessException stopped) {
            if (!closed) {
                plugin.getLogger()
                        .severe(
                                "Settlement callback could not return to the server thread: "
                                        + stopped.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (tools != null) tools.close();
        activityListener.close();
        closed = true;
        ready = false;
        if (outboxTask != null) {
            outboxTask.cancel();
        }
        visualization.close();
        bypass.clear();
        querying.clear();
    }
}
