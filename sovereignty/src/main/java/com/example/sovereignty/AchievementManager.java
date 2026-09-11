package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Достижения с предметными наградами.
 */
public class AchievementManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;
    private final EnergyManager energyManager;
    private final ChunkUpgradeManager chunkUpgradeManager;

    private final Map<String, ItemStack> rewards = new HashMap<>();

    public AchievementManager(SovereigntyPlugin plugin, DatabaseManager db,
                              CountryManager countryManager, EnergyManager energyManager,
                              ChunkUpgradeManager chunkUpgradeManager) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = countryManager;
        this.energyManager = energyManager;
        this.chunkUpgradeManager = chunkUpgradeManager;
        initRewards();
    }

    private void initRewards() {
        rewards.put("territory_25", new ItemStack(Material.DIAMOND, 16));
        rewards.put("territory_100", new ItemStack(Material.DIAMOND, 32));
        rewards.put("territory_200", new ItemStack(Material.DIAMOND, 64));

        rewards.put("economy_50k", new ItemStack(Material.EMERALD, 16));
        rewards.put("economy_500k", new ItemStack(Material.EMERALD, 32));
        rewards.put("economy_1m", new ItemStack(Material.EMERALD, 64));

        rewards.put("energy_max", new ItemStack(Material.GOLDEN_APPLE, 8));

        rewards.put("upgraded_1", new ItemStack(Material.DIAMOND, 8));
        rewards.put("upgraded_10", new ItemStack(Material.DIAMOND, 16));
        rewards.put("upgraded_20", new ItemStack(Material.DIAMOND, 32));
        rewards.put("upgraded_50", new ItemStack(Material.DIAMOND, 64));

        rewards.put("max_all_upgrades", new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        rewards.put("alliances_5", new ItemStack(Material.TOTEM_OF_UNDYING, 2));
        rewards.put("alliances_10", new ItemStack(Material.TOTEM_OF_UNDYING, 5));
        rewards.put("military_pacts_3", new ItemStack(Material.TOTEM_OF_UNDYING, 3));
    }

    public void checkAchievements(Player player) {
        UUID uuid = player.getUniqueId();
        String country = countryManager.getCountryName(uuid);
        if (country == null) return;

        int claims = countryManager.getClaimCount(country);
        double bank = countryManager.getBankBalance(country);
        double energy = energyManager.getEnergy(uuid);
        double maxEnergy = energyManager.getMaxEnergy(uuid);
        int upgradedChunks = chunkUpgradeManager.countUpgradedChunks(country);

        checkAndAward(uuid, country, "territory_25", claims >= 25, 5000, 10, rewards.get("territory_25"));
        checkAndAward(uuid, country, "territory_100", claims >= 100, 25000, 25, rewards.get("territory_100"));
        checkAndAward(uuid, country, "territory_200", claims >= 200, 60000, 50, rewards.get("territory_200"));

        checkAndAward(uuid, country, "economy_50k", bank >= 50000, 10000, 0, rewards.get("economy_50k"));
        checkAndAward(uuid, country, "economy_500k", bank >= 500000, 50000, 0, rewards.get("economy_500k"));
        checkAndAward(uuid, country, "economy_1m", bank >= 1000000, 150000, 0, rewards.get("economy_1m"));

        checkAndAward(uuid, country, "energy_max", energy >= maxEnergy && maxEnergy >= 10, 3000, 5, rewards.get("energy_max"));

        checkAndAward(uuid, country, "upgraded_1", upgradedChunks >= 1, 1000, 0, rewards.get("upgraded_1"));
        checkAndAward(uuid, country, "upgraded_10", upgradedChunks >= 10, 8000, 5, rewards.get("upgraded_10"));
        checkAndAward(uuid, country, "upgraded_20", upgradedChunks >= 20, 20000, 10, rewards.get("upgraded_20"));
        checkAndAward(uuid, country, "upgraded_50", upgradedChunks >= 50, 50000, 20, rewards.get("upgraded_50"));

        checkAndAward(uuid, country, "max_all_upgrades", isMaxUpgrades(uuid), 100000, 50, rewards.get("max_all_upgrades"));

        int alliances = countAlliances(country);
        checkAndAward(uuid, country, "alliances_5", alliances >= 5, 5000, 0, rewards.get("alliances_5"));
        checkAndAward(uuid, country, "alliances_10", alliances >= 10, 15000, 0, rewards.get("alliances_10"));

        int militaryPacts = countPactsOfType(country, "military");
        checkAndAward(uuid, country, "military_pacts_3", militaryPacts >= 3, 8000, 0, rewards.get("military_pacts_3"));
    }

    private void checkAndAward(UUID uuid, String country, String achievementId,
                               boolean condition, double moneyReward, double energyReward, ItemStack itemReward) {
        if (!condition) return;
        if (isClaimed(uuid, achievementId)) return;

        if (moneyReward > 0) countryManager.depositToBank(country, moneyReward);
        if (energyReward > 0) energyManager.addEnergy(uuid, energyReward);
        if (itemReward != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemReward.clone());
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }

        markClaimed(uuid, achievementId);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            StringBuilder rewardMsg = new StringBuilder();
            if (moneyReward > 0) rewardMsg.append(plugin.getEconomyManager().format(moneyReward)).append(" в казну");
            if (energyReward > 0) {
                if (rewardMsg.length() > 0) rewardMsg.append(", ");
                rewardMsg.append("+").append(String.format("%.1f", energyReward)).append(" энергии");
            }
            if (itemReward != null) {
                if (rewardMsg.length() > 0) rewardMsg.append(", ");
                String itemName = itemReward.getType().name().toLowerCase().replace('_', ' ');
                rewardMsg.append(itemReward.getAmount()).append(" × ").append(itemName);
            }
            player.sendMessage("§a🏆 Достижение получено: " + getDisplayName(achievementId) +
                    "! Награда: " + rewardMsg.toString());
        }
    }

    private boolean isClaimed(UUID uuid, String achievementId) {
        String sql = "SELECT claimed FROM achievements WHERE uuid=? AND achievement_id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, achievementId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("claimed") == 1;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    private void markClaimed(UUID uuid, String achievementId) {
        String sql = "INSERT OR REPLACE INTO achievements(uuid, achievement_id, claimed) VALUES(?,?,1)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, achievementId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public Map<String, Boolean> getAchievements(UUID uuid) {
        Map<String, Boolean> result = new HashMap<>();
        String sql = "SELECT achievement_id, claimed FROM achievements WHERE uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("achievement_id"), rs.getInt("claimed") == 1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    public static Map<String, String[]> getAchievementDescriptions() {
        Map<String, String[]> map = new LinkedHashMap<>();
        map.put("territory_25", new String[]{"Расширение I", "Захватите 25 чанков"});
        map.put("territory_100", new String[]{"Расширение II", "Захватите 100 чанков"});
        map.put("territory_200", new String[]{"Империя", "Захватите 200 чанков"});
        map.put("economy_50k", new String[]{"Богатство I", "Накопите 50,000 в казне"});
        map.put("economy_500k", new String[]{"Богатство II", "Накопите 500,000 в казне"});
        map.put("economy_1m", new String[]{"Казначей", "Накопите 1,000,000 в казне"});
        map.put("energy_max", new String[]{"Полный заряд", "Накопите максимум энергии"});
        map.put("upgraded_1", new String[]{"Первый шаг", "Улучшите 1 чанк"});
        map.put("upgraded_10", new String[]{"Развитие", "Улучшите 10 чанков"});
        map.put("upgraded_20", new String[]{"Инфраструктура", "Улучшите 20 чанков"});
        map.put("upgraded_50", new String[]{"Мегаполис", "Улучшите 50 чанков"});
        map.put("max_all_upgrades", new String[]{"Совершенство", "Прокачайте все ветки до максимума"});
        map.put("alliances_5", new String[]{"Дипломат", "Заключите 5 союзов"});
        map.put("alliances_10", new String[]{"Миротворец", "Заключите 10 союзов"});
        map.put("military_pacts_3", new String[]{"Военный блок", "Заключите 3 военных пакта"});
        return map;
    }

    private String getDisplayName(String achievementId) {
        Map<String, String[]> descriptions = getAchievementDescriptions();
        String[] value = descriptions.get(achievementId);
        return value != null ? value[0] : achievementId;
    }

    private boolean isMaxUpgrades(UUID uuid) {
        EnergyManager.PlayerData data = energyManager.getData(uuid);
        int maxLevels = plugin.getConfig().getDoubleList("energy.max-levels").size();
        int regenLevels = plugin.getConfig().getDoubleList("energy.regen-levels").size();
        int chunkLimitLevels = plugin.getConfig().getDoubleList("energy.chunk-limit-upgrade-costs").size();
        int farmLevels = plugin.getConfig().getDoubleList("energy.farm-upgrade-costs").size();

        return data.maxLevel >= maxLevels - 1
                && data.regenLevel >= regenLevels - 1
                && data.chunkLimitLevel >= chunkLimitLevels - 1
                && data.farmUpgradeLevel >= farmLevels - 1;
    }

    private int countAlliances(String country) {
        int count = 0;
        for (String other : countryManager.getAllCountries()) {
            if (!other.equals(country) && countryManager.isAlly(country, other)) count++;
        }
        return count;
    }

    private int countPactsOfType(String country, String type) {
        int count = 0;
        for (String other : countryManager.getAllCountries()) {
            if (!other.equals(country) && plugin.getPactManager().hasPact(country, other, type)) count++;
        }
        return count;
    }
}
