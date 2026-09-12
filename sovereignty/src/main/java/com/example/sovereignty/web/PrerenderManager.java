package com.example.sovereignty.web;

import com.example.sovereignty.SovereigntyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Админ-прогрузка чанков для наполнения terrain-кэша.
 *
 * v4.7 — оптимизация под слабый CPU:
 *   - CHUNKS_PER_TICK с 2 → 1 (по умолчанию, настраивается)
 *   - TPS-защита: если TPS < min-tps, пропускаем тик
 *   - Прогресс реже в чат (каждые 100 чанков вместо 50)
 */
public class PrerenderManager {

    private static final int DEFAULT_CHUNKS_PER_TICK = 1;
    private static final double DEFAULT_MIN_TPS = 17.0;
    private static final int MAX_RADIUS = 20;
    private static final int MAX_TOTAL_CHUNKS = 100_000;

    private final SovereigntyPlugin plugin;
    private final int chunksPerTick;
    private final double minTps;

    private BukkitTask task;
    private boolean running = false;
    private UUID requesterUuid;
    private int total, done, loaded, skipped, errors;
    private long skippedTicksLowTps = 0;

    public PrerenderManager(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getConfig().isSet("prerender.chunks-per-tick")) {
            plugin.getConfig().set("prerender.chunks-per-tick", DEFAULT_CHUNKS_PER_TICK);
            plugin.saveConfig();
        }
        if (!plugin.getConfig().isSet("prerender.min-tps")) {
            plugin.getConfig().set("prerender.min-tps", DEFAULT_MIN_TPS);
            plugin.saveConfig();
        }
        this.chunksPerTick = Math.max(1, plugin.getConfig().getInt("prerender.chunks-per-tick", DEFAULT_CHUNKS_PER_TICK));
        this.minTps = plugin.getConfig().getDouble("prerender.min-tps", DEFAULT_MIN_TPS);
    }

    public boolean isRunning() { return running; }
    public int getDone() { return done; }
    public int getTotal() { return total; }

    public boolean start(Player admin, int radius) {
        if (running) {
            admin.sendMessage("§cПрогрузка уже идёт. Отмена: §f/country panel prerender cancel");
            return false;
        }
        if (radius < 1 || radius > MAX_RADIUS) {
            admin.sendMessage("§cРадиус должен быть от 1 до " + MAX_RADIUS + ".");
            return false;
        }

        admin.sendMessage("§eСобираю список чанков...");

        Set<String> allPoints = new LinkedHashSet<>();
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT DISTINCT world, chunk_x, chunk_z FROM chunks")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String world = rs.getString("world");
                int cx = rs.getInt("chunk_x");
                int cz = rs.getInt("chunk_z");
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        allPoints.add(world + ":" + (cx + dx) + ":" + (cz + dz));
                    }
                }
                if (allPoints.size() > MAX_TOTAL_CHUNKS) {
                    admin.sendMessage("§cПревышен лимит " + MAX_TOTAL_CHUNKS + " чанков. Уменьшите радиус.");
                    return false;
                }
            }
        } catch (Exception e) {
            admin.sendMessage("§cОшибка чтения БД: " + e.getMessage());
            return false;
        }

        if (allPoints.isEmpty()) {
            admin.sendMessage("§cНет заклеймленных чанков.");
            return false;
        }

        Set<String> needRender = new LinkedHashSet<>();
        Map<String, ?> cache = plugin.getTerrainRenderer().getCache();
        for (String point : allPoints) {
            if (!cache.containsKey(point)) needRender.add(point);
        }

        if (needRender.isEmpty()) {
            admin.sendMessage("§aВсе чанки в радиусе " + radius + " уже прогружены (в кэше: " +
                    cache.size() + ").");
            return false;
        }

        this.total = needRender.size();
        this.done = 0;
        this.loaded = 0;
        this.skipped = 0;
        this.errors = 0;
        this.skippedTicksLowTps = 0;
        this.requesterUuid = admin.getUniqueId();
        this.running = true;

        Bukkit.broadcastMessage("§6[Прогрузка] §eНачинаю прогрузку §f" + total + "§e чанков (радиус " +
                radius + ", уже в кэше: " + cache.size() + ").");
        Bukkit.broadcastMessage("§6[Прогрузка] §7Отмена: §f/country panel prerender cancel");
        Bukkit.broadcastMessage("§6[Прогрузка] §7Скорость: " + chunksPerTick +
                " чанк/тик, пауза при TPS < " + minTps);

        final List<String> queue = new ArrayList<>(needRender);

        task = new BukkitRunnable() {
            int idx = 0;

            @Override
            public void run() {
                if (!running) { cancel(); return; }

                // TPS-защита
                double tps = Bukkit.getTPS()[0];
                if (tps < minTps) {
                    skippedTicksLowTps++;
                    if (skippedTicksLowTps % 100 == 0) {
                        Bukkit.broadcastMessage("§6[Прогрузка] §eПауза (TPS=" +
                                String.format("%.1f", tps) + "). Обработано: " + done + "/" + total);
                    }
                    return;
                }

                int processed = 0;
                while (processed < chunksPerTick && idx < queue.size()) {
                    String key = queue.get(idx++);
                    processed++;
                    done++;

                    String[] parts = key.split(":");
                    if (parts.length < 3) { skipped++; continue; }
                    String worldName = parts[0];
                    int cx, cz;
                    try {
                        cx = Integer.parseInt(parts[1]);
                        cz = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) { skipped++; continue; }

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) { skipped++; continue; }

                    try {
                        Chunk chunk = world.getChunkAt(cx, cz);
                        if (chunk != null) {
                            boolean ok = plugin.getTerrainRenderer().forceRender(chunk);
                            if (ok) loaded++;
                            else skipped++;
                        } else {
                            skipped++;
                        }
                    } catch (Exception e) {
                        errors++;
                    }
                }

                if (done % 100 == 0 || done == total) {
                    int percent = (int) (100.0 * done / total);
                    Bukkit.broadcastMessage("§6[Прогрузка] §e" + percent + "% §7(" + done + "/" + total +
                            ") §a↑" + loaded + " §7проп: " + skipped + " §cош: " + errors);
                }
                if (requesterUuid != null && done % 5 == 0) {
                    Player p = Bukkit.getPlayer(requesterUuid);
                    if (p != null) {
                        int percent = (int) (100.0 * done / total);
                        p.sendActionBar(Component.text("Прогрузка: ", NamedTextColor.GRAY)
                                .append(Component.text(done + "/" + total, NamedTextColor.YELLOW))
                                .append(Component.text(" (" + percent + "%)", NamedTextColor.WHITE)));
                    }
                }

                if (idx >= queue.size()) {
                    cancel();
                    finish();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private void finish() {
        running = false;
        task = null;

        Bukkit.broadcastMessage("§6[Прогрузка] §a✓ Завершено! Отрендерено: §f" + loaded +
                "§a чанков (проп: " + skipped + ", ош: " + errors + ").");
        Bukkit.broadcastMessage("§6[Прогрузка] §7Всего в кэше: §f" +
                plugin.getTerrainRenderer().getCacheSize());

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (plugin.getWebPanelUploader().isEnabled()) {
                Bukkit.broadcastMessage("§6[Прогрузка] §eОтправляю обновлённую карту на GitHub...");
                plugin.getWebPanelUploader().push(true);
            } else {
                Bukkit.broadcastMessage("§6[Прогрузка] §7Веб-панель отключена, карта не отправлена.");
            }
        }, 60L);
    }

    public boolean cancel() {
        if (!running) return false;
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        Bukkit.broadcastMessage("§6[Прогрузка] §c✗ Отменено. Обработано: " + done + "/" + total);
        return true;
    }
}
