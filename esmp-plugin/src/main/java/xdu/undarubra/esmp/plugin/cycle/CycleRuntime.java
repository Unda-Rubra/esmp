package xdu.undarubra.esmp.plugin.cycle;

import net.kyori.adventure.text.Component;

import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.civilization.cycle.CycleService;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.plugin.settlement.SettlementRuntime;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class CycleRuntime implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final CycleService service;
    private final Consumer<Throwable> fatal;
    private BukkitTask task;
    private boolean busy, closed;
    private long next;
    private final long interval;

    public CycleRuntime(
            JavaPlugin plugin,
            SettlementRuntime runtime,
            CycleService service,
            long interval,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.service = service;
        this.interval = interval;
        this.fatal = fatal;
    }

    public CycleService service() {
        return service;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getConfig().getBoolean("cycle.enabled", true))
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private void main(Runnable action) {
        if (!closed)
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (!closed) action.run();
                            });
    }

    private void tick() {
        if (busy
                || !runtime.clock().advancing()
                || !runtime.buildingCommitAvailable()
                || runtime.clock().ticks() < next) return;
        next = Math.addExact(runtime.clock().ticks(), interval);
        busy = true;
        service.due()
                .whenComplete(
                        (ids, error) ->
                                main(
                                        () -> {
                                            if (error != null) {
                                                busy = false;
                                                fatal.accept(error);
                                                return;
                                            }
                                            if (ids.isEmpty()) {
                                                busy = false;
                                                return;
                                            }
                                            UUID id = ids.getFirst();
                                            var view = runtime.index().byId(id);
                                            if (view == null) {
                                                busy = false;
                                                return;
                                            }
                                            var audience = new HashSet<>(view.members().keySet());
                                            audience.add(view.owner());
                                            var change = new AtomicReference<SettlementChange>();
                                            try {
                                                runtime.mutateDecay(
                                                                id,
                                                                () ->
                                                                        service.advance(id)
                                                                                .thenApply(
                                                                                        c -> {
                                                                                            change
                                                                                                    .set(
                                                                                                            c);
                                                                                            return c;
                                                                                        }))
                                                        .whenComplete(
                                                                (ignored, failure) ->
                                                                        main(
                                                                                () -> {
                                                                                    busy = false;
                                                                                    if (failure
                                                                                                    == null
                                                                                            && change
                                                                                                            .get()
                                                                                                    != null) {
                                                                                        for (var
                                                                                                who :
                                                                                                        audience) {
                                                                                            var
                                                                                                    player =
                                                                                                            plugin.getServer()
                                                                                                                    .getPlayer(
                                                                                                                            who);
                                                                                            if (player
                                                                                                    != null)
                                                                                                player
                                                                                                        .sendMessage(
                                                                                                                Component
                                                                                                                        .text(
                                                                                                                                change.get()
                                                                                                                                        .message()));
                                                                                        }
                                                                                    }
                                                                                }));
                                            } catch (SettlementException retry) {
                                                busy = false;
                                            }
                                        }));
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        UUID actor = event.getPlayer().getUniqueId();
        service.notices(actor)
                .whenComplete(
                        (notices, error) ->
                                main(
                                        () -> {
                                            if (error != null) {
                                                fatal.accept(error);
                                                return;
                                            }
                                            var player = plugin.getServer().getPlayer(actor);
                                            if (player != null)
                                                for (var n : notices)
                                                    player.sendMessage(
                                                            Component.text(
                                                                    "Land release notice: "
                                                                            + n.settlement()
                                                                            + " · "
                                                                            + n.chunks()
                                                                            + " chunks · due game"
                                                                            + " tick "
                                                                            + n.dueTick()
                                                                            + ". /settlement cycle "
                                                                            + n.settlement()));
                                        }));
    }

    @Override
    public void close() {
        closed = true;
        if (task != null) task.cancel();
        HandlerList.unregisterAll(this);
    }
}
