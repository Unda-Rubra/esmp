package xdu.undarubra.esmp.plugin;

import net.kyori.adventure.text.Component;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import xdu.undarubra.esmp.civilization.CivilizationWorlds;
import xdu.undarubra.esmp.civilization.activity.ActivityPolicy;
import xdu.undarubra.esmp.civilization.settlement.SettlementPolicy;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.StoreSnapshot;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldInstance;
import xdu.undarubra.esmp.plugin.settlement.ClaimProtection;
import xdu.undarubra.esmp.plugin.settlement.SettlementCommands;
import xdu.undarubra.esmp.plugin.settlement.SettlementRuntime;
import xdu.undarubra.esmp.resource.ResourceWorlds;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

/** Single composition root for world registration and playable Settlement protection. */
public final class EsmpPlugin extends JavaPlugin implements Listener {
    private enum State {
        STARTING,
        READY,
        FAILED,
        STOPPED
    }

    private volatile boolean stopping;
    private State state = State.STOPPED;
    private WorldStore store;
    private SettlementRuntime settlements;
    private GameClock gameClock;
    private GameTimeRuntime gameTime;
    private Map<String, WorldDefinition> definitions = Map.of();
    private Map<String, WorldDefinition> configuredWorlds = Map.of();

    @Override
    public void onEnable() {
        stopping = false;
        state = State.STARTING;
        try {
            saveDefaultConfig();
            migrateConfiguration();
            definitions = collectDefinitions();
            configuredWorlds = readWorldMappings();
            var initialBindings = new ArrayList<WorldBinding>();
            for (World world : getServer().getWorlds()) {
                WorldBinding binding = captureBinding(world);
                if (binding != null) {
                    initialBindings.add(binding);
                }
            }
            Objects.requireNonNull(getCommand("esmp"), "Missing esmp command").setExecutor(this);
            getServer().getPluginManager().registerEvents(this, this);
            store = new WorldStore(getDataFolder().toPath().resolve("esmp.db"));
            gameClock = new GameClock(store);
            gameTime =
                    new GameTimeRuntime(
                            this,
                            getServer().getWorld(getConfig().getString("worlds.MAIN")),
                            gameClock,
                            failure -> fail("Game clock failed", failure));
            settlements =
                    new SettlementRuntime(
                            this,
                            store,
                            getConfig().getString("worlds.MAIN"),
                            readSettlementPolicy(),
                            gameClock,
                            readActivityPolicy(),
                            failure -> fail("Settlement storage or publication failed", failure));
            var settlementCommand = new SettlementCommands(this, settlements);
            var command =
                    Objects.requireNonNull(getCommand("settlement"), "Missing settlement command");
            command.setExecutor(settlementCommand);
            command.setTabCompleter(settlementCommand);
            getServer()
                    .getPluginManager()
                    .registerEvents(new ClaimProtection(this, settlements), this);
            getServer().getPluginManager().registerEvents(settlements.visualization(), this);
            store.initialize(List.copyOf(definitions.values()), initialBindings)
                    .thenCompose(ignored -> gameTime.initialize())
                    .thenCompose(ignored -> settlements.initialize())
                    .whenComplete(
                            (ownedSettlements, failure) ->
                                    onServerThread(
                                            () -> {
                                                if (failure != null) {
                                                    fail(
                                                            "SQLite/Settlement initialization"
                                                                + " failed",
                                                            failure);
                                                    return;
                                                }
                                                gameTime.start();
                                                settlements.activate(ownedSettlements);
                                                state = State.READY;
                                                StoreSnapshot snapshot = store.snapshot();
                                                getLogger()
                                                        .info(
                                                                "ESMP READY: schema="
                                                                        + snapshot.schemaVersion()
                                                                        + ", definitions="
                                                                        + snapshot.definitions()
                                                                                .size()
                                                                        + ", instances="
                                                                        + snapshot.instances()
                                                                                .size()
                                                                        + ", settlements="
                                                                        + settlements.index().size()
                                                                        + ". Settlement protection"
                                                                        + " enabled.");
                                            }));
        } catch (RuntimeException failure) {
            fail("ESMP startup failed", failure);
        }
    }

