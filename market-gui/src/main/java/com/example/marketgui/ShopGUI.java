package com.example.marketgui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI магазина: категории, товары, меню продавца.
 */
public class ShopGUI implements Listener {

    private static final String MAIN_TITLE = "§6Рынок сервера";
    private static final String SELLER_TITLE = "§6Мои товары";
    private static final String CATEGORY_PREFIX = "§6Категория: ";

    private final MarketPlugin plugin;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;

    public ShopGUI(MarketPlugin plugin, ShopManager shopManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.economyManager = economyManager;
    }

    public void openMainMenu(Player player) {
        Map<String, ShopManager.Category> categories = shopManager.getCategories();
        int size = categories.size() <= 9 ? 9 : 18;
        Inventory inv = Bukkit.createInventory(null, size, MAIN_TITLE);

        int slot = 0;
        for (ShopManager.Category cat : categories.values()) {
            if (slot >= size) break;
            ItemStack icon = new ItemStack(cat.getIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§a" + cat.getName());
            List<String> lore = new ArrayList<>();
            int count = shopManager.getListingsByCategory(cat.getName()).size();
            lore.add("§7Товаров: §f" + count);
            lore.add("§eНажмите, чтобы открыть");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }
        player.openInventory(inv);
    }

    public void openCategory(Player player, String categoryName) {
        List<ShopManager.Listing> listings = shopManager.getListingsByCategory(categoryName);
        // Фильтруем: если товар предназначен конкретному игроку, показываем только ему
        List<ShopManager.Listing> filtered = new ArrayList<>();
        for (ShopManager.Listing listing : listings) {
            if (listing.getTargetPlayer() == null || listing.getTargetPlayer().equals(player.getUniqueId())) {
                filtered.add(listing);
            }
        }

        Inventory inv = Bukkit.createInventory(null, 54, CATEGORY_PREFIX + categoryName);

        for (int i = 0; i < Math.min(filtered.size(), 45); i++) {
            ShopManager.Listing listing = filtered.get(i);
            ItemStack item = listing.getItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null && !meta.hasDisplayName()) {
                meta.setDisplayName("§f" + shopManager.getItemName(item.getType()));
            }
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("§7Продавец: §f" + listing.getSellerName());
            lore.add("§7Цена: §f" + formatPrice(listing));
            if (listing.getTargetPlayer() != null) {
                String targetName = Bukkit.getOfflinePlayer(listing.getTargetPlayer()).getName();
                lore.add("§6Лично для: §f" + (targetName != null ? targetName : listing.getTargetPlayer().toString()));
            }
            lore.add("§eНажмите, чтобы купить");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cНазад");
        back.setItemMeta(backMeta);
        inv.setItem(53, back);

        player.openInventory(inv);
    }

    public void openSellerMenu(Player player) {
        List<ShopManager.Listing> own = shopManager.getListingsBySeller(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, SELLER_TITLE);

        for (int i = 0; i < Math.min(own.size(), 45); i++) {
            ShopManager.Listing listing = own.get(i);
            ItemStack item = listing.getItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null && !meta.hasDisplayName()) {
                meta.setDisplayName("§f" + shopManager.getItemName(item.getType()));
            }
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("§7Категория: §f" + listing.getCategoryName());
            lore.add("§7Цена: §f" + formatPrice(listing));
            if (listing.getTargetPlayer() != null) {
                String targetName = Bukkit.getOfflinePlayer(listing.getTargetPlayer()).getName();
                lore.add("§6Лично для: §f" + (targetName != null ? targetName : listing.getTargetPlayer().toString()));
            }
            lore.add("§cНажмите, чтобы снять с продажи");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§eКак продавать?");
        infoMeta.setLore(List.of(
                "§7Держите предмет в руке и введите:",
                "§f/shop add <материал> <кол-во>",
                "§7или за валюту:",
                "§f/shop add money <сумма>",
                "§7или смешанно:",
                "§f/shop add <материал> <кол-во> money <сумма>",
                "§7Для личной продажи:",
                "§f/shop add <...> for <игрок>"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cВ главное меню");
        back.setItemMeta(backMeta);
        inv.setItem(53, back);

        player.openInventory(inv);
    }

    private String formatPrice(ShopManager.Listing listing) {
        StringBuilder sb = new StringBuilder();
        if (listing.getPriceMaterial() != null && listing.getPriceAmount() > 0) {
            sb.append(listing.getPriceAmount())
              .append(" × ")
              .append(shopManager.getMaterialName(listing.getPriceMaterial()));
        }
        if (listing.getMoneyPrice() > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(economyManager.format(listing.getMoneyPrice()));
        }
        return sb.length() > 0 ? sb.toString() : "Бесплатно";
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.equals(MAIN_TITLE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < event.getInventory().getSize()) {
                Map<String, ShopManager.Category> categories = shopManager.getCategories();
                int index = 0;
                for (ShopManager.Category cat : categories.values()) {
                    if (index == slot) {
                        openCategory(player, cat.getName());
                        return;
                    }
                    index++;
                }
            }
            return;
        }

        if (title.startsWith(CATEGORY_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 53) {
                openMainMenu(player);
                return;
            }
            if (slot >= 0 && slot < 45) {
                String categoryName = title.substring(CATEGORY_PREFIX.length());
                List<ShopManager.Listing> listings = shopManager.getListingsByCategory(categoryName);
                // Фильтруем так же, как при открытии
                List<ShopManager.Listing> filtered = new ArrayList<>();
                for (ShopManager.Listing listing : listings) {
                    if (listing.getTargetPlayer() == null || listing.getTargetPlayer().equals(player.getUniqueId())) {
                        filtered.add(listing);
                    }
                }
                if (slot < filtered.size()) {
                    buyItem(player, filtered.get(slot));
                }
            }
            return;
        }

        if (title.equals(SELLER_TITLE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 53) {
                openMainMenu(player);
                return;
            }
            if (slot >= 0 && slot < 45) {
                List<ShopManager.Listing> own = shopManager.getListingsBySeller(player.getUniqueId());
                if (slot < own.size()) {
                    removeListing(player, own.get(slot));
                }
            }
        }
    }

    private void buyItem(Player buyer, ShopManager.Listing listing) {
        if (buyer.getUniqueId().equals(listing.getSellerUuid())) {
            buyer.sendMessage("§cВы не можете купить свой собственный товар.");
            return;
        }

        // Проверка, что товар предназначен для этого игрока
        if (listing.getTargetPlayer() != null && !listing.getTargetPlayer().equals(buyer.getUniqueId())) {
            buyer.sendMessage("§cЭтот товар предназначен для другого игрока.");
            return;
        }

        // Проверяем предметную цену
        Material priceMaterial = listing.getPriceMaterial();
        int priceAmount = listing.getPriceAmount();
        if (priceMaterial != null && priceAmount > 0) {
            if (!hasItems(buyer, priceMaterial, priceAmount)) {
                buyer.sendMessage("§cУ вас недостаточно " + shopManager.getMaterialName(priceMaterial) +
                        ". Нужно: " + priceAmount);
                return;
            }
        }

        // Проверяем денежную цену
        double moneyPrice = listing.getMoneyPrice();
        if (moneyPrice > 0) {
            if (!economyManager.isEnabled() || !economyManager.has(buyer, moneyPrice)) {
                buyer.sendMessage("§cУ вас недостаточно денег. Нужно: " + economyManager.format(moneyPrice));
                return;
            }
        }

        // Снимаем предметную цену
        if (priceMaterial != null && priceAmount > 0) {
            removeItems(buyer, priceMaterial, priceAmount);
        }

        // Снимаем деньги
        if (moneyPrice > 0) {
            economyManager.withdraw(buyer, moneyPrice);
        }

        // Переводим оплату продавцу
        Player seller = plugin.getServer().getPlayer(listing.getSellerUuid());

        // Предметная оплата
        if (priceMaterial != null && priceAmount > 0) {
            if (seller != null && seller.isOnline()) {
                HashMap<Integer, ItemStack> leftover = seller.getInventory().addItem(new ItemStack(priceMaterial, priceAmount));
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        seller.getWorld().dropItemNaturally(seller.getLocation(), drop);
                    }
                    seller.sendMessage("§eИнвентарь полон — оплата выброшена рядом.");
                }
                seller.sendMessage("§aВаш товар куплен! Вы получили " + priceAmount + " × " +
                        shopManager.getMaterialName(priceMaterial) + ".");
            } else {
                shopManager.addPendingPayment(listing.getSellerUuid(), priceMaterial, priceAmount);
                buyer.sendMessage("§eПродавец оффлайн. Предметная оплата будет выдана при входе.");
            }
        }

        // Денежная оплата — зачисляем сразу на баланс, офлайн не проблема
        if (moneyPrice > 0) {
            if (seller != null && seller.isOnline()) {
                economyManager.deposit(seller, moneyPrice);
                seller.sendMessage("§aВаш товар куплен! Вы получили " + economyManager.format(moneyPrice) + ".");
            } else {
                // Для офлайн используем Vault deposit по OfflinePlayer
                try {
                    economyManager.deposit(plugin.getServer().getOfflinePlayer(listing.getSellerUuid()), moneyPrice);
                } catch (Exception e) {
                    plugin.getLogger().warning("Не удалось зачислить деньги офлайн игроку " + listing.getSellerName());
                }
                buyer.sendMessage("§eПродавец оффлайн. Деньги зачислены на его баланс.");
            }
        }

        // Отдаём товар покупателю
        ItemStack product = listing.getItemStack();
        HashMap<Integer, ItemStack> leftover = buyer.getInventory().addItem(product);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                buyer.getWorld().dropItemNaturally(buyer.getLocation(), drop);
            }
            buyer.sendMessage("§eИнвентарь полон — предмет выброшен рядом.");
        }

        shopManager.removeListing(listing.getId());
        buyer.sendMessage("§aПокупка успешна!");

        openCategory(buyer, listing.getCategoryName());
    }

    private void removeListing(Player seller, ShopManager.Listing listing) {
        shopManager.removeListing(listing.getId());
        HashMap<Integer, ItemStack> leftover = seller.getInventory().addItem(listing.getItemStack());
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                seller.getWorld().dropItemNaturally(seller.getLocation(), drop);
            }
            seller.sendMessage("§eИнвентарь полон — предмет выброшен рядом.");
        }
        seller.sendMessage("§aТовар снят с продажи.");
        openSellerMenu(seller);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Map<Material, Integer> pending = shopManager.claimPendingPayments(player.getUniqueId());
        if (!pending.isEmpty()) {
            for (Map.Entry<Material, Integer> entry : pending.entrySet()) {
                Material mat = entry.getKey();
                int amount = entry.getValue();
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(mat, amount));
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                player.sendMessage("§aВы получили отложенную оплату: " + amount + " × " +
                        shopManager.getMaterialName(mat) + ".");
            }
        }
    }

    private boolean hasItems(Player player, Material material, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int toRemove = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - toRemove);
                remaining -= toRemove;
            }
        }
    }
}
