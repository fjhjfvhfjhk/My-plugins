package com.example.sovereignty.web;

import com.example.sovereignty.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
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
 * WebPanelUploader v4.10 — расширенный JSON.
 *
 * Добавлено:
 *   - map_meta: world_time (0..24000) и world_day — для day/night на клиенте
 *   - countries[].color — hash-цвет в hex
 *   - countries[].activity — метрика активности (дельта чанков за 7д + войны + союзы)
 *   - countries[].claims_delta_7d
 *   - online_history[] — массив {ts, count} за 24 часа
 *   - online_avg_24h — средний онлайн
 */
public class WebPanelUploader {

    private final SovereigntyPlugin plugin;
    private final File configFile;
    private YamlConfiguration config;
    private BukkitTask scheduledTask, mapTask, autoPushTask, forcePushTask;

    private boolean enabled = false;
    private String ghOwner, ghRepo, ghBranch, ghJsonPath, ghMapPath, ghToken, serverName;
    private int jsonIntervalMinutes, mapIntervalMinutes, autoPushChunkThreshold;

    private volatile boolean forcePushScheduled = false;
    private final Object pushLock = new Object();
    private volatile boolean pushInProgress = false;

    private volatile MapRenderer cachedRenderer = null;

    private static final int CONFLICT_RETRIES = 3;
    private static final long CONFLICT_RETRY_BASE_MS = 300L;

    private final HttpClient httpClient;