    private static Map<String, WorldDefinition> collectDefinitions() {
        var result = new LinkedHashMap<String, WorldDefinition>();
        var all = new ArrayList<WorldDefinition>();
        all.addAll(CivilizationWorlds.definitions());
        all.addAll(ResourceWorlds.definitions());
        for (WorldDefinition definition : all) {
            if (result.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate definition: " + definition.id());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private void migrateConfiguration() {
        Object version = getConfig().get("config-version", 1);
        if (!(version instanceof Integer number) || number < 1 || number > 4) {
            throw new IllegalArgumentException("Unsupported ESMP config-version: " + version);
        }
        if (number == 1) {
            var original = getDataFolder().toPath().resolve("config.yml");
            var backup = getDataFolder().toPath().resolve("config-v1.yml");
            try {
                if (!Files.exists(backup)) {
                    Files.copy(original, backup);
                }
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not back up the previous configuration", failure);
            }
            getConfig().set("worlds.FACTORY", null);
            getConfig().set("config-version", 2);
            saveConfig();
            getLogger()
                    .info(
                            "Migrated configuration to version 2; former Factory world mapping"
                                + " retired. World data is untouched.");
        }
        if (number < 3) {
            try {
                var backup = getDataFolder().toPath().resolve("config-before-game-clock.yml");
                if (!Files.exists(backup)) {
                    Files.copy(getDataFolder().toPath().resolve("config.yml"), backup);
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot back up timer configuration", failure);
            }
            String[] names = {"invitation", "transfer", "promotion", "abandon"};
            long[] fallback = {86400, 1800, 86400, 60};
            for (int i = 0; i < names.length; i++) {
                long ticks =
                        Math.multiplyExact(
                                positiveSetting(
                                        "settlements." + names[i] + "-seconds", fallback[i]),
                                20);
                getConfig().set("settlements." + names[i] + "-ticks", ticks);
                getConfig().set("settlements." + names[i] + "-seconds", null);
            }
            getConfig().set("config-version", 3);
            saveConfig();
        }
        if (number < 4) {
            try {
                var backup = getDataFolder().toPath().resolve("config-before-parcels.yml");
                if (!Files.exists(backup))
                    Files.copy(getDataFolder().toPath().resolve("config.yml"), backup);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot back up Parcel configuration", failure);
            }
            getConfig()
                    .set(
                            "activity.public-credit",
                            getConfig().get("activity.visitor-credit", 0.02));
            getConfig().set("activity.visitor-credit", null);
            getConfig()
                    .set(
                            "settlements.claim-base-price",
                            getConfig().get("settlements.claim-base-price", 100));
            getConfig()
                    .set(
                            "settlements.claim-price-step",
                            getConfig().get("settlements.claim-price-step", 25));
            getConfig().set("config-version", 4);
            saveConfig();
        }
    }

    private SettlementPolicy readSettlementPolicy() {
        Object enabled = getConfig().get("settlements.chunk-cap-enabled", true);
        if (!(enabled instanceof Boolean capEnabled)) {
            throw new IllegalArgumentException("settlements.chunk-cap-enabled must be a boolean");
        }
        long cap = positiveSetting("settlements.max-chunks", 64);
        if (cap > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Settlement chunk cap is too large");
        }
        return new SettlementPolicy(
                capEnabled,
                (int) cap,
                positiveSetting("settlements.invitation-ticks", 1_728_000),
                positiveSetting("settlements.transfer-ticks", 36_000),
                positiveSetting("settlements.promotion-ticks", 1_728_000),
                positiveSetting("settlements.abandon-ticks", 1_200),
                positiveSetting("settlements.claim-base-price", 100),
                positiveSetting("settlements.claim-price-step", 25));
    }

    private long positiveSetting(String path, long fallback) {
        Object value = getConfig().get(path, fallback);
        if (!(value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() <= 0) {
            throw new IllegalArgumentException(path + " must be a positive integer");
        }
        long number = ((Number) value).longValue();
        return number;
    }

    private ActivityPolicy readActivityPolicy() {
        var p = ActivityPolicy.defaults();
        return new ActivityPolicy(
                activityNumber("max-score", p.maxScore()),
                activityNumber("half-life-days", p.halfLifeDays()),
                activityNumber("inactive-threshold", p.inactiveThreshold()),
                activityNumber("recovery-threshold", p.recoveryThreshold()),
                activityNumber("initial-score", p.initialScore()),
                activityNumber("build-credit", p.buildCredit()),
                activityNumber("interaction-credit", p.interactionCredit()),
                activityNumber("public-credit", p.publicCredit()),
                activityNumber("player-daily-cap", p.playerDailyCap()),
                activityNumber("settlement-daily-cap", p.settlementDailyCap()),
                positiveSetting("activity.grace-ticks", p.graceTicks()),
                positiveSetting("activity.action-cooldown-ticks", p.actionCooldownTicks()));
    }

    private double activityNumber(String name, double fallback) {
        Object value = getConfig().get("activity." + name, fallback);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("activity." + name + " must be numeric");
        }
        return number.doubleValue();
    }

    private Map<String, WorldDefinition> readWorldMappings() {
        ConfigurationSection section = getConfig().getConfigurationSection("worlds");
        if (section == null) {
            throw new IllegalArgumentException("config.yml must contain a worlds mapping");
        }
        var result = new LinkedHashMap<String, WorldDefinition>();
        for (String id : section.getKeys(false)) {
            WorldDefinition definition = definitions.get(id);
            Object value = section.get(id);
            if (definition == null || !(value instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException(
                        "Unknown definition or invalid world name: " + id);
            }
            if (result.putIfAbsent(name, definition) != null) {
                throw new IllegalArgumentException("Physical world name assigned twice: " + name);
            }
        }
        for (String id : definitions.keySet()) {
            if (!section.contains(id)) {
                throw new IllegalArgumentException("Missing world mapping: " + id);
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private WorldBinding captureBinding(World world) {
        WorldDefinition definition = configuredWorlds.get(world.getName());
        if (definition == null) {
            return null;
        }
        if (!definition.environment().name().equals(world.getEnvironment().name())) {
            throw new IllegalArgumentException(
                    "World "
                            + world.getName()
                            + " must use environment "
                            + definition.environment()
                            + " for "
                            + definition.id());
        }
        return new WorldBinding(definition.id(), world.getUID(), world.getName());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (stopping || store == null || state == State.FAILED) {
            return;
        }
        try {
            WorldBinding binding = captureBinding(event.getWorld());
            if (binding == null) {
                return;
            }
            store.register(binding)
                    .whenComplete(
                            (instance, failure) ->
                                    onServerThread(
                                            () -> {
                                                if (failure != null) {
                                                    fail("World registration failed", failure);
                                                } else {
                                                    getLogger()
                                                            .info(
                                                                    "Registered "
                                                                            + instance
                                                                                    .definitionId()
                                                                            + " instance="
                                                                            + instance
                                                                                    .instanceId());
                                                }
                                            }));
        } catch (RuntimeException failure) {
            fail("Invalid loaded world mapping", failure);
        }
    }

    private void onServerThread(Runnable action) {
        if (stopping) {
            return;
        }
        try {
            getServer()
                    .getScheduler()
                    .runTask(
                            this,
                            () -> {
                                if (!stopping && isEnabled() && state != State.FAILED) {
                                    action.run();
                                }
                            });
        } catch (IllegalPluginAccessException shutdownRace) {
            if (!stopping) {
                getLogger()
                        .log(Level.SEVERE, "Could not dispatch completion to Paper", shutdownRace);
            }
        }
    }

    private void fail(String message, Throwable failure) {
        state = State.FAILED;
        Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
        getLogger().log(Level.SEVERE, message, cause);
        if (settlements != null) {
            settlements.lockdown();
        }
        getLogger()
                .severe(
                        "Stopping the server rather than exposing Main World without reliable claim"
                            + " protection.");
        getServer().shutdown();
    }

    @Override
    public void onDisable() {
        stopping = true;
        getServer().getScheduler().cancelTasks(this);
        if (settlements != null) {
            if (!getServer().isStopping()) {
                getLogger()
                        .severe(
                                "Disabling ESMP protection requires a server stop; hot reload is"
                                    + " unsupported.");
                getServer().shutdown();
            }
            settlements.close();
        }
        if (gameTime != null && gameClock != null) {
            try {
                gameTime.close();
            } catch (RuntimeException failure) {
                getLogger().log(Level.SEVERE, "Game clock shutdown failed", failure);
            }
        }
        if (store != null) {
            try {
                store.close();
            } catch (RuntimeException failure) {
                getLogger().log(Level.SEVERE, "SQLite shutdown failed", failure);
            }
        }
        if (state != State.FAILED) {
            state = State.STOPPED;
        }
        getLogger().info("ESMP stopped.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("esmp.admin")) {
            sender.sendMessage(Component.text("You do not have permission to inspect ESMP."));
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("Usage: /" + label + " status"));
            return true;
        }
        sender.sendMessage(
                Component.text("ESMP state=" + state + " | Java " + Runtime.version().feature()));
        if (store == null) {
            sender.sendMessage(Component.text("Storage is not initialized."));
            return true;
        }
        StoreSnapshot snapshot = store.snapshot();
        sender.sendMessage(
                Component.text(
                        "SQLite schema="
                                + snapshot.schemaVersion()
                                + " | definitions="
                                + snapshot.definitions().size()
                                + " | active-definition instances="
                                + snapshot.instances().size()));
        for (String module : List.of("civilization", "resource")) {
            long count =
                    snapshot.definitions().stream()
                            .filter(d -> d.moduleId().equals(module))
                            .count();
            sender.sendMessage(
                    Component.text("Module " + module + ": " + count + " world definitions"));
        }
        for (Map.Entry<String, WorldDefinition> mapping : configuredWorlds.entrySet()) {
            World world = getServer().getWorld(mapping.getKey());
            String instanceId = "not registered";
            if (world != null) {
                for (WorldInstance instance : snapshot.instances()) {
                    if (instance.bukkitWorldId().equals(world.getUID())
                            && instance.definitionId().equals(mapping.getValue().id())) {
                        instanceId = instance.instanceId().toString();
                        break;
                    }
                }
            }
            sender.sendMessage(
                    Component.text(
                            mapping.getValue().id()
                                    + " -> "
                                    + mapping.getKey()
                                    + " | "
                                    + (world == null ? "not loaded" : "loaded")
                                    + " | instance="
                                    + instanceId));
        }
        sender.sendMessage(
                Component.text(
                        "Settlements="
                                + (settlements == null ? 0 : settlements.index().size())
                                + " | protection="
                                + (settlements != null && settlements.ready())));
        sender.sendMessage(
                Component.text(
                        "Game tick="
                                + (gameClock == null ? 0 : gameClock.ticks())
                                + " | advancing="
                                + (gameClock != null && gameClock.advancing())));
        sender.sendMessage(
                Component.text(
                        "Implemented: claims, game-time offers, Activity, history, borders. Decay,"
                            + " repair, and Resource resets remain disabled."));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (sender.hasPermission("esmp.admin")
                && args.length == 1
                && "status".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            return List.of("status");
        }
        return List.of();
    }
}
