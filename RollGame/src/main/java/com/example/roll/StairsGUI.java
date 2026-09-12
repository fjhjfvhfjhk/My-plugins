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
 * GUI лестницы на 12 ступеней.
 * Ступени 1-4 — нижний ряд (38-41), 5-8 — средний (29-32), 9-12 — верхний (20-23).
 */
public class StairsGUI implements Listener {

    private static final String TITLE = "§6🪜 Лестница";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();

    /** 12 слотов ступеней — снизу вверх. */
    private static final int[] STEP_SLOTS = {
            38, 39, 40, 41,  // ступени 1-4 (низ)
            29, 30, 31, 32,  // ступени 5-8 (середина)
            20, 21, 22, 23   // ступени 9-12 (верх)
    };

    private static final int SLOT_INFO = 4;
    private static final int SLOT_STEP = 13;
    private static final int SLOT_CASHOUT = 15;
    private static final int SLOT_CLOSE = 44;

    private final RollPlugin plugin;
    private final StairsManager stairsManager;

    public StairsGUI(RollPlugin plugin, StairsManager stairsManager) {
        this.plugin = plugin;
        this.stairsManager = stairsManager;
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

        StairsManager.Game game = stairsManager.getGame(player.getUniqueId());

        // === Информация ===
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e🪜 Лестница");
        List<String> infoLore = new ArrayList<>();
        if (game != null) {
            infoLore.add("§7Ставка: §f" + plugin.getEconomyManager().format(game.bet));
            infoLore.add("§7Ступень: §f" + game.currentStep + " / " + StairsManager.MULTIPLIERS.length);
            if (game.currentStep > 0) {
                double mult = StairsManager.MULTIPLIERS[game.currentStep - 1];
                double amount = game.bet * mult;
                infoLore.add("§7Текущий множитель: §e" + formatMultiplier(mult));
                infoLore.add("§7Можно забрать: §f" + plugin.getEconomyManager().format(amount));
            }
            if (game.finished) infoLore.add("§7Игра окончена");
        } else {
            infoLore.add("§7Игра не активна");
            infoLore.add("§7Использование: §f/roll stairs <ставка>");
        }
        im.setLore(infoLore);
        info.setItemMeta(im);
        inv.setItem(SLOT_INFO, info);

        // === Ступени ===
        if (game != null) {
            for (int i = 0; i < STEP_SLOTS.length; i++) {
                int stepNum = i + 1;
                double mult = StairsManager.MULTIPLIERS[i];
                boolean passed = game.currentStep >= stepNum;
                boolean next = game.currentStep + 1 == stepNum && !game.finished;

                Material mat;
                if (game.finished && game.currentStep == stepNum) mat = Material.LIME_WOOL;
                else if (passed) mat = Material.LIME_STAINED_GLASS_PANE;
                else if (next) mat = Material.YELLOW_STAINED_GLASS_PANE;
                else mat = Material.GRAY_STAINED_GLASS_PANE;

                ItemStack step = new ItemStack(mat);
                ItemMeta sm = step.getItemMeta();
                String prefix = passed ? "§a✔ " : (next ? "§e➡ " : "§7");
                sm.setDisplayName(prefix + "Ступень " + stepNum + " §7(§e" + formatMultiplier(mult) + "§7)");
                sm.setLore(List.of(
                        "§7Выигрыш: §f" + plugin.getEconomyManager().format(game.bet * mult),
                        "§7Шанс провала на этой ступени: §c15%"
                ));
                step.setItemMeta(sm);
                inv.setItem(STEP_SLOTS[i], step);
            }
        }

        // === Кнопка "Шагнуть" ===
        if (game != null && !game.finished && game.currentStep < StairsManager.MULTIPLIERS.length) {
            ItemStack stepBtn = new ItemStack(Material.LADDER);
            ItemMeta sbm = stepBtn.getItemMeta();
            sbm.setDisplayName("§e➡ Шагнуть дальше");
            double failChance = plugin.getConfig().getDouble("stairs.fail-chance", 0.15);
            double nextMult = StairsManager.MULTIPLIERS[Math.min(game.currentStep, StairsManager.MULTIPLIERS.length - 1)];
            sbm.setLore(List.of(
                    "§7Следующий множитель: §e" + formatMultiplier(nextMult),
                    "§cШанс провала: §f" + String.format("%.0f%%", failChance * 100),
                    "§eНажмите, чтобы рискнуть"
            ));
            stepBtn.setItemMeta(sbm);
            inv.setItem(SLOT_STEP, stepBtn);
        }

        // === Кнопка "Забрать" ===
        if (game != null && !game.finished && game.currentStep > 0) {
            ItemStack cashoutBtn = new ItemStack(Material.LIME_WOOL);
            ItemMeta cbm = cashoutBtn.getItemMeta();
            double mult = StairsManager.MULTIPLIERS[game.currentStep - 1];
            cbm.setDisplayName("§a💰 Забрать выигрыш");
            cbm.setLore(List.of(
                    "§7Получите: §f" + plugin.getEconomyManager().format(game.bet * mult),
                    "§7Множитель: §e" + formatMultiplier(mult),
                    "§eНажмите, чтобы забрать"
            ));
            cashoutBtn.setItemMeta(cbm);
            inv.setItem(SLOT_CASHOUT, cashoutBtn);
        }

        // === Закрыть ===
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(SLOT_CLOSE, close);
    }

    private String formatMultiplier(double m) {
        if (m == Math.floor(m)) return (int) m + "x";
        return String.format("%.2fx", m);
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
        StairsGUI gui = plugin.getStairsGUI();
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

        StairsManager.Game game = stairsManager.getGame(player.getUniqueId());
        if (game == null || game.finished) {
            if (event.getRawSlot() == SLOT_CLOSE) player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }
        if (slot == SLOT_STEP) { stairsManager.step(player); updateInventory(event.getInventory(), player); return; }
        if (slot == SLOT_CASHOUT) { stairsManager.cashout(player); updateInventory(event.getInventory(), player); return; }
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
        StairsManager.Game game = stairsManager.getGame(player.getUniqueId());
        if (game != null && !game.finished && game.currentStep > 0) {
            // Авто-cashout при закрытии
            stairsManager.cashout(player);
        } else if (game != null && !game.finished && game.currentStep == 0) {
            // Ничего не сделал — возврат
            stairsManager.cancel(player.getUniqueId());
        }
    }
}
