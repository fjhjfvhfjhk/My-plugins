package com.example.sovereignty;

import org.bukkit.Bukkit;
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

import java.util.UUID;

public class ProtectionListener implements Listener {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;

    public ProtectionListener(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    /**
     * Проверяет, имеет ли игрок право взаимодействовать с чанком (строить/ломать).
     *
     * Порядок проверок:
     *  1. Дикая земля — все могут.
     *  2. Лидер или соправитель владельца — полный доступ.
     *  3. Союзник (даже если чанк защищённый) — доступ по ally-build.
     *  4. Владелец чанка оффлайн — полный запрет (защита от обворовывания спящих).
     *  5. Защищённый чанк (для остальных) — запрет.
     *  6. Враг — разрешено.
     *  7. В остальных случаях — запрет.
     */
    private boolean canBuild(Player player, Chunk chunk) {
        String owner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (owner == null) return true;

        // 1. Лидер / соправитель владеющей страны — полный доступ
        if (countryManager.isLeaderOrCoRuler(player.getUniqueId(), owner)) return true;

        String myCountry = countryManager.getCountryName(player.getUniqueId());

        // 2. Союзник — полный доступ, включая защищённые чанки
        if (myCountry != null && countryManager.isAlly(myCountry, owner)) {
            return plugin.getConfig().getBoolean("ally-build", true);
        }

        // 3. Если владелец чанка оффлайн — никто посторонний не может взаимодействовать
        UUID ownerUuid = countryManager.getOwner(owner);
        if (ownerUuid != null && Bukkit.getPlayer(ownerUuid) == null) {
            return false;
        }

        // 4. Защищённый чанк — запрещён всем, кроме союзников (уже отсеяли выше)
        if (plugin.getDefensiveManager().isDefended(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            return false;
        }

        // 5. Враг — можно
        if (myCountry != null && countryManager.isEnemy(myCountry, owner)) {
            return true;
        }

        return false;
    }

    private boolean canOpenContainer(Player player, Chunk chunk) {
        String owner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (owner == null) return true;

        if (countryManager.isLeaderOrCoRuler(player.getUniqueId(), owner)) return true;

        String myCountry = countryManager.getCountryName(player.getUniqueId());

        // Союзник — полный доступ, включая защищённые чанки
        if (myCountry != null && countryManager.isAlly(myCountry, owner)) {
            return true;
        }

        // Владелец оффлайн — не даём открывать сундуки
        UUID ownerUuid = countryManager.getOwner(owner);
        if (ownerUuid != null && Bukkit.getPlayer(ownerUuid) == null) {
            return false;
        }

        if (plugin.getDefensiveManager().isDefended(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            return false;
        }

        if (myCountry != null && countryManager.isEnemy(myCountry, owner)) {
            return true;
        }

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

            if (plugin.getPactManager().hasNonAggressionPact(a, b)) {
                e.setCancelled(true);
                p.sendMessage("§cМежду вашими странами действует пакт о ненападении.");
                return;
            }

            boolean allow;
            if (countryManager.isAlly(a, b)) allow = plugin.getConfig().getBoolean("pvp-ally", false);
            else if (countryManager.isEnemy(a, b)) allow = plugin.getConfig().getBoolean("pvp-enemy", true);
            else allow = plugin.getConfig().getBoolean("pvp-neutral", false);

            if (!allow) {
                e.setCancelled(true);
                p.sendMessage("§cНельзя атаковать этого игрока.");
                return;
            }

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
