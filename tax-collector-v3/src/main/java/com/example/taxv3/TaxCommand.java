package com.example.taxv3;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Обработчик команд /tax.
 */
public class TaxCommand implements CommandExecutor, TabCompleter {

    private final TaxPlugin plugin;
    private final TaxManager taxManager;
    private final DataManager dataManager;
    private final TaxGUI taxGUI;

    public TaxCommand(TaxPlugin plugin, TaxManager taxManager,
                      DataManager dataManager, TaxGUI taxGUI) {
        this.plugin = plugin;
        this.taxManager = taxManager;
        this.dataManager = dataManager;
        this.taxGUI = taxGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("tax.use")) {
                player.sendMessage("§cУ вас нет прав.");
                return true;
            }
            taxGUI.open(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pay" -> {
                taxGUI.open(player);
                return true;
            }
            case "debts" -> {
                return showAllDebts(player);
            }
            case "admin" -> {
                return adminCommand(player, args);
            }
            default -> {
                player.sendMessage("§cНеизвестная подкоманда. См. /tax");
                return true;
            }
        }
    }

    private boolean showAllDebts(Player player) {
        if (!player.hasPermission("tax.debts")) {
            player.sendMessage("§cУ вас нет прав.");
            return true;
        }

        Map<UUID, DataManager.PlayerTaxData> all = dataManager.getAllPlayers();
        player.sendMessage("§6===== Долги игроков =====");

        boolean found = false;
        for (Map.Entry<UUID, DataManager.PlayerTaxData> entry : all.entrySet()) {
            DataManager.PlayerTaxData d = entry.getValue();
            if (d.getDebt() <= 0) continue;
            found = true;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString().substring(0, 8);
            player.sendMessage("§f" + name + " §7— §c" +
                    String.format("%.2f", d.getDebt()) + " §7(просрочек: " + d.getMissed() + ")");
        }

        if (!found) player.sendMessage("§aНикто не должен!");
        return true;
    }

    private boolean adminCommand(Player player, String[] args) {
        if (!player.hasPermission("tax.admin")) {
            player.sendMessage("§cУ вас нет прав администратора.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§6===== Админ-команды =====");
            player.sendMessage("§f/tax admin collect §7— собрать налог сейчас");
            player.sendMessage("§f/tax admin info <ник> §7— инфо об игроке");
            player.sendMessage("§f/tax admin clear <ник> §7— обнулить долг");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "collect" -> {
                World world = player.getWorld();
                taxManager.collectNow(world);
                player.sendMessage("§aСбор налога завершён.");
                return true;
            }
            case "info" -> {
                if (args.length < 3) {
                    player.sendMessage("§cИспользование: /tax admin info <ник>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("§cИгрок не в сети.");
                    return true;
                }
                double debt = dataManager.getDebt(target.getUniqueId());
                int missed = dataManager.getMissed(target.getUniqueId());
                player.sendMessage("§f" + target.getName() + " §7— долг: §c" +
                        String.format("%.2f", debt) + " §7(просрочек: " + missed + ")");
                return true;
            }
            case "clear" -> {
                if (args.length < 3) {
                    player.sendMessage("§cИспользование: /tax admin clear <ник>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("§cИгрок не в сети.");
                    return true;
                }
                dataManager.clearDebt(target.getUniqueId());
                dataManager.resetMissed(target.getUniqueId());
                player.sendMessage("§aДолг обнулён.");
                return true;
            }
            default -> {
                player.sendMessage("§cНеизвестная админ-подкоманда.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("pay", "debts"));
            if (sender.hasPermission("tax.admin")) options.add("admin");
            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("collect", "info", "clear").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
