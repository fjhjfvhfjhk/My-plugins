package com.example.bounty;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bounty — система наёмников.
 */
public final class BountyPlugin extends JavaPlugin {

    private BountyManager bountyManager;
    private EconomyManager economyManager;

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

        getLogger().info("Bounty включён.");
    }

    @Override
    public void onDisable() {
        if (bountyManager != null) bountyManager.save();
        getLogger().info("Bounty выключен.");
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
