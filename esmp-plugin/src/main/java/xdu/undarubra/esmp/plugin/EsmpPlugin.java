package xdu.undarubra.esmp.plugin;

import net.kyori.adventure.text.Component;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import xdu.undarubra.esmp.civilization.CivilizationWorlds;
import xdu.undarubra.esmp.civilization.activity.ActivityPolicy;
import xdu.undarubra.esmp.civilization.land.LandValuePolicy;
import xdu.undarubra.esmp.civilization.settlement.ClaimPricingPolicy;
import xdu.undarubra.esmp.civilization.settlement.ClaimQuote;
import xdu.undarubra.esmp.civilization.settlement.Role;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.civilization.settlement.SettlementPolicy;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.StoreSnapshot;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldInstance;
import xdu.undarubra.esmp.economy.CurrencyDefinition;
import xdu.undarubra.esmp.economy.LedgerAccount;
import xdu.undarubra.esmp.economy.LedgerException;
import xdu.undarubra.esmp.economy.LedgerPolicy;
import xdu.undarubra.esmp.economy.LedgerService;
import xdu.undarubra.esmp.economy.LedgerService.Actor;
import xdu.undarubra.esmp.economy.LedgerService.Movement;
import xdu.undarubra.esmp.plugin.economy.EconomyCommands;
import xdu.undarubra.esmp.plugin.economy.StartingFundsRuntime;
import xdu.undarubra.esmp.plugin.settlement.ClaimProtection;
import xdu.undarubra.esmp.plugin.settlement.LandValueRuntime;
import xdu.undarubra.esmp.plugin.settlement.ManagementTools;
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
    private LedgerService ledger;
    private EconomyCommands economyCommands;
    private StartingFundsRuntime startingFunds;
    private LandValueRuntime landValues;
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
                            failure -> fail("Settlement storage or publication failed", failure),
                            (connection, id) -> {
                                try {
                                    ledger.requireEmptyTreasury(connection, id);
                                } catch (LedgerException occupied) {
                                    throw new SettlementException(occupied.getMessage());
                                }
                            },
                            readClaimPricingPolicy(),
                            (c, q) -> {
                                try {
                                    // The field is initialized before activation permits requests.
                                    // Posting on this context keeps payment in the claim
                                    // transaction.
                                    ledger.post(
                                            c,
                                            new LedgerService.Request(
                                                    q.id(),
                                                    new Actor(q.actor(), false),
                                                    Movement.SINK,
                                                    q.payer() == ClaimQuote.Payer.WALLET
                                                            ? LedgerAccount.wallet(q.payerId())
                                                            : LedgerAccount.treasury(q.payerId()),
                                                    null,
                                                    q.amount(),
                                                    q.founding()
                                                            ? "SETTLEMENT_FOUNDING"
                                                            : "CLAIM_PURCHASE",
                                                    ""));
                                } catch (LedgerException rejected) {
                                    throw new SettlementException(rejected.getMessage());
                                }
                            });
            ledger =
                    xdu.undarubra.esmp.economy.api.EconomyServices.ledger(
                            store,
                            gameClock,
                            readCurrency(),
                            readLedgerPolicy(),
                            (connection, actor, id) -> {
                                var permission =
                                        settlements
                                                .service()
                                                .treasuryPermission(connection, actor, id);
                                return new LedgerService.TreasuryAuthority(
                                        permission.role() == Role.OWNER,
                                        permission.role() == Role.GOVERNOR,
                                        permission.ownershipRevision(),
                                        permission.promotionId());
                            });
            economyCommands =
                    new EconomyCommands(
                            this,
                            ledger,
                            settlements,
                            failure -> fail("Currency storage failed", failure));
            startingFunds =
                    new StartingFundsRuntime(
                            this,
                            ledger,
                            positiveSetting("economy.starting-funds", 100),
                            failure -> fail("Starting-fund storage failed", failure));
            getServer().getPluginManager().registerEvents(startingFunds, this);
            var landValueService =
                    xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                            store, readLandValuePolicy());
            landValues =
                    new LandValueRuntime(
                            this,
                            getServer().getWorld(getConfig().getString("worlds.MAIN")),
                            store,
                            landValueService,
                            failure -> fail("Natural land-value capture failed", failure));
            for (String name : List.of("balance", "pay")) {
                var moneyCommand =
                        Objects.requireNonNull(getCommand(name), "Missing " + name + " command");
                moneyCommand.setExecutor(economyCommands);
                moneyCommand.setTabCompleter(economyCommands);
            }
            var settlementCommand = new SettlementCommands(this, settlements, landValues);
            var command =
                    Objects.requireNonNull(getCommand("settlement"), "Missing settlement command");
            command.setExecutor(settlementCommand);
            command.setTabCompleter(settlementCommand);
            getServer()
                    .getPluginManager()
                    .registerEvents(new ClaimProtection(this, settlements), this);
            getServer().getPluginManager().registerEvents(settlements.visualization(), this);
            getServer().getPluginManager().registerEvents(new QuotedCommandListener(this), this);
            store.initialize(List.copyOf(definitions.values()), initialBindings)
                    .thenCompose(ignored -> gameTime.initialize())
                    .thenCompose(ignored -> settlements.initialize())
                    .thenCompose(owned -> ledger.initialize().thenApply(ignored -> owned))
                    .thenCompose(owned -> landValueService.initialize().thenApply(ignored -> owned))
                    .thenCompose(owned -> store.finishMigration().thenApply(ignored -> owned))
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
                                                startingFunds.start();
                                                landValues.start();
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
        if (!(version instanceof Integer number) || number < 1 || number > 8) {
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
            getConfig().set("config-version", 4);
            saveConfig();
        }
        if (number < 5) {
            try {
                var backup = getDataFolder().toPath().resolve("config-before-economy.yml");
                if (!Files.exists(backup))
                    Files.copy(getDataFolder().toPath().resolve("config.yml"), backup);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot back up economy configuration", failure);
            }
            getConfig().options().copyDefaults(true);
            getConfig().set("config-version", 5);
            saveConfig();
        }
        if (number < 6) {
            try {
                var backup = getDataFolder().toPath().resolve("config-before-starting-funds.yml");
                if (!Files.exists(backup))
                    Files.copy(getDataFolder().toPath().resolve("config.yml"), backup);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Cannot back up starting-fund configuration", failure);
            }
            getConfig().options().copyDefaults(true);
            getConfig().set("config-version", 6);
            saveConfig();
        }
        if (number < 7) {
            try {
                var backup = getDataFolder().toPath().resolve("config-before-land-values.yml");
                if (!Files.exists(backup))
                    Files.copy(getDataFolder().toPath().resolve("config.yml"), backup);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot back up land-value configuration", failure);
            }
            getConfig().options().copyDefaults(true);
            getConfig().set("config-version", 7);
            saveConfig();
        }
        if (number < 8) {
            try {
                var backup = getDataFolder().toPath().resolve("config-before-paid-claims.yml");
                if (!Files.exists(backup))
                    Files.copy(getDataFolder().toPath().resolve("config.yml"), backup);
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Cannot back up paid-claims configuration", failure);
            }
            getConfig().options().copyDefaults(true);
            getConfig().set("settlements.claim-base-price", null);
            getConfig().set("settlements.claim-price-step", null);
            getConfig().set("config-version", 8);
            saveConfig();
        }
    }

    private CurrencyDefinition readCurrency() {
        var defaults = CurrencyDefinition.defaults();
        return new CurrencyDefinition(
                getConfig().getString("economy.currency.id", defaults.id()),
                getConfig().getString("economy.currency.singular-name", defaults.singularName()),
                getConfig().getString("economy.currency.plural-name", defaults.pluralName()),
                getConfig().getString("economy.currency.symbol", defaults.symbol()),
                getConfig().getString("economy.currency.format", defaults.format()));
    }

    private LedgerPolicy readLedgerPolicy() {
        String tier = getConfig().getString("economy.issuance-tier", "small");
        if (!tier.matches("[a-z][a-z0-9_-]*"))
            throw new IllegalArgumentException("Invalid issuance tier");
        Object limit = getConfig().get("economy.issuance-tiers." + tier);
        if (!(limit instanceof Integer || limit instanceof Long)
                || ((Number) limit).longValue() < 0) {
            throw new IllegalArgumentException(
                    "Issuance tier must select a non-negative integer budget");
        }
        return new LedgerPolicy(
                positiveSetting("economy.period-ticks", 168000), ((Number) limit).longValue());
    }

    private LandValuePolicy readLandValuePolicy() {
        return new LandValuePolicy(
                Math.toIntExact(positiveSetting("land-values.version", 1)),
                positiveSetting("land-values.base-value", 100),
                nonNegativeSetting("land-values.relief-penalty", 2),
                nonNegativeSetting("land-values.max-relief-penalty", 50),
                nonNegativeSetting("land-values.shore-bonus", 25));
    }

    private long nonNegativeSetting(String path, long fallback) {
        Object value = getConfig().get(path, fallback);
        if (!(value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() < 0) {
            throw new IllegalArgumentException(path + " must be a non-negative integer");
        }
        return ((Number) value).longValue();
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
                positiveSetting("settlements.abandon-ticks", 1_200));
    }

    private ClaimPricingPolicy readClaimPricingPolicy() {
        Object section = getConfig().get("claim-pricing");
        if (section != null && !(section instanceof ConfigurationSection)) {
            throw new IllegalArgumentException("claim-pricing must be a mapping");
        }
        var p = ClaimPricingPolicy.defaults();
        return new ClaimPricingPolicy(
                positiveSetting("claim-pricing.quote-ticks", p.quoteTicks()),
                claimTiers("settlements", p.settlements()),
                claimTiers("portfolio-chunks", p.portfolioChunks()),
                claimTiers("settlement-size", p.settlementSize()),
                claimTiers("proximity", p.proximity()),
                claimDiscounts(p.earlyDiscounts()),
                claimFactor("active-factor", p.activeFactor()),
                claimFactor("inactive-factor", p.inactiveFactor()));
    }

    private List<ClaimPricingPolicy.Tier> claimTiers(
            String name, List<ClaimPricingPolicy.Tier> fallback) {
        String path = "claim-pricing." + name;
        Object value =
                getConfig()
                        .get(
                                path,
                                fallback.stream()
                                        .map(t -> t.minimum() + ":" + t.basisPoints())
                                        .toList());
        if (!(value instanceof List<?> entries) || entries.isEmpty()) {
            throw new IllegalArgumentException(
                    path + " must be a non-empty list of 'threshold:basisPoints' strings");
        }
        var tiers = new ArrayList<ClaimPricingPolicy.Tier>();
        long previous = -1;
        for (Object entry : entries) {
            if (!(entry instanceof String text) || !text.matches("[0-9]+:[0-9]+")) {
                throw new IllegalArgumentException(
                        path + " entries must be 'threshold:basisPoints' strings");
            }
            String[] parts = text.split(":", -1);
            long minimum;
            int basisPoints;
            try {
                minimum = Long.parseLong(parts[0]);
                basisPoints = Integer.parseInt(parts[1]);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(
                        path + " threshold or basis points out of range", invalid);
            }
            if (minimum < 0
                    || minimum <= previous
                    || (tiers.isEmpty() && minimum != 0)
                    || basisPoints <= 0) {
                throw new IllegalArgumentException(
                        path
                                + " requires increasing non-negative thresholds starting at 0 and"
                                + " positive integer basis points");
            }
            tiers.add(new ClaimPricingPolicy.Tier(minimum, basisPoints));
            previous = minimum;
        }
        return List.copyOf(tiers);
    }

    private List<Integer> claimDiscounts(List<Integer> fallback) {
        String path = "claim-pricing.early-discounts";
        Object value = getConfig().get(path, fallback);
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException(path + " must be an integer list");
        }
        var discounts = new ArrayList<Integer>();
        for (Object entry : entries) {
            if (!(entry instanceof Integer || entry instanceof Long)
                    || ((Number) entry).longValue() <= 0
                    || ((Number) entry).longValue() > 10000) {
                throw new IllegalArgumentException(
                        path + " entries must be integers from 1 to 10000");
            }
            discounts.add(((Number) entry).intValue());
        }
        return List.copyOf(discounts);
    }

    private int claimFactor(String name, int fallback) {
        String path = "claim-pricing." + name;
        long value = positiveSetting(path, fallback);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " must not exceed " + Integer.MAX_VALUE);
        }
        return (int) value;
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
        if (startingFunds != null) startingFunds.close();
        getServer().getScheduler().cancelTasks(this);
        if (economyCommands != null) economyCommands.close();
        if (landValues != null) {
            try {
                landValues.close();
            } catch (RuntimeException failure) {
                getLogger().log(Level.SEVERE, "Land-value shutdown failed", failure);
            }
        }
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
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] rawArgs) {
        try {
            return dispatchCommand(sender, CommandArguments.parse(rawArgs));
        } catch (IllegalArgumentException rejected) {
            sender.sendMessage(Component.text(rejected.getMessage()));
            return true;
        }
    }

    private boolean dispatchCommand(CommandSender sender, String[] args) {
        if (args.length > 0
                && (args[0].equalsIgnoreCase("economy") || args[0].equalsIgnoreCase("treasury"))) {
            if (economyCommands == null) {
                sender.sendMessage(Component.text("The economy is loading."));
                return true;
            }
            return economyCommands.root(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("tools")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Tool requests must be made by a player."));
                return true;
            }
            if (!player.hasPermission("esmp.settlement")) {
                player.sendMessage(
                        Component.text("You do not have permission to request ESMP tools."));
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(
                        Component.text("Usage: /esmp tools <parcel_wand|management_book>"));
                return true;
            }
            if (settlements == null || !settlements.ready()) {
                player.sendMessage(Component.text("ESMP tools are initializing or unavailable."));
                return true;
            }
            try {
                settlements.tools().issue(player, args[1]);
            } catch (SettlementException | IllegalArgumentException rejected) {
                player.sendMessage(Component.text(rejected.getMessage()));
            }
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("Usage: /esmp tools | treasury | economy | status"));
            return true;
        }
        if (!sender.hasPermission("esmp.admin")) {
            sender.sendMessage(Component.text("You do not have permission to inspect ESMP."));
            return true;
        }
        sender.sendMessage(
                Component.text("ESMP state=" + state + " | Java " + Runtime.version().feature()));
        if (gameClock == null || state != State.READY) {
            sender.sendMessage(Component.text("Game time: unavailable until ESMP is ready."));
        } else {
            gameTime.sample();
            long ticks = gameClock.ticks();
            sender.sendMessage(
                    Component.text(
                            "Game time: day "
                                    + ticks / GameClock.TICKS_PER_DAY
                                    + " + "
                                    + ticks % GameClock.TICKS_PER_DAY
                                    + "/24000 ticks"
                                    + " | "
                                    + (gameClock.advancing() ? "advancing" : "paused")
                                    + " | Game tick="
                                    + ticks));
            World main = getServer().getWorld(getConfig().getString("worlds.MAIN"));
            if (main != null) {
                long minutes = ((main.getTime() + 6000) % 24000) * 60 / 1000;
                sender.sendMessage(
                        Component.text(
                                String.format(
                                        java.util.Locale.ROOT,
                                        "Main world clock: %02d:%02d",
                                        minutes / 60,
                                        minutes % 60)));
            }
        }
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
        sender.sendMessage(Component.text("Claims · Parcels · Activity · Ledger · History"));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        boolean toolsAllowed = sender instanceof Player && sender.hasPermission("esmp.settlement");
        boolean moneyAllowed =
                sender.hasPermission("esmp.economy") || sender.hasPermission("esmp.admin");
        if (moneyAllowed
                && args.length > 1
                && (args[0].equalsIgnoreCase("economy") || args[0].equalsIgnoreCase("treasury"))
                && economyCommands != null) {
            return economyCommands.rootSuggestions(sender, args);
        }
        if (args.length == 1) {
            var choices = new ArrayList<String>();
            if (toolsAllowed) choices.add("tools");
            if (moneyAllowed) {
                choices.add("treasury");
                choices.add("economy");
            }
            if (sender.hasPermission("esmp.admin")) choices.add("status");
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            return choices.stream().filter(choice -> choice.startsWith(prefix)).toList();
        }
        if (toolsAllowed && args.length == 2 && args[0].equalsIgnoreCase("tools")) {
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            return ManagementTools.TOOL_NAMES.stream()
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
