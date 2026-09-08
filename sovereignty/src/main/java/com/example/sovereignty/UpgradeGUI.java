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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpgradeGUI implements Listener {

    private static final String TITLE = "§6Прокачка";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final EnergyManager energyManager;
    private final EconomyManager economyManager;

    public UpgradeGUI(SovereigntyPlugin plugin, CountryManager countryManager,
                      EnergyManager energyManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
        this.energyManager = energyManager;
        this.economyManager = economyManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillEmptyWithGlass(inv);

        inv.setItem(11, maxEnergyButton(player));
        inv.setItem(13, regenButton(player));
        inv.setItem(15, chunkLimitButton(player));
        inv.setItem(21, farmUpgradeButton(player));
        inv.setItem(23, boostButton(player));
        inv.setItem(31, scienceButton(player));
        inv.setItem(33, instantEnergyButton(player));

        inv.setItem(49, createButton(Material.BARRIER, "§cНазад в главное меню", List.of()));

        player.openInventory(inv);
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

    private ItemStack createButton(Material mat, String name, List<String> lore) {
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack maxEnergyButton(Player player) {
        UUID uuid = player.getUniqueId();
        int level = energyManager.getMaxLevel(uuid);
        double current = energyManager.getMaxEnergy(uuid);
        boolean hasNext = energyManager.hasNextMaxUpgrade(uuid);
        double cost = energyManager.getNextMaxUpgradeCost(uuid);
        double balance = economyManager.getBalance(player);

        ItemStack icon = new ItemStack(hasNext ? Material.NETHER_STAR : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§aМаксимум энергии: " + String.format("%.1f", current));
        List<String> lore = new ArrayList<>();
        lore.add("§7Уровень: " + level + "/19");
        if (hasNext) {
            lore.add("§7Следующий: " + String.format("%.1f", energyManager.getMaxEnergy(level + 1)));
            lore.add("§7Стоимость: " + economyManager.format(cost));
            lore.add("§7Баланс: " + economyManager.format(balance));
            lore.add(balance >= cost ? "§eНажмите" : "§cНедостаточно денег");
        } else {
            lore.add("§7Максимум");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack regenButton(Player player) {
        UUID uuid = player.getUniqueId();
        int level = energyManager.getRegenLevel(uuid);
        double current = energyManager.getRegenPerHour(level);
        boolean hasNext = energyManager.hasNextRegenUpgrade(uuid);
        double cost = energyManager.getNextRegenUpgradeCost(uuid);
        double balance = economyManager.getBalance(player);

        ItemStack icon = new ItemStack(hasNext ? Material.CLOCK : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§aРегенерация: " + String.format("%.1f", current) + "/час");
        List<String> lore = new ArrayList<>();
        lore.add("§7Уровень: " + level + "/19");
        if (hasNext) {
            lore.add("§7Следующая: " + String.format("%.1f", energyManager.getRegenPerHour(level + 1)) + "/час");
            lore.add("§7Стоимость: " + economyManager.format(cost));
            lore.add("§7Баланс: " + economyManager.format(balance));
            lore.add(balance >= cost ? "§eНажмите" : "§cНедостаточно денег");
        } else {
            lore.add("§7Максимум");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack chunkLimitButton(Player player) {
        UUID uuid = player.getUniqueId();
        int level = energyManager.getChunkLimitLevel(uuid);
        int currentMax = countryManager.getMaxClaims(uuid);
        boolean hasNext = energyManager.hasNextChunkLimitUpgrade(uuid);
        double cost = energyManager.getNextChunkLimitUpgradeCost(uuid);
        double balance = economyManager.getBalance(player);

        ItemStack icon = new ItemStack(hasNext ? Material.GRASS_BLOCK : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§aЛимит чанков: " + currentMax);
        List<String> lore = new ArrayList<>();
        lore.add("§7Уровень: " + level);
        if (hasNext) {
            lore.add("§7Следующий лимит: " + (currentMax + plugin.getConfig().getInt("chunk-limit-upgrade-step", 10)));
            lore.add("§7Стоимость: " + economyManager.format(cost));
            lore.add("§7Баланс: " + economyManager.format(balance));
            lore.add(balance >= cost ? "§eНажмите" : "§cНедостаточно денег");
        } else {
            lore.add("§7Максимум");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack farmUpgradeButton(Player player) {
        UUID uuid = player.getUniqueId();
        int level = energyManager.getFarmUpgradeLevel(uuid);
        boolean hasNext = energyManager.hasNextFarmUpgrade(uuid);
        double cost = energyManager.getNextFarmUpgradeCost(uuid);
        double balance = economyManager.getBalance(player);

        double baseIncome = plugin.getConfig().getDouble("chunk-upgrades.farm.income", 150.0);
        double step = plugin.getConfig().getDouble("chunk-upgrades.farm.income-step", 100.0);
        double currentPerChunk = baseIncome + level * step;

        ItemStack icon = new ItemStack(hasNext ? Material.WHEAT : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§aДоход ферм: " + String.format("%.0f", currentPerChunk) + "/чанк");
        List<String> lore = new ArrayList<>();
        lore.add("§7Уровень: " + level);
        if (hasNext) {
            lore.add("§7Следующий доход: " + String.format("%.0f", currentPerChunk + step) + "/чанк");
            lore.add("§7Стоимость: " + economyManager.format(cost));
            lore.add("§7Баланс: " + economyManager.format(balance));
            lore.add(balance >= cost ? "§eНажмите" : "§cНедостаточно денег");
        } else {
            lore.add("§7Максимум");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack boostButton(Player player) {
        UUID uuid = player.getUniqueId();
        boolean active = energyManager.isBoostActive(uuid);
        long remaining = energyManager.getBoostRemainingMillis(uuid);
        double cost = plugin.getConfig().getDouble("energy.boost-cost", 5000.0);
        double balance = economyManager.getBalance(player);

        ItemStack icon = new ItemStack(active ? Material.REDSTONE_TORCH : Material.TORCH);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§aБуст регенерации");
        List<String> lore = new ArrayList<>();
        if (active) {
            long minutes = remaining / 60000;
            lore.add("§7Активен ещё: " + minutes + " мин");
        } else {
            lore.add("§7Купить буст на " + plugin.getConfig().getInt("energy.boost-duration-hours", 1) + " час");
            lore.add("§7Стоимость: " + economyManager.format(cost));
            lore.add("§7Баланс: " + economyManager.format(balance));
            lore.add(balance >= cost ? "§eНажмите" : "§cНедостаточно денег");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack scienceButton(Player player) {
        ScienceManager sm = plugin.getScienceManager();
        UUID uuid = player.getUniqueId();
        int level = sm.getScienceLevel(uuid);
        double points = sm.getSciencePoints(uuid);
        boolean hasNext = sm.hasNextScienceUpgrade(uuid);
        double cost = sm.getNextScienceUpgradeCost(uuid);
        double balance = economyManager.getBalance(player);

        ItemStack icon = new ItemStack(hasNext ? Material.KNOWLEDGE_BOOK : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§aНаука: уровень " + level);
        List<String> lore = new ArrayList<>();
        lore.add("§7Очки науки: " + String.format("%.1f", points));
        lore.add("§7Базовая генерация: " + String.format("%.1f", 0.5 + level * 0.5) + "/час");
        if (hasNext) {
            lore.add("§7Следующий уровень: " + (level + 1));
            lore.add("§7Стоимость: " + economyManager.format(cost));
            lore.add("§7Баланс: " + economyManager.format(balance));
            lore.add(balance >= cost ? "§eНажмите" : "§cНедостаточно денег");
        } else {
            lore.add("§7Максимум");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack instantEnergyButton(Player player) {
        UUID uuid = player.getUniqueId();
        double regenPerHour = energyManager.getCurrentRegenPerHour(uuid);
        double cost = regenPerHour * plugin.getConfig().getDouble("energy.buy-energy-cost", 1000.0);
        double balance = economyManager.getBalance(player);
        double currentEnergy = energyManager.getEnergy(uuid);
        double maxEnergy = energyManager.getMaxEnergy(uuid);
        boolean atMax = currentEnergy >= maxEnergy;

        ItemStack icon = new ItemStack(atMax ? Material.GRAY_DYE : Material.SUNFLOWER);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§eМгновенная энергия");
        List<String> lore = new ArrayList<>();
        lore.add("§7Получите " + String.format("%.1f", regenPerHour) + " энергии");
        lore.add("§7Стоимость: " + economyManager.format(cost));
        lore.add("§7Баланс: " + economyManager.format(balance));
        if (atMax) lore.add("§cВы уже на максимуме энергии");
        else if (balance >= cost) lore.add("§eНажмите");
        else lore.add("§cНедостаточно денег");
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();

        boolean success = false;
        switch (slot) {
            case 11 -> success = energyManager.upgradeMax(player.getUniqueId());
            case 13 -> success = energyManager.upgradeRegen(player.getUniqueId());
            case 15 -> success = energyManager.upgradeChunkLimit(player.getUniqueId());
            case 21 -> success = energyManager.upgradeFarm(player.getUniqueId());
            case 23 -> success = energyManager.buyBoost(player.getUniqueId());
            case 31 -> success = plugin.getScienceManager().upgradeScience(player);
            case 33 -> {
                UUID uuid = player.getUniqueId();
                double regenPerHour = energyManager.getCurrentRegenPerHour(uuid);
                double cost = regenPerHour * plugin.getConfig().getDouble("energy.buy-energy-cost", 1000.0);
                double balance = economyManager.getBalance(player);
                double currentEnergy = energyManager.getEnergy(uuid);
                double maxEnergy = energyManager.getMaxEnergy(uuid);
                if (currentEnergy >= maxEnergy) player.sendMessage("§cВы уже на максимуме энергии.");
                else if (balance < cost) player.sendMessage("§cНедостаточно денег. Нужно: " + economyManager.format(cost));
                else {
                    economyManager.withdraw(player, cost);
                    energyManager.addEnergy(uuid, regenPerHour);
                    player.sendMessage("§aКуплено " + String.format("%.1f", regenPerHour) + " энергии за " + economyManager.format(cost) + ".");
                    success = true;
                }
            }
            case 49 -> {
                player.closeInventory();
                player.performCommand("country");
                return;
            }
        }
        if (success) player.sendMessage("§aУлучшено!");
        else if (slot != 33 && slot != 49) player.sendMessage("§cНе удалось улучшить.");
        open(player);
    }
}
