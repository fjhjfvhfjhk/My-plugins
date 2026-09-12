package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Crash — множитель растёт, игрок должен забрать до взрыва.
 *
 * Баланс:
 *   RTP ≈ 95%
 *   crash = max(1.00, 0.95 / (1 - rand))
 *   3% шанс мгновенного краха на 1.00x
 *   Рост: mult(t) = 1.00 + 0.1·t + 0.003·t²   (t в секундах)
 *   Обновление GUI: каждые 5 тиков (0.25 сек)
 */
public class CrashManager {

    private static final double CRASH_EDGE = 0.95;
    private static final int TICK_INTERVAL = 5;

    private final RollPlugin plugin;
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Random random = new Random();

    public CrashManager(RollPlugin plugin) { this.plugin = plugin; }

    public Game getGame(UUID uuid) { return games.get(uuid); }
    public boolean isActive(UUID uuid) {
        Game g = games.get(uuid);
        return g != null && !g.finished;
    }

    public boolean start(Player player, double bet) {
        if (isActive(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже есть активная игра Crash.");
            return false;
        }
        if (bet <= 0) { player.sendMessage("§cСумма должна быть положительной."); return false; }

        double minBet = plugin.getConfig().getDouble("crash.min-bet", 0);
        double maxBet = plugin.getConfig().getDouble("crash.max-bet", 0);
        if (minBet > 0 && bet < minBet) {
            player.sendMessage("§cМинимальная ставка: " + plugin.getEconomyManager().format(minBet));
            return false;
        }
        if (maxBet > 0 && bet > maxBet) {
            player.sendMessage("§cМаксимальная ставка: " + plugin.getEconomyManager().format(maxBet));
            return false;
        }

        if (!plugin.getEconomyManager().withdraw(player, bet)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(bet)));
            return false;
        }

        double crashPoint = calculateCrashPoint();
        Game game = new Game(player.getUniqueId(), bet, crashPoint);
        games.put(player.getUniqueId(), game);

        player.sendMessage("§6🚀 Crash! Ставка: §f" + plugin.getEconomyManager().format(bet));
        player.sendMessage("§eЗабирайте, пока ракета не взорвалась!");
        playSound(player, "crash-start");

        startTick(player, game);
        return true;
    }

    private double calculateCrashPoint() {
        double r = random.nextDouble();
        if (r < 0.03) return 1.00;
        double crash = CRASH_EDGE / (1.0 - r);
        if (crash < 1.00) crash = 1.00;
        return Math.round(crash * 100.0) / 100.0;
    }

    private void startTick(Player player, Game game) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (game.finished) {
                    cancel();
                    tasks.remove(player.getUniqueId());
                    return;
                }
                if (!player.isOnline()) {
                    // Игрок вышел — если множитель уже вырос, отдаём по нему.
                    // Иначе крах.
                    if (game.currentMultiplier > 1.01) cashoutSilent(player, game);
                    else finishCrash(player, game);
                    cancel();
                    tasks.remove(player.getUniqueId());
                    return;
                }

                long elapsed = System.currentTimeMillis() - game.startTime;
                double seconds = elapsed / 1000.0;
                double newMult = 1.00 + 0.1 * seconds + 0.003 * seconds * seconds;

                if (newMult >= game.crashPoint) {
                    game.currentMultiplier = game.crashPoint;
                    finishCrash(player, game);
                    cancel();
                    tasks.remove(player.getUniqueId());
                    return;
                }

                game.currentMultiplier = newMult;
                CrashGUI.updateAllOpen();
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);

        tasks.put(player.getUniqueId(), task);
    }

    public boolean cashout(Player player) {
        Game game = games.get(player.getUniqueId());
        if (game == null || game.finished) return false;
        if (game.currentMultiplier <= 1.01) {
            player.sendMessage("§cСлишком рано — множитель ещё ~1.00x.");
            return false;
        }
        cashoutSilent(player, game);
        return true;
    }

    private void cashoutSilent(Player player, Game game) {
        double payout = game.bet * game.currentMultiplier;
        plugin.getEconomyManager().deposit(player, payout);
        game.finished = true;
        game.won = true;
        game.payout = payout;

        if (player.isOnline()) {
            player.sendMessage("§a🚀 Забрали на §e" + String.format("%.2fx", game.currentMultiplier) +
                    "§a! Выигрыш: §f" + plugin.getEconomyManager().format(payout));
            playSound(player, "crash-win");
        }
        plugin.getRollData().recordResult(player, "crash", game.bet, payout, true);
        CrashGUI.updateAllOpen();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            games.remove(player.getUniqueId());
            if (player.isOnline()) player.closeInventory();
        }, 60L);
    }

    private void finishCrash(Player player, Game game) {
        game.finished = true;
        game.won = false;

        if (player.isOnline()) {
            player.sendMessage("§c💥 CRASH на §f" + String.format("%.2fx", game.crashPoint) +
                    "§c! Потеряно: §f" + plugin.getEconomyManager().format(game.bet));
            playSound(player, "crash-fail");
        }
        plugin.getRollData().recordResult(player, "crash", game.bet, 0, false);
        CrashGUI.updateAllOpen();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            games.remove(player.getUniqueId());
            if (player.isOnline()) player.closeInventory();
        }, 60L);
    }

    public void clearGame(UUID uuid) {
        BukkitTask t = tasks.remove(uuid);
        if (t != null) t.cancel();
        games.remove(uuid);
    }

    /** Отмена всех активных игр — для onDisable. */
    public void shutdown() {
        for (BukkitTask t : tasks.values()) {
            if (t != null) t.cancel();
        }
        tasks.clear();
        games.clear();
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfig().getString("sounds." + key);
        if (soundName == null) return;
        try {
            Sound s = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), s, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    public static class Game {
        public final UUID playerId;
        public final double bet;
        public final double crashPoint;
        public final long startTime;
        public double currentMultiplier = 1.00;
        public boolean finished = false;
        public boolean won = false;
        public double payout = 0;

        public Game(UUID playerId, double bet, double crashPoint) {
            this.playerId = playerId;
            this.bet = bet;
            this.crashPoint = crashPoint;
            this.startTime = System.currentTimeMillis();
        }
    }
}
