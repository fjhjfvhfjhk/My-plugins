package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class UpgradeItemGUI implements Listener {

    public static final String TITLE = "§6💎 Апгрейд — Предмет";

    private static final int SLOT_ITEM  = 22;
    private static final int SLOT_INFO  = 4;
    private static final int SLOT_GO    = 40;
    private static final int SLOT_CLOSE = 38;
    private static final int SLOT_MULT  = 36;

    private static final int[] CIRCLE = { 13, 14, 23, 32, 31, 30, 21, 12 };
    private static final int SPIN_TICKS = 60;
    private static final int FULL_CYCLES = 4;

    private final RollPlugin plugin;
    private final UpgradeManager manager;
    private final UpgradeRewardGUI rewardGUI;

    private final Set<UUID> closingPlayers = new HashSet<>();
    private final Map<UUID, Boolean> spinning = new HashMap<>();
    private final Map<UUID, Integer> multipliers = new HashMap<>();

    public UpgradeItemGUI(RollPlugin plugin, UpgradeManager manager, UpgradeRewardGUI rewardGUI) {
        this.plugin = plugin;
        this.manager = manager;
        this.rewardGUI = rewardGUI;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        closingPlayers.remove(player.getUniqueId());
        spinning.remove(player.getUniqueId());
        multipliers.putIfAbsent(player.getUniqueId(), 1);

        Inventory inv = Bukkit.createInventory(null, 45, TITLE);
        fillGlass(inv);

        ItemStack info = new ItemStack(Material.EMERALD);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§a🎯 Указатель");
        im.setLore(List.of(
                "§7Положи предмет в центр (слот 22).",
                "§7Вокруг — панели:",
                "§aЗелёная §7= шанс победы",
                "§cКрасная §7= провал",
                "",
                "§7Энчанты переносятся на награду.",
                "§eНажми «Апгрейд», чтобы крутить."
        ));
        info.setItemMeta(im);
        inv.setItem(SLOT_INFO, info);

        updateMultButton(inv, player);
        updatePanels(inv, null);

        ItemStack go = new ItemStack(Material.GRAY_DYE);
        ItemMeta gm = go.getItemMeta();
        gm.setDisplayName("§7Апгрейд недоступен");
        gm.setLore(List.of("§7Положи предмет в центр."));
        go.setItemMeta(gm);
        inv.setItem(SLOT_GO, go);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cЗакрыть");
        close.setItemMeta(cm);
        inv.setItem(SLOT_CLOSE, close);

        player.openInventory(inv);
    }

    private void fillGlass(Inventory inv) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = g.getItemMeta();
        gm.setDisplayName(" ");
        g.setItemMeta(gm);
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == SLOT_ITEM || i == SLOT_MULT || i == SLOT_GO || i == SLOT_CLOSE) continue;
            if (inv.getItem(i) == null) inv.setItem(i, g);
        }
    }

    private void updateMultButton(Inventory inv, Player p) {
        int mult = multipliers.getOrDefault(p.getUniqueId(), 1);
        Material mat;
        switch (mult) {
            case 2 -> mat = Material.IRON_INGOT;
            case 3 -> mat = Material.DIAMOND;
            default -> mat = Material.GOLD_NUGGET;
        }
        ItemStack btn = new ItemStack(mat);
        ItemMeta m = btn.getItemMeta();
        m.setDisplayName("§eМножитель: §f×" + mult);
        m.setLore(List.of(
                "§7Апгрейд применяется к §f" + mult + "§7 предметам.",
                "§7В слоте нужно минимум §f" + mult + "§7 шт.",
                "",
                "§eЛКМ — переключить (1 → 2 → 3 → 1)"
        ));
        btn.setItemMeta(m);
        inv.setItem(SLOT_MULT, btn);
    }

    private void updatePanels(Inventory inv, UpgradeManager.UpgradeRecipe recipe) {
        if (recipe == null) {
            for (int slot : CIRCLE)
                inv.setItem(slot, panel(Material.GRAY_STAINED_GLASS_PANE, "§7Панель", "§7Положи предмет в центр"));
            return;
        }
        int greenCount = countGreen(recipe.chance);
        boolean[] isGreen = new boolean[CIRCLE.length];
        int idx = 0;
        for (int step = 0; step < CIRCLE.length && idx < greenCount; step += 2) { isGreen[step] = true; idx++; }
        for (int step = 1; step < CIRCLE.length && idx < greenCount; step += 2) { isGreen[step] = true; idx++; }
        for (int i = 0; i < CIRCLE.length; i++) {
            int slot = CIRCLE[i];
            if (isGreen[i]) inv.setItem(slot, panel(Material.LIME_STAINED_GLASS_PANE, "§a✔ Шанс победы", "§7Зелёная = выигрыш"));
            else inv.setItem(slot, panel(Material.RED_STAINED_GLASS_PANE, "§c✘ Провал", "§7Красная = потеря"));
        }
    }

    private ItemStack panel(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> l = new ArrayList<>();
        for (String line : lore) l.add(line);
        meta.setLore(l);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(TITLE)) return;

        Inventory inv = e.getInventory();
        int raw = e.getRawSlot();
        int topSize = inv.getSize();

        if (spinning.getOrDefault(p.getUniqueId(), false)) { e.setCancelled(true); return; }
        if (raw >= topSize) return;

        if (raw == SLOT_CLOSE) { e.setCancelled(true); p.closeInventory(); return; }

        if (raw == SLOT_MULT) {
            e.setCancelled(true);
            int cur = multipliers.getOrDefault(p.getUniqueId(), 1);
            cur = cur >= 3 ? 1 : cur + 1;
            multipliers.put(p.getUniqueId(), cur);
            updateMultButton(inv, p);
            UpgradeManager.UpgradeRecipe recipe = recipeFor(inv);
            updateGO(inv, recipe, p);
            return;
        }

        if (raw == SLOT_GO) { e.setCancelled(true); handleSpin(p, inv); return; }

        if (raw == SLOT_ITEM) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                UpgradeManager.UpgradeRecipe recipe = recipeFor(inv);
                updatePanels(inv, recipe);
                updateGO(inv, recipe, p);
            });
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!e.getView().getTitle().equals(TITLE)) return;
        boolean ok = true;
        for (int slot : e.getRawSlots()) if (slot != SLOT_ITEM) ok = false;
        if (!ok) { e.setCancelled(true); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(e.getWhoClicked() instanceof Player p)) return;
            UpgradeManager.UpgradeRecipe recipe = recipeFor(e.getInventory());
            updatePanels(e.getInventory(), recipe);
            updateGO(e.getInventory(), recipe, p);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals(TITLE)) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        UUID uuid = p.getUniqueId();
        if (closingPlayers.contains(uuid)) return;
        closingPlayers.add(uuid);
        try {
            Inventory inv = e.getInventory();
            ItemStack slot = inv.getItem(SLOT_ITEM);
            if (slot != null && slot.getType() != Material.AIR) {
                inv.setItem(SLOT_ITEM, null);
                HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(slot);
                for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
        } finally {
            Bukkit.getScheduler().runTaskLater(plugin, () -> closingPlayers.remove(uuid), 1L);
        }
    }

    private UpgradeManager.UpgradeRecipe recipeFor(Inventory inv) {
        ItemStack slot = inv.getItem(SLOT_ITEM);
        if (slot == null || slot.getType() == Material.AIR) return null;
        return manager.getRecipe(slot.getType());
    }

    private void updateGO(Inventory inv, UpgradeManager.UpgradeRecipe recipe, Player p) {
        int mult = multipliers.getOrDefault(p.getUniqueId(), 1);
        ItemStack go = new ItemStack(recipe != null ? Material.ANVIL : Material.GRAY_DYE);
        ItemMeta gm = go.getItemMeta();
        if (recipe == null) {
            gm.setDisplayName("§7Апгрейд недоступен");
            gm.setLore(List.of("§7Положи предмет в центр (слот 22).", "§7Не все предметы апгрейдятся."));
        } else {
            ItemStack slot = inv.getItem(SLOT_ITEM);
            int have = (slot != null) ? slot.getAmount() : 0;
            List<String> lore = new ArrayList<>();
            lore.add("§7Вход: §f" + mult + " × " + slot.getType().name().toLowerCase().replace('_', ' '));
            lore.add("§7Есть в слоте: §f" + have);
            lore.add("§7Выход: §f" + mult + " × " + recipe.output.name().toLowerCase().replace('_', ' '));
            lore.add("§7Шанс: §e" + String.format("%.0f%%", recipe.chance * 100));
            lore.add("§7Деньги: §f" + plugin.getEconomyManager().format(recipe.valueOut * mult));
            if (have < mult) {
                lore.add("");
                lore.add("§c⚠ Нужно минимум " + mult + " шт. в слоте!");
            }
            Map<Enchantment, Integer> ench = extractEnchants(slot);
            if (!ench.isEmpty()) {
                lore.add("");
                lore.add("§d✦ Энчанты переносятся: §f" + ench.size());
            }
            lore.add("");
            lore.add("§e▶ Нажми, чтобы крутить");
            gm.setLore(lore);
        }
        go.setItemMeta(gm);
        inv.setItem(SLOT_GO, go);
    }

    private static Map<Enchantment, Integer> extractEnchants(ItemStack stack) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        if (stack == null || stack.getType() == Material.AIR) return result;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return result;
        if (!meta.getEnchants().isEmpty()) result.putAll(meta.getEnchants());
        if (meta instanceof EnchantmentStorageMeta esm) result.putAll(esm.getStoredEnchants());
        return result;
    }

    private void handleSpin(Player p, Inventory inv) {
        ItemStack slot = inv.getItem(SLOT_ITEM);
        if (slot == null || slot.getType() == Material.AIR) { p.sendMessage("§cПоложи предмет в центр."); return; }
        UpgradeManager.UpgradeRecipe recipe = manager.getRecipe(slot.getType());
        if (recipe == null) { p.sendMessage("§cЭтот предмет нельзя улучшить."); return; }

        int mult = multipliers.getOrDefault(p.getUniqueId(), 1);
        if (slot.getAmount() < mult) {
            p.sendMessage("§cНужно минимум §f" + mult + "§c шт. в слоте (множитель ×" + mult + ").");
            return;
        }

        int amount = mult;
        Material inputType = slot.getType();
        Map<Enchantment, Integer> enchants = extractEnchants(slot);

        boolean win = manager.rollItemUpgrade(recipe);
        spinning.put(p.getUniqueId(), true);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);

        int greenCount = countGreen(recipe.chance);
        int targetIdx = win ? 0 : Math.min(greenCount, CIRCLE.length - 1);
        final int totalSectors = FULL_CYCLES * CIRCLE.length + targetIdx;

        new BukkitRunnable() {
            int tick = 0;
            int lastSector = -1;
            @Override
            public void run() {
                if (!p.isOnline()) { cancel(); spinning.remove(p.getUniqueId()); return; }
                if (tick >= SPIN_TICKS) {
                    cancel();
                    spinning.remove(p.getUniqueId());
                    highlightFinal(p, inv, recipe, win, targetIdx);
                    finishSpin(p, inv, recipe, inputType, amount, win, enchants);
                    return;
                }
                double t = (double) tick / (double) SPIN_TICKS;
                double eased = 1.0 - Math.pow(1.0 - t, 3.0);
                int currentSector = (int) Math.round(eased * totalSectors);
                if (currentSector != lastSector) {
                    int currentIdx = currentSector % CIRCLE.length;
                    updatePanels(inv, recipe);
                    int slotIdx = CIRCLE[currentIdx];
                    boolean isGreen = currentIdx < greenCount;
                    Material litMat = isGreen ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
                    String litName = isGreen ? "§a★ Указатель" : "§c★ Указатель";
                    inv.setItem(slotIdx, panel(litMat, litName, "§7Смотрит сюда"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f + currentIdx * 0.1f);
                    p.updateInventory();
                    lastSector = currentSector;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void highlightFinal(Player p, Inventory inv, UpgradeManager.UpgradeRecipe recipe, boolean win, int targetIdx) {
        updatePanels(inv, recipe);
        int slotIdx = CIRCLE[targetIdx];
        Material litMat = win ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
        String litName = win ? "§a§l★ ПОБЕДА!" : "§c§l★ ПРОВАЛ";
        inv.setItem(slotIdx, panel(litMat, litName, win ? "§aВы забрали!" : "§cНе повезло"));
        p.updateInventory();
    }

    private int countGreen(double chance) {
        int n = (int) Math.round(chance * CIRCLE.length);
        return Math.max(1, Math.min(CIRCLE.length - 1, n));
    }

    private void finishSpin(Player p, Inventory inv, UpgradeManager.UpgradeRecipe recipe,
                            Material inputType, int amount, boolean win,
                            Map<Enchantment, Integer> enchants) {
        ItemStack slot = inv.getItem(SLOT_ITEM);
        if (slot != null && slot.getAmount() > amount) {
            slot.setAmount(slot.getAmount() - amount);
            inv.setItem(SLOT_ITEM, slot);
        } else {
            inv.setItem(SLOT_ITEM, null);
        }

        double profitBet = recipe.valueIn * amount;
        double profitPayout = win ? (recipe.valueOut * amount) : 0;
        plugin.getRollData().recordUpgradeRoll(p, win, profitBet, profitPayout, recipe.chance);

        if (win) {
            final double moneyValue = recipe.valueOut * amount;
            final Material outMat = recipe.output;
            final int outAmount = amount;
            final Map<Enchantment, Integer> ec = new LinkedHashMap<>(enchants);

            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    p.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            rewardGUI.open(p, outMat, outAmount, moneyValue, ec);
                        }
                    });
                }
            }, 25L);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
        } else {
            p.sendMessage("§c💥 Провал. Потеряно: §f" + amount + " × " +
                    inputType.name().toLowerCase().replace('_', ' ') + "§c.");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    p.closeInventory();
                }
            }, 25L);
        }
    }

    public int getMultiplier(UUID uuid) { return multipliers.getOrDefault(uuid, 1); }
}
