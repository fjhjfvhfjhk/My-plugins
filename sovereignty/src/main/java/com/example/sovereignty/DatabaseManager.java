package com.example.sovereignty;

import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final SovereigntyPlugin plugin;
    private Connection connection;

    public DatabaseManager(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        File dbFile = new File(dataFolder, "sovereignty.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            createTables();
            migrateTables();
            plugin.getLogger().info("SQLite подключена.");
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось подключиться к базе данных: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                plugin.getLogger().warning("Соединение с БД закрыто, переподключаюсь...");
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка проверки соединения: " + e.getMessage());
            connect();
        }
        return connection;
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS countries (
                name TEXT PRIMARY KEY,
                owner_uuid TEXT NOT NULL UNIQUE
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS chunks (
                country_name TEXT NOT NULL,
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                chunk_type TEXT NOT NULL DEFAULT 'normal',
                defensive INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (world, chunk_x, chunk_z)
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS relations (
                country_a TEXT NOT NULL,
                country_b TEXT NOT NULL,
                relation TEXT NOT NULL,
                PRIMARY KEY (country_a, country_b)
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS player_data (
                uuid TEXT PRIMARY KEY,
                energy REAL NOT NULL,
                max_level INTEGER NOT NULL,
                regen_level INTEGER NOT NULL,
                last_regen BIGINT NOT NULL,
                boost_until BIGINT NOT NULL,
                chunk_limit_level INTEGER NOT NULL,
                farm_upgrade_level INTEGER NOT NULL DEFAULT 0,
                science_points REAL NOT NULL DEFAULT 0,
                science_level INTEGER NOT NULL DEFAULT 0
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS technologies (
                uuid TEXT NOT NULL,
                tech_id TEXT NOT NULL,
                PRIMARY KEY (uuid, tech_id)
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS country_bank (
                country_name TEXT PRIMARY KEY,
                balance REAL NOT NULL,
                debt REAL NOT NULL DEFAULT 0,
                diplomacy_ban_until BIGINT NOT NULL DEFAULT 0
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS pacts (
                country_a TEXT NOT NULL,
                country_b TEXT NOT NULL,
                pact_type TEXT NOT NULL,
                PRIMARY KEY (country_a, country_b, pact_type)
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS wars (
                attacker TEXT NOT NULL,
                defender TEXT NOT NULL,
                PRIMARY KEY (attacker, defender)
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS achievements (
                uuid TEXT NOT NULL,
                achievement_id TEXT NOT NULL,
                claimed INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, achievement_id)
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS court_cases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plaintiff TEXT NOT NULL,
                defendant TEXT NOT NULL,
                reason TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS court_votes (
                case_id INTEGER NOT NULL,
                voter TEXT NOT NULL,
                vote INTEGER NOT NULL,
                sanctions TEXT,
                PRIMARY KEY (case_id, voter)
            )
        """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS co_rulers (
                country_name TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                PRIMARY KEY (country_name, player_uuid)
            )
        """);

        // НОВАЯ ТАБЛИЦА: персистентный кэш terrain-чанков (как у Xaero)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS terrain_cache (
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                png BLOB NOT NULL,
                last_update BIGINT NOT NULL,
                PRIMARY KEY (world, chunk_x, chunk_z)
            )
        """);

        // Индекс по last_update для быстрой очистки старых
        stmt.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_terrain_last_update
            ON terrain_cache(last_update)
        """);

        stmt.close();
    }

    private void migrateTables() {
        try {
            if (!columnExists("chunks", "chunk_type")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE chunks ADD COLUMN chunk_type TEXT NOT NULL DEFAULT 'normal'");
                }
            }
            if (!columnExists("chunks", "defensive")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE chunks ADD COLUMN defensive INTEGER NOT NULL DEFAULT 0");
                }
            }
            if (!columnExists("player_data", "farm_upgrade_level")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN farm_upgrade_level INTEGER NOT NULL DEFAULT 0");
                }
            }
            if (!columnExists("player_data", "science_points")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN science_points REAL NOT NULL DEFAULT 0");
                }
            }
            if (!columnExists("player_data", "science_level")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN science_level INTEGER NOT NULL DEFAULT 0");
                }
            }
            if (!columnExists("country_bank", "debt")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE country_bank ADD COLUMN debt REAL NOT NULL DEFAULT 0");
                }
            }
            if (!columnExists("country_bank", "diplomacy_ban_until")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE country_bank ADD COLUMN diplomacy_ban_until BIGINT NOT NULL DEFAULT 0");
                }
            }
            if (!columnExists("court_votes", "sanctions")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE court_votes ADD COLUMN sanctions TEXT");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка миграции базы данных: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + tableName + ")")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getString("name").equals(columnName)) return true;
            }
        }
        return false;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка закрытия базы данных: " + e.getMessage());
        }
    }
}
