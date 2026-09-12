package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Экспортирует данные RollGame (джекпот, топ покера) в Sovereignty-панель.
 *
 * Пишет JSON-файлы в plugins/Sovereignty/exports/:
 *   - jackpot.json   → {"jackpot": 1234567}
 *   - top_poker.json → [{"name":"...","profit":...,"hands":...}]
 *
 * Если Sovereignty не установлен — класс молча ничего не делает.
 */
public class PanelExporter {

    private final RollPlugin plugin;
    private final File exportsDir;
    private volatile boolean available = false;
    private volatile boolean jackpotDirty = false;
    private volatile boolean pokerDirty = false;
    private BukkitTask task;

    public PanelExporter(RollPlugin plugin) {
        this.plugin = plugin;

        Plugin sov = Bukkit.getPluginManager().getPlugin("Sovereignty");
        if (sov == null) {
            this.exportsDir = null;
            return;
        }

        File sovDir = sov.getDataFolder();
        this.exportsDir = new File(sovDir, "exports");
        if (!exportsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            exportsDir.mkdirs();
        }

        this.available = true;

        // Батчинг: раз в 30 сек проверяем dirty-флаги
        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (jackpotDirty) { jackpotDirty = false; flushJackpot(); }
            if (pokerDirty) { pokerDirty = false; flushPoker(); }
        }, 600L, 600L);

        plugin.getLogger().info("[PanelExporter] RollGame → Sovereignty подключён.");
    }

    public boolean isAvailable() { return available; }

    public void markJackpotDirty() { if (available) jackpotDirty = true; }
    public void markPokerDirty() { if (available) pokerDirty = true; }

    private void flushJackpot() {
        try {
            long jackpot = plugin.getRollData().getJackpot();
            String json = "{\"jackpot\":" + jackpot + "}";
            writeFile("jackpot.json", json);
        } catch (Exception e) {
            plugin.getLogger().warning("[PanelExporter] jackpot: " + e.getMessage());
        }
    }

    private void flushPoker() {
        try {
            List<PokerHistory.PlayerProfit> top = plugin.getPokerHistory().getTop(10);
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (PokerHistory.PlayerProfit p : top) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"name\":").append(escape(p.name));
                sb.append(",\"profit\":").append(p.profit);
                sb.append(",\"hands\":").append(p.handsPlayed);
                sb.append("}");
            }
            sb.append("]");
            writeFile("top_poker.json", sb.toString());
        } catch (Exception e) {
            plugin.getLogger().warning("[PanelExporter] top_poker: " + e.getMessage());
        }
    }

    private void writeFile(String name, String content) throws IOException {
        File target = new File(exportsDir, name);
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        if (!available) return;
        if (jackpotDirty) flushJackpot();
        if (pokerDirty) flushPoker();
    }
}
