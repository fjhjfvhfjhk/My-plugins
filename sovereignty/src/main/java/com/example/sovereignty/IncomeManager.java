package com.example.sovereignty;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Начисляет пассивный доход странам: базовый за чанки, бонус за торговые пакты.
 */
public class IncomeManager {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final ChunkUpgradeManager chunkUpgradeManager;

    public IncomeManager(SovereigntyPlugin plugin, CountryManager countryManager,
                         ChunkUpgradeManager chunkUpgradeManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
        this.chunkUpgradeManager = chunkUpgradeManager;
    }

    public void start() {
        long interval = plugin.getConfig().getLong("passive-income-interval-ticks", 72000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                distributeIncome();
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void distributeIncome() {
        double basePerChunk = plugin.getConfig().getDouble("income.base-per-chunk", 5.0);

        for (String country : countryManager.getAllCountries()) {
            int totalChunks = countryManager.getClaimCount(country);
            double income = totalChunks * basePerChunk;

            // Доход от ферм и торговых чанков
            income += chunkUpgradeManager.calculatePassiveIncome(country);

            // Бонус за торговые пакты
            double tradePactBonus = plugin.getConfig().getDouble("income.trade-pact-bonus-percent", 10.0);
            int tradePacts = countTradePacts(country);
            if (tradePacts > 0) {
                income *= (1.0 + (tradePactBonus / 100.0) * tradePacts);
            }

            if (income > 0) {
                countryManager.depositToBank(country, income);
                plugin.getLogger().info("Пассивный доход страны " + country + ": " + income);
            }
        }
    }

    private int countTradePacts(String country) {
        int count = 0;
        for (String other : countryManager.getAllCountries()) {
            if (!other.equals(country) && plugin.getPactManager().hasPact(country, other, "trade")) {
                count++;
            }
        }
        return count;
    }
}
