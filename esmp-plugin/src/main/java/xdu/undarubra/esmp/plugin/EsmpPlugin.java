package xdu.undarubra.esmp.plugin;

import xdu.undarubra.esmp.civilization.CivilizationWorlds;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.world.StoreSnapshot;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldInstance;
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

/** Single composition root. This foundation does not enable gameplay systems. */
public final class EsmpPlugin extends JavaPlugin implements Listener {
    private enum State { STARTING, READY, FAILED, STOPPED }

    private volatile boolean stopping;
    private State state = State.STOPPED;
    private WorldStore store;
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
            store.initialize(List.copyOf(definitions.values()), initialBindings)
                    .whenComplete((snapshot, failure) -> onServerThread(() -> {
                        if (failure != null) {
                            fail("SQLite initialization failed", failure);
                            return;
                        }
                        state = State.READY;
                        getLogger().info("ESMP READY: schema=" + snapshot.schemaVersion()
                                + ", definitions=" + snapshot.definitions().size()
                                + ", instances=" + snapshot.instances().size()
                                + ". Foundation only; claim/decay/repair/reset gameplay is not enabled.");
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
        if (!(version instanceof Integer number) || number < 1 || number > 2) {
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
                throw new IllegalStateException("Could not back up the previous configuration", failure);
            }
            getConfig().set("worlds.FACTORY", null);
            getConfig().set("config-version", 2);
            saveConfig();
            getLogger().info("Migrated configuration to version 2; former Factory world mapping retired. World data is untouched.");
        }
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
                throw new IllegalArgumentException("Unknown definition or invalid world name: " + id);
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
            throw new IllegalArgumentException("World " + world.getName() + " must use environment "
                    + definition.environment() + " for " + definition.id());
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
            store.register(binding).whenComplete((instance, failure) -> onServerThread(() -> {
                if (failure != null) {
                    fail("World registration failed", failure);
                } else {
                    getLogger().info("Registered " + instance.definitionId() + " instance=" + instance.instanceId());
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
            getServer().getScheduler().runTask(this, () -> {
                if (!stopping && isEnabled() && state != State.FAILED) {
                    action.run();
                }
            });
        } catch (IllegalPluginAccessException shutdownRace) {
            if (!stopping) {
                getLogger().log(Level.SEVERE, "Could not dispatch completion to Paper", shutdownRace);
            }
        }
    }

    private void fail(String message, Throwable failure) {
        state = State.FAILED;
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        getLogger().log(Level.SEVERE, message, cause);
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        stopping = true;
        getServer().getScheduler().cancelTasks(this);
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
        sender.sendMessage(Component.text("ESMP state=" + state + " | Java " + Runtime.version().feature()));
        if (store == null) {
            sender.sendMessage(Component.text("Storage is not initialized."));
            return true;
        }
        StoreSnapshot snapshot = store.snapshot();
        sender.sendMessage(Component.text("SQLite schema=" + snapshot.schemaVersion()
                + " | definitions=" + snapshot.definitions().size()
                + " | active-definition instances=" + snapshot.instances().size()));
        for (String module : List.of("civilization", "resource")) {
            long count = snapshot.definitions().stream().filter(d -> d.moduleId().equals(module)).count();
            sender.sendMessage(Component.text("Module " + module + ": " + count + " world definitions"));
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
            sender.sendMessage(Component.text(mapping.getValue().id() + " -> " + mapping.getKey()
                    + " | " + (world == null ? "not loaded" : "loaded") + " | instance=" + instanceId));
        }
        sender.sendMessage(Component.text("Foundation only: no claim protection, decay, repair, or Resource reset gameplay yet."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender.hasPermission("esmp.admin") && args.length == 1
                && "status".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            return List.of("status");
        }
        return List.of();
    }
}
