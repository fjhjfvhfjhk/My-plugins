package com.example.sovereignty.web;

import com.example.sovereignty.SovereigntyPlugin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;

/**
 * Собирает финальную PNG-карту:
 *  1) terrain из кэша TerrainRenderer (с relief-шейдингом)
 *  2) серые заглушки для заклеймленных, но не отрендеренных чанков
 *  3) цветные границы стран с полупрозрачной заливкой
 */
public class MapRenderer {

    /** Увеличенный максимум — 12288 пикселей (12K). */
    private static final int MAX_SIZE = 12288;
    private static final int PADDING_CHUNKS = 15;

    private static final Color[] PALETTE = {
            new Color(0x6366f1), new Color(0xef4444), new Color(0x10b981),
            new Color(0xf59e0b), new Color(0x8b5cf6), new Color(0x06b6d4),
            new Color(0xec4899), new Color(0x84cc16), new Color(0xf97316),
            new Color(0x14b8a6), new Color(0xa855f7), new Color(0xf43f5e),
            new Color(0x22d3ee), new Color(0xa3e635), new Color(0xfacc15),
            new Color(0xfb923c), new Color(0xe879f9), new Color(0x4ade80),
            new Color(0x60a5fa), new Color(0xfca5a5)
    };

    private final SovereigntyPlugin plugin;

    public MapRenderer(SovereigntyPlugin plugin) {
        this.plugin = plugin;
    }

    public byte[] renderMap() throws Exception {
        Map<String, String> ownerMap = new HashMap<>();
        int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE;
        int minCZ = Integer.MAX_VALUE, maxCZ = Integer.MIN_VALUE;
        String worldName = null;

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT country_name, world, chunk_x, chunk_z FROM chunks")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String country = rs.getString("country_name");
                String world = rs.getString("world");
                int cx = rs.getInt("chunk_x");
                int cz = rs.getInt("chunk_z");
                String key = world + ":" + cx + ":" + cz;
                ownerMap.put(key, country);

                if (worldName == null) {
                    worldName = world;
                    minCX = cx; maxCX = cx;
                    minCZ = cz; maxCZ = cz;
                } else if (world.equals(worldName)) {
                    if (cx < minCX) minCX = cx;
                    if (cx > maxCX) maxCX = cx;
                    if (cz < minCZ) minCZ = cz;
                    if (cz > maxCZ) maxCZ = cz;
                }
            }
        }

        if (ownerMap.isEmpty() || worldName == null) return null;

        Map<String, BufferedImage> terrainCache = plugin.getTerrainRenderer().getCache();
        for (String key : terrainCache.keySet()) {
            String[] parts = key.split(":");
            if (parts.length < 3) continue;
            if (!parts[0].equals(worldName)) continue;
            try {
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);
                if (cx < minCX) minCX = cx;
                if (cx > maxCX) maxCX = cx;
                if (cz < minCZ) minCZ = cz;
                if (cz > maxCZ) maxCZ = cz;
            } catch (NumberFormatException ignored) {}
        }

        minCX -= PADDING_CHUNKS; minCZ -= PADDING_CHUNKS;
        maxCX += PADDING_CHUNKS; maxCZ += PADDING_CHUNKS;

        int widthChunks = maxCX - minCX + 1;
        int heightChunks = maxCZ - minCZ + 1;
        int imgWidth = widthChunks * TerrainRenderer.IMG_SIZE;
        int imgHeight = heightChunks * TerrainRenderer.IMG_SIZE;

        double scale = 1.0;
        if (imgWidth > MAX_SIZE || imgHeight > MAX_SIZE) {
            scale = Math.min((double) MAX_SIZE / imgWidth, (double) MAX_SIZE / imgHeight);
        }
        int finalWidth = Math.max(1, (int) (imgWidth * scale));
        int finalHeight = Math.max(1, (int) (imgHeight * scale));

        BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x0f1117));
        g.fillRect(0, 0, imgWidth, imgHeight);

        // Terrain из кэша
        for (Map.Entry<String, BufferedImage> e : terrainCache.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length < 3) continue;
            if (!parts[0].equals(worldName)) continue;
            int cx, cz;
            try {
                cx = Integer.parseInt(parts[1]);
                cz = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ex) { continue; }

            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            if (px < -TerrainRenderer.IMG_SIZE || pz < -TerrainRenderer.IMG_SIZE
                    || px > imgWidth || pz > imgHeight) continue;
            g.drawImage(e.getValue(), px, pz, null);
        }

        // Заклеймленные, но не отрендеренные — серая заливка
        Color gray = new Color(0x2a2d38);
        for (Map.Entry<String, String> e : ownerMap.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length < 3) continue;
            if (!parts[0].equals(worldName)) continue;
            if (terrainCache.containsKey(e.getKey())) continue;

            int cx, cz;
            try {
                cx = Integer.parseInt(parts[1]);
                cz = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ex) { continue; }

            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            g.setColor(gray);
            g.fillRect(px, pz, TerrainRenderer.IMG_SIZE, TerrainRenderer.IMG_SIZE);
        }

        // Цвета стран
        Map<String, Color> countryColors = new HashMap<>();
        int idx = 0;
        for (String name : plugin.getCountryManager().getAllCountries()) {
            countryColors.put(name, PALETTE[idx++ % PALETTE.length]);
        }

        // Полупрозрачная заливка территорий
        Composite originalComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f));
        for (Map.Entry<String, String> e : ownerMap.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length < 3) continue;
            if (!parts[0].equals(worldName)) continue;
            int cx, cz;
            try {
                cx = Integer.parseInt(parts[1]);
                cz = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ex) { continue; }

            Color col = countryColors.get(e.getValue());
            if (col == null) continue;
            g.setColor(col);
            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            g.fillRect(px, pz, TerrainRenderer.IMG_SIZE, TerrainRenderer.IMG_SIZE);
        }
        g.setComposite(originalComposite);

        // Границы стран
        int borderWidth = Math.max(2, Math.round(TerrainRenderer.IMG_SIZE * 0.15f));
        g.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Map.Entry<String, String> e : ownerMap.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length < 3) continue;
            if (!parts[0].equals(worldName)) continue;

            int cx, cz;
            try {
                cx = Integer.parseInt(parts[1]);
                cz = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ex) { continue; }

            String country = e.getValue();
            Color col = countryColors.get(country);
            if (col == null) continue;

            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            int size = TerrainRenderer.IMG_SIZE;

            g.setColor(col);

            if (!country.equals(ownerMap.get(worldName + ":" + cx + ":" + (cz - 1)))) {
                g.drawLine(px, pz, px + size, pz);
            }
            if (!country.equals(ownerMap.get(worldName + ":" + cx + ":" + (cz + 1)))) {
                g.drawLine(px, pz + size, px + size, pz + size);
            }
            if (!country.equals(ownerMap.get(worldName + ":" + (cx - 1) + ":" + cz))) {
                g.drawLine(px, pz, px, pz + size);
            }
            if (!country.equals(ownerMap.get(worldName + ":" + (cx + 1) + ":" + cz))) {
                g.drawLine(px + size, pz, px + size, pz + size);
            }
        }

        g.dispose();

        BufferedImage finalImg = img;
        if (scale < 1.0) {
            finalImg = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D sg = finalImg.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            sg.drawImage(img, 0, 0, finalWidth, finalHeight, null);
            sg.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(finalImg, "PNG", baos);
        return baos.toByteArray();
    }
}
