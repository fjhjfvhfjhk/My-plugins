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
import java.util.UUID;

public class ConfirmDeleteGUI implements Listener {

    private static final String TITLE = "§cПодтверждение удаления";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;

    public ConfirmDeleteGUI(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        this.countryManager = plugin.getCountryManager();
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);
        fillEmptyWithGlass(inv);

        inv.setItem(2, createButton(Material.LIME_WOOL, "§aДа, удалить страну",
                List.of("§cЭто действие необратимо!")));

        inv.setItem(6, createButton(Material.RED_WOOL, "§cНет, отмена",
                List.of("§7Вернуться в меню управления")));

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
        if (slot == 2) {
            player.closeInventory();
            deleteCountry(player);
        } else if (slot == 6) {
            player.closeInventory();
            plugin.getManageCountryGUI().open(player);
        }
    }

    private void deleteCountry(Player player) {
        String countryName = countryManager.getCountryName(player.getUniqueId());
        if (countryName == null) {
            player.sendMessage("§cУ вас нет страны.");
            return;
        }

        if (!countryManager.isLeader(player.getUniqueId(), countryName)) {
            player.sendMessage("§cТолько лидер может удалить страну.");
            return;
        }

        if (countryManager.deleteCountry(countryName)) {
            player.sendMessage("§cВаша страна §e" + countryName + "§c удалена.");
            for (UUID coUuid : countryManager.getCoRulers(countryName)) {
                Player co = Bukkit.getPlayer(coUuid);
                if (co != null && co.isOnline()) {
                    co.sendMessage("§cСтрана §e" + countryName + "§c была удалена лидером.");
                }
            }
        } else {
            player.sendMessage("§cНе удалось удалить страну. Ошибка базы данных.");
        }
    }
}
