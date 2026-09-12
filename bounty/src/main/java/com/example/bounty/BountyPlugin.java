package com.example.bounty;

import org.bukkit.plugin.java.JavaPlugin;

public final class BountyPlugin extends JavaPlugin {

    private BountyManager bountyManager;
    private EconomyManager economyManager;
    private PanelExporter panelExporter;

    @Override
    public void onEnable() {
        saveResource("data.yml", false);

        this.economyManager = new EconomyManager(this);
        this.bountyManager = new BountyManager(this);

        BountyGUI bountyGUI = new BountyGUI(this, bountyManager);
        BountyCommand bountyCommand = new BountyCommand(this, bountyManager, bountyGUI, economyManager);

        getCommand("bounty").setExecutor(bountyCommand);
        getCommand("bounty").setTabCompleter(bountyCommand);

        getServer().getPluginManager().registerEvents(new BountyListener(this, bountyManager, economyManager), this);
        getServer().getPluginManager().registerEvents(bountyGUI, this);

        this.panelExporter = new PanelExporter(this);

        getLogger().info("Bounty включён.");
    }

    @Override
    public void onDisable() {
        if (panelExporter != null) panelExporter.shutdown();
        if (bountyManager != null) bountyManager.save();
        getLogger().info("Bounty выключен.");
    }

    public BountyManager getBountyManager() { return bountyManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public PanelExporter getPanelExporter() { return panelExporter; }
}
