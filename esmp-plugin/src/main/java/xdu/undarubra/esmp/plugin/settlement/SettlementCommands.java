package xdu.undarubra.esmp.plugin.settlement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import xdu.undarubra.esmp.civilization.acquisition.Acquisition;
import xdu.undarubra.esmp.civilization.certification.CertificationApplication;
import xdu.undarubra.esmp.civilization.certification.CertificationView;
import xdu.undarubra.esmp.civilization.settlement.ChunkPosition;
import xdu.undarubra.esmp.civilization.settlement.ClaimQuote;
import xdu.undarubra.esmp.civilization.settlement.Role;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.civilization.settlement.SettlementOffer;
import xdu.undarubra.esmp.civilization.settlement.SettlementView;
import xdu.undarubra.esmp.plugin.CommandArguments;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SettlementCommands implements CommandExecutor, TabCompleter {
    private static final List<String> COMMANDS =
            List.of(
                    "create",
                    "claim",
                    "purchase",
                    "unclaim",
                    "info",
                    "list",
                    "members",
                    "view",
                    "invite",
                    "offers",
                    "accept",
                    "decline",
                    "cancel",
                    "promote",
                    "demote",
                    "transfer",
                    "acquisition",
                    "acquisitions",
                    "accept-transfer",
                    "review-transfer",
                    "cancel-transfer",
                    "kick",
                    "leave",
                    "rename",
                    "abandon",
                    "confirm",
                    "history",
                    "activity",
                    "local-activity",
                    "price",
                    "landvalue",
                    "ban",
                    "unban",
                    "bans",
                    "certification",
                    "apply",
                    "applications",
                    "review",
                    "verified",
                    "parcel",
                    "building",
                    "bypass",
                    "help");
    private final JavaPlugin plugin;
    private final SettlementRuntime runtime;
    private final LandValueRuntime landValues;

    public SettlementCommands(
            JavaPlugin plugin, SettlementRuntime runtime, LandValueRuntime landValues) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.landValues = landValues;
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] rawArgs) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    Component.text(
                            "Use Settlement commands in-game. Console diagnostics: esmp status."));
            return true;
        }
        if (!player.hasPermission("esmp.settlement")) {
            player.sendMessage(Component.text("You do not have permission to use Settlements."));
            return true;
        }
        try {
            String[] args = CommandArguments.parse(rawArgs);
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                help(player);
                return true;
            }
            if (!runtime.ready())
                throw new SettlementException("Settlements are initializing or unavailable.");
            UUID actor = player.getUniqueId();
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> {
                    exact(args, 1, "create");
                    requestQuote(player, null);
                }
                case "parcel" -> runtime.tools().command(player, args);
                case "building" ->
                        runtime.buildings()
                                .command(
                                        player, java.util.Arrays.copyOfRange(args, 1, args.length));
                case "landvalue" -> {
                    exact(args, 1, "landvalue");
                    landValues.inspect(player);
                }
                case "price" -> {
                    optionalName(args, "price");
                    requestQuote(player, args.length == 2 ? named(args[1]) : null);
                }
                case "purchase" -> {
                    exact(args, 2, "purchase <quoteUUID>");
                    UUID quoteId = parseUuid(args[1]);
                    runtime.rememberAdministrator(actor);
                    runtime.query(
                            player,
                            () -> runtime.service().quote(actor, quoteId),
                            q -> {
                                Player current = plugin.getServer().getPlayer(actor);
                                if (current == null) return;
                                if (!current.hasPermission("esmp.settlement"))
                                    throw new SettlementException(
                                            "You do not have permission to use Settlements.");
                                // Receipts remain replayable after moving away.
                                if (!q.purchased()
                                        && (!q.worldInstance()
                                                        .equals(
                                                                runtime.mainInstance(
                                                                        current.getWorld()))
                                                || !q.chunk().equals(chunk(current))))
                                    throw new SettlementException(
                                            "Return to the quoted world and chunk ("
                                                    + q.chunk().x()
                                                    + ", "
                                                    + q.chunk().z()
                                                    + ") before purchasing, or request a new"
                                                    + " quote.");
                                runtime.mutate(
                                        current,
                                        q.founding() ? null : q.settlementId(),
                                        new SettlementRuntime.Reservation(
                                                q.worldInstance(), q.chunk()),
                                        () -> runtime.service().purchase(actor, q.id()));
                            });
                }
                case "ban", "unban" -> {
                    exact(args, 3, args[0] + " <settlement> <player|uuid>");
                    var settlement = named(args[1]);
                    if (!runtime.administrator(actor)) manage(player, settlement);
                    UUID target = resolvePlayer(args[2]);
                    runtime.rememberAdministrator(target);
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () ->
                                    runtime.service()
                                            .ban(
                                                    actor,
                                                    settlement.id(),
                                                    target,
                                                    args[0].equalsIgnoreCase("ban")));
                }
                case "bans" -> {
                    exact(args, 2, "bans <settlement>");
                    var settlement = named(args[1]);
                    player.sendMessage(
                            Component.text(
                                    "Banned players: " + runtime.land(settlement.id()).bans()));
                }
                case "certification" -> {
                    exact(args, 2, "certification <settlement>");
                    UUID id = referenceId(args[1]);
                    runtime.query(
                            player,
                            () -> runtime.certification().view(id),
                            view -> {
                                Player current = plugin.getServer().getPlayer(actor);
                                if (current != null) showCertification(current, view);
                            });
                }
                case "apply" -> {
                    if (args.length != 2 && args.length != 3)
                        throw new SettlementException(
                                "Usage: /settlement apply <settlement> [proposed-name]");
                    UUID id = named(args[1]).id();
                    String proposedName = args.length == 3 ? args[2] : null;
                    // Governance, bans, eligibility and replay are authoritative in the repository.
                    runtime.mutate(
                            player,
                            id,
                            null,
                            () -> runtime.certification().apply(actor, id, proposedName));
                }
                case "applications" -> {
                    exact(args, 2, "applications <settlement>");
                    UUID id = referenceId(args[1]);
                    runtime.query(
                            player,
                            () -> runtime.certification().applications(id),
                            applications -> {
                                Player current = plugin.getServer().getPlayer(actor);
                                if (current == null) return;
                                current.sendMessage(
                                        Component.text(
                                                "Certification applications: "
                                                        + applications.size()));
                                for (var application : applications)
                                    showApplication(current, application);
                            });
                }
                case "review" -> {
                    if (args.length != 4 && args.length != 5)
                        throw new SettlementException(
                                "Usage: /settlement review <settlement> <applicationUUID>"
                                        + " <approve|deny> [note]");
                    if (!runtime.administrator(actor))
                        throw new SettlementException("Administrator required");
                    UUID id = referenceId(args[1]);
                    UUID applicationId = parseUuid(args[2]);
                    String action = args[3].toLowerCase(Locale.ROOT);
                    if (!action.equals("approve") && !action.equals("deny"))
                        throw new SettlementException("Review action must be approve or deny.");
                    String note = args.length == 5 ? args[4].trim() : "";
                    if (note.length() > 256)
                        throw new SettlementException(
                                "Decision notes must not exceed 256 characters.");
                    runtime.mutate(
                            player,
                            id,
                            null,
                            () ->
                                    runtime.certification()
                                            .decide(
                                                    actor,
                                                    id,
                                                    applicationId,
                                                    action.equals("approve"),
                                                    note));
                }
                case "verified" -> {
                    exact(args, 1, "verified");
                    runtime.query(
                            player,
                            () -> runtime.certification().verifiedSettlements(),
                            settlements -> {
                                Player current = plugin.getServer().getPlayer(actor);
                                if (current == null) return;
                                current.sendMessage(
                                        Component.text(
                                                "Named verified Settlements: "
                                                        + settlements.size()));
                                for (var settlement : settlements)
                                    current.sendMessage(
                                            Component.text(
                                                            settlement.officialName()
                                                                    + " (#"
                                                                    + settlement.number()
                                                                    + ") | settlementId="
                                                                    + settlement.id())
                                                    .clickEvent(
                                                            ClickEvent.runCommand(
                                                                    "/settlement certification "
                                                                            + settlement.id())));
                            });
                }
                case "claim" -> {
                    exact(args, 2, "claim <settlement>");
                    requestQuote(player, named(args[1]));
                }
                case "unclaim" -> {
                    exact(args, 1, "unclaim");
                    mainWorld(player);
                    SettlementView settlement = here(player);
                    manage(player, settlement);
                    ChunkPosition chunk = chunk(player);
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () -> runtime.service().unclaim(actor, settlement.id(), chunk));
                }
                case "info", "members" -> {
                    optionalName(args, args[0]);
                    SettlementView settlement = args.length == 2 ? named(args[1]) : here(player);
                    var land = runtime.land(settlement.id());
                    String displayLabel =
                            land.officialName() == null
                                    ? settlement.name()
                                    : settlement.name() + " (#" + land.number() + ")";
                    int count = settlement.chunks().size();
                    player.sendMessage(
                            Component.text(
                                    displayLabel
                                            + " · "
                                            + count
                                            + (count == 1 ? " chunk" : " chunks")));
                    player.sendMessage(
                            Component.text("Certification: " + land.certification())
                                    .clickEvent(
                                            ClickEvent.runCommand(
                                                    "/settlement certification "
                                                            + settlement.id())));
                    player.sendMessage(Component.text("Role: " + roleName(settlement.role(actor))));
                    player.sendMessage(Component.text("Owner: " + playerName(settlement.owner())));
                    settlement.members().entrySet().stream()
                            .sorted(Comparator.comparing(e -> e.getKey().toString()))
                            .limit(50)
                            .forEach(
                                    e ->
                                            player.sendMessage(
                                                    Component.text(
                                                            roleName(e.getValue())
                                                                    + ": "
                                                                    + playerName(e.getKey()))));
                    if (args[0].equalsIgnoreCase("info")) showActivity(player, settlement);
                }
                case "activity" -> {
                    optionalName(args, "activity");
                    showActivity(player, args.length == 2 ? named(args[1]) : here(player));
                }
                case "local-activity" -> {
                    if (!runtime.administrator(actor))
                        throw new SettlementException("Administrator required.");
                    if (args.length > 3)
                        throw new SettlementException(
                                "Usage: /settlement local-activity [settlement] [page]");
                    var settlement = args.length >= 2 ? named(args[1]) : here(player);
                    int page = args.length == 3 ? Integer.parseInt(args[2]) : 1;
                    if (page < 1) throw new SettlementException("Page must be positive.");
                    runtime.query(
                            player,
                            () -> runtime.activity().localActivity(settlement.id()),
                            view -> {
                                var chunks =
                                        view.rawScores().keySet().stream()
                                                .sorted(
                                                        Comparator.comparingInt(ChunkPosition::x)
                                                                .thenComparingInt(ChunkPosition::z))
                                                .toList();
                                int pages = Math.max(1, (chunks.size() + 31) / 32);
                                if (page > pages)
                                    throw new SettlementException("Last page: " + pages);
                                var regions = new java.util.HashMap<ChunkPosition, Integer>();
                                for (int i = 0; i < view.regions().size(); i++)
                                    for (var chunk : view.regions().get(i))
                                        regions.put(chunk, i + 1);
                                player.sendMessage(
                                        Component.text(
                                                "Local Activity · tick "
                                                        + view.measuredAtTick()
                                                        + " · threshold "
                                                        + view.threshold()
                                                        + " · "
                                                        + chunks.size()
                                                        + " chunks · "
                                                        + view.regions().size()
                                                        + " derived regions · page "
                                                        + page
                                                        + "/"
                                                        + pages));
                                for (int i = (page - 1) * 32;
                                        i < Math.min(page * 32, chunks.size());
                                        i++) {
                                    var chunk = chunks.get(i);
                                    player.sendMessage(
                                            Component.text(
                                                    String.format(
                                                            Locale.ROOT,
                                                            "%d,%d · raw %.3f · support %.3f ·"
                                                                    + " region %s",
                                                            chunk.x(),
                                                            chunk.z(),
                                                            view.rawScores().get(chunk),
                                                            view.support().get(chunk),
                                                            regions.containsKey(chunk)
                                                                    ? regions.get(chunk).toString()
                                                                    : "none")));
                                }
                            });
                }
                case "list" -> {
                    exact(args, 1, "list");
                    var visible =
                            runtime.index().all().stream()
                                    .filter(
                                            s ->
                                                    s.role(actor) != Role.PUBLIC
                                                            || runtime
                                                                    .land(s.id())
                                                                    .parcels()
                                                                    .values()
                                                                    .stream()
                                                                    .anyMatch(
                                                                            p ->
                                                                                    p.holders()
                                                                                            .contains(
                                                                                                    actor)))
                                    .sorted(Comparator.comparing(SettlementView::name))
                                    .limit(50)
                                    .toList();
                    player.sendMessage(
                            Component.text("Your Settlements (up to 50): " + visible.size()));
                    visible.forEach(
                            s ->
                                    player.sendMessage(
                                            Component.text(
                                                    s.name()
                                                            + " | "
                                                            + s.role(actor)
                                                            + " | "
                                                            + s.chunks().size()
                                                            + " chunks")));
                }
                case "view" -> {
                    optionalName(args, "view");
                    runtime.visualization().show(player, args.length == 2 ? named(args[1]) : null);
                    player.sendMessage(
                            Component.text(
                                    "Nearby borders for 200 game ticks: white=current chunk,"
                                            + " green=team, red=Public."));
                }
                case "invite", "promote" -> {
                    exact(args, 3, args[0] + " <settlement> <player|uuid>");
                    SettlementView settlement = named(args[1]);
                    if (args[0].equalsIgnoreCase("invite")) manage(player, settlement);
                    else owner(player, settlement);
                    UUID recipient = resolvePlayer(args[2]);
                    SettlementOffer.Type type =
                            args[0].equalsIgnoreCase("invite")
                                    ? SettlementOffer.Type.INVITE
                                    : SettlementOffer.Type.PROMOTE;
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () -> runtime.service().offer(actor, settlement.id(), recipient, type));
                }
                case "transfer" -> {
                    exact(args, 3, "transfer <settlement> <recipient>");
                    var settlement = named(args[1]);
                    owner(player, settlement);
                    UUID recipient = resolvePlayer(args[2]);
                    runtime.rememberAdministrator(recipient);
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () ->
                                    runtime.acquisitions()
                                            .propose(actor, settlement.id(), recipient));
                }
                case "acquisitions" -> {
                    exact(args, 2, "acquisitions <settlement>");
                    UUID id = referenceId(args[1]);
                    runtime.query(
                            player,
                            () -> runtime.acquisitions().history(id),
                            rows -> {
                                player.sendMessage(Component.text("Acquisitions: " + rows.size()));
                                for (var acquisition : rows) showAcquisition(player, acquisition);
                            });
                }
                case "acquisition" -> {
                    exact(args, 2, "acquisition <id>");
                    runtime.query(
                            player,
                            () -> runtime.acquisitions().view(parseUuid(args[1])),
                            acquisition -> showAcquisition(player, acquisition));
                }
                case "accept-transfer", "review-transfer", "cancel-transfer" -> {
                    String verb = args[0].toLowerCase(Locale.ROOT);
                    UUID id;
                    long expectedFee = 0;
                    boolean approve = false;
                    String note = "";
                    if (verb.equals("accept-transfer")) {
                        exact(args, 3, "accept-transfer <id> <quoted-fee>");
                        expectedFee = Long.parseLong(args[2]);
                        if (expectedFee <= 0)
                            throw new SettlementException(
                                    "Enter the positive fee shown on the proposal.");
                    } else if (verb.equals("review-transfer")) {
                        if (args.length != 3 && args.length != 4)
                            throw new SettlementException(
                                    "Usage: /settlement review-transfer <id> <approve|deny>"
                                            + " [note]");
                        if (!runtime.administrator(actor))
                            throw new SettlementException("Administrator required.");
                        if (!args[2].equalsIgnoreCase("approve")
                                && !args[2].equalsIgnoreCase("deny"))
                            throw new SettlementException("Choose approve or deny.");
                        approve = args[2].equalsIgnoreCase("approve");
                        note = args.length == 4 ? args[3] : "";
                    } else exact(args, 2, "cancel-transfer <id>");
                    id = parseUuid(args[1]);
                    long fee = expectedFee;
                    boolean approved = approve;
                    String decisionNote = note;
                    runtime.query(
                            player,
                            () -> runtime.acquisitions().view(id),
                            acquisition -> {
                                if (acquisition == null)
                                    throw new SettlementException("Acquisition not found.");
                                runtime.rememberAdministrator(acquisition.owner());
                                runtime.rememberAdministrator(acquisition.recipient());
                                runtime.mutate(
                                        player,
                                        acquisition.settlementId(),
                                        null,
                                        () ->
                                                switch (verb) {
                                                    case "accept-transfer" ->
                                                            runtime.acquisitions()
                                                                    .accept(actor, id, fee);
                                                    case "review-transfer" ->
                                                            runtime.acquisitions()
                                                                    .review(
                                                                            actor,
                                                                            id,
                                                                            approved,
                                                                            decisionNote);
                                                    default ->
                                                            runtime.acquisitions()
                                                                    .cancel(actor, id);
                                                });
                            });
                }
                case "abandon" -> {
                    exact(args, 2, "abandon <settlement>");
                    SettlementView settlement = named(args[1]);
                    owner(player, settlement);
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () ->
                                    runtime.service()
                                            .offer(
                                                    actor,
                                                    settlement.id(),
                                                    actor,
                                                    SettlementOffer.Type.ABANDON));
                }
                case "accept", "confirm", "cancel", "decline" -> {
                    exact(args, 2, args[0] + " <offer-id>");
                    UUID token = parseUuid(args[1]);
                    String verb = args[0].toLowerCase(Locale.ROOT);
                    runtime.query(
                            player,
                            () -> runtime.service().inspectOffer(token),
                            offer -> {
                                if (verb.equals("cancel")) {
                                    SettlementView settlement =
                                            runtime.index().byId(offer.settlementId());
                                    if (settlement == null)
                                        throw new SettlementException(
                                                "Settlement is no longer owned.");
                                    if (offer.type() == SettlementOffer.Type.INVITE)
                                        manage(player, settlement);
                                    else owner(player, settlement);
                                } else if (!offer.recipient().equals(actor))
                                    throw new SettlementException(
                                            "Only the nominated recipient may resolve this offer.");
                                runtime.mutate(
                                        player,
                                        offer.settlementId(),
                                        null,
                                        () ->
                                                switch (verb) {
                                                    case "accept" ->
                                                            runtime.service()
                                                                    .accept(actor, token, false);
                                                    case "confirm" ->
                                                            runtime.service()
                                                                    .accept(actor, token, true);
                                                    case "cancel" ->
                                                            runtime.service()
                                                                    .resolveOffer(
                                                                            actor, token, false);
                                                    default ->
                                                            runtime.service()
                                                                    .resolveOffer(
                                                                            actor, token, true);
                                                });
                            });
                }
                case "offers" -> {
                    exact(args, 1, "offers");
                    runtime.query(
                            player,
                            () -> runtime.service().offers(actor),
                            offers -> {
                                player.sendMessage(
                                        Component.text(
                                                "Pending offers (up to 50): " + offers.size()));
                                for (SettlementOffer offer : offers) {
                                    SettlementView settlement =
                                            runtime.index().byId(offer.settlementId());
                                    String name =
                                            settlement == null
                                                    ? offer.settlementId().toString()
                                                    : settlement.name();
                                    String verb =
                                            offer.type() == SettlementOffer.Type.ABANDON
                                                    ? "confirm"
                                                    : "accept";
                                    player.sendMessage(
                                            Component.text(
                                                            offer.type()
                                                                    + " | "
                                                                    + name
                                                                    + " | "
                                                                    + offer.id()
                                                                    + " | expires at game tick "
                                                                    + offer.expiresAtTick())
                                                    .clickEvent(
                                                            ClickEvent.suggestCommand(
                                                                    "/settlement "
                                                                            + verb
                                                                            + " "
                                                                            + offer.id())));
                                }
                            });
                }
                case "kick", "demote" -> {
                    exact(args, 3, args[0] + " <settlement> <player|uuid>");
                    SettlementView settlement = named(args[1]);
                    if (args[0].equalsIgnoreCase("demote")) owner(player, settlement);
                    else manage(player, settlement);
                    UUID target = resolvePlayer(args[2]);
                    boolean demote = args[0].equalsIgnoreCase("demote");
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () ->
                                    demote
                                            ? runtime.service()
                                                    .demote(actor, settlement.id(), target)
                                            : runtime.service()
                                                    .remove(actor, settlement.id(), target, false));
                }
                case "leave" -> {
                    exact(args, 2, "leave <settlement>");
                    SettlementView settlement = named(args[1]);
                    if (settlement.role(actor) == Role.OWNER
                            || settlement.role(actor) == Role.PUBLIC)
                        throw new SettlementException(
                                "Only Citizens and Governors can leave; Owners must transfer or"
                                        + " abandon.");
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () -> runtime.service().remove(actor, settlement.id(), actor, true));
                }
                case "rename" -> {
                    exact(args, 3, "rename <settlement> <new-name>");
                    SettlementView settlement = named(args[1]);
                    if (!runtime.administrator(actor))
                        throw new SettlementException(
                                "Only a server administrator may rename a VERIFIED Settlement");
                    runtime.mutate(
                            player,
                            settlement.id(),
                            null,
                            () -> runtime.service().rename(actor, settlement.id(), args[2]));
                }
                case "history" -> {
                    exact(args, 2, "history <settlement|uuid>");
                    UUID id = referenceId(args[1]);
                    runtime.query(
                            player,
                            () -> runtime.service().history(id),
                            events -> {
                                player.sendMessage(
                                        Component.text(
                                                "Latest historical events: " + events.size()));
                                events.forEach(
                                        event ->
                                                player.sendMessage(
                                                        Component.text(
                                                                (event.gameTick() == null
                                                                                ? "Legacy timestamp"
                                                                                        + " "
                                                                                        + event
                                                                                                .occurredAt()
                                                                                : "Game tick "
                                                                                        + event
                                                                                                .gameTick())
                                                                        + " | "
                                                                        + event.type()
                                                                        + " | "
                                                                        + event.detail())));
                            });
                }
                case "bypass" -> {
                    exact(args, 2, "bypass <on|off>");
                    if (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))
                        throw new SettlementException("Use bypass on or bypass off.");
                    player.sendMessage(
                            Component.text(
                                    "Explicit protection bypass="
                                            + runtime.bypass(
                                                    player, args[1].equalsIgnoreCase("on"))));
                }
                default -> help(player);
            }
        } catch (SettlementException | IllegalArgumentException rejected) {
            player.sendMessage(Component.text(rejected.getMessage()));
        }
        return true;
    }

    private UUID referenceId(String reference) {
        var settlement = runtime.find(reference);
        return settlement == null ? parseUuid(reference) : settlement.id();
    }

    private void showCertification(Player player, CertificationView view) {
        if (view == null) throw new SettlementException("Settlement not found.");
        player.sendMessage(
                Component.text(
                        "Settlement #"
                                + view.number()
                                + " · "
                                + view.status()
                                + (view.officialName() == null
                                        ? ""
                                        : " · " + view.officialName())));
        player.sendMessage(
                Component.text(
                        String.format(
                                Locale.ROOT,
                                "Age: %.2f / %.2f game days · Lifetime activity: %.2f / %.2f",
                                view.ageTicks() / 24000.0,
                                view.minimumAgeTicks() / 24000.0,
                                view.lifetimeCredit(),
                                view.minimumLifetimeCredit())));
        player.sendMessage(
                Component.text(
                        "Activity: "
                                + (view.active() ? "active" : "inactive")
                                + " · Eligible to apply: "
                                + (view.eligible() ? "yes" : "no")));
        if (view.pendingApplication() == null)
            player.sendMessage(Component.text("No pending application."));
        else showApplication(player, view.pendingApplication());
        Component actions =
                Component.text("[Applications]")
                        .clickEvent(
                                ClickEvent.runCommand(
                                        "/settlement applications " + view.settlementId()));
        if (view.eligible() && view.pendingApplication() == null) {
            actions =
                    actions.append(
                            Component.text(" [Apply]")
                                    .clickEvent(
                                            ClickEvent.suggestCommand(
                                                    "/settlement apply "
                                                            + view.settlementId()
                                                            + " ")));
        }
        player.sendMessage(actions);
    }

    private void showApplication(Player player, CertificationApplication application) {
        player.sendMessage(
                Component.text(
                        "applicationId="
                                + application.id()
                                + " | state="
                                + application.state()
                                + " | applicant="
                                + application.applicant()
                                + " | appliedTick="
                                + application.appliedTick()));
        player.sendMessage(
                Component.text(
                        "proposedName="
                                + (application.proposedName() == null
                                        ? "(none)"
                                        : application.proposedName())
                                + " | decidedBy="
                                + application.decidedBy()
                                + " | decidedTick="
                                + application.decidedTick()
                                + " | note="
                                + (application.decisionNote() == null
                                        ? ""
                                        : application.decisionNote())));
        if (application.state() == CertificationApplication.State.PENDING
                && runtime.administrator(player.getUniqueId())) {
            String command =
                    "/settlement review " + application.settlementId() + " " + application.id();
            player.sendMessage(
                    Component.text("[Review: approve]")
                            .clickEvent(ClickEvent.suggestCommand(command + " approve "))
                            .append(
                                    Component.text(" [Review: deny]")
                                            .clickEvent(
                                                    ClickEvent.suggestCommand(
                                                            command + " deny "))));
        }
    }

    private void requestQuote(Player player, SettlementView settlement) {
        UUID actor = player.getUniqueId();
        runtime.rememberAdministrator(actor);
        if (settlement != null) manage(player, settlement);
        UUID world = mainWorld(player);
        requireUnclaimedHere(player);
        ChunkPosition chunk = chunk(player);
        // Requires a committed natural profile; never force a chunk load/capture.
        runtime.query(
                player,
                () ->
                        settlement == null
                                ? runtime.service().quoteCreate(actor, world, chunk)
                                : runtime.service()
                                        .quoteClaim(actor, settlement.id(), world, chunk),
                q -> {
                    Player current = plugin.getServer().getPlayer(actor);
                    if (current != null) showQuote(current, q);
                });
    }

    private static void showQuote(Player player, ClaimQuote q) {
        player.sendMessage(
                Component.text(
                        (q.founding() ? "Founding quote" : "Expansion quote")
                                + " | Price: "
                                + q.amount()
                                + " | Payer: "
                                + q.payer()
                                + " "
                                + q.payerId()));
        player.sendMessage(Component.text(q.summary()));
        player.sendMessage(
                Component.text(
                        "Chunk: "
                                + q.chunk().x()
                                + ", "
                                + q.chunk().z()
                                + " | expires at game tick "
                                + q.expiresTick()
                                + (q.purchased() ? " | Purchased receipt" : "")));
        player.sendMessage(
                Component.text("[Confirm]")
                        .clickEvent(ClickEvent.suggestCommand("/settlement purchase " + q.id())));
    }

    static String roleName(Role role) {
        return switch (role) {
            case OWNER -> "Owner";
            case GOVERNOR -> "Governor";
            case CITIZEN -> "Citizen";
            case PUBLIC -> "Public";
        };
    }

    private void showActivity(Player player, SettlementView settlement) {
        runtime.query(
                player,
                () -> runtime.activity().status(settlement.id()),
                status -> {
                    String state =
                            status.state()
                                            == xdu.undarubra.esmp.civilization.activity
                                                    .ActivityStatus.State.ACTIVE
                                    ? "Active"
                                    : "Inactive";
                    player.sendMessage(
                            Component.text(
                                    String.format(
                                            Locale.ROOT,
                                            "%s · Activity %.1f · %s",
                                            settlement.name(),
                                            status.score(),
                                            state)));
                    player.sendMessage(
                            Component.text(
                                    String.format(
                                            Locale.ROOT,
                                            "Today: %.2f points",
                                            status.creditedToday())));
                    if (status.recoveryNeeded() > 0)
                        player.sendMessage(
                                Component.text(
                                        String.format(
                                                Locale.ROOT,
                                                "%.1f points to recover.",
                                                status.recoveryNeeded())));
                });
    }

    private static void manage(Player player, SettlementView settlement) {
        if (!settlement.role(player.getUniqueId()).canManage())
            throw new SettlementException(
                    "Only the Owner or a Governor may manage this Settlement.");
    }

    private static void owner(Player player, SettlementView settlement) {
        if (settlement.role(player.getUniqueId()) != Role.OWNER)
            throw new SettlementException(
                    "You do not have authority; only the Owner may perform this operation.");
    }

    private void requireUnclaimedHere(Player player) {
        if (runtime.at(player.getLocation()) != null)
            throw new SettlementException("This chunk is already claimed.");
    }

    private SettlementView named(String value) {
        var settlement = runtime.find(value);
        if (settlement == null) throw new SettlementException("Settlement not found: " + value);
        return settlement;
    }

    private SettlementView here(Player player) {
        SettlementView settlement = runtime.at(player.getLocation());
        if (settlement == null) throw new SettlementException("This chunk is unclaimed.");
        return settlement;
    }

    private UUID mainWorld(Player player) {
        UUID id = runtime.mainInstance(player.getWorld());
        if (id == null)
            throw new SettlementException("Claims are only allowed in the registered Main World.");
        return id;
    }

    private static ChunkPosition chunk(Player player) {
        return new ChunkPosition(
                player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
    }

    private UUID resolvePlayer(String input) {
        Player online = plugin.getServer().getPlayerExact(input);
        if (online != null) return online.getUniqueId();
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            /* Cached names only, no network lookup. */
        }
        OfflinePlayer known = plugin.getServer().getOfflinePlayerIfCached(input);
        if (known == null)
            throw new SettlementException(
                    "Player must be online/known to this server, or use their UUID.");
        return known.getUniqueId();
    }

    private String playerName(UUID player) {
        String name = plugin.getServer().getOfflinePlayer(player).getName();
        return name == null ? player.toString() : name;
    }

    private void showAcquisition(Player player, Acquisition acquisition) {
        if (acquisition == null) throw new SettlementException("Acquisition not found.");
        player.sendMessage(
                Component.text("Acquisition " + acquisition.id() + " · " + acquisition.state()));
        player.sendMessage(
                Component.text(
                        "Owner: "
                                + playerName(acquisition.owner())
                                + " → "
                                + playerName(acquisition.recipient())
                                + " · Fee: "
                                + acquisition.fee()
                                + " from recipient wallet · Land base: "
                                + acquisition.baseTotal()));
        player.sendMessage(
                Component.text(
                        "Expires at game tick "
                                + acquisition.expiresTick()
                                + (clockExpired(acquisition) ? " (expired)" : "")
                                + (acquisition.decisionNote() == null
                                        ? ""
                                        : " · " + acquisition.decisionNote())));
        if (acquisition.state() == Acquisition.State.PROPOSED
                && !clockExpired(acquisition)
                && player.getUniqueId().equals(acquisition.recipient())) {
            player.sendMessage(
                    Component.text("[Accept fee " + acquisition.fee() + "]")
                            .clickEvent(
                                    ClickEvent.suggestCommand(
                                            "/settlement accept-transfer "
                                                    + acquisition.id()
                                                    + " "
                                                    + acquisition.fee())));
        }
        if (acquisition.state() == Acquisition.State.ACCEPTED
                && runtime.administrator(player.getUniqueId())) {
            player.sendMessage(
                    Component.text("[Approve acquisition]")
                            .clickEvent(
                                    ClickEvent.suggestCommand(
                                            "/settlement review-transfer "
                                                    + acquisition.id()
                                                    + " approve")));
        }
    }

    private boolean clockExpired(Acquisition acquisition) {
        return runtime.clock().ticks() >= acquisition.expiresTick();
    }

    private static UUID parseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException invalid) {
            throw new SettlementException("Expected a valid UUID.");
        }
    }

    private static void exact(String[] args, int count, String usage) {
        if (args.length != count) throw new SettlementException("Usage: /settlement " + usage);
    }

    private static void optionalName(String[] args, String verb) {
        if (args.length > 2)
            throw new SettlementException("Usage: /settlement " + verb + " [settlement]");
    }

    private static void help(Player player) {
        for (String line :
                List.of(
                        "/settlement create | price [settlement] | claim <settlement> | unclaim |"
                                + " view [name]",
                        "/settlement purchase <quoteUUID> (confirm a quoted claim at its chunk)",
                        "Create, claim and price request quotes only; payment occurs on purchase.",
                        "Founding uses your wallet; expansion uses the Settlement treasury.",
                        "/esmp treasury <settlement> deposit <amount> (fund expansion)",
                        "/settlement info [name] | activity [name] | list | members [name] |"
                                + " history <name|uuid>",
                        "/settlement local-activity [settlement] [page] (admin; derived support"
                                + " only)",
                        "/settlement invite|promote|kick|demote <name> <player|uuid>",
                        "/settlement transfer <settlement> <recipient> (propose acquisition)",
                        "/settlement acquisition <id> | acquisitions <settlement>",
                        "/settlement accept-transfer <id> <quoted-fee> (recipient consent only)",
                        "/settlement review-transfer <id> <approve|deny> [note] (admin) |"
                                + " cancel-transfer <id>",
                        "/settlement offers | accept|decline|cancel <offer-id> | leave <name>",
                        "/settlement certification <settlement> | applications <settlement> |"
                                + " verified",
                        "/settlement apply <settlement> [proposed-name] (Owner/Governor; no fee)",
                        "/settlement review <settlement> <applicationUUID> <approve|deny> [note]"
                                + " (admin)",
                        "/settlement rename <settlement> <official-name> (admin; VERIFIED only)",
                        "/settlement ban|unban <settlement> <player> | bans <settlement>",
                        "/esmp tools <parcel_wand|management_book>",
                        "/settlement landvalue (natural base of the current chunk)",
                        "/settlement building help (bounded registration, baseline and station)",
                        "/settlement parcel help | abandon <settlement> | confirm <offer-id>"))
            player.sendMessage(Component.text(line));
        player.sendMessage(
                Component.text(
                        "Put multiword names and notes in double quotes, e.g. \"New Haven\"."));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)
                || !sender.hasPermission("esmp.settlement")
                || !runtime.ready()) return List.of();
        if (args.length == 1)
            return COMMANDS.stream()
                    .filter(s -> !s.equals("review") || sender.hasPermission("esmp.admin"))
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        // Parse complete quoted arguments while preserving the current (possibly empty) token.
        String[] parsed;
        try {
            parsed = CommandArguments.parse(args);
        } catch (IllegalArgumentException incompleteQuote) {
            parsed = args;
        }
        if (parsed.length == 0) return List.of();
        String verb = parsed[0].toLowerCase(Locale.ROOT);
        if (verb.equals("building"))
            return runtime.buildings()
                    .complete(java.util.Arrays.copyOfRange(parsed, 1, parsed.length));
        if (verb.equals("review") && !sender.hasPermission("esmp.admin")) return List.of();
        if (parsed.length == 4 && verb.equals("review")) {
            String prefix = parsed[3].toLowerCase(Locale.ROOT);
            return List.of("approve", "deny").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (parsed.length == 2
                && List.of(
                                "claim",
                                "price",
                                "info",
                                "activity",
                                "local-activity",
                                "members",
                                "view",
                                "invite",
                                "promote",
                                "demote",
                                "transfer",
                                "kick",
                                "leave",
                                "rename",
                                "abandon",
                                "history",
                                "certification",
                                "apply",
                                "applications",
                                "review")
                        .contains(verb)) {
            String prefix = parsed[1].toLowerCase(Locale.ROOT);
            return runtime.index().all().stream()
                    .map(SettlementView::name)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .limit(50)
                    .map(CommandArguments::quote)
                    .toList();
        }
        if (parsed.length == 3
                && List.of("invite", "promote", "demote", "transfer", "kick").contains(verb)) {
            String prefix = parsed[2].toLowerCase(Locale.ROOT);
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }
}
