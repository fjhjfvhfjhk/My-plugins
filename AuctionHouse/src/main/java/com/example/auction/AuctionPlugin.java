package com.example.auction;

import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionPlugin extends JavaPlugin {

    private static AuctionPlugin instance;
    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.economyManager = new EconomyManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.auctionManager = new AuctionManager(this);

        getCommand("auction").setExecutor(new AuctionCommand(this));
        getCommand("auction").setTabCompleter(new AuctionCommand(this));

        getServer().getPluginManager().registerEvents(new AuctionListener(this), this);

        // Запуск таймера проверки аукционов каждые 20 тиков (1 сек)
        getServer().getScheduler().runTaskTimer(this, () -> auctionManager.checkExpiredAuctions(), 20L, 20L);

        getLogger().info("AuctionHouse включён.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getLogger().info("AuctionHouse выключен.");
    }

    public static AuctionPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
}
