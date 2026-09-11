package com.example.roll;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RollHistory {

    private final RollPlugin plugin;
    private final File dataFile;
    private final List<GameRecord> records = new ArrayList<>();
    private final int maxRecords;

    public RollHistory(RollPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "history.yml");
        this.maxRecords = plugin.getConfig().getInt("history-limit", 30);
        load();
    }

    public void addRecord(GameRecord record) {
        records.add(0, record);
        while (records.size() > maxRecords) {
            records.remove(records.size() - 1);
        }
        save();
    }

    public List<GameRecord> getRecentRecords(int count) {
        return records.stream().limit(count).collect(Collectors.toList());
    }

    public Map<UUID, PlayerStats> getTopPlayers(int limit) {
        Map<UUID, PlayerStats> stats = new HashMap<>();
        for (GameRecord record : records) {
            UUID winner = record.winner;
            stats.computeIfAbsent(winner, k -> new PlayerStats())
                    .wins++;
            stats.get(winner).totalWon += record.totalPot;
        }
        return stats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().wins, a.getValue().wins))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            try {
                long timestamp = yaml.getLong(key + ".timestamp");
                UUID winner = UUID.fromString(yaml.getString(key + ".winner"));
                double totalPot = yaml.getDouble(key + ".totalPot");
                Map<UUID, Double> bets = new HashMap<>();
                if (yaml.contains(key + ".bets")) {
                    for (String uuidStr : yaml.getConfigurationSection(key + ".bets").getKeys(false)) {
                        UUID uuid = UUID.fromString(uuidStr);
                        double amount = yaml.getDouble(key + ".bets." + uuidStr);
                        bets.put(uuid, amount);
                    }
                }
                records.add(new GameRecord(timestamp, winner, totalPot, bets));
            } catch (Exception ignored) {}
        }
        records.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        if (records.size() > maxRecords) {
            records.subList(maxRecords, records.size()).clear();
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (GameRecord record : records) {
            String key = String.valueOf(index++);
            yaml.set(key + ".timestamp", record.timestamp);
            yaml.set(key + ".winner", record.winner.toString());
            yaml.set(key + ".totalPot", record.totalPot);
            for (Map.Entry<UUID, Double> entry : record.bets.entrySet()) {
                yaml.set(key + ".bets." + entry.getKey().toString(), entry.getValue());
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить историю: " + e.getMessage());
        }
    }

    public static class GameRecord {
        public final long timestamp;
        public final UUID winner;
        public final double totalPot;
        public final Map<UUID, Double> bets;

        public GameRecord(long timestamp, UUID winner, double totalPot, Map<UUID, Double> bets) {
            this.timestamp = timestamp;
            this.winner = winner;
            this.totalPot = totalPot;
            this.bets = new HashMap<>(bets);
        }
    }

    public static class PlayerStats {
        public int wins = 0;
        public double totalWon = 0;
    }
}
