package com.example.roll;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DuelHistory {

    private final RollPlugin plugin;
    private final File dataFile;
    private final List<DuelRecord> records = new ArrayList<>();
    private final int maxRecords;

    public DuelHistory(RollPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "duel_history.yml");
        this.maxRecords = plugin.getConfig().getInt("history-limit", 30);
        load();
    }

    public void addRecord(DuelRecord record) {
        records.add(0, record);
        while (records.size() > maxRecords) {
            records.remove(records.size() - 1);
        }
        save();
    }

    public List<DuelRecord> getRecentRecords(int count) {
        return records.stream().limit(count).collect(Collectors.toList());
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            try {
                long timestamp = yaml.getLong(key + ".timestamp");
                UUID player = UUID.fromString(yaml.getString(key + ".player"));
                double bet = yaml.getDouble(key + ".bet");
                boolean win = yaml.getBoolean(key + ".win");
                double winAmount = yaml.getDouble(key + ".winAmount");
                records.add(new DuelRecord(timestamp, player, bet, win, winAmount));
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
        for (DuelRecord record : records) {
            String key = String.valueOf(index++);
            yaml.set(key + ".timestamp", record.timestamp);
            yaml.set(key + ".player", record.player.toString());
            yaml.set(key + ".bet", record.bet);
            yaml.set(key + ".win", record.win);
            yaml.set(key + ".winAmount", record.winAmount);
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить историю дуэлей: " + e.getMessage());
        }
    }

    public static class DuelRecord {
        public final long timestamp;
        public final UUID player;
        public final double bet;
        public final boolean win;
        public final double winAmount;

        public DuelRecord(long timestamp, UUID player, double bet, boolean win, double winAmount) {
            this.timestamp = timestamp;
            this.player = player;
            this.bet = bet;
            this.win = win;
            this.winAmount = winAmount;
        }
    }
}
