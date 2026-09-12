package com.example.bounty;

import org.bukkit.Bukkit;
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
        if (killer.getUniqueId().equals(victim.getUniqueId())) return;

        BountyManager.BountyEntry bounty = bountyManager.getBounty(victim.getUniqueId());
        if (bounty == null) return;

        double amount = bounty.amount;
        bountyManager.removeBounty(victim.getUniqueId());
        economyManager.deposit(killer, amount);

        killer.sendMessage("§6§l💰 ВЫ ПОЛУЧИЛИ НАГРАДУ: §f" + economyManager.format(amount) +
                " §6за голову §f" + victim.getName());
        victim.sendMessage("§cНаграда за вашу голову была получена игроком §f" + killer.getName());

        Bukkit.broadcastMessage("§4☠ §f" + killer.getName() +
                " §7получил награду §f" + economyManager.format(amount) +
                " §7за голову §f" + victim.getName());
    }
}
