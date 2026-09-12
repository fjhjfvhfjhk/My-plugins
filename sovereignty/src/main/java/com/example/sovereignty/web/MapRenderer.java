package com.example.sovereignty.web;

import com.example.sovereignty.SovereigntyPlugin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;

/**
 * MapRenderer v4.13 — визуальное улучшение карты.
 *
 * Новое:
 *   1. Названия стран на карте (жирный текст с тенью, цвет = hash-цвет страны).
 *   2. Метка столицы — маркер в «центральном» чанке страны.
 *   3. Умное размещение — если подписи перекрываются, большая сдвигает меньшую.
 *   4. Трёхслойные границы (внешний ореол + чёрная обводка + цветная линия).
 *   5. Дороги союзников — тоньше и полупрозрачнее.
 *
 * Использует общий MapBounds с WebPanelUploader (маркеры не смещаются).
 * Terrain-кэш НЕ трогает — старая карта перерисовывается мгновенно.
 */
public class MapRenderer {

    private static final int DEFAULT_MAX_SIZE = 6144;

    /** Названия стран рисуем с такой длиной, максимум символов. */
    private static final int LABEL_MAX_CHARS = 18;

    /** Показываем метки только для стран с таким минимумом чанков. */
    private static final int LABEL_MIN_CHUNKS = 3;

    private final SovereigntyPlugin plugin;
    private final int maxSize;

