package com.example.sovereignty.web;

import com.example.sovereignty.SovereigntyPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;

/**
 * Единый расчёт границ карты.
 *
 * v1.0 — вынесен из MapRenderer и WebPanelUploader, чтобы оба модуля
 * использовали ОДИН и тот же bounding box. Раньше WebPanelUploader
 * считал границы только по таблице chunks, а MapRenderer — ещё и по
 * terrain-кэшу. Из-за этого PNG-карта начиналась левее, чем ожидал
 * клиент, и головы игроков рисовались со сдвигом.
 *
 * Логика:
 *   1. min/max по таблице chunks (заклеймленные чанки).
 *   2. Расширить границами terrain-кэша (везде, где был игрок).
 *   3. Прибавить PADDING_CHUNKS с каждой стороны.
 */
public class MapBounds {

    public static final int PADDING_CHUNKS = 15;

    public final String world;
    public final int minCX, minCZ, maxCX, maxCZ;

    private MapBounds(String world, int minCX, int minCZ, int maxCX, int maxCZ) {
        this.world = world;
        this.minCX = minCX;
        this.minCZ = minCZ;
        this.maxCX = maxCX;
        this.maxCZ = maxCZ;
    }

    public int widthChunks()  { return maxCX - minCX + 1; }
    public int heightChunks() { return maxCZ - minCZ + 1; }

    /** Возвращает null, если нет ни одного чанка. */
    public static MapBounds compute(SovereigntyPlugin plugin) {
        int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE;
        int minCZ = Integer.MAX_VALUE, maxCZ = Integer.MIN_VALUE;
        String worldName = null;

        // 1. Заклеймленные чанки
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT world, chunk_x, chunk_z FROM chunks")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String w = rs.getString("world");
                int cx = rs.getInt("chunk_x");
                int cz = rs.getInt("chunk_z");
                if (worldName == null) {
                    worldName = w;
                    minCX = maxCX = cx;
                    minCZ = maxCZ = cz;
                } else if (w.equals(worldName)) {
                    if (cx < minCX) minCX = cx;
                    if (cx > maxCX) maxCX = cx;
                    if (cz < minCZ) minCZ = cz;
                    if (cz > maxCZ) maxCZ = cz;
                }
            }
        } catch (Exception ignored) {}

        if (worldName == null) return null;

        // 2. Terrain-кэш — расширяет границы
        try {
            Set<String> keys = plugin.getTerrainRenderer().getCache().keySet();
            for (String key : keys) {
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
        } catch (Throwable ignored) {}

        // 3. Padding
        minCX -= PADDING_CHUNKS; minCZ -= PADDING_CHUNKS;
        maxCX += PADDING_CHUNKS; maxCZ += PADDING_CHUNKS;

        return new MapBounds(worldName, minCX, minCZ, maxCX, maxCZ);
    }
}
