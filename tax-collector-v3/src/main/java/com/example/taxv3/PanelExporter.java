package com.example.taxv3;

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
 * Экспортирует долги по налогам в Sovereignty-панель.
 * Пишет plugins/Sovereignty/exports/tax_debts.json.
 */
public class PanelExporter {

    private final TaxPlugin plugin;
    private final File exportsDir;
    private volatile boolean available = false;
    private volatile boolean dirty = false;
    private BukkitTask task;

    public PanelExporter(TaxPlugin plugin) {
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

        plugin.getLogger().info("[PanelExporter] TaxCollector → Sovereignty подключён.");
    }

    public boolean isAvailable() { return available; }
    public void markDirty() { if (available) dirty = true; }

    private void flush() {
        try {
            Map<UUID, DataManager.PlayerTaxData> all = plugin.getDataManager().getAllPlayers();
            SovereigntyBridge bridge = plugin.getSovereigntyBridge();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Map.Entry<UUID, DataManager.PlayerTaxData> e : all.entrySet()) {
                DataManager.PlayerTaxData d = e.getValue();
                if (d.getDebt() <= 0) continue;

                if (!first) sb.append(",");
                first = false;

                String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                if (name == null) name = e.getKey().toString().substring(0, 8);

                String country = bridge.isAvailable() ? bridge.getCountryName(e.getKey()) : null;

                sb.append("{");
                sb.append("\"player\":").append(escape(name));
                if (country != null) {
                    sb.append(",\"country\":").append(escape(country));
                }
                sb.append(",\"debt\":").append((long) d.getDebt());
                sb.append(",\"missed\":").append(d.getMissed());
                sb.append("}");
            }
            sb.append("]");
            writeFile("tax_debts.json", sb.toString());
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
