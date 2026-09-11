package com.example.sovereignty.web;

import com.example.sovereignty.SovereigntyPlugin;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Рендерит terrain каждого загруженного чанка с настоящим relief-шейдингом,
 * похожим на Xaero Map:
 *   - склоновая подсветка (солнце с северо-запада)
 *   - глубина воды (глубоко = тёмный, мелко = яркий)
 *   - микро-вариация (шум для травы/листвы)
 *   - жёсткое затенение по высоте
 */
public class TerrainRenderer implements Listener {

    /** Сколько пикселей на 1 блок. 1 = Xaero при отдалении, 2-3 = Xaero при приближении. */
    public static final int PIXELS_PER_BLOCK = 2;
    public static final int CHUNK_SIZE = 16;
    public static final int IMG_SIZE = CHUNK_SIZE * PIXELS_PER_BLOCK;

    private static final int BATCH_PER_TICK = 5;

    // Настройки высот для шейдинга
    private static final int MIN_Y = 40;
    private static final int MAX_Y = 220;
    private static final float HEIGHT_SHADE_MIN = 0.45f;
    private static final float HEIGHT_SHADE_MAX = 1.35f;

    // Сила склоновой подсветки (Xaero ~0.25)
    private static final float SLOPE_STRENGTH = 0.22f;

    // Максимальная глубина воды для расчёта
    private static final int MAX_WATER_DEPTH = 20;

    private static final int MAX_CACHED_CHUNKS = 200_000;
    private static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;

    private final SovereigntyPlugin plugin;
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final Queue<Chunk> queue = new ConcurrentLinkedQueue<>();
    private BukkitTask processorTask;
    private BukkitTask cleanerTask;

    private int newChunksSinceLastPush = 0;

    public TerrainRenderer(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        loadAllFromDatabase();
        startProcessor();
        startCleaner();
    }

    // ==================== БД ====================

    private void loadAllFromDatabase() {
        long startTime = System.currentTimeMillis();
        int count = 0;

        String sql = "SELECT world, chunk_x, chunk_z, png FROM terrain_cache";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String world = rs.getString("world");
                int cx = rs.getInt("chunk_x");
                int cz = rs.getInt("chunk_z");
                byte[] png = rs.getBytes("png");
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                    if (img != null) {
                        cache.put(world + ":" + cx + ":" + cz, img);
                        count++;
                    }
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[TerrainCache] Ошибка загрузки: " + e.getMessage());
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        plugin.getLogger().info("[TerrainCache] Загружено " + count + " чанков за " + elapsed + " мс (размер " +
                IMG_SIZE + "×" + IMG_SIZE + ").");
    }

    private void saveToDatabase(String world, int cx, int cz, BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] png = baos.toByteArray();

