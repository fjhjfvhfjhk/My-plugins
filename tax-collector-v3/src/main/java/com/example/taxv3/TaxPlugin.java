package com.example.taxv3;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TaxCollectorV3 — налоговая система, работающая через SovereigntyBridge.
 * Не имеет жёсткой зависимости от Sovereignty.
 */
public final class TaxPlugin extends JavaPlugin {

    private EconomyManager economyManager;
    private DataManager dataManager;
    private TaxManager taxManager;
    private TaxGUI taxGUI;
    private SovereigntyBridge sovereigntyBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("data.yml", false);

        this.economyManager = new EconomyManager(this);
        this.dataManager = new DataManager(this);
        this.sovereigntyBridge = new SovereigntyBridge(this);

        if (sovereigntyBridge.isAvailable()) {
            getLogger().info("Sovereignty найден. Налоги будут списываться из казны страны.");
        } else {
            getLogger().warning("Sovereignty не найден. Налоги будут списываться из личного баланса.");
        }

        this.taxGUI = new TaxGUI(this, economyManager, dataManager, sovereigntyBridge);
        this.taxManager = new TaxManager(this, economyManager, dataManager, sovereigntyBridge);

        getCommand("tax").setExecutor(new TaxCommand(this, taxManager, dataManager, taxGUI));
        getServer().getPluginManager().registerEvents(taxGUI, this);

        taxManager.start();
        getLogger().info("TaxCollectorV3 включён.");
    }

    @Override
    public void onDisable() {
        if (taxManager != null) taxManager.stop();
        if (dataManager != null) dataManager.save();
        getLogger().info("TaxCollectorV3 выключен.");
    }

    public TaxManager getTaxManager() { return taxManager; }
    public DataManager getDataManager() { return dataManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public SovereigntyBridge getSovereigntyBridge() { return sovereigntyBridge; }
}
