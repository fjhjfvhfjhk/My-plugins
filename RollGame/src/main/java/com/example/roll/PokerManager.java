package com.example.roll;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class PokerManager {

    private final RollPlugin plugin;
    private final Map<Integer, PokerTable> tables = new HashMap<>();
    private final Map<UUID, Integer> playerTable = new HashMap<>();
    private int nextTableId = 1;
    private BukkitTask monitorTask;
    private BukkitTask actionBarTask;
    private long lastBlindIncrease = System.currentTimeMillis();
    private final int blindIncreaseMinutes;
    private final Map<Integer, List<PokerHand.Card>> pendingCommunity = new HashMap<>();

    public PokerManager(RollPlugin plugin) {
        this.plugin = plugin;
        this.blindIncreaseMinutes = plugin.getConfig().getInt("poker.blind-increase-minutes", 10);
        startMonitor();
        startActionBar();
    }

    public PokerTable getTable(int id) { return tables.get(id); }
    public PokerTable getPlayerTable(UUID uuid) {
        Integer id = playerTable.get(uuid);
        return id == null ? null : tables.get(id);
    }
    public Collection<PokerTable> getAllTables() { return tables.values(); }

    public boolean createTable(Player creator, long buyIn) {
        if (playerTable.containsKey(creator.getUniqueId())) {
            creator.sendMessage("§cВы уже за столом #" + playerTable.get(creator.getUniqueId()));
            return false;
        }
        if (buyIn < 100) { creator.sendMessage("§cМинимальный бай-ин: 100"); return false; }
        if (!plugin.getEconomyManager().has(creator, buyIn)) {
            creator.sendMessage("§cНедостаточно денег. Нужно: " + plugin.getEconomyManager().format(buyIn));
            return false;
        }
        plugin.getEconomyManager().withdraw(creator, buyIn);

        PokerTable table = new PokerTable(nextTableId++, buyIn);
        PokerTable.Player p = new PokerTable.Player(creator.getUniqueId(), creator.getName(), buyIn);
        table.players.put(creator.getUniqueId(), p);
        table.dealerIndex = 0;

        tables.put(table.id, table);
        playerTable.put(creator.getUniqueId(), table.id);

        creator.sendMessage("§a✓ Стол #" + table.id + " создан.");
        creator.sendMessage("§7Бай-ин: §f" + plugin.getEconomyManager().format(buyIn) +
                "§7. Блайнды: §f" + table.smallBlind + "/" + table.bigBlind);
        creator.sendMessage("§eПригласите друзей: §f/roll poker join " + table.id);
        return true;
    }

    public boolean joinTable(Player player, int tableId) {
        if (playerTable.containsKey(player.getUniqueId())) { player.sendMessage("§cВы уже за столом."); return false; }
        PokerTable table = tables.get(tableId);
        if (table == null) { player.sendMessage("§cСтол #" + tableId + " не найден."); return false; }
        if (table.stage != PokerTable.Stage.WAITING) { player.sendMessage("§cИгра уже началась."); return false; }
        if (table.players.size() >= 6) { player.sendMessage("§cСтол заполнен."); return false; }

        if (!plugin.getEconomyManager().has(player, table.buyIn)) {
            player.sendMessage("§cНедостаточно денег. Нужно: " + plugin.getEconomyManager().format(table.buyIn));
            return false;
        }
        plugin.getEconomyManager().withdraw(player, table.buyIn);

        PokerTable.Player p = new PokerTable.Player(player.getUniqueId(), player.getName(), table.buyIn);
        table.players.put(player.getUniqueId(), p);
        playerTable.put(player.getUniqueId(), tableId);

        player.sendMessage("§a✓ Вы за столом #" + tableId + " (" + table.players.size() + "/6)");
        broadcastToTable(table, "§e" + player.getName() + " присоединился (§f" + table.players.size() + "/6§e)");
        return true;
    }

    public void leaveTable(Player player) {
        PokerTable table = getPlayerTable(player.getUniqueId());
        if (table == null) { player.sendMessage("§cВы не за столом."); return; }
        PokerTable.Player p = table.getPlayer(player.getUniqueId());

        if (table.stage != PokerTable.Stage.WAITING) {
            if (p != null && !p.folded) {
                p.folded = true;
                broadcastToTable(table, "§8" + player.getName() + " покинул стол (пас).");
            }
            table.players.remove(player.getUniqueId());
            playerTable.remove(player.getUniqueId());
            if (p != null && p.chips > 0) {
                plugin.getEconomyManager().deposit(player, p.chips);
                player.sendMessage("§aВозвращено: " + plugin.getEconomyManager().format(p.chips));
            }
            player.closeInventory();
            if (table.activePlayerCount() <= 1) advanceTurn(table);
            if (table.players.isEmpty()) { tables.remove(table.id); pendingCommunity.remove(table.id); }
            return;
        }

        if (p != null) {
            plugin.getEconomyManager().deposit(player, p.chips);
            player.sendMessage("§aВы вышли. Возвращено: " + plugin.getEconomyManager().format(p.chips));
        }
        table.players.remove(player.getUniqueId());
        playerTable.remove(player.getUniqueId());
        player.closeInventory();
        if (table.players.isEmpty()) tables.remove(table.id);
    }

    public boolean startGame(Player player) {
        PokerTable table = getPlayerTable(player.getUniqueId());
        if (table == null) { player.sendMessage("§cВы не за столом."); return false; }
        if (!table.players.values().iterator().next().uuid.equals(player.getUniqueId())) {
            player.sendMessage("§cТолько создатель может начать."); return false;
        }
        if (table.stage != PokerTable.Stage.WAITING) { player.sendMessage("§cИгра уже идёт."); return false; }
        if (table.players.size() < 2) { player.sendMessage("§cНужно минимум 2 игрока."); return false; }

        table.handNumber = 0;
        this.lastBlindIncrease = System.currentTimeMillis();
        broadcastToTable(table, "§6§l🎰 Игра начинается!");
        startNewHand(table);
        return true;
    }

    private void startNewHand(PokerTable table) {
        table.players.entrySet().removeIf(e -> !playerTable.containsKey(e.getKey()));

        if (table.players.size() < 2) {
            PokerTable.Player last = table.players.values().stream().findFirst().orElse(null);
            if (last != null) {
                plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(last.uuid), last.chips);
                broadcastToTable(table, "§6§l🏆 Победитель: " + last.name + "! Забрано: " +
                        plugin.getEconomyManager().format(last.chips));
            }
            for (UUID uuid : new ArrayList<>(table.players.keySet())) {
                playerTable.remove(uuid);
                Player pl = Bukkit.getPlayer(uuid);
                if (pl != null) pl.closeInventory();
            }
            tables.remove(table.id);
            pendingCommunity.remove(table.id);
            return;
        }

        table.handNumber++;
        table.pot = 0;
        table.highestBet = 0;
        table.community.clear();
        table.lastAggressor = null;
        table.stage = PokerTable.Stage.PREFLOP;
        table.handStartTime = System.currentTimeMillis();
        table.dealing = true;
        table.dealtCards = 0;
        table.revealingIndex = -1;
        table.chipsAtHandStart.clear();

        for (PokerTable.Player p : table.players.values()) {
            p.hole.clear();
            p.folded = false;
            p.allIn = false;
            p.hasActedThisRound = false;
            p.roundBet = 0;
            p.contributed = 0;
            table.chipsAtHandStart.put(p.uuid, p.chips);
        }

        List<PokerHand.Card> deck = PokerHand.newDeck();
        int idx = 0;
        for (int i = 0; i < 2; i++) for (PokerTable.Player p : table.players.values()) p.hole.add(deck.get(idx++));
        List<PokerHand.Card> community = new ArrayList<>();
        for (int i = 0; i < 5; i++) community.add(deck.get(idx++));
        pendingCommunity.put(table.id, community);

        if (table.handNumber > 1) table.dealerIndex = (table.dealerIndex + 1) % table.players.size();

        broadcastToTable(table, "§6§l═══ Раздача #" + table.handNumber + " ═══");
        broadcastToTable(table, "§7Блайнды: §f" + table.smallBlind + "/" + table.bigBlind);

        for (PokerTable.Player p : table.players.values()) {
            Player pl = Bukkit.getPlayer(p.uuid);
            if (pl != null && playerTable.containsKey(p.uuid)) {
                PokerGUI.open(pl, table);
                pl.playSound(pl.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.4f);
            }
        }

        int totalCards = table.players.size() * 2;
        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (!tables.containsKey(table.id)) { cancel(); return; }
                table.dealtCards = step;
                refreshGUIs(table);
                for (PokerTable.Player p : table.players.values()) {
                    Player pl = Bukkit.getPlayer(p.uuid);
                    if (pl != null) pl.playSound(pl.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.4f, 1.6f + step * 0.03f);
                }
                step++;
                if (step > totalCards) {
                    table.dealing = false;
                    cancel();
                    List<PokerTable.Player> list = table.orderedPlayers();
                    int n = list.size();
                    PokerTable.Player sbPlayer, bbPlayer;
                    if (n == 2) {
                        sbPlayer = list.get(table.dealerIndex);
                        bbPlayer = list.get((table.dealerIndex + 1) % 2);
                    } else {
                        sbPlayer = list.get((table.dealerIndex + 1) % n);
                        bbPlayer = list.get((table.dealerIndex + 2) % n);
                    }
                    postBet(sbPlayer, table.smallBlind, table);
                    postBet(bbPlayer, table.bigBlind, table);
                    table.highestBet = table.bigBlind;

                    int startIdx = n == 2 ? table.dealerIndex : (table.dealerIndex + 3) % n;
                    table.currentTurn = list.get(startIdx).uuid;
                    table.turnStartTime = System.currentTimeMillis();
                    broadcastTurn(table);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void postBet(PokerTable.Player p, long amount, PokerTable table) {
        long actual = Math.min(amount, p.chips);
        p.chips -= actual;
        p.contributed += actual;
        p.roundBet += actual;
        table.pot += actual;
        if (p.chips == 0) p.allIn = true;
    }

    private void broadcastTurn(PokerTable table) {
        PokerTable.Player current = table.getPlayer(table.currentTurn);
        if (current == null) return;
        broadcastToTable(table, "§e▶ Ход: §f" + current.name + " §7(30 сек)");
        Player p = Bukkit.getPlayer(current.uuid);
        if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    public boolean check(Player player) {
        PokerTable table = getPlayerTable(player.getUniqueId());
        if (table == null || table.currentTurn == null) return false;
        if (table.dealing) { player.sendMessage("§cКарты ещё раздаются."); return false; }
        if (!table.currentTurn.equals(player.getUniqueId())) { player.sendMessage("§cНе ваш ход."); return false; }
        PokerTable.Player p = table.getPlayer(player.getUniqueId());
        if (p.roundBet < table.highestBet) { player.sendMessage("§cНельзя чек."); return false; }
        p.hasActedThisRound = true;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        broadcastToTable(table, "§7" + p.name + " — §fчек");
        advanceTurn(table);
        return true;
    }

    public boolean call(Player player) {
        PokerTable table = getPlayerTable(player.getUniqueId());
        if (table == null || table.currentTurn == null) return false;
        if (table.dealing) { player.sendMessage("§cКарты ещё раздаются."); return false; }
        if (!table.currentTurn.equals(player.getUniqueId())) { player.sendMessage("§cНе ваш ход."); return false; }
        PokerTable.Player p = table.getPlayer(player.getUniqueId());
        long toCall = table.highestBet - p.roundBet;
        if (toCall <= 0) { player.sendMessage("§cУже уравнено."); return false; }
        long actual = Math.min(toCall, p.chips);
        p.chips -= actual;
        p.contributed += actual;
        p.roundBet += actual;
        table.pot += actual;
        if (p.chips == 0) p.allIn = true;
        p.hasActedThisRound = true;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f);
        broadcastToTable(table, "§7" + p.name + " — §fколл " + actual + (p.allIn ? " §c(ва-банк)" : ""));
        advanceTurn(table);
        return true;
    }

    public boolean raise(Player player, long amount) {
        PokerTable table = getPlayerTable(player.getUniqueId());
        if (table == null || table.currentTurn == null) return false;
        if (table.dealing) { player.sendMessage("§cКарты ещё раздаются."); return false; }
        if (!table.currentTurn.equals(player.getUniqueId())) { player.sendMessage("§cНе ваш ход."); return false; }
        PokerTable.Player p = table.getPlayer(player.getUniqueId());
        long minTotal = table.highestBet + table.bigBlind;
        if (amount < minTotal && amount < p.chips + p.roundBet) {
            player.sendMessage("§cМинимальный рейз: " + minTotal + " (или ва-банк).");
            return false;
        }
        long needed = amount - p.roundBet;
        if (needed > p.chips) { player.sendMessage("§cНедостаточно чипов."); return false; }
        p.chips -= needed;
        p.contributed += needed;
        p.roundBet = amount;
        table.pot += needed;
        if (p.chips == 0) p.allIn = true;
        table.highestBet = Math.max(table.highestBet, p.roundBet);
        table.lastAggressor = p.uuid;
        p.hasActedThisRound = true;
        for (PokerTable.Player other : table.players.values()) {
            if (other.uuid.equals(p.uuid)) continue;
            if (!other.folded && !other.allIn) other.hasActedThisRound = false;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        broadcastToTable(table, "§a" + p.name + " — §fрейз до " + amount + (p.allIn ? " §c(ва-банк)" : ""));
        advanceTurn(table);
        return true;
    }

    public boolean allIn(Player player) {
        PokerTable table = getPlayerTable(player.getUniqueId());
        if (table == null || table.currentTurn == null) return false;
        if (table.dealing) { player.sendMessage("§cКарты ещё раздаются."); return false; }
        if (!table.currentTurn.equals(player.getUniqueId())) { player.sendMessage("§cНе ваш ход."); return false; }
        PokerTable.Player p = table.getPlayer(player.getUniqueId());
        long amount = p.chips + p.roundBet;
        p.roundBet = amount;
        p.contributed += p.chips;
        table.pot += p.chips;
        p.chips = 0;
        p.allIn = true;
        p.hasActedThisRound = true;
        if (amount > table.highestBet) {
            table.highestBet = amount;
            table.lastAggressor = p.uuid;
            for (PokerTable.Player other : table.players.values()) {
                if (other.uuid.equals(p.uuid)) continue;
                if (!other.folded && !other.allIn) other.hasActedThisRound = false;
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.5f);
        broadcastToTable(table, "§c" + p.name + " — §4§lВА-БАНК " + amount + "!");
        advanceTurn(table);
        return true;
    }

    public boolean fold(Player player) {
        PokerTable table = getPlayerTable(player.getUniqueId());
        if (table == null || table.currentTurn == null) return false;
        if (table.dealing) { player.sendMessage("§cКарты ещё раздаются."); return false; }
        if (!table.currentTurn.equals(player.getUniqueId())) { player.sendMessage("§cНе ваш ход."); return false; }
        PokerTable.Player p = table.getPlayer(player.getUniqueId());
        p.folded = true;
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
        broadcastToTable(table, "§8" + p.name + " — пас");
        advanceTurn(table);
        return true;
    }

    private void advanceTurn(PokerTable table) {
        if (table.activePlayerCount() <= 1) {
            PokerTable.Player last = table.players.values().stream().filter(p -> !p.folded).findFirst().orElse(null);
            if (last != null) awardUncontested(table, last);
            return;
        }
        if (isRoundComplete(table)) { endRound(table); return; }
        int currentIdx = table.indexOf(table.currentTurn);
        PokerTable.Player next = table.nextActivePlayer(currentIdx);
        if (next == null) { endRound(table); return; }
        table.currentTurn = next.uuid;
        table.turnStartTime = System.currentTimeMillis();
        broadcastTurn(table);
        refreshGUIs(table);
    }

    private boolean isRoundComplete(PokerTable table) {
        for (PokerTable.Player p : table.players.values()) {
            if (p.folded || p.allIn) continue;
            if (!p.hasActedThisRound) return false;
            if (p.roundBet < table.highestBet) return false;
        }
        return true;
    }

    private void endRound(PokerTable table) {
        for (PokerTable.Player p : table.players.values()) {
            p.roundBet = 0;
            p.hasActedThisRound = false;
        }
        table.highestBet = 0;
        table.lastAggressor = null;
        switch (table.stage) {
            case PREFLOP -> revealCommunity(table, 3, PokerTable.Stage.FLOP, "ФЛОП");
            case FLOP -> revealCommunity(table, 1, PokerTable.Stage.TURN, "ТЁРН");
            case TURN -> revealCommunity(table, 1, PokerTable.Stage.RIVER, "РИВЕР");
            case RIVER -> startShowdown(table);
            default -> { }
        }
    }

    private void revealCommunity(PokerTable table, int count, PokerTable.Stage newStage, String stageName) {
        table.stage = newStage;
        List<PokerHand.Card> pending = pendingCommunity.get(table.id);
        if (pending == null) { startBettingRound(table); return; }

        List<PokerHand.Card> newCards = new ArrayList<>();
        int targetSize = table.community.size() + count;
        for (int i = table.community.size(); i < targetSize && i < pending.size(); i++) newCards.add(pending.get(i));

        broadcastToTable(table, "§6§l═══ " + stageName + " ═══");

        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (!tables.containsKey(table.id)) { cancel(); return; }
                if (step < newCards.size()) {
                    table.community.add(null);
                    table.revealingIndex = table.community.size() - 1;
                    refreshGUIs(table);
                    for (PokerTable.Player p : table.players.values()) {
                        Player pl = Bukkit.getPlayer(p.uuid);
                        if (pl != null) pl.playSound(pl.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
                    }
                    step++;
                    return;
                }
                for (int i = 0; i < newCards.size(); i++) {
                    int idx = table.community.size() - newCards.size() + i;
                    table.community.set(idx, newCards.get(i));
                }
                table.revealingIndex = -1;
                refreshGUIs(table);
                for (PokerTable.Player p : table.players.values()) {
                    Player pl = Bukkit.getPlayer(p.uuid);
                    if (pl != null) pl.playSound(pl.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.3f);
                }
                broadcastToTable(table, "§7Общие карты: " + formatCommunity(table));
                cancel();
                Bukkit.getScheduler().runTaskLater(plugin, () -> startBettingRound(table), 10L);
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    private String formatCommunity(PokerTable table) {
        StringBuilder sb = new StringBuilder();
        for (PokerHand.Card c : table.community) {
            if (c == null) sb.append("§7[рубашка]  ");
            else sb.append(c.display()).append("  ");
        }
        return sb.toString().trim();
    }

    private void startBettingRound(PokerTable table) {
        List<PokerTable.Player> list = table.orderedPlayers();
        int n = list.size();
        int startIdx = (table.dealerIndex + 1) % n;
        PokerTable.Player first = null;
        for (int i = 0; i < n; i++) {
            PokerTable.Player cand = list.get((startIdx + i) % n);
            if (!cand.folded && !cand.allIn) { first = cand; break; }
        }
        if (first == null) { endRound(table); return; }
        table.currentTurn = first.uuid;
        table.turnStartTime = System.currentTimeMillis();
        broadcastTurn(table);
        refreshGUIs(table);
    }

    private void startShowdown(PokerTable table) {
        table.stage = PokerTable.Stage.SHOWDOWN;
        table.currentTurn = null;
        broadcastToTable(table, "§6§l═══ ВСКРЫТИЕ ═══");

        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("§6═══ Итоги раздачи #" + table.handNumber + " ═══");

        for (PokerTable.Player p : table.players.values()) {
            if (p.folded) { summaryLines.add("§8" + p.name + " — спасовал"); continue; }
            List<PokerHand.Card> seven = new ArrayList<>(p.hole);
            for (PokerHand.Card c : table.community) if (c != null) seven.add(c);
            int[] eval = PokerHand.evaluateBest(seven);
            StringBuilder sb = new StringBuilder("§e" + p.name + ": ");
            for (PokerHand.Card c : p.hole) sb.append(c.display()).append(" ");
            sb.append("§7— §f").append(PokerHand.handName(eval));
            broadcastToTable(table, sb.toString());
            summaryLines.add("§e" + p.name + "§7: " + handNameStr(p.hole) + " §7→ §f" + PokerHand.handName(eval));
        }

        Map<UUID, Long> profitsThisHand = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (PokerTable.Player p : table.players.values()) {
            long startChips = table.chipsAtHandStart.getOrDefault(p.uuid, p.chips);
            profitsThisHand.put(p.uuid, p.chips - startChips);
            names.put(p.uuid, p.name);
        }

        List<String> winnerLines = new ArrayList<>();
        long totalPot = table.pot;
        UUID winnerUuid = distributePotsAndTrack(table, winnerLines);
        String winnerName = winnerUuid != null ? names.getOrDefault(winnerUuid, "?") : "?";

        summaryLines.addAll(winnerLines);
        for (PokerTable.Player p : table.players.values()) {
            long startChips = table.chipsAtHandStart.getOrDefault(p.uuid, p.chips);
            profitsThisHand.put(p.uuid, p.chips - startChips);
        }

        String finalHandName = "—";
        if (winnerUuid != null) {
            PokerTable.Player wp = table.getPlayer(winnerUuid);
            if (wp != null) {
                List<PokerHand.Card> seven = new ArrayList<>(wp.hole);
                for (PokerHand.Card c : table.community) if (c != null) seven.add(c);
                finalHandName = PokerHand.handName(PokerHand.evaluateBest(seven));
            }
        }

        plugin.getPokerHistory().addHand(
                new PokerHistory.Record(System.currentTimeMillis(), winnerUuid != null ? winnerUuid : UUID.randomUUID(),
                        winnerName, totalPot, finalHandName, summaryLines),
                profitsThisHand, names);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int alive = 0;
            PokerTable.Player lastAlive = null;
            for (PokerTable.Player p : table.players.values()) {
                if (p.chips > 0) { alive++; lastAlive = p; }
            }
            if (alive <= 1) {
                table.stage = PokerTable.Stage.FINISHED;
                if (lastAlive != null) {
                    broadcastToTable(table, "§6§l🏆 Победитель: " + lastAlive.name + "! Забирает " +
                            plugin.getEconomyManager().format(lastAlive.chips));
                    plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(lastAlive.uuid), lastAlive.chips);
                    Player wp = Bukkit.getPlayer(lastAlive.uuid);
                    if (wp != null) {
                        wp.showTitle(Title.title(
                                Component.text("🏆 ПОБЕДА!", NamedTextColor.GOLD),
                               
