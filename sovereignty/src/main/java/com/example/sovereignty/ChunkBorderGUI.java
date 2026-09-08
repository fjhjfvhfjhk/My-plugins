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

        inv.setItem(2, createButton(Material.GRASS_BLOCK, "§aГраницы чанков",
                List.of("§7Показать границы каждого чанка страны")));
        inv.setItem(4, createButton(Material.EMERALD_BLOCK, "§aВнешний контур",
                List.of("§7Показать только внешнюю границу страны")));
        inv.setItem(6, createButton(Material.REDSTONE_BLOCK, "§cВыключить",
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
        if (slot == 2) {
            chunkBorderManager.setMode(player, 1);
            player.closeInventory();
        } else if (slot == 4) {
            chunkBorderManager.setMode(player, 2);
            player.closeInventory();
        } else if (slot == 6) {
            chunkBorderManager.setMode(player, 0);
            player.closeInventory();
        }
    }
}
