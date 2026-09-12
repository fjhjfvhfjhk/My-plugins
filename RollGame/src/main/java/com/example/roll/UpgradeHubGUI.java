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

/**
 * Hub апгрейдера — 4 кнопки: Chance, Item, History, Top.
 */
public class UpgradeHubGUI implements Listener {

    public static final String TITLE = "§6🔮 Апгрейдер";

    private final RollPlugin plugin;

    public UpgradeHubGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillGlass(inv);

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6🔮 Апгрейдер");
        im.setLore(List.of(
                "§7Два режима и статистика.",
                "§7Выбери, чтобы начать."
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        inv.setItem(10, button(Material.GOLD_INGOT, "§a🎯 Режим Шанс",
                List.of("§7Выбери шанс победы (5%–90%).",
                        "§7Множитель = 0.95 / шанс.",
                        "§7Например: 25% → x3.8.",
                        "",
                        "§7ПКМ — авто-ролл ×10.",
                        "",
                        "§e▶ Нажми, чтобы играть")));

        inv.setItem(12, button(Material.DIAMOND, "§b💎 Режим Предмет",
                List.of("§7Положи предмет в центр.",
                        "§7Победа — предмет выше тиром",
                        "§7или деньги по стоимости.",
                        "",
                        "§7Множитель ×1/×2/×3 в самом GUI.",
                        "§7Энчанты переносятся.",
                        "",
                        "§e▶ Нажми, чтобы играть")));

        inv.setItem(14, button(Material.BOOK, "§e📜 История",
                List.of("§7Последние 10 роллов.",
                        "§7Ставка, шанс, профит.",
                        "",
                        "§e▶ Нажми, чтобы открыть")));

        inv.setItem(16, button(Material.GOLD_BLOCK, "§e🏆 Топ игроков",
                List.of("§7Рейтинг по профиту.",
                        "",
                        "§e▶ Нажми, чтобы открыть")));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(22, close);

        player.openInventory(inv);
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
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

        int slot = e.getRawSlot();
        if (slot == 10) {
            p.closeInventory();
            plugin.getBetInputGUI().open(p, "upgrade_chance", "Шанс", 100);
        } else if (slot == 12) {
            p.closeInventory();
            plugin.getUpgradeItemGUI().open(p);
        } else if (slot == 14) {
            p.closeInventory();
            plugin.getUpgradeHistoryGUI().open(p);
        } else if (slot == 16) {
            p.closeInventory();
            plugin.getUpgradeTopGUI().open(p);
        } else if (slot == 22) {
            p.closeInventory();
        }
    }
}
