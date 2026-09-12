package com.example.marketgui;

import org.bukkit.plugin.java.JavaPlugin;

public final class MarketPlugin extends JavaPlugin {

    private ShopManager shopManager;
    private ShopGUI shopGUI;
    private EconomyManager economyManager;
    private PanelExporter panelExporter;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.economyManager = new EconomyManager(this);
        this.shopManager = new ShopManager(this);
        this.shopGUI = new ShopGUI(this, shopManager, economyManager);

        ShopCommand shopCommand = new ShopCommand(this, shopManager, shopGUI);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        getServer().getPluginManager().registerEvents(shopGUI, this);

        this.panelExporter = new PanelExporter(this);

        getLogger().info("MarketGUI включён. Категорий: " + shopManager.getCategories().size() +
                ". Валюта: " + (economyManager.isEnabled() ? "включена" : "отключена"));
    }

    @Override
    public void onDisable() {
        if (panelExporter != null) panelExporter.shutdown();
        if (shopManager != null) {
            shopManager.save();
        }
        getLogger().info("MarketGUI выключен.");
    }

    public ShopManager getShopManager() { return shopManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public PanelExporter getPanelExporter() { return panelExporter; }
}
