package xdu.undarubra.esmp.plugin.settlement;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.civilization.settlement.SettlementView;

/** Temporary particles sent to the requester only; no block scans or chunk loading. */
public final class ClaimVisualization implements Listener, AutoCloseable {
    private record Request(UUID settlementId, long expiresAt) { }
    private static final Particle.DustOptions TEAM = new Particle.DustOptions(Color.LIME, 1.0f);
    private static final Particle.DustOptions PUBLIC = new Particle.DustOptions(Color.RED, 1.0f);
    private static final Particle.DustOptions CURRENT = new Particle.DustOptions(Color.WHITE, 0.7f);
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final Map<UUID, Request> requests = new HashMap<>();
    private BukkitTask task;

    public ClaimVisualization(JavaPlugin plugin, SettlementRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::render, 1L, 10L);
    }

    public void show(Player player, SettlementView settlement) {
        UUID world = runtime.mainInstance(player.getWorld());
        if (world == null) { throw new SettlementException("Borders are available in the registered Main World only."); }
        if (settlement != null && !world.equals(settlement.worldInstanceId())) {
            throw new SettlementException("That Settlement is in a different physical world instance.");
        }
        requests.put(player.getUniqueId(), new Request(settlement == null ? null : settlement.id(), Math.addExact(runtime.clock().ticks(), 200)));
        describe(player, player.getLocation());
    }

    public void forget(UUID player) { requests.remove(player); }

    private void render() {
        long now = runtime.clock().ticks();
        Iterator<Map.Entry<UUID, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || now >= entry.getValue().expiresAt() || !runtime.ready()
                    || runtime.mainInstance(player.getWorld()) == null) {
                iterator.remove();
                continue;
            }
            World world = player.getWorld();
            UUID instance = runtime.mainInstance(world);
            Location position = player.getLocation();
            int centerX = position.getBlockX() >> 4;
            int centerZ = position.getBlockZ() >> 4;
            double y = Math.max(world.getMinHeight() + 0.2, Math.min(world.getMaxHeight() - 0.2, position.getY() + 0.15));
            int remaining = 256;
            remaining = square(player, CURRENT, centerX * 16, centerZ * 16, y, remaining);
            for (int x = centerX - 2; x <= centerX + 2 && remaining > 0; x++) {
                for (int z = centerZ - 2; z <= centerZ + 2 && remaining > 0; z++) {
                    if (!world.isChunkLoaded(x, z)) { continue; }
                    SettlementView settlement = runtime.index().at(instance, x, z);
                    if (settlement == null || (entry.getValue().settlementId() != null
                            && !settlement.id().equals(entry.getValue().settlementId()))) { continue; }
                    Particle.DustOptions color = settlement.role(player.getUniqueId()).canBuild() ? TEAM : PUBLIC;
                    if (!same(settlement, runtime.index().at(instance, x - 1, z))) {
                        remaining = edge(player, color, x * 16, z * 16, 0, 1, y, remaining);
                    }
                    if (!same(settlement, runtime.index().at(instance, x + 1, z))) {
                        remaining = edge(player, color, (x + 1) * 16, z * 16, 0, 1, y, remaining);
                    }
                    if (!same(settlement, runtime.index().at(instance, x, z - 1))) {
                        remaining = edge(player, color, x * 16, z * 16, 1, 0, y, remaining);
                    }
                    if (!same(settlement, runtime.index().at(instance, x, z + 1))) {
                        remaining = edge(player, color, x * 16, (z + 1) * 16, 1, 0, y, remaining);
                    }
                }
            }
        }
    }

    private static boolean same(SettlementView first, SettlementView second) {
        return second != null && first.id().equals(second.id());
    }

    private static int square(Player player, Particle.DustOptions color, int x, int z, double y, int remaining) {
        remaining = edge(player, color, x, z, 1, 0, y, remaining);
        remaining = edge(player, color, x, z + 16, 1, 0, y, remaining);
        remaining = edge(player, color, x, z, 0, 1, y, remaining);
        return edge(player, color, x + 16, z, 0, 1, y, remaining);
    }

    private static int edge(Player player, Particle.DustOptions color, int x, int z, int dx, int dz, double y, int remaining) {
        for (int distance = 0; distance <= 16 && remaining > 0; distance += 2, remaining--) {
            player.spawnParticle(Particle.DUST, x + distance * dx, y, z + distance * dz, 1, 0, 0, 0, 0, color);
        }
        return remaining;
    }

    private void describe(Player player, Location location) {
        if (!runtime.isMain(location.getWorld())) { return; }
        SettlementView settlement = runtime.at(location);
        if (!runtime.ready()) {
            player.sendActionBar(Component.text("Settlement protection initializing"));
        } else if (settlement == null) {
            player.sendActionBar(Component.text("Wilderness — unclaimed"));
        } else {
            String owner = plugin.getServer().getOfflinePlayer(settlement.owner()).getName();
            player.sendActionBar(Component.text(settlement.name() + " | Owner: "
                    + (owner == null ? settlement.owner() : owner) + " | " + settlement.role(player.getUniqueId())
                    + " | " + settlement.chunks().size() + " chunks"));
        }
    }

    private void describeTransition(Player player, Location from, Location to) {
        if (!runtime.ready()) { return; }
        SettlementView previous = runtime.at(from);
        SettlementView next = runtime.at(to);
        if (previous == null ? next == null : same(previous, next)) { return; }
        if (next == null) {
            player.sendActionBar(Component.text("Left " + previous.name()));
        } else {
            describe(player, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) { return; }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld() || (from.getBlockX() >> 4) != (to.getBlockX() >> 4)
                || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4)) {
            describeTransition(event.getPlayer(), from, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        describeTransition(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        describeTransition(event.getPlayer(), event.getPlayer().getLocation(), event.getRespawnLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { runtime.forget(event.getPlayer()); }

    @Override
    public void close() {
        if (task != null) { task.cancel(); }
        requests.clear();
    }
}
