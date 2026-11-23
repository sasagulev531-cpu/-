package Frontiks.Craft.casino.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import Frontiks.Craft.casino.CasinoPlugin;
import Frontiks.Craft.casino.table.RouletteTable;
import Frontiks.Craft.casino.util.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** /roulette — все команды плагина. */
public class RouletteCommand implements CommandExecutor, TabCompleter {

    private final CasinoPlugin plugin;

    public RouletteCommand(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, Messages.get("help"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> requireAdmin(sender, () -> create(sender));
            case "remove" -> requireAdmin(sender, () -> remove(sender, args));
            case "list" -> list(sender);
            case "chip" -> chip(sender, args);
            case "balance" -> balance(sender);
            case "give" -> requireAdmin(sender, () -> give(sender, args, true));
            case "set" -> requireAdmin(sender, () -> give(sender, args, false));
            case "spin" -> requireAdmin(sender, () -> spin(sender));
            case "reload" -> requireAdmin(sender, () -> reload(sender));
            default -> send(sender, Messages.get("help"));
        }
        return true;
    }

    private void requireAdmin(CommandSender sender, Runnable action) {
        if (!sender.hasPermission("roulette.admin")) {
            send(sender, Messages.get("no-permission"));
            return;
        }
        action.run();
    }

    private void create(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, Messages.get("only-players"));
            return;
        }
        RouletteTable table = plugin.getTableManager().createTable(player);
        send(sender, Messages.format("table-created", "id", table.getId()));
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            send(sender, plugin.getTableManager().removeTable(args[1])
                    ? Messages.format("table-removed", "id", args[1])
                    : Messages.get("table-not-found"));
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, Messages.get("only-players"));
            return;
        }
        RouletteTable nearest = plugin.getTableManager().nearestTable(player.getLocation(), 60);
        if (nearest == null) {
            send(sender, Messages.get("table-not-found"));
            return;
        }
        send(sender, plugin.getTableManager().removeTable(nearest.getId())
                ? Messages.format("table-removed", "id", nearest.getId())
                : Messages.get("table-not-found"));
    }

    private void list(CommandSender sender) {
        send(sender, Messages.get("tables-list-header"));
        for (RouletteTable table : plugin.getTableManager().all()) {
            var c = table.getCenter();
            String phaseKey = switch (table.getPhase()) {
                case BETTING -> "phase-betting";
                case SPINNING -> "phase-spinning";
                case RESULT -> "phase-result";
            };
            send(sender, Messages.format("tables-list-entry",
                    "id", table.getId(),
                    "phase", Messages.raw(phaseKey),
                    "world", c.getWorld().getName(),
                    "x", c.getBlockX(), "y", c.getBlockY(), "z", c.getBlockZ()));
        }
    }

    private void chip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, Messages.get("only-players"));
            return;
        }
        if (args.length == 1) {
            String list = plugin.getChipManager().getDenominations().stream()
                    .map(String::valueOf).collect(Collectors.joining(", "));
            send(sender, Messages.format("chip-list", "list", list));
            return;
        }
        try {
            long amount = Long.parseLong(args[1]);
            if (!plugin.getChipManager().select(player, amount)) {
                send(sender, Messages.format("chip-list", "list",
                        plugin.getChipManager().getDenominations().stream()
                                .map(String::valueOf).collect(Collectors.joining(", "))));
                return;
            }
            send(sender, Messages.format("chip-selected", "amount", amount));
        } catch (NumberFormatException e) {
            send(sender, Messages.format("chip-list", "list",
                    plugin.getChipManager().getDenominations().stream()
                            .map(String::valueOf).collect(Collectors.joining(", "))));
        }
    }

    private void balance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, Messages.get("only-players"));
            return;
        }
        send(sender, Messages.format("balance",
                "amount", plugin.getBalanceProvider().getBalance(player.getUniqueId())));
    }

    private void give(CommandSender sender, String[] args, boolean add) {
        if (args.length < 3) {
            send(sender, Messages.get("help"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, Messages.get("player-not-found"));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            send(sender, Messages.get("help"));
            return;
        }
        if (add) {
            plugin.getBalanceProvider().deposit(target.getUniqueId(), amount);
            send(sender, Messages.format("balance-given", "amount", amount,
                    "player", target.getName(),
                    "balance", plugin.getBalanceProvider().getBalance(target.getUniqueId())));
        } else {
            Frontiks.Craft.casino.balance.BalanceProvider provider = plugin.getBalanceProvider();
            long current = provider.getBalance(target.getUniqueId());
            long targetBalance = Math.max(0, amount);
            if (targetBalance > current) {
                provider.deposit(target.getUniqueId(), targetBalance - current);
            } else if (targetBalance < current) {
                provider.withdraw(target.getUniqueId(), current - targetBalance);
            }
            send(sender, Messages.format("balance-set", "player", target.getName(), "balance", targetBalance));
        }
    }

    private void spin(CommandSender sender) {
        RouletteTable table;
        if (sender instanceof Player player) {
            table = plugin.getTableManager().nearestTable(player.getLocation(), 60);
        } else {
            table = plugin.getTableManager().all().stream().findFirst().orElse(null);
        }
        if (table == null) {
            send(sender, Messages.get("table-not-found"));
            return;
        }
        table.beginSpin();
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getChipManager().reload();
        send(sender, Messages.get("reloaded"));
    }

    private void send(CommandSender sender, net.kyori.adventure.text.Component component) {
        Frontiks.Craft.casino.util.Messages.send(sender, component);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "chip", "balance"));
            if (sender.hasPermission("roulette.admin")) {
                subs.addAll(List.of("create", "remove", "give", "set", "spin", "reload"));
            }
            String prefix = args[0].toLowerCase();
            subs.stream().filter(s -> s.startsWith(prefix)).forEach(result::add);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            plugin.getTableManager().all().forEach(t -> result.add(t.getId()));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set"))) {
            result.add("1000");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("chip")) {
            plugin.getChipManager().getDenominations().forEach(d -> result.add(String.valueOf(d)));
        }
        return result;
    }
}
