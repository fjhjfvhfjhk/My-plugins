package com.example.bounty;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Выдаёт награду убийце при смерти цели.
 */
public class BountyListener implements Listener {

    private final BountyPlugin plugin;
    private final BountyManager bountyManager;
    private final EconomyManager economyManager;

    public BountyListener(BountyPlugin plugin, BountyManager bountyManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.bountyManager = bountyManager;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        BountyManager.BountyEntry bounty = bountyManager.getB
