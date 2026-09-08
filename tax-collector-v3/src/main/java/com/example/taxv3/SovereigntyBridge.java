package com.example.taxv3;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public class SovereigntyBridge {

    private final TaxPlugin plugin;
    private Plugin sovereignty;
    private Object countryManager;
    private Method getCountryNameMethod;
    private Method getClaimCountMethod;
    private Method removeRandomChunkMethod;
    private Method getBankBalanceMethod;
    private Method withdrawFromBankMethod;
    private Method depositToBankMethod;
    private Method getCountryDebtMethod;
    private Method addCountryDebtMethod;
    private Method reduceCountryDebtMethod;
    private Method getOwnerMethod;

    public SovereigntyBridge(TaxPlugin plugin) {
        this.plugin = plugin;
        this.sovereignty = Bukkit.getPluginManager().getPlugin("Sovereignty");
        if (sovereignty != null) {
            initReflection();
        }
    }

    private void initReflection() {
        try {
            Method getManager = sovereignty.getClass().getMethod("getCountryManager");
            this.countryManager = getManager.invoke(sovereignty);

            Class<?> managerClass = countryManager.getClass();
            this.getCountryNameMethod = managerClass.getMethod("getCountryName", UUID.class);
            this.getClaimCountMethod = managerClass.getMethod("getClaimCount", String.class);
            this.removeRandomChunkMethod = managerClass.getMethod("removeRandomChunk", String.class);
            this.getBankBalanceMethod = managerClass.getMethod("getBankBalance", String.class);
            this.withdrawFromBankMethod = managerClass.getMethod("withdrawFromBank", String.class, double.class);
            this.depositToBankMethod = managerClass.getMethod("depositToBank", String.class, double.class);
            this.getCountryDebtMethod = managerClass.getMethod("getCountryDebt", String.class);
            this.addCountryDebtMethod = managerClass.getMethod("addCountryDebt", String.class, double.class);
            this.reduceCountryDebtMethod = managerClass.getMethod("reduceCountryDebt", String.class, double.class);
            this.getOwnerMethod = managerClass.getMethod("getOwner", String.class);
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось подключиться к Sovereignty: " + e.getMessage());
            this.countryManager = null;
        }
    }

    public boolean isAvailable() {
        return countryManager != null;
    }

    public String getCountryName(UUID uuid) {
        if (!isAvailable()) return null;
        try { return (String) getCountryNameMethod.invoke(countryManager, uuid); }
        catch (Exception e) { return null; }
    }

    public int getClaimCount(String country) {
        if (!isAvailable() || country == null) return 0;
        try { return (Integer) getClaimCountMethod.invoke(countryManager, country); }
        catch (Exception e) { return 0; }
    }

    public boolean removeRandomChunk(String country) {
        if (!isAvailable() || country == null) return false;
        try { return (Boolean) removeRandomChunkMethod.invoke(countryManager, country); }
        catch (Exception e) { return false; }
    }

    public double getBankBalance(String country) {
        if (!isAvailable() || country == null) return 0.0;
        try { return (Double) getBankBalanceMethod.invoke(countryManager, country); }
        catch (Exception e) { return 0.0; }
    }

    public boolean withdrawFromBank(String country, double amount) {
        if (!isAvailable() || country == null) return false;
        try { return (Boolean) withdrawFromBankMethod.invoke(countryManager, country, amount); }
        catch (Exception e) { return false; }
    }

    public boolean depositToBank(String country, double amount) {
        if (!isAvailable() || country == null) return false;
        try { return (Boolean) depositToBankMethod.invoke(countryManager, country, amount); }
        catch (Exception e) { return false; }
    }

    public double getCountryDebt(String country) {
        if (!isAvailable() || country == null) return 0.0;
        try { return (Double) getCountryDebtMethod.invoke(countryManager, country); }
        catch (Exception e) { return 0.0; }
    }

    public void addCountryDebt(String country, double amount) {
        if (!isAvailable() || country == null) return;
        try { addCountryDebtMethod.invoke(countryManager, country, amount); }
        catch (Exception ignored) {}
    }

    public boolean reduceCountryDebt(String country, double amount) {
        if (!isAvailable() || country == null) return false;
        try { return (Boolean) reduceCountryDebtMethod.invoke(countryManager, country, amount); }
        catch (Exception e) { return false; }
    }

    public UUID getOwner(String country) {
        if (!isAvailable() || country == null) return null;
        try { return (UUID) getOwnerMethod.invoke(countryManager, country); }
        catch (Exception e) { return null; }
    }
}
