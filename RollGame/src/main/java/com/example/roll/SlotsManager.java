package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SlotsManager {

    public static final List<Material> SYMBOLS = List.of(
            Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT,
            Material.IRON_INGOT, Material.REDSTONE, Material.LAPIS_LAZULI, Material.NETHER_STAR
    );

    private final RollPlugin plugin;
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

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

        if (multiplier > 0) {
            double commissionPercent = plugin.getConfig().getDouble("slots.commission-percent", 5);
            double commission = winAmount * commissionPercent / 100.0;
            double payout = winAmount - commission;
            plugin.getEconomyManager().deposit(player, payout);
            if (commission > 0) depositCommission(player.getUniqueId(), commission);
            player.sendMessage("§a🎰 " + prettify(a) + " | " + prettify(b) + " | " + prettify(c) +
                    " §a— Множитель: §e" + formatMultiplier(multiplier) +
                    "§a, выигрыш: §f" + plugin.getEconomyManager().format(payout));
            playSound(player, multiplier >= 5 ? "slots-jackpot" : "slots-win");
            plugin.getRollData().recordResult(player, "slots", game.bet, payout, true);
        } else {
            double commissionPercent = plugin.getConfig().getDouble("slots.commission-percent", 5);
            double commission = game.bet * commissionPercent / 100.0;
            if (commission > 0) depositCommission(player.getUniqueId(), commission);
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

    private double computeMultiplier(Material a, Material b, Material c) {
        if (a == b && b == c) {
            return switch (a) {
                case NETHER_STAR -> 50.0;
                case DIAMOND -> 10.0;
                case EMERALD -> 8.0;
                case GOLD_INGOT -> 5.0;
                case IRON_INGOT -> 4.0;
                default -> 3.0;
            };
        }
        if (a == b || b == c || a == c) {
            Material pair = a == b ? a : (b == c ? b : a);
            return switch (pair) {
                case NETHER_STAR -> 5.0;
                case DIAMOND -> 2.5;
                case EMERALD -> 2.0;
                default -> 1.5;
            };
        }
        return 0.0;
    }

    private Material randomSymbol() { return SYMBOLS.get(new Random().nextInt(SYMBOLS.size())); }

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
        public int tick = 0;
        public Material bar1, bar2, bar3;
        public Material result1, result2, result3;
        public boolean stopped1, stopped2, stopped3;
        public boolean finished = false;
        public Game(UUID playerId, double bet) {
            this.playerId = playerId;
            this.bet = bet;
            this.bar1 = randomSymbol();
            this.bar2 = randomSymbol();
            this.bar3 = randomSymbol();
        }
        private Material randomSymbol() { return SYMBOLS.get(new Random().nextInt(SYMBOLS.size())); }
    }
}
