package com.example.marketgui;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Обработчик команд /shop.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final MarketPlugin plugin;
    private final ShopManager shopManager;
    private final ShopGUI shopGUI;

    public ShopCommand(MarketPlugin plugin, ShopManager shopManager, ShopGUI shopGUI) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.shopGUI = shopGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("marketgui.use")) {
                player.sendMessage("§cУ вас нет прав на использование магазина.");
                return true;
            }
            shopGUI.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                return handleAdd(player, args);
            }
            case "sell" -> {
                if (!player.hasPermission("marketgui.sell")) {
                    player.sendMessage("§cУ вас нет прав на продажу.");
                    return true;
                }
                shopGUI.openSellerMenu(player);
                return true;
            }
            case "reload" -> {
                if (!player.hasPermission("marketgui.admin")) {
                    player.sendMessage("§cУ вас нет прав администратора.");
                    return true;
                }
                plugin.reloadConfig();
                shopManager.save();
                player.sendMessage("§aКонфиг перезагружен.");
                return true;
            }
            default -> {
                player.sendMessage("§cНеизвестная подкоманда. См. /shop");
                return true;
            }
        }
    }

    private boolean handleAdd(Player player, String[] args) {
        if (!player.hasPermission("marketgui.sell")) {
            player.sendMessage("§cУ вас нет прав на продажу.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cИспользование:");
            player.sendMessage("§7/shop add <материал> <кол-во> [money <сумма>] [категория]");
            player.sendMessage("§7/shop add money <сумма> [категория]");
            player.sendMessage("§7Держите предмет в руке, которым хотите торговать.");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§cВозьмите в руку предмет, который хотите продать.");
            return true;
        }

        Material priceMaterial = null;
        int priceAmount = 0;
        double moneyPrice = 0.0;
        String categoryName = null;
        int index = 1;

        if (args[index].equalsIgnoreCase("money")) {
            // /shop add money <сумма> [категория]
            if (args.length < 3) {
                player.sendMessage("§cУкажите сумму: /shop add money <сумма>");
                return true;
            }
            try {
                moneyPrice = Double.parseDouble(args[index + 1]);
                if (moneyPrice <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage("§cСумма должна быть положительным числом.");
                return true;
            }
            index += 2;
        } else {
            // /shop add <материал> <кол-во> [money <сумма>] [категория]
            if (args.length < 3) {
                player.sendMessage("§cУкажите материал и количество: /shop add <материал> <кол-во>");
                return true;
            }
            priceMaterial = Material.matchMaterial(args[index].toUpperCase(Locale.ROOT));
            if (priceMaterial == null) {
                player.sendMessage("§cМатериал не найден: " + args[index]);
                return true;
            }
            try {
                priceAmount = Integer.parseInt(args[index + 1]);
                if (priceAmount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage("§cКоличество должно быть положительным числом.");
                return true;
            }
            index += 2;

            if (index < args.length && args[index].equalsIgnoreCase("money")) {
                if (index + 1 >= args.length) {
                    player.sendMessage("§cУкажите сумму после money.");
                    return true;
                }
                try {
                    moneyPrice = Double.parseDouble(args[index + 1]);
                    if (moneyPrice <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage("§cСумма должна быть положительным числом.");
                    return true;
                }
                index += 2;
            }
        }

        if (index < args.length) {
            categoryName = args[index];
            if (shopManager.getCategory(categoryName) == null) {
                player.sendMessage("§cКатегория не найдена: " + categoryName);
                return true;
            }
        } else {
            categoryName = shopManager.getCategoryForMaterial(hand.getType());
        }

        ItemStack toSell = hand.clone();
        player.getInventory().setItemInMainHand(null);

        shopManager.addListing(player, toSell, priceMaterial, priceAmount, moneyPrice, categoryName);

        player.sendMessage("§aТовар выставлен на продажу!");
        player.sendMessage("§7Категория: §f" + categoryName);
        StringBuilder priceDesc = new StringBuilder();
        if (priceMaterial != null && priceAmount > 0) {
            priceDesc.append(priceAmount).append(" × ").append(shopManager.getMaterialName(priceMaterial));
        }
        if (moneyPrice > 0) {
            if (priceDesc.length() > 0) priceDesc.append(" + ");
            priceDesc.append(plugin.getEconomyManager().format(moneyPrice));
        }
        player.sendMessage("§7Цена: §f" + priceDesc);
        shopGUI.openSellerMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("add", "sell"));
            if (sender.hasPermission("marketgui.admin")) options.add("reload");
            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            List<String> options = Arrays.stream(Material.values())
                    .map(Material::name)
                    .filter(m -> m.startsWith(args[1].toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toList());
            options.add("money");
            return options;
        }
        if (args[0].equalsIgnoreCase("add")) {
            // Подсказка слова money
            return List.of("money");
        }
        return Collections.emptyList();
    }
}
