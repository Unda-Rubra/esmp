package xdu.undarubra.esmp.plugin.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.economy.LedgerService;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Main-thread eligibility/feedback adapter; monetary admission and retries are durable SQL work.
 */
public final class StartingFundsRuntime implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final LedgerService ledger;
    private final long amount;
    private final Consumer<Throwable> fatal;
    private final LinkedHashMap<UUID, Boolean> joins = new LinkedHashMap<>();
    private BukkitTask task;
    private boolean active, joining, retrying, morePending;
    private long scannedPeriod = -1;
    private volatile boolean closed;

    public StartingFundsRuntime(
            JavaPlugin plugin, LedgerService ledger, long amount, Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.amount = amount;
        this.fatal = fatal;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        if (closed) return;
        // Capture the first-join fact now, not after asynchronous persistence or on reconnect.
        joins.merge(
                event.getPlayer().getUniqueId(),
                !event.getPlayer().hasPlayedBefore(),
                Boolean::logicalOr);
        if (active) flushJoin();
    }

    public void start() {
        active = true;
        flushJoin();
        task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    flushJoin();
                                    if (retrying
                                            || (!morePending
                                                    && scannedPeriod == ledger.monetaryPeriod()))
                                        return;
                                    retrying = true;
                                    complete(
                                            ledger.retryStartingGrants(),
                                            batch -> {
                                                retrying = false;
                                                scannedPeriod = batch.period();
                                                morePending =
                                                        batch.processed()
                                                                == LedgerService
                                                                        .STARTING_GRANT_BATCH_SIZE;
                                                for (var grant : batch.paid()) feedback(grant);
                                            },
                                            () -> retrying = false);
                                },
                                1,
                                20);
    }

    private void flushJoin() {
        if (closed || !active || joining || joins.isEmpty()) return;
        var entry = joins.entrySet().iterator().next();
        UUID player = entry.getKey();
        joining = true;
        complete(
                ledger.startingGrant(player, entry.getValue(), amount),
                grant -> {
                    joining = false;
                    joins.remove(player);
                    if (grant != null) feedback(grant);
                    flushJoin();
                },
                () -> joining = false);
    }

    private void feedback(LedgerService.StartingGrant grant) {
        var player = plugin.getServer().getPlayer(grant.player());
        if (player == null) return;
        if (grant.issuedNow()) {
            player.sendMessage(
                    Component.text(
                                    "Welcome! Starting funds: +"
                                            + ledger.currency().display(grant.amount())
                                            + ". Balance: "
                                            + ledger.currency().display(grant.balanceAfter())
                                            + ". ")
                            .append(
                                    Component.text("[Balance]")
                                            .clickEvent(ClickEvent.runCommand("/balance"))));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .25f, 1.2f);
        } else if (!grant.paid()) {
            player.sendMessage(
                    Component.text(
                            "Starting funds: "
                                    + ledger.currency().display(grant.amount())
                                    + " pending. We'll retry automatically."));
        }
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
                                    .warning("Starting-fund feedback interrupted by shutdown");
                    }
                });
    }

    @Override
    public void close() {
        closed = true;
        active = false;
        if (task != null) task.cancel();
        joins.clear();
    }
}
