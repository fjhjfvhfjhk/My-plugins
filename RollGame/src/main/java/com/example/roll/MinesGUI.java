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

public class MinesGUI implements Listener {

    private static final String TITLE = "§6Мины";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();

    private static final int[] SLOTS_3X3 = {
            20, 21, 22,
            29, 30, 31,
            38, 39, 40
    };
    private static final int[] SLOTS_4X4 = {
            11, 12, 13, 14,
            20, 21, 22, 23,
            29, 30, 31, 32,
            38, 39, 40, 41
    };
    private static final int[] SLOTS_5X5 = {
            2, 3, 4, 5, 6,
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    };

    private static final int SLOT_INFO = 45;
    private static final int SLOT_CASHOUT = 49;
    private static final int SLOT_CLOSE = 53;

    private final RollPlugin plugin;
    private final MinesManager minesManager;

    public MinesGUI(RollPlugin plugin, MinesManager minesManager) {
        this.plugin = plugin;
        this.minesManager = minesManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        updateInventory(inv, player);
        player.openInventory(inv);
        openInventories.put(player, inv);
    }

    private int[] getSlotMap(int gridSize) {
        return switch (gridSize) {
            case 3 -> SLOTS_3X3;
            case 4 -> SLOTS_4X4;
            default -> SLOTS_5X5;
        };
    }

    private void updateInventory(Inventory inv, Player player) {
        inv.clear();
        fillEmptyWithGlass(inv);

        MinesManager.MinesGame game = minesManager.getGame(player.getUniqueId());

        if (game == null) {
            for (int slot : SLOTS_5X5) {
                inv.setItem(slot, createHiddenItem());
            }
        } else {
            int[] slots = getSlotMap(game.gridSize);
            for (int i = 0; i < slots.length; i++) {
                int slot = slots[i];
                int cellIndex = i;

                if (game.gameOver) {
                    if (game.minePositions.contains(cellIndex)) {
                        inv.setItem(slot, createMineItem());
                    } else if (game.revealedSafe.contains(cellIndex)) {
                        inv.setItem(slot, createSafeItem());
                    } else {
                        // Нераскрытые безопасные клетки после проигрыша – просто белые панели
                        inv.setItem(slot, createHiddenItem());
                    }
                } else {
                    if (game.revealedSafe.contains(cellIndex)) {
                        inv.setItem(slot, createSafeItem());
                    } else {
                        inv.setItem(slot, createHiddenItem());
                    }
                }
            }

            // Информация
            ItemStack info = new ItemStack(Material.OAK_SIGN);
            ItemMeta infoMeta = info.getItemMeta();
            infoMeta.setDisplayName("§eИгра 'Мины'");
            List<String> lore = new ArrayList<>();
            lore.add("§7Ставка: §f" + plugin.getEconomyManager().format(game.bet));
            lore.add("§7Мин: §f" + game.minesCount + "§7 из §f" + game.totalCells);
            lore.add("§7Сетка: §f" + game.gridSize + "x" + game.gridSize);
            lore.add("§7Открыто клеток: §f" + game.revealedSafe.size());
            if (!game.gameOver) {
                double mult = game.getMultiplier();
                lore.add("§7Множитель: §e" + String.format("%.2f", mult) + "x");
                lore.add("§7Потенциальный выигрыш: §f" +
                        plugin.getEconomyManager().format(game.getPotentialWin()));
            } else {
                if (game.won) {
                    lore.add("§a🎉 Вы забрали выигрыш!");
                } else {
                    lore.add("§c💥 Вы попали на мину.");
                }
            }
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
            inv.setItem(SLOT_INFO, info);

            // Кнопка "Забрать выигрыш"
            if (!game.gameOver && !game.revealedSafe.isEmpty()) {
                ItemStack cashout = new ItemStack(Material.LIME_WOOL);
                ItemMeta cashMeta = cashout.getItemMeta();
                cashMeta.setDisplayName("§a💰 Забрать выигрыш");
                cashMeta.setLore(List.of(
                        "§7Получите: §f" + plugin.getEconomyManager().format(game.getPotentialWin()),
                        "§7Множитель: §e" + String.format("%.2f", game.getMultiplier()) + "x",
                        "§eНажмите, чтобы забрать"
                ));
                cashout.setItemMeta(cashMeta);
                inv.setItem(SLOT_CASHOUT, cashout);
            }
        }

        // Кнопка "Закрыть"
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        close.setItemMeta(closeMeta);
        inv.setItem(SLOT_CLOSE, close);
    }

    private ItemStack createHiddenItem() {
        ItemStack item = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f?");
        meta.setLore(List.of("§7Нажмите, чтобы открыть"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSafeItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b💎 Безопасно");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMineItem() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c💥 МИНА");
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
        MinesGUI gui = plugin.getMinesGUI();
        if (gui == null) return;
        for (Map.Entry<Player, Inventory> entry : new HashMap<>(openInventories).entrySet()) {
            Player player = entry.getKey();
            Inventory inv = entry.getValue();
            if (player.isOnline() && player.getOpenInventory() != null &&
                    player.getOpenInventory().getTopInventory() != null &&
                    player.getOpenInventory().getTopInventory().equals(inv)) {
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

        MinesManager.MinesGame game = minesManager.getGame(player.getUniqueId());
        if (game == null) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_CASHOUT) {
            if (!game.gameOver && !game.revealedSafe.isEmpty()) {
                minesManager.cashout(player);
                updateInventory(event.getInventory(), player);
            }
            return;
        }

        if (game.gameOver) return;

        int[] slots = getSlotMap(game.gridSize);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                boolean safe = minesManager.revealCell(player, i);
                updateInventory(event.getInventory(), player);

                // Авто-cashout, если открыты все безопасные клетки
                if (safe && game.revealedSafe.size() == game.totalCells - game.minesCount) {
                    minesManager.cashout(player);
                    updateInventory(event.getInventory(), player);
                }
                return;
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

        MinesManager.MinesGame game = minesManager.getGame(player.getUniqueId());
        if (game != null && !game.gameOver) {
            if (game.revealedSafe.isEmpty()) {
                plugin.getEconomyManager().deposit(player, game.bet);
                player.sendMessage("§eИгра отменена, ставка возвращена.");
                minesManager.clearGame(player.getUniqueId());
            } else {
                double amount = game.getPotentialWin();
                plugin.getEconomyManager().deposit(player, amount);
                player.sendMessage("§eИгра закрыта. Автоматически забрано: §f" +
                        plugin.getEconomyManager().format(amount));
                game.gameOver = true;
                minesManager.clearGame(player.getUniqueId());
            }
        }
    }
}