    public MapRenderer(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getConfig().isSet("map.max-size")) {
            plugin.getConfig().set("map.max-size", DEFAULT_MAX_SIZE);
            plugin.saveConfig();
        }
        this.maxSize = Math.max(1024, plugin.getConfig().getInt("map.max-size", DEFAULT_MAX_SIZE));
    }

    /* ==================== Hash-цвета ==================== */

    public static Color hashColor(String name) {
        if (name == null) name = "?";
        int hash = 0x811c9dc5;
        for (int i = 0; i < name.length(); i++) {
            hash ^= name.charAt(i);
            hash *= 0x01000193;
        }
        float hue = Math.abs(hash % 360) / 360f;
        return hslToRgb(hue, 0.68f, 0.58f);
    }

    public static String hashColorHex(String name) {
        Color c = hashColor(name);
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0) { r = g = b = l; }
        else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hue2rgb(p, q, h + 1f / 3f);
            g = hue2rgb(p, q, h);
            b = hue2rgb(p, q, h - 1f / 3f);
        }
        return new Color(clamp((int)(r * 255)), clamp((int)(g * 255)), clamp((int)(b * 255)));
    }

    private static float hue2rgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /* ==================== Result ==================== */

    public static class MapRenderResult {
        public final byte[] png;
        public final String world;
        public final int minChunkX;
        public final int minChunkZ;

        public MapRenderResult(byte[] png, String world, int minChunkX, int minChunkZ) {
            this.png = png;
            this.world = world;
            this.minChunkX = minChunkX;
            this.minChunkZ = minChunkZ;
        }
    }

    /* ==================== Render ==================== */

    public MapRenderResult renderMap() throws Exception {
        MapBounds bounds = MapBounds.compute(plugin);
        if (bounds == null) return null;

        String worldName = bounds.world;
        int minCX = bounds.minCX, minCZ = bounds.minCZ;
        int maxCX = bounds.maxCX, maxCZ = bounds.maxCZ;

        Map<String, BufferedImage> terrainCache;
        try {
            terrainCache = new HashMap<>(plugin.getTerrainRenderer().getCache());
        } catch (Throwable t) {
            throw new RuntimeException("Не удалось получить snapshot terrain-кэша: " + t, t);
        }

        // === Собрать данные по странам ===
        // ownerMap: world:cx:cz → country
        // countryChunks: country → список [cx, cz]
        Map<String, String> ownerMap = new HashMap<>();
        Map<String, List<int[]>> countryChunks = new HashMap<>();

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

                if (world.equals(worldName)) {
                    countryChunks.computeIfAbsent(country, k -> new ArrayList<>()).add(new int[]{ cx, cz });
                }
            }
        }

        if (ownerMap.isEmpty()) return null;

        int widthChunks = maxCX - minCX + 1;
        int heightChunks = maxCZ - minCZ + 1;
        int imgWidth = widthChunks * TerrainRenderer.IMG_SIZE;
        int imgHeight = heightChunks * TerrainRenderer.IMG_SIZE;

        double scale = 1.0;
        if (imgWidth > maxSize || imgHeight > maxSize) {
            scale = Math.min((double) maxSize / imgWidth, (double) maxSize / imgHeight);
        }
        int finalWidth = Math.max(1, (int) (imgWidth * scale));
        int finalHeight = Math.max(1, (int) (imgHeight * scale));

        BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x0f1117));
        g.fillRect(0, 0, imgWidth, imgHeight);

        // 1. Terrain
        for (Map.Entry<String, BufferedImage> e : terrainCache.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length < 3 || !parts[0].equals(worldName)) continue;
            int cx, cz;
            try { cx = Integer.parseInt(parts[1]); cz = Integer.parseInt(parts[2]); }
            catch (NumberFormatException ex) { continue; }

            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            if (px < -TerrainRenderer.IMG_SIZE || pz < -TerrainRenderer.IMG_SIZE
                    || px > imgWidth || pz > imgHeight) continue;
            g.drawImage(e.getValue(), px, pz, null);
        }

        // 2. Серые заглушки
        Color gray = new Color(0x2a2d38);
        for (Map.Entry<String, String> e : ownerMap.entrySet()) {
            if (terrainCache.containsKey(e.getKey())) continue;
            String[] parts = e.getKey().split(":");
            if (parts.length < 3 || !parts[0].equals(worldName)) continue;
            int cx, cz;
            try { cx = Integer.parseInt(parts[1]); cz = Integer.parseInt(parts[2]); }
            catch (NumberFormatException ex) { continue; }

            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            g.setColor(gray);
            g.fillRect(px, pz, TerrainRenderer.IMG_SIZE, TerrainRenderer.IMG_SIZE);
        }

        // 3. Цвета стран
        Map<String, Color> countryColors = new HashMap<>();
        for (String name : plugin.getCountryManager().getAllCountries()) {
            countryColors.put(name, hashColor(name));
        }

        // 4. Полупрозрачная заливка территорий
        Composite originalComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        for (Map.Entry<String, String> e : ownerMap.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length < 3 || !parts[0].equals(worldName)) continue;
            int cx, cz;
            try { cx = Integer.parseInt(parts[1]); cz = Integer.parseInt(parts[2]); }
            catch (NumberFormatException ex) { continue; }

            Color col = countryColors.get(e.getValue());
            if (col == null) continue;
            g.setColor(col);
            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            g.fillRect(px, pz, TerrainRenderer.IMG_SIZE, TerrainRenderer.IMG_SIZE);
        }
        g.setComposite(originalComposite);

        // 5. Дороги союзников (тонкие, полупрозрачные)
        drawAlliedRoads(g, countryChunks, worldName, minCX, minCZ);

        // 6. Трёхслойные границы
        int borderWidth = Math.max(2, Math.round(TerrainRenderer.IMG_SIZE * 0.18f));

        // Слой 1: внешний ореол (полупрозрачный, +4px)
        drawBorders(g, ownerMap, countryColors, worldName, minCX, minCZ,
                new BasicStroke(borderWidth + 4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
                BorderLayer.GLOW);
        // Слой 2: чёрная обводка (+2px)
        drawBorders(g, ownerMap, countryColors, worldName, minCX, minCZ,
                new BasicStroke(borderWidth + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
                BorderLayer.SHADOW);
        // Слой 3: яркая цветная линия
        drawBorders(g, ownerMap, countryColors, worldName, minCX, minCZ,
                new BasicStroke(borderWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
                BorderLayer.COLOR);

        g.dispose();

        // === Скейл ===
        BufferedImage finalImg = img;
        if (scale < 1.0) {
            finalImg = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D sg = finalImg.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            sg.drawImage(img, 0, 0, finalWidth, finalHeight, null);
            sg.dispose();
        }

        // 7. Названия стран и столицы — рисуем на ФИНАЛЬНОМ разрешении
        //    (чтобы текст был резким, а не размытым после скейла)
        drawLabels(finalImg, countryChunks, countryColors, worldName, minCX, minCZ, scale);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(finalImg, "PNG", baos);

        return new MapRenderResult(baos.toByteArray(), worldName, minCX, minCZ);
    }

    /* ==================== Границы ==================== */

    private enum BorderLayer { GLOW, SHADOW, COLOR }

    private void drawBorders(Graphics2D g, Map<String, String> ownerMap,
                             Map<String, Color> countryColors, String worldName,
                             int minCX, int minCZ, Stroke stroke, BorderLayer layer) {
        g.setStroke(stroke);

        for (Map.Entry<String, String> e : ownerMap.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length < 3 || !parts[0].equals(worldName)) continue;

            int cx, cz;
            try { cx = Integer.parseInt(parts[1]); cz = Integer.parseInt(parts[2]); }
            catch (NumberFormatException ex) { continue; }

            String country = e.getValue();
            Color col = countryColors.get(country);
            if (col == null) continue;

            switch (layer) {
                case GLOW -> g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 70));
                case SHADOW -> g.setColor(new Color(0, 0, 0, 220));
                case COLOR -> g.setColor(col);
            }

            int px = (cx - minCX) * TerrainRenderer.IMG_SIZE;
            int pz = (cz - minCZ) * TerrainRenderer.IMG_SIZE;
            int size = TerrainRenderer.IMG_SIZE;

            if (!country.equals(ownerMap.get(worldName + ":" + cx + ":" + (cz - 1)))) g.drawLine(px, pz, px + size, pz);
            if (!country.equals(ownerMap.get(worldName + ":" + cx + ":" + (cz + 1)))) g.drawLine(px, pz + size, px + size, pz + size);
            if (!country.equals(ownerMap.get(worldName + ":" + (cx - 1) + ":" + cz))) g.drawLine(px, pz, px, pz + size);
            if (!country.equals(ownerMap.get(worldName + ":" + (cx + 1) + ":" + cz))) g.drawLine(px + size, pz, px + size, pz + size);
        }
    }

    /* ==================== Дороги союзников ==================== */

    private void drawAlliedRoads(Graphics2D g, Map<String, List<int[]>> countryChunks,
                                 String worldName, int minCX, int minCZ) {
        Map<String, int[]> centers = new HashMap<>();
        for (Map.Entry<String, List<int[]>> e : countryChunks.entrySet()) {
            int[] c = computeCenter(e.getValue());
            if (c != null) centers.put(e.getKey(), c);
        }
        if (centers.size() < 2) return;

        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(
                2f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10f,
                new float[]{ 10f, 8f }, 0f));

        List<String> names = new ArrayList<>(centers.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String a = names.get(i), b = names.get(j);
                if (!plugin.getCountryManager().isAlly(a, b)) continue;

                int[] ca = centers.get(a);
                int[] cb = centers.get(b);
                int x1 = (ca[0] - minCX) * TerrainRenderer.IMG_SIZE + TerrainRenderer.IMG_SIZE / 2;
                int z1 = (ca[1] - minCZ) * TerrainRenderer.IMG_SIZE + TerrainRenderer.IMG_SIZE / 2;
                int x2 = (cb[0] - minCX) * TerrainRenderer.IMG_SIZE + TerrainRenderer.IMG_SIZE / 2;
                int z2 = (cb[1] - minCZ) * TerrainRenderer.IMG_SIZE + TerrainRenderer.IMG_SIZE / 2;

                g.drawLine(x1, z1, x2, z2);
            }
        }
    }

    /* ==================== Подписи стран + столицы ==================== */

    private void drawLabels(BufferedImage img, Map<String, List<int[]>> countryChunks,
                            Map<String, Color> countryColors, String worldName,
                            int minCX, int minCZ, double scale) {
        if (countryChunks.isEmpty()) return;

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        FontRenderContext frc = g.getFontRenderContext();

        // Собираем данные подписей
        List<LabelData> labels = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : countryChunks.entrySet()) {
            String country = e.getKey();
            List<int[]> chunks = e.getValue();
            if (chunks.size() < LABEL_MIN_CHUNKS) continue;

            int[] center = computeCenter(chunks);
            if (center == null) continue;

            // Позиция в пикселях финального изображения
            double cxPx = (center[0] - minCX + 0.5) * TerrainRenderer.IMG_SIZE * scale;
            double czPx = (center[1] - minCZ + 0.5) * TerrainRenderer.IMG_SIZE * scale;

            // Размер шрифта зависит от количества чанков
            int chunkCount = chunks.size();
            int fontSize;
            if (chunkCount < 10) fontSize = 12;
            else if (chunkCount < 30) fontSize = 14;
            else if (chunkCount < 80) fontSize = 16;
            else if (chunkCount < 200) fontSize = 18;
            else fontSize = 22;

            String text = truncate(country, LABEL_MAX_CHARS);
            Color color = countryColors.getOrDefault(country, Color.WHITE);

            labels.add(new LabelData(country, center, cxPx, czPx, text, color, fontSize, chunkCount));
        }

        // Сортируем по убыванию размера: большие страны рисуем первыми, маленькие поверх
        labels.sort((a, b) -> Integer.compare(b.chunkCount, a.chunkCount));

        // Проверка перекрытия: пропускаем метки, чей bounding-box пересекается с уже занятым
        List<Rectangle2D> occupied = new ArrayList<>();

        for (LabelData l : labels) {
            Font font = new Font("SansSerif", Font.BOLD, l.fontSize);
            TextLayout layout = new TextLayout(l.text, font, frc);
            Rectangle2D bounds = layout.getBounds();

            double textW = bounds.getWidth();
            double textH = layout.getAscent() + layout.getDescent();

            // Центр метки — над столицей, со сдвигом вверх на 12px
            double labelCx = l.cxPx;
            double labelCy = l.czPx - 16;

            // Bounding box текста с запасом
            Rectangle2D rect = new Rectangle2D.Double(
                    labelCx - textW / 2 - 6,
                    labelCy - textH / 2 - 2,
                    textW + 12,
                    textH + 4
            );

            boolean overlaps = false;
            for (Rectangle2D o : occupied) {
                if (o.intersects(rect)) { overlaps = true; break; }
            }
            if (overlaps) continue;
            occupied.add(rect);

            // === Рисуем столицу (кружок) ===
            drawCapital(g, l.cxPx, l.czPx, l.color, l.fontSize);

            // === Рисуем текст ===
            double textX = labelCx - textW / 2 - bounds.getX();
            double textY = labelCy + textH / 2 - layout.getDescent() - bounds.getY();

            g.setFont(font);

            // Тень (обводка)
            g.setColor(new Color(0, 0, 0, 230));
            for (int dx = -2; dx <= 2; dx += 2) {
                for (int dy = -2; dy <= 2; dy += 2) {
                    if (dx == 0 && dy == 0) continue;
                    g.drawString(l.text, (float)(textX + dx), (float)(textY + dy));
                }
            }

            // Основной текст — белый с ореолом цвета страны
            g.setColor(l.color);
            g.drawString(l.text, (float) textX, (float) textY);

            // Тонкий белый контур поверх цветного текста — для контраста на тёмном фоне
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(l.text, (float) textX, (float) textY);
        }

        g.dispose();
    }

    /** Кружок-столица с белой обводкой. */
    private void drawCapital(Graphics2D g, double cx, double cy, Color color, int fontSize) {
        int r = Math.max(4, fontSize / 3);

        // Тень
        g.setColor(new Color(0, 0, 0, 180));
        g.fillOval((int)(cx - r - 1), (int)(cy - r), r * 2 + 3, r * 2 + 3);

        // Заливка цветом страны
        g.setColor(color);
        g.fillOval((int)(cx - r), (int)(cy - r), r * 2, r * 2);

        // Белая обводка
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawOval((int)(cx - r), (int)(cy - r), r * 2, r * 2);

        // Звёздочка в центре
        if (r >= 5) {
            int sr = Math.max(2, r / 2);
            g.setColor(Color.WHITE);
            g.fillOval((int)(cx - sr / 2.0), (int)(cy - sr / 2.0), sr, sr);
        }
    }

    /* ==================== Утилиты ==================== */

    private static int[] computeCenter(List<int[]> chunks) {
        if (chunks == null || chunks.isEmpty()) return null;
        long sumX = 0, sumZ = 0;
        for (int[] c : chunks) { sumX += c[0]; sumZ += c[1]; }
        return new int[]{ (int)(sumX / chunks.size()), (int)(sumZ / chunks.size()) };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "?";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static class LabelData {
        final String country;
        final int[] centerChunk;
        final double cxPx, czPx;
        final String text;
        final Color color;
        final int fontSize;
        final int chunkCount;

        LabelData(String country, int[] centerChunk, double cxPx, double czPx,
                  String text, Color color, int fontSize, int chunkCount) {
            this.country = country;
            this.centerChunk = centerChunk;
            this.cxPx = cxPx;
            this.czPx = czPx;
            this.text = text;
            this.color = color;
            this.fontSize = fontSize;
            this.chunkCount = chunkCount;
        }
    }
}
