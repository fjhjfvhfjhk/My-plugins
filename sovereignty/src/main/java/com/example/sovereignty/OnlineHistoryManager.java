package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Раз в N минут записывает текущее число онлайна в online_history.
 * Хранит последние keep-hours часов. Для графика за 24 часа на сайте.
 */
public class OnlineHistoryManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final int intervalMinutes;
    private final int keepHours;
    private BukkitTask task;

    public OnlineHistoryManager(SovereigntyPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;

        if (!plugin.getConfig().isSet("online-history.interval-minutes")) {
            plugin.getConfig().set("online-history.interval-minutes", 5);
            plugin.getConfig().set("online-history.keep-hours", 24);
            plugin.saveConfig();
        }
        this.intervalMinutes = Math.max(1, plugin.getConfig().getInt("online-history.interval-minutes", 5));
        this.keepHours = Math.max(1, plugin.getConfig().getInt("online-history.keep-hours", 24));
    }

    public void start() {
        long periodTicks = 20L * 60L * intervalMinutes;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                snapshot();
                cleanup();
            }
        }.runTaskTimer(plugin, 200L, periodTicks);
        plugin.getLogger().info("[OnlineHistory] Запущен, интервал " + intervalMinutes + " мин, хранение " + keepHours + " ч.");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void snapshot() {
        int online = Bukkit.getOnlinePlayers().size();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO online_history(timestamp, count) VALUES(?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, online);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[OnlineHistory] Ошибка записи: " + e.getMessage());
        }
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - (long) keepHours * 3600_000L;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM online_history WHERE timestamp < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[OnlineHistory] Ошибка очистки: " + e.getMessage());
        }
    }

    /** Возвращает историю за последние keep-hours часов: список [timestamp, count]. */
    public List<long[]> getHistory() {
        List<long[]> list = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - (long) keepHours * 3600_000L;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT timestamp, count FROM online_history WHERE timestamp >= ? ORDER BY timestamp ASC")) {
            ps.setLong(1, cutoff);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new long[]{ rs.getLong("timestamp"), rs.getInt("count") });
        } catch (SQLException e) {
            plugin.getLogger().warning("[OnlineHistory] Ошибка чтения: " + e.getMessage());
        }
        return list;
    }
}
