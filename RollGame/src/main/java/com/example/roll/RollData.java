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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RollData {

    public static final String[] GAME_KEYS = {"classic", "duel", "mines", "slots", "wheel", "stairs"};
    public static final String[] GAME_NAMES = {"Классика", "Дуэль", "Мины", "Слоты", "Колесо", "Лестница"};

    private final RollPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;

    private final Map<UUID, Map<String, Double>> lastBets = new HashMap<>();
    private final Map<UUID, Map<String, Stats>> stats = new HashMap<>();
    private long jackpot = 0;

    private final Map<UUID, LinkedList<UpgradeRoll>> upgradeHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Double> upgradeProfit = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> upgradeRolls = new ConcurrentHashMap<>();

    private static final int HISTORY_LIMIT = 10;

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
                    if (userSec != null) for (String key : userSec.getKeys(false)) map.put(key, userSec.getDouble(key));
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

        ConfigurationSection profitSec = data.getConfigurationSection("upgrade-profit");
        if (profitSec != null) {
            for (String uuidStr : profitSec.getKeys(false)) {
                try {
                    Object v = profitSec.get(uuidStr);
                    if (v instanceof Number n) upgradeProfit.put(UUID.fromString(uuidStr), n.doubleValue());
                } catch (Exception ignored) {}
            }
        }
        ConfigurationSection rollsSec = data.getConfigurationSection("upgrade-rolls");
        if (rollsSec != null) {
            for (String uuidStr : rollsSec.getKeys(false)) {
                try {
                    Object v = rollsSec.get(uuidStr);
                    if (v instanceof Number n) upgradeRolls.put(UUID.fromString(uuidStr), n.intValue());
                } catch (Exception ignored) {}
            }
        }
        ConfigurationSection histSec = data.getConfigurationSection("upgrade-history");
        if (histSec != null) {
            for (String uuidStr : histSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    LinkedList<UpgradeRoll> list = new LinkedList<>();
                    List<?> raw = data.getList("upgrade-history." + uuidStr);
                    if (raw != null) {
                        for (Object o : raw) {
                            if (o instanceof Map<?, ?> m) {
                                UpgradeRoll r = new UpgradeRoll();
                                r.timestamp = readLong(m, "ts");
                                r.win = Boolean.TRUE.equals(m.get("win"));
                                r.bet = readDouble(m, "bet");
                                r.payout = readDouble(m, "payout");
                                r.chance = readDouble(m, "chance");
                                list.add(r);
                            }
                        }
                    }
                    if (!list.isEmpty()) upgradeHistory.put(uuid, list);
                } catch (Exception ignored) {}
            }
        }
    }

    private static long readLong(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private static double readDouble(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jackpot", jackpot);

        for (Map.Entry<UUID, Map<String, Double>> e : lastBets.entrySet())
            for (Map.Entry<String, Double> b : e.getValue().entrySet())
                yaml.set("last-bets." + e.getKey() + "." + b.getKey(), b.getValue());

        for (Map.Entry<UUID, Map<String, Stats>> e : stats.entrySet())
            for (Map.Entry<String, Stats> s : e.getValue().entrySet()) {
                String path = "stats." + e.getKey() + "." + s.getKey() + ".";
                yaml.set(path + "games", s.getValue().gamesPlayed);
                yaml.set(path + "wins", s.getValue().wins);
                yaml.set(path + "totalBet", s.getValue().totalBet);
                yaml.set(path + "totalWon", s.getValue().totalWon);
                yaml.set(path + "biggestWin", s.getValue().biggestWin);
            }

        for (Map.Entry<UUID, Double> e : upgradeProfit.entrySet())
            yaml.set("upgrade-profit." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Integer> e : upgradeRolls.entrySet())
            yaml.set("upgrade-rolls." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, LinkedList<UpgradeRoll>> e : upgradeHistory.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (UpgradeRoll r : e.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ts", r.timestamp);
                m.put("win", r.win);
                m.put("bet", r.bet);
                m.put("payout", r.payout);
                m.put("chance", r.chance);
                list.add(m);
            }
            yaml.set("upgrade-history." + e.getKey(), list);
        }

        try { yaml.save(dataFile); } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить roll_data.yml: " + ex.getMessage());
        }
    }

    public double getLastBet(UUID uuid, String gameKey) {
        Map<String, Double> map = lastBets.get(uuid);
        return map == null ? 0 : map.getOrDefault(gameKey, 0.0);
    }

    public void setLastBet(UUID uuid, String gameKey, double amount) {
        lastBets.computeIfAbsent(uuid, k -> new HashMap<>()).put(gameKey, amount);
        save();
    }

    public Stats getStats(UUID uuid, String gameKey) {
        return stats.computeIfAbsent(uuid, k -> new HashMap<>()).computeIfAbsent(gameKey, k -> new Stats());
    }

    public Map<String, Stats> getAllStats(UUID uuid) {
        return stats.getOrDefault(uuid, Collections.emptyMap());
    }

    public long getJackpot() { return jackpot; }

    public void addToJackpot(double amount) {
        jackpot += (long) amount;
        save();
        notifyJackpotChanged();
    }

    public void resetJackpot() {
        jackpot = 0;
        save();
        notifyJackpotChanged();
    }

    private void notifyJackpotChanged() {
        if (plugin.getPanelExporter() != null) plugin.getPanelExporter().markJackpotDirty();
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
        long oldJackpot = jackpot;
        jackpot += (long) jackpotFee;
        if (jackpot != oldJackpot) notifyJackpotChanged();

        long broadcastThreshold = plugin.getConfig().getLong("broadcast-threshold", 50000);
        if (win && payout >= broadcastThreshold) {
            Bukkit.broadcastMessage("§6§l🎉 " + player.getName() + " сорвал куш в " +
                    gameName + "§6§l: +" + plugin.getEconomyManager().format(payout) + "!");
        }

        if (win && jackpot > 0 && tryJackpot()) {
            long pot = jackpot;
            jackpot = 0;
            notifyJackpotChanged();
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

        if (win) spawnWinParticles(player, payout);
        save();
    }

    public void recordUpgradeRoll(Player player, boolean win, double bet, double payout, double chance) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        UpgradeRoll r = new UpgradeRoll();
        r.timestamp = System.currentTimeMillis();
        r.win = win;
        r.bet = bet;
        r.payout = payout;
        r.chance = chance;

        LinkedList<UpgradeRoll> list = upgradeHistory.computeIfAbsent(uuid, k -> new LinkedList<>());
        list.addFirst(r);
        while (list.size() > HISTORY_LIMIT) list.removeLast();

        double profitDelta = payout - bet;
        upgradeProfit.merge(uuid, profitDelta, Double::sum);
        upgradeRolls.merge(uuid, 1, Integer::sum);

        save();
    }

    public List<UpgradeRoll> getUpgradeHistory(UUID uuid) {
        List<UpgradeRoll> list = upgradeHistory.get(uuid);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public double getUpgradeProfit(UUID uuid) {
        return upgradeProfit.getOrDefault(uuid, 0.0);
    }

    public int getUpgradeRolls(UUID uuid) {
        return upgradeRolls.getOrDefault(uuid, 0);
    }

    public List<Map.Entry<UUID, Double>> getUpgradeTop(int limit) {
        return upgradeProfit.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
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
        for (int i = 0; i < GAME_KEYS.length; i++)
            if (GAME_KEYS[i].equalsIgnoreCase(gameKey)) return GAME_NAMES[i];
        return gameKey;
    }

    public static class Stats {
        public int gamesPlayed = 0;
        public int wins = 0;
        public double totalBet = 0;
        public double totalWon = 0;
        public double biggestWin = 0;
        public double getProfit() { return totalWon - totalBet; }
        public double getWinRate() { return gamesPlayed == 0 ? 0 : 100.0 * wins / gamesPlayed; }
    }

    public static class UpgradeRoll {
        public long timestamp;
        public boolean win;
        public double bet;
        public double payout;
        public double chance;
    }
}
