package com.example.sovereignty;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    private final SovereigntyPlugin plugin;
    private final EnergyManager energyManager;

    public DeathListener(SovereigntyPlugin plugin, EnergyManager energyManager) {
        this.plugin = plugin;
        this.energyManager = energyManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Штраф энергии
        double loss = plugin.getConfig().getDouble("energy.death-energy-loss", 1.0);
        energyManager.forceRemoveEnergy(player.getUniqueId(), loss);
        player.sendMessage("§cВы погибли! Потеряно энергии: " + String.format("%.1f", loss));

        // Снятие защиты с защитных чанков на время
        plugin.getDefensiveManager().onPlayerDeath(player.getUniqueId());
    }
}
