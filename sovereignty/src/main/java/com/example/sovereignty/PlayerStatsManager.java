package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Хранит статистику игроков: first_seen, last_seen, playtime_seconds, последняя позиция.
 *
 * playtime берётся из Bukkit Statistic (тики) и дублируется в БД при quit,
 * чтобы оффлайн-игроки тоже имели актуальное значение.
 *
 * При старте Sovereignty читает player_stats_import.yml — файл с базовыми
 * данными о игроках, которые зашли до установки плагина. См. importFromYaml().
 */
public class PlayerStatsManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;

    public PlayerStatsManager(SovereigntyPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ==================== ЧТЕНИЕ ====================

    public long getFirstSeen(UUID uuid) {
        return getLong(uuid, "first_seen", 0L);
    }

    public long getLastSeen(UUID uuid) {
        return getLong(uuid, "last_seen", 0L);
    }

    public long getPlaytimeSeconds(UUID uuid) {
        return getLong(uuid, "playtime_seconds", 0L);
    }

    private long getLong(UUID uuid, String column, long def) {
        String sql = "SELECT " + column + " FROM player_stats WHERE uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(column);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка чтения " + column + ": " + e.getMessage());
        }
        return def;
    }

    // ==================== ЗАПИСЬ ====================

    /** При входе: если first_seen пуст — заполняем, всегда обновляем last_seen. */
    public void onJoin(UUID uuid) {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO player_stats(uuid, first_seen, last_seen) VALUES(?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET last_seen=excluded.last_seen";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка onJoin: " + e.getMessage());
        }
    }

    /**
     * При выходе: сохраняем last_seen, актуальное playtime и последнюю позицию.
     *
     * playtime = max(existing_in_db, statistic_seconds).
     * Это нужно, чтобы не потерять данные, влитые через player_stats_import.yml:
     * если сервер свежий и Statistic маленький, а в БД лежит импортное значение
     * (271138 секунд и т.п.) — оставляем большее.
     */
    public void onQuit(UUID uuid, long statisticPlaytimeSeconds, Location location) {
        long now = System.currentTimeMillis();
        long existingPlaytime = getPlaytimeSeconds(uuid);
        long finalPlaytime = Math.max(existingPlaytime, statisticPlaytimeSeconds);

        boolean hasLocation = location != null && location.getWorld() != null;
        String sql;
        if (hasLocation) {
            sql = "UPDATE player_stats SET last_seen=?, playtime_seconds=?, " +
                    "last_world=?, last_x=?, last_y=?, last_z=? WHERE uuid=?";
        } else {
            sql = "UPDATE player_stats SET last_seen=?, playtime_seconds=? WHERE uuid=?";
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, now);
            ps.setLong(i++, finalPlaytime);
            if (hasLocation) {
                ps.setString(i++, location.getWorld().getName());
                ps.setDouble(i++, location.getX());
                ps.setDouble(i++, location.getY());
                ps.setDouble(i++, location.getZ());
            }
            ps.setString(i, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка onQuit: " + e.getMessage());
        }
    }

    /** Сохранённая позиция (для оффлайн; можно использовать при onJoin). */
    public Location getLastLocation(UUID uuid) {
        String sql = "SELECT last_world, last_x, last_y, last_z FROM player_stats WHERE uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String w = rs.getString("last_world");
                if (w == null) return null;
                World world = Bukkit.getWorld(w);
                if (world == null) return null;
                return new Location(world, rs.getDouble("last_x"), rs.getDouble("last_y"), rs.getDouble("last_z"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка чтения позиции: " + e.getMessage());
        }
        return null;
    }

    // ==================== ИМПОРТ ИЗ YAML ====================

    /**
     * Читает player_stats_import.yml и вливает данные в БД.
     *
     * UUID вычисляется как offline-UUID от ника:
     *   UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8))
     * Именно так Bukkit генерирует UUID для пиратских аккаунтов.
     */
    public void importFromYaml() {
        File file = new File(plugin.getDataFolder(), "player_stats_import.yml");
        if (!file.exists()) {
            plugin.getLogger().info("[Stats] player_stats_import.yml не найден — импорт пропущен.");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null || section.getKeys(false).isEmpty()) {
            plugin.getLogger().info("[Stats] player_stats_import.yml пуст — импорт пропущен.");
            return;
        }

        int imported = 0;
        int updated = 0;
        for (String name : section.getKeys(false)) {
            long firstSeen = section.getLong(name + ".first_seen", 0);
            long playtime = section.getLong(name + ".playtime_seconds", 0);
            if (firstSeen <= 0 && playtime <= 0) continue;

            UUID uuid = offlineUuid(name);
            int res = updateOrInsertFromImport(uuid, firstSeen, playtime);
            if (res == 1) imported++;
            else if (res == 2) updated++;
        }

        plugin.getLogger().info("[Stats] Импорт из player_stats_import.yml: создано " + imported +
                ", обновлено " + updated + " записей.");

        if ((imported > 0 || updated > 0) && plugin.getWebPanelUploader() != null) {
            plugin.getWebPanelUploader().scheduleForcePush();
        }
    }

    /** @return 0 — ничего не делали; 1 — создали; 2 — обновили. */
    private int updateOrInsertFromImport(UUID uuid, long firstSeen, long playtime) {
        long curFirst = 0;
        long curPlay = 0;
        boolean exists = false;

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT first_seen, playtime_seconds FROM player_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                exists = true;
                curFirst = rs.getLong("first_seen");
                curPlay = rs.getLong("playtime_seconds");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка чтения при импорте: " + e.getMessage());
            return 0;
        }

        // first_seen: берём самый ранний из импорта и БД
        long newFirst = curFirst;
        if (firstSeen > 0 && (curFirst == 0 || firstSeen < curFirst)) {
            newFirst = firstSeen;
        }
        // playtime: берём максимум
        long newPlay = Math.max(curPlay, playtime);

        // Если ничего не меняется — не трогаем БД
        if (exists && newFirst == curFirst && newPlay == curPlay) {
            return 0;
        }

        try {
            if (exists) {
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "UPDATE player_stats SET first_seen=?, playtime_seconds=? WHERE uuid=?")) {
                    ps.setLong(1, newFirst);
                    ps.setLong(2, newPlay);
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }
                return 2;
            } else {
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "INSERT INTO player_stats(uuid, first_seen, last_seen, playtime_seconds) VALUES(?,?,?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setLong(2, newFirst);
                    ps.setLong(3, newFirst); // last_seen = first_seen для импорта
                    ps.setLong(4, newPlay);
                    ps.executeUpdate();
                }
                return 1;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка записи при импорте: " + e.getMessage());
            return 0;
        }
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    // ==================== ОТБОР ====================

    /** UUID игроков, у которых есть страна (лидеры и соправители). */
    public Set<UUID> getAllWithCountry() {
        Set<UUID> result = new LinkedHashSet<>();

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT owner_uuid FROM countries")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                try { result.add(UUID.fromString(rs.getString("owner_uuid"))); }
                catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка чтения лидеров: " + e.getMessage());
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT player_uuid FROM co_rulers")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                try { result.add(UUID.fromString(rs.getString("player_uuid"))); }
                catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Stats] Ошибка чтения соправителей: " + e.getMessage());
        }

        return result;
    }

    public boolean hasStats(UUID uuid) {
        String sql = "SELECT 1 FROM player_stats WHERE uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
}
