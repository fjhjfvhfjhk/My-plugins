package com.example.roll;

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

/**
 * Маленький GUI: 2 карты игрока + 5 общих.
 * Легко читаемо: красные масти = красный текст, чёрные = белый.
 */
public class PokerCardsGUI implements Listener {

    public static final String TITLE = "§6👁 Ваши карты";

    private final RollPlugin plugin;

    public PokerCardsGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, PokerTable table) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);
        PokerTable.Player p = table.getPlayer(player.getUniqueId());
        if (p == null) { player.closeInventory(); return; }

        // Мои карты
        if (p.hole.size() >= 2) {
            inv.setItem(1, p.hole.get(0).toItem());
            inv.setItem(2, p.hole.get(1).toItem());
        }

        // Общие карты
        int[] slots = {4, 5, 6, 7, 8};
        for (int i = 0; i < 5; i++) {
            if (i < table.community.size()) {
                inv.setItem(slots[i], table.community.get(i).toItem());
            } else {
                ItemStack hidden = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta hm = hidden.getItemMeta();
                hm.setDisplayName("§7—");
                hidden.setItemMeta(hm);
                inv.setItem(slots[i], hidden);
            }
        }

        // Информация о текущей руке
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e🃏 Ваша рука");
        List<String> lore = new ArrayList<>();
        if (p.hole.size() == 2) {
            lore.add("§7Ваши карты: " + p.hole.get(0).display() + " §7| " + p.hole.get(1).display());
            List<PokerHand.Card> seven = new ArrayList<>(p.hole);
            seven.addAll(table.community);
            if (seven.size() >= 5) {
                int[] eval = PokerHand.evaluateBest(seven);
                lore.add("");
                lore.add("§7Лучшая комбинация:");
                lore.add("§a§l" + PokerHand.handName(eval));
            } else {
                lore.add("");
                lore.add("§7Ждём общих карт...");
            }
            lore.add("");
            lore.add("§7Стадия: §f" + stageName(table.stage));
        }
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(3, info);

        // Назад
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§c↩ Вернуться к столу");
        back.setItemMeta(bm);
        inv.setItem(0, back);

        player.openInventory(inv);
    }

    private String stageName(PokerTable.Stage s) {
        return switch (s) {
            case PREFLOP -> "Префлоп";
            case FLOP -> "Флоп";
            case TURN -> "Тёрн";
            case RIVER -> "Ривер";
            default -> "Ожидание";
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        if (event.getRawSlot() == 0) {
            PokerTable table = plugin.getPokerManager().getPlayerTable(player.getUniqueId());
            if (table != null) {
                player.closeInventory();
                PokerGUI.open(player, table);
            }
        }
    }
}
