package Frontiks.Craft.casino.commands;

import Frontiks.Craft.casino.CasinoPlugin;
import Frontiks.Craft.casino.balance.BalanceProvider;
import Frontiks.Craft.casino.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /roulette открывает GUI. Админские команды: give, set, reload. */
public final class RouletteCommand implements CommandExecutor, TabCompleter {
    private final CasinoPlugin plugin;

    public RouletteCommand(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, Messages.get("only-players"));
            } else if (!sender.hasPermission("roulette.play")) {
                send(sender, Messages.get("no-permission"));
            } else {
                plugin.getGui().open(player);
            }
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "balance" -> {
                if (sender instanceof Player p) {
                    send(sender, Messages.format("balance", "amount",
                            plugin.getBalanceProvider().getBalance(p.getUniqueId())));
                } else {
                    send(sender, Messages.get("only-players"));
                }
            }
            case "give", "set", "reload" -> {
                if (!sender.hasPermission("roulette.admin")) {
                    send(sender, Messages.get("no-permission"));
                } else if (args[0].equalsIgnoreCase("reload")) {
                    plugin.reloadConfig();
                    plugin.getChipManager().reload();
                    plugin.getGui().refreshAll();
                    send(sender, Messages.get("reloaded"));
                } else {
                    changeBalance(sender, args, args[0].equalsIgnoreCase("give"));
                }
            }
            default -> send(sender, Messages.get("help"));
        }
        return true;
    }

    private void changeBalance(CommandSender sender, String[] args, boolean add) {
        if (args.length != 3) {
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
            if (amount < 0 || amount > Integer.MAX_VALUE) {
                throw new NumberFormatException("range");
            }
        } catch (NumberFormatException e) {
            send(sender, Messages.get("invalid-amount"));
            return;
        }
        BalanceProvider provider = plugin.getBalanceProvider();
        long current = provider.getBalance(target.getUniqueId());
        if (add) {
            if (amount > Integer.MAX_VALUE - current) {
                send(sender, Messages.get("invalid-amount"));
                return;
            }
            provider.deposit(target.getUniqueId(), amount);
            send(sender, Messages.format("balance-given", "amount", amount,
                    "player", target.getName(), "balance", provider.getBalance(target.getUniqueId())));
        } else {
            if (amount > current) {
                provider.deposit(target.getUniqueId(), amount - current);
            } else if (amount < current && !provider.withdraw(target.getUniqueId(), current - amount)) {
                send(sender, Messages.get("not-enough-admin"));
                return;
            }
            send(sender, Messages.format("balance-set", "player", target.getName(),
                    "balance", provider.getBalance(target.getUniqueId())));
        }
        plugin.getGui().refreshAll();
    }

    private static void send(CommandSender sender, net.kyori.adventure.text.Component message) {
        Messages.send(sender, Messages.legacy(Messages.rawPrefix()).append(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("balance"));
            if (sender.hasPermission("roulette.admin")) {
                options.addAll(List.of("give", "set", "reload"));
            }
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length == 2 && sender.hasPermission("roulette.admin")
                && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        return List.of();
    }
}
