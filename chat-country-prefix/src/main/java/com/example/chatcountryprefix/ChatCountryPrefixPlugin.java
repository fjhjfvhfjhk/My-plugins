package com.example.chatcountryprefix;

import org.bukkit.plugin.java.JavaPlugin;

public final class ChatCountryPrefixPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getLogger().info("ChatCountryPrefix включён.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChatCountryPrefix выключен.");
    }
}
