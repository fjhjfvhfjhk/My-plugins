package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CrashGUI implements Listener {

    public static final String TITLE = "§6🚀 Crash";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();

    private static final int SLOT_INFO = 4;
    private static final int SLOT_MULT = 22;
    private static final int SLOT_CASHOUT = 31;
    private static final int SLOT_CLOSE = 49;

    private final RollPlugin plugin;
    private final CrashManager crashManager;

    public CrashGUI(RollPlugin plugin, CrashManager cm) {
        this.plugin = plugin;
        this.crashManager = cm;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        updateInventory(inv, player);
        player.openInventory(inv);
        openInventories.put(player, inv);
    }

    private void updateInventory(Inventory inv, Player player) {
        inv.clear();
        fillGlass(inv);

        CrashManager.Game game = crashManager.getGame(player.getUniqueId());

        // === Инфо ===
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e🚀 Crash");
        List<String> lore = new ArrayList<>();
        if (game != null) {
            lore.add("§7Ставка: §f" + plugin.getEconomyManager().format(game.bet));
            if (game.finished) {
                if (game.won) lore.add("§a✔ Забрали: §f" + plugin.getEconomyManager().format(game.payout));
                else lore.add("§c💥 CRASH на §f" + String.format("%.2fx", game.crashPoint));
            } else {
                lore.add("§7Множитель растёт...");
                lore.add("§eУспейте забрать!");
            }
        } else {
            lore.add("§7Игра не активна");
            lore.add("§7Использование: §f/roll crash <ставка>");
        }
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(SLOT_INFO, info);

        // === Множитель ===
        if (game != null) {
            Material mat;
            String multStr;
            if (game.finished) {
                if (game.won) {
                    mat = Material.LIME_CONCRETE;
                    multStr = "§a" + String.format("%.2fx", game.payout / game.bet);
                } else {
                    mat = Material.RED_CONCRETE;
                    multStr = "§c💥 CRASH";
                }
            } else {
                double m = game.currentMultiplier;
                if (m < 1.5) mat = Material.LIME_STAINED_GLASS_PANE;
                else if (m < 3) mat = Material.YELLOW_STAINED_GLASS_PANE;
                else if (m < 10) mat = Material.ORANGE_STAINED_GLASS_PANE;
                else mat = Material.RED_STAINED_GLASS_PANE;
                multStr = "§6" + String.format("%.2fx", m);
            }

            ItemStack mult = new ItemStack(mat);
            ItemMeta mm = mult.getItemMeta();
            mm.setDisplayName(multStr);
            if (game.finished && !game.won) {
                mm.setLore(List.of("§7Ракета взорвалась на этой отметке"));
            } else if (!game.finished) {
                double payout = game.bet * game.currentMultiplier;
                mm.setLore(List.of(
                        "§7Текущий выигрыш: §f" + plugin.getEconomyManager().format(payout)
                ));
            }
            mult.setItemMeta(mm);
            inv.setItem(SLOT_MULT, mult);
        }

        // === Кнопка ЗАБРАТЬ ===
        if (game != null && !game.finished) {
            ItemStack cashout = new ItemStack(Material.LIME_WOOL);
            ItemMeta cm = cashout.getItemMeta();
            cm.setDisplayName("§a💰 ЗАБРАТЬ");
            double payout = game.bet * game.currentMultiplier;
            cm.setLore(List.of(
                    "§7Получите: §f" + plugin.getEconomyManager().format(payout),
                    "§7Множитель: §e" + String.format("%.2fx", game.currentMultiplier),
                    "",
                    "§eКликните, чтобы забрать выигрыш"
            ));
            cashout.setItemMeta(cm);
            inv.setItem(SLOT_CASHOUT, cashout);
        }

        // === Закрыть ===
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta ccm = close.getItemMeta();
        if (game != null && !game.finished) {
            ccm.setDisplayName("§cНельзя выйти — заберите или ждите краха");
        } else {
            ccm.setDisplayName("§cЗакрыть");
        }
        close.setItemMeta(ccm);
        inv.setItem(SLOT_CLOSE, close);
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

    public static void updateAllOpen() {
        RollPlugin plugin = RollPlugin.getInstance();
        if (plugin == null) return;
        CrashGUI gui = plugin.getCrashGUI();
        if (gui == null) return;
        for (Map.Entry<Player, Inventory> e : new HashMap<>(openInventories).entrySet()) {
            Player player = e.getKey();
            Inventory inv = e.getValue();
            if (player.isOnline() && player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory().equals(inv)) {
                gui.updateInventory(inv, player);
                player.updateInventory();
            } else {
                openInventories.remove(player);
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) {
            CrashManager.Game g = crashManager.getGame(player.getUniqueId());
            if (g == null || g.finished) player.closeInventory();
            return;
        }
        if (slot == SLOT_CASHOUT) {
            crashManager.cashout(player);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.getView().getTitle().equals(TITLE) && e.getPlayer() instanceof Player p) {
            openInventories.put(p, e.getInventory());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        openInventories.remove(e.getPlayer());
    }
}
