package com.example.roll;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class GameListener implements Listener {

    private final RollPlugin plugin;

    public GameListener(RollPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Если игра активна, можно показать уведомление
        if (plugin.getGameManager().isActive()) {
            player.sendMessage("§6Идёт игра в рулетку! Используйте /roll, чтобы присоединиться.");
        }
    }
}
