package com.example.sovereignty;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Раз в сутки сохраняет снимок количества чанков каждой страны.
 * Используется для расчёта "роста за 7 дней" (activity score на сайте).
 *
 * Ключ: day_start = начало календарного дня UTC (System.currentTimeMillis() / 86400000L * 86400000L).
 * Если день уже начался и снимок сделан — при следующем запуске он не перезапишется (INSERT OR IGNORE).
 */
public class CountrySnapshotManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;
    private final int keepDays;
    private BukkitTask task;

    private static final long DAY_MS = 86_400_000L;

    public CountrySnapshotManager(SovereigntyPlugin plugin, DatabaseManager db, CountryManager cm) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = cm;

        if (!plugin.getConfig().isSet("country-snapshots.keep-days")) {
            plugin.getConfig().set("country-snapshots.keep-days", 30);
            plugin.saveConfig();
        }
        this.keepDays = Math.max(7, plugin.getConfig().getInt("country-snapshots.keep-days", 30));
    }

    public void start() {
        // Каждый час проверяем, не начался ли новый день, и делаем снимок.
        task = new BukkitRunnable() {
            @Override
            public void run() {
                snapshotIfNeeded();
                cleanup();
            }
        }.runTaskTimer(plugin, 600L, 20L * 60L * 60L);

        // Первый снимок — сразу при старте (если ещё не сделан сегодня)
        snapshotIfNeeded();
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void snapshotIfNeeded() {
        long dayStart = System.currentTimeMillis() / DAY_MS * DAY_MS;
        for (String country : countryManager.getAllCountries()) {
            int claims = countryManager.getClaimCount(country);
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "INSERT OR IGNORE INTO country_snapshots(country_name, day_start, claims) VALUES(?,?,?)")) {
                ps.setString(1, country);
                ps.setLong(2, dayStart);
                ps.setInt(3, claims);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Snapshots] Ошибка записи: " + e.getMessage());
            }
        }
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - (long) keepDays * DAY_MS;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM country_snapshots WHERE day_start < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Snapshots] Ошибка очистки: " + e.getMessage());
        }
    }

    /** Возвращает количество чанков страны на ближайший снимок 7 дней назад, или -1 если данных нет. */
    public int getClaims7DaysAgo(String countryName) {
        long weekAgo = System.currentTimeMillis() - 7 * DAY_MS;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT claims FROM country_snapshots WHERE country_name=? AND day_start <= ? ORDER BY day_start DESC LIMIT 1")) {
            ps.setString(1, countryName);
            ps.setLong(2, weekAgo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("claims");
        } catch (SQLException e) {
            plugin.getLogger().warning("[Snapshots] Ошибка чтения: " + e.getMessage());
        }
        return -1;
    }

    /** Возвращает дельту чанков за 7 дней (0, если данных нет). */
    public int getClaimsDelta7d(String countryName, int currentClaims) {
        int old = getClaims7DaysAgo(countryName);
        if (old < 0) return 0;
        return currentClaims - old;
    }
}
