package com.example.sovereignty;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TerritoryListener implements Listener {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final Map<UUID, String> currentTerritoryKey = new HashMap<>();

    public TerritoryListener(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = player.getLocation().getChunk();
        String key = getTerritoryKey(chunk.getWorld(), chunk.getX(), chunk.getZ());
        currentTerritoryKey.put(player.getUniqueId(), key);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Chunk from = event.getFrom().getChunk();
        Chunk to = event.getTo() == null ? null : event.getTo().getChunk();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && from.getWorld().equals(to.getWorld())) return;

        String newKey = getTerritoryKey(to.getWorld(), to.getX(), to.getZ());
        String oldKey = currentTerritoryKey.get(player.getUniqueId());

        if (oldKey == null || !oldKey.equals(newKey)) {
            showTerritoryTitle(player, newKey);
        }
        currentTerritoryKey.put(player.getUniqueId(), newKey);
    }

    private String getTerritoryKey(World world, int x, int z) {
        String owner = countryManager.getChunkOwner(world, x, z);
        return owner == null ? "__WILD__" : owner;
    }

    private void showTerritoryTitle(Player player, String territoryKey) {
        if (territoryKey.equals("__WILD__")) {
            player.sendTitle("§2Дикие земли", "", 10, 30, 10);
            return;
        }

        String myCountry = countryManager.getCountryName(player.getUniqueId());
        String relationColor;
        if (territoryKey.equals(myCountry)) {
            relationColor = "§a"; // своя — зелёный
        } else if (myCountry != null && countryManager.isAlly(myCountry, territoryKey)) {
            relationColor = "§a"; // альянс — зелёный
        } else if (myCountry != null && countryManager.isEnemy(myCountry, territoryKey)) {
            relationColor = "§c"; // война — красный
        } else {
            relationColor = "§7"; // нейтралитет — серый
        }

        player.sendTitle(relationColor + territoryKey, "", 10, 30, 10);
    }
}
