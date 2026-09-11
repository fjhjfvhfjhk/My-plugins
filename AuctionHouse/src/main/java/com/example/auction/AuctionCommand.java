package com.example.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final AuctionPlugin plugin;
    private final AuctionManager manager;
    private final AuctionGUI gui;

    public AuctionCommand(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getAuctionManager();
        this.gui = new AuctionGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }

        if (args.length == 0) {
            gui.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /auc add <цена> [время_в_минутах]");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    player.sendMessage(plugin.getConfig().getString("messages.wrong-item", "§cВ руке должен быть предмет."));
                    return true;
                }
                double startPrice;
                try {
                    startPrice = Double.parseDouble(args[1]);
                    double minPrice = plugin.getConfig().getDouble("min-start-price", 10.0);
                    if (startPrice < minPrice) startPrice = minPrice;
                    if (startPrice <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getConfig().getString("messages.invalid-price", "§cЦена должна быть положительным числом.")
                            .replace("%min%", String.valueOf(plugin.getConfig().getDouble("min-start-price", 10))));
                    return true;
                }
                int minutes = plugin.getConfig().getInt("default-auction-time", 60);
                if (args.length >= 3) {
                    try {
                        minutes = Integer.parseInt(args[2]);
                        int max = plugin.getConfig().getInt("max-auction-time", 180);
                        if (minutes < 1 || minutes > max) {
                            player.sendMessage(plugin.getConfig().getString("messages.invalid-time", "§cВремя должно быть от 1 до %max% минут.")
                                    .replace("%max%", String.valueOf(max)));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cВремя должно быть числом.");
                        return true;
                    }
                }
                // Снимаем предмет с руки
                ItemStack toSell = hand.clone();
                player.getInventory().setItemInMainHand(null);
                int id = manager.createAuction(player, toSell, startPrice, minutes);
                if (id != -1) {
                    player.sendMessage(plugin.getConfig().getString("messages.auction-created", "§aАукцион создан!")
                            .replace("%id%", String.valueOf(id))
                            .replace("%item%", toSell.getType().name().toLowerCase().replace('_', ' '))
                            .replace("%price%", plugin.getEconomyManager().format(startPrice))
                            .replace("%time%", String.valueOf(minutes)));
                } else {
                    player.sendMessage("§cОшибка создания аукциона.");
                    player.getInventory().addItem(toSell);
                }
                return true;
            }
            case "bid" -> {
                if (args.length < 3) {
                    player.sendMessage("§cИспользование: /auc bid <id> <сумма>");
                    return true;
                }
                int id;
                try { id = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                    player.sendMessage("§cID должен быть числом.");
                    return true;
                }
                double amount;
                try { amount = Double.parseDouble(args[2]); if (amount <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) {
                    player.sendMessage("§cСумма должна быть положительным числом.");
                    return true;
                }
                manager.placeBid(player, id, amount);
                return true;
            }
            case "biditem" -> {
                player.sendMessage("§cСтавки предметами пока не реализованы.");
                return true;
            }
            case "cancel" -> {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /auc cancel <id>");
                    return true;
                }
                int id;
                try { id = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                    player.sendMessage("§cID должен быть числом.");
                    return true;
                }
                manager.cancelAuction(player, id);
                return true;
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /auc info <id>");
                    return true;
                }
                int id;
                try { id = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                    player.sendMessage("§cID должен быть числом.");
                    return true;
                }
                Auction auction = plugin.getDatabaseManager().getAuction(id);
                if (auction == null) {
                    player.sendMessage(plugin.getConfig().getString("messages.auction-not-found", "§cАукцион не найден."));
                    return true;
                }
                player.sendMessage("§6=== Аукцион #" + id + " ===");
                player.sendMessage("§7Продавец: §f" + Bukkit.getOfflinePlayer(auction.getSeller()).getName());
                player.sendMessage("§7Предмет: §f" + auction.getItem().getType().name().toLowerCase().replace('_', ' '));
                player.sendMessage("§7Стартовая цена: §f" + plugin.getEconomyManager().format(auction.getStartPrice()));
                player.sendMessage("§7Текущая цена: §f" + plugin.getEconomyManager().format(auction.getCurrentPrice()));
                if (auction.getCurrentBidder() != null)
                    player.sendMessage("§7Лидер: §f" + Bukkit.getOfflinePlayer(auction.getCurrentBidder()).getName());
                long remaining = Math.max(0, (auction.getEndTime() - System.currentTimeMillis()) / 1000);
                player.sendMessage("§7Осталось: §f" + (remaining / 60) + " мин " + (remaining % 60) + " сек");
                player.sendMessage("§7Статус: §f" + auction.getStatus());
                return true;
            }
            default -> {
                player.sendMessage("§cНеизвестная подкоманда. См. /auc");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("add", "bid", "biditem", "cancel", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return List.of("<цена>");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            return List.of("[время_в_минутах]");
        }
        if (args[0].equalsIgnoreCase("bid") || args[0].equalsIgnoreCase("cancel") || args[0].equalsIgnoreCase("info")) {
            if (args.length == 2) {
                // Подсказываем ID активных аукционов
                return plugin.getDatabaseManager().getActiveAuctions().stream()
                        .map(a -> String.valueOf(a.getId()))
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
