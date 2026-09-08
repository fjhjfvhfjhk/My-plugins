package com.example.taxv3;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Хранит долги и счётчики просрочек.
 */
public class DataManager {

    private final TaxPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, PlayerTaxData> players = new HashMap<>();

    public DataManager(TaxPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) plugin.saveResource("data.yml", false);
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        players.clear();

        if (data.contains("players")) {
            for (String key : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double debt = data.getDouble("players." + key + ".debt", 0);
                    int missed = data.getInt("players." + key + ".missed", 0);
                    if (debt > 0 || missed > 0) {
                        players.put(uuid, new PlayerTaxData(debt, missed));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        data.set("players", null);
        for (Map.Entry<UUID, PlayerTaxData> entry : players.entrySet()) {
            String key = entry.getKey().toString();
            PlayerTaxData d = entry.getValue();
            if (d.getDebt() > 0 || d.getMissed() > 0) {
                data.set("players." + key + ".debt", d.getDebt());
                data.set("players." + key + ".missed", d.getMissed());
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    public PlayerTaxData getData(UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerTaxData(0, 0));
    }

    public double getDebt(UUID uuid) { return getData(uuid).getDebt(); }

    public void addDebt(UUID uuid, double amount) {
        PlayerTaxData d = getData(uuid);
        d.setDebt(d.getDebt() + amount);
        save();
    }

    public boolean reduceDebt(UUID uuid, double amount) {
        PlayerTaxData d = getData(uuid);
        double remaining = d.getDebt() - amount;
        if (remaining <= 0) {
            d.setDebt(0);
            save();
            return true;
        } else {
            d.setDebt(remaining);
            save();
            return false;
        }
    }

    public void clearDebt(UUID uuid) {
        PlayerTaxData d = getData(uuid);
        d.setDebt(0);
        save();
    }

    public int getMissed(UUID uuid) { return getData(uuid).getMissed(); }

    public void incrementMissed(UUID uuid) {
        PlayerTaxData d = getData(uuid);
        d.setMissed(d.getMissed() + 1);
        save();
    }

    public void resetMissed(UUID uuid) {
        PlayerTaxData d = getData(uuid);
        d.setMissed(0);
        save();
    }

    public Map<UUID, PlayerTaxData> getAllPlayers() { return new HashMap<>(players); }

    public static class PlayerTaxData {
        private double debt;
        private int missed;

        public PlayerTaxData(double debt, int missed) {
            this.debt = debt;
            this.missed = missed;
        }

        public double getDebt() { return debt; }
        public void setDebt(double debt) { this.debt = debt; }
        public int getMissed() { return missed; }
        public void setMissed(int missed) { this.missed = missed; }
    }
}
