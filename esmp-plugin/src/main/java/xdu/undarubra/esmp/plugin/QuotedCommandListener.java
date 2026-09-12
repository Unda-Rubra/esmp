package xdu.undarubra.esmp.plugin;

import net.kyori.adventure.text.Component;

import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Preserves quoted text before Bukkit's legacy splitting loses whitespace. */
public final class QuotedCommandListener implements Listener {
    private final JavaPlugin plugin;

    public QuotedCommandListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void playerCommand(PlayerCommandPreprocessEvent event) {
        dispatchQuoted(
                event.getPlayer(), event.getMessage().substring(1), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void serverCommand(ServerCommandEvent event) {
        dispatchQuoted(event.getSender(), event.getCommand(), () -> event.setCancelled(true));
    }

    private void dispatchQuoted(CommandSender sender, String input, Runnable cancel) {
        if (input.indexOf('"') < 0) return;
        int separator = input.indexOf(' ');
        if (separator < 0) return;
        String label = input.substring(0, separator);
        var command = plugin.getServer().getPluginCommand(label);
        if (command == null || command.getPlugin() != plugin) return;
        cancel.run();
        try {
            // Preserve registered command permissions/executor without repeating legacy splitting.
            command.execute(
                    sender,
                    label,
                    CommandArguments.parse(input.substring(separator + 1).split(" ", -1)));
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(Component.text(invalid.getMessage()));
        }
    }
}
