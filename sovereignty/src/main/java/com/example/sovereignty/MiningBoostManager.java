package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MiningBoostManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public MiningBoostManager(SovereigntyPlugin plugin, DatabaseManager db, CountryManager countryManager) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = countryManager;
    }

    public boolean activate(Player player) {
        UUID uuid = player.getUniqueId();
        String country = countryManager.getCountryName(uuid);
        if (country == null) {
            player.sendMessage("§cСоздайте страну.");
            return false;
        }

        long cooldownMs = plugin.getConfig().getLong("mining-boost.cooldown-minutes", 60) * 60000L;
        long lastUsed = cooldowns.getOrDefault(uuid, 0L);
        long now = System.currentTimeMillis();
        if (now - lastUsed < cooldownMs) {
            long remainingMin = (cooldownMs - (now - lastUsed)) / 60000L + 1;
            player.sendMessage("§cКулдаун ещё активен. Осталось: " + remainingMin + " мин.");
            return false;
        }

        int miningChunks = countMiningChunks(country);
        if (miningChunks == 0) {
            player.sendMessage("§cУ вас нет шахтёрских чанков.");
            return false;
        }

        int level = Math.min(plugin.getConfig().getInt("mining-boost.max-level", 3),
                plugin.getConfig().getInt("mining-boost.base-level", 1)
                        + miningChunks * plugin.getConfig().getInt("mining-boost.level-per-chunk", 1) - 1);
        int durationSeconds = plugin.getConfig().getInt("mining-boost.base-duration-seconds", 900)
                + miningChunks * plugin.getConfig().getInt("mining-boost.duration-per-chunk-seconds", 60);
        durationSeconds = Math.min(durationSeconds, plugin.getConfig().getInt("mining-boost.max-duration-seconds", 3600));

        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationSeconds * 20, level - 1, true, true));
        cooldowns.put(uuid, now);

        player.sendMessage("§aШахтёрский бонус активирован! Уровень Спешки: " + level +
                ", длительность: " + (durationSeconds / 60) + " мин.");
        return true;
    }

    private int countMiningChunks(String countryName) {
        String sql = "SELECT COUNT(*) FROM chunks WHERE country_name=? AND chunk_type='mining'";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
