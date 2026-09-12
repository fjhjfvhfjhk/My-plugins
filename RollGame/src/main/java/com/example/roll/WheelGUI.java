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

/**
 * GUI Колеса Фортуны в виде круга.
 *
 * Раскладка 5×5 (смещено на (0, 2)) в инвентаре 6×9:
 *        [4]
 *     [3]   [5]
 *   [11]      [15]
 *   [20]      [24]
 *   [29]      [33]
 *     [39]   [41]
 *        [40]
 *
 * Позиция 0 (слот 4) — "верхний" сектор под указателем = текущий.
 * Далее по часовой стрелке: 5, 15, 24, 33, 41, 40, 39, 29, 20, 11, 3.
 */
public class WheelGUI implements Listener {

    private static final String TITLE = "§6🎡 Колесо Фортуны";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();

    /** 12 позиций круга, начиная с верхнего и далее по часовой. */
    private static final int[] WHEEL_SLOTS = {
            4, 5, 15, 24, 33, 41, 40, 39, 29, 20, 11, 3
    };

    private static final int SLOT_INFO = 13;
    private static final int SLOT_REPEAT = 49;
    private static final int SLOT_CLOSE = 53;

    private final RollPlugin plugin;
    private final WheelManager wheelManager;

    public WheelGUI(RollPlugin plugin, WheelManager wheelManager) {
        this.plugin = plugin;
        this.wheelManager = wheelManager;
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
        fillEmptyWithGlass(inv);

        WheelManager.Game game = wheelManager.getGame(player.getUniqueId());

        // === Информация ===
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e🎡 Колесо Фортуны");
        List<String> infoLore = new ArrayList<>();
        if (game != null) {
            infoLore.add("§7Ставка: §f" + plugin.getEconomyManager().format(game.bet));
            if (game.finished) {
                infoLore.add("§7Выпало: §e" + formatMultiplier(game.targetMultiplier));
                double win = game.bet * game.targetMultiplier;
                if (game.targetMultiplier > 0) {
                    infoLore.add("§aВыигрыш: §f" + plugin.getEconomyManager().format(win));
                } else {
                    infoLore.add("§cПроигрыш");
                }
            } else {
                infoLore.add("§6Колесо вращается...");
            }
        } else {
            infoLore.add("§7Игра не активна");
            infoLore.add("§7Использование: §f/roll wheel <ставка>");
        }
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(SLOT_INFO, info);

        // === Указатель сверху ===
        ItemStack pointer = new ItemStack(Material.LIME_DYE);
        ItemMeta pm = pointer.getItemMeta();
        pm.setDisplayName("§a▲");
        pm.setLore(List.of("§7Стрелка указывает на текущий сектор"));
        pointer.setItemMeta(pm);
        // Оставим слот 4 для текущего сектора, а стрелку поставим над ним (в слот 2 или 6 — углы)
        inv.setItem(2, pointer);

        // === Круг ===
        if (game != null && !game.sequence.isEmpty()) {
            int size = game.sequence.size();
            for (int i = 0; i < WHEEL_SLOTS.length; i++) {
                // Позиция 0 (верхний центр) = index 4 в window.
                int seqIdx = (game.currentShift + 4 + i) % size;
                if (seqIdx < 0) seqIdx += size;
                int sectorIdx = game.sequence.get(seqIdx);
                double mult = WheelManager.SECTORS[sectorIdx];

                boolean isCurrent = (i == 0);
                boolean isWinner = isCurrent && game.finished;
                inv.setItem(WHEEL_SLOTS[i], createSectorItem(mult, isCurrent, isWinner));
            }
        } else {
            // Игра не активна — показываем превью секторов.
            for (int i = 0; i < WHEEL_SLOTS.length; i++) {
                double mult = WheelManager.SECTORS[i % WheelManager.SECTORS.length];
                inv.setItem(WHEEL_SLOTS[i], createSectorItem(mult, false, false));
            }
        }

        // === Справка по секторам ===
        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta hm = help.getItemMeta();
        hm.setDisplayName("§6Секторы множителей");
        hm.setLore(List.of(
                "§7Всего секторов: §f12",
                "§c0x §7— 5 секторов (проигрыш)",
                "§e0.5x §7— 2 сектора",
                "§e1x §7— 3 сектора (возврат)",
                "§a2.5x §7— 1 сектор",
                "§d5x §7— 1 сектор (джекпот)"
        ));
        help.setItemMeta(hm);
        inv.setItem(0, help);

        // === Кнопка "Повторить ставку" ===
        if (game != null && game.finished) {
            ItemStack repeat = new ItemStack(Material.GOLD_INGOT);
            ItemMeta rm = repeat.getItemMeta();
            rm.setDisplayName("§a🔁 Повторить ставку");
            rm.setLore(List.of(
                    "§7Снова поставить: §f" + plugin.getEconomyManager().format(game.bet),
                    "§eНажмите, чтобы сыграть ещё раз"
            ));
            repeat.setItemMeta(rm);
            inv.setItem(SLOT_REPEAT, repeat);
        }

        // === Кнопка "Закрыть" ===
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(SLOT_CLOSE, close);
    }

    /**
     * Создаёт иконку сектора. Цвет зависит от множителя,
     * угловые (текущий / победитель) подсвечиваются ярче.
     */
    private ItemStack createSectorItem(double mult, boolean isCurrent, boolean isWinner) {
        Material mat;
        if (mult <= 0) mat = Material.RED_STAINED_GLASS_PANE;
        else if (mult < 1) mat = Material.ORANGE_STAINED_GLASS_PANE;
        else if (mult < 2) mat = Material.YELLOW_STAINED_GLASS_PANE;
        else if (mult < 5) mat = Material.LIME_STAINED_GLASS_PANE;
        else mat = Material.MAGENTA_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String prefix;
        if (isWinner) prefix = "§d§l🏆 ";
        else if (isCurrent) prefix = "§a▶ ";
        else prefix = "";
        meta.setDisplayName(prefix + "§e" + formatMultiplier(mult));
        if (isCurrent) {
            meta.setLore(List.of("§7Текущий сектор под указателем"));
        } else if (isWinner) {
            meta.setLore(List.of("§a🎉 ВЫПАЛ!"));
        }
        item.setItemMeta(meta);
        return item;
    }

    private String formatMultiplier(double m) {
        if (m == Math.floor(m)) return (int) m + "x";
        return String.format("%.1fx", m);
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

    public static void updateAllOpen() {
        RollPlugin plugin = RollPlugin.getInstance();
        if (plugin == null) return;
        WheelGUI gui = plugin.getWheelGUI();
        if (gui == null) return;
        for (Map.Entry<Player, Inventory> entry : new HashMap<>(openInventories).entrySet()) {
            Player player = entry.getKey();
            Inventory inv = entry.getValue();
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
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }
        if (slot == SLOT_REPEAT) {
            WheelManager.Game game = wheelManager.getGame(player.getUniqueId());
            if (game != null && game.finished) {
                double bet = game.bet;
                wheelManager.clearGame(player.getUniqueId());
                if (wheelManager.start(player, bet)) {
                    updateInventory(event.getInventory(), player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            openInventories.put((Player) event.getPlayer(), event.getInventory());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        openInventories.remove(player);
        WheelManager.Game game = wheelManager.getGame(player.getUniqueId());
        if (game != null && game.finished) {
            wheelManager.clearGame(player.getUniqueId());
        }
    }
}
