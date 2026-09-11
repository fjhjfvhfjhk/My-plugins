package com.example.sovereignty.web;

import com.example.sovereignty.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
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
 * Публикация: старт / раз в N минут / выключение / /country panel push /
 * авто-пуш при накоплении N новых чанков.
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
        intervalMinutes = config.getInt("interval-minutes", 60);
        autoPushChunkThreshold = config.getInt("auto-push-chunk-threshold", 200);

        if (enabled) {
            if (ghOwner.isEmpty() || ghRepo.isEmpty() || ghToken.isEmpty()) {
                plugin.getLogger().warning("[WebPanel] Не настроены owner/repo/token — публикация отключена.");
                enabled = false;
            } else {
                plugin.getLogger().info("[WebPanel] Включён. Цель: " + ghOwner + "/" + ghRepo);
            }
        }
    }

    public void start() {
        if (!enabled) return;

        // Первая публикация через 5 сек
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::push, 100L);

        // Периодическая публикация (раз в N минут)
        long periodTicks = 20L * 60L * intervalMinutes;
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::push, periodTicks, periodTicks);

        // Авто-пуш при накоплении N новых чанков — проверка раз в минуту
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
        if (!enabled) return;
        try {
            pushInternal();
        } catch (Exception ignored) {}
    }

    public boolean isEnabled() { return enabled; }

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
                byte[] png = renderer.renderMap();
                if (png != null) {
                    uploadBinary(ghMapPath, png, "Auto-update map");
                } else {
                    plugin.getLogger().info("[WebPanel] Нет чанков — карта не сгенерирована.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[WebPanel] Ошибка карты: " + e.getMessage());
            }
        }
    }

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

    private String buildJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("updated_at", System.currentTimeMillis());
        root.put("server_name", serverName);
        root.put("online_players", Bukkit.getOnlinePlayers().size());
        root.put("max_players", Bukkit.getMaxPlayers());

        List<Map<String, Object>> countries = new ArrayList<>();
        CountryManager cm = plugin.getCountryManager();
        EnergyManager em = plugin.getEnergyManager();

        for (String name : cm.getAllCountries()) {
            try {
                Map<String, Object> c = new LinkedHashMap<>();
                UUID owner = cm.getOwner(name);
                c.put("name", name);
                c.put("owner", owner != null ? safeName(owner) : "???");
                c.put("claims", cm.getClaimCount(name));
                c.put("bank", Math.round(cm.getBankBalance(name) * 100.0) / 100.0);
                c.put("debt", Math.round(cm.getCountryDebt(name) * 100.0) / 100.0);
                if (owner != null) {
                    c.put("energy", Math.round(em.getEnergy(owner) * 10.0) / 10.0);
                    c.put("max_energy", Math.round(em.getMaxEnergy(owner) * 10.0) / 10.0);
                    c.put("regen", Math.round(em.getCurrentRegenPerHour(owner) * 10.0) / 10.0);
                    c.put("max_claims", cm.getMaxClaims(owner));
                    c.put("farm_level", em.getFarmUpgradeLevel(owner));
                }
                int pacts = 0;
                for (String other : cm.getAllCountries()) {
                    if (other.equals(name)) continue;
                    if (plugin.getPactManager().hasPact(name, other, "trade")) pacts++;
                    if (plugin.getPactManager().hasPact(name, other, "military")) pacts++;
                    if (plugin.getPactManager().hasPact(name, other, "defense")) pacts++;
                    if (plugin.getPactManager().hasPact(name, other, "nonaggression")) pacts++;
                }
                c.put("pacts", pacts);
                int allies = 0;
                for (String other : cm.getAllCountries()) {
                    if (!other.equals(name) && cm.isAlly(name, other)) allies++;
                }
                c.put("allies", allies);
                c.put("chunks_farm", plugin.getChunkUpgradeManager().countType(name, "farm"));
                c.put("chunks_mining", plugin.getChunkUpgradeManager().countType(name, "mining"));
                c.put("chunks_military", plugin.getChunkUpgradeManager().countType(name, "military"));
                c.put("chunks_trade", plugin.getChunkUpgradeManager().countType(name, "trade"));
                countries.add(c);
            } catch (Exception ignored) {}
        }
        root.put("countries", countries);
        root.put("countries_count", countries.size());

        return JsonUtil.toJson(root);
    }

    private String safeName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String n = op.getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }
}
