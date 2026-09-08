package com.example.taxv3;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TaxManager {

    private final TaxPlugin plugin;
    private final EconomyManager economyManager;
    private final DataManager dataManager;
    private final SovereigntyBridge sovereigntyBridge;
    private BukkitTask task;
    private final Map<World, Integer> lastTaxedDay = new HashMap<>();

    public TaxManager(TaxPlugin plugin, EconomyManager economyManager,
                      DataManager dataManager, SovereigntyBridge sovereigntyBridge) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.dataManager = dataManager;
        this.sovereigntyBridge = sovereigntyBridge;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAllWorlds, 200L, 400L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void checkAllWorlds() {
        for (World world : Bukkit.getWorlds()) {
            checkWorld(world);
        }
    }

    private void checkWorld(World world) {
        long fullTime = world.getFullTime();
        int currentDay = (int) (fullTime / 24000L);
        int interval = plugin.getConfig().getInt("tax-interval-days", 5);
        if (currentDay % interval != 0) return;

        int lastDay = lastTaxedDay.getOrDefault(world, -1);
        if (lastDay == currentDay) return;
        lastTaxedDay.put(world, currentDay);
        collectTaxes(world);
    }

    public double calculateTax(int chunks) {
        double base = plugin.getConfig().getDouble("base-tax", 100.0);
        double perChunk = plugin.getConfig().getDouble("per-chunk-tax", 100.0);
        double per5Bonus = plugin.getConfig().getDouble("per-5-chunks-bonus", 200.0);
        int bonusGroups = chunks / 5;
        return base + (chunks * perChunk) + (bonusGroups * per5Bonus);
    }

    private void collectTaxes(World world) {
        String taxCollectedMsg = plugin.getConfig().getString("messages.tax-collected",
                "§aНалог списан: §f%amount%§a. Баланс: §f%balance%§a.");
        String debtAddedMsg = plugin.getConfig().getString("messages.debt-added",
                "§cНедостаточно средств! Списано: §f%paid%§c. Долг страны: §f%debt%§c.");
        String chunkRemovedMsg = plugin.getConfig().getString("messages.chunk-removed",
                "§cПросрочено 3 налога! Один чанк изъят.");

        int missedBeforeUnclaim = plugin.getConfig().getInt("missed-before-unclaim", 3);
        double subsidyPercent = plugin.getConfig().getDouble("tax-subsidy-percent", 10.0);

        for (Player player : world.getPlayers()) {
            UUID uuid = player.getUniqueId();
            String country = sovereigntyBridge.getCountryName(uuid);
            if (country == null) continue;

            int chunks = sovereigntyBridge.getClaimCount(country);
            double tax = calculateTax(chunks);
            double treasury = sovereigntyBridge.getBankBalance(country);

            double fromTreasury = Math.min(treasury, tax);
            if (fromTreasury > 0) {
                sovereigntyBridge.withdrawFromBank(country, fromTreasury);
            }

            double remaining = tax - fromTreasury;

            if (remaining > 0) {
                UUID ownerUuid = sovereigntyBridge.getOwner(country);
                if (ownerUuid != null) {
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
                    if (economyManager.isEnabled() && economyManager.has(owner, remaining)) {
                        economyManager.withdraw(owner, remaining);
                        remaining = 0;
                    }
                }
            }

            if (remaining > 0) {
                sovereigntyBridge.addCountryDebt(country, remaining);
                player.sendMessage(debtAddedMsg
                        .replace("%paid%", economyManager.format(tax - remaining))
                        .replace("%debt%", economyManager.format(sovereigntyBridge.getCountryDebt(country))));

                int missed = dataManager.getMissed(uuid) + 1;
                dataManager.incrementMissed(uuid);
                if (missed >= missedBeforeUnclaim) {
                    boolean removed = sovereigntyBridge.removeRandomChunk(country);
                    if (removed) {
                        player.sendMessage(chunkRemovedMsg);
                    }
                    dataManager.resetMissed(uuid);
                }
            } else {
                dataManager.resetMissed(uuid);
                double subsidy = tax * (subsidyPercent / 100.0);
                if (subsidy > 0) {
                    sovereigntyBridge.depositToBank(country, subsidy);
                }

                // Правильная замена баланса
                double newBalance = sovereigntyBridge.getBankBalance(country);
                player.sendMessage(taxCollectedMsg
                        .replace("%amount%", economyManager.format(tax))
                        .replace("%balance%", economyManager.format(newBalance)));
            }
        }

        plugin.getLogger().info("Налог собран в мире " + world.getName());
    }

    public void collectNow(World world) {
        collectTaxes(world);
    }
}
