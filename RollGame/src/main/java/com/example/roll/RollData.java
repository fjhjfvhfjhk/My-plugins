package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Центральное хранилище RollGame:
 *  - последние ставки игрока по каждой игре
 *  - статистика игрока по каждой игре
 *  - общий джекпот-банк
 *  - рассылки о крупных выигрышах и триггер джекпота
 *  - визуальные эффекты при победе
 */
public class RollData {

    public static final String[] GAME_KEYS = {"classic", "duel", "mines", "slots", "wheel", "stairs"};
    public static final String[] GAME_NAMES = {"Классика", "Дуэль", "Мины", "Слоты", "Колесо", "Лестница"};

    private final RollPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;

    private final Map<UUID, Map<String, Double>> lastBets = new HashMap<>();
    private final Map<UUID, Map<String, Stats>> stats = new HashMap<>();
    private long jackpot = 0;

    public RollData(RollPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "roll_data.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) return;
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        jackpot = data.getLong("jackpot", 0);

        ConfigurationSection betsSection = data.getConfigurationSection("last-bets");
        if (betsSection != null) {
            for (String uuidStr : betsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Double> map = new HashMap<>();
                    ConfigurationSection userSec = betsSection.getConfigurationSection(uuidStr);
                    if (userSec != null) {
                        for (String key : userSec.getKeys(false)) {
                            map.put(key, userSec.getDouble(key));
                        }
                    }
                    lastBets.put(uuid, map);
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection statsSection = data.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String uuidStr : statsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Stats> map = new HashMap<>();
                    ConfigurationSection userSec = statsSection.getConfigurationSection(uuidStr);
                    if (userSec != null) {
                        for (String gameKey : userSec.getKeys(false)) {
                            Stats s = new Stats();
                            s.gamesPlayed = userSec.getInt(gameKey + ".games", 0);
                            s.wins = userSec.getInt(gameKey + ".wins", 0);
                            s.totalBet = userSec.getDouble(gameKey + ".totalBet", 0);
                            s.totalWon = userSec.getDouble(gameKey + ".totalWon", 0);
                            s.biggestWin = userSec.getDouble(gameKey + ".biggestWin", 0);
                            map.put(gameKey, s);
                        }
                    }
                    stats.put(uuid, map);
                } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jackpot", jackpot);

        for (Map.Entry<UUID, Map<String, Double>> e : lastBets.entrySet()) {
            for (Map.Entry<String, Double> b : e.getValue().entrySet()) {
                yaml.set("last-bets." + e.getKey() + "." + b.getKey(), b.getValue());
            }
        }

        for (Map.Entry<UUID, Map<String, Stats>> e : stats.entrySet()) {
            for (Map.Entry<String, Stats> s : e.getValue().entrySet()) {
                String path = "stats." + e.getKey() + "." + s.getKey() + ".";
                yaml.set(path + "games", s.getValue().gamesPlayed);
                yaml.set(path + "wins", s.getValue().wins);
                yaml.set(path + "totalBet", s.getValue().totalBet);
                yaml.set(path + "totalWon", s.getValue().totalWon);
                yaml.set(path + "biggestWin", s.getValue().biggestWin);
            }
        }

        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить roll_data.yml: " + e.getMessage());
        }
    }

    public double getLastBet(UUID uuid, String gameKey) {
        Map<String, Double> map = lastBets.get(uuid);
        if (map == null) return 0;
        return map.getOrDefault(gameKey, 0.0);
    }

    public void setLastBet(UUID uuid, String gameKey, double amount) {
        lastBets.computeIfAbsent(uuid, k -> new HashMap<>()).put(gameKey, amount);
        save();
    }

    public Stats getStats(UUID uuid, String gameKey) {
        return stats.computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(gameKey, k -> new Stats());
    }

    public Map<String, Stats> getAllStats(UUID uuid) {
        return stats.getOrDefault(uuid, Collections.emptyMap());
    }

    public long getJackpot() { return jackpot; }

    public void addToJackpot(double amount) {
        jackpot += (long) amount;
        save();
    }

    public void resetJackpot() {
        jackpot = 0;
        save();
    }

    public boolean tryJackpot() {
        return ThreadLocalRandom.current().nextDouble() < 0.001;
    }

    public void recordResult(Player player, String gameKey, double bet, double payout, boolean win) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String gameName = gameNameOf(gameKey);

        setLastBet(uuid, gameKey, bet);

        Stats s = getStats(uuid, gameKey);
        s.gamesPlayed++;
        s.totalBet += bet;
        s.totalWon += payout;
        if (win) {
            s.wins++;
            if (payout > s.biggestWin) s.biggestWin = payout;
        }

        double jackpotFee = bet * 0.01;
        jackpot += (long) jackpotFee;

        long broadcastThreshold = plugin.getConfig().getLong("broadcast-threshold", 50000);
        if (win && payout >= broadcastThreshold) {
            Bukkit.broadcastMessage("§6§l🎉 " + player.getName() + " сорвал куш в " +
                    gameName + "§6§l: +" + plugin.getEconomyManager().format(payout) + "!");
        }

        if (win && jackpot > 0 && tryJackpot()) {
            long pot = jackpot;
            jackpot = 0;
            plugin.getEconomyManager().deposit(player, pot);
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§d§l╔══════════════════════════════╗");
            Bukkit.broadcastMessage("§d§l     💎 ДЖЕКПОТ СОРВАН! 💎");
            Bukkit.broadcastMessage("§f    " + player.getName() + " §dсорвал джекпот");
            Bukkit.broadcastMessage("§f    в игре §d" + gameName);
            Bukkit.broadcastMessage("§a§l     +" + plugin.getEconomyManager().format(pot));
            Bukkit.broadcastMessage("§d§l╚══════════════════════════════╝");
            Bukkit.broadcastMessage("");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        if (win) {
            spawnWinParticles(player, payout);
        }

        save();
    }

    private void spawnWinParticles(Player player, double payout) {
        World world = player.getWorld();
        double x = player.getLocation().getX();
        double y = player.getLocation().getY() + 1.0;
        double z = player.getLocation().getZ();

        int count = 30;
        if (payout > 100000) count = 60;
        if (payout > 500000) count = 100;

        world.spawnParticle(Particle.FLAME, x, y, z, count, 0.5, 0.7, 0.5, 0.05);
        world.spawnParticle(Particle.FIREWORK, x, y, z, count / 2, 0.5, 0.7, 0.5, 0.1);
        world.spawnParticle(Particle.CRIT, x, y, z, count / 2, 0.5, 0.7, 0.5, 0.1);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private String gameNameOf(String gameKey) {
        for (int i = 0; i < GAME_KEYS.length; i++) {
            if (GAME_KEYS[i].equalsIgnoreCase(gameKey)) return GAME_NAMES[i];
        }
        return gameKey;
    }

    public static class Stats {
        public int gamesPlayed = 0;
        public int wins = 0;
        public double totalBet = 0;
        public double totalWon = 0;
        public double biggestWin = 0;

        public double getProfit() { return totalWon - totalBet; }

        public double getWinRate() {
            if (gamesPlayed == 0) return 0;
            return 100.0 * wins / gamesPlayed;
        }
    }
}
