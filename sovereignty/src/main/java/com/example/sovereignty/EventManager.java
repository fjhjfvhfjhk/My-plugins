package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public class EventManager {

    private final SovereigntyPlugin plugin;
    private String currentEvent = null;
    private long eventEndTime = 0;

    public EventManager(SovereigntyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long intervalTicks = plugin.getConfig().getLong("events.interval-minutes", 60) * 60 * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                rollEvent();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void rollEvent() {
        if (!plugin.getConfig().getBoolean("events.enabled", true)) return;

        Random random = new Random();
        int chanceNegative = plugin.getConfig().getInt("events.chance-negative", 50);
        int chancePositive = plugin.getConfig().getInt("events.chance-positive", 30);
        int totalChance = chanceNegative + chancePositive;
        int roll = random.nextInt(totalChance);

        if (roll < chanceNegative) {
            startNegativeEvent();
        } else {
            startPositiveEvent();
        }
    }

    private void startNegativeEvent() {
        List<String> negatives = plugin.getConfig().getStringList("events.negative");
        if (negatives.isEmpty()) return;
        String event = negatives.get(new Random().nextInt(negatives.size()));
        activateEvent(event);
    }

    private void startPositiveEvent() {
        List<String> positives = plugin.getConfig().getStringList("events.positive");
        if (positives.isEmpty()) return;
        String event = positives.get(new Random().nextInt(positives.size()));
        activateEvent(event);
    }

    /**
     * Ручной запуск события по ID.
     */
    public boolean forceEvent(String eventId) {
        List<String> all = new java.util.ArrayList<>();
        all.addAll(plugin.getConfig().getStringList("events.negative"));
        all.addAll(plugin.getConfig().getStringList("events.positive"));
        if (!all.contains(eventId)) {
            return false;
        }
        if (currentEvent != null) {
            stopCurrentEvent();
        }
        activateEvent(eventId);
        return true;
    }

    /**
     * Останавливает текущее событие вручную.
     */
    public boolean stopCurrentEvent() {
        if (currentEvent == null) return false;
        currentEvent = null;
        eventEndTime = 0;
        Bukkit.broadcastMessage("§6[Событие] §eТекущее событие было остановлено администратором.");
        return true;
    }

    private void activateEvent(String event) {
        currentEvent = event;
        long durationMinutes = plugin.getConfig().getLong("events.duration-minutes", 45);
        eventEndTime = System.currentTimeMillis() + durationMinutes * 60000L;

        String announcement = plugin.getConfig().getString("events.announcements." + event,
                "§6[Событие] §e" + event + " началось!");
        Bukkit.broadcastMessage(announcement);

        applyImmediateEffects(event);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentEvent != null && currentEvent.equals(event)) {
                    String endMsg = plugin.getConfig().getString("events.end-announcements." + event,
                            "§6[Событие] §e" + event + " закончилось.");
                    Bukkit.broadcastMessage(endMsg);
                    currentEvent = null;
                    eventEndTime = 0;
                }
            }
        }.runTaskLater(plugin, durationMinutes * 60 * 20L);
    }

    private void applyImmediateEffects(String event) {
        switch (event) {
            case "mining_boom" -> {
                int durationSeconds = plugin.getConfig().getInt("events.mining-boom-haste-seconds", 3600);
                int hasteLevel = plugin.getConfig().getInt("events.mining-boom-haste-level", 1);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.HASTE,
                            durationSeconds * 20,
                            hasteLevel - 1,
                            true,
                            true
                    ));
                }
            }
            case "energy_surge" -> {
                double energyBonus = plugin.getConfig().getDouble("events.energy-surge-bonus", 3.0);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.getEnergyManager().addEnergy(player.getUniqueId(), energyBonus);
                }
            }
        }
    }

    public String getCurrentEventId() {
        if (currentEvent == null) return null;
        if (System.currentTimeMillis() >= eventEndTime) {
            currentEvent = null;
            eventEndTime = 0;
            return null;
        }
        return currentEvent;
    }

    public boolean isEventActive(String event) {
        return event.equals(getCurrentEventId());
    }

    /**
     * Возвращает название активного события или null, если событий нет.
     */
    public String getActiveEventName() {
        String id = getCurrentEventId();
        if (id == null) return null;

        String descLine = plugin.getConfig().getString("events.descriptions." + id);
        if (descLine != null && descLine.contains("|")) {
            return descLine.split("\\|")[0].trim();
        }
        // Запасной вариант: берём первое слово из анонса
        String announcement = plugin.getConfig().getString("events.announcements." + id,
                id);
        return announcement.replaceAll("§[0-9a-f]", "").trim();
    }

    /**
     * Возвращает описание активного события или null.
     */
    public String getActiveEventDescription() {
        String id = getCurrentEventId();
        if (id == null) return null;

        String descLine = plugin.getConfig().getString("events.descriptions." + id);
        if (descLine != null && descLine.contains("|")) {
            return descLine.split("\\|")[1].trim();
        }
        return "";
    }

    public double getFarmIncomeMultiplier() {
        if (isEventActive("drought")) return 0.5;
        if (isEventActive("flood")) return 0.6;
        if (isEventActive("harvest_season")) return 1.75;
        return 1.0;
    }

    public double getTradeIncomeMultiplier() {
        if (isEventActive("flood")) return 0.6;
        if (isEventActive("trade_boom")) return 1.5;
        return 1.0;
    }

    public double getEnergyRegenMultiplier() {
        if (isEventActive("plague")) return 0.5;
        return 1.0;
    }

    public boolean isPvpDisabled() {
        return isEventActive("peace_time");
    }
}