    public WebPanelUploader(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "webpanel.yml");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        loadConfig();
    }

    public void loadConfig() {
        if (!configFile.exists()) plugin.saveResource("webpanel.yml", false);
        this.config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("enabled", false);
        ghOwner = config.getString("github.owner", "");
        ghRepo = config.getString("github.repo", "");
        ghBranch = config.getString("github.branch", "main");
        ghJsonPath = config.getString("github.json-path", "data/server1.json");
        ghMapPath = config.getString("github.map-path", "data/map.png");
        ghToken = config.getString("github.token", "");
        serverName = config.getString("server-name", "Minecraft Server");
        jsonIntervalMinutes = config.getInt("interval-minutes", 5);
        if (!config.isSet("map-interval-minutes")) {
            config.set("map-interval-minutes", 60);
            try { config.save(configFile); } catch (Exception ignored) {}
        }
        mapIntervalMinutes = config.getInt("map-interval-minutes", 60);
        autoPushChunkThreshold = config.getInt("auto-push-chunk-threshold", 200);

        if (enabled) {
            if (ghOwner.isEmpty() || ghRepo.isEmpty() || ghToken.isEmpty()) {
                plugin.getLogger().warning("[WebPanel] Не настроены owner/repo/token — публикация отключена.");
                enabled = false;
            } else {
                plugin.getLogger().info("[WebPanel] Включён. Цель: " + ghOwner + "/" + ghRepo +
                        ", JSON " + jsonIntervalMinutes + "м, карта " + mapIntervalMinutes + "м");
            }
        }
    }

    private MapRenderer getRenderer() {
        MapRenderer r = cachedRenderer;
        if (r == null) {
            synchronized (this) {
                if (cachedRenderer == null) cachedRenderer = new MapRenderer(plugin);
                r = cachedRenderer;
            }
        }
        return r;
    }

    public void start() {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> push(false), 100L);

        long jp = 20L * 60L * jsonIntervalMinutes;
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> push(false), jp, jp);

        long mp = 20L * 60L * mapIntervalMinutes;
        mapTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> push(true), mp, mp);

        if (autoPushChunkThreshold > 0) {
            autoPushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                int n = plugin.getTerrainRenderer().getNewChunksSinceLastPush();
                if (n >= autoPushChunkThreshold) push(false);
            }, 1200L, 1200L);
        }
    }

    public void stop() {
        if (scheduledTask != null) scheduledTask.cancel();
        if (mapTask != null) mapTask.cancel();
        if (autoPushTask != null) autoPushTask.cancel();
        if (forcePushTask != null) forcePushTask.cancel();
        if (!enabled) return;
        try { pushInternal(true); } catch (Exception ignored) {}
    }

    public boolean isEnabled() { return enabled; }

    public void scheduleForcePush() {
        if (!enabled || forcePushScheduled) return;
        forcePushScheduled = true;
        forcePushTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            forcePushScheduled = false;
            forcePushTask = null;
            push(false);
        }, 20L * 30L);
    }

    public void push() { push(false); }

    public void push(boolean includeMap) {
        if (!enabled) return;
        synchronized (pushLock) {
            if (pushInProgress) return;
            pushInProgress = true;
        }
        boolean jsonOk = false, mapOk = false;
        try {
            try {
                uploadText(ghJsonPath, buildJson(), "Auto-update data");
                jsonOk = true;
            } catch (Exception e) {
                plugin.getLogger().warning("[WebPanel] Ошибка данных: " + e.getMessage());
            }
            if (includeMap && jsonOk && ghMapPath != null && !ghMapPath.isEmpty()) {
                try {
                    MapRenderer.MapRenderResult r = getRenderer().renderMap();
                    if (r != null && r.png != null) {
                        uploadBinary(ghMapPath, r.png, "Auto-update map");
                        mapOk = true;
                    } else {
                        mapOk = true;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[WebPanel] Ошибка карты: " + e.getMessage());
                }
            } else if (!includeMap) mapOk = true;

            if (jsonOk && mapOk) {
                plugin.getTerrainRenderer().resetNewChunksCounter();
                int c = plugin.getTerrainRenderer().getCacheSize();
                plugin.getLogger().info("[WebPanel] ✓ Данные" + (includeMap ? " и карта" : "") +
                        " отправлены (чанков в кэше: " + c + ").");
            } else if (jsonOk) {
                plugin.getLogger().warning("[WebPanel] ⚠ Данные ушли, карта — НЕТ.");
            } else {
                plugin.getLogger().warning("[WebPanel] ✗ Публикация данных не удалась.");
            }
        } finally {
            pushInProgress = false;
        }
    }

    private void pushInternal(boolean includeMap) throws Exception {
        uploadText(ghJsonPath, buildJson(), "Auto-update data");
        if (includeMap && ghMapPath != null && !ghMapPath.isEmpty()) {
            MapRenderer.MapRenderResult r = getRenderer().renderMap();
            if (r != null && r.png != null) uploadBinary(ghMapPath, r.png, "Auto-update map");
        }
    }

    // ==================== GITHUB ====================

    private void uploadText(String path, String content, String message) throws Exception {
        uploadBase64(path, Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)), message);
    }

    private void uploadBinary(String path, byte[] data, String message) throws Exception {
        uploadBase64(path, Base64.getEncoder().encodeToString(data), message);
    }

    private void uploadBase64(String path, String base64, String message) throws Exception {
        String url = "https://api.github.com/repos/" + ghOwner + "/" + ghRepo + "/contents/" + path;
        Exception lastError = null;
        for (int attempt = 1; attempt <= CONFLICT_RETRIES; attempt++) {
            String sha = getExistingSha(url);
            String body = "{\"message\":\"" + escape(message) + "\",\"branch\":\"" + ghBranch +
                    "\",\"content\":\"" + base64 + "\"" +
                    (sha != null && !sha.isEmpty() ? ",\"sha\":\"" + sha + "\"" : "") + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "token " + ghToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Sovereignty-Plugin")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 200 || code == 201) return;
            if (code == 409 || code == 422) {
                lastError = new RuntimeException(code + " on " + path);
                Thread.sleep(CONFLICT_RETRY_BASE_MS + (attempt - 1) * 200L);
                continue;
            }
            throw new RuntimeException("GitHub API (" + path + ") ответил " + code + ": " + truncate(resp.body(), 500));
        }
        throw new RuntimeException("Не удалось загрузить " + path + " после " + CONFLICT_RETRIES +
                " попыток: " + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    private String getExistingSha(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "?ref=" + ghBranch + "&t=" + System.currentTimeMillis()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "token " + ghToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Sovereignty-Plugin")
                    .header("Cache-Control", "no-cache")
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 200) return extractSha(resp.body());
            if (code == 404) return null;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractSha(String body) {
        if (body == null) return null;
        int i = body.indexOf("\"sha\":\"");
        if (i == -1) return null;
        int s = i + 7, e = body.indexOf('"', s);
        return e > s ? body.substring(s, e) : null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== JSON BUILD ====================

    private String buildJson() {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("{");

        appendKey(sb, "updated_at"); sb.append(System.currentTimeMillis()); sb.append(",");
        appendKey(sb, "server_name"); sb.append(JsonUtil.escape(serverName)); sb.append(",");
        appendKey(sb, "online_players"); sb.append(Bukkit.getOnlinePlayers().size()); sb.append(",");
        appendKey(sb, "max_players"); sb.append(Bukkit.getMaxPlayers()); sb.append(",");

        // === MAP META + WORLD TIME ===
        int minCX = 0, minCZ = 0;
        String metaWorld = null;
        long worldTime = 6000L; // noon по умолчанию
        long worldDay = 0L;
        try {
            var conn = plugin.getDatabaseManager().getConnection();
            try (var ps = conn.prepareStatement(
                    "SELECT world, MIN(chunk_x) AS minX, MIN(chunk_z) AS minZ FROM chunks GROUP BY world LIMIT 1")) {
                var rs = ps.executeQuery();
                if (rs.next()) {
                    metaWorld = rs.getString("world");
                    minCX = rs.getInt("minX") - 15;
                    minCZ = rs.getInt("minZ") - 15;
                }
            }
        } catch (Exception ignored) {}

        // Текущее время мира
        try {
            List<World> worlds = Bukkit.getWorlds();
            if (!worlds.isEmpty()) {
                World w = metaWorld != null ? Bukkit.getWorld(metaWorld) : null;
                if (w == null) w = worlds.get(0);
                worldTime = w.getTime();
                worldDay = w.getFullTime() / 24000L;
            }
        } catch (Exception ignored) {}

        appendKey(sb, "map_meta");
        if (metaWorld != null) {
            sb.append("{\"world\":").append(JsonUtil.escape(metaWorld));
            sb.append(",\"min_chunk_x\":").append(minCX);
            sb.append(",\"min_chunk_z\":").append(minCZ);
            sb.append(",\"world_time\":").append(worldTime);
            sb.append(",\"world_day\":").append(worldDay);
            sb.append("}");
        } else sb.append("null");
        sb.append(",");

        // === ONLINE HISTORY ===
        appendKey(sb, "online_history");
        sb.append(buildOnlineHistoryJson());
        sb.append(",");

        // === COUNTRIES ===
        appendKey(sb, "countries");
        sb.append(buildCountriesJson());
        sb.append(",");

        // === PLAYERS ===
        appendKey(sb, "players");
        sb.append(buildPlayersJson());

        // === EXTERNAL ===
        Map<String, String> external = plugin.getExternalDataLoader() != null
                ? plugin.getExternalDataLoader().loadAll() : Collections.emptyMap();
        for (Map.Entry<String, String> e : external.entrySet()) {
            sb.append(",");
            appendKey(sb, e.getKey());
            sb.append(e.getValue());
        }

        sb.append("}");
        return sb.toString();
    }

    private void appendKey(StringBuilder sb, String key) { sb.append("\"").append(key).append("\":"); }

    private String buildOnlineHistoryJson() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("[");
        try {
            OnlineHistoryManager ohm = plugin.getOnlineHistoryManager();
            if (ohm != null) {
                List<long[]> hist = ohm.getHistory();
                boolean first = true;
                for (long[] h : hist) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"ts\":").append(h[0]).append(",\"count\":").append(h[1]).append("}");
                }
            }
        } catch (Exception ignored) {}
        sb.append("]");
        return sb.toString();
    }

    private String buildCountriesJson() {
        CountryManager cm = plugin.getCountryManager();
        EnergyManager em = plugin.getEnergyManager();
        CountrySnapshotManager snapshots = plugin.getCountrySnapshotManager();

        StringBuilder sb = new StringBuilder(8192);
        sb.append("[");
        boolean first = true;

        for (String name : cm.getAllCountries()) {
            try {
                if (!first) sb.append(",");
                first = false;
                UUID owner = cm.getOwner(name);
                int claims = cm.getClaimCount(name);
                int delta7d = snapshots != null ? snapshots.getClaimsDelta7d(name, claims) : 0;
                int warsActive = countActiveWars(cm, name);

                // Формула активности: дельта чанков *3 + войны *5
                int activity = delta7d * 3 + warsActive * 5;

                sb.append("{");
                appendKey(sb, "name"); sb.append(JsonUtil.escape(name)); sb.append(",");
                appendKey(sb, "color"); sb.append(JsonUtil.escape(MapRenderer.hashColorHex(name))); sb.append(",");
                appendKey(sb, "owner"); sb.append(owner != null ? JsonUtil.escape(safeName(owner)) : "null"); sb.append(",");
                appendKey(sb, "claims"); sb.append(claims); sb.append(",");
                appendKey(sb, "bank"); sb.append(round2(cm.getBankBalance(name))); sb.append(",");
                appendKey(sb, "debt"); sb.append(round2(cm.getCountryDebt(name))); sb.append(",");
                appendKey(sb, "claims_delta_7d"); sb.append(delta7d); sb.append(",");
                appendKey(sb, "activity"); sb.append(activity); sb.append(",");
                appendKey(sb, "active_wars"); sb.append(warsActive);
                if (owner != null) {
                    sb.append(",");
                    appendKey(sb, "energy"); sb.append(round1(em.getEnergy(owner))); sb.append(",");
                    appendKey(sb, "max_energy"); sb.append(round1(em.getMaxEnergy(owner))); sb.append(",");
                    appendKey(sb, "regen"); sb.append(round1(em.getCurrentRegenPerHour(owner))); sb.append(",");
                    appendKey(sb, "max_claims"); sb.append(cm.getMaxClaims(owner)); sb.append(",");
                    appendKey(sb, "farm_level"); sb.append(em.getFarmUpgradeLevel(owner));
                }
                sb.append(",");
                appendKey(sb, "pacts"); sb.append(countPacts(cm, name)); sb.append(",");
                appendKey(sb, "allies"); sb.append(countAllies(cm, name)); sb.append(",");
                appendKey(sb, "chunks_farm"); sb.append(plugin.getChunkUpgradeManager().countType(name, "farm")); sb.append(",");
                appendKey(sb, "chunks_mining"); sb.append(plugin.getChunkUpgradeManager().countType(name, "mining")); sb.append(",");
                appendKey(sb, "chunks_military"); sb.append(plugin.getChunkUpgradeManager().countType(name, "military")); sb.append(",");
                appendKey(sb, "chunks_trade"); sb.append(plugin.getChunkUpgradeManager().countType(name, "trade"));
                sb.append("}");
            } catch (Exception ignored) {}
        }
        sb.append("]");
        return sb.toString();
    }

    private int countActiveWars(CountryManager cm, String name) {
        int n = 0;
        try (var ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT COUNT(*) FROM wars WHERE attacker=? OR defender=?")) {
            ps.setString(1, name);
            ps.setString(2, name);
            var rs = ps.executeQuery();
            if (rs.next()) n = rs.getInt(1);
        } catch (Exception ignored) {}
        return n;
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
                sb.append(buildPlayerJson(p.getUniqueId(), true));
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
                    sb.append(buildPlayerJson(uuid, false));
                } catch (Exception ignored) {}
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildPlayerJson(UUID uuid, boolean online) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        Player p = online ? op.getPlayer() : null;
        String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);

        StringBuilder sb = new StringBuilder(512);
        sb.append("{");
        appendKey(sb, "uuid"); sb.append(JsonUtil.escape(uuid.toString())); sb.append(",");
        appendKey(sb, "name"); sb.append(JsonUtil.escape(name)); sb.append(",");
        appendKey(sb, "online"); sb.append(online ? "true" : "false");

        String country = plugin.getCountryManager().getCountryName(uuid);
        if (country != null) {
            sb.append(",");
            appendKey(sb, "country"); sb.append(JsonUtil.escape(country)); sb.append(",");
            appendKey(sb, "country_color"); sb.append(JsonUtil.escape(MapRenderer.hashColorHex(country))); sb.append(",");
            appendKey(sb, "country_role");
            if (plugin.getCountryManager().isLeader(uuid, country)) sb.append("\"leader\"");
            else if (plugin.getCountryManager().isCoRuler(uuid, country)) sb.append("\"co_ruler\"");
            else sb.append("null");
        }

        sb.append(",");
        appendKey(sb, "balance"); sb.append(round2(plugin.getEconomyManager().getBalance(op))); sb.append(",");
        appendKey(sb, "energy"); sb.append(round1(plugin.getEnergyManager().getEnergy(uuid))); sb.append(",");
        appendKey(sb, "max_energy"); sb.append(round1(plugin.getEnergyManager().getMaxEnergy(uuid)));

        PlayerStatsManager stats = plugin.getPlayerStatsManager();
        if (online && p != null) {
            long ticks = 0;
            try { ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE); } catch (Exception ignored) {}
            sb.append(",");
            appendKey(sb, "playtime_seconds"); sb.append(ticks / 20L);
            if (stats != null) {
                sb.append(",");
                appendKey(sb, "first_seen"); sb.append(stats.getFirstSeen(uuid)); sb.append(",");
                appendKey(sb, "last_seen"); sb.append(System.currentTimeMillis());
            }
            try {
                sb.append(",");
                appendKey(sb, "kills"); sb.append(p.getStatistic(Statistic.PLAYER_KILLS)); sb.append(",");
                appendKey(sb, "deaths"); sb.append(p.getStatistic(Statistic.DEATHS));
            } catch (Exception ignored) {}
            JobsIntegration jobs = plugin.getJobsIntegration();
            if (jobs != null && jobs.isAvailable()) {
                String job = jobs.getJob(p);
                if (job != null) {
                    sb.append(",");
                    appendKey(sb, "job"); sb.append(JsonUtil.escape(job));
                    int lvl = jobs.getJobLevel(p);
                    if (lvl >= 0) { sb.append(","); appendKey(sb, "job_level"); sb.append(lvl); }
                }
            }
            Location loc = p.getLocation();
            if (loc.getWorld() != null) {
                sb.append(",");
                appendKey(sb, "position");
                sb.append("{\"world\":").append(JsonUtil.escape(loc.getWorld().getName()));
                sb.append(",\"x\":").append(round1(loc.getX()));
                sb.append(",\"y\":").append(round1(loc.getY()));
                sb.append(",\"z\":").append(round1(loc.getZ())).append("}");
            }
        } else if (stats != null) {
            sb.append(",");
            appendKey(sb, "playtime_seconds"); sb.append(stats.getPlaytimeSeconds(uuid)); sb.append(",");
            appendKey(sb, "first_seen"); sb.append(stats.getFirstSeen(uuid)); sb.append(",");
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

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private int countPacts(CountryManager cm, String name) {
        int n = 0;
        PactManager pm = plugin.getPactManager();
        if (pm == null) return 0;
        for (String o : cm.getAllCountries()) {
            if (o.equals(name)) continue;
            if (pm.hasPact(name, o, "trade")) n++;
            if (pm.hasPact(name, o, "military")) n++;
            if (pm.hasPact(name, o, "defense")) n++;
            if (pm.hasPact(name, o, "nonaggression")) n++;
        }
        return n;
    }

    private int countAllies(CountryManager cm, String name) {
        int n = 0;
        for (String o : cm.getAllCountries()) {
            if (!o.equals(name) && cm.isAlly(name, o)) n++;
        }
        return n;
    }

    private String safeName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String n = op.getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }
}
