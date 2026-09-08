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

public class TopGUI implements Listener {

    private static final String TITLE = "§6Топ стран";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final EnergyManager energyManager;

    private String currentSort = "claims";

    public TopGUI(SovereigntyPlugin plugin, CountryManager countryManager, EnergyManager energyManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
        this.energyManager = energyManager;
    }

    public void open(Player player, String sort) {
        this.currentSort = sort;
        open(player);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillEmptyWithGlass(inv);

        inv.setItem(45, sortButton(Material.GRASS_BLOCK, "§aПо территории", "claims"));
        inv.setItem(46, sortButton(Material.GOLD_INGOT, "§aПо казне", "bank"));
        inv.setItem(47, sortButton(Material.DIAMOND, "§aПо силе (энергии)", "energy"));
        inv.setItem(49, createButton(Material.BARRIER, "§cНазад в главное меню", List.of()));

        List<CountryRank> ranks = new ArrayList<>();
        for (String name : countryManager.getAllCountries()) {
            int claims = countryManager.getClaimCount(name);
            double bank = countryManager.getBankBalance(name);
            UUID owner = countryManager.getOwner(name);
            double energy = owner != null ? energyManager.getEnergy(owner) : 0;
            ranks.add(new CountryRank(name, claims, bank, energy));
        }

        switch (currentSort) {
            case "bank" -> ranks.sort((a, b) -> Double.compare(b.bank, a.bank));
            case "energy" -> ranks.sort((a, b) -> Double.compare(b.energy, a.energy));
            default -> ranks.sort((a, b) -> Integer.compare(b.claims, a.claims));
        }

        int max = Math.min(ranks.size(), 45);
        for (int i = 0; i < max; i++) {
            CountryRank rank = ranks.get(i);
            ItemStack icon = new ItemStack(i == 0 ? Material.NETHER_STAR : Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§e#" + (i + 1) + " §f" + rank.name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Территория: §f" + rank.claims + " чанков");
            lore.add("§7Казна: §f" + plugin.getEconomyManager().format(rank.bank));
            lore.add("§7Энергия: §f" + String.format("%.1f", rank.energy));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        player.openInventory(inv);
    }

    private ItemStack sortButton(Material material, String name, String sort) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(sort.equals(currentSort) ? "§a§lАктивно" : "§7Нажмите"));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack icon = new ItemStack(material);
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
        if (slot == 45) open(player, "claims");
        else if (slot == 46) open(player, "bank");
        else if (slot == 47) open(player, "energy");
        else if (slot == 49) {
            player.closeInventory();
            player.performCommand("country");
        }
    }

    private static class CountryRank {
        String name;
        int claims;
        double bank;
        double energy;

        CountryRank(String name, int claims, double bank, double energy) {
            this.name = name;
            this.claims = claims;
            this.bank = bank;
            this.energy = energy;
        }
    }
}
