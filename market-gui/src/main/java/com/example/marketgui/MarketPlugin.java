package com.example.marketgui;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MarketGUI — магазин с категориями, продавцами и смешанной ценой.
 */
public final class MarketPlugin extends JavaPlugin {

    private ShopManager shopManager;
    private ShopGUI shopGUI;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("listings.yml", false);

        this.economyManager = new EconomyManager(this);
        this.shopManager = new ShopManager(this);
        this.shopGUI = new ShopGUI(this, shopManager, economyManager);

        ShopCommand shopCommand = new ShopCommand(this, shopManager, shopGUI);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        getServer().getPluginManager().registerEvents(shopGUI, this);

        getLogger().info("MarketGUI включён. Категорий: " + shopManager.getCategories().size() +
                ". Валюта: " + (economyManager.isEnabled() ? "включена" : "отключена"));
    }

    @Override
    public void onDisable() {
        if (shopManager != null) {
            shopManager.save();
        }
        getLogger().info("MarketGUI выключен.");
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
