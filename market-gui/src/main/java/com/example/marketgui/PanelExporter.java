package com.example.marketgui;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Экспортирует категории и топ-товары рынка в Sovereignty-панель.
 * Пишет plugins/Sovereignty/exports/market_categories.json.
 */
public class PanelExporter {

    private final MarketPlugin plugin;
    private final File exportsDir;
    private volatile boolean available = false;
    private volatile boolean dirty = false;
    private BukkitTask task;

    public PanelExporter(MarketPlugin plugin) {
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

        plugin.getLogger().info("[PanelExporter] MarketGUI → Sovereignty подключён.");
    }

    public boolean isAvailable() { return available; }
    public void markDirty() { if (available) dirty = true; }

    private void flush() {
        try {
            ShopManager sm = plugin.getShopManager();
            Map<String, ShopManager.Category> categories = sm.getCategories();

            StringBuilder sb = new StringBuilder("{");

            // Категории
            sb.append("\"categories\":[");
            boolean first = true;
            for (String name : categories.keySet()) {
                if (!first) sb.append(",");
                first = false;
                int count = sm.getListingsByCategory(name).size();
                sb.append("{");
                sb.append("\"name\":").append(escape(name));
                sb.append(",\"count\":").append(count);
                sb.append("}");
            }
            sb.append("],");

            // Топ-10 самых дорогих товаров
            List<ShopManager.Listing> all = new ArrayList<>(sm.getAllListings());
            all.sort((a, b) -> Double.compare(b.getMoneyPrice(), a.getMoneyPrice()));
            if (all.size() > 10) all = all.subList(0, 10);

            sb.append("\"top_items\":[");
            first = true;
            for (ShopManager.Listing l : all) {
                if (!first) sb.append(",");
                first = false;
                String itemName = l.getItemStack().getType().name().toLowerCase().replace('_', ' ');
                sb.append("{");
                sb.append("\"seller\":").append(escape(l.getSellerName()));
                sb.append(",\"item\":").append(escape(itemName));
                sb.append(",\"price\":").append((long) l.getMoneyPrice());
                sb.append(",\"category\":").append(escape(l.getCategoryName()));
                sb.append("}");
            }
            sb.append("]");

            sb.append("}");
            writeFile("market_categories.json", sb.toString());
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
