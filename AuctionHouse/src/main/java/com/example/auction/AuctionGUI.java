package com.example.auction;

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

public class AuctionGUI implements Listener {

    private static final String TITLE = "§6Аукционы";

    private final AuctionPlugin plugin;
    private final DatabaseManager db;

    public AuctionGUI(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMainMenu(Player player) {
        List<Auction> auctions = db.getActiveAuctions();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillEmptyWithGlass(inv);

        int slot = 0;
        for (Auction auction : auctions) {
            if (slot >= 45) break;
            ItemStack icon = auction.getItem().clone();
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§f#" + auction.getId() + " " + meta.getDisplayName());
            List<String> lore = meta.getLore() != null ? meta.getLore() : new java.util.ArrayList<>();
            lore.add("§7Продавец: §f" + Bukkit.getOfflinePlayer(auction.getSeller()).getName());
            lore.add("§7Цена: §f" + plugin.getEconomyManager().format(auction.getCurrentPrice()));
            if (auction.getCurrentBidder() != null)
                lore.add("§7Лидер: §f" + Bukkit.getOfflinePlayer(auction.getCurrentBidder()).getName());
            long remaining = Math.max(0, (auction.getEndTime() - System.currentTimeMillis()) / 1000);
            lore.add("§7Осталось: §f" + (remaining / 60) + " мин " + (remaining % 60) + " сек");
            lore.add("§eНажмите, чтобы сделать ставку");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§eИнформация");
        infoMeta.setLore(List.of(
                "§7Для ставки используйте:",
                "§f/auc bid <id> <сумма>",
                "§7или через GUI (скоро)"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        player.openInventory(inv);
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
        // Пока только информационное окно – ставки через команды
        player.sendMessage("§eИспользуйте /auc bid <id> <сумма> для ставки.");
    }
}
