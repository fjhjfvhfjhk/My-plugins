package com.example.roll;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UpgradeManager v3.0 — универсальные апгрейды + кастомные рецепты из config.yml.
 *
 * Формат custom-recipes в config.yml:
 *
 * upgrade:
 *   custom-recipes:
 *     GOLDEN_APPLE: "ENCHANTED_GOLDEN_APPLE:0.15"
 *     STICK: "BLAZE_ROD:0.30"
 *
 * Значение = "ВЫХОД:ШАНС". chance от 0.05 до 0.60.
 * valueIn берётся из стандартного реестра (или 100 по умолчанию).
 * valueOut = valueIn * RTP / chance.
 */
public class UpgradeManager {

    public static final double RTP = 0.95;

    public static final double[] CHANCE_STEPS = {
            0.05, 0.10, 0.15, 0.25, 0.40, 0.50, 0.65, 0.75, 0.90
    };

    private static final Map<Material, Double> VALUES = new LinkedHashMap<>();
    private static final Map<Material, Material> UPGRADES = new LinkedHashMap<>();

    /** Кастомные рецепты (переопределяют встроенные). */
    private final Map<Material, UpgradeRecipe> customRecipes = new LinkedHashMap<>();

    public UpgradeManager(RollPlugin plugin) {
        initDefaults();
        loadCustomRecipes(plugin);
    }

