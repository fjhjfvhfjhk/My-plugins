package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Мягкая интеграция с Jobs через рефлексию.
 * Если Jobs не установлен или API изменился — все методы возвращают null / -1.
 *
 * Используется для показа профессии игрока на веб-панели.
 */
public class JobsIntegration {

    private final SovereigntyPlugin plugin;
    private boolean available = false;

    // Кешированные методы
    private Object jobsManager;
    private Method getJobsPlayerMethod;
    private Method getJobProgressionMethod;
    private Method getJobNameMethod;
    private Method getLevelMethod;

    public JobsIntegration(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        Plugin jobs = Bukkit.getPluginManager().getPlugin("Jobs");
        if (jobs == null) {
            return;
        }

        try {
            // jobs.getJobsManager()
            Method getManager = jobs.getClass().getMethod("getJobsManager");
            this.jobsManager = getManager.invoke(jobs);

            if (jobsManager == null) return;

            Class<?> managerClass = jobsManager.getClass();

            // manager.getJobsPlayer(Player)
            this.getJobsPlayerMethod = managerClass.getMethod("getJobsPlayer", Player.class);

            // Пробуем получить класс JobsPlayer
            Class<?> jobsPlayerClass = Class.forName("com.gamingmesh.jobs.container.JobsPlayer");

            // jobsPlayer.getJobProgression() → Map<String, JobProgression>
            this.getJobProgressionMethod = jobsPlayerClass.getMethod("getJobProgression");

            // Class JobProgression: getJob() → Job; Job: getName() → String; getMaxLevel() / getLevel()
            Class<?> jobProgressionClass = Class.forName("com.gamingmesh.jobs.container.JobProgression");
            Method getJobMethod = jobProgressionClass.getMethod("getJob");
            Class<?> jobClass = Class.forName("com.gamingmesh.jobs.container.Job");
            this.getJobNameMethod = jobClass.getMethod("getName");
            this.getLevelMethod = jobProgressionClass.getMethod("getLevel");

            // Сохраняем getJobMethod отдельно
            this.cachedGetJobMethod = getJobMethod;

            this.available = true;
            plugin.getLogger().info("[JobsIntegration] Jobs найден — профессии будут показаны на панели.");
        } catch (Throwable t) {
            plugin.getLogger().info("[JobsIntegration] Jobs установлен, но API не распознан: " + t.getMessage());
            this.available = false;
        }
    }

    private Method cachedGetJobMethod;

    public boolean isAvailable() {
        return available;
    }

    /**
     * @return название профессии с максимальным уровнем, либо null.
     */
    public String getJob(Player player) {
        if (!available) return null;
        try {
            Object jobsPlayer = getJobsPlayerMethod.invoke(jobsManager, player);
            if (jobsPlayer == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> progression = (Map<String, Object>) getJobProgressionMethod.invoke(jobsPlayer);
            if (progression == null || progression.isEmpty()) return null;

            String bestJob = null;
            int bestLevel = -1;

            for (Object prog : progression.values()) {
                if (prog == null) continue;
                Object job = cachedGetJobMethod.invoke(prog);
                if (job == null) continue;
                String name = (String) getJobNameMethod.invoke(job);
                int level = ((Number) getLevelMethod.invoke(prog)).intValue();
                if (level > bestLevel) {
                    bestLevel = level;
                    bestJob = name;
                }
            }
            return bestJob;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * @return уровень профессии с максимальным уровнем, либо -1.
     */
    public int getJobLevel(Player player) {
        if (!available) return -1;
        try {
            Object jobsPlayer = getJobsPlayerMethod.invoke(jobsManager, player);
            if (jobsPlayer == null) return -1;

            @SuppressWarnings("unchecked")
            Map<String, Object> progression = (Map<String, Object>) getJobProgressionMethod.invoke(jobsPlayer);
            if (progression == null || progression.isEmpty()) return -1;

            int bestLevel = -1;
            for (Object prog : progression.values()) {
                if (prog == null) continue;
                int level = ((Number) getLevelMethod.invoke(prog)).intValue();
                if (level > bestLevel) bestLevel = level;
            }
            return bestLevel;
        } catch (Throwable t) {
            return -1;
        }
    }
}
