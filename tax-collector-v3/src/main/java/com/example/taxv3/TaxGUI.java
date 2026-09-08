package com.example.taxv3;

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

/**
 * Налоговое GUI: показывает долг страны, позволяет оплатить.
 */
public class TaxGUI implements Listener {

    private final TaxPlugin plugin;
    private final EconomyManager economyManager;
    private final DataManager dataManager;
    private final SovereigntyBridge sovereigntyBridge;

    public TaxGUI(TaxPlugin plugin, EconomyManager economyManager,
                  DataManager dataManager, SovereigntyBridge sovereigntyBridge) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.dataManager = dataManager;
        this.sovereigntyBridge = sovereigntyBridge;
    }

    public void open(Player player) {
        String title = plugin.getConfig().getString("gui.title", "§6Налоги");
        Inventory inv = Bukkit.createInventory(null, 9, title);

        double debt = 0.0;
        double balance = economyManager.getBalance(player);

        String country = sovereigntyBridge.getCountryName(player.getUniqueId());
        if (country != null) {
            debt = sovereigntyBridge.getCountryDebt(country);
            if (debt <= 0) {
                debt = dataManager.getDebt(player.getUniqueId());
            }
        } else {
            debt = dataManager.getDebt(player.getUniqueId());
        }

        int chunks = country != null ? sovereigntyBridge.getClaimCount(country) : 0;
        double nextTax = plugin.getTaxManager().calculateTax(chunks);

        double finalDebt = debt;

        ItemStack payBtn = new ItemStack(debt > 0 ? Material.LIME_WOOL : Material.GRAY_WOOL);
        ItemMeta payMeta = payBtn.getItemMeta();
        payMeta.setDisplayName(plugin.getConfig().getString("gui.pay-button", "§aОплатить долг"));
        payMeta.setLore(List.of(
                "§7Долг: §f" + economyManager.format(finalDebt),
                finalDebt > 0 ? "§eНажмите, чтобы оплатить" : "§7Долгов нет"
        ));
        payBtn.setItemMeta(payMeta);
        inv.setItem(2, payBtn);

        ItemStack infoBtn = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = infoBtn.getItemMeta();
        infoMeta.setDisplayName(plugin.getConfig().getString("gui.info-button", "§eИнформация"));
        infoMeta.setLore(List.of(
                "§7Баланс: §f" + economyManager.format(balance),
                "§7Ваши чанки: §f" + chunks,
                "§7Следующий налог: §f" + economyManager.format(nextTax),
                "§7Долг: §f" + economyManager.format(finalDebt)
        ));
        infoBtn.setItemMeta(infoMeta);
        inv.setItem(4, infoBtn);

        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        closeBtn.setItemMeta(closeMeta);
        inv.setItem(6, closeBtn);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = plugin.getConfig().getString("gui.title", "§6Налоги");
        if (!event.getView().getTitle().equals(title)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == 2) {
            String country = sovereigntyBridge.getCountryName(player.getUniqueId());
            double debt;
            if (country != null) {
                debt = sovereigntyBridge.getCountryDebt(country);
                if (debt <= 0) debt = dataManager.getDebt(player.getUniqueId());
            } else {
                debt = dataManager.getDebt(player.getUniqueId());
            }

            if (debt <= 0) {
                player.sendMessage("§aДолгов нет.");
                return;
            }

            double balance = economyManager.getBalance(player);
            double toPay = Math.min(balance, debt);
            if (toPay <= 0) {
                player.sendMessage("§cНедостаточно средств. Нужно: " + economyManager.format(debt));
                return;
            }

            economyManager.withdraw(player, toPay);

            if (country != null && sovereigntyBridge.getCountryDebt(country) > 0) {
                sovereigntyBridge.reduceCountryDebt(country, toPay);
                player.sendMessage("§aОплачено: " + economyManager.format(toPay) +
                        ". Осталось долга страны: " +
                        economyManager.format(sovereigntyBridge.getCountryDebt(country)));
            } else {
                dataManager.reduceDebt(player.getUniqueId(), toPay);
                player.sendMessage("§aОплачено: " + economyManager.format(toPay) +
                        ". Осталось долга: " +
                        economyManager.format(dataManager.getDebt(player.getUniqueId())));
            }
            open(player);
        } else if (slot == 6) {
            player.closeInventory();
        }
    }
}
