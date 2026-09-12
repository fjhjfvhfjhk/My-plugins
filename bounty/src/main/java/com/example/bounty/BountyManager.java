package com.example.bounty;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BountyManager {

    private final BountyPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, BountyEntry> bounties = new HashMap<>();

    public BountyManager(BountyPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        bounties.clear();

        ConfigurationSection section = data.getConfigurationSection("bounties");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID target = UUID.fromString(key);
                    String setterStr = section.getString(key + ".setter", "");
                    UUID setter = UUID.fromString(setterStr);
                    double amount = section.getDouble(key + ".amount", 0);
                    if (amount > 0) {
                        bounties.put(target, new BountyEntry(amount, setter));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        data.set("bounties", null);
        for (Map.Entry<UUID, BountyEntry> entry : bounties.entrySet()) {
            String key = entry.getKey().toString();
            BountyEntry bounty = entry.getValue();
            data.set("bounties." + key + ".amount", bounty.amount);
            data.set("bounties." + key + ".setter", bounty.setter.toString());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    public void setBounty(UUID target, UUID setter, double amount) {
        bounties.put(target, new BountyEntry(amount, setter));
        save();
        if (plugin.getPanelExporter() != null) plugin.getPanelExporter().markDirty();
    }

    public void removeBounty(UUID target) {
        bounties.remove(target);
        save();
        if (plugin.getPanelExporter() != null) plugin.getPanelExporter().markDirty();
    }

    public BountyEntry getBounty(UUID target) {
        return bounties.get(target);
    }

    public Map<UUID, BountyEntry> getAllBounties() {
        return new LinkedHashMap<>(bounties);
    }

    public static class BountyEntry {
        public final double amount;
        public final UUID setter;

        public BountyEntry(double amount, UUID setter) {
            this.amount = amount;
            this.setter = setter;
        }
    }
}
