package com.example.roll;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * История покерных раздач + недельный лидерборд по прибыли.
 */
public class PokerHistory {

    public static class Record {
        public final long timestamp;
        public final UUID winner;
        public final String winnerName;
        public final long potSize;
        public final String handName;
        public final List<String> lines;

        public Record(long timestamp, UUID winner, String winnerName, long potSize,
                      String handName, List<String> lines) {
            this.timestamp = timestamp;
            this.winner = winner;
            this.winnerName = winnerName;
            this.potSize = potSize;
            this.handName = handName;
            this.lines = lines;
        }
    }

    public static class PlayerProfit {
        public UUID uuid;
        public String name;
        public long profit;
        public int handsPlayed;

        public PlayerProfit(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    private final RollPlugin plugin;
    private final File dataFile;
    private final List<Record> recentRecords = new ArrayList<>();
    // Профит за последнюю неделю: uuid → (суммарный профит, кол-во раздач)
    private final Map<UUID, PlayerProfit> weeklyProfit = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();

    public PokerHistory(RollPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "poker_history.yml");
        load();
    }

    /** Добавляет запись о раздаче и обновляет недельный профит. */
    public void addHand(Record record, Map<UUID, Long> profitsThisHand, Map<UUID, String> names) {
        recentRecords.add(0, record);
        while (recentRecords.size() > 30) recentRecords.remove(recentRecords.size() - 1);

        for (Map.Entry<UUID, Long> e : profitsThisHand.entrySet()) {
            UUID uuid = e.getKey();
            long profit = e.getValue();
            String name = names.getOrDefault(uuid, uuid.toString().substring(0, 8));
            PlayerProfit pp = weeklyProfit.computeIfAbsent(uuid, k -> new PlayerProfit(uuid, name));
            pp.name = name;
            pp.profit += profit;
            pp.handsPlayed++;
        }

        save();
    }

    /** Топ игроков по прибыли за неделю. */
    public List<PlayerProfit> getTop(int limit) {
        return weeklyProfit.values().stream()
                .sorted((a, b) -> Long.compare(b.profit, a.profit))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Record> getRecent(int count) {
        return recentRecords.stream().limit(count).collect(Collectors.toList());
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        long now = System.currentTimeMillis();

        ConfigurationSection profitSection = yaml.getConfigurationSection("weekly-profit");
        if (profitSection != null) {
            for (String key : profitSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long ts = profitSection.getLong(key + ".last-updated", now);
                    // Игнорируем записи старше недели
                    if (now - ts > WEEK_MS) continue;
                    PlayerProfit pp = new PlayerProfit(uuid, profitSection.getString(key + ".name", "???"));
                    pp.profit = profitSection.getLong(key + ".profit", 0);
                    pp.handsPlayed = profitSection.getInt(key + ".hands", 0);
                    weeklyProfit.put(uuid, pp);
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection historySection = yaml.getConfigurationSection("recent");
        if (historySection != null) {
            for (String key : historySection.getKeys(false)) {
                try {
                    long ts = historySection.getLong(key + ".timestamp");
                    // Показываем последние 30 раздач независимо от даты
                    List<String> lines = historySection.getStringList(key + ".lines");
                    String handName = historySection.getString(key + ".hand", "?");
                    long pot = historySection.getLong(key + ".pot", 0);
                    String wName = historySection.getString(key + ".winner-name", "?");
                    String wUuidStr = historySection.getString(key + ".winner-uuid");
                    UUID winner = wUuidStr != null ? UUID.fromString(wUuidStr) : UUID.randomUUID();
                    recentRecords.add(new Record(ts, winner, wName, pot, handName, lines));
                } catch (Exception ignored) {}
            }
            recentRecords.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
            while (recentRecords.size() > 30) recentRecords.remove(recentRecords.size() - 1);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();

        // Сохраняем только активные (за неделю) записи
        int idx = 0;
        for (Map.Entry<UUID, PlayerProfit> e : weeklyProfit.entrySet()) {
            PlayerProfit pp = e.getValue();
            yaml.set("weekly-profit." + pp.uuid + ".name", pp.name);
            yaml.set("weekly-profit." + pp.uuid + ".profit", pp.profit);
            yaml.set("weekly-profit." + pp.uuid + ".hands", pp.handsPlayed);
            yaml.set("weekly-profit." + pp.uuid + ".last-updated", now);
            idx++;
        }

        int i = 0;
        for (Record r : recentRecords) {
            String key = String.valueOf(i++);
            yaml.set("recent." + key + ".timestamp", r.timestamp);
            yaml.set("recent." + key + ".winner-uuid", r.winner.toString());
            yaml.set("recent." + key + ".winner-name", r.winnerName);
            yaml.set("recent." + key + ".pot", r.potSize);
            yaml.set("recent." + key + ".hand", r.handName);
            yaml.set("recent." + key + ".lines", r.lines);
        }

        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить poker_history.yml: " + e.getMessage());
        }
    }
}
