package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Chance-режим Upgrader.
 *
 * v3.0:
 *   - ЛКМ по кнопке → обычный ролл.
 *   - ПКМ по кнопке → авто-ролл ×10 (10 ставок подряд с одним шансом).
 *   - Все роллы записываются в RollData (история + лидерборд).
 */
public class UpgradeChanceGUI implements Listener {

    public static final String TITLE = "§6🎯 Апгрейд — Шанс";

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 23};

    private final RollPlugin plugin;
    private final UpgradeManager manager;
    private final Map<UUID, Double> currentBets = new HashMap<>();

    public UpgradeChanceGUI(RollPlugin plugin, UpgradeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, double bet) {
        currentBets.put(player.getUniqueId(), bet);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillGlass(inv);

        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e🎯 Ставка: §f" + plugin.getEconomyManager().format(bet));
        im.setLore(List.of(
                "§7Чем ниже шанс — тем выше множитель.",
                "§7RTP ≈ 95%.",
                "",
                "§eЛКМ §7по кнопке — обычный ролл",
                "§eПКМ §7по кнопке — авто-ролл ×10"
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        double[] steps = UpgradeManager.CHANCE_STEPS;
        for (int i = 0; i < steps.length; i++) {
            double chance = steps[i];
            double mult = manager.calcMultiplier(chance);
            double win = bet * mult;

            Material mat;
            if (chance >= 0.75) mat = Material.LIME_WOOL;
            else if (chance >= 0.40) mat = Material.YELLOW_WOOL;
            else if (chance >= 0.15) mat = Material.ORANGE_WOOL;
            else mat = Material.RED_WOOL;

            ItemStack btn = new ItemStack(mat);
            ItemMeta bm = btn.getItemMeta();
            bm.setDisplayName("§eШанс: §f" + String.format("%.0f%%", chance * 100) +
                    " §7→ §a" + String.format("%.2fx", mult));
            bm.setLore(List.of(
                    "§7Выигрыш: §f" + plugin.getEconomyManager().format(win),
                    "",
                    "§eЛКМ §7— крутить (1 раз)",
                    "§eПКМ §7— крутить ×10"
            ));
            btn.setItemMeta(bm);
            inv.setItem(SLOTS[i], btn);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(26, close);

        player.openInventory(inv);
    }

    private void fillGlass(Inventory inv) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = g.getItemMeta();
        gm.setDisplayName(" ");
        g.setItemMeta(gm);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, g);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot == 26) {
            currentBets.remove(p.getUniqueId());
            p.closeInventory();
            return;
        }

        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                Double bet = currentBets.get(p.getUniqueId());
                if (bet == null || bet <= 0) { p.closeInventory(); return; }
                boolean x10 = e.isRightClick();
                handleRoll(p, bet, UpgradeManager.CHANCE_STEPS[i], x10);
                return;
            }
        }
    }

    private void handleRoll(Player p, double bet, double chance, boolean x10) {
        int rolls = x10 ? 10 : 1;
        double totalBet = bet * rolls;

        if (!plugin.getEconomyManager().withdraw(p, totalBet)) {
            p.sendMessage("§cНедостаточно денег для ставки " +
                    plugin.getEconomyManager().format(totalBet) + " (" + rolls + " × " +
                    plugin.getEconomyManager().format(bet) + ")");
            p.closeInventory();
            return;
        }

        int wins = 0;
        double totalPayout = 0;
        double mult = manager.calcMultiplier(chance);

        for (int i = 0; i < rolls; i++) {
            boolean win = manager.rollChance(chance);
            if (win) {
                wins++;
                double payout = bet * mult;
                totalPayout += payout;
                plugin.getEconomyManager().deposit(p, payout);
                plugin.getRollData().recordResult(p, "upgrade", bet, payout, true);
            } else {
                plugin.getRollData().recordResult(p, "upgrade", bet, 0, false);
            }
            plugin.getRollData().recordUpgradeRoll(p, win, bet, win ? bet * mult : 0, chance);
        }

        double profit = totalPayout - totalBet;
        String sign = profit >= 0 ? "+" : "";

        if (x10) {
            p.sendMessage("§6🎯 ×10 §7(шанс " + String.format("%.0f%%", chance * 100) +
                    "): §a" + wins + "§7/§f10 §7побед. " +
                    "Итого: §f" + plugin.getEconomyManager().format(totalPayout) +
                    " §7(профит: " + (profit >= 0 ? "§a" : "§c") + sign +
                    plugin.getEconomyManager().format(profit) + "§7).");
        } else {
            if (wins > 0) {
                p.sendMessage("§a🎯 Выиграл! §e" + String.format("%.2fx", mult) +
                        " §a→ §f" + plugin.getEconomyManager().format(totalPayout));
            } else {
                p.sendMessage("§c💥 Провал. Потеряно: §f" + plugin.getEconomyManager().format(totalBet));
            }
        }

        if (wins > 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        currentBets.remove(p.getUniqueId());
        p.closeInventory();
    }
}
