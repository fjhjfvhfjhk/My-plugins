package com.example.sovereignty.web;

import com.example.sovereignty.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Отправляет данные Sovereignty на GitHub.
 *
 * Тайминги:
 *   - первая публикация через 5 сек после старта
 *   - периодическая — раз в interval-minutes (по умолчанию 5)
 *   - force push по важным событиям (создание страны, война, etc.) — батчится 30 сек
 *   - авто-пуш при накоплении N новых чанков
 *   - финальный push при выключении
 */
public class WebPanelUploader {

    private final SovereigntyPlugin plugin;
    private final File configFile;
    private YamlConfiguration config;
    private BukkitTask scheduledTask;
    private BukkitTask autoPushTask;

    private boolean enabled = false;
    private String ghOwner;
    private String ghRepo;
    private String ghBranch;
    private String ghJsonPath;
    private String ghMapPath;
    private String ghToken;
    private String serverName;
    private int intervalMinutes;
    private int autoPushChunkThreshold;

    private volatile boolean forcePushScheduled = false;
    private BukkitTask forcePushTask;

    private final HttpClient httpClient;

    public WebPanelUploader(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "webpanel.yml");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        loadConfig();
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("webpanel.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("enabled", false);
        ghOwner = config.getString("github.owner", "");
        ghRepo = config.getString("github.repo", "");
        ghBranch = config.getString("github.branch", "main");
        ghJsonPath = config.getString("github.json-path", "data/server1.json");
        ghMapPath = config.getString("github.map-path", "data/map.png");
        ghToken = config.getString("github.token", "");
        serverName = config.getString("server-name", "Minecraft Server");
        intervalMinutes = config.getInt("interval-minutes", 5);
        autoPushChunkThreshold = config.getInt("auto-push-chunk-threshold", 200);

        if (enabled) {
            if (ghOwner.isEmpty() || ghRepo.isEmpty() || ghToken.isEmpty()) {
                plugin.getLogger().warning("[WebPanel] Не настроены owner/repo/token — публикация отключена.");
                enabled = false;
            } else {
                plugin.getLogger().info("[WebPanel] Включён. Цель: " + ghOwner + "/" + ghRepo
                        + ", интервал: " + intervalMinutes + " мин");
            }
        }
    }

    public void start() {
        if (!enabled) return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::push, 100L);

