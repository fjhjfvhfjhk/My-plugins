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

public class RollDuelGUI implements Listener {

    private static final String TITLE = "§6Дуэль";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();

    // Слоты окна карусели: 10..16, центр = 13
    private static final int WINDOW_START = 10;
    private static final int WINDOW_CENTER = 13;
    private static final int POINTER_SLOT = 4;

    private final RollPlugin plugin;
    private final DuelManager duelManager;

    public RollDuelGUI(RollPlugin plugin, DuelManager duelManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        updateInventory(inv);
        player.openInventory(inv);
        openInventories.put(player, inv);
    }

    private void updateInventory(Inventory inv) {
        inv.clear();
        fillEmptyWithGlass(inv);

        // Указатель (слот 4) – над центром окна
        ItemStack pointer = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta pointerMeta = pointer.getItemMeta();
        pointerMeta.setDisplayName("§a▲");
        pointer.setItemMeta(pointerMeta);
        inv.setItem(POINTER_SLOT, pointer);

        // Информация (слот 22)
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§eДуэль");
        List<String> lore = new ArrayList<>();
        if (duelManager.isActive()) {
            lore.add("§7Ставка: §f" + plugin.getEconomyManager().format(duelManager.getBet()));
            if (duelManager.isFinished()) {
                if (duelManager.getWin()) {
                    lore.add("§aВы выиграли! +" + plugin.getEconomyManager().format(duelManager.getWinAmount()));
                } else {
                    lore.add("§cВы проиграли. Потеряно: " + plugin.getEconomyManager().format(duelManager.getBet()));
                }
            } else if (duelManager.isFrozen()) {
                lore.add("§6Колесо остановилось...");
            } else if (duelManager.isSpinning()) {
                lore.add("§6Колесо вращается...");
            } else {
                lore.add("§eОжидание старта...");
            }
        } else {
            lore.add("§7Игра не активна");
            lore.add("§eИспользуйте /roll duel <сумма>");
        }
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(22, info);

        // Окно карусели (слоты 10..16)
        if (duelManager.isActive()) {
            if (duelManager.isSpinning() || duelManager.isFrozen()) {
                List<Material> visible = duelManager.getVisibleSlots();
                for (int i = 0; i < visible.size() && i < 7; i++) {
                    Material mat = visible.get(i);
                    int slot = WINDOW_START + i;
                    boolean isCenter = (slot == WINDOW_CENTER);
                    ItemStack item = createItem(mat, isCenter && duelManager.isFrozen());
                    inv.setItem(slot, item);
                }
            } else {
                // Ожидание старта – показываем статичные предметы
                inv.setItem(WINDOW_START, createItem(Material.REDSTONE, false));
                inv.setItem(WINDOW_START + 1, createItem(Material.EMERALD, false));
                inv.setItem(WINDOW_START + 2, createItem(Material.REDSTONE, false));
                inv.setItem(WINDOW_START + 3, createItem(Material.EMERALD, true)); // центр
                inv.setItem(WINDOW_START + 4, createItem(Material.REDSTONE, false));
                inv.setItem(WINDOW_START + 5, createItem(Material.EMERALD, false));
                inv.setItem(WINDOW_START + 6, createItem(Material.REDSTONE, false));
            }
        }

        // Кнопка закрытия (слот 26)
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        closeBtn.setItemMeta(closeMeta);
        inv.setItem(26, closeBtn);
    }

    private ItemStack createItem(Material mat, boolean highlight) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (mat == Material.EMERALD) {
            if (highlight) {
                meta.setDisplayName("§a§l🏆 Изумруд — ВЫИГРЫШ!");
                meta.setLore(List.of("§7Удвоение ставки", "§a🎉 ПОБЕДА!"));
            } else {
                meta.setDisplayName("§aИзумруд");
                meta.setLore(List.of("§7Выигрыш — удвоение"));
            }
        } else if (mat == Material.REDSTONE) {
            if (highlight) {
                meta.setDisplayName("§c§l🏆 Редстоун — ПРОИГРЫШ!");
                meta.setLore(List.of("§7Потеря ставки", "§c😢 УВЫ..."));
            } else {
                meta.setDisplayName("§cРедстоун");
                meta.setLore(List.of("§7Проигрыш — потеря"));
            }
        } else {
            meta.setDisplayName("§f" + mat.name().toLowerCase());
        }
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
        RollDuelGUI gui = plugin.getDuelGUI();
        if (gui == null) return;
        for (Map.Entry<Player, Inventory> entry : new HashMap<>(openInventories).entrySet()) {
            Player player = entry.getKey();
            Inventory inv = entry.getValue();
            if (player.isOnline() && player.getOpenInventory() != null &&
                    player.getOpenInventory().getTopInventory() != null &&
                    player.getOpenInventory().getTopInventory().equals(inv)) {
                gui.updateInventory(inv);
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
        if (slot == 26) {
            player.closeInventory();
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
        openInventories.remove(event.getPlayer());
    }
}
