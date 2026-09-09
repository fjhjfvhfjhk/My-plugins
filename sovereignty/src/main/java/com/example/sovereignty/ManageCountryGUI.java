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

public class ManageCountryGUI implements Listener {

    private static final String TITLE = "§6Управление страной";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;

    public ManageCountryGUI(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        this.countryManager = plugin.getCountryManager();
    }

    public void open(Player player) {
        String country = countryManager.getCountryName(player.getUniqueId());
        if (country == null) {
            player.sendMessage("§cУ вас нет страны.");
            return;
        }

        if (!countryManager.isLeader(player.getUniqueId(), country)) {
            player.sendMessage("§cТолько лидер страны может управлять ей.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillEmptyWithGlass(inv);

        inv.setItem(10, createButton(Material.REDSTONE_BLOCK, "§cУдалить страну",
                List.of("§7Удалить страну навсегда", "§7Все чанки будут освобождены", "§cЭто действие необратимо!")));

        inv.setItem(13, createButton(Material.NAME_TAG, "§aПереименовать страну",
                List.of("§7Введите новое название в чат", "§7/country rename <новое>")));

        inv.setItem(16, createButton(Material.BARRIER, "§cНазад",
                List.of("§7Вернуться в главное меню")));

        player.openInventory(inv);
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
        if (slot == 10) {
            player.closeInventory();
            plugin.getConfirmDeleteGUI().open(player);
        } else if (slot == 13) {
            player.closeInventory();
            player.sendMessage("§eВведите новое название страны в чат:");
            player.sendMessage("§eИспользуйте команду: §f/country rename <новое>");
        } else if (slot == 16) {
            player.closeInventory();
            new CountryGUI(plugin, countryManager, plugin.getEnergyManager(), plugin.getEconomyManager())
                    .openManageMenu(player);
        }
    }
}
