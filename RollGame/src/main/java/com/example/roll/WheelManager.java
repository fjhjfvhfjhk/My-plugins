package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Колесо Фортуны.
 *
 * Изменения (v2.1):
 *  - пересчитаны множители секторов под RTP ≈ 95.8%;
 *  - 12 секторов: 5×0x, 2×0.5x, 3×1x, 1×2.5x, 1×5x.
 */
public class WheelManager {

    /** Множители 12 секторов колеса. RTP = 11.5/12 ≈ 95.8%. */
    public static final double[] SECTORS = {
            0.0, 0.0, 0.0, 0.0, 0.0,   // 5× 0x — проигрыш
            0.5, 0.5,                   // 2× 0.5x — малый возврат
            1.0, 1.0, 1.0,              // 3× 1x — возврат ставки
            2.5,                        // 1× 2.5x
            5.0                         // 1× 5x — джекпот
    };

    private final RollPlugin plugin;
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Random random = new Random();

    public WheelManager(RollPlugin plugin) { this.plugin = plugin; }

    public Game getGame(UUID uuid) { return games.get(uuid); }
    public boolean isActive(UUID uuid) {
        Game g = games.get(uuid);
        return g != null && !g.finished;
    }

    public boolean start(Player player, double bet) {
        if (isActive(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже есть активная игра в колесо.");
            return false;
        }
        if (bet <= 0) { player.sendMessage("§cСумма должна быть положительной."); return false; }
        double minBet = plugin.getConfig().getDouble("wheel.min-bet", 0);
        double maxBet = plugin.getConfig().getDouble("wheel.max-bet", 0);
        if (minBet > 0 && bet < minBet) { player.sendMessage("§cМинимальная ставка: " + plugin.getEconomyManager().format(minBet)); return false; }
        if (maxBet > 0 && bet > maxBet) { player.sendMessage("§cМаксимальная ставка: " + plugin.getEconomyManager().format(maxBet)); return false; }

        if (!plugin.getEconomyManager().withdraw(player, bet)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(bet)));
            return false;
        }

        UUID uuid = player.getUniqueId();
        Game game = new Game(uuid, bet);
        int targetIndex = random.nextInt(SECTORS.length);
        game.targetIndex = targetIndex;
        game.targetMultiplier = SECTORS[targetIndex];

        // Строим последовательность из случайных секторов + гарантированный target в конце.
        // maxShift = size - 5, чтобы при "текущем" индексе i=4 сектор совпал с targetIndex.
        int sequenceLength = 96;
        for (int i = 0; i < sequenceLength; i++) game.sequence.add(random.nextInt(SECTORS.length));
        game.sequence.add(targetIndex);
        game.maxShift = game.sequence.size() - 5;

        games.put(uuid, game);
        playSound(player, "wheel-spin");
        WheelGUI.updateAllOpen();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Game g = games.get(uuid);
                if (g == null || g.finished) { this.cancel(); tasks.remove(uuid); return; }
                updateAnimation(player, g);
                if (g.finished) { this.cancel(); tasks.remove(uuid); }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        tasks.put(uuid, task);
        return true;
    }

    private void updateAnimation(Player player, Game game) {
        game.animationTicks++;
        int duration = plugin.getConfig().getInt("wheel.animation-ticks", 60);

        if (game.animationTicks >= duration) {
            game.currentShift = game.maxShift;
            finishGame(player, game);
            return;
        }
        double progress = (double) game.animationTicks / duration;
        double eased = 1 - Math.pow(1 - progress, 3);
        game.currentShift = (int) Math.round(eased * game.maxShift);
        if (game.currentShift > game.maxShift) game.currentShift = game.maxShift;
        WheelGUI.updateAllOpen();
    }

    private void finishGame(Player player, Game game) {
        game.finished = true;
        double multiplier = game.targetMultiplier;
        double winAmount = game.bet * multiplier;

        if (multiplier > 0) {
            double commissionPercent = plugin.getConfig().getDouble("wheel.commission-percent", 0);
            double commission = winAmount * commissionPercent / 100.0;
            double payout = winAmount - commission;
            plugin.getEconomyManager().deposit(player, payout);
            if (commission > 0) depositCommission(player.getUniqueId(), commission);
            player.sendMessage("§d🎡 Выпало: §e" + formatMultiplier(multiplier) +
                    "§d! Выигрыш: §f" + plugin.getEconomyManager().format(payout));
            playSound(player, multiplier >= 3 ? "wheel-jackpot" : "wheel-win");
            plugin.getRollData().recordResult(player, "wheel", game.bet, payout, true);
        } else {
            double commissionPercent = plugin.getConfig().getDouble("wheel.commission-percent", 0);
            double commission = game.bet * commissionPercent / 100.0;
            if (commission > 0) depositCommission(player.getUniqueId(), commission);
            player.sendMessage("§c🎡 Выпало: §40x§c! Вы проиграли §f" +
                    plugin.getEconomyManager().format(game.bet));
            playSound(player, "wheel-lose");
            plugin.getRollData().recordResult(player, "wheel", game.bet, 0, false);
        }
        WheelGUI.updateAllOpen();
    }

    public void clearGame(UUID uuid) {
        tasks.remove(uuid);
        games.remove(uuid);
    }

    private String formatMultiplier(double m) {
        if (m == Math.floor(m)) return (int) m + "x";
        return String.format("%.1fx", m);
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfig().getString("sounds." + key);
        if (soundName == null) return;
        try {
            Sound s = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), s, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    private void depositCommission(UUID uuid, double amount) {
        try {
            Object sovereignty = Bukkit.getPluginManager().getPlugin("Sovereignty");
            if (sovereignty != null) {
                Object cm = sovereignty.getClass().getMethod("getCountryManager").invoke(sovereignty);
                String cName = (String) cm.getClass().getMethod("getCountryName", UUID.class).invoke(cm, uuid);
                if (cName != null) cm.getClass().getMethod("depositToBank", String.class, double.class).invoke(cm, cName, amount);
            }
        } catch (Exception ignored) {}
    }

    public static class Game {
        public final UUID playerId;
        public final double bet;
        public final List<Integer> sequence = new ArrayList<>();
        public int targetIndex;
        public double targetMultiplier;
        public int maxShift;
        public int currentShift = 0;
        public int animationTicks = 0;
        public boolean finished = false;
        public Game(UUID playerId, double bet) {
            this.playerId = playerId;
            this.bet = bet;
        }
    }
}
