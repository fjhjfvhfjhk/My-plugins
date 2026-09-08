package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TechnologyGUI implements Listener {

    private static final String TITLE = "§6Исследования";

    private final SovereigntyPlugin plugin;
    private final ScienceManager scienceManager;

    public TechnologyGUI(SovereigntyPlugin plugin, ScienceManager scienceManager) {
        this.plugin = plugin;
        this.scienceManager = scienceManager;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, String[]> techs = scienceManager.getTechnologies();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillEmptyWithGlass(inv);

        int slot = 0;
        for (Map.Entry<String, String[]> entry : techs.entrySet()) {
            if (slot >= 45) break;
            String id = entry.getKey();
            String[] info = entry.getValue();
            String name = info[0];
            String desc = info[1];
            double cost = Double.parseDouble(info[2]);
            String dependency = info[3];

            boolean researched = scienceManager.isResearched(uuid, id);
            boolean available = !researched && (dependency == null || scienceManager.isResearched(uuid, dependency));
            double points = scienceManager.getSciencePoints(uuid);

            Material mat;
            if (researched) mat = Material.LIME_WOOL;
            else if (available && points >= cost) mat = Material.KNOWLEDGE_BOOK;
            else mat = Material.GRAY_DYE;

            ItemStack icon = new ItemStack(mat);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName((researched ? "§a" : "§f") + name + (researched ? " (изучено)" : ""));
            List<String> lore = new ArrayList<>();
            lore.add("§7" + desc);
            lore.add("§7Стоимость: §f" + String.format("%.0f", cost) + " очков");
            if (dependency != null) lore.add("§7Требуется: §f" + techs.get(dependency)[0]);
            lore.add("§7Ваши очки науки: §f" + String.format("%.1f", points));
            if (researched) lore.add("§aИзучено");
            else if (!available) lore.add("§cНедоступно");
            else if (points < cost) lore.add("§cНедостаточно очков");
            else lore.add("§eНажмите, чтобы изучить");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        inv.setItem(49, createButton(Material.BARRIER, "§cНазад в главное меню", List.of()));
        player.openInventory(inv);
    }

    private ItemStack createButton(Material mat, String name, List<String> lore) {
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private void fillEmptyWithGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            player.performCommand("country");
            return;
        }

        if (slot < 0 || slot >= 45) return;

        Map<String, String[]> techs = scienceManager.getTechnologies();
        List<String> ids = new ArrayList<>(techs.keySet());
        if (slot < ids.size()) {
            String id = ids.get(slot);
            if (scienceManager.research(player.getUniqueId(), id)) {
                player.sendMessage("§aТехнология изучена!");
            } else {
                player.sendMessage("§cНе удалось изучить технологию.");
            }
            open(player);
        }
    }
}