    private void initDefaults() {
        // --- Ресурсы ---
        VALUES.put(Material.COAL, 20.0);
        VALUES.put(Material.REDSTONE, 30.0);
        VALUES.put(Material.IRON_INGOT, 80.0);
        VALUES.put(Material.LAPIS_LAZULI, 100.0);
        VALUES.put(Material.GOLD_INGOT, 150.0);
        VALUES.put(Material.EMERALD, 250.0);
        VALUES.put(Material.DIAMOND, 500.0);
        VALUES.put(Material.NETHERITE_INGOT, 5000.0);
        VALUES.put(Material.GOLDEN_APPLE, 200.0);
        VALUES.put(Material.ENCHANTED_GOLDEN_APPLE, 4000.0);
        VALUES.put(Material.BLAZE_ROD, 300.0);

        // --- Мечи ---
        VALUES.put(Material.WOODEN_SWORD, 30.0);
        VALUES.put(Material.STONE_SWORD, 100.0);
        VALUES.put(Material.IRON_SWORD, 250.0);
        VALUES.put(Material.DIAMOND_SWORD, 1500.0);
        VALUES.put(Material.NETHERITE_SWORD, 12000.0);

        // --- Шлемы ---
        VALUES.put(Material.LEATHER_HELMET, 50.0);
        VALUES.put(Material.CHAINMAIL_HELMET, 120.0);
        VALUES.put(Material.IRON_HELMET, 200.0);
        VALUES.put(Material.DIAMOND_HELMET, 1200.0);
        VALUES.put(Material.NETHERITE_HELMET, 10000.0);

        // --- Нагрудники ---
        VALUES.put(Material.LEATHER_CHESTPLATE, 80.0);
        VALUES.put(Material.CHAINMAIL_CHESTPLATE, 200.0);
        VALUES.put(Material.IRON_CHESTPLATE, 320.0);
        VALUES.put(Material.DIAMOND_CHESTPLATE, 1900.0);
        VALUES.put(Material.NETHERITE_CHESTPLATE, 15000.0);

        // --- Штаны ---
        VALUES.put(Material.LEATHER_LEGGINGS, 70.0);
        VALUES.put(Material.CHAINMAIL_LEGGINGS, 180.0);
        VALUES.put(Material.IRON_LEGGINGS, 280.0);
        VALUES.put(Material.DIAMOND_LEGGINGS, 1700.0);
        VALUES.put(Material.NETHERITE_LEGGINGS, 13000.0);

        // --- Ботинки ---
        VALUES.put(Material.LEATHER_BOOTS, 50.0);
        VALUES.put(Material.CHAINMAIL_BOOTS, 120.0);
        VALUES.put(Material.IRON_BOOTS, 200.0);
        VALUES.put(Material.DIAMOND_BOOTS, 1200.0);
        VALUES.put(Material.NETHERITE_BOOTS, 10000.0);

        // --- Кирки ---
        VALUES.put(Material.WOODEN_PICKAXE, 30.0);
        VALUES.put(Material.STONE_PICKAXE, 100.0);
        VALUES.put(Material.IRON_PICKAXE, 250.0);
        VALUES.put(Material.DIAMOND_PICKAXE, 1500.0);
        VALUES.put(Material.NETHERITE_PICKAXE, 12000.0);

        // --- Топоры ---
        VALUES.put(Material.WOODEN_AXE, 30.0);
        VALUES.put(Material.STONE_AXE, 100.0);
        VALUES.put(Material.IRON_AXE, 250.0);
        VALUES.put(Material.DIAMOND_AXE, 1500.0);
        VALUES.put(Material.NETHERITE_AXE, 12000.0);

        // --- Лопаты ---
        VALUES.put(Material.WOODEN_SHOVEL, 20.0);
        VALUES.put(Material.STONE_SHOVEL, 80.0);
        VALUES.put(Material.IRON_SHOVEL, 200.0);
        VALUES.put(Material.DIAMOND_SHOVEL, 1200.0);
        VALUES.put(Material.NETHERITE_SHOVEL, 10000.0);

        // Цепочки
        linkChain(Material.COAL, Material.IRON_INGOT);
        linkChain(Material.REDSTONE, Material.LAPIS_LAZULI, Material.EMERALD, Material.DIAMOND, Material.NETHERITE_INGOT);
        linkChain(Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHERITE_INGOT);

        linkChain(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
        linkChain(Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET);
        linkChain(Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE);
        linkChain(Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS);
        linkChain(Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS);

        linkChain(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
        linkChain(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
        linkChain(Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL);

        // Яблоки
        UPGRADES.put(Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE);
    }

    private void linkChain(Material... chain) {
        for (int i = 0; i < chain.length - 1; i++) UPGRADES.put(chain[i], chain[i + 1]);
    }

    private void loadCustomRecipes(RollPlugin plugin) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("upgrade.custom-recipes");
        if (sec == null) return;
        int loaded = 0;
        for (String key : sec.getKeys(false)) {
            try {
                Material in = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (in == null) {
                    plugin.getLogger().warning("[Upgrade] Неизвестный материал входа: " + key);
                    continue;
                }
                String value = sec.getString(key, "");
                String[] parts = value.split(":");
                if (parts.length != 2) {
                    plugin.getLogger().warning("[Upgrade] Неверный формат рецепта для " + key + ": " + value);
                    continue;
                }
                Material out = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
                double chance = Double.parseDouble(parts[1].trim());
                if (out == null) {
                    plugin.getLogger().warning("[Upgrade] Неизвестный материал выхода: " + parts[0]);
                    continue;
                }
                chance = Math.max(0.05, Math.min(0.60, chance));

                double vIn = VALUES.getOrDefault(in, 100.0);
                double vOut = vIn * RTP / chance;

                customRecipes.put(in, new UpgradeRecipe(out, chance, vIn, vOut));
                loaded++;
            } catch (Exception e) {
                plugin.getLogger().warning("[Upgrade] Ошибка рецепта " + key + ": " + e.getMessage());
            }
        }
        if (loaded > 0) plugin.getLogger().info("[Upgrade] Загружено " + loaded + " кастомных рецептов.");
    }

    public Double getValue(Material mat) { return VALUES.get(mat); }
    public Material getNextTier(Material mat) { return UPGRADES.get(mat); }

    public UpgradeRecipe getRecipe(Material input) {
        UpgradeRecipe custom = customRecipes.get(input);
        if (custom != null) return custom;

        Material next = UPGRADES.get(input);
        if (next == null) return null;
        Double vIn = VALUES.get(input);
        Double vOut = VALUES.get(next);
        if (vIn == null || vOut == null) return null;
        double raw = RTP * vIn / vOut;
        double chance = roundToStep(raw, 0.05);
        chance = Math.max(0.05, Math.min(0.60, chance));
        return new UpgradeRecipe(next, chance, vIn, vOut);
    }

    private static double roundToStep(double v, double step) { return Math.round(v / step) * step; }

    public double calcMultiplier(double chance) {
        if (chance <= 0 || chance >= 1) return 1.0;
        return Math.round((RTP / chance) * 100.0) / 100.0;
    }

    public boolean rollChance(double chance) { return ThreadLocalRandom.current().nextDouble() < chance; }
    public boolean rollItemUpgrade(UpgradeRecipe recipe) { return ThreadLocalRandom.current().nextDouble() < recipe.chance; }

    public static class UpgradeRecipe {
        public final Material output;
        public final double chance;
        public final double valueIn;
        public final double valueOut;
        public UpgradeRecipe(Material output, double chance, double valueIn, double valueOut) {
            this.output = output; this.chance = chance; this.valueIn = valueIn; this.valueOut = valueOut;
        }
    }
}
