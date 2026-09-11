package com.example.sovereignty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    // Храним последнюю страну игрока (для title)
    private final Map<UUID, String> lastOwner = new HashMap<>();
    // Храним последний чанк игрока (для actionbar типа)
    private final Map<UUID, String> lastChunk = new HashMap<>();

    public TerritoryListener(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = player.getLocation().getChunk();
        String owner = getOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        lastOwner.put(player.getUniqueId(), owner);
        lastChunk.put(player.getUniqueId(), chunkKey(chunk.getWorld(), chunk.getX(), chunk.getZ()));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Chunk from = event.getFrom().getChunk();
        Chunk to = event.getTo() == null ? null : event.getTo().getChunk();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && from.getWorld().equals(to.getWorld())) return;

        UUID uuid = player.getUniqueId();
        String newChunkKey = chunkKey(to.getWorld(), to.getX(), to.getZ());
        String newOwner = getOwner(to.getWorld(), to.getX(), to.getZ());
        String oldOwner = lastOwner.get(uuid);

        // ActionBar с типом — показывается при каждом переходе на новый чанк
        if (!newChunkKey.equals(lastChunk.get(uuid))) {
            showTypeActionBar(player, to.getWorld(), to.getX(), to.getZ());
            lastChunk.put(uuid, newChunkKey);
        }

        // Title с названием страны — только при пересечении границы страны
        boolean ownerChanged = (oldOwner == null && newOwner != null)
                || (oldOwner != null && !oldOwner.equals(newOwner));
        if (ownerChanged) {
            showTerritoryTitle(player, newOwner);
            lastOwner.put(uuid, newOwner);
        }
    }

    private String chunkKey(World world, int x, int z) {
        return world.getName() + ":" + x + ":" + z;
    }

    private String getOwner(World world, int x, int z) {
        String owner = countryManager.getChunkOwner(world, x, z);
        return owner == null ? "__WILD__" : owner;
    }

    private void showTerritoryTitle(Player player, String owner) {
        if (owner == null || owner.equals("__WILD__")) {
            player.sendTitle("§2Дикие земли", "", 10, 30, 10);
            return;
        }

        String myCountry = countryManager.getCountryName(player.getUniqueId());
        String relationColor;
        if (owner.equals(myCountry)) {
            relationColor = "§a";
        } else if (myCountry != null && countryManager.isAlly(myCountry, owner)) {
            relationColor = "§a";
        } else if (myCountry != null && countryManager.isEnemy(myCountry, owner)) {
            relationColor = "§c";
        } else {
            relationColor = "§7";
        }

        player.sendTitle(relationColor + owner, "", 10, 30, 10);
    }

    private void showTypeActionBar(Player player, World world, int x, int z) {
        String type = plugin.getChunkUpgradeManager().getChunkType(world, x, z);
        String typeName;
        NamedTextColor color;
        switch (type) {
            case "farm" -> { typeName = "🌾 Ферма"; color = NamedTextColor.GREEN; }
            case "mining" -> { typeName = "⛏ Шахта"; color = NamedTextColor.AQUA; }
            case "military" -> { typeName = "⚔ Военный"; color = NamedTextColor.RED; }
            case "trade" -> { typeName = "💰 Торговый"; color = NamedTextColor.GOLD; }
            default -> {
                if (plugin.getDefensiveManager().isDefended(world, x, z)) {
                    typeName = "🛡 Защитный"; color = NamedTextColor.LIGHT_PURPLE;
                } else {
                    typeName = "• Обычный"; color = NamedTextColor.GRAY;
                }
            }
        }
        if (plugin.getDefensiveManager().isDefended(world, x, z) && !type.equals("normal")) {
            player.sendActionBar(Component.text("Тип: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(typeName, color))
                    .append(Component.text(" | 🛡 Защита", NamedTextColor.LIGHT_PURPLE)));
        } else {
            player.sendActionBar(Component.text("Тип чанка: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(typeName, color)));
        }
    }
}
