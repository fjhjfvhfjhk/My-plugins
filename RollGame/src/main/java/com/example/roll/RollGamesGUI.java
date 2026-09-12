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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Главное меню RollGame.
 * Клик по игре → BetInputGUI (или сразу GUI для upgrade).
 * v2.2: добавлены crash (slot 16) и upgrade (slot 17).
 */
public class RollGamesGUI implements Listener {

    private static final String TITLE = "§6🎰 Казино";

    private final RollPlugin plugin;
    private final Map<Integer, String[]> slotGame = new HashMap<>();

    public RollGamesGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        slotGame.clear();
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillEmptyWithGlass(inv);

        double balance = plugin.getEconomyManager().getBalance(player);
        long jackpot = plugin.getRollData().getJackpot();
        ItemStack header = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta hm = header.getItemMeta();
        hm.setDisplayName("§6🎰 Добро пожаловать в казино!");
        hm.setLore(List.of(
                "§7Ваш баланс: §e" + plugin.getEconomyManager().format(balance),
                "§7Общий джекпот: §d" + plugin.getEconomyManager().format(jackpot),
                "",
                "§7Нажмите на игру, чтобы сделать ставку"
        ));
        header.setItemMeta(hm);
        inv.setItem(4, header);

        addGame(inv, 10, "slots", "🎰 Слоты",
                List.of("§7Три барабана, три шанса!",
                        "§7Собери 3 одинаковых — умножь ставку.",
                        "§aМножители: 1.5x — 50x",
                        "",
                        "§eНажмите, чтобы играть"));
        addGame(inv, 11, "duel", "⚔ Дуэль",
                List.of("§7Игра 50/50 против казино.",
                        "§7Удвой ставку или потеряй.",
                        "§aПобеда: x2",
                        "",
                        "§eНажмите, чтобы играть"));
        addGame(inv, 12, "mines", "💥 Мины",
                List.of("§7Открывай клетки, избегай мин.",
                        "§7Множитель растёт с каждым ходом.",
                        "§7После выбора ставки — введите параметры.",
                        "",
                        "§eНажмите, чтобы играть"));
        addGame(inv, 13, "wheel", "🎡 Колесо Фортуны",
                List.of("§7Крути колесо с множителями.",
                        "§7Секторы: 0x — 5x.",
                        "§aМаксимум x5",
                        "",
                        "§eНажмите, чтобы играть"));
        addGame(inv, 14, "stairs", "🪜 Лестница",
                List.of("§7Поднимайся по ступеням, множитель растёт.",
                        "§7На каждой ступени: забрать или рискнуть.",
                        "§aМаксимум x42",
                        "",
                        "§eНажмите, чтобы играть"));
        addGame(inv, 15, "classic", "📊 Классическая рулетка",
                List.of("§7Рулетка со ставками игроков.",
                        "§7Победитель забирает весь банк.",
                        "§aШанс = ваша ставка / банк",
                        "",
                        "§eНажмите, чтобы играть"));
        addGame(inv, 16, "crash", "🚀 Crash",
                List.of("§7Множитель растёт со временем.",
                        "§7Успей забрать до взрыва!",
                        "§aМаксимум x100+",
                        "",
                        "§eНажмите, чтобы играть"));

        // Upgrade — отдельная кнопка (не через BetInputGUI)
        ItemStack upgrade = new ItemStack(Material.NETHER_STAR);
        ItemMeta um = upgrade.getItemMeta();
        um.setDisplayName("§a🔮 Апгрейдер");
        um.setLore(List.of(
                "§7Улучшай предметы или крути шанс.",
                "§7Два режима: Шанс / Предмет.",
                "",
                "§eНажмите, чтобы играть"));
        upgrade.setItemMeta(um);
        inv.setItem(17, upgrade);
        slotGame.put(17, new String[]{"__upgrade__", "Апгрейдер", "0"});

        ItemStack statsBtn = new ItemStack(Material.BOOK);
        ItemMeta sm = statsBtn.getItemMeta();
        sm.setDisplayName("§e📊 Моя статистика");
        sm.setLore(List.of("§7Показать статистику по играм.",
                "§f/roll stats",
                "", "§eНажмите, чтобы открыть"));
        statsBtn.setItemMeta(sm);
        inv.setItem(22, statsBtn);

        ItemStack topBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta tm = topBtn.getItemMeta();
        tm.setDisplayName("§e📜 Топ игроков");
        tm.setLore(List.of("§7Рейтинг по победам в рулетке.",
                "§f/roll top",
                "", "§eНажмите, чтобы открыть"));
        topBtn.setItemMeta(tm);
        inv.setItem(20, topBtn);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(26, close);

        player.openInventory(inv);
    }

    private void addGame(Inventory inv, int slot, String key, String name, List<String> lore) {
        Material mat = switch (key) {
            case "slots" -> Material.LEVER;
            case "duel" -> Material.DIAMOND_SWORD;
            case "mines" -> Material.TNT;
            case "wheel" -> Material.NETHER_STAR;
            case "stairs" -> Material.LADDER;
            case "classic" -> Material.PAPER;
            case "crash" -> Material.FIREWORK_ROCKET;
            case "upgrade" -> Material.NETHER_STAR;
            default -> Material.STONE;
        };
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§a" + name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        inv.setItem(slot, icon);
        slotGame.put(slot, new String[]{key, name.replaceAll("§.", ""), "100"});
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot == 26) { player.closeInventory(); return; }
        if (slot == 20) { player.closeInventory(); player.performCommand("roll top"); return; }
        if (slot == 22) { player.closeInventory(); player.performCommand("roll stats"); return; }

        String[] gameInfo = slotGame.get(slot);
        if (gameInfo == null) return;

        player.closeInventory();

        if ("__upgrade__".equals(gameInfo[0])) {
            plugin.getUpgradeHubGUI().open(player);
            return;
        }

        plugin.getBetInputGUI().open(player, gameInfo[0], gameInfo[1], 100);
    }
}
