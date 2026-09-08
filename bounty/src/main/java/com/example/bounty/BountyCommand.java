package com.example.bounty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Обработчик команд /bounty.
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final BountyPlugin plugin;
    private final BountyManager bountyManager;
    private final BountyGUI bountyGUI;
    private final EconomyManager economyManager;

    public BountyCommand(BountyPlugin plugin, BountyManager bountyManager,
                         BountyGUI bountyGUI, EconomyManager economyManager) {
        this.plugin = plugin;
        this.bountyManager = bountyManager;
        this.bountyGUI = bountyGUI;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        if (!player.hasPermission("bounty.use")) {
            player.sendMessage("§cУ вас нет прав.");
            return true;
        }

        if (args.length == 0) {
            bountyGUI.open(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                showTopList(player);
                return true;
            }
            case "menu" -> {
                bountyGUI.open(player);
                return true;
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /bounty remove <ник>");
                    return true;
                }
                return removeBounty(player, args[1]);
            }
            default -> {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /bounty <ник> <сумма>");
                    return true;
                }
                return setBounty(player, args[0], args[1]);
            }
        }
    }

    private boolean setBounty(Player player, String targetName, String amountStr) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || target.getName() == null) {
            player.sendMessage("§cИгрок не найден: " + targetName);
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cНельзя назначить награду за самого себя.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage("§cСумма должна быть положительным числом.");
            return true;
        }

        if (!economyManager.has(player, amount)) {
            player.sendMessage("§cУ вас недостаточно денег. Нужно: " + economyManager.format(amount));
            return true;
        }

        economyManager.withdraw(player, amount);
        bountyManager.setBounty(target.getUniqueId(), player.getUniqueId(), amount);

        player.sendMessage("§aНаграда за " + target.getName() + " назначена: " + economyManager.format(amount));
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage("§4⚠ За вашу голову назначена награда " + economyManager.format(amount) + "!");
        }
        return true;
    }

    private boolean removeBounty(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || target.getName() == null) {
            player.sendMessage("§cИгрок не найден: " + targetName);
            return true;
        }

        BountyManager.BountyEntry bounty = bountyManager.getBounty(target.getUniqueId());
        if (bounty == null) {
            player.sendMessage("§cНаграды за этого игрока нет.");
            return true;
        }

        if (!bounty.setter.equals(player.getUniqueId())) {
            player.sendMessage("§cВы не назначали эту награду.");
            return true;
        }

        bountyManager.removeBounty(target.getUniqueId());
        economyManager.deposit(player, bounty.amount);
        player.sendMessage("§aНаграда снята. Вам возвращено: " + economyManager.format(bounty.amount));
        return true;
    }

    private void showTopList(Player player) {
        Map<UUID, BountyManager.BountyEntry> all = bountyManager.getAllBounties();
        player.sendMessage("§6===== Награды за головы =====");

        if (all.isEmpty()) {
            player.sendMessage("§aСейчас наград нет.");
            return;
        }

        List<Map.Entry<UUID, BountyManager.BountyEntry>> sorted = new ArrayList<>(all.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue().amount, a.getValue().amount));

        int count = 0;
        for (Map.Entry<UUID, BountyManager.BountyEntry> entry : sorted) {
            if (count >= 10) break;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString().substring(0, 8);
            player.sendMessage("§f" + name + " §7— §c" + economyManager.format(entry.getValue().amount));
            count++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list", "menu", "remove"));
            options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return options.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || Bukkit.getPlayer(args[0]) != null)) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
