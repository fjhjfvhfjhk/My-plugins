package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Слоты с весами символов.
 *
 * Изменения (v2.1):
 *  - символы имеют веса (NETHER_STAR редкий, DIAMOND частый);
 *  - пересчитаны множители, RTP ≈ 94.5%;
 *  - при 2-в-ряд мелкие символы дают 1.0x (возврат).
 */
public class SlotsManager {

    /** Символы и их веса (сумма = 100). */
    private static final Map<Material, Integer> WEIGHTS = new LinkedHashMap<>();
    static {
        WEIGHTS.put(Material.DIAMOND, 25);
        WEIGHTS.put(Material.EMERALD, 22);
        WEIGHTS.put(Material.GOLD_INGOT, 18);
        WEIGHTS.put(Material.IRON_INGOT, 15);
        WEIGHTS.put(Material.REDSTONE, 10);
        WEIGHTS.put(Material.LAPIS_LAZULI, 8);
        WEIGHTS.put(Material.NETHER_STAR, 2);
    }

    /** Множители для 3-в-ряд. */
    private static final Map<Material, Double> MULT_3 = Map.of(
            Material.NETHER_STAR, 200.0,
            Material.DIAMOND, 15.0,
            Material.EMERALD, 12.0,
            Material.GOLD_INGOT, 8.0,
            Material.IRON_INGOT, 6.0,
            Material.REDSTONE, 5.0,
            Material.LAPIS_LAZULI, 5.0
    );

    /** Множители для 2-в-ряд. */
    private static final Map<Material, Double> MULT_2 = Map.of(
            Material.NETHER_STAR, 10.0,
            Material.DIAMOND, 1.3,
            Material.EMERALD, 1.15,
            Material.GOLD_INGOT, 1.0,
            Material.IRON_INGOT, 1.0,
            Material.REDSTONE, 1.0,
            Material.LAPIS_LAZULI, 1.0
    );

    private final RollPlugin plugin;
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Random random = new Random();

    public SlotsManager(RollPlugin plugin) { this.plugin = plugin; }

    public Game getGame(UUID uuid) { return games.get(uuid); }
    public boolean isActive(UUID uuid) {
        Game g = games.get(uuid);
        return g != null && !g.finished;
    }

    public boolean start(Player player, double bet) {
        if (isActive(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже есть активная игра в слоты.");
            return false;
        }
        if (bet <= 0) { player.sendMessage("§cСумма должна быть положительной."); return false; }
        double minBet = plugin.getConfig().getDouble("slots.min-bet", 0);
        double maxBet = plugin.getConfig().getDouble("slots.max-bet", 0);
        if (minBet > 0 && bet < minBet) { player.sendMessage("§cМинимальная ставка: " + plugin.getEconomyManager().format(minBet)); return false; }
        if (maxBet > 0 && bet > maxBet) { player.sendMessage("§cМаксимальная ставка: " + plugin.getEconomyManager().format(maxBet)); return false; }

        if (!plugin.getEconomyManager().withdraw(player, bet)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(bet)));
            return false;
        }

        UUID uuid = player.getUniqueId();
        clearGame(uuid);

