package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Топ игроков Upgrader по профиту.
 */
public class UpgradeTopGUI implements Listener {

    public static final String TITLE = "§6🏆 Топ апгрейдеров";

    private final RollPlugin plugin;

    public UpgradeTopGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillGlass(inv);

        ItemStack info = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6🏆 Топ игроков");
        im.setLore(List.of("§7Рейтинг по профиту в апгрейдере."));
        info.setItemMeta(im);
        inv.setItem(4, info);

        List<Map.Entry<UUID, Double>> top = plugin.getRollData().getUpgradeTop(10);

        if (top.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName("§cТоп пуст");
            em.setLore(List.of("§7Никто ещё не играл в апгрейдер."));
            empty.setItemMeta(em);
            inv.setItem(13, empty);
        } else {
            int slot = 10;
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : top) {
                if (slot == 17) slot = 19;
                if (slot > 25) break;

                UUID uuid = entry.getKey();
                double profit = entry.getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = uuid.toString().substring(0, 8);

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta hm = (SkullMeta) head.getItemMeta();
                hm.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                hm.setDisplayName("§e#" + rank + " §f" + name);
                double pf = plugin.getRollData().getUpgradeProfit(uuid);
                int rolls = plugin.getRollData().getUpgradeRolls(uuid);
                hm.setLore(List.of(
                        "§7Профит: " + (pf >= 0 ? "§a+" : "§c") + plugin.getEconomyManager().format(pf),
                        "§7Роллов: §f" + rolls
                ));
                head.setItemMeta(hm);
                inv.setItem(slot, head);
                slot++;
                rank++;
            }
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cНазад");
        close.setItemMeta(cm);
        inv.setItem(26, close);

        player.openInventory(inv);
    }

    private void fillGlass(Inventory inv) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = g.getItemMeta();
        gm.setDisplayName(" ");
        g.setItemMeta(gm);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, g);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);
        if (e.getRawSlot() == 26) {
            p.closeInventory();
            plugin.getUpgradeHubGUI().open(p);
        }
    }
}
