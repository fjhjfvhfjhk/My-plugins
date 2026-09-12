package com.example.auction;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Экспортирует активные аукционы в Sovereignty-панель.
 * Пишет plugins/Sovereignty/exports/active_auctions.json.
 */
public class PanelExporter {

    private final AuctionPlugin plugin;
    private final File exportsDir;
    private volatile boolean available = false;
    private volatile boolean dirty = false;
    private BukkitTask task;

    public PanelExporter(AuctionPlugin plugin) {
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

        plugin.getLogger().info("[PanelExporter] AuctionHouse → Sovereignty подключён.");
    }

    public boolean isAvailable() { return available; }
    public void markDirty() { if (available) dirty = true; }

    private void flush() {
        try {
            List<Auction> auctions = plugin.getDatabaseManager().getActiveAuctions();
            long now = System.currentTimeMillis();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Auction a : auctions) {
                if (!first) sb.append(",");
                first = false;

                String sellerName = Bukkit.getOfflinePlayer(a.getSeller()).getName();
                if (sellerName == null) sellerName = a.getSeller().toString().substring(0, 8);

                String itemName = a.getItem().getType().name().toLowerCase().replace('_', ' ');
                long endsIn = Math.max(0, (a.getEndTime() - now) / 1000L);

                sb.append("{");
                sb.append("\"id\":").append(a.getId());
                sb.append(",\"seller\":").append(escape(sellerName));
                sb.append(",\"item\":").append(escape(itemName));
                sb.append(",\"current_price\":").append((long) a.getCurrentPrice());
                sb.append(",\"ends_in\":").append(endsIn);
                if (a.getCurrentBidder() != null) {
                    String bidderName = Bukkit.getOfflinePlayer(a.getCurrentBidder()).getName();
                    if (bidderName == null) bidderName = a.getCurrentBidder().toString().substring(0, 8);
                    sb.append(",\"top_bidder\":").append(escape(bidderName));
                }
                sb.append("}");
            }
            sb.append("]");
            writeFile("active_auctions.json", sb.toString());
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
