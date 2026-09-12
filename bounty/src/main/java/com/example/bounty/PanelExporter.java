package com.example.bounty;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

/**
 * Экспортирует награды за головы в Sovereignty-панель.
 * Пишет plugins/Sovereignty/exports/bounties.json.
 */
public class PanelExporter {

    private final BountyPlugin plugin;
    private final File exportsDir;
    private volatile boolean available = false;
    private volatile boolean dirty = false;
    private BukkitTask task;

    public PanelExporter(BountyPlugin plugin) {
        this.plugin = plugin;

        Plugin sov = Bukkit.getPluginManager().getPlugin("Sovereignty");
        if (sov == null) {
            this.exportsDir = null;
            return;
        }

        File sovDir = sov.getDataFolder();
        this.exportsDir = new File(sovDir, "exports");
        if (!exportsDir.exists()) exportsDir.mkdirs();

        this.available = true;

        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (dirty) { dirty = false; flush(); }
        }, 600L, 600L);

        plugin.getLogger().info("[PanelExporter] Bounty → Sovereignty подключён.");
    }

    public boolean isAvailable() { return available; }
    public void markDirty() { if (available) dirty = true; }

    private void flush() {
        try {
            Map<UUID, BountyManager.BountyEntry> all = plugin.getBountyManager().getAllBounties();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Map.Entry<UUID, BountyManager.BountyEntry> e : all.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                String targetName = Bukkit.getOfflinePlayer(e.getKey()).getName();
                if (targetName == null) targetName = e.getKey().toString().substring(0, 8);
                String setterName = Bukkit.getOfflinePlayer(e.getValue().setter).getName();
                if (setterName == null) setterName = e.getValue().setter.toString().substring(0, 8);

                sb.append("{");
                sb.append("\"target\":").append(escape(targetName));
                sb.append(",\"amount\":").append((long) e.getValue().amount);
                sb.append(",\"setter\":").append(escape(setterName));
                sb.append("}");
            }
            sb.append("]");
            writeFile("bounties.json", sb.toString());
        } catch (Exception e) {
            plugin.getLogger().warning("[PanelExporter] Ошибка: " + e.getMessage());
        }
    }

    private void writeFile(String name, String content) throws IOException {
        Files.writeString(new File(exportsDir, name).toPath(), content, StandardCharsets.UTF_8);
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
        if (available && dirty) flush();
    }
}
