package xdu.undarubra.esmp.plugin.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.economy.*;
import xdu.undarubra.esmp.plugin.settlement.SettlementRuntime;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class EconomyCommands implements CommandExecutor, TabCompleter, AutoCloseable {
    private final JavaPlugin plugin;
    private final LedgerService ledger;
    private final SettlementRuntime settlements;
    private final Consumer<Throwable> fatal;
    private final Set<String> pending = new HashSet<>();
    private volatile boolean closed;

    public EconomyCommands(
            JavaPlugin plugin,
            LedgerService ledger,
            SettlementRuntime settlements,
            Consumer<Throwable> fatal) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.settlements = settlements;
        this.fatal = fatal;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ready(sender)) return true;
        try {
            var actor = actor(sender);
            if (command.getName().equals("balance")) {
                exact(args, 0, "/balance");
                Player player = player(sender);
                query(
                        sender,
                        () -> ledger.balance(actor, LedgerAccount.wallet(player.getUniqueId())),
                        amount ->
                                sender.sendMessage(
                                        Component.text(
                                                "Balance: " + ledger.currency().display(amount))));
            } else if (command.getName().equals("pay")) {
                between(args, 2, 3, "/pay <player> <amount> [transaction-id]");
                Player player = player(sender);
                UUID recipient = resolvePlayer(args[0]);
                long amount = amount(args[1], false);
                UUID id = id(args, 2);
                post(
                        sender,
                        new LedgerService.Request(
                                id,
                                actor,
                                LedgerService.Movement.TRANSFER,
                                LedgerAccount.wallet(player.getUniqueId()),
                                LedgerAccount.wallet(recipient),
                                amount,
                                "PLAYER_TRANSFER",
                                ""),
                        "Sent " + ledger.currency().display(amount) + ".");
            }
        } catch (LedgerException | IllegalArgumentException | SettlementException rejected) {
            sender.sendMessage(Component.text(rejected.getMessage()));
        }
        return true;
    }

    public boolean root(CommandSender sender, String[] args) {
        if (!ready(sender)) return true;
        try {
            if (args[0].equalsIgnoreCase("treasury")) treasury(sender, args);
            else economy(sender, args);
        } catch (LedgerException | IllegalArgumentException | SettlementException rejected) {
            sender.sendMessage(Component.text(rejected.getMessage()));
        }
        return true;
    }

    private void economy(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text(
                            "/esmp economy history [player] | budget | grant | grant-treasury"));
            return;
        }
        var actor = actor(sender);
        String verb = args[1].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "budget" -> {
                exact(args, 2, "/esmp economy budget");
                requireAdmin(sender);
                query(
                        sender,
                        () ->
                                ledger.budget(actor)
                                        .thenCombine(
                                                ledger.supply(actor),
                                                (budget, supply) ->
                                                        "Issuance: "
                                                                + budget.issued()
                                                                + " / "
                                                                + budget.limit()
                                                                + " · period "
                                                                + budget.period()
                                                                + " · next period in "
                                                                + budget.ticksRemaining()
                                                                + " ticks\nSupply: "
                                                                + supply.total()
                                                                + " (wallets "
                                                                + supply.wallets()
                                                                + ", treasuries "
                                                                + supply.treasuries()
                                                                + ")"),
                        text -> sender.sendMessage(Component.text(text)));
            }
            case "history" -> {
                between(args, 2, 3, "/esmp economy history [player]");
                UUID owner;
                if (args.length == 3) {
                    requireAdmin(sender);
                    owner = resolvePlayer(args[2]);
                } else owner = player(sender).getUniqueId();
                query(
                        sender,
                        () -> ledger.history(actor, LedgerAccount.wallet(owner)),
                        rows -> history(sender, rows));
            }
            case "grant", "grant-treasury" -> {
                between(
                        args,
                        4,
                        5,
                        "/esmp economy " + verb + " <recipient> <amount> [transaction-id]");
                requireAdmin(sender);
                LedgerAccount target =
                        verb.equals("grant")
                                ? LedgerAccount.wallet(resolvePlayer(args[2]))
                                : LedgerAccount.treasury(settlementId(args[2]));
                long amount = amount(args[3], false);
                post(
                        sender,
                        new LedgerService.Request(
                                id(args, 4),
                                actor,
                                LedgerService.Movement.ISSUANCE,
                                null,
                                target,
                                amount,
                                "ADMIN_GRANT",
                                ""),
                        "Granted " + ledger.currency().display(amount) + ".");
            }
            default -> throw new LedgerException("Use history, budget, grant or grant-treasury.");
        }
    }

    private void treasury(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(
                    Component.text(
                            "/esmp treasury <settlement>"
                                    + " balance|history|deposit|withdraw|pay|budget|allowance"));
            sender.sendMessage(Component.text("/esmp treasury <settlement> allowance [governor]"));
            return;
        }
        UUID settlement = settlementId(args[1]);
        var account = LedgerAccount.treasury(settlement);
        var actor = actor(sender);
        String verb = args[2].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "balance" -> {
                exact(args, 3, "/esmp treasury <settlement> balance");
                query(
                        sender,
                        () -> ledger.balance(actor, account),
                        amount ->
                                sender.sendMessage(
                                        Component.text(
                                                "Treasury: " + ledger.currency().display(amount))));
            }
            case "history" -> {
                exact(args, 3, "/esmp treasury <settlement> history");
                query(sender, () -> ledger.history(actor, account), rows -> history(sender, rows));
            }
            case "deposit", "withdraw" -> {
                between(
                        args,
                        4,
                        5,
                        "/esmp treasury <settlement> " + verb + " <amount> [transaction-id]");
                var wallet = LedgerAccount.wallet(player(sender).getUniqueId());
                long amount = amount(args[3], false);
                boolean deposit = verb.equals("deposit");
                post(
                        sender,
                        new LedgerService.Request(
                                id(args, 4),
                                actor,
                                LedgerService.Movement.TRANSFER,
                                deposit ? wallet : account,
                                deposit ? account : wallet,
                                amount,
                                deposit ? "SETTLEMENT_DONATION" : "TREASURY_WITHDRAWAL",
                                ""),
                        (deposit ? "Deposited " : "Withdrew ")
                                + ledger.currency().display(amount)
                                + ".");
            }
            case "pay" -> {
                between(
                        args,
                        5,
                        6,
                        "/esmp treasury <settlement> pay <player> <amount> [transaction-id]");
                player(sender);
                long amount = amount(args[4], false);
                post(
                        sender,
                        new LedgerService.Request(
                                id(args, 5),
                                actor,
                                LedgerService.Movement.TRANSFER,
                                account,
                                LedgerAccount.wallet(resolvePlayer(args[3])),
                                amount,
                                "TREASURY_PAYMENT",
                                ""),
                        "Treasury paid " + ledger.currency().display(amount) + ".");
            }
            case "budget" -> {
                between(
                        args,
                        5,
                        6,
                        "/esmp treasury <settlement> budget <governor> <remaining-amount>"
                                + " [operation-id]");
                player(sender);
                UUID governor = resolvePlayer(args[3]);
                settlements.rememberAdministrator(governor);
                long amount = amount(args[4], true);
                query(
                        sender,
                        () -> ledger.setAllowance(actor, settlement, governor, amount, id(args, 5)),
                        result ->
                                sender.sendMessage(
                                        receiptText(
                                                result.replayed()
                                                        ? "Budget already recorded."
                                                        : "Spending budget: "
                                                                + ledger.currency()
                                                                        .display(result.amount()),
                                                result.id())));
            }
            case "allowance" -> {
                between(args, 3, 4, "/esmp treasury <settlement> allowance [governor]");
                UUID governor =
                        args.length == 4 ? resolvePlayer(args[3]) : player(sender).getUniqueId();
                settlements.rememberAdministrator(governor);
                query(
                        sender,
                        () -> ledger.allowance(actor, settlement, governor),
                        amount ->
                                sender.sendMessage(
                                        Component.text(
                                                "Remaining allowance: "
                                                        + ledger.currency().display(amount))));
            }
            default ->
                    throw new LedgerException(
                            "Use balance, history, deposit, withdraw, pay, budget or allowance.");
        }
    }

    private void post(CommandSender sender, LedgerService.Request request, String message) {
        query(
                sender,
                () -> ledger.post(request),
                result -> {
                    if (result.replayed()) {
                        sender.sendMessage(receiptText("Already recorded.", result.id()));
                        return;
                    }
                    sender.sendMessage(receiptText(message, result.id()));
                    if (sender instanceof Player player)
                        player.playSound(
                                player.getLocation(),
                                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                .25f,
                                1.2f);
                    if (result.target() != null
                            && result.target().kind() == LedgerAccount.Kind.WALLET
                            && !result.target().owner().equals(request.actor().player())) {
                        var recipient = plugin.getServer().getPlayer(result.target().owner());
                        if (recipient != null) {
                            recipient.sendActionBar(
                                    Component.text(
                                            "Received "
                                                    + ledger.currency().display(result.amount())));
                            recipient.playSound(
                                    recipient.getLocation(),
                                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                    .25f,
                                    1.2f);
                        }
                    }
                });
    }

    private static Component receiptText(String text, UUID id) {
        return Component.text(text)
                .hoverEvent(Component.text("Receipt: " + id))
                .clickEvent(ClickEvent.copyToClipboard(id.toString()));
    }

    private void history(CommandSender sender, List<LedgerService.Receipt> rows) {
        sender.sendMessage(Component.text("Recent transactions: " + rows.size()));
        for (var row : rows)
            sender.sendMessage(
                    receiptText(
                                    ledger.currency().display(row.amount())
                                            + " · "
                                            + row.movement()
                                            + " · "
                                            + row.reason()
                                            + " · tick "
                                            + row.gameTick(),
                                    row.id())
                            .color(NamedTextColor.GRAY));
    }

    private LedgerService.Actor actor(CommandSender sender) {
        UUID player = sender instanceof Player p ? p.getUniqueId() : null;
        if (player != null) settlements.rememberAdministrator(player);
        return new LedgerService.Actor(player, sender.hasPermission("esmp.admin"));
    }

    private boolean ready(CommandSender sender) {
        if (!sender.hasPermission("esmp.economy") && !sender.hasPermission("esmp.admin")) {
            sender.sendMessage(Component.text("Currency access denied."));
            return false;
        }
        if (closed || !settlements.ready()) {
            sender.sendMessage(Component.text("The economy is loading."));
            return false;
        }
        return true;
    }

    private <T> void query(
            CommandSender sender, Supplier<CompletableFuture<T>> action, Consumer<T> success) {
        String key = sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE";
        if (!pending.add(key))
            throw new LedgerException("Your previous request is still processing.");
        try {
            action.get()
                    .whenComplete(
                            (value, failure) -> {
                                if (closed) return;
                                try {
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        pending.remove(key);
                                                        if (closed) return;
                                                        CommandSender current =
                                                                sender instanceof Player p
                                                                        ? plugin.getServer()
                                                                                .getPlayer(
                                                                                        p
                                                                                                .getUniqueId())
                                                                        : sender;
                                                        Throwable cause = failure;
                                                        while (cause instanceof CompletionException
                                                                && cause.getCause() != null)
                                                            cause = cause.getCause();
                                                        if (cause == null) {
                                                            if (current != null)
                                                                success.accept(value);
                                                        } else if (cause instanceof LedgerException
                                                                || cause
                                                                        instanceof
                                                                        SettlementException
                                                                || cause
                                                                        instanceof
                                                                        IllegalArgumentException
                                                                || cause
                                                                        instanceof
                                                                        RejectedExecutionException) {
                                                            if (current != null)
                                                                current.sendMessage(
                                                                        Component.text(
                                                                                cause
                                                                                        .getMessage()));
                                                        } else fatal.accept(cause);
                                                    });
                                } catch (IllegalPluginAccessException stopping) {
                                    if (!closed)
                                        plugin.getLogger()
                                                .warning(
                                                        "Currency feedback interrupted by"
                                                                + " shutdown");
                                }
                            });
        } catch (RuntimeException failure) {
            pending.remove(key);
            throw failure;
        }
    }

    private UUID resolvePlayer(String input) {
        var online = plugin.getServer().getPlayerExact(input);
        if (online != null) return online.getUniqueId();
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
        }
        var cached = plugin.getServer().getOfflinePlayerIfCached(input);
        if (cached == null) throw new LedgerException("Use a known player name or UUID.");
        return cached.getUniqueId();
    }

    private UUID settlementId(String input) {
        var settlement = settlements.find(input);
        if (settlement != null) return settlement.id();
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException invalid) {
            throw new LedgerException("Settlement not found.");
        }
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        throw new LedgerException("Use this command in game.");
    }

    private static void requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("esmp.admin"))
            throw new LedgerException("Administrator required.");
    }

    private static long amount(String input, boolean zero) {
        try {
            long amount = Long.parseLong(input);
            if (amount < 0 || (!zero && amount == 0)) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException invalid) {
            throw new LedgerException(
                    zero ? "Enter a non-negative whole amount." : "Enter a positive whole amount.");
        }
    }

    private static UUID id(String[] args, int index) {
        return args.length > index ? UUID.fromString(args[index]) : UUID.randomUUID();
    }

    private static void exact(String[] args, int count, String usage) {
        if (args.length != count) throw new LedgerException("Usage: " + usage);
    }

    private static void between(String[] args, int min, int max, String usage) {
        if (args.length < min || args.length > max) throw new LedgerException("Usage: " + usage);
    }

    public List<String> rootSuggestions(CommandSender sender, String[] args) {
        if (!sender.hasPermission("esmp.economy") && !sender.hasPermission("esmp.admin"))
            return List.of();
        List<String> choices = List.of();
        if (args[0].equalsIgnoreCase("economy") && args.length == 2)
            choices =
                    sender.hasPermission("esmp.admin")
                            ? List.of("history", "budget", "grant", "grant-treasury")
                            : List.of("history");
        else if (args[0].equalsIgnoreCase("treasury") && args.length == 2)
            choices =
                    settlements.index().all().stream()
                            .map(s -> Long.toString(settlements.land(s.id()).number()))
                            .toList();
        else if (args[0].equalsIgnoreCase("treasury") && args.length == 3)
            choices =
                    List.of(
                            "balance",
                            "history",
                            "deposit",
                            "withdraw",
                            "pay",
                            "budget",
                            "allowance");
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(c -> c.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("pay")
                && args.length == 1
                && sender.hasPermission("esmp.economy"))
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(
                            n ->
                                    n.toLowerCase(Locale.ROOT)
                                            .startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        return List.of();
    }

    @Override
    public void close() {
        closed = true;
        pending.clear();
    }
}
