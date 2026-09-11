package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class CountryGUI implements Listener {

    private static final String CREATE_TITLE = "§6Создание страны";
    private static final String MANAGE_TITLE = "§6Моя страна";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final EnergyManager energyManager;
    private final EconomyManager economyManager;

    public CountryGUI(SovereigntyPlugin plugin, CountryManager countryManager,
                      EnergyManager energyManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
        this.energyManager = energyManager;
        this.economyManager = economyManager;
    }

    public void openMainMenu(Player player) {
        if (countryManager.hasCountry(player.getUniqueId())) {
            openManageMenu(player);
        } else {
            openCreateMenu(player);
        }
    }

    private void openCreateMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, CREATE_TITLE);
        fillEmptyWithGlass(inv);
        inv.setItem(4, createButton(
                Material.GOLD_NUGGET,
                "§aСоздать страну",
                List.of("§7Введите команду:", "§f/country create <название>", "§7Название может быть любым")
        ));
        player.openInventory(inv);
    }

    public void openManageMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MANAGE_TITLE);
        fillEmptyWithGlass(inv);

        String country = countryManager.getCountryName(player.getUniqueId());
        int claims = countryManager.getClaimCount(country);
        double energy = energyManager.getEnergy(player.getUniqueId());
        double maxEnergy = energyManager.getMaxEnergy(player.getUniqueId());
        double bank = countryManager.getBankBalance(country);

        inv.setItem(10, createButton(Material.GRASS_BLOCK, "§aТерритория",
                List.of("§7Захват, освобождение, автозахват")));
        inv.setItem(12, createButton(Material.WRITABLE_BOOK, "§aДипломатия",
                List.of("§7Союзы, войны, пакты, капитуляция")));
        inv.setItem(14, createButton(Material.EXPERIENCE_BOTTLE, "§eУлучшения",
                List.of("§7Энергия, регенерация, лимит чанков,", "§7доход ферм, буст, наука")));
        inv.setItem(16, createButton(Material.GOLD_INGOT, "§aБанк страны",
                List.of("§7Баланс: " + economyManager.format(bank),
                        "§7/country bank deposit <сумма>",
                        "§7/country bank withdraw <сумма>")));

        inv.setItem(28, createButton(Material.GLOWSTONE_DUST, "§aSeechunk",
                List.of("§7Показать границы чанка частицами")));
        inv.setItem(30, createButton(Material.ENDER_PEARL, "§aUnstuck",
                List.of("§7Телепорт если вы на чужой земле")));
        inv.setItem(32, createButton(Material.NETHER_STAR, "§eТоп стран",
                List.of("§7Рейтинг по территории, казне, силе")));
        inv.setItem(34, createButton(Material.KNOWLEDGE_BOOK, "§eИсследования",
                List.of("§7Дерево технологий")));

        EventManager eventManager = plugin.getEventManager();
        String eventName = eventManager.getActiveEventName();
        if (eventName != null) {
            String eventDesc = eventManager.getActiveEventDescription();
            inv.setItem(36, createButton(Material.CLOCK, "§eСобытие: " + eventName,
                    List.of("§7" + eventDesc)));
        } else {
            inv.setItem(36, createButton(Material.BARRIER, "§7Событий нет",
                    List.of("§7Сейчас не происходит никаких глобальных событий.")));
        }

        inv.setItem(40, createButton(Material.BOOKSHELF, "§eМеждународный суд",
                List.of("§7Жалобы, голосования, санкции")));

        inv.setItem(46, createButton(Material.DIAMOND_PICKAXE, "§aШахтёрский бонус",
                List.of("§7Спешка за шахтёрские чанки")));
        inv.setItem(47, createButton(Material.CHEST, "§aМагазин",
                List.of("§7Открыть серверный магазин", "§7(/shop)")));
        inv.setItem(48, createButton(Material.CRAFTING_TABLE, "§aУлучшение чанка",
                List.of("§7Ферма, шахта, военный, торговый")));
        inv.setItem(50, createButton(Material.COMMAND_BLOCK, "§6Управление страной",
                List.of("§7Удалить, переименовать")));
        inv.setItem(52, createButton(Material.OAK_SIGN, "§eИнформация",
                List.of("§7Страна: §f" + country, "§7Чанков: §f" + claims,
                        "§7Энергия: §f" + String.format("%.1f", energy) + "/" + String.format("%.1f", maxEnergy))));

        player.openInventory(inv);
    }

    private void fillEmptyWithGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals(CREATE_TITLE) && !title.equals(MANAGE_TITLE)) return;

        event.setCancelled(true);

        if (title.equals(CREATE_TITLE)) {
            if (event.getRawSlot() == 4) {
                player.closeInventory();
                player.sendMessage("§eВведите: /country create <название>");
            }
            return;
        }

        if (title.equals(MANAGE_TITLE)) {
            int slot = event.getRawSlot();
            if (slot == 10) { player.closeInventory(); plugin.getClaimGUI().open(player); }
            else if (slot == 12) { player.closeInventory(); plugin.getDiplomacyGUI().open(player); }
            else if (slot == 14) { player.closeInventory(); plugin.getUpgradeGUI().open(player); }
            else if (slot == 16) { player.closeInventory(); player.sendMessage("§eВведите: /country bank deposit <сумма> или /country bank withdraw <сумма>"); }
            else if (slot == 28) { player.closeInventory(); player.performCommand("country seechunk"); }
            else if (slot == 30) { player.closeInventory(); player.performCommand("country unstuck"); }
            else if (slot == 32) { player.closeInventory(); plugin.getTopGUI().open(player, "claims"); }
            else if (slot == 34) { player.closeInventory(); plugin.getTechnologyGUI().open(player); }
            else if (slot == 36) { player.closeInventory(); }
            else if (slot == 40) { player.closeInventory(); plugin.getCourtGUI().open(player); }
            else if (slot == 46) { player.closeInventory(); player.performCommand("country miningboost"); }
            else if (slot == 47) { player.closeInventory(); player.performCommand("shop"); }
            else if (slot == 48) { player.closeInventory(); plugin.getChunkUpgradeGUI().open(player); }
            else if (slot == 50) { player.closeInventory(); plugin.getManageCountryGUI().open(player); }
            else if (slot == 52) { player.closeInventory(); player.performCommand("country info"); }
        }
    }
}
