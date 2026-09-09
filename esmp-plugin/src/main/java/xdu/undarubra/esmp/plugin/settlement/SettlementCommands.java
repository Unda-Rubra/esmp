package xdu.undarubra.esmp.plugin.settlement;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;
import xdu.undarubra.esmp.civilization.settlement.Role;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.civilization.settlement.SettlementOffer;
import xdu.undarubra.esmp.civilization.settlement.SettlementView;

public final class SettlementCommands implements CommandExecutor, TabCompleter {
    private static final List<String> COMMANDS = List.of("create", "claim", "unclaim", "info", "list", "members", "view",
            "invite", "offers", "accept", "decline", "cancel", "promote", "demote", "transfer", "kick", "leave", "rename",
            "abandon", "confirm", "history", "activity", "bypass", "help");
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;

    public SettlementCommands(JavaPlugin plugin, SettlementRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Use Settlement commands in-game. Console diagnostics: esmp status."));
            return true;
        }
        if (!player.hasPermission("esmp.settlement")) {
            player.sendMessage(Component.text("You do not have permission to use Settlements."));
            return true;
        }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(player); return true; }
            if (!runtime.ready()) { throw new SettlementException("Settlements are initializing or unavailable."); }
            UUID actor = player.getUniqueId();
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> {
                    exact(args, 2, "create <name>");
                    UUID world = mainWorld(player);
                    requireUnclaimedHere(player);
                    ChunkPosition chunk = chunk(player);
                    runtime.mutate(player, null, new SettlementRuntime.Reservation(world, chunk),
                            () -> runtime.service().create(actor, world, chunk, args[1]));
                }
                case "claim" -> {
                    exact(args, 2, "claim <settlement>");
                    SettlementView settlement = named(args[1]);
                    manage(player, settlement);
                    requireUnclaimedHere(player);
                    UUID world = mainWorld(player);
                    ChunkPosition chunk = chunk(player);
                    runtime.mutate(player, settlement.id(), new SettlementRuntime.Reservation(world, chunk),
                            () -> runtime.service().claim(actor, settlement.id(), world, chunk));
                }
                case "unclaim" -> {
                    exact(args, 1, "unclaim");
                    mainWorld(player);
                    SettlementView settlement = here(player);
                    manage(player, settlement);
                    ChunkPosition chunk = chunk(player);
                    runtime.mutate(player, settlement.id(), null, () -> runtime.service().unclaim(actor, settlement.id(), chunk));
                }
                case "info", "members" -> {
                    optionalName(args, args[0]);
                    SettlementView settlement = args.length == 2 ? named(args[1]) : here(player);
                    player.sendMessage(Component.text(settlement.name() + " | " + settlement.id() + " | "
                            + settlement.chunks().size() + " chunks | Your role: " + settlement.role(actor)));
                    player.sendMessage(Component.text("Owner: " + playerName(settlement.owner())));
                    settlement.members().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).limit(50)
                            .forEach(e -> player.sendMessage(Component.text(e.getValue() + ": " + playerName(e.getKey()))));
                    if (args[0].equalsIgnoreCase("info")) { showActivity(player, settlement); }
                }
                case "activity" -> {
                    optionalName(args, "activity");
                    showActivity(player, args.length == 2 ? named(args[1]) : here(player));
                }
                case "list" -> {
                    exact(args, 1, "list");
                    var visible = runtime.index().all().stream().filter(s -> s.role(actor) != Role.VISITOR)
                            .sorted(Comparator.comparing(SettlementView::name)).limit(50).toList();
                    player.sendMessage(Component.text("Your Settlements (up to 50): " + visible.size()));
                    visible.forEach(s -> player.sendMessage(Component.text(s.name() + " | " + s.role(actor) + " | " + s.chunks().size() + " chunks")));
                }
                case "view" -> {
                    optionalName(args, "view");
                    runtime.visualization().show(player, args.length == 2 ? named(args[1]) : null);
                    player.sendMessage(Component.text("Nearby borders for 200 game ticks: white=current chunk, green=team, red=Visitor."));
                }
                case "invite", "promote", "transfer" -> {
                    exact(args, 3, args[0] + " <settlement> <player|uuid>");
                    SettlementView settlement = named(args[1]);
                    if (args[0].equalsIgnoreCase("invite")) { manage(player, settlement); }
                    else { owner(player, settlement); }
                    UUID recipient = resolvePlayer(args[2]);
                    SettlementOffer.Type type = switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "invite" -> SettlementOffer.Type.INVITE;
                        case "promote" -> SettlementOffer.Type.PROMOTE;
                        default -> SettlementOffer.Type.TRANSFER;
                    };
                    runtime.mutate(player, settlement.id(), null, () -> runtime.service().offer(actor, settlement.id(), recipient, type));
                }
                case "abandon" -> {
                    exact(args, 2, "abandon <settlement>");
                    SettlementView settlement = named(args[1]);
                    owner(player, settlement);
                    runtime.mutate(player, settlement.id(), null,
                            () -> runtime.service().offer(actor, settlement.id(), actor, SettlementOffer.Type.ABANDON));
                }
                case "accept", "confirm", "cancel", "decline" -> {
                    exact(args, 2, args[0] + " <offer-id>");
                    UUID token = parseUuid(args[1]);
                    String verb = args[0].toLowerCase(Locale.ROOT);
                    runtime.query(player, () -> runtime.service().inspectOffer(token), offer -> {
                        if (verb.equals("cancel")) {
                            SettlementView settlement = runtime.index().byId(offer.settlementId());
                            if (settlement == null) { throw new SettlementException("Settlement is no longer owned."); }
                            if (offer.type() == SettlementOffer.Type.INVITE) { manage(player, settlement); }
                            else { owner(player, settlement); }
                        } else if (!offer.recipient().equals(actor)) {
                            throw new SettlementException("Only the nominated recipient may resolve this offer.");
                        }
                        runtime.mutate(player, offer.settlementId(), null, () -> switch (verb) {
                            case "accept" -> runtime.service().accept(actor, token, false);
                            case "confirm" -> runtime.service().accept(actor, token, true);
                            case "cancel" -> runtime.service().resolveOffer(actor, token, false);
                            default -> runtime.service().resolveOffer(actor, token, true);
                        });
                    });
                }
                case "offers" -> {
                    exact(args, 1, "offers");
                    runtime.query(player, () -> runtime.service().offers(actor), offers -> {
                        player.sendMessage(Component.text("Pending offers (up to 50): " + offers.size()));
                        for (SettlementOffer offer : offers) {
                            SettlementView settlement = runtime.index().byId(offer.settlementId());
                            String name = settlement == null ? offer.settlementId().toString() : settlement.name();
                            String verb = offer.type() == SettlementOffer.Type.ABANDON ? "confirm" : "accept";
                            player.sendMessage(Component.text(offer.type() + " | " + name + " | " + offer.id()
                                    + " | expires at game tick " + offer.expiresAtTick())
                                    .clickEvent(ClickEvent.suggestCommand("/settlement " + verb + " " + offer.id())));
                        }
                    });
                }
                case "kick", "demote" -> {
                    exact(args, 3, args[0] + " <settlement> <player|uuid>");
                    SettlementView settlement = named(args[1]);
                    if (args[0].equalsIgnoreCase("demote")) { owner(player, settlement); }
                    else { manage(player, settlement); }
                    UUID target = resolvePlayer(args[2]);
                    boolean demote = args[0].equalsIgnoreCase("demote");
                    runtime.mutate(player, settlement.id(), null, () -> demote
                            ? runtime.service().demote(actor, settlement.id(), target)
                            : runtime.service().remove(actor, settlement.id(), target, false));
                }
                case "leave" -> {
                    exact(args, 2, "leave <settlement>");
                    SettlementView settlement = named(args[1]);
                    if (settlement.role(actor) == Role.OWNER || settlement.role(actor) == Role.VISITOR) {
                        throw new SettlementException("Only Members and Managers can leave; Owners must transfer or abandon.");
                    }
                    runtime.mutate(player, settlement.id(), null, () -> runtime.service().remove(actor, settlement.id(), actor, true));
                }
                case "rename" -> {
                    exact(args, 3, "rename <settlement> <new-name>");
                    SettlementView settlement = named(args[1]);
                    manage(player, settlement);
                    runtime.mutate(player, settlement.id(), null, () -> runtime.service().rename(actor, settlement.id(), args[2]));
                }
                case "history" -> {
                    exact(args, 2, "history <settlement|uuid>");
                    SettlementView settlement = runtime.index().byName(args[1]);
                    UUID id = settlement == null ? parseUuid(args[1]) : settlement.id();
                    runtime.query(player, () -> runtime.service().history(id), events -> {
                        player.sendMessage(Component.text("Latest historical events: " + events.size()));
                        events.forEach(event -> player.sendMessage(Component.text(
                                (event.gameTick() == null ? "Legacy timestamp " + event.occurredAt() : "Game tick " + event.gameTick())
                                + " | " + event.type() + " | " + event.detail())));
                    });
                }
                case "bypass" -> {
                    exact(args, 2, "bypass <on|off>");
                    if (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off")) {
                        throw new SettlementException("Use bypass on or bypass off.");
                    }
                    player.sendMessage(Component.text("Explicit protection bypass=" + runtime.bypass(player, args[1].equalsIgnoreCase("on"))));
                }
                default -> help(player);
            }
        } catch (SettlementException | IllegalArgumentException rejected) {
            player.sendMessage(Component.text(rejected.getMessage()));
        }
        return true;
    }

    private void showActivity(Player player, SettlementView settlement) {
        runtime.query(player, () -> runtime.activity().status(settlement.id()), status -> {
            player.sendMessage(Component.text(String.format(Locale.ROOT, "%s Activity: %.2f | %s | credited today %.2f | game tick %d",
                    settlement.name(), status.score(), status.state(), status.creditedToday(), status.measuredAtTick())));
            player.sendMessage(Component.text(String.format(Locale.ROOT, "Recovery needed: %.2f | inactive since: %s | production eligibility paused: %s",
                    status.recoveryNeeded(), status.inactiveSinceTick(), status.productionPaused())));
            player.sendMessage(Component.text("Last credited: " + status.lastCategory() + " at tick " + status.lastContributionTick()
                    + ". Decay/production are not enabled."));
        });
    }

    private static void manage(Player player, SettlementView settlement) {
        if (!settlement.role(player.getUniqueId()).canManage()) {
            throw new SettlementException("Only the Owner or a Manager may manage this Settlement.");
        }
    }

    private static void owner(Player player, SettlementView settlement) {
        if (settlement.role(player.getUniqueId()) != Role.OWNER) {
            throw new SettlementException("You do not have authority; only the Owner may perform this operation.");
        }
    }

    private void requireUnclaimedHere(Player player) {
        if (runtime.at(player.getLocation()) != null) { throw new SettlementException("This chunk is already claimed."); }
    }

    private SettlementView named(String value) {
        SettlementView settlement = runtime.index().byName(value);
        if (settlement == null) {
            try { settlement = runtime.index().byId(UUID.fromString(value)); }
            catch (IllegalArgumentException ignored) { /* A normal name need not be a UUID. */ }
        }
        if (settlement == null) { throw new SettlementException("Settlement not found: " + value); }
        return settlement;
    }

    private SettlementView here(Player player) {
        SettlementView settlement = runtime.at(player.getLocation());
        if (settlement == null) { throw new SettlementException("This chunk is unclaimed."); }
        return settlement;
    }

    private UUID mainWorld(Player player) {
        UUID id = runtime.mainInstance(player.getWorld());
        if (id == null) { throw new SettlementException("Claims are only allowed in the registered Main World."); }
        return id;
    }

    private static ChunkPosition chunk(Player player) {
        return new ChunkPosition(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
    }

    private UUID resolvePlayer(String input) {
        Player online = plugin.getServer().getPlayerExact(input);
        if (online != null) { return online.getUniqueId(); }
        try { return UUID.fromString(input); }
        catch (IllegalArgumentException ignored) { /* Resolve cached names without network lookup. */ }
        OfflinePlayer known = plugin.getServer().getOfflinePlayerIfCached(input);
        if (known == null) { throw new SettlementException("Player must be online/known to this server, or use their UUID."); }
        return known.getUniqueId();
    }

    private String playerName(UUID player) {
        String name = plugin.getServer().getOfflinePlayer(player).getName();
        return name == null ? player.toString() : name;
    }

    private static UUID parseUuid(String input) {
        try { return UUID.fromString(input); }
        catch (IllegalArgumentException invalid) { throw new SettlementException("Expected a valid UUID/offer ID."); }
    }

    private static void exact(String[] args, int count, String usage) {
        if (args.length != count) { throw new SettlementException("Usage: /settlement " + usage); }
    }

    private static void optionalName(String[] args, String verb) {
        if (args.length > 2) { throw new SettlementException("Usage: /settlement " + verb + " [settlement]"); }
    }

    private static void help(Player player) {
        for (String line : List.of(
                "/settlement create <name> | claim <name> | unclaim | view [name]",
                "/settlement info [name] | activity [name] | list | members [name] | history <name|uuid>",
                "/settlement invite|promote|transfer|kick|demote <name> <player|uuid>",
                "/settlement offers | accept|decline|cancel <offer-id> | leave <name>",
                "/settlement rename <name> <new-name> | abandon <name> | confirm <offer-id>")) {
            player.sendMessage(Component.text(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("esmp.settlement") || !runtime.ready()) { return List.of(); }
        if (args.length == 1) {
            return COMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("claim", "info", "activity", "members", "view", "invite", "promote", "demote", "transfer", "kick", "leave", "rename", "abandon", "history").contains(args[0].toLowerCase(Locale.ROOT))) {
            return runtime.index().all().stream().map(SettlementView::name)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().limit(50).toList();
        }
        if (args.length == 3 && List.of("invite", "promote", "demote", "transfer", "kick").contains(args[0].toLowerCase(Locale.ROOT))) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        return List.of();
    }
}
