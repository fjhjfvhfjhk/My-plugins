package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class RollCommand implements CommandExecutor, TabCompleter {

    private final RollPlugin plugin;
    private final RollGUI gui;
    private final RollDuelGUI duelGUI;
    private final MinesGUI minesGUI;
    private final RollGamesGUI gamesGUI;
    private final SlotsGUI slotsGUI;
    private final WheelGUI wheelGUI;
    private final StairsGUI stairsGUI;

    public RollCommand(RollPlugin plugin, RollGUI gui, RollDuelGUI duelGUI, MinesGUI minesGUI,
                       RollGamesGUI gamesGUI, SlotsGUI slotsGUI, WheelGUI wheelGUI, StairsGUI stairsGUI) {
        this.plugin = plugin;
        this.gui = gui;
        this.duelGUI = duelGUI;
        this.minesGUI = minesGUI;
        this.gamesGUI = gamesGUI;
        this.slotsGUI = slotsGUI;
        this.wheelGUI = wheelGUI;
        this.stairsGUI = stairsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }

        if (args.length == 0) { gamesGUI.open(player); return true; }

        switch (args[0].toLowerCase()) {
            case "menu", "games" -> { gamesGUI.open(player); return true; }
            case "classic", "roulette" -> { gui.open(player); return true; }
            case "stats" -> { showStats(player); return true; }
            case "jackpot" -> {
                player.sendMessage("§d💰 Джекпот-банк: §f" +
                        plugin.getEconomyManager().format(plugin.getRollData().getJackpot()));
                player.sendMessage("§7Шанс сорвать: §f0.1% §7с каждой победы.");
                return true;
            }
            case "bet" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /roll bet <сумма>"); return true; }
                Double amount = parseAmount(player, args[1]);
                if (amount == null) return true;
                plugin.getGameManager().addBet(player, amount);
                return true;
            }
            case "raise", "add" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /roll raise <сумма>"); return true; }
                Double amount = parseAmount(player, args[1]);
                if (amount == null) return true;
                plugin.getGameManager().addToBet(player, amount);
                return true;
            }
            case "duel" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /roll duel <сумма>"); return true; }
                Double amount = parseAmount(player, args[1]);
                if (amount == null) return true;
                plugin.getDuelManager().startDuel(player, amount);
                duelGUI.open(player);
                return true;
            }
            case "mines" -> {
                if (args.length < 4) {
                    player.sendMessage("§cИспользование: /roll mines <ставка> <мины> <размер>");
                    return true;
                }
                Double amount = parseAmount(player, args[1]);
                if (amount == null) return true;
                int minesCount, gridSize;
                try { minesCount = Integer.parseInt(args[2]); } catch (NumberFormatException e) { player.sendMessage("§cМины — число."); return true; }
                try { gridSize = Integer.parseInt(args[3]); } catch (NumberFormatException e) { player.sendMessage("§cРазмер — число (3,4,5)."); return true; }
                if (plugin.getMinesManager().startGame(player, amount, minesCount, gridSize)) minesGUI.open(player);
                return true;
            }
            case "slots" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /roll slots <ставка>"); return true; }
                Double amount = parseAmount(player, args[1]);
                if (amount == null) return true;
                if (plugin.getSlotsManager().start(player, amount)) slotsGUI.open(player);
                return true;
            }
            case "wheel" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /roll wheel <ставка>"); return true; }
                Double amount = parseAmount(player, args[1]);
                if (amount == null) return true;
                if (plugin.getWheelManager().start(player, amount)) wheelGUI.open(player);
                return true;
            }
            case "stairs" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /roll stairs <ставка>"); return true; }
                Double amount = parseAmount(player, args[1]);
                if (amount == null) return true;
                if (plugin.getStairsManager().start(player, amount)) stairsGUI.open(player);
                return true;
            }
            case "poker" -> { return handlePoker(player, args); }
            case "info" -> {
                GameManager gm = plugin.getGameManager();
                if (!gm.isActive()) { player.sendMessage("§cИгра не активна."); return true; }
                player.sendMessage("§6=== Рулетка ===");
                player.sendMessage("§7Ставок: §f" + gm.getBetCount());
                player.sendMessage("§7Общий банк: §f" + plugin.getEconomyManager().format(gm.getTotalPool()));
                if (gm.hasBet(player.getUniqueId())) {
                    player.sendMessage("§7Ваша ставка: §f" + plugin.getEconomyManager().format(gm.getBets().get(player.getUniqueId())));
                    player.sendMessage("§7Ваш шанс: §e" + gm.getChanceFormatted(player.getUniqueId()));
                }
                return true;
            }
            case "top" -> { showTop(player); return true; }
            case "finish" -> {
                if (!player.hasPermission("roll.admin")) {
                    GameManager gm = plugin.getGameManager();
                    UUID first = gm.getFirstBetter();
                    if (first == null || !first.equals(player.getUniqueId())) {
                        player.sendMessage("§cУ вас нет прав.");
                        return true;
                    }
                }
                plugin.getGameManager().forceFinish(player);
                return true;
            }
            case "cancel" -> {
                if (!player.hasPermission("roll.admin")) { player.sendMessage("§cУ вас нет прав."); return true; }
                plugin.getGameManager().cancelGame(true);
                player.sendMessage("§aИгра отменена, деньги возвращены игрокам.");
                return true;
            }
            default -> {
                player.sendMessage("§cНеизвестная подкоманда. Откройте меню: §f/roll");
                return true;
            }
        }
    }

    private void showStats(Player player) {
        Map<String, RollData.Stats> all = plugin.getRollData().getAllStats(player.getUniqueId());
        player.sendMessage("§6§l═══ Ваша статистика ═══");
        if (all.isEmpty()) {
            player.sendMessage("§7Вы ещё не играли.");
            player.sendMessage("§7Откройте меню: §f/roll");
            return;
        }

        RollData.Stats total = new RollData.Stats();
        for (int i = 0; i < RollData.GAME_KEYS.length; i++) {
            String key = RollData.GAME_KEYS[i];
            RollData.Stats s = all.get(key);
            if (s == null || s.gamesPlayed == 0) continue;

            player.sendMessage("§e§l" + RollData.GAME_NAMES[i]);
            player.sendMessage("§7  Игр: §f" + s.gamesPlayed + " §7| Побед: §f" + s.wins +
                    " §7(§a" + String.format("%.1f", s.getWinRate()) + "%§7)");
            player.sendMessage("§7  Поставлено: §f" + plugin.getEconomyManager().format(s.totalBet) +
                    " §7| Получено: §f" + plugin.getEconomyManager().format(s.totalWon));
            String profitColor = s.getProfit() >= 0 ? "§a" : "§c";
            String sign = s.getProfit() >= 0 ? "+" : "";
            player.sendMessage("§7  Профит: " + profitColor + sign + plugin.getEconomyManager().format(s.getProfit()) +
                    " §7| Рекорд: §f" + plugin.getEconomyManager().format(s.biggestWin));

            total.gamesPlayed += s.gamesPlayed;
            total.wins += s.wins;
            total.totalBet += s.totalBet;
            total.totalWon += s.totalWon;
            if (s.biggestWin > total.biggestWin) total.biggestWin = s.biggestWin;
        }

        player.sendMessage("§6§l── ИТОГО ──");
        player.sendMessage("§7Всего игр: §f" + total.gamesPlayed + " §7| Побед: §f" + total.wins +
                " §7(§a" + String.format("%.1f", total.getWinRate()) + "%§7)");
        String profitColor = total.getProfit() >= 0 ? "§a" : "§c";
        String sign = total.getProfit() >= 0 ? "+" : "";
        player.sendMessage("§7Общий профит: " + profitColor + sign + plugin.getEconomyManager().format(total.getProfit()));
        player.sendMessage("§7Лучший выигрыш: §f" + plugin.getEconomyManager().format(total.biggestWin));
    }

    private boolean handlePoker(Player player, String[] args) {
        PokerManager pm = plugin.getPokerManager();
        if (args.length < 2) {
            player.sendMessage("§6=== Покер (Техасский Холдем) ===");
            player.sendMessage("§f/roll poker tables §7— список столов (GUI)");
            player.sendMessage("§f/roll poker create <бай-ин> §7— создать стол");
            player.sendMessage("§f/roll poker join <id> §7— присоединиться");
            player.sendMessage("§f/roll poker start §7— начать игру");
            player.sendMessage("§f/roll poker leave §7— покинуть стол");
            player.sendMessage("§f/roll poker raise <сумма> §7— рейз");
            player.sendMessage("§f/roll poker top §7— лидерборд за неделю");
            player.sendMessage("§f/roll poker history §7— последние раздачи");
            player.sendMessage("§f/roll poker rules §7— правила");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "tables", "list" -> { plugin.getPokerTablesGUI().open(player); return true; }
            case "create" -> {
                if (args.length < 3) { player.sendMessage("§cИспользование: /roll poker create <бай-ин>"); return true; }
                double buyIn;
                try { buyIn = Double.parseDouble(args[2]); if (buyIn <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) { player.sendMessage("§cВведите положительное число."); return true; }
                pm.createTable(player, (long) buyIn);
                return true;
            }
            case "join" -> {
                if (args.length < 3) { player.sendMessage("§cИспользование: /roll poker join <id>"); return true; }
                int id;
                try { id = Integer.parseInt(args[2]); } catch (NumberFormatException e) { player.sendMessage("§cID должен быть числом."); return true; }
                pm.joinTable(player, id);
                return true;
            }
            case "start" -> { pm.startGame(player); return true; }
            case "leave" -> { pm.leaveTable(player); return true; }
            case "raise" -> {
                if (args.length < 3) { player.sendMessage("§cИспользование: /roll poker raise <сумма>"); return true; }
                double amount;
                try { amount = Double.parseDouble(args[2]); if (amount <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) { player.sendMessage("§cВведите положительное число."); return true; }
                pm.raise(player, (long) amount);
                return true;
            }
            case "top" -> { showPokerTop(player); return true; }
            case "history" -> { showPokerHistory(player); return true; }
            case "rules" -> {
                player.sendMessage("§6=== Правила Техасского Холдема ===");
                player.sendMessage("§7Цель: собрать лучшую руку из §f2 своих§7 и §f5 общих§7 карт.");
                player.sendMessage("§7Раунды: префлоп → флоп → тёрн → ривер → вскрытие");
                player.sendMessage("§7Комбинации (сильные → слабые):");
                player.sendMessage("§7Флеш-рояль > стрит-флеш > каре > фулл-хаус > флеш > стрит > сет > две пары > пара > старшая");
                return true;
            }
            default -> { player.sendMessage("§cНеизвестная подкоманда. /roll poker"); return true; }
        }
    }

    private void showPokerTop(Player player) {
        List<PokerHistory.PlayerProfit> top = plugin.getPokerHistory().getTop(10);
        player.sendMessage("§6§l═══ ТОП ИГРОКОВ ПОКЕРА (за 7 дней) ═══");
        if (top.isEmpty()) { player.sendMessage("§7Пока нет данных за неделю."); return; }
        int rank = 1;
        for (PokerHistory.PlayerProfit pp : top) {
            String color = pp.profit >= 0 ? "§a" : "§c";
            String sign = pp.profit >= 0 ? "+" : "";
            player.sendMessage("§e#" + rank + " §f" + pp.name + " §7— " + color + sign +
                    plugin.getEconomyManager().format(pp.profit) + " §7(" + pp.handsPlayed + " раздач)");
            rank++;
        }
    }

    private void showPokerHistory(Player player) {
        List<PokerHistory.Record> history = plugin.getPokerHistory().getRecent(5);
        player.sendMessage("§6§l═══ Последние раздачи ═══");
        if (history.isEmpty()) { player.sendMessage("§7Пока нет истории."); return; }
        long now = System.currentTimeMillis();
        for (PokerHistory.Record r : history) {
            long minAgo = (now - r.timestamp) / 60_000L;
            String timeAgo = minAgo < 1 ? "только что"
                    : (minAgo < 60 ? minAgo + " мин назад" : (minAgo / 60) + " ч назад");
            player.sendMessage("§7[" + timeAgo + "] §e" + r.winnerName +
                    " §7забрал §f" + plugin.getEconomyManager().format(r.potSize) +
                    " §7(" + r.handName + ")");
        }
    }

    private Double parseAmount(Player player, String str) {
        try {
            double amount = Double.parseDouble(str);
            if (amount <= 0) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException e) {
            player.sendMessage("§cСумма должна быть положительным числом.");
            return null;
        }
    }

    private void showTop(Player player) {
        RollHistory history = plugin.getGameManager().getHistory();
        Map<UUID, RollHistory.PlayerStats> top = history.getTopPlayers(10);
        player.sendMessage("§6=== Топ игроков по победам в рулетке ===");
        if (top.isEmpty()) { player.sendMessage("§7Пока нет данных."); return; }
        int rank = 1;
        for (Map.Entry<UUID, RollHistory.PlayerStats> entry : top.entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String name = op.getName() != null ? op.getName() : entry.getKey().toString().substring(0, 8);
            RollHistory.PlayerStats stats = entry.getValue();
            player.sendMessage("§e#" + rank + " §f" + name + " §7— §6" + stats.wins + " побед, §e" +
                    plugin.getEconomyManager().format(stats.totalWon));
            rank++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("menu", "classic", "stats", "jackpot", "bet", "raise",
                    "duel", "mines", "slots", "wheel", "stairs", "poker", "info", "top", "finish"));
            if (sender.hasPermission("roll.admin")) options.add("cancel");
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("poker")) {
            return List.of("tables", "create", "join", "start", "leave", "raise", "top", "history", "rules").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("mines")) return List.of("3", "4", "5");
        return List.of();
    }
}
