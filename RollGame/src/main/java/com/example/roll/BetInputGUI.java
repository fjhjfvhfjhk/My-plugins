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

import java.util.*;

/**
 * GUI ввода ставки. Кнопки ±10 / ±100 / ±1000, «Максимум», «Повторить».
 * v2.2: добавлены игры crash и upgrade_chance.
 */
public class BetInputGUI implements Listener {

    public static final String TITLE = "§6🎯 Ставка";
    private static final int[] SIZE_STEPS = {10, 100, 1000};

    private final RollPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public BetInputGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, String gameKey, String displayName, double defaultBet) {
        Session s = new Session();
        s.gameKey = gameKey;
        s.displayName = displayName;
        double lastBet = plugin.getRollData().getLastBet(player.getUniqueId(), gameKey);
        s.amount = lastBet > 0 ? lastBet : Math.max(defaultBet, 100);
        sessions.put(player.getUniqueId(), s);
        render(player);
    }

    private void render(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        inv.setItem(4, item(Material.GOLD_INGOT,
                "§e💰 Ставка: §f" + plugin.getEconomyManager().format(s.amount),
                "§7Игра: §f" + s.displayName,
                "§7Ваш баланс: §f" + plugin.getEconomyManager().format(
                        plugin.getEconomyManager().getBalance(player)),
                "",
                "§7Используйте кнопки ниже,",
                "§7чтобы настроить сумму."));

        inv.setItem(9,  item(Material.RED_STAINED_GLASS_PANE, "§c− 1000", "§7Уменьшить на §f1000"));
        inv.setItem(10, item(Material.RED_STAINED_GLASS_PANE, "§c− 100",  "§7Уменьшить на §f100"));
        inv.setItem(11, item(Material.RED_STAINED_GLASS_PANE, "§c− 10",   "§7Уменьшить на §f10"));

        inv.setItem(15, item(Material.LIME_STAINED_GLASS_PANE, "§a+ 10",   "§7Увеличить на §f10"));
        inv.setItem(16, item(Material.LIME_STAINED_GLASS_PANE, "§a+ 100",  "§7Увеличить на §f100"));
        inv.setItem(17, item(Material.LIME_STAINED_GLASS_PANE, "§a+ 1000", "§7Увеличить на §f1000"));

        double lastBet = plugin.getRollData().getLastBet(player.getUniqueId(), s.gameKey);
        if (lastBet > 0 && Math.abs(lastBet - s.amount) > 0.01) {
            inv.setItem(18, item(Material.GOLD_NUGGET, "§e↻ Повторить",
                    "§7Поставить последнюю ставку:",
                    "§f" + plugin.getEconomyManager().format(lastBet),
                    "",
                    "§eНажмите, чтобы применить"));
        }

        inv.setItem(20, item(Material.DIAMOND, "§b💎 Максимум",
                "§7Поставить всё, что есть на балансе.",
                "§7Баланс: §f" + plugin.getEconomyManager().format(
                        plugin.getEconomyManager().getBalance(player)),
                "",
                "§eНажмите, чтобы установить"));

        inv.setItem(22, item(Material.LIME_WOOL, "§a✔ Начать игру",
                "§7Запустить §f" + s.displayName,
                "§7со ставкой §f" + plugin.getEconomyManager().format(s.amount),
                "",
                "§eНажмите, чтобы играть"));

        inv.setItem(24, item(Material.RED_WOOL, "§c✖ Отмена",
                "§7Вернуться в главное меню"));

        inv.setItem(26, item(Material.BARRIER, "§c← Назад",
                "§7Закрыть"));

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
            }
        }

        player.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> l = new ArrayList<>();
        for (String line : lore) l.add(line);
        meta.setLore(l);
        item.setItemMeta(meta);
        return item;
    }

    private void refresh(Player player) { render(player); }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        Session s = sessions.get(player.getUniqueId());
        if (s == null) { player.closeInventory(); return; }

        int slot = event.getRawSlot();
        double balance = plugin.getEconomyManager().getBalance(player);

        switch (slot) {
            case 9  -> { s.amount = Math.max(1, s.amount - 1000); refresh(player); }
            case 10 -> { s.amount = Math.max(1, s.amount - 100);  refresh(player); }
            case 11 -> { s.amount = Math.max(1, s.amount - 10);   refresh(player); }
            case 15 -> { s.amount += 10;   if (s.amount > balance) s.amount = balance; refresh(player); }
            case 16 -> { s.amount += 100;  if (s.amount > balance) s.amount = balance; refresh(player); }
            case 17 -> { s.amount += 1000; if (s.amount > balance) s.amount = balance; refresh(player); }
            case 18 -> {
                double lastBet = plugin.getRollData().getLastBet(player.getUniqueId(), s.gameKey);
                if (lastBet > 0) { s.amount = Math.min(lastBet, balance); refresh(player); }
            }
            case 20 -> { s.amount = Math.max(1, balance); refresh(player); }
            case 22 -> {
                double bet = s.amount;
                if (bet <= 0) { player.sendMessage("§cСтавка должна быть положительной."); return; }
                if (bet > balance) { player.sendMessage("§cНедостаточно денег."); return; }
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                startGame(player, s.gameKey, bet);
            }
            case 24, 26 -> {
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                plugin.getGamesGUI().open(player);
            }
        }
    }

    /** Запускает соответствующую игру с указанной ставкой. */
    private void startGame(Player player, String gameKey, double bet) {
        switch (gameKey) {
            case "slots" -> {
                if (plugin.getSlotsManager().start(player, bet)) plugin.getSlotsGUI().open(player);
            }
            case "wheel" -> {
                if (plugin.getWheelManager().start(player, bet)) plugin.getWheelGUI().open(player);
            }
            case "stairs" -> {
                if (plugin.getStairsManager().start(player, bet)) plugin.getStairsGUI().open(player);
            }
            case "duel" -> {
                plugin.getDuelManager().startDuel(player, bet);
                plugin.getDuelGUI().open(player);
            }
            case "classic" -> {
                plugin.getGameManager().addBet(player, bet);
                plugin.getRollGUI().open(player);
            }
            case "crash" -> {
                if (plugin.getCrashManager().start(player, bet)) {
                    plugin.getCrashGUI().open(player);
                }
            }
            case "upgrade_chance" -> {
                plugin.getUpgradeChanceGUI().open(player, bet);
            }
            case "mines" -> {
                player.sendMessage("§eДля мин укажите параметры командой:");
                player.sendMessage("§f/roll mines " + (long) bet + " <мины> <размер>");
            }
            default -> player.sendMessage("§cНеизвестная игра.");
        }
    }

    public static class Session {
        public String gameKey;
        public String displayName;
        public double amount;
    }
}
