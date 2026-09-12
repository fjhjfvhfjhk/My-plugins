package com.example.sovereignty;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Ловит вход/выход игрока, обновляет статистику.
 *
 * playtimeSeconds = Statistic.PLAY_ONE_MINUTE / 20.
 * Для импортных значений см. PlayerStatsManager.onQuit — там max с БД.
 */
public class PlayerStatsListener implements Listener {

    private final SovereigntyPlugin plugin;
    private final PlayerStatsManager stats;

    public PlayerStatsListener(SovereigntyPlugin plugin, PlayerStatsManager stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stats.onJoin(player.getUniqueId());

        if (plugin.getWebPanelUploader() != null) {
            plugin.getWebPanelUploader().scheduleForcePush();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long playtimeTicks = 0;
        try {
            playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        } catch (Exception ignored) {}
        long playtimeSeconds = playtimeTicks / 20L;

        stats.onQuit(uuid, playtimeSeconds, player.getLocation());

        if (plugin.getWebPanelUploader() != null) {
            plugin.getWebPanelUploader().scheduleForcePush();
        }
    }
}
