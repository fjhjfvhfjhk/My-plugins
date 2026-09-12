package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Лестница (Crash-style).
 *
 * Изменения (v2.1):
 *  - 12 ступеней вместо 10;
 *  - fail-chance 15% на каждой ступени (включая первую);
 *  - новые множители; RTP оптимальной игры ≈ 92.4%.
 */
public class StairsManager {

    /** 12 множителей: 1.10x → 6.50x. */
    public static final double[] MULTIPLIERS = {
            1.10, 1.25, 1.40, 1.60, 1.85, 2.15,
            2.50, 3.00, 3.60, 4.40, 5.40, 6.50
    };

    private final RollPlugin plugin;
    private final Map<UUID, Game> games = new HashMap<>();
    private final Random random = new Random();

    public StairsManager(RollPlugin plugin) { this.plugin = plugin; }

    public Game getGame(UUID uuid) { return games.get(uuid); }
    public boolean isActive(UUID uuid) {
        Game g = games.get(uuid);
        return g != null && !g.finished;
    }

    public boolean start(Player player, double bet) {
        if (isActive(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже есть активная игра в лестницу.");
            return false;
        }
        if (bet <= 0) { player.sendMessage("§cСумма должна быть положительной."); return false; }
        double minBet = plugin.getConfig().getDouble("stairs.min-bet", 0);
        double maxBet = plugin.getConfig().getDouble("stairs.max-bet", 0);
        if (minBet > 0 && bet < minBet) { player.sendMessage("§cМинимальная ставка: " + plugin.getEconomyManager().format(minBet)); return false; }
        if (maxBet > 0 && bet > maxBet) { player.sendMessage("§cМаксимальная ставка: " + plugin.getEconomyManager().format(maxBet)); return false; }

        if (!plugin.getEconomyManager().withdraw(player, bet)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(bet)));
            return false;
        }

        Game game = new Game(player.getUniqueId(), bet);
        games.put(player.getUniqueId(), game);
        player.sendMessage("§6🪜 Лестница началась! Ставка: §f" + plugin.getEconomyManager().format(bet));
        player.sendMessage("§eШагните на первую ступень, чтобы начать. Шанс провала: §f15%§e.");
        return true;
    }

    /**
     * Шаг на следующую ступень. Первый шаг тоже рисковый (fail-chance проверяется всегда).
     */
    public boolean step(Player player) {
        Game game = games.get(player.getUniqueId());
        if (game == null || game.finished) return false;
        if (game.currentStep >= MULTIPLIERS.length) { cashout(player); return true; }

        double failChance = plugin.getConfig().getDouble("stairs.fail-chance", 0.15);
        if (random.nextDouble() < failChance) {
            game.finished = true;
            player.sendMessage("§c💥 Вы упали на ступени " + (game.currentStep + 1) + "! Потеряно: §f" +
                    plugin.getEconomyManager().format(game.bet));
            playSound(player, "stairs-fall");
            plugin.getRollData().recordResult(player, "stairs", game.bet, 0, false);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                games.remove(player.getUniqueId());
                if (player.isOnline()) player.closeInventory();
            }, 60L);
            StairsGUI.updateAllOpen();
            return false;
        }

        game.currentStep++;
        playSound(player, "stairs-step");
        player.sendMessage("§a✔ Ступень §f" + game.currentStep +
                " §aпройдена! Множитель: §e" + formatMultiplier(MULTIPLIERS[game.currentStep - 1]) +
                " §a| Забрать: §f" + plugin.getEconomyManager().format(game.bet * MULTIPLIERS[game.currentStep - 1]));
        StairsGUI.updateAllOpen();
        return true;
    }

    public boolean cashout(Player player) {
        Game game = games.get(player.getUniqueId());
        if (game == null || game.finished) return false;
        if (game.currentStep == 0) {
            player.sendMessage("§cСначала шагните хотя бы на одну ступень.");
            return false;
        }

        double mult = MULTIPLIERS[game.currentStep - 1];
        double amount = game.bet * mult;
        double commissionPercent = plugin.getConfig().getDouble("stairs.commission-percent", 0);
        double commission = amount * commissionPercent / 100.0;
        double payout = amount - commission;
        if (payout < 0) payout = 0;

        plugin.getEconomyManager().deposit(player, payout);
        if (commission > 0) depositCommission(player.getUniqueId(), commission);

        player.sendMessage("§a🪜 Вы забрали на ступени " + game.currentStep + " (множитель §e" +
                formatMultiplier(mult) + "§a): §f" + plugin.getEconomyManager().format(payout));
        playSound(player, "win");
        game.finished = true;
        plugin.getRollData().recordResult(player, "stairs", game.bet, payout, true);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            games.remove(player.getUniqueId());
            if (player.isOnline()) player.closeInventory();
        }, 60L);
        StairsGUI.updateAllOpen();
        return true;
    }

    public void cancel(UUID uuid) {
        Game game = games.get(uuid);
        if (game == null) return;
        if (!game.finished && game.currentStep == 0) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) plugin.getEconomyManager().deposit(p, game.bet);
        }
        games.remove(uuid);
    }

    private String formatMultiplier(double m) {
        if (m == Math.floor(m)) return (int) m + "x";
        return String.format("%.2fx", m);
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
        public int currentStep = 0;
        public boolean finished = false;
        public Game(UUID playerId, double bet) {
            this.playerId = playerId;
            this.bet = bet;
        }
    }
}
