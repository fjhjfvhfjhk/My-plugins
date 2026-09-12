package com.example.roll;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * Мины.
 *
 * Изменения (v2.1):
 *  - house edge увеличен: 0.97 → 0.93 (RTP ≈ 93%).
 */
public class MinesManager {

    private final RollPlugin plugin;
    private final Map<UUID, MinesGame> games = new HashMap<>();

    public MinesManager(RollPlugin plugin) { this.plugin = plugin; }

    public MinesGame getGame(UUID uuid) { return games.get(uuid); }
    public boolean hasActiveGame(UUID uuid) {
        MinesGame game = games.get(uuid);
        return game != null && !game.gameOver;
    }

    public boolean startGame(Player player, double bet, int minesCount, int gridSize) {
        if (hasActiveGame(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже есть активная игра. Завершите или заберите выигрыш.");
            return false;
        }
        if (bet <= 0) { player.sendMessage("§cСумма должна быть положительной."); return false; }
        if (gridSize < 3 || gridSize > 5) { player.sendMessage("§cРазмер сетки: 3, 4 или 5."); return false; }
        int totalCells = gridSize * gridSize;
        if (minesCount < 1 || minesCount > totalCells - 2) {
            player.sendMessage("§cКоличество мин: от 1 до " + (totalCells - 2) + ".");
            return false;
        }

        double minBet = plugin.getConfig().getDouble("mines.min-bet", 0);
        double maxBet = plugin.getConfig().getDouble("mines.max-bet", 0);
        if (minBet > 0 && bet < minBet) { player.sendMessage("§cМинимальная ставка: " + plugin.getEconomyManager().format(minBet)); return false; }
        if (maxBet > 0 && bet > maxBet) { player.sendMessage("§cМаксимальная ставка: " + plugin.getEconomyManager().format(maxBet)); return false; }

        if (!plugin.getEconomyManager().withdraw(player, bet)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(bet)));
            return false;
        }

        MinesGame game = new MinesGame(player.getUniqueId(), bet, minesCount, gridSize);
        games.put(player.getUniqueId(), game);

        player.sendMessage("§6🎮 Игра 'Мины' началась! Ставка: §f" + plugin.getEconomyManager().format(bet) +
                "§6, мин: §f" + minesCount + "§6, сетка: §f" + gridSize + "x" + gridSize);
        player.sendMessage("§eОткрывайте безопасные клетки. Нажмите 'Забрать' в любой момент.");
        return true;
    }

    public boolean revealCell(Player player, int cellIndex) {
        MinesGame game = games.get(player.getUniqueId());
        if (game == null || game.gameOver) return false;
        if (game.revealedSafe.contains(cellIndex)) return false;
        if (cellIndex < 0 || cellIndex >= game.totalCells) return false;

        if (game.minePositions.contains(cellIndex)) {
            game.gameOver = true;
            game.won = false;
            player.sendMessage("§c💥 Вы наткнулись на мину! Потеряно: " + plugin.getEconomyManager().format(game.bet));
            playSound(player, "mines-lose");
            plugin.getRollData().recordResult(player, "mines", game.bet, 0, false);
            return false;
        } else {
            game.revealedSafe.add(cellIndex);
            double mult = game.getMultiplier();
            player.sendMessage("§a✔ Безопасно! Множитель: §e" + String.format("%.2f", mult) + "x" +
                    "§a. Потенциальный выигрыш: §f" + plugin.getEconomyManager().format(game.getPotentialWin()));
            playSound(player, "mines-safe");
            return true;
        }
    }

    public boolean cashout(Player player) {
        MinesGame game = games.get(player.getUniqueId());
        if (game == null || game.gameOver) return false;
        if (game.revealedSafe.isEmpty()) {
            player.sendMessage("§cОткройте хотя бы одну клетку, чтобы забрать выигрыш.");
            return false;
        }

        double payout = game.getPotentialWin();

        plugin.getEconomyManager().deposit(player, payout);

        player.sendMessage("§a🎉 Вы забрали выигрыш: §f" + plugin.getEconomyManager().format(payout) +
                "§a (множитель: §e" + String.format("%.2f", game.getMultiplier()) + "x§a)");
        playSound(player, "win");

        game.gameOver = true;
        game.won = true;

        plugin.getRollData().recordResult(player, "mines", game.bet, payout, true);
        return true;
    }

    public void cancelGame(Player player) {
        MinesGame game = games.get(player.getUniqueId());
        if (game == null || game.gameOver) return;
        if (game.revealedSafe.isEmpty()) {
            plugin.getEconomyManager().deposit(player, game.bet);
            player.sendMessage("§eИгра отменена, ставка возвращена.");
        } else {
            cashout(player);
        }
    }

    public void clearGame(UUID uuid) { games.remove(uuid); }

    private void playSound(Player player, String type) {
        String soundName = plugin.getConfig().getString("sounds." + type);
        if (soundName == null) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    public static class MinesGame {
        public final UUID playerId;
        public final double bet;
        public final int minesCount;
        public final int gridSize;
        public final int totalCells;
        public final Set<Integer> minePositions;
        public final Set<Integer> revealedSafe = new HashSet<>();
        public boolean gameOver = false;
        public boolean won = false;
        public final long startTime;

        public MinesGame(UUID playerId, double bet, int minesCount, int gridSize) {
            this.playerId = playerId;
            this.bet = bet;
            this.minesCount = minesCount;
            this.gridSize = gridSize;
            this.totalCells = gridSize * gridSize;
            this.minePositions = generateMines(minesCount, totalCells);
            this.startTime = System.currentTimeMillis();
        }

        private static Set<Integer> generateMines(int count, int total) {
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < total; i++) indices.add(i);
            Collections.shuffle(indices, new Random());
            return new HashSet<>(indices.subList(0, count));
        }

        public double getMultiplier() {
            int k = revealedSafe.size();
            if (k == 0) return 1.0;
            double mult = 1.0;
            for (int i = 0; i < k; i++) mult *= (double) (totalCells - i) / (totalCells - minesCount - i);
            // House edge 7% (RTP ≈ 93%)
            mult *= 0.93;
            return mult;
        }

        public double getPotentialWin() { return bet * getMultiplier(); }
    }
}
