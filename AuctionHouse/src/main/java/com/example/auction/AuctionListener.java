package com.example.auction;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AuctionListener implements Listener {

    private final AuctionPlugin plugin;

    public AuctionListener(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // В будущем можно выдавать выигранные предметы офлайн-победителям
        // Пока просто уведомление о завершённых аукционах (можно добавить позже)
    }
}
