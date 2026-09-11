package com.example.auction;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class ItemSerializer {
    public static String serialize(ItemStack item) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item);
        return yaml.saveToString();
    }

    public static ItemStack deserialize(String data) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(data);
            return yaml.getItemStack("item");
        } catch (Exception e) {
            return null;
        }
    }
}
