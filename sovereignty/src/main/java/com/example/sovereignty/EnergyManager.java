package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EnergyManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public EnergyManager(SovereigntyPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public PlayerData getData(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDb);
    }

    private PlayerData loadFromDb(UUID uuid) {
        double startEnergy = plugin.getConfig().getDouble("energy.starting-energy", 10.0);
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT * FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerData(
                        rs.getDouble("energy"),
                        rs.getInt("max_level"),
                        rs.getInt("regen_level"),
                        rs.getLong("last_regen"),
                        rs.getLong("boost_until"),
                        rs.getInt("chunk_limit_level"),
                        rs.getInt("farm_upgrade_level")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        PlayerData data = new PlayerData(startEnergy, 0, 0, System.currentTimeMillis(), 0, 0, 0);
        save(uuid, data);
        return data;
    }

    public void save(UUID uuid, PlayerData data) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO player_data(uuid, energy, max_level, regen_level, last_regen, boost_until, chunk_limit_level, farm_upgrade_level) VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, data.energy);
            ps.setInt(3, data.maxLevel);
            ps.setInt(4, data.regenLevel);
            ps.setLong(5, data.lastRegen);
            ps.setLong(6, data.boostUntil);
            ps.setInt(7, data.chunkLimitLevel);
            ps.setInt(8, data.farmUpgradeLevel);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    public double getEnergy(UUID uuid) { return getData(uuid).energy; }
    public double getMaxEnergy(int level) {
        var levels = plugin.getConfig().getDoubleList("energy.max-levels");
        if (level < 0 || level >= levels.size()) return levels.isEmpty() ? 10.0 : levels.get(levels.size() - 1);
        return levels.get(level);
    }
    public double getMaxEnergy(UUID uuid) {
        return getMaxEnergy(getData(uuid).maxLevel);
    }

    public boolean consumeEnergy(UUID uuid, double amount) {
        PlayerData data = getData(uuid);
        if (data.energy < amount) return false;
        data.energy -= amount;
        save(uuid, data);
        return true;
    }

    public boolean addEnergy(UUID uuid, double amount) {
        PlayerData data = getData(uuid);
        double max = getMaxEnergy(data.maxLevel);
        double newEnergy = Math.min(data.energy + amount, max);
        if (newEnergy <= data.energy && amount > 0) return false;
        data.energy = newEnergy;
        save(uuid, data);
        return true;
    }

    public void forceAddEnergy(UUID uuid, double amount) {
        PlayerData data = getData(uuid);
        data.energy += amount;
        save(uuid, data);
    }

    public void forceRemoveEnergy(UUID uuid, double amount) {
        PlayerData data = getData(uuid);
        data.energy = Math.max(0, data.energy - amount);
        save(uuid, data);
    }

    public double getRegenPerHour(int regenLevel) {
        var levels = plugin.getConfig().getDoubleList("energy.regen-levels");
        if (regenLevel < 0 || regenLevel >= levels.size()) return levels.isEmpty() ? 2.0 : levels.get(levels.size() - 1);
        return levels.get(regenLevel);
    }

    public boolean isBoostActive(UUID uuid) {
        return getData(uuid).boostUntil > System.currentTimeMillis();
    }

    public double getCurrentRegenPerHour(UUID uuid) {
        PlayerData data = getData(uuid);
        double base = getRegenPerHour(data.regenLevel);
        if (isBoostActive(uuid)) {
            base += plugin.getConfig().getDouble("energy.boost-regen-bonus", 2.0);
        }
        // Множитель событий
        base *= plugin.getEventManager().getEnergyRegenMultiplier();

        // Технология "Атомная энергия": +2/час
        if (plugin.getScienceManager().isResearched(uuid, "atomic_energy")) {
            base += 2.0;
        }

        return base;
    }

    public long getBoostRemainingMillis(UUID uuid) {
        return Math.max(0, getData(uuid).boostUntil - System.currentTimeMillis());
    }

    public boolean buyBoost(UUID uuid) {
        PlayerData data = getData(uuid);
        double cost = plugin.getConfig().getDouble("energy.boost-cost", 5000.0);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(player, cost)) return false;
        eco.withdraw(player, cost);
        long durationMs = (long)(plugin.getConfig().getDouble("energy.boost-duration-hours", 1) * 3600000);
        data.boostUntil = System.currentTimeMillis() + durationMs;
        save(uuid, data);
        return true;
    }

    public int getMaxLevel(UUID uuid) { return getData(uuid).maxLevel; }
    public int getRegenLevel(UUID uuid) { return getData(uuid).regenLevel; }
    public int getChunkLimitLevel(UUID uuid) { return getData(uuid).chunkLimitLevel; }
    public int getFarmUpgradeLevel(UUID uuid) { return getData(uuid).farmUpgradeLevel; }

    public boolean hasNextMaxUpgrade(UUID uuid) {
        return getData(uuid).maxLevel + 1 < plugin.getConfig().getDoubleList("energy.max-upgrade-costs").size();
    }
    public double getNextMaxUpgradeCost(UUID uuid) {
        int next = getData(uuid).maxLevel + 1;
        var costs = plugin.getConfig().getDoubleList("energy.max-upgrade-costs");
        if (next >= costs.size()) return -1;
        return costs.get(next);
    }
    public boolean hasNextRegenUpgrade(UUID uuid) {
        return getData(uuid).regenLevel + 1 < plugin.getConfig().getDoubleList("energy.regen-upgrade-costs").size();
    }
    public double getNextRegenUpgradeCost(UUID uuid) {
        int next = getData(uuid).regenLevel + 1;
        var costs = plugin.getConfig().getDoubleList("energy.regen-upgrade-costs");
        if (next >= costs.size()) return -1;
        return costs.get(next);
    }
    public boolean hasNextChunkLimitUpgrade(UUID uuid) {
        return getData(uuid).chunkLimitLevel + 1 < plugin.getConfig().getDoubleList("energy.chunk-limit-upgrade-costs").size();
    }
    public double getNextChunkLimitUpgradeCost(UUID uuid) {
        int next = getData(uuid).chunkLimitLevel + 1;
        var costs = plugin.getConfig().getDoubleList("energy.chunk-limit-upgrade-costs");
        if (next >= costs.size()) return -1;
        return costs.get(next);
    }
    public boolean hasNextFarmUpgrade(UUID uuid) {
        return getData(uuid).farmUpgradeLevel + 1 < plugin.getConfig().getDoubleList("energy.farm-upgrade-costs").size();
    }
    public double getNextFarmUpgradeCost(UUID uuid) {
        int next = getData(uuid).farmUpgradeLevel + 1;
        var costs = plugin.getConfig().getDoubleList("energy.farm-upgrade-costs");
        if (next >= costs.size()) return -1;
        return costs.get(next);
    }

    public boolean upgradeMax(UUID uuid) {
        PlayerData data = getData(uuid);
        if (!hasNextMaxUpgrade(uuid)) return false;
        double cost = getNextMaxUpgradeCost(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(player, cost)) return false;
        eco.withdraw(player, cost);
        data.maxLevel++;
        save(uuid, data);
        return true;
    }

    public boolean upgradeRegen(UUID uuid) {
        PlayerData data = getData(uuid);
        if (!hasNextRegenUpgrade(uuid)) return false;
        double cost = getNextRegenUpgradeCost(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(player, cost)) return false;
        eco.withdraw(player, cost);
        data.regenLevel++;
        save(uuid, data);
        return true;
    }

    public boolean upgradeChunkLimit(UUID uuid) {
        PlayerData data = getData(uuid);
        if (!hasNextChunkLimitUpgrade(uuid)) return false;
        double cost = getNextChunkLimitUpgradeCost(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(player, cost)) return false;
        eco.withdraw(player, cost);
        data.chunkLimitLevel++;
        save(uuid, data);
        return true;
    }

    public boolean upgradeFarm(UUID uuid) {
        PlayerData data = getData(uuid);
        if (!hasNextFarmUpgrade(uuid)) return false;
        double cost = getNextFarmUpgradeCost(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(player, cost)) return false;
        eco.withdraw(player, cost);
        data.farmUpgradeLevel++;
        save(uuid, data);
        return true;
    }

    public void regenAllOnline() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerData data = getData(uuid);
            long elapsedMs = now - data.lastRegen;
            double hours = elapsedMs / (1000.0 * 60.0 * 60.0);
            if (hours <= 0) continue;
            double regen = getCurrentRegenPerHour(uuid) * hours;
            double max = getMaxEnergy(data.maxLevel);
            data.energy = Math.min(data.energy + regen, max);
            data.lastRegen = now;
            save(uuid, data);
        }
    }

    public void tickBoosts() {}

    public static class PlayerData {
        public double energy;
        public int maxLevel;
        public int regenLevel;
        public long lastRegen;
        public long boostUntil;
        public int chunkLimitLevel;
        public int farmUpgradeLevel;

        public PlayerData(double energy, int maxLevel, int regenLevel, long lastRegen,
                          long boostUntil, int chunkLimitLevel, int farmUpgradeLevel) {
            this.energy = energy;
            this.maxLevel = maxLevel;
            this.regenLevel = regenLevel;
            this.lastRegen = lastRegen;
            this.boostUntil = boostUntil;
            this.chunkLimitLevel = chunkLimitLevel;
            this.farmUpgradeLevel = farmUpgradeLevel;
        }
    }
}
