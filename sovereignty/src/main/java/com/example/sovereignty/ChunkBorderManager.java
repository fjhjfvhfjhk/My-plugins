package com.example.sovereignty;

import org.bukkit.Color;
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

        switch (mode) {
            case 1 -> player.sendMessage("§aРежим: границы каждого чанка страны (белые).");
            case 2 -> player.sendMessage("§aРежим: внешний контур страны (белые).");
            case 3 -> player.sendMessage("§aРежим: типы чанков (цветные границы).");
            default -> player.sendMessage("§aРежим границ установлен.");
        }
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
                ChunkPos p = chunks.get(i);
                Color color = colorForType(p.type);
                drawChunkPerimeter(world, p.x, p.z, y, false, null, color);
            }
        } else if (mode == 2) {
            Set<String> chunkSet = new HashSet<>();
            for (ChunkPos p : chunks) chunkSet.add(p.x + ":" + p.z);
            for (int i = 0; i < Math.min(chunks.size(), maxDraw); i++) {
                ChunkPos p = chunks.get(i);
                Color color = colorForType(p.type);
                drawChunkPerimeter(world, p.x, p.z, y, true, chunkSet, color);
            }
        } else if (mode == 3) {
            Set<String> chunkSet = new HashSet<>();
            for (ChunkPos p : chunks) chunkSet.add(p.x + ":" + p.z);
            for (int i = 0; i < Math.min(chunks.size(), maxDraw); i++) {
                ChunkPos p = chunks.get(i);
                Color color = colorForType(p.type);
                drawChunkPerimeter(world, p.x, p.z, y, true, chunkSet, color);
            }
        }
    }

    private Color colorForType(String type) {
        if (type == null) return Color.WHITE;
        return switch (type) {
            case "farm" -> Color.fromRGB(0, 200, 0);       // зелёный
            case "mining" -> Color.fromRGB(0, 170, 255);   // голубой
            case "military" -> Color.fromRGB(255, 50, 50); // красный
            case "trade" -> Color.fromRGB(255, 200, 0);    // золотой
            default -> Color.WHITE;
        };
    }

    private void drawChunkPerimeter(World world, int chunkX, int chunkZ, double y,
                                    boolean onlyExternal, Set<String> chunkSet, Color color) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        boolean drawNorth = !onlyExternal || !hasNeighbor(chunkSet, chunkX, chunkZ - 1);
        boolean drawSouth = !onlyExternal || !hasNeighbor(chunkSet, chunkX, chunkZ + 1);
        boolean drawWest = !onlyExternal || !hasNeighbor(chunkSet, chunkX - 1, chunkZ);
        boolean drawEast = !onlyExternal || !hasNeighbor(chunkSet, chunkX + 1, chunkZ);

        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);

        if (drawNorth) for (int x = minX; x <= maxX; x += 2) spawnParticle(world, x + 0.5, y, minZ + 0.5, dust);
        if (drawSouth) for (int x = minX; x <= maxX; x += 2) spawnParticle(world, x + 0.5, y, maxZ + 0.5, dust);
        if (drawWest) for (int z = minZ; z <= maxZ; z += 2) spawnParticle(world, minX + 0.5, y, z + 0.5, dust);
        if (drawEast) for (int z = minZ; z <= maxZ; z += 2) spawnParticle(world, maxX + 0.5, y, z + 0.5, dust);
    }

    private void spawnParticle(World world, double x, double y, double z, Particle.DustOptions dust) {
        world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
    }

    private boolean hasNeighbor(Set<String> chunkSet, int x, int z) {
        return chunkSet != null && chunkSet.contains(x + ":" + z);
    }

    private List<ChunkPos> getCountryChunks(String countryName) {
        List<ChunkPos> result = new ArrayList<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT chunk_x, chunk_z, chunk_type FROM chunks WHERE country_name = ?")) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new ChunkPos(rs.getInt("chunk_x"), rs.getInt("chunk_z"), rs.getString("chunk_type")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    private static class ChunkPos {
        final int x;
        final int z;
        final String type;
        ChunkPos(int x, int z, String type) { this.x = x; this.z = z; this.type = type; }
    }
}
