package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ChunkUpgradeGUI implements Listener {

    private static final String TITLE = "§6Улучшение чанка";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final ChunkUpgradeManager chunkUpgradeManager;
    private final DefensiveManager defensiveManager;

    public ChunkUpgradeGUI(SovereigntyPlugin plugin, CountryManager countryManager,
                           ChunkUpgradeManager chunkUpgradeManager,
                           DefensiveManager defensiveManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
        this.chunkUpgradeManager = chunkUpgradeManager;
        this.defensiveManager = defensiveManager;
    }

    public void open(Player player) {
        String country = countryManager.getCountryName(player.getUniqueId());
        if (country == null) {
            player.sendMessage("§cСоздайте страну.");
            return;
        }

        var chunk = player.getLocation().getChunk();
        String currentType = chunkUpgradeManager.getChunkType(chunk.getWorld(), chunk.getX(), chunk.getZ());
        boolean isDefensive = defensiveManager.isDefended(chunk.getWorld(), chunk.getX(), chunk.getZ());
        double defensiveCost = plugin.getConfig().getDouble("chunk-upgrades.defensive.cost", 5000.0);

        Inventory inv = Bukkit.createInventory(null, 36, TITLE);
        fillEmptyWithGlass(inv);

        inv.setItem(10, upgradeButton("normal", "Обычный", Material.STONE, currentType,
                List.of("§7Без эффектов.")));
        inv.setItem(11, upgradeButton("farm", "Фермерский", Material.WHEAT, currentType,
                List.of("§7Приносит пассивный доход", "§7в казну страны каждый час.")));
        inv.setItem(12, upgradeButton("mining", "Шахтёрский", Material.DIAMOND_PICKAXE, currentType,
                List.of("§7Даёт эффект Спешки", "§7при активации бонуса.")));
        inv.setItem(13, upgradeButton("military", "Военный", Material.IRON_SWORD, currentType,
                List.of("§7Усиливает защиту территории.")));
        inv.setItem(14, upgradeButton("trade", "Торговый", Material.GOLD_INGOT, currentType,
                List.of("§7Увеличивает доход ферм", "§7и даёт бонус к продажам.")));

        // Кнопка защитного флага (теперь платная)
        ItemStack shield = new ItemStack(isDefensive ? Material.SHIELD : Material.GRAY_DYE);
        ItemMeta shieldMeta = shield.getItemMeta();
        shieldMeta.setDisplayName(isDefensive ? "§aЗащита активна" : "§7Включить защиту");
        List<String> shieldLore = new ArrayList<>();
        shieldLore.add("§7Защищённый чанк нельзя захватить");
        shieldLore.add("§7и нельзя строить/ломать врагу");
        shieldLore.add("§7После смерти владельца защита спадает на 1-2 минуты");
        shieldLore.add("§7Стоимость включения: " + (isDefensive ? "§aбесплатно (уже включена)" : plugin.getEconomyManager().format(defensiveCost)));
        shieldMeta.setLore(shieldLore);
        shield.setItemMeta(shieldMeta);
        inv.setItem(22, shield);

        inv.setItem(31, createButton(Material.OAK_SIGN, "§eТекущий тип: " + currentType, List.of()));
        inv.setItem(35, createButton(Material.BARRIER, "§cНазад в главное меню", List.of()));

        player.openInventory(inv);
    }

    private ItemStack upgradeButton(String type, String label, Material material, String currentType, List<String> description) {
        boolean isCurrent = type.equals(currentType);
        double cost = plugin.getConfig().getDouble("chunk-upgrades." + type + ".cost", 0.0);

        ItemStack icon = new ItemStack(isCurrent ? Material.EMERALD : material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName((isCurrent ? "§a" : "§f") + label + (isCurrent ? " (выбран)" : ""));
        List<String> lore = new ArrayList<>(description);
        lore.add("§7Стоимость: " + (cost > 0 ? plugin.getEconomyManager().format(cost) : "бесплатно"));
        lore.add("§eНажмите, чтобы установить");
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private void fillEmptyWithGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        String type = switch (slot) {
            case 10 -> "normal";
            case 11 -> "farm";
            case 12 -> "mining";
            case 13 -> "military";
            case 14 -> "trade";
            default -> null;
        };

        if (slot == 22) {
            var chunk = player.getLocation().getChunk();
            boolean current = defensiveManager.isDefended(chunk.getWorld(), chunk.getX(), chunk.getZ());
            if (!current) {
                double cost = plugin.getConfig().getDouble("chunk-upgrades.defensive.cost", 5000.0);
                if (!plugin.getEconomyManager().has(player, cost)) {
                    player.sendMessage("§cНедостаточно денег. Нужно: " + plugin.getEconomyManager().format(cost));
                    open(player);
                    return;
                }
                plugin.getEconomyManager().withdraw(player, cost);
                player.sendMessage("§aЗащита чанка включена!");
            } else {
                player.sendMessage("§cЗащита чанка выключена.");
            }
            chunkUpgradeManager.setDefensive(player, !current);
            open(player);
            return;
        }

        if (slot == 35) {
            player.closeInventory();
            player.performCommand("country");
            return;
        }

        if (type != null) {
            if (chunkUpgradeManager.setChunkType(player, type)) {
                player.sendMessage(plugin.getConfig().getString("messages.chunk-upgraded", "§aЧанк улучшен!")
                        .replace("%type%", type));
            } else {
                player.sendMessage("§cНе удалось улучшить. Проверьте, что вы на своём чанке, и хватает ли денег.");
            }
            open(player);
        }
    }
}
