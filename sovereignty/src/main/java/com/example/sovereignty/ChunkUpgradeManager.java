package com.example.sovereignty;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

public class ChunkUpgradeManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;
    private final EconomyManager economyManager;

    public ChunkUpgradeManager(SovereigntyPlugin plugin, DatabaseManager db,
                               CountryManager countryManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = countryManager;
        this.economyManager = economyManager;
    }

    public boolean setChunkType(Player player, String type) {
        String countryName = countryManager.getCountryName(player.getUniqueId());
        if (countryName == null) return false;

        Chunk chunk = player.getLocation().getChunk();
        String owner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (!countryName.equals(owner)) return false;

        double cost = plugin.getConfig().getDouble("chunk-upgrades." + type + ".cost", 0.0);
        if (cost > 0 && economyManager.isEnabled()) {
            if (!economyManager.has(player, cost)) return false;
            economyManager.withdraw(player, cost);
        }

        String sql = "UPDATE chunks SET chunk_type = ? WHERE country_name = ? AND world = ? AND chunk_x = ? AND chunk_z = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, countryName);
            ps.setString(3, chunk.getWorld().getName());
            ps.setInt(4, chunk.getX());
            ps.setInt(5, chunk.getZ());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getChunkType(World world, int x, int z) {
        String sql = "SELECT chunk_type FROM chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, world.getName());
            ps.setInt(2, x);
            ps.setInt(3, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("chunk_type");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "normal";
    }

    public boolean isDefensive(World world, int x, int z) {
        String sql = "SELECT defensive FROM chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, world.getName());
            ps.setInt(2, x);
            ps.setInt(3, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("defensive") == 1;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean setDefensive(Player player, boolean defensive) {
        String countryName = countryManager.getCountryName(player.getUniqueId());
        if (countryName == null) return false;
        Chunk chunk = player.getLocation().getChunk();
        String owner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (!countryName.equals(owner)) return false;
        return plugin.getDefensiveManager().setDefensive(countryName, chunk.getWorld(), chunk.getX(), chunk.getZ(), defensive);
    }

    public double calculatePassiveIncome(String countryName) {
        UUID ownerUuid = countryManager.getOwner(countryName);
        int farmLevel = ownerUuid != null ? plugin.getEnergyManager().getFarmUpgradeLevel(ownerUuid) : 0;
        double baseFarmIncome = plugin.getConfig().getDouble("chunk-upgrades.farm.income", 100.0);
        double farmIncomeStep = plugin.getConfig().getDouble("chunk-upgrades.farm.income-step", 100.0);
        double farmIncomePerChunk = baseFarmIncome + farmLevel * farmIncomeStep;
        double tradeIncome = plugin.getConfig().getDouble("chunk-upgrades.trade.income", 50.0);
        double tradeBonusPercent = plugin.getConfig().getDouble("chunk-upgrades.trade.farm-bonus-percent", 10.0);

        int farmCount = countType(countryName, "farm");
        int tradeCount = countType(countryName, "trade");

        // Множители событий
        double farmMultiplier = plugin.getEventManager().getFarmIncomeMultiplier();
        double tradeMultiplier = plugin.getEventManager().getTradeIncomeMultiplier();

        // Технологии
        if (ownerUuid != null) {
            ScienceManager sm = plugin.getScienceManager();
            if (sm.isResearched(ownerUuid, "farming")) {
                farmMultiplier *= 1.10;
            }
            if (sm.isResearched(ownerUuid, "trade_tech")) {
                tradeMultiplier *= 1.05;
            }
        }

        double income = farmCount * farmIncomePerChunk * farmMultiplier;
        income += tradeCount * tradeIncome * tradeMultiplier;
        if (tradeCount > 0) {
            income += farmCount * farmIncomePerChunk * farmMultiplier * (tradeBonusPercent / 100.0) * tradeCount * tradeMultiplier;
        }
        return income;
    }

    public double getTradeBonus(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String type = getChunkType(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (type.equals("trade")) {
            return plugin.getConfig().getDouble("chunk-upgrades.trade.shop-bonus-percent", 10.0);
        }
        return 0.0;
    }

    public boolean isMilitaryChunk(World world, int x, int z) {
        return getChunkType(world, x, z).equals("military");
    }

    public boolean isMiningChunk(World world, int x, int z) {
        return getChunkType(world, x, z).equals("mining");
    }

    public int countUpgradedChunks(String countryName) {
        String sql = "SELECT COUNT(*) FROM chunks WHERE country_name = ? AND chunk_type != 'normal'";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int countType(String countryName, String type) {
        String sql = "SELECT COUNT(*) FROM chunks WHERE country_name = ? AND chunk_type = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ps.setString(2, type);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
