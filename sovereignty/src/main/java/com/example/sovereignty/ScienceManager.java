package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;

public class ScienceManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;
    private final ChunkUpgradeManager chunkUpgradeManager;

    private final Map<UUID, Double> sciencePointsCache = new HashMap<>();
    private final Map<UUID, Integer> scienceLevelCache = new HashMap<>();
    private final Map<UUID, Set<String>> researchedTechCache = new HashMap<>();

    // Описание технологий: id -> {название, описание, стоимость, зависимость (или null)}
    private final Map<String, String[]> TECHNOLOGIES = new LinkedHashMap<>();

    public ScienceManager(SovereigntyPlugin plugin, DatabaseManager db,
                          CountryManager countryManager, ChunkUpgradeManager chunkUpgradeManager) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = countryManager;
        this.chunkUpgradeManager = chunkUpgradeManager;
        initTechnologies();
    }

    private void initTechnologies() {
        TECHNOLOGIES.put("farming", new String[]{"Земледелие", "+10% к доходу ферм", "5", null});
        TECHNOLOGIES.put("mining_tech", new String[]{"Горное дело", "+1 уровень Спешки в шахтёрском бонусе", "5", null});
        TECHNOLOGIES.put("fortification", new String[]{"Фортификация", "Защитные чанки держатся на 1 мин дольше после смерти", "10", null});
        TECHNOLOGIES.put("trade_tech", new String[]{"Торговля", "+5% к доходу торговых чанков", "10", null});
        TECHNOLOGIES.put("military_science", new String[]{"Военная наука", "+5% урон по врагам на своей территории", "20", "fortification"});
        TECHNOLOGIES.put("logistics", new String[]{"Логистика", "+5 к лимиту чанков", "20", "trade_tech"});
        TECHNOLOGIES.put("atomic_energy", new String[]{"Атомная энергия", "+2 к регенерации энергии/час", "30", "mining_tech"});
        TECHNOLOGIES.put("imperial_admin", new String[]{"Имперская администрация", "-10% к налогам", "50", "logistics"});
    }

    public Map<String, String[]> getTechnologies() {
        return TECHNOLOGIES;
    }

    public void loadPlayer(UUID uuid) {
        // Загружаем очки и уровень
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT science_points, science_level FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                sciencePointsCache.put(uuid, rs.getDouble("science_points"));
                scienceLevelCache.put(uuid, rs.getInt("science_level"));
            } else {
                sciencePointsCache.put(uuid, 0.0);
                scienceLevelCache.put(uuid, 0);
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // Загружаем изученные технологии
        Set<String> researched = new HashSet<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT tech_id FROM technologies WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) researched.add(rs.getString("tech_id"));
        } catch (SQLException e) { e.printStackTrace(); }
        researchedTechCache.put(uuid, researched);
    }

    public double getSciencePoints(UUID uuid) {
        return sciencePointsCache.getOrDefault(uuid, 0.0);
    }

    public int getScienceLevel(UUID uuid) {
        return scienceLevelCache.getOrDefault(uuid, 0);
    }

    public void addSciencePoints(UUID uuid, double amount) {
        sciencePointsCache.merge(uuid, amount, Double::sum);
        savePoints(uuid);
    }

    public boolean spendSciencePoints(UUID uuid, double amount) {
        double current = getSciencePoints(uuid);
        if (current < amount) return false;
        sciencePointsCache.put(uuid, current - amount);
        savePoints(uuid);
        return true;
    }

    public boolean isResearched(UUID uuid, String techId) {
        Set<String> set = researchedTechCache.getOrDefault(uuid, Collections.emptySet());
        return set.contains(techId);
    }

    public boolean research(UUID uuid, String techId) {
        String[] info = TECHNOLOGIES.get(techId);
        if (info == null) return false;
        if (isResearched(uuid, techId)) return false;

        // Проверяем зависимость
        String dependency = info[3];
        if (dependency != null && !isResearched(uuid, dependency)) return false;

        double cost = Double.parseDouble(info[2]);
        if (!spendSciencePoints(uuid, cost)) return false;

        researchedTechCache.computeIfAbsent(uuid, k -> new HashSet<>()).add(techId);
        saveTechnologies(uuid);
        return true;
    }

    public Set<String> getResearched(UUID uuid) {
        return researchedTechCache.getOrDefault(uuid, new HashSet<>());
    }

    private void savePoints(UUID uuid) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE player_data SET science_points = ?, science_level = ? WHERE uuid = ?")) {
            ps.setDouble(1, getSciencePoints(uuid));
            ps.setInt(2, getScienceLevel(uuid));
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void saveTechnologies(UUID uuid) {
        try {
            // Удаляем старые записи
            try (PreparedStatement del = db.getConnection().prepareStatement(
                    "DELETE FROM technologies WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            // Вставляем текущие
            for (String tech : getResearched(uuid)) {
                try (PreparedStatement ins = db.getConnection().prepareStatement(
                        "INSERT INTO technologies(uuid, tech_id) VALUES(?,?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, tech);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void saveAll() {
        for (UUID uuid : sciencePointsCache.keySet()) {
            savePoints(uuid);
            saveTechnologies(uuid);
        }
    }

    public void startGeneration() {
        long intervalTicks = 20L * 60L * 60L;
        new BukkitRunnable() {
            @Override
            public void run() {
                generateAll();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    private void generateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            String country = countryManager.getCountryName(uuid);
            if (country == null) continue;

            double baseRate = 0.5;
            int scienceLevel = getScienceLevel(uuid);
            baseRate += scienceLevel * 0.5;

            int upgradedChunks = chunkUpgradeManager.countUpgradedChunks(country);
            double chunkBonus = upgradedChunks * 0.1;

            addSciencePoints(uuid, baseRate + chunkBonus);
        }
    }

    public boolean upgradeScience(Player player) {
        UUID uuid = player.getUniqueId();
        int currentLevel = getScienceLevel(uuid);
        int maxLevel = plugin.getConfig().getInt("science.max-level", 10);
        if (currentLevel >= maxLevel) return false;

        double cost = plugin.getConfig().getDouble("science.upgrade-costs." + (currentLevel + 1),
                plugin.getConfig().getDouble("science.upgrade-cost", 10000.0));
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(player, cost)) return false;

        eco.withdraw(player, cost);
        scienceLevelCache.put(uuid, currentLevel + 1);
        savePoints(uuid);
        return true;
    }

    public boolean hasNextScienceUpgrade(UUID uuid) {
        int maxLevel = plugin.getConfig().getInt("science.max-level", 10);
        return getScienceLevel(uuid) < maxLevel;
    }

    public double getNextScienceUpgradeCost(UUID uuid) {
        int next = getScienceLevel(uuid) + 1;
        return plugin.getConfig().getDouble("science.upgrade-costs." + next,
                plugin.getConfig().getDouble("science.upgrade-cost", 10000.0));
    }
}
