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

import java.util.List;

public class UpgradeHistoryGUI implements Listener {

    public static final String TITLE = "§6📜 История апгрейдов";

    private final RollPlugin plugin;

    public UpgradeHistoryGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillGlass(inv);

        List<RollData.UpgradeRoll> history = plugin.getRollData().getUpgradeHistory(player.getUniqueId());

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6📜 Ваша история");
        double totalProfit = plugin.getRollData().getUpgradeProfit(player.getUniqueId());
        int totalRolls = plugin.getRollData().getUpgradeRolls(player.getUniqueId());
        String totalSign = totalProfit >= 0 ? "+" : "";
        im.setLore(List.of(
                "§7Всего роллов: §f" + totalRolls,
                "§7Профит: " + (totalProfit >= 0 ? "§a" : "§c") + totalSign +
                        plugin.getEconomyManager().format(totalProfit),
                "§7Показаны последние §f" + history.size() + "§7."
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        if (history.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName("§cИстория пуста");
            em.setLore(List.of("§7Сыграйте хотя бы один апгрейд."));
            empty.setItemMeta(em);
            inv.setItem(13, empty);
        } else {
            int slot = 10;
            for (RollData.UpgradeRoll r : history) {
                if (slot == 17) slot = 19;
                if (slot > 25) break;

                Material mat = r.win ? Material.EMERALD : Material.REDSTONE;
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(r.win ? "§a✔ Победа" : "§c✘ Провал");
                double delta = r.payout - r.bet;
                String rowSign = delta >= 0 ? "+" : "";
                meta.setLore(List.of(
                        "§7Шанс: §f" + String.format("%.0f%%", r.chance * 100),
                        "§7Ставка: §f" + plugin.getEconomyManager().format(r.bet),
                        "§7Выплата: §f" + plugin.getEconomyManager().format(r.payout),
                        "§7Итог: " + (delta >= 0 ? "§a" : "§c") + rowSign +
                                plugin.getEconomyManager().format(delta),
                        "§7Когда: §f" + timeAgo(r.timestamp)
                ));
                item.setItemMeta(meta);
                inv.setItem(slot, item);
                slot++;
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

    private static String timeAgo(long ts) {
        long s = (System.currentTimeMillis() - ts) / 1000;
        if (s < 60) return "только что";
        long m = s / 60;
        if (m < 60) return m + " мин назад";
        long h = m / 60;
        if (h < 24) return h + " ч назад";
        return (h / 24) + " дн назад";
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
