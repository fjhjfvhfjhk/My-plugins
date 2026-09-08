package com.example.bounty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI наёмников — список целей с наградами.
 */
public class BountyGUI implements Listener {

    private static final String TITLE = "§4Наёмники";

    private final BountyPlugin plugin;
    private final BountyManager bountyManager;

    public BountyGUI(BountyPlugin plugin, BountyManager bountyManager) {
        this.plugin = plugin;
        this.bountyManager = bountyManager;
    }

    public void open(Player player) {
        Map<UUID, BountyManager.BountyEntry> all = bountyManager.getAllBounties();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        List<Map.Entry<UUID, BountyManager.BountyEntry>> sorted = new ArrayList<>(all.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue().amount, a.getValue().amount));

        int max = Math.min(sorted.size(), 45);
        for (int i = 0; i < max; i++) {
            Map.Entry<UUID, BountyManager.BountyEntry> entry = sorted.get(i);
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString().substring(0, 8);

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getKey()));
            meta.setDisplayName("§c" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Награда: §f" + plugin.getEconomyManager().format(entry.getValue().amount));
            lore.add("§eУбейте цель, чтобы получить награду");
            meta.setLore(lore);
            skull.setItemMeta(meta);
            inv.setItem(i, skull);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
    }
}