        long periodTicks = 20L * 60L * intervalMinutes;
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::push, periodTicks, periodTicks);

        if (autoPushChunkThreshold > 0) {
            autoPushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                int newChunks = plugin.getTerrainRenderer().getNewChunksSinceLastPush();
                if (newChunks >= autoPushChunkThreshold) {
                    plugin.getLogger().info("[WebPanel] Накоплено " + newChunks +
                            " новых чанков — авто-публикация.");
                    push();
                }
            }, 1200L, 1200L);
        }
    }

    public void stop() {
        if (scheduledTask != null) { scheduledTask.cancel(); scheduledTask = null; }
        if (autoPushTask != null) { autoPushTask.cancel(); autoPushTask = null; }
        if (forcePushTask != null) { forcePushTask.cancel(); forcePushTask = null; }
        if (!enabled) return;
        try {
            pushInternal();
        } catch (Exception ignored) {}
    }

    public boolean isEnabled() { return enabled; }

    public void scheduleForcePush() {
        if (!enabled) return;
        if (forcePushScheduled) return;
        forcePushScheduled = true;

        forcePushTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            forcePushScheduled = false;
            forcePushTask = null;
            push();
        }, 20L * 30L);
    }

    public void push() {
        if (!enabled) return;
        try {
            pushInternal();
            plugin.getTerrainRenderer().resetNewChunksCounter();
            int chunks = plugin.getTerrainRenderer().getCacheSize();
            plugin.getLogger().info("[WebPanel] Данные и карта отправлены (чанков в кэше: " + chunks + ").");
        } catch (Exception e) {
            plugin.getLogger().warning("[WebPanel] Ошибка публикации: " + e.getMessage());
        }
    }

    private void pushInternal() throws Exception {
        String json = buildJson();
        uploadText(ghJsonPath, json, "Auto-update data");

        if (ghMapPath != null && !ghMapPath.isEmpty()) {
            try {
                MapRenderer renderer = new MapRenderer(plugin);
                MapRenderer.MapRenderResult result = renderer.renderMap();
                if (result != null && result.png != null) {
                    uploadBinary(ghMapPath, result.png, "Auto-update map");
                } else {
                    plugin.getLogger().info("[WebPanel] Нет чанков — карта не сгенерирована.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[WebPanel] Ошибка карты: " + e.getMessage());
            }
        }
    }

    // ==================== GITHUB API ====================

    private void uploadText(String path, String content, String message) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        uploadBase64(path, base64, message);
    }

    private void uploadBinary(String path, byte[] data, String message) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(data);
        uploadBase64(path, base64, message);
    }

    private void uploadBase64(String path, String base64, String message) throws Exception {
        String url = "https://api.github.com/repos/" + ghOwner + "/" + ghRepo + "/contents/" + path;
        String sha = getExistingSha(url);

        StringBuilder body = new StringBuilder();
        body.append("{");
        body.append("\"message\":\"").append(escape(message)).append("\",");
        body.append("\"branch\":\"").append(ghBranch).append("\",");
        body.append("\"content\":\"").append(base64).append("\"");
        if (sha != null && !sha.isEmpty()) {
            body.append(",\"sha\":\"").append(sha).append("\"");
        }
        body.append("}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "token " + ghToken)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Sovereignty-Plugin")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            throw new RuntimeException("GitHub API (" + path + ") ответил " + resp.statusCode() + ": " + resp.body());
        }
    }

    private String getExistingSha(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "?ref=" + ghBranch))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "token " + ghToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Sovereignty-Plugin")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                int idx = body.indexOf("\"sha\":\"");
                if (idx != -1) {
                    int start = idx + 7;
                    int end = body.indexOf('"', start);
                    if (end > start) return body.substring(start, end);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== JSON BUILD ====================

    private String buildJson() {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{");

        appendKey(sb, "updated_at"); sb.append(System.currentTimeMillis());
        sb.append(",");
        appendKey(sb, "server_name"); sb.append(JsonUtil.escape(serverName));
        sb.append(",");
        appendKey(sb, "online_players"); sb.append(Bukkit.getOnlinePlayers().size());
        sb.append(",");
        appendKey(sb, "max_players"); sb.append(Bukkit.getMaxPlayers());
        sb.append(",");

        // map_meta
        int metaMinCX = 0, metaMinCZ = 0;
        String metaWorld = null;
        try {
            var conn = plugin.getDatabaseManager().getConnection();
            try (var ps = conn.prepareStatement(
                    "SELECT world, MIN(chunk_x) AS minX, MIN(chunk_z) AS minZ FROM chunks GROUP BY world LIMIT 1")) {
                var rs = ps.executeQuery();
                if (rs.next()) {
                    metaWorld = rs.getString("world");
                    metaMinCX = rs.getInt("minX") - 15;
                    metaMinCZ = rs.getInt("minZ") - 15;
                }
            }
        } catch (Exception ignored) {}

        appendKey(sb, "map_meta");
        if (metaWorld != null) {
            sb.append("{\"world\":").append(JsonUtil.escape(metaWorld));
            sb.append(",\"min_chunk_x\":").append(metaMinCX);
            sb.append(",\"min_chunk_z\":").append(metaMinCZ);
            sb.append("}");
        } else {
            sb.append("null");
        }
        sb.append(",");

        appendKey(sb, "countries");
        sb.append(buildCountriesJson());
        sb.append(",");

        appendKey(sb, "players");
        sb.append(buildPlayersJson());

        Map<String, String> external = plugin.getExternalDataLoader() != null
                ? plugin.getExternalDataLoader().loadAll()
                : Collections.emptyMap();

        for (Map.Entry<String, String> e : external.entrySet()) {
            sb.append(",");
            appendKey(sb, e.getKey());
            sb.append(e.getValue());
        }

        sb.append("}");
        return sb.toString();
    }

    private void appendKey(StringBuilder sb, String key) {
        sb.append("\"").append(key).append("\":");
    }

    private String buildCountriesJson() {
        CountryManager cm = plugin.getCountryManager();
        EnergyManager em = plugin.getEnergyManager();

        StringBuilder sb = new StringBuilder(4096);
        sb.append("[");
        boolean first = true;

        for (String name : cm.getAllCountries()) {
            try {
                if (!first) sb.append(",");
                first = false;

                UUID owner = cm.getOwner(name);

                sb.append("{");
                appendKey(sb, "name"); sb.append(JsonUtil.escape(name));
                sb.append(",");
                appendKey(sb, "owner"); sb.append(owner != null ? JsonUtil.escape(safeName(owner)) : "null");
                sb.append(",");
                appendKey(sb, "claims"); sb.append(cm.getClaimCount(name));
                sb.append(",");
                appendKey(sb, "bank"); sb.append(round2(cm.getBankBalance(name)));
                sb.append(",");
                appendKey(sb, "debt"); sb.append(round2(cm.getCountryDebt(name)));
                if (owner != null) {
                    sb.append(",");
                    appendKey(sb, "energy"); sb.append(round1(em.getEnergy(owner)));
                    sb.append(",");
                    appendKey(sb, "max_energy"); sb.append(round1(em.getMaxEnergy(owner)));
                    sb.append(",");
                    appendKey(sb, "regen"); sb.append(round1(em.getCurrentRegenPerHour(owner)));
                    sb.append(",");
                    appendKey(sb, "max_claims"); sb.append(cm.getMaxClaims(owner));
                    sb.append(",");
                    appendKey(sb, "farm_level"); sb.append(em.getFarmUpgradeLevel(owner));
                }
                sb.append(",");
                appendKey(sb, "pacts"); sb.append(countPacts(cm, name));
                sb.append(",");
                appendKey(sb, "allies"); sb.append(countAllies(cm, name));
                sb.append(",");
                appendKey(sb, "chunks_farm"); sb.append(plugin.getChunkUpgradeManager().countType(name, "farm"));
                sb.append(",");
                appendKey(sb, "chunks_mining"); sb.append(plugin.getChunkUpgradeManager().countType(name, "mining"));
                sb.append(",");
                appendKey(sb, "chunks_military"); sb.append(plugin.getChunkUpgradeManager().countType(name, "military"));
                sb.append(",");
                appendKey(sb, "chunks_trade"); sb.append(plugin.getChunkUpgradeManager().countType(name, "trade"));
                sb.append("}");
            } catch (Exception ignored) {}
        }

        sb.append("]");
        return sb.toString();
    }

    private String buildPlayersJson() {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("[");
        boolean first = true;

        Set<UUID> processed = new HashSet<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (!first) sb.append(",");
                first = false;
                sb.append(buildOnlinePlayerJson(p));
                processed.add(p.getUniqueId());
            } catch (Exception ignored) {}
        }

        PlayerStatsManager stats = plugin.getPlayerStatsManager();
        if (stats != null) {
            for (UUID uuid : stats.getAllWithCountry()) {
                if (processed.contains(uuid)) continue;
                try {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append(buildOfflinePlayerJson(uuid));
                } catch (Exception ignored) {}
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private String buildOnlinePlayerJson(Player p) {
        UUID uuid = p.getUniqueId();
        StringBuilder sb = new StringBuilder(512);
        sb.append("{");

        appendKey(sb, "uuid"); sb.append(JsonUtil.escape(uuid.toString()));
        sb.append(",");
        appendKey(sb, "name"); sb.append(JsonUtil.escape(p.getName()));
        sb.append(",");
        appendKey(sb, "online"); sb.append("true");

        String country = plugin.getCountryManager().getCountryName(uuid);
        if (country != null) {
            sb.append(",");
            appendKey(sb, "country"); sb.append(JsonUtil.escape(country));
            sb.append(",");
            appendKey(sb, "country_role");
            if (plugin.getCountryManager().isLeader(uuid, country)) sb.append("\"leader\"");
            else if (plugin.getCountryManager().isCoRuler(uuid, country)) sb.append("\"co_ruler\"");
            else sb.append("null");
        }

        sb.append(",");
        appendKey(sb, "balance"); sb.append(round2(plugin.getEconomyManager().getBalance(p)));
        sb.append(",");
        appendKey(sb, "energy"); sb.append(round1(plugin.getEnergyManager().getEnergy(uuid)));
        sb.append(",");
        appendKey(sb, "max_energy"); sb.append(round1(plugin.getEnergyManager().getMaxEnergy(uuid)));

        long playtimeTicks = 0;
        try { playtimeTicks = p.getStatistic(Statistic.PLAY_ONE_MINUTE); } catch (Exception ignored) {}
        sb.append(",");
        appendKey(sb, "playtime_seconds"); sb.append(playtimeTicks / 20L);

        PlayerStatsManager stats = plugin.getPlayerStatsManager();
        if (stats != null) {
            sb.append(",");
            appendKey(sb, "first_seen"); sb.append(stats.getFirstSeen(uuid));
            sb.append(",");
            appendKey(sb, "last_seen"); sb.append(System.currentTimeMillis());
        }

        try {
            sb.append(",");
            appendKey(sb, "kills"); sb.append(p.getStatistic(Statistic.PLAYER_KILLS));
            sb.append(",");
            appendKey(sb, "deaths"); sb.append(p.getStatistic(Statistic.DEATHS));
        } catch (Exception ignored) {}

        try {
            Map<String, Boolean> ach = plugin.getAchievementManager().getAchievements(uuid);
            sb.append(",");
            appendKey(sb, "achievements_count"); sb.append(ach.size());
        } catch (Exception ignored) {}

        JobsIntegration jobs = plugin.getJobsIntegration();
        if (jobs != null && jobs.isAvailable()) {
            String job = jobs.getJob(p);
            if (job != null) {
                sb.append(",");
                appendKey(sb, "job"); sb.append(JsonUtil.escape(job));
                int lvl = jobs.getJobLevel(p);
                if (lvl >= 0) {
                    sb.append(",");
                    appendKey(sb, "job_level"); sb.append(lvl);
                }
            }
        }

        Location loc = p.getLocation();
        if (loc.getWorld() != null) {
            sb.append(",");
            appendKey(sb, "position");
            sb.append("{\"world\":").append(JsonUtil.escape(loc.getWorld().getName()));
            sb.append(",\"x\":").append(round1(loc.getX()));
            sb.append(",\"y\":").append(round1(loc.getY()));
            sb.append(",\"z\":").append(round1(loc.getZ()));
            sb.append("}");
        }

        sb.append("}");
        return sb.toString();
    }

    private String buildOfflinePlayerJson(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);

        StringBuilder sb = new StringBuilder(512);
        sb.append("{");

        appendKey(sb, "uuid"); sb.append(JsonUtil.escape(uuid.toString()));
        sb.append(",");
        appendKey(sb, "name"); sb.append(JsonUtil.escape(name));
        sb.append(",");
        appendKey(sb, "online"); sb.append("false");

        String country = plugin.getCountryManager().getCountryName(uuid);
        if (country != null) {
            sb.append(",");
            appendKey(sb, "country"); sb.append(JsonUtil.escape(country));
            sb.append(",");
            appendKey(sb, "country_role");
            if (plugin.getCountryManager().isLeader(uuid, country)) sb.append("\"leader\"");
            else if (plugin.getCountryManager().isCoRuler(uuid, country)) sb.append("\"co_ruler\"");
            else sb.append("null");
        }

        sb.append(",");
        appendKey(sb, "balance"); sb.append(round2(plugin.getEconomyManager().getBalance(op)));
        sb.append(",");
        appendKey(sb, "energy"); sb.append(round1(plugin.getEnergyManager().getEnergy(uuid)));
        sb.append(",");
        appendKey(sb, "max_energy"); sb.append(round1(plugin.getEnergyManager().getMaxEnergy(uuid)));

        PlayerStatsManager stats = plugin.getPlayerStatsManager();
        if (stats != null) {
            sb.append(",");
            appendKey(sb, "playtime_seconds"); sb.append(stats.getPlaytimeSeconds(uuid));
            sb.append(",");
            appendKey(sb, "first_seen"); sb.append(stats.getFirstSeen(uuid));
            sb.append(",");
            appendKey(sb, "last_seen"); sb.append(stats.getLastSeen(uuid));
        }

        try {
            Map<String, Boolean> ach = plugin.getAchievementManager().getAchievements(uuid);
            sb.append(",");
            appendKey(sb, "achievements_count"); sb.append(ach.size());
        } catch (Exception ignored) {}

        sb.append("}");
        return sb.toString();
    }

    // ==================== УТИЛИТЫ ====================

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Считает пакты страны через plugin.getPactManager() — не через CountryManager.plugin,
     * который объявлен private.
     */
    private int countPacts(CountryManager cm, String name) {
        int n = 0;
        PactManager pactManager = plugin.getPactManager();
        if (pactManager == null) return 0;
        for (String other : cm.getAllCountries()) {
            if (other.equals(name)) continue;
            if (pactManager.hasPact(name, other, "trade")) n++;
            if (pactManager.hasPact(name, other, "military")) n++;
            if (pactManager.hasPact(name, other, "defense")) n++;
            if (pactManager.hasPact(name, other, "nonaggression")) n++;
        }
        return n;
    }

    private int countAllies(CountryManager cm, String name) {
        int n = 0;
        for (String other : cm.getAllCountries()) {
            if (!other.equals(name) && cm.isAlly(name, other)) n++;
        }
        return n;
    }

    private String safeName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String n = op.getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }
}
