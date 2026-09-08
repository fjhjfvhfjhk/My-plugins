package com.example.sovereignty;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final SovereigntyPlugin plugin;
    private Economy economy;

    public EconomyManager(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return;
        this.economy = rsp.getProvider();
    }

    public boolean isEnabled() { return economy != null; }

    public double getBalance(Player player) {
        return economy == null ? 0.0 : economy.getBalance(player);
    }

    public double getBalance(OfflinePlayer player) {
        return economy == null ? 0.0 : economy.getBalance(player);
    }

    public boolean has(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (economy == null || !economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || !economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return economy == null ? String.valueOf(amount) : economy.format(amount);
    }
}
