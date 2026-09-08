package com.example.sovereignty;

import org.bukkit.World;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DefensiveManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;

    private final Map<String, Long> cooldowns = new HashMap<>();

    public DefensiveManager(SovereigntyPlugin plugin, DatabaseManager db, CountryManager countryManager) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = countryManager;
    }

    public boolean setDefensive(String countryName, World world, int x, int z, boolean defensive) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE chunks SET defensive=? WHERE country_name=? AND world=? AND chunk_x=? AND chunk_z=?")) {
            ps.setInt(1, defensive ? 1 : 0);
            ps.setString(2, countryName);
            ps.setString(3, world.getName());
            ps.setInt(4, x);
            ps.setInt(5, z);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isDefended(World world, int x, int z) {
        String key = world.getName() + ":" + x + ":" + z;
        Long cooldownEnd = cooldowns.get(key);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            return false;
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT defensive FROM chunks WHERE world=? AND chunk_x=? AND chunk_z=?")) {
            ps.setString(1, world.getName());
            ps.setInt(2, x);
            ps.setInt(3, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("defensive") == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void onPlayerDeath(java.util.UUID playerUuid) {
        String countryName = countryManager.getCountryName(playerUuid);
        if (countryName == null) return;

        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT world, chunk_x, chunk_z FROM chunks WHERE country_name=? AND defensive=1")) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            long cooldownMs = 60000 + (long)(Math.random() * 60000);
            long end = System.currentTimeMillis() + cooldownMs;
            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("chunk_x");
                int z = rs.getInt("chunk_z");
                cooldowns.put(world + ":" + x + ":" + z, end);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
