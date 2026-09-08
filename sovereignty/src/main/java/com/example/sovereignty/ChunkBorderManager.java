package com.example.sovereignty;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;

public class ChunkBorderManager {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final Map<UUID, BukkitRunnable> tasks = new HashMap<>();
    private final Map<UUID, Integer> modes = new HashMap<>();

    public ChunkBorderManager(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    public int getMode(UUID uuid) {
        return modes.getOrDefault(uuid, 0);
    }

    public void setMode(Player player, int mode) {
        UUID uuid = player.getUniqueId();
        modes.put(uuid, mode);

        if (tasks.containsKey(uuid)) {
            tasks.remove(uuid).cancel();
        }

        if (mode == 0) {
            player.sendMessage("§eОтображение границ выключено.");
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                drawBorders(player, modes.getOrDefault(player.getUniqueId(), 0));
            }
        };
        task.runTaskTimer(plugin, 0L, 5L);
        tasks.put(uuid, task);

        player.sendMessage(mode == 1
                ? "§aРежим: границы каждого чанка страны."
                : "§aРежим: внешний контур страны.");
    }

    private void drawBorders(Player player, int mode) {
        String countryName = countryManager.getCountryName(player.getUniqueId());
        if (countryName == null) return;

        List<ChunkPos> chunks = getCountryChunks(countryName);
        if (chunks.isEmpty()) return;

        World world = player.getWorld();
        double y = player.getLocation().getY() + 0.5;
        int maxDraw = plugin.getConfig().getInt("see-chunk-max-draw", 500);

        if (mode == 1) {
            for (int i = 0; i < Math.min(chunks.size(), maxDraw); i++) {
                drawChunkPerimeter(world, chunks.get(i).x, chunks.get(i).z, y, false, null);
            }
        } else if (mode == 2) {
            Set<String> chunkSet = new HashSet<>();
            for (ChunkPos p : chunks) chunkSet.add(p.x + ":" + p.z);
            for (int i = 0; i < Math.min(chunks.size(), maxDraw); i++) {
                ChunkPos p = chunks.get(i);
                drawChunkPerimeter(world, p.x, p.z, y, true, chunkSet);
            }
        }
    }

    private void drawChunkPerimeter(World world, int chunkX, int chunkZ, double y, boolean onlyExternal, Set<String> chunkSet) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        boolean drawNorth = !onlyExternal || !hasNeighbor(chunkSet, chunkX, chunkZ - 1);
        boolean drawSouth = !onlyExternal || !hasNeighbor(chunkSet, chunkX, chunkZ + 1);
        boolean drawWest = !onlyExternal || !hasNeighbor(chunkSet, chunkX - 1, chunkZ);
        boolean drawEast = !onlyExternal || !hasNeighbor(chunkSet, chunkX + 1, chunkZ);

        // Рисуем с шагом 2 блока, но на каждой точке спавним несколько частиц для яркости
        if (drawNorth) for (int x = minX; x <= maxX; x += 2) spawnBrightParticle(world, x + 0.5, y, minZ + 0.5);
        if (drawSouth) for (int x = minX; x <= maxX; x += 2) spawnBrightParticle(world, x + 0.5, y, maxZ + 0.5);
        if (drawWest) for (int z = minZ; z <= maxZ; z += 2) spawnBrightParticle(world, minX + 0.5, y, z + 0.5);
        if (drawEast) for (int z = minZ; z <= maxZ; z += 2) spawnBrightParticle(world, maxX + 0.5, y, z + 0.5);
    }

    /**
     * Спавнит несколько частиц с небольшим разбросом, чтобы граница была ярче и толще.
     */
    private void spawnBrightParticle(World world, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double offsetX = (Math.random() - 0.5) * 0.3;
            double offsetY = (Math.random() - 0.5) * 0.2;
            double offsetZ = (Math.random() - 0.5) * 0.3;
            world.spawnParticle(Particle.FLAME, x + offsetX, y + offsetY, z + offsetZ, 0, 0, 0, 0, 1);
        }
    }

    private boolean hasNeighbor(Set<String> chunkSet, int x, int z) {
        return chunkSet != null && chunkSet.contains(x + ":" + z);
    }

    private List<ChunkPos> getCountryChunks(String countryName) {
        List<ChunkPos> result = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT chunk_x, chunk_z FROM chunks WHERE country_name = ?")) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(new ChunkPos(rs.getInt("chunk_x"), rs.getInt("chunk_z")));
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    private static class ChunkPos {
        final int x;
        final int z;
        ChunkPos(int x, int z) { this.x = x; this.z = z; }
    }
}
