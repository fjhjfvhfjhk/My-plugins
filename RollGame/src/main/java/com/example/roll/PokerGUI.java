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
import java.util.List;

/**
 * GUI покерного стола. Аватарки, анимация карт, свой ход.
 */
public class PokerGUI implements Listener {

    public static final String TITLE = "§6🃏 Покер";

    private static final int SLOT_INFO = 4;
    private static final int[] COMMUNITY_SLOTS = {29, 30, 31, 32, 33};
    private static final int SLOT_POT = 22;
    private static final int[] SEAT_SLOTS = {10, 12, 14, 28, 34, 16};

    private static final int SLOT_MY_CARD_1 = 45;
    private static final int SLOT_MY_CARD_2 = 46;

    private static final int BTN_FOLD = 47;
    private static final int BTN_CHECK = 48;
    private static final int BTN_CALL = 49;
    private static final int BTN_RAISE_MIN = 50;
    private static final int BTN_RAISE_HALF = 51;
    private static final int BTN_RAISE_POT = 52;
    private static final int BTN_ALL_IN = 53;

    private static final int BTN_RULES = 38;
    private static final int BTN_CARDS = 40;
    private static final int BTN_LEAVE = 42;

    private final RollPlugin plugin;

    public PokerGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player, PokerTable table) {
        RollPlugin plugin = RollPlugin.getInstance();
        if (plugin == null) return;
        plugin.getPokerGUI().openInternal(player, table);
    }

    private void openInternal(Player player, PokerTable table) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        updateInventory(inv, table, player);
        player.openInventory(inv);
    }

    private void updateInventory(Inventory inv, PokerTable table, Player viewer) {
        inv.clear();
        fillGlass(inv);

        // Информация
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e🃏 Стол #" + table.id);
        List<String> lore = new ArrayList<>();
        lore.add("§7Стадия: §f" + stageName(table.stage));
        lore.add("§7Раздача: §f#" + table.handNumber);
        lore.add("§7Блайнды: §f" + table.smallBlind + "/" + table.bigBlind);
        if (table.dealing) {
            lore.add("§6▶ Раздача карт...");
        } else if (table.currentTurn != null) {
            PokerTable.Player cur = table.getPlayer(table.currentTurn);
            if (cur != null) {
                String marker = cur.uuid.equals(viewer.getUniqueId()) ? "§a➤ ВАШ ХОД!" : "§f" + cur.name;
                lore.add("§7Сейчас ходит: " + marker);
            }
        }
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(SLOT_INFO, info);

        // Банк
        ItemStack pot = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta pm = pot.getItemMeta();
        pm.setDisplayName("§6💰 Банк: §f" + plugin.getEconomyManager().format(table.pot));
        pot.setItemMeta(pm);
        inv.setItem(SLOT_POT, pot);

        // Общие карты
        for (int i = 0; i < 5; i++) {
            if (i < table.community.size()) {
                PokerHand.Card c = table.community.get(i);
                if (c == null) {
                    // Рубашка (в процессе переворота)
                    inv.setItem(COMMUNITY_SLOTS[i], hiddenCard());
                } else {
                    inv.setItem(COMMUNITY_SLOTS[i], c.toItem());
                }
            } else {
                inv.setItem(COMMUNITY_SLOTS[i], hiddenCard());
            }
        }

        // Игроки с аватарками
        int idx = 0;
        for (PokerTable.Player p : table.players.values()) {
            if (idx >= SEAT_SLOTS.length) break;
            ItemStack seat = createPlayerSkull(p);
            ItemMeta sm = seat.getItemMeta();
            String status = p.folded ? "§8(пас)" : (p.allIn ? "§c(ва-банк)" : "");
            sm.setDisplayName("§f" + p.name + " " + status);
            List<String> sl = new ArrayList<>();
            sl.add("§7Фишки: §f" + plugin.getEconomyManager().format(p.chips));
            sl.add("§7В банке: §f" + plugin.getEconomyManager().format(p.contributed));
            if (table.currentTurn != null && table.currentTurn.equals(p.uuid) && !table.dealing) sl.add("§e➤ ходит сейчас");
            sm.setLore(sl);
            seat.setItemMeta(sm);
            inv.setItem(SEAT_SLOTS[idx], seat);
            idx++;
        }

        // ===== Свои карты =====
        PokerTable.Player me = table.getPlayer(viewer.getUniqueId());
        if (me != null && me.hole.size() >= 2) {
            // Анимация: сколько карт уже «выдано» — показываем по нарастающей
            int cardsVisible = table.dealing ? Math.max(0, table.dealtCards / table.players.size()) : 2;
            cardsVisible = Math.min(2, cardsVisible);

            if (cardsVisible >= 1) {
                ItemStack c1 = me.hole.get(0).toItem();
                ItemMeta m1 = c1.getItemMeta();
                List<String> cardLore = new ArrayList<>();
                cardLore.add("§7Ваша закрытая карта");
                cardLore.add("§8(видна только вам)");
                m1.setLore(cardLore);
                c1.setItemMeta(m1);
                inv.setItem(SLOT_MY_CARD_1, c1);
            } else {
                inv.setItem(SLOT_MY_CARD_1, hiddenCard());
            }

            if (cardsVisible >= 2) {
                ItemStack c2 = me.hole.get(1).toItem();
                ItemMeta m2 = c2.getItemMeta();
                List<String> cardLore = new ArrayList<>();
                cardLore.add("§7Ваша закрытая карта");
                cardLore.add("§8(видна только вам)");
                m2.setLore(cardLore);
                c2.setItemMeta(m2);
                inv.setItem(SLOT_MY_CARD_2, c2);
            } else {
                inv.setItem(SLOT_MY_CARD_2, hiddenCard());
            }
        } else {
            inv.setItem(SLOT_MY_CARD_1, hiddenCard());
            inv.setItem(SLOT_MY_CARD_2, hiddenCard());
        }

        // Кнопки действий
        boolean inBettingRound = switch (table.stage) {
            case PREFLOP, FLOP, TURN, RIVER -> true;
            default -> false;
        };

        if (inBettingRound && !table.dealing) {
            boolean myTurn = table.currentTurn != null && table.currentTurn.equals(viewer.getUniqueId());
            long myChips = me != null ? me.chips : 0;
            long myRoundBet = me != null ? me.roundBet : 0;
            long toCall = table.highestBet - myRoundBet;

            inv.setItem(BTN_FOLD, btn(Material.RED_WOOL, "§c❌ Пас",
                    "§7Сбросить карты и выйти из раздачи.",
                    "§7Вложенные деньги в банк не возвращаются.",
                    "",
                    myTurn ? "§e▶ Нажмите, чтобы спасовать" : "§8Не ваш ход"));

            inv.setItem(BTN_CHECK, btn(Material.LIGHT_BLUE_WOOL, "§b✓ Чек",
                    "§7Пропустить ход без ставки.",
                    "§7Доступно, когда никто не повышал.",
                    "",
                    myTurn ? "§e▶ Нажмите, чтобы чекнуть" : "§8Не ваш ход"));

            String callTitle = toCall > 0 ? "§a💰 Колл " + toCall : "§a💰 Колл (0)";
            String callDesc = toCall > 0 ? "§7Поставить §f" + toCall + "§7 монет" : "§7Уже уравнено";
            inv.setItem(BTN_CALL, btn(Material.GREEN_WOOL, callTitle,
                    "§7Уравнять текущую ставку.",
                    callDesc,
                    "",
                    myTurn ? "§e▶ Нажмите, чтобы уравнять" : "§8Не ваш ход"));

            long minRaise = table.highestBet + table.bigBlind;
            long halfPotRaise = Math.max(minRaise, table.highestBet + table.pot / 2);
            long potRaise = Math.max(minRaise, table.highestBet + table.pot);

            inv.setItem(BTN_RAISE_MIN, btn(Material.ORANGE_WOOL, "§6⬆ Рейз (мин)",
                    "§7Повысить ставку до минимума.", "§7Сумма: §f" + minRaise, "",
                    myTurn ? "§e▶ Нажмите" : "§8Не ваш ход"));
            inv.setItem(BTN_RAISE_HALF, btn(Material.ORANGE_WOOL, "§6⬆ Рейз (+½ банка)",
                    "§7Повысить ставку на половину банка.", "§7Сумма: §f" + halfPotRaise, "",
                    myTurn ? "§e▶ Нажмите" : "§8Не ваш ход"));
            inv.setItem(BTN_RAISE_POT, btn(Material.ORANGE_WOOL, "§6⬆ Рейз (+банк)",
                    "§7Повысить ставку на весь банк.", "§7Сумма: §f" + potRaise, "",
                    myTurn ? "§e▶ Нажмите" : "§8Не ваш ход"));
            inv.setItem(BTN_ALL_IN, btn(Material.NETHERITE_BLOCK, "§4🔥 ВА-БАНК",
                    "§7Поставить ВСЕ свои фишки.", "§7Ваши фишки: §f" + myChips, "§cЕсли проиграете — вылетите.", "",
                    myTurn ? "§c▶ Нажмите" : "§8Не ваш ход"));
        }

        inv.setItem(BTN_RULES, btn(Material.BOOK, "§e📖 Как играть?",
                "§7Цель: лучшая рука из §f2 своих§7 и §f5 общих§7 карт.",
                "",
                "§7Порядок:",
                "§f1.§7 Ставки (префлоп)",
                "§f2.§7 Флоп — 3 общие карты",
                "§f3.§7 Тёрн — 4-я карта",
                "§f4.§7 Ривер — 5-я карта",
                "§f5.§7 Вскрытие",
                "",
                "§7Комбинации (сильные → слабые):",
                "§7Флеш-рояль, стрит-флеш, каре, фулл-хаус,",
                "§7флеш, стрит, сет, две пары, пара, старшая."));

        inv.setItem(BTN_CARDS, btn(Material.PAPER, "§e👁 Детали руки",
                "§7Открыть подробное окно с комбинацией.", "§e▶ Нажмите, чтобы открыть"));

        inv.setItem(BTN_LEAVE, btn(Material.BARRIER, "§c🚪 Выйти из-за стола",
                "§7Покинуть стол.",
                "§cВо время раздачи = пас, ставка не возвращается."));
    }

    private ItemStack hiddenCard() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c▨ рубашка");
        meta.setLore(List.of("§7Карта ещё не открыта"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Создаёт голову игрока. Если установлен SkinsRestorer, скин подтянется автоматически.
     */
    private ItemStack createPlayerSkull(PokerTable.Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            // setOwningPlayer в offline-mode работает только со SkinsRestorer,
            // без него будет стандартная голова.
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(p.uuid));
        } catch (Exception ignored) {}
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack btn(Material mat, String name, String... lines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String l : lines) lore.add(l);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String stageName(PokerTable.Stage s) {
        return switch (s) {
            case WAITING -> "Ожидание игроков";
            case PREFLOP -> "Префлоп (только 2 карты)";
            case FLOP -> "Флоп (3 общие карты)";
            case TURN -> "Тёрн (4-я карта)";
            case RIVER -> "Ривер (5-я карта)";
            case SHOWDOWN -> "Вскрытие";
            case FINISHED -> "Завершено";
        };
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
        if (slot == SLOT_MY_CARD_1 || slot == SLOT_MY_CARD_2) return;

        PokerManager pm = plugin.getPokerManager();
        PokerTable table = pm.getPlayerTable(player.getUniqueId());
        if (table == null) { player.closeInventory(); return; }
        if (table.dealing) { player.sendMessage("§cКарты ещё раздаются, подождите."); return; }

        switch (slot) {
            case BTN_FOLD -> pm.fold(player);
            case BTN_CHECK -> pm.check(player);
            case BTN_CALL -> pm.call(player);
            case BTN_RAISE_MIN -> {
                if (isMyTurn(table, player)) {
                    long minRaise = table.highestBet + table.bigBlind;
                    pm.raise(player, minRaise);
                }
            }
            case BTN_RAISE_HALF -> {
                if (isMyTurn(table, player)) {
                    long halfPot = Math.max(table.highestBet + table.bigBlind, table.highestBet + table.pot / 2);
                    pm.raise(player, halfPot);
                }
            }
            case BTN_RAISE_POT -> {
                if (isMyTurn(table, player)) {
                    long potRaise = Math.max(table.highestBet + table.bigBlind, table.highestBet + table.pot);
                    pm.raise(player, potRaise);
                }
            }
            case BTN_ALL_IN -> pm.allIn(player);
            case BTN_CARDS -> {
                player.closeInventory();
                plugin.getPokerCardsGUI().open(player, table);
            }
            case BTN_RULES -> player.performCommand("roll poker rules");
            case BTN_LEAVE -> {
                pm.leaveTable(player);
                player.closeInventory();
            }
        }
    }

    private boolean isMyTurn(PokerTable table, Player player) {
        return table.currentTurn != null && table.currentTurn.equals(player.getUniqueId());
    }
}
