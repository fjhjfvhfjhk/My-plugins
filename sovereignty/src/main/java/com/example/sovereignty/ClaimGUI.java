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

public class ClaimGUI implements Listener {

    private static final String TITLE = "§6Управление территорией";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;

    public ClaimGUI(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    public void open(Player player) {
        String country = countryManager.getCountryName(player.getUniqueId());
        if (country == null) {
            player.sendMessage("§cСоздайте страну.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillEmptyWithGlass(inv);

        int claims = countryManager.getClaimCount(country);
        int maxClaims = countryManager.getMaxClaims(player.getUniqueId());
        boolean autoClaim = plugin.isAutoClaimEnabled(player.getUniqueId());

        inv.setItem(11, createButton(Material.GRASS_BLOCK, "§aЗахватить чанк",
                List.of("§7Захватить текущий чанк за 2 энергии", "§7/country claim")));
        inv.setItem(13, createButton(Material.RED_WOOL, "§cОтпустить чанк",
                List.of("§7Освободить текущий чанк", "§7/country unclaim")));
        inv.setItem(15, createButton(
                autoClaim ? Material.LIME_WOOL : Material.GRAY_WOOL,
                (autoClaim ? "§aАвтозахват: ВКЛ" : "§7Автозахват: ВЫКЛ"),
                List.of("§7Автоматический захват при беге", "§7/country autoclaim")));

        inv.setItem(22, createButton(Material.OAK_SIGN, "§eИнформация",
                List.of("§7Территория: §f" + claims + " / " + maxClaims + " чанков")));

        inv.setItem(26, createButton(Material.BARRIER, "§cНазад в главное меню", List.of()));

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
        if (slot == 11) {
            player.closeInventory();
            player.performCommand("country claim");
        } else if (slot == 13) {
            player.closeInventory();
            player.performCommand("country unclaim");
        } else if (slot == 15) {
            player.closeInventory();
            player.performCommand("country autoclaim");
            open(player); // обновить меню
        } else if (slot == 26) {
            player.closeInventory();
            player.performCommand("country");
        }
    }
}
