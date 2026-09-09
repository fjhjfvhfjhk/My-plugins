package com.example.marketgui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ShopManager {

    private final MarketPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, String> materialNames = new HashMap<>();
    private final Map<String, String> itemNames = new HashMap<>();
    private final Map<UUID, Map<Material, Integer>> pendingPayments = new HashMap<>();

    private static final Map<String, String> BUILT_IN_MATERIAL_NAMES = new HashMap<>();
    private static final Map<String, String> BUILT_IN_ITEM_NAMES = new HashMap<>();

    static {
        // Встроенные названия (краткий набор)
        BUILT_IN_MATERIAL_NAMES.put("DIAMOND", "Алмаз");
        BUILT_IN_MATERIAL_NAMES.put("EMERALD", "Изумруд");
        BUILT_IN_MATERIAL_NAMES.put("GOLD_INGOT", "Золотой слиток");
        BUILT_IN_MATERIAL_NAMES.put("IRON_INGOT", "Железный слиток");
        BUILT_IN_MATERIAL_NAMES.put("NETHERITE_INGOT", "Незеритовый слиток");
        BUILT_IN_MATERIAL_NAMES.put("STICK", "Палка");
        BUILT_IN_MATERIAL_NAMES.put("STRING", "Нить");
        BUILT_IN_MATERIAL_NAMES.put("LEATHER", "Кожа");
        BUILT_IN_MATERIAL_NAMES.put("BONE", "Кость");
        BUILT_IN_MATERIAL_NAMES.put("ENDER_PEARL", "Жемчуг Края");
        BUILT_IN_MATERIAL_NAMES.put("BLAZE_ROD", "Огненный стержень");
        BUILT_IN_MATERIAL_NAMES.put("SLIME_BALL", "Сгусток слизи");
        BUILT_IN_MATERIAL_NAMES.put("GUNPOWDER", "Порох");
        BUILT_IN_MATERIAL_NAMES.put("ROTTEN_FLESH", "Гнилая плоть");
        BUILT_IN_MATERIAL_NAMES.put("SPIDER_EYE", "Паучий глаз");
        BUILT_IN_MATERIAL_NAMES.put("FEATHER", "Перо");
        BUILT_IN_MATERIAL_NAMES.put("FLINT", "Кремень");
        BUILT_IN_MATERIAL_NAMES.put("CLAY_BALL", "Комок глины");
        BUILT_IN_MATERIAL_NAMES.put("BRICK", "Кирпич");
        BUILT_IN_MATERIAL_NAMES.put("PAPER", "Бумага");
        BUILT_IN_MATERIAL_NAMES.put("BOOK", "Книга");
        BUILT_IN_MATERIAL_NAMES.put("EXPERIENCE_BOTTLE", "Пузырёк опыта");
        BUILT_IN_MATERIAL_NAMES.put("HONEYCOMB", "Медовые соты");
        BUILT_IN_MATERIAL_NAMES.put("GOLDEN_APPLE", "Золотое яблоко");
        BUILT_IN_MATERIAL_NAMES.put("ENCHANTED_GOLDEN_APPLE", "Зачарованное золотое яблоко");
        BUILT_IN_MATERIAL_NAMES.put("TOTEM_OF_UNDYING", "Тотем бессмертия");
        BUILT_IN_MATERIAL_NAMES.put("ELYTRA", "Элитры");
        BUILT_IN_MATERIAL_NAMES.put("DRAGON_EGG", "Яйцо дракона");

        BUILT_IN_ITEM_NAMES.put("DIAMOND_SWORD", "Алмазный меч");
        BUILT_IN_ITEM_NAMES.put("IRON_SWORD", "Железный меч");
        BUILT_IN_ITEM_NAMES.put("NETHERITE_SWORD", "Незеритовый меч");
        BUILT_IN_ITEM_NAMES.put("BOW", "Лук");
        BUILT_IN_ITEM_NAMES.put("CROSSBOW", "Арбалет");
        BUILT_IN_ITEM_NAMES.put("TRIDENT", "Трезубец");
        BUILT_IN_ITEM_NAMES.put("DIAMOND_HELMET", "Алмазный шлем");
        BUILT_IN_ITEM_NAMES.put("DIAMOND_CHESTPLATE", "Алмазный нагрудник");
        BUILT_IN_ITEM_NAMES.put("DIAMOND_LEGGINGS", "Алмазные поножи");
        BUILT_IN_ITEM_NAMES.put("DIAMOND_BOOTS", "Алмазные ботинки");
        BUILT_IN_ITEM_NAMES.put("NETHERITE_HELMET", "Незеритовый шлем");
        BUILT_IN_ITEM_NAMES.put("NETHERITE_CHESTPLATE", "Незеритовый нагрудник");
        BUILT_IN_ITEM_NAMES.put("NETHERITE_LEGGINGS", "Незеритовые поножи");
        BUILT_IN_ITEM_NAMES.put("NETHERITE_BOOTS", "Незеритовые ботинки");
        BUILT_IN_ITEM_NAMES.put("DIAMOND_PICKAXE", "Алмазная кирка");
        BUILT_IN_ITEM_NAMES.put("IRON_PICKAXE", "Железная кирка");
        BUILT_IN_ITEM_NAMES.put("NETHERITE_PICKAXE", "Незеритовая кирка");
        BUILT_IN_ITEM_NAMES.put("DIAMOND_AXE", "Алмазный топор");
        BUILT_IN_ITEM_NAMES.put("IRON_AXE", "Железный топор");
        BUILT_IN_ITEM_NAMES.put("NETHERITE_AXE", "Незеритовый топор");
        BUILT_IN_ITEM_NAMES.put("BREAD", "Хлеб");
        BUILT_IN_ITEM_NAMES.put("COOKED_BEEF", "Жареная говядина");
        BUILT_IN_ITEM_NAMES.put("COOKED_PORKCHOP", "Жареная свинина");
        BUILT_IN_ITEM_NAMES.put("COOKED_CHICKEN", "Жареная курица");
        BUILT_IN_ITEM_NAMES.put("COOKED_COD", "Жареная треска");
        BUILT_IN_ITEM_NAMES.put("COOKED_SALMON", "Жареный лосось");
        BUILT_IN_ITEM_NAMES.put("CARROT", "Морковь");
        BUILT_IN_ITEM_NAMES.put("POTATO", "Картофель");
        BUILT_IN_ITEM_NAMES.put("BAKED_POTATO", "Печёный картофель");
        BUILT_IN_ITEM_NAMES.put("APPLE", "Яблоко");
        BUILT_IN_ITEM_NAMES.put("MELON_SLICE", "Ломтик арбуза");
        BUILT_IN_ITEM_NAMES.put("SWEET_BERRIES", "Сладкие ягоды");
        BUILT_IN_ITEM_NAMES.put("GLOW_BERRIES", "Светящиеся ягоды");
        BUILT_IN_ITEM_NAMES.put("HONEY_BOTTLE", "Бутылочка мёда");
        BUILT_IN_ITEM_NAMES.put("STONE", "Камень");
        BUILT_IN_ITEM_NAMES.put("COBBLESTONE", "Булыжник");
        BUILT_IN_ITEM_NAMES.put("DIRT", "Земля");
        BUILT_IN_ITEM_NAMES.put("GRASS_BLOCK", "Травяной блок");
        BUILT_IN_ITEM_NAMES.put("SAND", "Песок");
        BUILT_IN_ITEM_NAMES.put("GRAVEL", "Гравий");
        BUILT_IN_ITEM_NAMES.put("OAK_LOG", "Дубовое бревно");
        BUILT_IN_ITEM_NAMES.put("SPRUCE_LOG", "Еловое бревно");
        BUILT_IN_ITEM_NAMES.put("BIRCH_LOG", "Берёзовое бревно");
        BUILT_IN_ITEM_NAMES.put("OAK_PLANKS", "Дубовые доски");
        BUILT_IN_ITEM_NAMES.put("SPRUCE_PLANKS", "Еловые доски");
        BUILT_IN_ITEM_NAMES.put("BIRCH_PLANKS", "Берёзовые доски");
        BUILT_IN_ITEM_NAMES.put("GLASS", "Стекло");
        BUILT_IN_ITEM_NAMES.put("BRICKS", "Кирпичи");
        BUILT_IN_ITEM_NAMES.put("STONE_BRICKS", "Каменные кирпичи");
        BUILT_IN_ITEM_NAMES.put("OBSIDIAN", "Обсидиан");
        BUILT_IN_ITEM_NAMES.put("TNT", "Динамит");
        BUILT_IN_ITEM_NAMES.put("ANVIL", "Наковальня");
        BUILT_IN_ITEM_NAMES.put("ENCHANTING_TABLE", "Стол зачарования");
        BUILT_IN_ITEM_NAMES.put("CRAFTING_TABLE", "Верстак");
        BUILT_IN_ITEM_NAMES.put("FURNACE", "Печь");
        BUILT_IN_ITEM_NAMES.put("CHEST", "Сундук");
    }

    public ShopManager(MarketPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "listings.yml");
        loadMaterialNames();
        loadItemNames();
        loadCategories();
        loadListings();
    }

    private void loadMaterialNames() {
        materialNames.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("material-names");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                materialNames.put(key.toUpperCase(Locale.ROOT), section.getString(key));
            }
        }
    }

    private void loadItemNames() {
        itemNames.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("item-names");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                itemNames.put(key.toUpperCase(Locale.ROOT), section.getString(key));
            }
        }
    }

    public String getMaterialName(Material material) {
        String name = materialNames.get(material.name());
        if (name != null) return name;
        name = BUILT_IN_MATERIAL_NAMES.get(material.name());
        if (name != null) return name;
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public String getItemName(Material material) {
        String name = itemNames.get(material.name());
        if (name != null) return name;
        name = BUILT_IN_ITEM_NAMES.get(material.name());
        if (name != null) return name;
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void loadCategories() {
        categories.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("categories");
        if (section == null) return;

        for (String name : section.getKeys(false)) {
            String iconName = section.getString(name + ".icon", "STONE");
            Material icon = Material.matchMaterial(iconName);
            if (icon == null) icon = Material.STONE;

            List<Material> materials = new ArrayList<>();
            for (String matName : section.getStringList(name + ".materials")) {
                Material mat = Material.matchMaterial(matName);
                if (mat != null) materials.add(mat);
            }

            categories.put(name, new Category(name, icon, materials));
        }
    }

    private void loadListings() {
        listings.clear();
        pendingPayments.clear();
        if (!dataFile.exists()) {
            plugin.saveResource("listings.yml", false);
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection section = data.getConfigurationSection("listings");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String sellerUuidStr = section.getString(key + ".seller-uuid");
                    if (sellerUuidStr == null) continue;
                    UUID sellerUuid = UUID.fromString(sellerUuidStr);
                    String sellerName = section.getString(key + ".seller-name", "Неизвестный");
                    String categoryName = section.getString(key + ".category", "Редкости");
                    String priceMaterialName = section.getString(key + ".price-material");
                    Material priceMaterial = priceMaterialName != null ? Material.matchMaterial(priceMaterialName) : null;
                    int priceAmount = section.getInt(key + ".price-amount", 0);
                    double moneyPrice = section.getDouble(key + ".money-price", 0.0);
                    long createdAt = section.getLong(key + ".created-at", System.currentTimeMillis());
                    String targetPlayerStr = section.getString(key + ".target-player");
                    UUID targetPlayer = targetPlayerStr != null ? UUID.fromString(targetPlayerStr) : null;

                    ConfigurationSection itemSection = section.getConfigurationSection(key + ".item");
                    ItemStack item = null;
                    if (itemSection != null) {
                        Map<String, Object> map = itemSection.getValues(false);
                        item = ItemStack.deserialize(map);
                    }
                    if (item == null) continue;

                    Listing listing = new Listing(id, sellerUuid, sellerName, item,
                            priceMaterial, priceAmount, moneyPrice, categoryName, createdAt, targetPlayer);
                    listings.put(id, listing);
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection pendingSection = data.getConfigurationSection("pending-payments");
        if (pendingSection != null) {
            for (String uuidStr : pendingSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<Material, Integer> payments = new HashMap<>();
                    ConfigurationSection matSection = pendingSection.getConfigurationSection(uuidStr);
                    if (matSection != null) {
                        for (String matName : matSection.getKeys(false)) {
                            Material mat = Material.matchMaterial(matName);
                            if (mat != null) {
                                payments.put(mat, matSection.getInt(matName));
                            }
                        }
                    }
                    if (!payments.isEmpty()) {
                        pendingPayments.put(uuid, payments);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        data.set("listings", null);
        for (Map.Entry<UUID, Listing> entry : listings.entrySet()) {
            String key = entry.getKey().toString();
            Listing l = entry.getValue();
            data.set("listings." + key + ".seller-uuid", l.getSellerUuid().toString());
            data.set("listings." + key + ".seller-name", l.getSellerName());
            data.set("listings." + key + ".category", l.getCategoryName());
            if (l.getPriceMaterial() != null) {
                data.set("listings." + key + ".price-material", l.getPriceMaterial().name());
                data.set("listings." + key + ".price-amount", l.getPriceAmount());
            }
            if (l.getMoneyPrice() > 0) {
                data.set("listings." + key + ".money-price", l.getMoneyPrice());
            }
            data.set("listings." + key + ".created-at", l.getCreatedAt());
            data.set("listings." + key + ".item", l.getItemStack().serialize());
            if (l.getTargetPlayer() != null) {
                data.set("listings." + key + ".target-player", l.getTargetPlayer().toString());
            }
        }

        data.set("pending-payments", null);
        for (Map.Entry<UUID, Map<Material, Integer>> entry : pendingPayments.entrySet()) {
            String uuidKey = entry.getKey().toString();
            Map<Material, Integer> payments = entry.getValue();
            for (Map.Entry<Material, Integer> pay : payments.entrySet()) {
                data.set("pending-payments." + uuidKey + "." + pay.getKey().name(), pay.getValue());
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить listings.yml: " + e.getMessage());
        }
    }

    public Category addListing(Player seller, ItemStack item, Material priceMaterial,
                               int priceAmount, double moneyPrice, String categoryName, UUID targetPlayer) {
        UUID id = UUID.randomUUID();
        Listing listing = new Listing(id, seller.getUniqueId(), seller.getName(), item,
                priceMaterial, priceAmount, moneyPrice, categoryName, System.currentTimeMillis(), targetPlayer);
        listings.put(id, listing);
        save();
        return getCategory(categoryName);
    }

    public void removeListing(UUID id) {
        listings.remove(id);
        save();
    }

    public Listing getListing(UUID id) {
        return listings.get(id);
    }

    public List<Listing> getListingsByCategory(String categoryName) {
        return listings.values().stream()
                .filter(l -> l.getCategoryName().equalsIgnoreCase(categoryName))
                .collect(Collectors.toList());
    }

    public List<Listing> getListingsBySeller(UUID sellerUuid) {
        return listings.values().stream()
                .filter(l -> l.getSellerUuid().equals(sellerUuid))
                .collect(Collectors.toList());
    }

    public Collection<Listing> getAllListings() {
        return listings.values();
    }

    public Map<String, Category> getCategories() {
        return categories;
    }

    public Category getCategory(String name) {
        return categories.get(name);
    }

    public String getCategoryForMaterial(Material material) {
        for (Category cat : categories.values()) {
            if (cat.getMaterials().contains(material)) {
                return cat.getName();
            }
        }
        return "Редкости";
    }

    public void addPendingPayment(UUID sellerUuid, Material material, int amount) {
        pendingPayments.computeIfAbsent(sellerUuid, k -> new HashMap<>())
                .merge(material, amount, Integer::sum);
        save();
    }

    public Map<Material, Integer> claimPendingPayments(UUID uuid) {
        Map<Material, Integer> payments = pendingPayments.remove(uuid);
        if (payments != null && !payments.isEmpty()) {
            save();
        }
        return payments != null ? payments : Collections.emptyMap();
    }

    // ===== Внутренние классы =====
    public static class Category {
        private final String name;
        private final Material icon;
        private final List<Material> materials;

        public Category(String name, Material icon, List<Material> materials) {
            this.name = name;
            this.icon = icon;
            this.materials = materials;
        }

        public String getName() { return name; }
        public Material getIcon() { return icon; }
        public List<Material> getMaterials() { return materials; }
    }

    public static class Listing {
        private final UUID id;
        private final UUID sellerUuid;
        private final String sellerName;
        private final ItemStack itemStack;
        private final Material priceMaterial;
        private final int priceAmount;
        private final double moneyPrice;
        private final String categoryName;
        private final long createdAt;
        private final UUID targetPlayer;

        public Listing(UUID id, UUID sellerUuid, String sellerName, ItemStack itemStack,
                       Material priceMaterial, int priceAmount, double moneyPrice,
                       String categoryName, long createdAt, UUID targetPlayer) {
            this.id = id;
            this.sellerUuid = sellerUuid;
            this.sellerName = sellerName;
            this.itemStack = itemStack;
            this.priceMaterial = priceMaterial;
            this.priceAmount = priceAmount;
            this.moneyPrice = moneyPrice;
            this.categoryName = categoryName;
            this.createdAt = createdAt;
            this.targetPlayer = targetPlayer;
        }

        public UUID getId() { return id; }
        public UUID getSellerUuid() { return sellerUuid; }
        public String getSellerName() { return sellerName; }
        public ItemStack getItemStack() { return itemStack.clone(); }
        public Material getPriceMaterial() { return priceMaterial; }
        public int getPriceAmount() { return priceAmount; }
        public double getMoneyPrice() { return moneyPrice; }
        public String getCategoryName() { return categoryName; }
        public long getCreatedAt() { return createdAt; }
        public UUID getTargetPlayer() { return targetPlayer; }
    }
}
