package com.example.sovereignty;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

public class ProtectionListener implements Listener {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;

    public ProtectionListener(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    private boolean canBuild(Player player, Chunk chunk) {
        String owner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (owner == null) return true;

        String myCountry = countryManager.getCountryName(player.getUniqueId());
        if (owner.equals(myCountry)) return true;

        if (plugin.getDefensiveManager().isDefended(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            return false;
        }

        if (myCountry != null && countryManager.isAlly(myCountry, owner)) {
            return plugin.getConfig().getBoolean("ally-build", true);
        }

        if (myCountry != null && countryManager.isEnemy(myCountry, owner)) {
            return true;
        }

        return false;
    }

    private boolean canOpenContainer(Player player, Chunk chunk) {
        String owner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (owner == null) return true;

        String myCountry = countryManager.getCountryName(player.getUniqueId());
        if (owner.equals(myCountry)) return true;

        if (plugin.getDefensiveManager().isDefended(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            return false;
        }

        if (myCountry != null && countryManager.isAlly(myCountry, owner)) return true;
        if (myCountry != null && countryManager.isEnemy(myCountry, owner)) return true;

        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы не можете ломать блоки на этой территории.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы не можете строить на этой территории.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv.getLocation() == null) return;
        Chunk chunk = inv.getLocation().getChunk();
        if (!canOpenContainer(player, chunk)) {
            event.setCancelled(true);
            player.sendMessage("§cВы не можете открывать контейнеры на этой территории.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && e.getEntity() instanceof Player v) {
            if (plugin.getEventManager().isPvpDisabled()) {
                e.setCancelled(true);
                p.sendMessage("§cСейчас мирное время, PvP запрещён.");
                return;
            }

            String a = countryManager.getCountryName(p.getUniqueId());
            String b = countryManager.getCountryName(v.getUniqueId());
            if (a == null || b == null) return;

            boolean allow;
            if (countryManager.isAlly(a, b)) allow = plugin.getConfig().getBoolean("pvp-ally", false);
            else if (countryManager.isEnemy(a, b)) allow = plugin.getConfig().getBoolean("pvp-enemy", true);
            else allow = plugin.getConfig().getBoolean("pvp-neutral", false);

            if (!allow) {
                e.setCancelled(true);
                p.sendMessage("§cНельзя атаковать этого игрока.");
                return;
            }

            // Военная наука: +5% урона по врагам на своей территории
            if (countryManager.isEnemy(a, b)) {
                Chunk chunk = v.getLocation().getChunk();
                String chunkOwner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
                if (b.equals(chunkOwner) && plugin.getScienceManager().isResearched(p.getUniqueId(), "military_science")) {
                    e.setDamage(e.getDamage() * 1.05);
                }
            }
        }
    }
}
