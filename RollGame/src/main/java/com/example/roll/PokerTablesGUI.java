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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI со списком покерных столов.
 * Переработан: сортировка по статусу, аватарки игроков, кнопка "Обновить",
 * подробное описание каждого стола.
 */
public class PokerTablesGUI implements Listener {

    public static final String TITLE = "§6🃏 Столы покера";

    private static final int SLOT_REFRESH = 47;
    private static final int SLOT_CREATE_INFO = 49;
    private static final int SLOT_CLOSE = 51;

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

        // Сортируем столы: WAITING сверху, затем по id
        List<PokerTable> tables = new ArrayList<>(plugin.getPokerManager().getAllTables());
        tables.sort(Comparator
                .comparingInt((PokerTable t) -> t.stage == PokerTable.Stage.WAITING ? 0 : 1)
                .thenComparingInt(t -> t.id));

        int slot = 0;
        for (PokerTable table : tables) {
            if (slot >= 45) break;
            ItemStack icon = createTableIcon(table);
            inv.setItem(slot, icon);
            slotToTableId.put(slot, table.id);
            slot++;
        }

        if (tables.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName("§cНет активных столов");
            em.setLore(List.of(
                    "§7Создайте свой стол командой:",
                    "§f/roll poker create <бай-ин>",
                    "",
                    "§7Например: §f/roll poker create 5000",
                    "",
                    "§7О создании стола будет объявлено в чат,"
            ));
            empty.setItemMeta(em);
            inv.setItem(22, empty);
        }

        // Кнопка "Обновить"
        ItemStack refresh = new ItemStack(Material.NETHER_STAR);
        ItemMeta rm = refresh.getItemMeta();
        rm.setDisplayName("§e🔄 Обновить список");
        rm.setLore(List.of(
                "§7Перечитать список столов.",
                "",
                "§e▶ Нажмите, чтобы обновить"
        ));
        refresh.setItemMeta(rm);
        inv.setItem(SLOT_REFRESH, refresh);

        // Информация о создании
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e💡 Создать свой стол");
        im.setLore(List.of(
                "§7Создать можно командой:",
                "§f/roll poker create <бай-ин>",
                "",
                "§7Бай-ин — сумма, которую каждый игрок",
                "§7должен внести за стол. Блайнды: §f1%§7 и §f2%§7 бай-ина.",
                "",
                "§7Минимальный бай-ин: §f100",
                "",
                "§7Когда создадите — сервер узнает об этом"
        ));
        info.setItemMeta(im);
        inv.setItem(SLOT_CREATE_INFO, info);

        // Кнопка "Закрыть"
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
        meta.setDisplayName("§e🃏 Стол #" + table.id + " §7— " + status);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Бай-ин: §f" + plugin.getEconomyManager().format(table.buyIn));
        lore.add("§7Игроков: §f" + table.players.size() + "§7/6");
        lore.add("§7Блайнды: §f" + table.smallBlind + "/" + table.bigBlind);
        if (table.handNumber > 0) {
            lore.add("§7Раздача: §f#" + table.handNumber);
        }
        lore.add("");
        if (!table.players.isEmpty()) {
            lore.add("§7За столом:");
            int i = 0;
            for (PokerTable.Player p : table.players.values()) {
                if (i >= 6) break;
                String mark = p.uuid.equals(table.currentTurn) && !table.dealing ? "§e➤ " : "  §f• ";
                lore.add(mark + p.name + " §7(" + plugin.getEconomyManager().format(p.chips) + ")");
                i++;
            }
            lore.add("");
        }
        if (table.stage == PokerTable.Stage.WAITING) {
            if (table.players.size() >= 2) {
                lore.add("§a▶ Готов к запуску — /roll poker start");
            } else {
                lore.add("§7Ожидает игроков (§eнужно минимум 2§7)");
            }
            lore.add("§e▶ Нажмите, чтобы присоединиться");
        } else {
            lore.add("§cИгра уже идёт — присоединение закрыто");
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
        if (slot == SLOT_REFRESH) { open(player); return; }
        if (slot == SLOT_CREATE_INFO) {
            player.closeInventory();
            player.sendMessage("§eВведите: §f/roll poker create <бай-ин>");
            return;
        }
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }

        Integer tableId = slotToTableId.get(slot);
        if (tableId != null) {
            PokerTable table = plugin.getPokerManager().getTable(tableId);
            if (table == null) { player.sendMessage("§cСтол больше не существует."); open(player); return; }
            if (table.stage != PokerTable.Stage.WAITING) {
                player.sendMessage("§cИгра уже началась. Подождите следующей раздачи.");
                return;
            }
            player.closeInventory();
            plugin.getPokerManager().joinTable(player, tableId);
        }
    }
}
