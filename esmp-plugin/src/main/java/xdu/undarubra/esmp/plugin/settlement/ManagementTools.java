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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private record Selection(UUID world, ParcelCell first, ParcelCell second, String name) {}

    private record Aim(ParcelCell cell, boolean hitBlock) {}

    private record Preview(
            UUID world,
            UUID settlement,
            String name,
            UUID parcel,
            ParcelCell first,
            ParcelCell last,
            Set<ParcelCell> cells,
            Set<ParcelCell> blocked,
            ParcelCell removal,
            LandSnapshot.Land expectedLand,
            long ownership,
            long chunks,
            long expires,
            String error) {}

    private record DraftKey(
            UUID world,
            UUID settlement,
            ParcelCell first,
            ParcelCell last,
            String name,
            long publication,
            boolean admin) {}

    private record DraftCache(DraftKey key, ParcelDraft.Result result) {}

    private record View(UUID world, UUID settlement, UUID parcel) {}

    private record EdgeKey(
            UUID world,
            ParcelCell center,
            long publication,
            UUID parcel,
            ParcelCell invalidFirst,
            ParcelCell invalidLast,
            Set<ParcelCell> blocked) {}

    private record EdgeCache(EdgeKey key, List<ParcelOverlayGeometry.Edge> edges) {}

    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final NamespacedKey identity;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, Long> lastIssue = new HashMap<>();
    private final Map<UUID, Long> renderUntil = new HashMap<>();
    private BukkitTask renderTask;
    private static final int PARTICLE_BUDGET = 1536;
    private static final Particle.DustOptions BLUE =
            new Particle.DustOptions(Color.fromRGB(64, 144, 255), 0.55f);
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.LIME, 0.7f);
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.RED, 0.7f);
    private final Map<UUID, DraftCache> drafts = new HashMap<>();
    private final Map<UUID, EdgeCache> edgeCaches = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, String> hints = new HashMap<>();
    private final Map<UUID, ParcelCell> lastTargets = new HashMap<>();
    private final Map<UUID, Integer> rightClicks = new HashMap<>();
    private long frame;

    public ManagementTools(JavaPlugin plugin, SettlementRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
        identity = new NamespacedKey(plugin, "management_tool");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        renderTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::render, 1, 2);
        for (var player : plugin.getServer().getOnlinePlayers()) refreshInventory(player);
    }

    public void forget(UUID player) {
        selections.remove(player);
        previews.remove(player);
        lastIssue.remove(player);
        renderUntil.remove(player);
        drafts.remove(player);
        edgeCaches.remove(player);
        views.remove(player);
        hints.remove(player);
        lastTargets.remove(player);
        rightClicks.remove(player);
    }

    public static final List<String> TOOL_NAMES = List.of("parcel_wand", "management_book");

    public void issue(Player player, String toolName) {
        String kind =
                switch (toolName.toLowerCase(Locale.ROOT)) {
                    case "parcel_wand" -> "stick";
                    case "management_book" -> "book";
                    default ->
                            throw new SettlementException(
                                    "Usage: /esmp tools <parcel_wand|management_book>");
                };
        for (var item : player.getInventory().getContents())
            if (kind.equals(kind(item)))
                throw new SettlementException("You already have this tool");
        long now = runtime.clock().ticks();
        Long last = lastIssue.get(player.getUniqueId());
        if (last != null && now - last < 40)
            throw new SettlementException("Try again in a moment.");
        if (player.getInventory().firstEmpty() < 0)
            throw new SettlementException("Free one inventory slot first");
        var item = new ItemStack(kind.equals("book") ? Material.WRITTEN_BOOK : Material.STICK);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(identity, PersistentDataType.STRING, kind);
        item.setItemMeta(meta);
        refreshItem(item);
        player.getInventory().addItem(item);
        lastIssue.put(player.getUniqueId(), now);
        player.sendMessage(
                Component.text(
                        kind.equals("book") ? "Management book added." : "Parcel wand added."));
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

    private boolean refreshItem(ItemStack item) {
        String type = kind(item);
        if (!"book".equals(type) && !"stick".equals(type)) return false;
        var meta = item.getItemMeta();
        var title =
                Component.text(
                        type.equals("book") ? "Management Book" : "Parcel Wand",
                        NamedTextColor.AQUA);
        var lore =
                type.equals("book")
                        ? List.of(
                                Component.text("Manage your settlement."),
                                Component.text("Right-click to open."))
                        : List.of(
                                Component.text("Select land for a parcel."),
                                Component.text("Left: first corner. Right: second."),
                                Component.text("Hold a named tag in your off hand."));
        boolean changed =
                !Objects.equals(meta.displayName(), title) || !Objects.equals(meta.lore(), lore);
        meta.displayName(title);
        meta.lore(lore);
        if (meta instanceof BookMeta book) {
            List<Component> pages =
                    List.of(Component.text("Right-click to manage your settlement."));
            changed |= !book.pages().equals(pages);
            book.title(Component.text("Management"));
            book.author(Component.text("ESMP"));
            book.pages(pages);
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    private void refreshInventory(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (refreshItem(item)) inventory.setItem(slot, item);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        var inventory = event.getPlayer().getInventory();
        var item = inventory.getItem(event.getNewSlot());
        if (refreshItem(item)) inventory.setItem(event.getNewSlot(), item);
    }

    private String tagName(Player player) {
        var tag = player.getInventory().getItemInOffHand();
        if (tag.getType() != Material.NAME_TAG
                || !tag.hasItemMeta()
                || !tag.getItemMeta().hasDisplayName()) return "";
        return PlainTextComponentSerializer.plainText()
                .serialize(tag.getItemMeta().displayName())
                .strip();
    }

    private Aim aim(Player player) {
        var eye = player.getEyeLocation();
        var direction = eye.getDirection();
        direction.setX(ParcelAim.cardinal(direction.getX()));
        direction.setY(ParcelAim.cardinal(direction.getY()));
        direction.setZ(ParcelAim.cardinal(direction.getZ()));
        direction.normalize();
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
        double reach = attribute == null ? 4.5 : attribute.getValue();
        double safe =
                ParcelAim.loadedReach(
                        eye.getX(),
                        eye.getZ(),
                        direction.getX(),
                        direction.getZ(),
                        reach,
                        player.getWorld()::isChunkLoaded);
        var hit =
                safe > 0
                        ? player.getWorld()
                                .rayTraceBlocks(
                                        eye, direction, safe, FluidCollisionMode.NEVER, false)
                        : null;
        if (hit != null && hit.getHitBlock() != null) {
            var block = hit.getHitBlock();
            return new Aim(ParcelCell.atBlock(block.getX(), block.getY(), block.getZ()), true);
        }
        return new Aim(
                ParcelAim.outsidePlayer(
                        eye.getX(),
                        eye.getY(),
                        eye.getZ(),
                        direction.getX(),
                        direction.getY(),
                        direction.getZ(),
                        ParcelCell.atBlock(
                                (int) Math.floor(player.getX()),
                                (int) Math.floor(player.getY()),
                                (int) Math.floor(player.getZ()))),
                false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void use(PlayerInteractEvent event) {
        String type = kind(event.getItem());
        if (type == null) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND || !runtime.ready()) return;
        Player player = event.getPlayer();
        refreshInventory(player);
        boolean right =
                event.getAction() == Action.RIGHT_CLICK_AIR
                        || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left =
                event.getAction() == Action.LEFT_CLICK_AIR
                        || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (type.equals("book")) {
            if (right) open(player);
            return;
        }
        if (!left && !right) return;
        if (event.getAction() == Action.LEFT_CLICK_AIR
                && Bukkit.getCurrentTick() - rightClicks.getOrDefault(player.getUniqueId(), -100)
                        <= 1) return;
        if (right) rightClicks.put(player.getUniqueId(), Bukkit.getCurrentTick());
        try {
            var target = aim(player).cell();
            String name = tagName(player);
            selectCell(player, left, target, name);
            if (right) previewAdd(player, settlementAt(player, target), name);
        } catch (IllegalArgumentException | SettlementException rejected) {
            player.sendMessage(Component.text(rejected.getMessage(), NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent event) {
        var player = event.getPlayer();
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING
                || !"stick".equals(kind(player.getInventory().getItemInMainHand()))
                || !runtime.ready()) return;
        if (Bukkit.getCurrentTick() - rightClicks.getOrDefault(player.getUniqueId(), -100) <= 1)
            return;
        var target = aim(player);
        if (!target.hitBlock()) selectCell(player, true, target.cell(), tagName(player));
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
                Component.text("Settlement Management\n", NamedTextColor.BLACK)
                        .append(link("Create here", "/settlement create", false))
                        .append(link("My Settlements", "/settlement list", false))
                        .append(link("Wallet", "/balance", false))
                        .append(link("Transactions", "/esmp economy history", false))
                        .append(link("Founding quote", "/settlement price", false))
                        .append(link("Natural land value", "/settlement landvalue", false))
                        .append(link("Parcel Wand", "/esmp tools parcel_wand", false));
        if (settlement == null) {
            player.openBook(
                    Book.builder()
                            .title(Component.text("Management"))
                            .author(Component.text("ESMP"))
                            .pages(
                                    front.append(
                                            Component.text(
                                                    "\nStand in a settlement to manage it.")))
                            .build());
            return;
        }
        String ref = settlement.id().toString();
        var land = runtime.land(settlement.id());
        Component governance =
                Component.text(
                                settlement.name()
                                        + " (#"
                                        + land.number()
                                        + ")\nRole: "
                                        + SettlementCommands.roleName(
                                                settlement.role(player.getUniqueId()))
                                        + "\n")
                        .append(link("Status", "/settlement info " + ref, false))
                        .append(link("Activity", "/settlement activity " + ref, false))
                        .append(link("Expansion quote", "/settlement price " + ref, false))
                        .append(link("Treasury", "/esmp treasury " + ref + " balance", false))
                        .append(
                                link(
                                        "Treasury history",
                                        "/esmp treasury " + ref + " history",
                                        false))
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
                        .append(link("Certification", "/settlement certification " + ref, false))
                        .append(
                                link(
                                        "Apply for certification",
                                        "/settlement apply " + ref + " ",
                                        true))
                        .append(
                                link(
                                        "Applications / review",
                                        "/settlement applications " + ref,
                                        false))
                        .append(
                                link(
                                        "Rename verified settlement (admin)",
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
        input = input.strip();
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

    private SettlementView settlementAt(Player player, ParcelCell cell) {
        var world = runtime.mainInstance(player.getWorld());
        return world == null ? null : runtime.index().at(world, cell.chunk().x(), cell.chunk().z());
    }

    private void select(Player player, boolean first, Location location) {
        selectCell(
                player,
                first,
                ParcelCell.atBlock(
                        location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                tagName(player));
    }

    private void selectCell(Player player, boolean first, ParcelCell cell, String name) {
        UUID actor = player.getUniqueId();
        UUID world = player.getWorld().getUID();
        var previous = selections.get(actor);
        if (previous == null || !previous.world().equals(world))
            previous = new Selection(world, null, null, name);
        if (first
                && cell.equals(previous.first())
                && previous.second() == null
                && name.equals(previous.name())) return;
        selections.put(
                actor,
                first
                        ? new Selection(world, cell, null, name)
                        : new Selection(world, previous.first(), cell, name));
        previews.remove(actor);
        drafts.remove(actor);
        views.remove(actor);
        renderUntil.put(actor, Math.addExact(runtime.clock().ticks(), 1200));
        player.sendMessage(
                Component.text((first ? "First" : "Second") + " corner: " + coordinates(cell)));
    }

    private static String coordinates(ParcelCell cell) {
        return (long) cell.x() * 8 + " " + (long) cell.y() * 8 + " " + (long) cell.z() * 8;
    }

    private ParcelDraft.Result draft(
            Player player,
            SettlementView settlement,
            String name,
            ParcelCell first,
            ParcelCell last) {
        boolean admin = runtime.administrator(player.getUniqueId());
        var key =
                new DraftKey(
                        player.getWorld().getUID(),
                        settlement == null ? null : settlement.id(),
                        first,
                        last,
                        name,
                        runtime.publicationVersion(),
                        admin);
        var cached = drafts.get(player.getUniqueId());
        if (cached != null && cached.key().equals(key)) return cached.result();
        var result =
                ParcelDraft.add(
                        settlement,
                        settlement == null ? null : runtime.land(settlement.id()),
                        player.getUniqueId(),
                        admin,
                        name,
                        first,
                        last,
                        player.getWorld().getMinHeight(),
                        player.getWorld().getMaxHeight());
        drafts.put(player.getUniqueId(), new DraftCache(key, result));
        return result;
    }

    private void previewAdd(Player player, SettlementView settlement, String rawName) {
        String name = LandName.normalize(rawName);
        var selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first() == null || selection.second() == null)
            throw new SettlementException("Set both corners first.");
        if (!selection.world().equals(player.getWorld().getUID()))
            throw new SettlementException("Return to your selection's world.");
        var result = draft(player, settlement, name, selection.first(), selection.second());
        var land = settlement == null ? null : runtime.land(settlement.id());
        var preview =
                new Preview(
                        player.getWorld().getUID(),
                        settlement == null ? null : settlement.id(),
                        name,
                        result.parcel(),
                        selection.first(),
                        selection.second(),
                        result.cells(),
                        result.blocked(),
                        null,
                        land,
                        settlement == null ? 0 : settlement.ownershipRevision(),
                        settlement == null ? 0 : settlement.chunkRevision(),
                        Math.addExact(runtime.clock().ticks(), 1200),
                        result.error());
        previews.put(player.getUniqueId(), preview);
        selections.put(
                player.getUniqueId(),
                new Selection(selection.world(), selection.first(), selection.second(), name));
        if (!result.valid()) {
            player.sendMessage(Component.text(result.error(), NamedTextColor.RED));
            return;
        }
        player.sendMessage(
                Component.text(
                                name
                                        + " · "
                                        + result.added()
                                        + (result.added() == 1 ? " cell " : " cells "))
                        .append(
                                Component.text("[Confirm]", NamedTextColor.BLUE)
                                        .clickEvent(
                                                ClickEvent.runCommand(
                                                        "/settlement parcel commit"))));
    }

    private String previewError(Player player, Preview preview) {
        if (preview.error() != null) return preview.error();
        var settlement =
                preview.settlement() == null ? null : runtime.index().byId(preview.settlement());
        if (!preview.world().equals(player.getWorld().getUID()))
            return "Return to your selection's world.";
        if (settlement == null
                || runtime.clock().ticks() >= preview.expires()
                || runtime.land(settlement.id()) != preview.expectedLand()
                || settlement.ownershipRevision() != preview.ownership()
                || settlement.chunkRevision() != preview.chunks())
            return "Selection changed. Preview it again.";
        return null;
    }

    private void commit(Player player) {
        var preview = previews.get(player.getUniqueId());
        if (preview == null) throw new SettlementException("Preview a selection first.");
        String error = previewError(player, preview);
        if (error != null) throw new SettlementException(error);
        previews.remove(player.getUniqueId());
        selections.remove(player.getUniqueId());
        drafts.remove(player.getUniqueId());
        renderUntil.remove(player.getUniqueId());
        var settlement = runtime.index().byId(preview.settlement());
        runtime.mutate(
                player,
                settlement.id(),
                null,
                () ->
                        preview.removal() == null
                                ? runtime.parcels()
                                        .add(
                                                player.getUniqueId(),
                                                settlement.id(),
                                                preview.name(),
                                                preview.cells(),
                                                player.getWorld().getMinHeight(),
                                                player.getWorld().getMaxHeight())
                                : runtime.parcels()
                                        .removeCell(
                                                player.getUniqueId(),
                                                settlement.id(),
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
                            ? targetLocation(player, aim(player).cell())
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
            drafts.remove(actor);
            edgeCaches.remove(actor);
            views.remove(actor);
            hints.remove(actor);
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
                    if (!s.worldInstanceId().equals(runtime.mainInstance(player.getWorld())))
                        throw new SettlementException("Go to this parcel's world.");
                    views.put(actor, new View(player.getWorld().getUID(), s.id(), p.id()));
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
                String error = null;
                if (!p.cells().contains(cell)
                        || cell.neighbors().stream().allMatch(p.cells()::contains))
                    error = "Choose a surface cell.";
                var remaining = new HashSet<>(p.cells());
                remaining.remove(cell);
                if (error == null && !ParcelCell.connected(remaining))
                    error = "Keep the remaining cells connected.";
                boolean admin = runtime.administrator(actor);
                if (!admin && (!s.role(actor).canManage() && !p.holders().contains(actor)))
                    error = "Ask an owner or holder to edit this parcel.";
                if (!admin && runtime.land(s.id()).bans().contains(actor))
                    error = "You are banned here.";
                previews.put(
                        actor,
                        new Preview(
                                player.getWorld().getUID(),
                                s.id(),
                                p.name(),
                                p.id(),
                                cell,
                                cell,
                                Set.of(cell),
                                Set.of(),
                                cell,
                                runtime.land(s.id()),
                                s.ownershipRevision(),
                                s.chunkRevision(),
                                Math.addExact(runtime.clock().ticks(), 1200),
                                error));
                player.sendMessage(
                        error == null
                                ? Component.text("Remove this cell ")
                                        .append(
                                                Component.text("[Confirm]", NamedTextColor.BLUE)
                                                        .clickEvent(
                                                                ClickEvent.runCommand(
                                                                        "/settlement parcel"
                                                                                + " commit")))
                                : Component.text(error, NamedTextColor.RED));
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
                        "/settlement parcel remove <settlement> <parcel> <blockX> <blockY> <blockZ>"
                                + " | commit",
                        "/settlement parcel holders <settlement> <parcel> <player,player|none>",
                        "/settlement parcel policy <settlement> <parcel> <action>"
                                + " <OWNER,GOVERNOR,CITIZEN,HOLDER,PUBLIC|none>",
                        "/settlement parcel rename <settlement> <parcel> <name> | delete"
                                + " <settlement> <parcel>",
                        "/settlement parcel requests <settlement> | approve|reject <settlement>"
                                + " <requestUUID>")) player.sendMessage(Component.text(line));
    }

    private static Location targetLocation(Player player, ParcelCell cell) {
        return new Location(player.getWorld(), cell.x() * 8.0, cell.y() * 8.0, cell.z() * 8.0);
    }

    private List<ParcelOverlayGeometry.Edge> existingEdges(
            Player player,
            ParcelCell center,
            View view,
            ParcelCell invalidFirst,
            ParcelCell invalidLast,
            Set<ParcelCell> blocked) {
        var key =
                new EdgeKey(
                        player.getWorld().getUID(),
                        center,
                        runtime.publicationVersion(),
                        view == null ? null : view.parcel(),
                        invalidFirst,
                        invalidLast,
                        blocked);
        var cached = edgeCaches.get(player.getUniqueId());
        if (cached != null && cached.key().equals(key)) return cached.edges();
        var edges = new LinkedHashSet<ParcelOverlayGeometry.Edge>();
        var instance = runtime.mainInstance(player.getWorld());
        if (instance != null) {
            for (int x = center.x() - 3; x <= center.x() + 3; x++)
                for (int y = center.y() - 3; y <= center.y() + 3; y++)
                    for (int z = center.z() - 3; z <= center.z() + 3; z++) {
                        var cell = new ParcelCell(x, y, z);
                        var settlement =
                                runtime.index().at(instance, cell.chunk().x(), cell.chunk().z());
                        if (settlement == null) continue;
                        var land = runtime.land(settlement.id());
                        var parcel = land.cells().get(cell);
                        if (parcel != null && (view == null || view.parcel().equals(parcel.id())))
                            ParcelOverlayGeometry.exposedEdges(cell, parcel.cells(), edges);
                    }
        }
        edges.removeIf(edge -> hiddenEdge(edge, invalidFirst, invalidLast, blocked));
        var ordered = new ArrayList<>(edges);
        var eye = player.getEyeLocation();
        ordered.sort(
                Comparator.comparingDouble(
                        edge -> {
                            double x = edge.x() - eye.getX(),
                                    y = edge.y() - eye.getY(),
                                    z = edge.z() - eye.getZ();
                            return x * x + y * y + z * z;
                        }));
        var result = List.copyOf(ordered);
        edgeCaches.put(player.getUniqueId(), new EdgeCache(key, result));
        return result;
    }

    private static boolean hiddenEdge(
            ParcelOverlayGeometry.Edge edge,
            ParcelCell first,
            ParcelCell last,
            Set<ParcelCell> blocked) {
        if (first != null && last != null) {
            double maxX = (Math.max(first.x(), last.x()) + 1.0) * 8,
                    maxY = (Math.max(first.y(), last.y()) + 1.0) * 8,
                    maxZ = (Math.max(first.z(), last.z()) + 1.0) * 8;
            if (edge.x() >= Math.min(first.x(), last.x()) * 8.0
                    && edge.y() >= Math.min(first.y(), last.y()) * 8.0
                    && edge.z() >= Math.min(first.z(), last.z()) * 8.0
                    && edge.x() + (edge.axis() == 0 ? 8 : 0) <= maxX
                    && edge.y() + (edge.axis() == 1 ? 8 : 0) <= maxY
                    && edge.z() + (edge.axis() == 2 ? 8 : 0) <= maxZ) return true;
        }
        if (blocked.isEmpty()) return false;
        int x = (int) Math.floorDiv(edge.x(), 8),
                y = (int) Math.floorDiv(edge.y(), 8),
                z = (int) Math.floorDiv(edge.z(), 8);
        for (int i = 0; i <= 1; i++)
            for (int j = 0; j <= 1; j++) {
                var cell =
                        edge.axis() == 0
                                ? new ParcelCell(x, y - i, z - j)
                                : edge.axis() == 1
                                        ? new ParcelCell(x - i, y, z - j)
                                        : new ParcelCell(x - i, y - j, z);
                if (blocked.contains(cell)) return true;
            }
        return false;
    }

    private void render() {
        if (!runtime.ready()) return;
        frame++;
        for (var player : plugin.getServer().getOnlinePlayers()) {
            UUID actor = player.getUniqueId();
            boolean wand = "stick".equals(kind(player.getInventory().getItemInMainHand()));
            var preview = previews.get(actor);
            var selection = selections.get(actor);
            var view = views.get(actor);
            if (preview != null && runtime.clock().ticks() >= preview.expires()) {
                previews.remove(actor);
                preview = null;
            }
            if (view != null
                    && (runtime.clock().ticks() >= renderUntil.getOrDefault(actor, 0L)
                            || !view.world().equals(player.getWorld().getUID()))) {
                views.remove(actor);
                view = null;
            }
            boolean selected =
                    selection != null
                            && selection.world().equals(player.getWorld().getUID())
                            && runtime.clock().ticks() < renderUntil.getOrDefault(actor, 0L);
            if (!wand && preview == null && view == null && !selected) {
                lastTargets.remove(actor);
                drafts.remove(actor);
                edgeCaches.remove(actor);
                continue;
            }
            if (preview != null && !preview.world().equals(player.getWorld().getUID()))
                preview = null;
            var target = wand ? aim(player).cell() : null;
            var oldDraft = drafts.get(actor);
            ParcelCell first = null, last = null;
            Set<ParcelCell> blocked = Set.of();
            String error = null;
            String hint = "";
            if (preview != null) {
                first = preview.first();
                last = preview.last();
                blocked = preview.blocked();
                error = previewError(player, preview);
                hint = error == null ? preview.name() + " · Confirm in chat" : error;
            } else if (selected && selection.first() != null) {
                first = selection.first();
                last = selected && selection.second() != null ? selection.second() : target;
                if (first != null && last != null) {
                    String name =
                            selected && selection.second() != null
                                    ? selection.name()
                                    : tagName(player);
                    var result = draft(player, settlementAt(player, first), name, first, last);
                    blocked = result.blocked();
                    error = result.error();
                    hint =
                            error == null
                                    ? (selected && selection.first() != null
                                            ? "Right-click: second corner"
                                            : "Left-click: first corner")
                                    : error;
                }
            }
            if (wand && hint.isEmpty())
                hint =
                        tagName(player).isEmpty()
                                ? "Hold a named tag in your off hand."
                                : "Left-click: first corner";
            if (wand && target != null) hint = coordinates(target) + " · " + hint;
            boolean changed =
                    !Objects.equals(lastTargets.put(actor, target), target)
                            || oldDraft != drafts.get(actor)
                            || !Objects.equals(hints.get(actor), hint);
            if (!hint.isEmpty() && (changed || frame % 10 == 0)) {
                player.sendActionBar(
                        Component.text(
                                hint, error == null ? NamedTextColor.BLUE : NamedTextColor.RED));
                hints.put(actor, hint);
            }
            if (!changed && frame % 2 != 0) continue;
            int budget = PARTICLE_BUDGET;
            if (target != null)
                budget -=
                        ParcelOverlayGeometry.surface(
                                target,
                                Math.min(512, budget),
                                (x, y, z) -> particle(player, BLUE, x, y, z));
            if (first != null && last != null) {
                var color = error == null ? BLUE : RED;
                budget -=
                        ParcelOverlayGeometry.border(
                                first,
                                last,
                                Math.min(384, budget),
                                (x, y, z) -> particle(player, color, x, y, z));
            }
            if (error == null && !blocked.isEmpty()) {
                var cells = new ArrayList<>(blocked);
                var eye = player.getEyeLocation();
                cells.sort(
                        Comparator.comparingDouble(
                                c -> {
                                    double x = c.x() * 8 - eye.getX(),
                                            y = c.y() * 8 - eye.getY(),
                                            z = c.z() * 8 - eye.getZ();
                                    return x * x + y * y + z * z;
                                }));
                int remaining = Math.min(192, budget);
                for (var cell : cells) {
                    if (remaining < 24) break;
                    int used =
                            ParcelOverlayGeometry.border(
                                    cell,
                                    cell,
                                    Math.min(96, remaining),
                                    (x, y, z) -> particle(player, RED, x, y, z));
                    remaining -= used;
                    budget -= used;
                }
            }
            var eye = player.getEyeLocation();
            var center = ParcelCell.atBlock(eye.getBlockX(), eye.getBlockY(), eye.getBlockZ());
            var edges =
                    existingEdges(
                            player,
                            center,
                            view,
                            error == null ? null : first,
                            error == null ? null : last,
                            blocked);
            ParcelOverlayGeometry.edges(
                    edges,
                    (int) ((frame / 2) * 64 % Math.max(1, edges.size())),
                    Math.min(640, budget),
                    (x, y, z) -> particle(player, GREEN, x, y, z));
        }
    }

    private static void particle(
            Player player, Particle.DustOptions color, double x, double y, double z) {
        double dx = x - player.getX(),
                dy = y - player.getY() - player.getEyeHeight(),
                dz = z - player.getZ();
        if (dx * dx + dy * dy + dz * dz <= 72 * 72)
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, color);
    }

    @Override
    public void close() {
        if (renderTask != null) renderTask.cancel();
        selections.clear();
        previews.clear();
        lastIssue.clear();
        renderUntil.clear();
        drafts.clear();
        edgeCaches.clear();
        views.clear();
        hints.clear();
        lastTargets.clear();
        rightClicks.clear();
    }
}
