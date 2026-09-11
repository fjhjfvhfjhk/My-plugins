package com.example.auction;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final AuctionPlugin plugin;
    private Connection connection;

    public DatabaseManager(AuctionPlugin plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        File dbFile = new File(dataFolder, "auctions.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось подключиться к БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) connect();
        } catch (SQLException ignored) {}
        return connection;
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS auctions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seller_uuid TEXT NOT NULL,
                item_data TEXT NOT NULL, -- YAML сериализация
                start_price REAL NOT NULL,
                current_price REAL NOT NULL,
                current_bidder_uuid TEXT,
                end_time BIGINT NOT NULL,
                status TEXT NOT NULL -- 'active', 'ended', 'cancelled'
            )
        """);
        stmt.close();
    }

    public int createAuction(UUID seller, ItemStack item, double startPrice, long endTime) {
        String itemYaml = ItemSerializer.serialize(item);
        String sql = "INSERT INTO auctions(seller_uuid, item_data, start_price, current_price, end_time, status) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.toString());
            ps.setString(2, itemYaml);
            ps.setDouble(3, startPrice);
            ps.setDouble(4, startPrice);
            ps.setLong(5, endTime);
            ps.setString(6, "active");
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public List<Auction> getActiveAuctions() {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status='active' ORDER BY id DESC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public Auction getAuction(int id) {
        String sql = "SELECT * FROM auctions WHERE id=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapResultSet(rs);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean updateBid(int id, double newPrice, UUID bidder) {
        String sql = "UPDATE auctions SET current_price=?, current_bidder_uuid=? WHERE id=? AND status='active'";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setString(2, bidder.toString());
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean setStatus(int id, String status) {
        String sql = "UPDATE auctions SET status=? WHERE id=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean deleteAuction(int id) {
        String sql = "DELETE FROM auctions WHERE id=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    private Auction mapResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID seller = UUID.fromString(rs.getString("seller_uuid"));
        String itemData = rs.getString("item_data");
        ItemStack item = ItemSerializer.deserialize(itemData);
        double startPrice = rs.getDouble("start_price");
        double currentPrice = rs.getDouble("current_price");
        String bidderStr = rs.getString("current_bidder_uuid");
        UUID currentBidder = bidderStr != null ? UUID.fromString(bidderStr) : null;
        long endTime = rs.getLong("end_time");
        String status = rs.getString("status");
        return new Auction(id, seller, item, startPrice, currentPrice, currentBidder, endTime, status);
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
