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

import java.util.List;

public class ChunkBorderGUI implements Listener {

    private static final String TITLE = "§6Границы территории";

    private final SovereigntyPlugin plugin;
    private final ChunkBorderManager chunkBorderManager;

    public ChunkBorderGUI(SovereigntyPlugin plugin, ChunkBorderManager chunkBorderManager) {
        this.plugin = plugin;
        this.chunkBorderManager = chunkBorderManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);
        fillEmptyWithGlass(inv);

        inv.setItem(1, createButton(Material.WHITE_WOOL, "§fГраницы чанков",
                List.of("§7Показать границы каждого чанка", "§7цвет = тип чанка")));
        inv.setItem(3, createButton(Material.WHITE_STAINED_GLASS_PANE, "§fВнешний контур",
                List.of("§7Показать только внешнюю границу")));
        inv.setItem(5, createButton(Material.DIAMOND, "§aТипы чанков (цветные)",
                List.of("§7Цвет границы показывает тип:",
                        "§aЗелёный — ферма",
                        "§bГолубой — шахта",
                        "§cКрасный — военный",
                        "§6Золотой — торговый")));
        inv.setItem(7, createButton(Material.REDSTONE_BLOCK, "§cВыключить",
                List.of("§7Отключить отображение границ")));

        player.openInventory(inv);
    }

    private ItemStack createButton(Material mat, String name, List<String> lore) {
        ItemStack icon = new ItemStack(mat);
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
        if (slot == 1) {
            chunkBorderManager.setMode(player, 1);
            player.closeInventory();
        } else if (slot == 3) {
            chunkBorderManager.setMode(player, 2);
            player.closeInventory();
        } else if (slot == 5) {
            chunkBorderManager.setMode(player, 3);
            player.closeInventory();
        } else if (slot == 7) {
            chunkBorderManager.setMode(player, 0);
            player.closeInventory();
        }
    }
}
