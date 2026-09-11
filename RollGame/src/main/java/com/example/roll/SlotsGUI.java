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

public class SlotsGUI implements Listener {

    private static final String TITLE = "§6🎰 Слоты";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();

    private static final int SLOT_BAR1 = 20;
    private static final int SLOT_BAR2 = 22;
    private static final int SLOT_BAR3 = 24;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_REPEAT = 42;
    private static final int SLOT_CLOSE = 40;

    private final RollPlugin plugin;
    private final SlotsManager slotsManager;

    public SlotsGUI(RollPlugin plugin, SlotsManager slotsManager) {
        this.plugin = plugin;
        this.slotsManager = slotsManager;
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

        SlotsManager.Game game = slotsManager.getGame(player.getUniqueId());

        // Информация
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e🎰 Слоты");
        List<String> infoLore = new ArrayList<>();
        if (game != null) {
            infoLore.add("§7Ставка: §f" + plugin.getEconomyManager().format(game.bet));
            if (game.finished) {
                double mult = computeMultiplierForDisplay(game);
                if (mult > 0) {
                    infoLore.add("§aМножитель: §e" + formatMultiplier(mult));
                    infoLore.add("§aВыигрыш: §f" + plugin.getEconomyManager().format(game.bet * mult));
                } else {
                    infoLore.add("§cПроигрыш");
                }
            } else {
                infoLore.add("§7Вращение...");
            }
        } else {
            infoLore.add("§7Игра не активна");
            infoLore.add("§7Использование: §f/roll slots <ставка>");
        }
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(SLOT_INFO, info);

        if (game != null) {
            inv.setItem(SLOT_BAR1, createSymbol(game.bar1));
            inv.setItem(SLOT_BAR2, createSymbol(game.bar2));
            inv.setItem(SLOT_BAR3, createSymbol(game.bar3));
        } else {
            inv.setItem(SLOT_BAR1, createSymbol(Material.DIAMOND));
            inv.setItem(SLOT_BAR2, createSymbol(Material.EMERALD));
            inv.setItem(SLOT_BAR3, createSymbol(Material.GOLD_INGOT));
        }

        // Таблица выплат
        ItemStack paytable = new ItemStack(Material.BOOK);
        ItemMeta ptMeta = paytable.getItemMeta();
        ptMeta.setDisplayName("§6Таблица выплат");
        ptMeta.setLore(List.of(
                "§d⭐ 3× = §fx50 §7| 2× = §fx5",
                "§b💎 3× = §fx10 §7| 2× = §fx2.5",
                "§a💚 3× = §fx8 §7| 2× = §fx2",
                "§6🥇 3× = §fx5 §7| 2× = §fx1.5",
                "§7⚙ 3× = §fx4 §7| 2× = §fx1.5",
                "§c🔴 3× = §fx3 §7| 2× = §fx1.5",
                "§9🔵 3× = §fx3 §7| 2× = §fx1.5"
        ));
        paytable.setItemMeta(ptMeta);
        inv.setItem(0, paytable);

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

        // Закрыть
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        close.setItemMeta(closeMeta);
        inv.setItem(SLOT_CLOSE, close);
    }

    private double computeMultiplierForDisplay(SlotsManager.Game game) {
        Material a = game.result1, b = game.result2, c = game.result3;
        if (a == null || b == null || c == null) return 0;
        if (a == b && b == c) {
            return switch (a) {
                case NETHER_STAR -> 50.0;
                case DIAMOND -> 10.0;
                case EMERALD -> 8.0;
                case GOLD_INGOT -> 5.0;
                case IRON_INGOT -> 4.0;
                default -> 3.0;
            };
        }
        if (a == b || b == c || a == c) {
            Material pair = a == b ? a : (b == c ? b : a);
            return switch (pair) {
                case NETHER_STAR -> 5.0;
                case DIAMOND -> 2.5;
                case EMERALD -> 2.0;
                default -> 1.5;
            };
        }
        return 0.0;
    }

    private String formatMultiplier(double m) {
        if (m == Math.floor(m)) return (int) m + "x";
        return String.format("%.1fx", m);
    }

    private ItemStack createSymbol(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + mat.name().toLowerCase().replace('_', ' '));
        item.setItemMeta(meta);
        return item;
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
        SlotsGUI gui = plugin.getSlotsGUI();
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
            SlotsManager.Game game = slotsManager.getGame(player.getUniqueId());
            if (game != null && game.finished) {
                double bet = game.bet;
                slotsManager.clearGame(player.getUniqueId());
                slotsManager.start(player, bet);
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
        SlotsManager.Game game = slotsManager.getGame(player.getUniqueId());
        if (game != null && game.finished) {
            slotsManager.clearGame(player.getUniqueId());
        }
    }
}