            String sql = "INSERT OR REPLACE INTO terrain_cache(world, chunk_x, chunk_z, png, last_update) " +
                    "VALUES(?,?,?,?,?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, cx);
                ps.setInt(3, cz);
                ps.setBytes(4, png);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TerrainCache] Ошибка сохранения чанка " + world + ":" + cx + ":" + cz +
                    " — " + e.getMessage());
        }
    }

    private void cleanupDatabase() {
        try {
            String oldSql = "DELETE FROM terrain_cache WHERE last_update < ?";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(oldSql)) {
                ps.setLong(1, System.currentTimeMillis() - MAX_AGE_MS);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("[TerrainCache] Удалено старых чанков: " + deleted);
                }
            }

            String countSql = "SELECT COUNT(*) FROM terrain_cache";
            int total;
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(countSql)) {
                ResultSet rs = ps.executeQuery();
                total = rs.next() ? rs.getInt(1) : 0;
            }

            if (total > MAX_CACHED_CHUNKS) {
                int toDelete = total - MAX_CACHED_CHUNKS;
                String delSql = "DELETE FROM terrain_cache WHERE (world, chunk_x, chunk_z) IN " +
                        "(SELECT world, chunk_x, chunk_z FROM terrain_cache ORDER BY last_update ASC LIMIT ?)";
                try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(delSql)) {
                    ps.setInt(1, toDelete);
                    ps.executeUpdate();
                    plugin.getLogger().info("[TerrainCache] Удалено " + toDelete +
                            " самых старых чанков (лимит " + MAX_CACHED_CHUNKS + ").");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[TerrainCache] Ошибка очистки: " + e.getMessage());
        }
    }

    // ==================== ЗАПУСК ====================

    private void startProcessor() {
        processorTask = new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (processed < BATCH_PER_TICK && !queue.isEmpty()) {
                    Chunk chunk = queue.poll();
                    if (chunk == null) break;
                    if (!chunk.isLoaded()) {
                        pending.remove(key(chunk));
                        continue;
                    }
                    try {
                        renderChunk(chunk);
                    } catch (Exception ignored) {}
                    processed++;
                }
            }
        }.runTaskTimer(plugin, 40L, 1L);
    }

    private void startCleaner() {
        cleanerTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupDatabase();
            }
        }.runTaskTimerAsynchronously(plugin, 72000L, 72000L);
    }

    public void shutdown() {
        if (processorTask != null) processorTask.cancel();
        if (cleanerTask != null) cleanerTask.cancel();
    }

    public Map<String, BufferedImage> getCache() { return cache; }
    public int getCacheSize() { return cache.size(); }
    public int getNewChunksSinceLastPush() { return newChunksSinceLastPush; }
    public void resetNewChunksCounter() { newChunksSinceLastPush = 0; }

    private String key(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private void invalidate(Chunk chunk) {
        String k = key(chunk);
        cache.remove(k);
        if (!pending.contains(k)) {
            pending.add(k);
            queue.add(chunk);
        }
    }

    // ==================== СОБЫТИЯ ====================

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String k = key(chunk);
        if (cache.containsKey(k) || pending.contains(k)) return;
        pending.add(k);
        queue.add(chunk);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        invalidate(event.getBlock().getChunk());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        invalidate(event.getBlock().getChunk());
    }

    public boolean forceRender(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return false;
        String k = key(chunk);
        if (cache.containsKey(k)) return true;
        try {
            renderChunk(chunk);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== РЕНДЕР ЧАНКА С RELIEF-ШЕЙДИНГОМ ====================

    private void renderChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() * CHUNK_SIZE;
        int baseZ = chunk.getZ() * CHUNK_SIZE;

        // 1. Собираем матрицу высот и материалов (16×16, включая соседей для склона)
        int gridSize = CHUNK_SIZE + 2; // с 1-блочным ободком для расчёта склона
        int[][] heights = new int[gridSize][gridSize];
        Material[][] materials = new Material[gridSize][gridSize];
        int[][] waterDepths = new int[gridSize][gridSize];

        for (int gx = 0; gx < gridSize; gx++) {
            for (int gz = 0; gz < gridSize; gz++) {
                int wx = baseX + gx - 1;
                int wz = baseZ + gz - 1;
                try {
                    int y = world.getHighestBlockYAt(wx, wz);
                    heights[gx][gz] = y;
                    Block top = world.getBlockAt(wx, y, wz);
                    materials[gx][gz] = top.getType();
                    waterDepths[gx][gz] = top.getType() == Material.WATER
                            ? measureWaterDepth(world, wx, y, wz) : 0;
                } catch (Exception e) {
                    heights[gx][gz] = MIN_Y;
                    materials[gx][gz] = Material.STONE;
                }
            }
        }

        // 2. Создаём изображение — каждый блок = PIXELS_PER_BLOCK пикселей
        BufferedImage img = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        for (int gx = 1; gx <= CHUNK_SIZE; gx++) {
            for (int gz = 1; gz <= CHUNK_SIZE; gz++) {
                int y = heights[gx][gz];
                Material mat = materials[gx][gz];
                Color baseColor = colorOf(mat);

                // 2.1. Высотная подсветка
                float heightShade = shadeForHeight(y);

                // 2.2. Склоновая подсветка (солнце с северо-запада: свет идёт на юго-восток)
                // Считаем разницу высот с соседями по X и Z.
                int yWest = heights[gx - 1][gz];
                int yEast = heights[gx + 1][gz];
                int yNorth = heights[gx][gz - 1];
                int ySouth = heights[gx][gz + 1];

                float slopeX = (yEast - yWest); // + = спуск на восток, - = спуск на запад
                float slopeZ = (ySouth - yNorth); // + = спуск на юг, - = спуск на север

                // Свет с северо-запада: если блок ниже соседа на востоке/юге — он в тени склона
                // Если он выше соседей на западе/севере — на нём свет
                float slopeShade = 1.0f
                        - slopeX * SLOPE_STRENGTH * 0.15f
                        - slopeZ * SLOPE_STRENGTH * 0.15f;

                // 2.3. Микро-вариация (шум) для органичности
                float noise = 1.0f + ((gx * 31 + gz * 17) % 11 - 5) * 0.012f;

                // 2.4. Глубина воды
                float depthShade = 1.0f;
                Color finalColor = baseColor;
                int depth = waterDepths[gx][gz];
                if (depth > 0) {
                    float t = Math.min(1f, depth / (float) MAX_WATER_DEPTH);
                    // Мелкая вода — бирюзовая, глубокая — тёмно-синяя
                    int r = (int) (0x60 * (1 - t) + 0x0a * t);
                    int g = (int) (0xc0 * (1 - t) + 0x30 * t);
                    int b = (int) (0xe0 * (1 - t) + 0x60 * t);
                    finalColor = new Color(clamp(r), clamp(g), clamp(b));
                    depthShade = 1.0f;
                }

                // 2.5. Итоговый цвет
                float shade = heightShade * slopeShade * noise * depthShade;
                int r = clamp((int) (finalColor.getRed() * shade));
                int g = clamp((int) (finalColor.getGreen() * shade));
                int b = clamp((int) (finalColor.getBlue() * shade));
                int rgb = (r << 16) | (g << 8) | b;

                // 2.6. Заполняем PIXELS_PER_BLOCK × PIXELS_PER_BLOCK пикселей
                int px = (gx - 1) * PIXELS_PER_BLOCK;
                int pz = (gz - 1) * PIXELS_PER_BLOCK;
                for (int ox = 0; ox < PIXELS_PER_BLOCK; ox++) {
                    for (int oz = 0; oz < PIXELS_PER_BLOCK; oz++) {
                        img.setRGB(px + ox, pz + oz, rgb);
                    }
                }
            }
        }

        String k = key(chunk);
        cache.put(k, img);
        pending.remove(k);

        saveToDatabase(world.getName(), chunk.getX(), chunk.getZ(), img);
        newChunksSinceLastPush++;
    }

    /** Возвращает глубину воды, если верхний блок — вода. */
    private int measureWaterDepth(World world, int x, int topY, int z) {
        int depth = 0;
        int y = topY;
        while (depth < MAX_WATER_DEPTH) {
            try {
                Block b = world.getBlockAt(x, y, z);
                if (b.getType() != Material.WATER) break;
                depth++;
                y--;
            } catch (Exception e) {
                break;
            }
        }
        return depth;
    }

    private float shadeForHeight(int y) {
        if (y <= MIN_Y) return HEIGHT_SHADE_MIN;
        if (y >= MAX_Y) return HEIGHT_SHADE_MAX;
        float t = (float) (y - MIN_Y) / (MAX_Y - MIN_Y);
        return HEIGHT_SHADE_MIN + t * (HEIGHT_SHADE_MAX - HEIGHT_SHADE_MIN);
    }

    private int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    // ==================== ЦВЕТА БЛОКОВ ====================

    public static Color colorOf(Material m) {
        String n = m.name();

        if (n.contains("WATER")) return new Color(0x3f76e4);
        if (n.contains("LAVA")) return new Color(0xea5c0a);
        if (n.equals("SNOW_BLOCK") || n.equals("SNOW")) return new Color(0xf5f5f5);
        if (n.contains("ICE")) return new Color(0x91b3ff);
        if (n.contains("POWDER_SNOW")) return new Color(0xeef2f7);
        if (n.equals("GRASS_BLOCK") || n.contains("GRASS")) return new Color(0x6faa35);
        if (n.equals("DIRT") || n.equals("COARSE_DIRT")) return new Color(0x8b5a3c);
        if (n.contains("PODZOL")) return new Color(0x5b3d1f);
        if (n.contains("MYCELIUM")) return new Color(0x6f6265);
        if (n.equals("FARMLAND")) return new Color(0x6a4425);
        if (n.equals("MUD") || n.contains("MUD_")) return new Color(0x3f373a);
        if (n.equals("DIRT_PATH") || n.contains("DIRT_PATH")) return new Color(0x9a7a4a);

        if (n.contains("RED_SAND")) return new Color(0xbf6c31);
        if (n.contains("SAND")) return new Color(0xdbd3a0);
        if (n.contains("TERRACOTTA")) return new Color(0x985f43);
        if (n.contains("CONCRETE_POWDER")) return new Color(0xc8b48c);

        if (n.contains("DEEPSLATE")) return new Color(0x4a4a4d);
        if (n.equals("STONE") || n.equals("STONE_BRICKS") || n.equals("SMOOTH_STONE") || n.contains("STONE_STAIRS") || n.contains("STONE_SLAB")) return new Color(0x7a7a7a);
        if (n.contains("COBBLESTONE")) return new Color(0x6f6f6f);
        if (n.contains("GRANITE")) return new Color(0x956651);
        if (n.contains("DIORITE")) return new Color(0xcdcdcd);
        if (n.contains("ANDESITE")) return new Color(0x888888);
        if (n.contains("BASALT") || n.contains("BLACKSTONE")) return new Color(0x2e2a2d);
        if (n.contains("TUFF")) return new Color(0x555755);
        if (n.contains("CALCITE")) return new Color(0xe0e0dc);
        if (n.contains("DRIPSTONE")) return new Color(0x8a6a4c);

        if (n.contains("DIAMOND_ORE")) return new Color(0x5decf5);
        if (n.contains("EMERALD_ORE")) return new Color(0x17dd62);
        if (n.contains("GOLD_ORE")) return new Color(0xb28b3a);
        if (n.contains("IRON_ORE")) return new Color(0xa88f7b);
        if (n.contains("REDSTONE_ORE")) return new Color(0xa81010);
        if (n.contains("LAPIS_ORE")) return new Color(0x1c4fa8);
        if (n.contains("COAL_ORE")) return new Color(0x2a2a2a);
        if (n.contains("COPPER_ORE")) return new Color(0xb87333);
        if (n.contains("_ORE")) return new Color(0x7a7a7a);

        if (n.equals("DIAMOND_BLOCK")) return new Color(0x5decf5);
        if (n.equals("EMERALD_BLOCK")) return new Color(0x17dd62);
        if (n.equals("GOLD_BLOCK")) return new Color(0xf9f01c);
        if (n.equals("IRON_BLOCK")) return new Color(0xd8d8d8);
        if (n.equals("NETHERITE_BLOCK")) return new Color(0x4a3b3b);
        if (n.equals("COAL_BLOCK")) return new Color(0x101010);
        if (n.equals("REDSTONE_BLOCK")) return new Color(0xa81010);
        if (n.equals("LAPIS_BLOCK")) return new Color(0x1c4fa8);
        if (n.contains("COPPER_BLOCK")) return new Color(0xb87333);
        if (n.contains("QUARTZ")) return new Color(0xedede3);
        if (n.equals("AMETHYST_BLOCK")) return new Color(0x9c6cd0);
        if (n.contains("RAW_IRON_BLOCK")) return new Color(0xbfa291);
        if (n.contains("RAW_COPPER_BLOCK")) return new Color(0xbd7a53);
        if (n.contains("RAW_GOLD_BLOCK")) return new Color(0xd8b348);

        if (n.contains("_LOG") || n.contains("_WOOD") || n.contains("HYPHAE")) {
            if (n.contains("OAK")) return new Color(0x6b4f32);
            if (n.contains("SPRUCE")) return new Color(0x4a3521);
            if (n.contains("BIRCH")) return new Color(0xd7cfbe);
            if (n.contains("JUNGLE")) return new Color(0x53431c);
            if (n.contains("ACACIA")) return new Color(0x67593a);
            if (n.contains("DARK_OAK")) return new Color(0x3a2a15);
            if (n.contains("MANGROVE")) return new Color(0x74323c);
            if (n.contains("CHERRY")) return new Color(0xd6a2a2);
            if (n.contains("CRIMSON")) return new Color(0x6a3046);
            if (n.contains("WARPED")) return new Color(0x2f6b62);
            return new Color(0x6b4f32);
        }

        if (n.contains("_LEAVES")) {
            if (n.contains("SPRUCE")) return new Color(0x1f4a1a);
            if (n.contains("BIRCH")) return new Color(0x76a838);
            if (n.contains("CHERRY")) return new Color(0xf2b2ce);
            if (n.contains("AZALEA")) return new Color(0x3a8028);
            if (n.contains("MANGROVE")) return new Color(0x4a9028);
            return new Color(0x2e7a15);
        }

        if (n.contains("_PLANKS") || n.contains("_STAIRS") || n.contains("_SLAB") || n.contains("_FENCE") || n.contains("_DOOR")) {
            if (n.contains("OAK")) return new Color(0xb08b52);
            if (n.contains("SPRUCE")) return new Color(0x745636);
            if (n.contains("BIRCH")) return new Color(0xd0c090);
            if (n.contains("JUNGLE")) return new Color(0xa07257);
            if (n.contains("ACACIA")) return new Color(0xb05e35);
            if (n.contains("DARK_OAK")) return new Color(0x3f2a15);
            if (n.contains("MANGROVE")) return new Color(0x8a3528);
            if (n.contains("CHERRY")) return new Color(0xe0a8b2);
            if (n.contains("CRIMSON")) return new Color(0x6b354f);
            if (n.contains("WARPED")) return new Color(0x2b6960);
            if (n.contains("BAMBOO")) return new Color(0xb8a24d);
        }

        if (n.contains("NETHERRACK")) return new Color(0x6a3232);
        if (n.contains("SOUL_SAND") || n.contains("SOUL_SOIL")) return new Color(0x514235);
        if (n.contains("MAGMA")) return new Color(0x9f4418);
        if (n.contains("GLOWSTONE") || n.contains("SHROOMLIGHT")) return new Color(0xf8d679);
        if (n.contains("END_STONE")) return new Color(0xdadcb9);
        if (n.contains("OBSIDIAN")) return new Color(0x110519);
        if (n.contains("PURPUR")) return new Color(0xa26ea3);
        if (n.contains("NETHER_WART_BLOCK")) return new Color(0x730a0a);
        if (n.contains("WARPED_WART")) return new Color(0x1a96a4);
        if (n.contains("CRYING_OBSIDIAN")) return new Color(0x2a1140);

        if (n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO") || n.contains("BEETROOT")) return new Color(0xb8b03c);
        if (n.contains("PUMPKIN") || n.contains("MELON")) return new Color(0xc57e1c);
        if (n.contains("HAY")) return new Color(0xc9a842);
        if (n.contains("SUGAR_CANE") || n.contains("BAMBOO") || n.contains("KELP")) return new Color(0x8dc23a);
        if (n.contains("VINE") || n.contains("MOSS")) return new Color(0x4f9a30);
        if (n.contains("CACTUS")) return new Color(0x0d6d1c);
        if (n.contains("COCOA")) return new Color(0x7c4a24);
        if (n.contains("NETHER_WART")) return new Color(0x740505);
        if (n.contains("CHORUS")) return new Color(0x8f6e8c);
        if (n.contains("LILY_PAD")) return new Color(0x208030);

        if (n.contains("TULIP") || n.contains("ORCHID") || n.contains("ROSE") || n.contains("DAISY")
                || n.contains("LILY") || n.contains("POPPY") || n.contains("DANDELION")
                || n.contains("SUNFLOWER") || n.contains("BLOSSOM") || n.contains("PETAL")) {
            return new Color(0xe05555);
        }
        if (n.contains("SHORT_GRASS") || n.contains("TALL_GRASS") || n.contains("FERN")
                || n.contains("SEAGRASS")) {
            return new Color(0x5e9d32);
        }

        if (n.contains("GLASS")) return new Color(0xc0e4f2);
        if (n.contains("SEA_LANTERN")) return new Color(0xb4d6d6);
        if (n.contains("LAMP") || n.contains("LANTERN")) return new Color(0xf8d679);
        if (n.contains("BEACON")) return new Color(0x77e8e5);

        if (n.contains("CONCRETE")) {
            if (n.contains("RED")) return new Color(0x8e2121);
            if (n.contains("BLUE")) return new Color(0x2d2d8e);
            if (n.contains("GREEN")) return new Color(0x495b24);
            if (n.contains("YELLOW")) return new Color(0xf1af15);
            if (n.contains("ORANGE")) return new Color(0xf07613);
            if (n.contains("PURPLE")) return new Color(0x641f9c);
            if (n.contains("PINK")) return new Color(0xf38baa);
            if (n.contains("BLACK")) return new Color(0x080a0f);
            if (n.contains("WHITE")) return new Color(0xcfd5d6);
            if (n.contains("GRAY")) return new Color(0x383c40);
            if (n.contains("LIME")) return new Color(0x5ea919);
            if (n.contains("CYAN")) return new Color(0x158991);
            if (n.contains("BROWN")) return new Color(0x603c20);
            if (n.contains("MAGENTA")) return new Color(0xc156c9);
        }

        if (n.contains("_WOOL")) {
            if (n.contains("RED")) return new Color(0xa02722);
            if (n.contains("BLUE")) return new Color(0x35399d);
            if (n.contains("GREEN")) return new Color(0x546d1b);
            if (n.contains("YELLOW")) return new Color(0xf8c627);
            if (n.contains("ORANGE")) return new Color(0xf07613);
            if (n.contains("PURPLE")) return new Color(0x792aac);
            if (n.contains("PINK")) return new Color(0xf6b2ca);
            if (n.contains("BLACK")) return new Color(0x1c1d21);
            if (n.contains("WHITE")) return new Color(0xe9ecec);
        }

        if (n.contains("BRICKS")) return new Color(0x985542);
        if (n.contains("PRISMARINE")) return new Color(0x59a79b);
        if (n.contains("NETHER_BRICK")) return new Color(0x2d161a);
        if (n.contains("SCULK")) return new Color(0x0e2021);
        if (n.contains("MOSS_BLOCK")) return new Color(0x59672b);
        if (n.contains("BEDROCK")) return new Color(0x333333);
        if (n.contains("SPONGE")) return new Color(0xc3c33e);
        if (n.contains("SLIME_BLOCK")) return new Color(0x6bba61);
        if (n.contains("HONEY_BLOCK")) return new Color(0xfbaf24);
        if (n.contains("CRAFTING_TABLE")) return new Color(0x9e7c52);
        if (n.contains("FURNACE")) return new Color(0x6f6f6f);
        if (n.contains("CHEST")) return new Color(0xa87f39);
        if (n.contains("ANVIL")) return new Color(0x4a4a4a);
        if (n.contains("ENCHANTING_TABLE")) return new Color(0xa2435f);
        if (n.contains("LECTERN")) return new Color(0xa87f39);
        if (n.contains("BOOKSHELF")) return new Color(0x6d5638);

        return new Color(0x505050);
    }
}
