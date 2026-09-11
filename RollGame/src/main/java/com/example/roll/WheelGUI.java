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

public class WheelGUI implements Listener {

    private static final String TITLE = "§6🎡 Колесо Фортуны";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();
    private static final int WINDOW_START = 18;
    private static final int WINDOW_CENTER = 22;
    private static final int SLOT_REPEAT = 42;
    private static final int SLOT_CLOSE = 44;

    private final RollPlugin plugin;
    private final WheelManager wheelManager;

    public WheelGUI(RollPlugin plugin, WheelManager wheelManager) {
        this.plugin = plugin;
        this.wheelManager = wheelManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE);
        updateInventory(inv, player);
        player.openInventory(inv);
        openInventories.put(player, inv);
    }

    private void updateInventory(Inventory inv, Player player) {
        inv.clear();
        fillEmptyWithGlass(inv);

        WheelManager.Game game = wheelManager.getGame(player.getUniqueId());

        // Информация
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
                infoLore.add("§7Вращение...");
            }
        } else {
            infoLore.add("§7Игра не активна");
            infoLore.add("§7Использование: §f/roll wheel <ставка>");
        }
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Указатель
        ItemStack pointer = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta pm = pointer.getItemMeta();
        pm.setDisplayName("§a▲");
        pointer.setItemMeta(pm);
        inv.setItem(13, pointer);

        // Окно колеса (9 слотов 18..26)
        if (game != null) {
            int size = game.sequence.size();
            for (int i = 0; i < 9; i++) {
                int idx = (game.currentShift + i) % size;
                int sector = game.sequence.get(idx);
                double mult = WheelManager.SECTORS[sector];
                boolean isCenter = (WINDOW_START + i) == WINDOW_CENTER;
                inv.setItem(WINDOW_START + i, createSectorItem(mult, isCenter && game.finished));
            }
        } else {
            for (int i = 0; i < 9; i++) {
                int sector = i % WheelManager.SECTORS.length;
                inv.setItem(WINDOW_START + i, createSectorItem(WheelManager.SECTORS[sector], false));
            }
        }

        // Справка
        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta hm = help.getItemMeta();
        hm.setDisplayName("§6Секторы множителей");
        hm.setLore(List.of(
                "§7Всего секторов: §f12",
                "§c0x §7— 4 сектора (проигрыш)",
                "§e0.5x, 1x §7— малый возврат",
                "§62x, 3x §7— средний выигрыш",
                "§d5x §7— джекпот (1 сектор)"
        ));
        help.setItemMeta(hm);
        inv.setItem(0, help);

        // Кнопка "Повторить ставку"
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

        // Кнопка "Закрыть"
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(SLOT_CLOSE, close);
    }

    private ItemStack createSectorItem(double mult, boolean highlight) {
        Material mat;
        if (mult <= 0) mat = Material.RED_STAINED_GLASS_PANE;
        else if (mult < 1) mat = Material.ORANGE_STAINED_GLASS_PANE;
        else if (mult < 2) mat = Material.YELLOW_STAINED_GLASS_PANE;
        else if (mult < 5) mat = Material.LIME_STAINED_GLASS_PANE;
        else mat = Material.PURPLE_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(highlight ? Material.NETHER_STAR : mat);
        ItemMeta meta = item.getItemMeta();
        String prefix = highlight ? "§d§l🏆 " : "";
        meta.setDisplayName(prefix + "§e" + formatMultiplier(mult));
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
                wheelManager.start(player, bet);
                updateInventory(event.getInventory(), player);
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
