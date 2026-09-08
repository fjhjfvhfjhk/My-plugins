package com.example.sovereignty;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class AutoclaimListener implements Listener {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;

    public AutoclaimListener(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isAutoClaimEnabled(player.getUniqueId())) return;

        Chunk from = event.getFrom().getChunk();
        Chunk to = event.getTo() == null ? null : event.getTo().getChunk();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && from.getWorld().equals(to.getWorld())) {
            return;
        }

        String myCountry = countryManager.getCountryName(player.getUniqueId());
        if (myCountry == null) {
            player.sendMessage("§cУ вас нет страны, автозахват выключен.");
            plugin.setAutoClaim(player.getUniqueId(), false);
            return;
        }

        String existingOwner = countryManager.getChunkOwner(to.getWorld(), to.getX(), to.getZ());
        if (existingOwner != null && existingOwner.equals(myCountry)) {
            return; // уже наш чанк, не тратим энергию
        }

        double cost = plugin.getConfig().getDouble("claim-energy-cost", 2.0);
        if (plugin.getEnergyManager().getEnergy(player.getUniqueId()) < cost) {
            player.sendMessage("§cЭнергия закончилась, автозахват автоматически выключен.");
            plugin.setAutoClaim(player.getUniqueId(), false);
            return;
        }

        if (countryManager.claimChunk(player)) {
            player.sendMessage("§a[Автозахват] Чанк захвачен!");
        } else {
            // Если после попытки энергия кончилась — выключаем
            if (plugin.getEnergyManager().getEnergy(player.getUniqueId()) < cost) {
                player.sendMessage("§cЭнергия закончилась, автозахват автоматически выключен.");
                plugin.setAutoClaim(player.getUniqueId(), false);
            }
        }
    }
}
