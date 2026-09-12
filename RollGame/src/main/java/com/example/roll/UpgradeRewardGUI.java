package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class UpgradeRewardGUI implements Listener {

    public static final String TITLE = "§6🎁 Выбор приза";

    private static final int SLOT_ITEM  = 11;
    private static final int SLOT_MONEY = 15;

    private final RollPlugin plugin;
    private final Map<UUID, Pending> pending = new java.util.concurrent.ConcurrentHashMap<>();

    public UpgradeRewardGUI(RollPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, Material outputMat, int amount, double moneyValue) {
        open(player, outputMat, amount, moneyValue, Collections.emptyMap());
    }

    public void open(Player player, Material outputMat, int amount, double moneyValue,
                     Map<Enchantment, Integer> enchants) {
        Pending p = new Pending(outputMat, amount, moneyValue, enchants);
        pending.put(player.getUniqueId(), p);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fillGlass(inv);

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6🎁 Что забрать?");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Апгрейд прошёл успешно!");
        infoLore.add("§7Выбери награду:");
        if (!enchants.isEmpty()) {
            infoLore.add("");
            infoLore.add("§d✦ Перенос энчантов: §f" + enchants.size());
        }
        im.setLore(infoLore);
        info.setItemMeta(im);
        inv.setItem(4, info);

        ItemStack item = new ItemStack(outputMat, amount);
        ItemMeta itm = item.getItemMeta();
        if (itm != null) {
            itm.setDisplayName("§a💎 Забрать предмет");
            List<String> itemLore = new ArrayList<>();
            itemLore.add("§7" + amount + " × " + outputMat.name().toLowerCase().replace('_', ' '));
            if (!enchants.isEmpty()) {
                itemLore.add("");
                itemLore.add("§dЭнчанты:");
                for (Map.Entry<Enchantment, Integer> en : enchants.entrySet()) {
                    if (en.getKey().canEnchantItem(new ItemStack(outputMat))) {
                        itemLore.add("  §7- §f" + prettyEnch(en.getKey()) +
                                (en.getValue() > 1 ? " " + roman(en.getValue()) : ""));
                    } else {
                        itemLore.add("  §8- " + prettyEnch(en.getKey()) + " §8(не подходит)");
                    }
                }
            }
            itemLore.add("");
            itemLore.add("§e▶ Нажми, чтобы забрать");
            itm.setLore(itemLore);
            item.setItemMeta(itm);
        }
        inv.setItem(SLOT_ITEM, item);

        ItemStack money = new ItemStack(Material.GOLD_INGOT);
        ItemMeta mm = money.getItemMeta();
        mm.setDisplayName("§a💰 Забрать деньги");
        mm.setLore(List.of(
                "§7Сумма: §f" + plugin.getEconomyManager().format(moneyValue),
                "",
                "§e▶ Нажми, чтобы забрать"
        ));
        money.setItemMeta(mm);
        inv.setItem(SLOT_MONEY, money);

        player.openInventory(inv);
    }

    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
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

        Pending pend = pending.get(p.getUniqueId());
        if (pend == null) { p.closeInventory(); return; }

        int slot = e.getRawSlot();
        if (slot == SLOT_ITEM) {
            pending.remove(p.getUniqueId());
            p.closeInventory();
            giveItemReward(p, pend);
        } else if (slot == SLOT_MONEY) {
            pending.remove(p.getUniqueId());
            p.closeInventory();
            plugin.getEconomyManager().deposit(p, pend.moneyValue);
            p.sendMessage("§a💰 Получено: §f" + plugin.getEconomyManager().format(pend.moneyValue) + "§a.");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals(TITLE)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Pending pend = pending.remove(p.getUniqueId());
        if (pend != null) {
            giveItemReward(p, pend);
        }
    }

    private void giveItemReward(Player p, Pending pend) {
        ItemStack reward = new ItemStack(pend.outputMat, pend.amount);
        ItemMeta meta = reward.getItemMeta();
        int applied = 0;
        if (meta != null && !pend.enchants.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> en : pend.enchants.entrySet()) {
                if (en.getKey().canEnchantItem(reward)) {
                    meta.addEnchant(en.getKey(), en.getValue(), true);
                    applied++;
                }
            }
            reward.setItemMeta(meta);
        }
        HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(reward);
        for (ItemStack drop : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
        if (applied > 0) {
            p.sendMessage("§a💎 Получено: §f" + pend.amount + " × " +
                    pend.outputMat.name().toLowerCase().replace('_', ' ') +
                    " §a(+§d" + applied + " §aэнчантов)§a.");
        } else {
            p.sendMessage("§a💎 Получено: §f" + pend.amount + " × " +
                    pend.outputMat.name().toLowerCase().replace('_', ' ') + "§a.");
        }
    }

    private static String prettyEnch(Enchantment e) {
        String key = e.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String roman(int n) {
        String[] rn = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 1 && n <= 10) return rn[n];
        return String.valueOf(n);
    }

    private static class Pending {
        final Material outputMat;
        final int amount;
        final double moneyValue;
        final Map<Enchantment, Integer> enchants;

        Pending(Material outputMat, int amount, double moneyValue,
                Map<Enchantment, Integer> enchants) {
            this.outputMat = outputMat;
            this.amount = amount;
            this.moneyValue = moneyValue;
            this.enchants = new LinkedHashMap<>(enchants);
        }
    }
}
