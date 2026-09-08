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

import java.util.*;

public class CourtGUI implements Listener {

    private static final String TITLE = "§6Международный суд";
    private static final String SANCTION_TITLE = "§6Выбор санкций";

    private final SovereigntyPlugin plugin;
    private final CourtManager courtManager;

    private final Map<UUID, Set<String>> pendingSanctions = new HashMap<>();
    private final Map<UUID, Integer> pendingCaseId = new HashMap<>();

    public CourtGUI(SovereigntyPlugin plugin, CourtManager courtManager) {
        this.plugin = plugin;
        this.courtManager = courtManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillEmptyWithGlass(inv);

        List<CourtManager.CaseInfo> cases = courtManager.getActiveCases();
        int slot = 0;
        for (CourtManager.CaseInfo caseInfo : cases) {
            if (slot >= 45) break;

            String plaintiffName = Bukkit.getOfflinePlayer(caseInfo.plaintiff).getName();
            String defendantName = Bukkit.getOfflinePlayer(caseInfo.defendant).getName();

            ItemStack icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§eДело #" + caseInfo.id);
            List<String> lore = new ArrayList<>();
            lore.add("§7Истец: §f" + (plaintiffName != null ? plaintiffName : "Неизвестно"));
            lore.add("§7Ответчик: §f" + (defendantName != null ? defendantName : "Неизвестно"));
            lore.add("§7Причина: §f" + caseInfo.reason);
            lore.add("§7Голосов за: §f" + courtManager.getAgreeVoters(caseInfo.id));
            lore.add("§7Всего проголосовало: §f" + courtManager.getTotalVoters(caseInfo.id));

            if (player.getUniqueId().equals(caseInfo.defendant)) {
                lore.add("§cВы являетесь ответчиком и не можете голосовать.");
            } else {
                lore.add("§eЛКМ - проголосовать за санкции");
                lore.add("§cПКМ - проголосовать против");
            }
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        inv.setItem(49, createButton(Material.BARRIER, "§cНазад", List.of()));
        player.openInventory(inv);
    }

    private void openSanctionSelection(Player player, int caseId) {
        pendingCaseId.put(player.getUniqueId(), caseId);
        pendingSanctions.put(player.getUniqueId(), new HashSet<>());

        Inventory inv = Bukkit.createInventory(null, 27, SANCTION_TITLE);
        fillEmptyWithGlass(inv);

        inv.setItem(11, createSanctionButton("fine", "§eШтраф", "Списывает деньги из казны"));
        inv.setItem(13, createSanctionButton("embargo", "§eТорговое эмбарго", "Разрывает торговые пакты"));
        inv.setItem(15, createSanctionButton("diplomacy", "§eДипломатические", "Разрывает альянсы и блокирует дипломатию"));

        inv.setItem(22, createButton(Material.LIME_WOOL, "§aПодтвердить голос", List.of()));
        player.openInventory(inv);
    }

    private ItemStack createSanctionButton(String type, String name, String desc) {
        ItemStack icon = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of("§7" + desc, "§eНажмите, чтобы выбрать"));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createButton(Material mat, String name, List<String> lore) {
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ВАЖНО: отменяем клик только в наших меню
        if (!title.equals(TITLE) && !title.equals(SANCTION_TITLE)) {
            return;
        }

        event.setCancelled(true);

        if (title.equals(TITLE)) {
            int slot = event.getRawSlot();
            if (slot == 49) {
                player.closeInventory();
                player.performCommand("country");
                return;
            }
            if (slot >= 0 && slot < 45) {
                List<CourtManager.CaseInfo> cases = courtManager.getActiveCases();
                if (slot < cases.size()) {
                    CourtManager.CaseInfo caseInfo = cases.get(slot);

                    if (player.getUniqueId().equals(caseInfo.defendant)) {
                        player.sendMessage("§cВы являетесь ответчиком и не можете голосовать в этом деле.");
                        open(player);
                        return;
                    }

                    if (courtManager.hasVoted(player.getUniqueId(), caseInfo.id)) {
                        player.sendMessage("§cВы уже голосовали по этому делу.");
                        open(player);
                        return;
                    }

                    if (event.isLeftClick()) {
                        openSanctionSelection(player, caseInfo.id);
                    } else if (event.isRightClick()) {
                        courtManager.vote(player.getUniqueId(), caseInfo.id, false, Set.of());
                        player.sendMessage("§cВы проголосовали против санкций.");
                        open(player);
                    }
                }
            }
            return;
        }

        if (title.equals(SANCTION_TITLE)) {
            int slot = event.getRawSlot();
            UUID uuid = player.getUniqueId();
            Set<String> selected = pendingSanctions.getOrDefault(uuid, new HashSet<>());

            if (slot == 11) toggle(selected, "fine");
            else if (slot == 13) toggle(selected, "embargo");
            else if (slot == 15) toggle(selected, "diplomacy");
            else if (slot == 22) {
                if (selected.isEmpty()) {
                    player.sendMessage("§cВыберите хотя бы одну санкцию.");
                    return;
                }
                int caseId = pendingCaseId.getOrDefault(uuid, -1);
                if (caseId != -1) {
                    courtManager.vote(uuid, caseId, true, selected);
                    player.sendMessage("§aГолос за санкции принят!");
                }
                pendingSanctions.remove(uuid);
                pendingCaseId.remove(uuid);
                player.closeInventory();
                open(player);
                return;
            }

            pendingSanctions.put(uuid, selected);
            Inventory inv = event.getInventory();
            updateSanctionMenu(inv, selected);
            return;
        }
    }

    private void toggle(Set<String> selected, String type) {
        if (!selected.remove(type)) selected.add(type);
    }

    private void updateSanctionMenu(Inventory inv, Set<String> selected) {
        updateButton(inv, 11, "fine", selected.contains("fine"));
        updateButton(inv, 13, "embargo", selected.contains("embargo"));
        updateButton(inv, 15, "diplomacy", selected.contains("diplomacy"));
    }

    private void updateButton(Inventory inv, int slot, String type, boolean selected) {
        ItemStack item = inv.getItem(slot);
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (selected) {
            item.setType(Material.LIME_WOOL);
            meta.setDisplayName(meta.getDisplayName().replace("§e", "§a"));
        } else {
            item.setType(Material.GRAY_DYE);
            meta.setDisplayName(meta.getDisplayName().replace("§a", "§e"));
        }
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }
}
