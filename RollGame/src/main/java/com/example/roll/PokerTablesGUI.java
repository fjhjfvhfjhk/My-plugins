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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI со списком покерных столов. Клик по столу → присоединиться.
 */
public class PokerTablesGUI implements Listener {

    public static final String TITLE = "§6🃏 Столы покера";

    private static final int SLOT_CLOSE = 53;
    private static final int SLOT_CREATE_INFO = 49;

    private final RollPlugin plugin;
    private final Map<Integer, Integer> slotToTableId = new HashMap<>();

    public PokerTablesGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        slotToTableId.clear();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillGlass(inv);

        int slot = 0;
        for (PokerTable table : plugin.getPokerManager().getAllTables()) {
            if (slot >= 45) break;
            ItemStack icon = createTableIcon(table);
            inv.setItem(slot, icon);
            slotToTableId.put(slot, table.id);
            slot++;
        }

        if (slot == 0) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName("§cНет активных столов");
            em.setLore(List.of(
                    "§7Создайте стол командой:",
                    "§f/roll poker create <бай-ин>",
                    "",
                    "§7Например: §f/roll poker create 5000"
            ));
            empty.setItemMeta(em);
            inv.setItem(22, empty);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e💡 Как играть?");
        im.setLore(List.of(
                "§7• Нажмите на стол, чтобы присоединиться",
                "§7• Играть можно только в статусе «Ожидание»",
                "§7• После начала — только наблюдение",
                "",
                "§7Создать свой стол:",
                "§f/roll poker create <бай-ин>"
        ));
        info.setItemMeta(im);
        inv.setItem(SLOT_CREATE_INFO, info);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(SLOT_CLOSE, close);

        player.openInventory(inv);
    }

    private ItemStack createTableIcon(PokerTable table) {
        Material mat;
        String status;
        switch (table.stage) {
            case WAITING -> { status = "§aОжидание игроков"; mat = Material.LIME_WOOL; }
            case PREFLOP, FLOP, TURN, RIVER -> { status = "§cИгра идёт"; mat = Material.RED_WOOL; }
            case SHOWDOWN -> { status = "§eВскрытие"; mat = Material.YELLOW_WOOL; }
            default -> { status = "§7Завершено"; mat = Material.GRAY_WOOL; }
        }
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§e🃏 Стол #" + table.id);
        List<String> lore = new ArrayList<>();
        lore.add("§7Бай-ин: §f" + plugin.getEconomyManager().format(table.buyIn));
        lore.add("§7Игроков: §f" + table.players.size() + " §7/ 6");
        lore.add("§7Блайнды: §f" + table.smallBlind + "/" + table.bigBlind);
        lore.add("§7Стадия: " + status);
        lore.add("");
        if (!table.players.isEmpty()) {
            lore.add("§7За столом:");
            int i = 0;
            for (PokerTable.Player p : table.players.values()) {
                if (i >= 6) break;
                lore.add("  §f• " + p.name + " §7(" + plugin.getEconomyManager().format(p.chips) + ")");
                i++;
            }
            lore.add("");
        }
        if (table.stage == PokerTable.Stage.WAITING) {
            lore.add("§e▶ Нажмите, чтобы присоединиться");
        } else {
            lore.add("§cИгра уже началась");
        }
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
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }

        Integer tableId = slotToTableId.get(slot);
        if (tableId != null) {
            PokerTable table = plugin.getPokerManager().getTable(tableId);
            if (table == null) { player.sendMessage("§cСтол больше не существует."); return; }
            if (table.stage != PokerTable.Stage.WAITING) {
                player.sendMessage("§cИгра уже началась. Вы можете только наблюдать (пока не реализовано).");
                return;
            }
            player.closeInventory();
            plugin.getPokerManager().joinTable(player, tableId);
        }
    }
}
