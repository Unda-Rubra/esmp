package xdu.undarubra.esmp.plugin.settlement;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import xdu.undarubra.esmp.civilization.settlement.*;

import java.util.*;

/**
 * Session-only selection and disposable item frontends over the same command/application services.
 */
public final class ManagementTools implements Listener, AutoCloseable {
    private record Selection(UUID world, ParcelCell first, ParcelCell second) {}

    private record Preview(
            UUID settlement,
            String name,
            UUID parcel,
            Set<ParcelCell> cells,
            ParcelCell removal,
            LandSnapshot.Land expectedLand,
            long ownership,
            long chunks,
            long expires) {}

    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final NamespacedKey identity;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, Long> lastIssue = new HashMap<>();
    private final Map<UUID, Long> renderUntil = new HashMap<>();
    private BukkitTask renderTask;
    private static final Particle.DustOptions CURRENT = new Particle.DustOptions(Color.AQUA, 0.8f);
    private static final Particle.DustOptions SELECTED = new Particle.DustOptions(Color.LIME, 0.8f);
    private static final Particle.DustOptions EXISTING = new Particle.DustOptions(Color.BLUE, 0.8f);
    private static final Particle.DustOptions CONFLICT = new Particle.DustOptions(Color.RED, 0.8f);

    public ManagementTools(JavaPlugin plugin, SettlementRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
        identity = new NamespacedKey(plugin, "management_tool");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        renderTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::render, 1, 10);
    }

    public void forget(UUID player) {
        selections.remove(player);
        previews.remove(player);
        lastIssue.remove(player);
        renderUntil.remove(player);
    }

    public void issue(Player player, String kind) {
        if (!kind.equals("book") && !kind.equals("stick"))
            throw new SettlementException("Use tools book or tools stick");
        for (var item : player.getInventory().getContents())
            if (kind.equals(kind(item)))
                throw new SettlementException("You already have this tool");
        long now = runtime.clock().ticks();
        Long last = lastIssue.get(player.getUniqueId());
        if (last != null && now - last < 40)
            throw new SettlementException("Wait 40 advancing game ticks between tool requests");
        if (player.getInventory().firstEmpty() < 0)
            throw new SettlementException("Free one inventory slot first");
        var item = new ItemStack(kind.equals("book") ? Material.WRITTEN_BOOK : Material.STICK);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(identity, PersistentDataType.STRING, kind);
        meta.displayName(
                Component.text(
                        kind.equals("book") ? "Settlement Management" : "Parcel Wand",
                        NamedTextColor.AQUA));
        meta.lore(
                List.of(
                        Component.text("Server tool; permissions belong to its user"),
                        Component.text(
                                kind.equals("stick")
                                        ? "Named Name Tag offhand; left/right select; preview then"
                                              + " commit"
                                        : "Right-click for current management actions")));
        if (meta instanceof BookMeta book) {
            book.title(Component.text("Settlement Management"));
            book.author(Component.text("ESMP"));
            book.addPages(
                    Component.text(
                            "Right-click this server tool to open current management actions."));
        }
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        lastIssue.put(player.getUniqueId(), now);
        player.sendMessage(
                Component.text("Issued " + kind + "; replaceable, no transferable authority."));
    }

    private String kind(ItemStack item) {
        return item == null || !item.hasItemMeta()
                ? null
                : item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(identity, PersistentDataType.STRING);
    }

    private boolean tool(ItemStack item) {
        return kind(item) != null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void use(PlayerInteractEvent event) {
        String kind = kind(event.getItem());
        if (kind == null) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND || !runtime.ready()) return;
        Player player = event.getPlayer();
        try {
            if (kind.equals("book")) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR
                        || event.getAction() == Action.RIGHT_CLICK_BLOCK) open(player);
                return;
            }
            if (event.getClickedBlock() == null) return;
            if (event.getAction() != Action.LEFT_CLICK_BLOCK
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            ItemStack tag = player.getInventory().getItemInOffHand();
            if (tag.getType() != Material.NAME_TAG
                    || !tag.hasItemMeta()
                    || !tag.getItemMeta().hasDisplayName())
                throw new SettlementException("Hold a named Name Tag in your off hand");
            String name =
                    PlainTextComponentSerializer.plainText()
                            .serialize(tag.getItemMeta().displayName());
            Location location = event.getClickedBlock().getLocation();
            select(player, event.getAction() == Action.LEFT_CLICK_BLOCK, location);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                var settlement = runtime.at(location);
                if (settlement == null) throw new SettlementException("Select within a Settlement");
                previewAdd(player, settlement, name);
            }
        } catch (IllegalArgumentException | SettlementException rejected) {
            player.sendMessage(Component.text(rejected.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void preventBreak(BlockBreakEvent event) {
        if (tool(event.getPlayer().getInventory().getItemInMainHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void preventDamage(BlockDamageEvent event) {
        if (tool(event.getItemInHand())) event.setCancelled(true);
    }

    @EventHandler
    public void crafting(PrepareItemCraftEvent event) {
        for (var item : event.getInventory().getMatrix())
            if (tool(item)) {
                event.getInventory().setResult(null);
                return;
            }
    }

    @EventHandler
    public void anvil(PrepareAnvilEvent event) {
        if (tool(event.getInventory().getItem(0)) || tool(event.getInventory().getItem(1)))
            event.setResult(null);
    }

    @EventHandler
    public void grindstone(PrepareGrindstoneEvent event) {
        if (tool(event.getInventory().getItem(0)) || tool(event.getInventory().getItem(1)))
            event.setResult(null);
    }

    public void open(Player player) {
        var settlement = runtime.at(player.getLocation());
        Component front =
                Component.text(
                                "ESMP Management\nNumeric prices only; nothing charged.\n",
                                NamedTextColor.BLACK)
                        .append(link("Create here", "/settlement create", false))
                        .append(link("My Settlements", "/settlement list", false))
                        .append(link("First claim price", "/settlement price", false))
                        .append(link("Parcel Wand", "/settlement tools stick", false));
        if (settlement == null) {
            player.openBook(
                    Book.builder()
                            .title(Component.text("Management"))
                            .author(Component.text("ESMP"))
                            .pages(
                                    front.append(
                                            Component.text(
                                                    "\n"
                                                        + "Stand in a Settlement for its management"
                                                        + " pages.")))
                            .build());
            return;
        }
        String ref = settlement.name();
        var land = runtime.land(settlement.id());
        Component governance =
                Component.text(
                                ref
                                        + " (#"
                                        + land.number()
                                        + ")\nRole: "
                                        + settlement.role(player.getUniqueId())
                                        + "\n")
                        .append(link("Status", "/settlement info " + ref, false))
                        .append(link("Activity", "/settlement activity " + ref, false))
                        .append(link("Next claim price", "/settlement price " + ref, false))
                        .append(link("Claim here", "/settlement claim " + ref, false))
                        .append(link("Unclaim here", "/settlement unclaim", false))
                        .append(link("Borders", "/settlement view " + ref, false));
        Component people =
                Component.text("People and governance\n")
                        .append(link("Members", "/settlement members " + ref, false))
                        .append(link("Invite", "/settlement invite " + ref + " ", true))
                        .append(link("Promote", "/settlement promote " + ref + " ", true))
                        .append(link("Demote", "/settlement demote " + ref + " ", true))
                        .append(link("Remove citizen", "/settlement kick " + ref + " ", true))
                        .append(link("Ban", "/settlement ban " + ref + " ", true))
                        .append(link("Unban", "/settlement unban " + ref + " ", true))
                        .append(link("Bans", "/settlement bans " + ref, false))
                        .append(link("Transfer", "/settlement transfer " + ref + " ", true))
                        .append(link("Offers", "/settlement offers", false));
        Component parcels =
                Component.text("Parcels and permissions\n")
                        .append(link("List Parcels", "/settlement parcel list " + ref, false))
                        .append(link("Selection help", "/settlement parcel help", false))
                        .append(
                                link(
                                        "Add selected cells",
                                        "/settlement parcel add " + ref + " ",
                                        true))
                        .append(
                                link(
                                        "Set Holders",
                                        "/settlement parcel holders " + ref + " ",
                                        true))
                        .append(link("Set policy", "/settlement parcel policy " + ref + " ", true))
                        .append(link("Proposals", "/settlement parcel requests " + ref, false))
                        .append(
                                link(
                                        "Delete Parcel",
                                        "/settlement parcel delete " + ref + " ",
                                        true));
        Component naming =
                Component.text("History and administration\n")
                        .append(link("History", "/settlement history " + ref, false))
                        .append(
                                link(
                                        "Certify naming (admin)",
                                        "/settlement certify " + ref + " true",
                                        false))
                        .append(
                                link(
                                        "Approve name (admin)",
                                        "/settlement rename " + ref + " ",
                                        true))
                        .append(link("Leave", "/settlement leave " + ref, false))
                        .append(link("Abandon warning", "/settlement abandon " + ref, false));
        player.openBook(
                Book.builder()
                        .title(Component.text("Management"))
                        .author(Component.text("ESMP"))
                        .pages(front, governance, people, parcels, naming)
                        .build());
    }

    private static Component link(String title, String command, boolean suggest) {
        return Component.text("[" + title + "]\n", NamedTextColor.DARK_BLUE)
                .clickEvent(
                        suggest
                                ? ClickEvent.suggestCommand(command)
                                : ClickEvent.runCommand(command));
    }

    private SettlementView settlement(String input) {
        var s = runtime.find(input);
        if (s == null) throw new SettlementException("Settlement not found");
        return s;
    }

    private ParcelView parcel(SettlementView s, String input) {
        var land = runtime.land(s.id());
        for (var p : land.parcels().values())
            if (p.name().equalsIgnoreCase(input) || p.id().toString().equals(input)) return p;
        throw new SettlementException("Parcel not found");
    }

    private UUID playerId(String input) {
        var online = plugin.getServer().getPlayerExact(input);
        if (online != null) return online.getUniqueId();
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
        }
        var cached = plugin.getServer().getOfflinePlayerIfCached(input);
        if (cached == null)
            throw new SettlementException("Use an online/cached player name or UUID");
        return cached.getUniqueId();
    }

    private void select(Player player, boolean first, Location location) {
        if (runtime.mainInstance(location.getWorld()) == null)
            throw new SettlementException("Selections require the registered Main World");
        var cell =
                ParcelCell.atBlock(
                        location.getBlockX(), location.getBlockY(), location.getBlockZ());
        var previous = selections.get(player.getUniqueId());
        if (previous == null || !previous.world().equals(location.getWorld().getUID()))
            previous = new Selection(location.getWorld().getUID(), null, null);
        selections.put(
                player.getUniqueId(),
                first
                        ? new Selection(previous.world(), cell, previous.second())
                        : new Selection(previous.world(), previous.first(), cell));
        previews.remove(player.getUniqueId());
        renderUntil.put(player.getUniqueId(), Math.addExact(runtime.clock().ticks(), 1200));
        player.sendMessage(
                Component.text(
                        (first ? "First" : "Second")
                                + " cell="
                                + cell
                                + "; 8x8x8 aligned. Preview before commit."));
    }

    private void previewAdd(Player player, SettlementView s, String name) {
        var selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first() == null || selection.second() == null)
            throw new SettlementException("Select both positions first");
        if (!selection.world().equals(player.getWorld().getUID())
                || !s.worldInstanceId().equals(runtime.mainInstance(player.getWorld())))
            throw new SettlementException("Selection/Settlement world mismatch");
        var cells = ParcelCell.cuboid(selection.first(), selection.second());
        var land = runtime.land(s.id());
        var combined = new HashSet<ParcelCell>();
        ParcelView existing = null;
        for (var p : land.parcels().values())
            if (p.name().equalsIgnoreCase(name)) {
                existing = p;
                combined.addAll(p.cells());
            }
        int conflicts = 0;
        for (var cell : cells) {
            if (!s.chunks().contains(cell.chunk()))
                throw new SettlementException("Selection leaves the Settlement");
            if (land.cells().containsKey(cell)) conflicts++;
            else combined.add(cell);
        }
        if (!ParcelCell.connected(combined) || combined.size() > ParcelCell.LIMIT)
            throw new SettlementException(
                    "Unparcelled selection must produce a nonempty connected Parcel <=4096 cells");
        previews.put(
                player.getUniqueId(),
                new Preview(
                        s.id(),
                        name,
                        existing == null ? null : existing.id(),
                        cells,
                        null,
                        land,
                        s.ownershipRevision(),
                        s.chunkRevision(),
                        Math.addExact(runtime.clock().ticks(), 1200)));
        player.sendMessage(
                Component.text(
                        "Preview "
                                + name
                                + ": "
                                + (cells.size() - conflicts)
                                + " new cells; "
                                + conflicts
                                + " already parcelled cells skipped. /settlement parcel commit"
                                + " (1200 game ticks)"));
    }

    private void commit(Player player) {
        var preview = previews.remove(player.getUniqueId());
        if (preview == null) throw new SettlementException("Create a preview first");
        var s = runtime.index().byId(preview.settlement());
        if (s == null
                || runtime.clock().ticks() >= preview.expires()
                || runtime.land(s.id()) != preview.expectedLand()
                || s.ownershipRevision() != preview.ownership()
                || s.chunkRevision() != preview.chunks())
            throw new SettlementException("Preview expired or state changed; preview again");
        if (!s.worldInstanceId().equals(runtime.mainInstance(player.getWorld())))
            throw new SettlementException("Return to the selection world");
        runtime.mutate(
                player,
                s.id(),
                null,
                () ->
                        preview.removal() == null
                                ? runtime.parcels()
                                        .add(
                                                player.getUniqueId(),
                                                s.id(),
                                                preview.name(),
                                                preview.cells(),
                                                player.getWorld().getMinHeight(),
                                                player.getWorld().getMaxHeight())
                                : runtime.parcels()
                                        .removeCell(
                                                player.getUniqueId(),
                                                s.id(),
                                                preview.parcel(),
                                                preview.removal()));
    }

    public void command(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
            help(player);
            return;
        }
        UUID actor = player.getUniqueId();
        String verb = args[1].toLowerCase(Locale.ROOT);
        if (verb.equals("pos1") || verb.equals("pos2")) {
            if (args.length != 2 && args.length != 5)
                throw new SettlementException("parcel pos1|pos2 [blockX blockY blockZ]");
            Location p =
                    args.length == 2
                            ? player.getLocation()
                            : new Location(
                                    player.getWorld(),
                                    Integer.parseInt(args[2]),
                                    Integer.parseInt(args[3]),
                                    Integer.parseInt(args[4]));
            select(player, verb.equals("pos1"), p);
            return;
        }
        if (verb.equals("commit")) {
            exact(args, 2, "parcel commit");
            commit(player);
            return;
        }
        if (verb.equals("clear")) {
            exact(args, 2, "parcel clear");
            selections.remove(actor);
            previews.remove(actor);
            renderUntil.remove(actor);
            player.sendMessage(Component.text("Selection cleared"));
            return;
        }
        if (args.length < 3) throw new SettlementException("Specify a Settlement number/name");
        var s = settlement(args[2]);
        switch (verb) {
            case "list" -> {
                exact(args, 3, "parcel list <settlement>");
                player.sendMessage(
                        Component.text("Parcels: " + runtime.land(s.id()).parcels().size()));
                runtime.land(s.id())
                        .parcels()
                        .values()
                        .forEach(
                                p ->
                                        player.sendMessage(
                                                Component.text(
                                                        p.name()
                                                                + " | "
                                                                + p.id()
                                                                + " | cells="
                                                                + p.cells().size()
                                                                + " | Holders="
                                                                + p.holders())));
            }
            case "info", "view" -> {
                exact(args, 4, "parcel info|view <settlement> <parcel>");
                var p = parcel(s, args[3]);
                player.sendMessage(
                        Component.text(
                                p.name()
                                        + " revision="
                                        + p.revision()
                                        + " | cells="
                                        + p.cells().size()
                                        + " | Holders="
                                        + p.holders()
                                        + " | policy="
                                        + p.policy()));
                if (verb.equals("view")) {
                    selections.put(
                            actor,
                            new Selection(
                                    player.getWorld().getUID(),
                                    ParcelCell.atBlock(
                                            player.getLocation().getBlockX(),
                                            player.getLocation().getBlockY(),
                                            player.getLocation().getBlockZ()),
                                    null));
                    renderUntil.put(actor, Math.addExact(runtime.clock().ticks(), 1200));
                }
            }
            case "add" -> {
                exact(args, 4, "parcel add <settlement> <name>");
                previewAdd(player, s, args[3]);
            }
            case "remove" -> {
                exact(args, 7, "parcel remove <settlement> <parcel> <blockX> <blockY> <blockZ>");
                var p = parcel(s, args[3]);
                var cell =
                        ParcelCell.atBlock(
                                Integer.parseInt(args[4]),
                                Integer.parseInt(args[5]),
                                Integer.parseInt(args[6]));
                if (!p.cells().contains(cell)
                        || cell.neighbors().stream().allMatch(p.cells()::contains))
                    throw new SettlementException("Select a Parcel surface cell");
                var remaining = new HashSet<>(p.cells());
                remaining.remove(cell);
                if (!ParcelCell.connected(remaining))
                    throw new SettlementException(
                            "Removal disconnects/empties Parcel; use delete for whole Parcel");
                previews.put(
                        actor,
                        new Preview(
                                s.id(),
                                p.name(),
                                p.id(),
                                Set.of(cell),
                                cell,
                                runtime.land(s.id()),
                                s.ownershipRevision(),
                                s.chunkRevision(),
                                Math.addExact(runtime.clock().ticks(), 1200)));
                player.sendMessage(
                        Component.text("Removal preview ready; /settlement parcel commit"));
            }
            case "delete" -> {
                exact(args, 4, "parcel delete <settlement> <parcel>");
                var p = parcel(s, args[3]);
                runtime.mutate(
                        player,
                        s.id(),
                        null,
                        () -> runtime.parcels().delete(actor, s.id(), p.id()));
            }
            case "holders" -> {
                exact(args, 5, "parcel holders <settlement> <parcel> <player,player|none>");
                var p = parcel(s, args[3]);
                var holders = new HashSet<UUID>();
                if (!args[4].equalsIgnoreCase("none"))
                    for (var who : args[4].split(",")) holders.add(playerId(who));
                runtime.mutate(
                        player,
                        s.id(),
                        null,
                        () -> runtime.parcels().holders(actor, s.id(), p.id(), holders));
            }
            case "policy" -> {
                exact(
                        args,
                        6,
                        "parcel policy <settlement> <parcel> <BUILD|INTERACT|CONTAINER|AUTOMATION>"
                            + " <relations,comma|none>");
                var p = parcel(s, args[3]);
                var action = ParcelAction.valueOf(args[4].toUpperCase(Locale.ROOT));
                var relations = EnumSet.noneOf(ParcelRelation.class);
                if (!args[5].equalsIgnoreCase("none"))
                    for (var relation : args[5].split(","))
                        relations.add(ParcelRelation.valueOf(relation.toUpperCase(Locale.ROOT)));
                runtime.mutate(
                        player,
                        s.id(),
                        null,
                        () -> runtime.parcels().policy(actor, s.id(), p.id(), action, relations));
            }
            case "rename" -> {
                exact(args, 5, "parcel rename <settlement> <parcel> <newName>");
                var p = parcel(s, args[3]);
                runtime.mutate(
                        player,
                        s.id(),
                        null,
                        () -> runtime.parcels().rename(actor, s.id(), p.id(), args[4]));
            }
            case "requests" -> {
                exact(args, 3, "parcel requests <settlement>");
                runtime.query(
                        player,
                        () -> runtime.parcels().requests(s.id()),
                        requests -> {
                            player.sendMessage(
                                    Component.text("Pending proposals=" + requests.size()));
                            for (var r : requests)
                                player.sendMessage(
                                        Component.text(
                                                r.id()
                                                        + " | "
                                                        + r.operation()
                                                        + " | requires governance + "
                                                        + r.requiredHolders()
                                                        + " | approvals="
                                                        + r.approvals()
                                                        + " | expires tick "
                                                        + r.expiresTick()));
                        });
            }
            case "approve", "reject" -> {
                exact(args, 4, "parcel approve|reject <settlement> <requestUUID>");
                UUID request = UUID.fromString(args[3]);
                runtime.mutate(
                        player,
                        s.id(),
                        null,
                        () ->
                                runtime.parcels()
                                        .resolve(actor, s.id(), request, verb.equals("approve")));
            }
            default -> help(player);
        }
    }

    private static void exact(String[] args, int length, String usage) {
        if (args.length != length) throw new SettlementException("Usage: /settlement " + usage);
    }

    private static void help(Player player) {
        for (String line :
                List.of(
                        "/settlement parcel pos1|pos2 [blockX blockY blockZ] | clear",
                        "/settlement parcel add <settlement> <name> | commit",
                        "/settlement parcel list <settlement> | info|view <settlement> <parcel>",
                        "/settlement parcel remove <settlement> <parcel> <blockX blockY blockZ> |"
                            + " commit",
                        "/settlement parcel holders <settlement> <parcel> <player,player|none>",
                        "/settlement parcel policy <settlement> <parcel> <action>"
                            + " <OWNER,GOVERNOR,CITIZEN,HOLDER,PUBLIC|none>",
                        "/settlement parcel rename <settlement> <parcel> <name> | delete"
                            + " <settlement> <parcel>",
                        "/settlement parcel requests <settlement> | approve|reject <settlement>"
                            + " <requestUUID>"))
            player.sendMessage(Component.text(line));
    }

    private void render() {
        for (var player : plugin.getServer().getOnlinePlayers()) {
            UUID actor = player.getUniqueId();
            var preview = previews.get(actor);
            var selection = selections.get(actor);
            if (preview != null && runtime.clock().ticks() >= preview.expires()) {
                previews.remove(actor);
                preview = null;
            }
            if (preview == null
                    && (selection == null
                            || runtime.clock().ticks() >= renderUntil.getOrDefault(actor, 0L)))
                continue;
            if (selection != null && !selection.world().equals(player.getWorld().getUID()))
                continue;
            int budget = 256;
            Location location = player.getLocation();
            var current =
                    ParcelCell.atBlock(
                            location.getBlockX(), location.getBlockY(), location.getBlockZ());
            budget = draw(player, current, CURRENT, budget);
            if (preview != null) {
                for (var cell : preview.cells()) {
                    if (budget <= 128) break;
                    boolean conflict =
                            preview.removal() != null
                                    || preview.expectedLand().cells().containsKey(cell);
                    budget = draw(player, cell, conflict ? CONFLICT : SELECTED, budget);
                }
            } else if (selection != null) {
                if (selection.first() != null)
                    budget = draw(player, selection.first(), SELECTED, budget);
                if (selection.second() != null)
                    budget = draw(player, selection.second(), SELECTED, budget);
            }
            var settlement = runtime.at(location);
            if (settlement != null) {
                var land = runtime.land(settlement.id());
                // Bounded spatial lookup, never iterate every Parcel/cell for every viewer.
                for (int x = current.x() - 4; x <= current.x() + 4 && budget > 0; x++)
                    for (int y = current.y() - 4; y <= current.y() + 4 && budget > 0; y++)
                        for (int z = current.z() - 4; z <= current.z() + 4 && budget > 0; z++) {
                            var cell = new ParcelCell(x, y, z);
                            var parcel = land.cells().get(cell);
                            if (parcel != null
                                    && cell.neighbors().stream()
                                            .anyMatch(n -> !parcel.cells().contains(n)))
                                budget = draw(player, cell, EXISTING, budget);
                        }
            }
        }
    }

    private static int draw(
            Player player, ParcelCell cell, Particle.DustOptions color, int budget) {
        double x = (double) cell.x() * 8, y = (double) cell.y() * 8, z = (double) cell.z() * 8;
        if (budget <= 0
                || Math.abs(player.getX() - x) > 64
                || Math.abs(player.getY() - y) > 64
                || Math.abs(player.getZ() - z) > 64
                || !player.getWorld()
                        .isChunkLoaded(Math.floorDiv(cell.x(), 2), Math.floorDiv(cell.z(), 2)))
            return budget;
        for (int dx = 0; dx <= 8 && budget > 0; dx += 8)
            for (int dy = 0; dy <= 8 && budget > 0; dy += 8)
                for (int dz = 0; dz <= 8 && budget > 0; dz += 8) {
                    player.spawnParticle(
                            Particle.DUST, x + dx, y + dy, z + dz, 1, 0, 0, 0, 0, color);
                    budget--;
                }
        return budget;
    }

    @Override
    public void close() {
        if (renderTask != null) renderTask.cancel();
        selections.clear();
        previews.clear();
        lastIssue.clear();
        renderUntil.clear();
    }
}