        Game game = new Game(uuid, bet);
        games.put(uuid, game);
        playSound(player, "slots-spin");

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Game g = games.get(uuid);
                if (g == null || g.finished) { this.cancel(); tasks.remove(uuid); return; }
                tick(player, g);
                if (g.finished) { this.cancel(); tasks.remove(uuid); }
            }
        }.runTaskTimer(plugin, 0L, 3L);

        tasks.put(uuid, task);
        return true;
    }

    private void tick(Player player, Game game) {
        game.tick++;
        if (game.tick < 20 || !game.stopped1) game.bar1 = randomSymbol();
        if (game.tick < 30 || !game.stopped2) game.bar2 = randomSymbol();
        if (game.tick < 40 || !game.stopped3) game.bar3 = randomSymbol();

        if (game.tick == 20) { game.stopped1 = true; game.result1 = randomSymbol(); game.bar1 = game.result1; playSound(player, "slots-stop"); }
        if (game.tick == 30) { game.stopped2 = true; game.result2 = randomSymbol(); game.bar2 = game.result2; playSound(player, "slots-stop"); }
        if (game.tick == 40) { game.stopped3 = true; game.result3 = randomSymbol(); game.bar3 = game.result3; playSound(player, "slots-stop"); }

        if (game.tick >= 50) { finishGame(player, game); return; }
        SlotsGUI.updateAllOpen();
    }

    private void finishGame(Player player, Game game) {
        game.finished = true;
        Material a = game.result1, b = game.result2, c = game.result3;
        double multiplier = computeMultiplier(a, b, c);
        double winAmount = game.bet * multiplier;

        if (multiplier > 1.0) {
            double payout = winAmount;
            plugin.getEconomyManager().deposit(player, payout);
            player.sendMessage("§a🎰 " + prettify(a) + " | " + prettify(b) + " | " + prettify(c) +
                    " §a— Множитель: §e" + formatMultiplier(multiplier) +
                    "§a, выигрыш: §f" + plugin.getEconomyManager().format(payout));
            playSound(player, multiplier >= 5 ? "slots-jackpot" : "slots-win");
            plugin.getRollData().recordResult(player, "slots", game.bet, payout, true);
        } else if (multiplier > 0) {
            // Множитель 1.0 = возврат ставки
            plugin.getEconomyManager().deposit(player, game.bet * multiplier);
            player.sendMessage("§e🎰 " + prettify(a) + " | " + prettify(b) + " | " + prettify(c) +
                    " §7— Пара совпала, возврат: §f" + plugin.getEconomyManager().format(game.bet * multiplier));
            playSound(player, "slots-stop");
            plugin.getRollData().recordResult(player, "slots", game.bet, game.bet * multiplier, false);
        } else {
            player.sendMessage("§c🎰 " + prettify(a) + " | " + prettify(b) + " | " + prettify(c) +
                    " §c— Проигрыш. Потеряно: §f" + plugin.getEconomyManager().format(game.bet));
            playSound(player, "slots-lose");
            plugin.getRollData().recordResult(player, "slots", game.bet, 0, false);
        }
        SlotsGUI.updateAllOpen();
    }

    public void clearGame(UUID uuid) {
        tasks.remove(uuid);
        games.remove(uuid);
    }

    /**
     * Множитель для комбинации. Возвращает 0, 1.0 или больше.
     * При 2-в-ряд множитель = MULT_2.get(symbol) (может быть 1.0 = возврат).
     * При 3-в-ряд множитель = MULT_3.get(symbol).
     */
    private double computeMultiplier(Material a, Material b, Material c) {
        if (a == b && b == c) {
            return MULT_3.getOrDefault(a, 5.0);
        }
        if (a == b || b == c || a == c) {
            Material pair = a == b ? a : (b == c ? b : a);
            return MULT_2.getOrDefault(pair, 1.0);
        }
        return 0.0;
    }

    private Material randomSymbol() {
        int total = WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (Map.Entry<Material, Integer> entry : WEIGHTS.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) return entry.getKey();
        }
        return Material.DIAMOND;
    }

    private String prettify(Material mat) {
        return switch (mat) {
            case DIAMOND -> "§b💎";
            case EMERALD -> "§a💚";
            case GOLD_INGOT -> "§6🥇";
            case IRON_INGOT -> "§7⚙";
            case REDSTONE -> "§c🔴";
            case LAPIS_LAZULI -> "§9🔵";
            case NETHER_STAR -> "§d⭐";
            default -> "§f?";
        };
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

    public static class Game {
        public final UUID playerId;
        public final double bet;
        public int tick = 0;
        public Material bar1, bar2, bar3;
        public Material result1, result2, result3;
        public boolean stopped1, stopped2, stopped3;
        public boolean finished = false;

        public Game(UUID playerId, double bet) {
            this.playerId = playerId;
            this.bet = bet;
            this.bar1 = Material.DIAMOND;
            this.bar2 = Material.EMERALD;
            this.bar3 = Material.GOLD_INGOT;
        }
    }
}
