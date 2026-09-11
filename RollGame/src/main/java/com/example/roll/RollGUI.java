package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.DecimalFormat;
import java.util.*;

public class RollGUI implements Listener {

    private static final String TITLE = "§6Рулетка";
    private static final Map<Player, Inventory> openInventories = new HashMap<>();
    private static final DecimalFormat CHANCE_FORMAT = new DecimalFormat("#0.00");

    private final RollPlugin plugin;
    private final GameManager gameManager;

    public RollGUI(RollPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        updateInventory(inv);
        player.openInventory(inv);
        openInventories.put(player, inv);
    }

    private void updateInventory(Inventory inv) {
        inv.clear();
        fillEmptyWithGlass(inv);

        // Информация (слот 4)
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§eИнформация");
        List<String> lore = new ArrayList<>();
        if (gameManager.isActive() && !gameManager.isFinished()) {
            lore.add("§7Ставок: §f" + gameManager.getBetCount());
            lore.add("§7Банк: §f" + plugin.getEconomyManager().format(gameManager.getTotalPool()));
            if (!gameManager.isSpinning() && !gameManager.isFrozen() && !gameManager.isFinished()) {
                long remaining = gameManager.getStartTime() + plugin.getConfig().getInt("wait-time-seconds", 300) * 1000L - System.currentTimeMillis();
                if (remaining > 0) {
                    long sec = remaining / 1000;
                    lore.add("§7Осталось: §f" + sec + " сек.");
                } else {
                    lore.add("§cВремя вышло, игра завершается...");
                }
            } else if (gameManager.isSpinning()) {
                lore.add("§6Колесо вращается...");
            } else if (gameManager.isFrozen()) {
                lore.add("§6Колесо остановилось...");
            }
        } else if (gameManager.isFinished()) {
            UUID winner = gameManager.getWinner();
            if (winner != null) {
                String name = Bukkit.getOfflinePlayer(winner).getName();
                lore.add("§aПобедитель: §f" + name);
                lore.add("§aВыигрыш: §f" + plugin.getEconomyManager().format(gameManager.getTotalPool()));
            }
        } else {
            lore.add("§7Игра не активна");
            lore.add("§eСделайте первую ставку, чтобы начать!");
        }
        double commission = plugin.getConfig().getDouble("commission-percent", 0);
        if (commission > 0) lore.add("§7Комиссия: §f" + commission + "%");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Зелёный указатель – слот 13
        ItemStack pointer = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta pointerMeta = pointer.getItemMeta();
        pointerMeta.setDisplayName("§a▲");
        pointer.setItemMeta(pointerMeta);
        inv.setItem(13, pointer);

        // Карусель предметов – слоты 18-26
        if (gameManager.isActive() && !gameManager.isFinished()) {
            Map<UUID, Double> bets = gameManager.getBets();
            Map<UUID, Material> playerItems = gameManager.getPlayerItems();

            if (gameManager.isSpinning() || gameManager.isFrozen()) {
                List<UUID> visible = gameManager.getVisibleSlots();
                for (int i = 0; i < visible.size() && i < 9; i++) {
                    UUID player = visible.get(i);
                    if (player == null) continue;
                    Material mat = playerItems.getOrDefault(player, Material.DIAMOND);
                    String playerName = Bukkit.getOfflinePlayer(player).getName();
                    if (playerName == null) playerName = "???";
                    double bet = bets.getOrDefault(player, 0.0);
                    double chance = gameManager.getChance(player);
                    String chanceStr = CHANCE_FORMAT.format(chance) + "%";

                    ItemStack item = new ItemStack(mat);
                    ItemMeta meta = item.getItemMeta();

                    if (gameManager.isFrozen() && i == 4) {
                        meta.setDisplayName("§6§l🏆 " + playerName);
                        meta.setLore(List.of(
                                "§7Ставка: §f" + plugin.getEconomyManager().format(bet),
                                "§7Шанс выигрыша: §e" + chanceStr,
                                "§a🎉 ПОБЕДИТЕЛЬ!"
                        ));
                    } else {
                        meta.setDisplayName("§f" + playerName);
                        meta.setLore(List.of(
                                "§7Ставка: §f" + plugin.getEconomyManager().format(bet),
                                "§7Шанс выигрыша: §e" + chanceStr
                        ));
                    }
                    item.setItemMeta(meta);
                    inv.setItem(18 + i, item);
                }
            } else {
                int index = 0;
                for (UUID player : bets.keySet()) {
                    if (index > 8) break;
                    Material mat = playerItems.getOrDefault(player, Material.DIAMOND);
                    String playerName = Bukkit.getOfflinePlayer(player).getName();
                    if (playerName == null) playerName = "???";
                    double bet = bets.getOrDefault(player, 0.0);
                    double chance = gameManager.getChance(player);
                    String chanceStr = CHANCE_FORMAT.format(chance) + "%";

                    ItemStack item = new ItemStack(mat);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName("§f" + playerName);
                    meta.setLore(List.of(
                            "§7Ставка: §f" + plugin.getEconomyManager().format(bet),
                            "§7Шанс выигрыша: §e" + chanceStr
                    ));
                    item.setItemMeta(meta);
                    inv.setItem(18 + index, item);
                    index++;
                }
            }

            // Метка
            ItemStack wheelLabel = new ItemStack(Material.NAME_TAG);
            ItemMeta labelMeta = wheelLabel.getItemMeta();
            labelMeta.setDisplayName("§6Колесо фортуны");
            labelMeta.setLore(List.of("§7Вращается..."));
            wheelLabel.setItemMeta(labelMeta);
            inv.setItem(27, wheelLabel);
        } else if (gameManager.isFinished()) {
            UUID winner = gameManager.getWinner();
            if (winner != null) {
                ItemStack greenGlass = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta glassMeta = greenGlass.getItemMeta();
                glassMeta.setDisplayName(" ");
                greenGlass.setItemMeta(glassMeta);
                for (int i = 0; i < 9; i++) inv.setItem(i, greenGlass);
                for (int i = 45; i < 54; i++) inv.setItem(i, greenGlass);
                inv.setItem(9, greenGlass);
                inv.setItem(17, greenGlass);
                inv.setItem(18, greenGlass);
                inv.setItem(26, greenGlass);
                inv.setItem(27, greenGlass);
                inv.setItem(35, greenGlass);
                inv.setItem(36, greenGlass);
                inv.setItem(44, greenGlass);

                ItemStack winnerIcon = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) winnerIcon.getItemMeta();
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(winner));
                meta.setDisplayName("§a🏆 Победитель!");
                meta.setLore(List.of("§f" + Bukkit.getOfflinePlayer(winner).getName(),
                        "§7Выигрыш: §f" + plugin.getEconomyManager().format(gameManager.getTotalPool())));
                winnerIcon.setItemMeta(meta);
                inv.setItem(22, winnerIcon);
            }
        }

        // Кнопка ставки (слот 49)
        ItemStack betBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta betMeta = betBtn.getItemMeta();
        betMeta.setDisplayName("§aСделать ставку");
        betMeta.setLore(List.of("§7Введите сумму в чат:", "§f/roll bet <сумма>"));
        betBtn.setItemMeta(betMeta);
        inv.setItem(49, betBtn);

        // Кнопка завершения (слот 40)
        if (gameManager.isActive() && !gameManager.isSpinning() && !gameManager.isFrozen() && !gameManager.isFinished()) {
            ItemStack finishBtn = new ItemStack(Material.REDSTONE_BLOCK);
            ItemMeta finishMeta = finishBtn.getItemMeta();
            finishMeta.setDisplayName("§cЗавершить игру досрочно");
            finishMeta.setLore(List.of("§7Только для первого поставившего", "§7или администратора"));
            finishBtn.setItemMeta(finishMeta);
            inv.setItem(40, finishBtn);
        }
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

    public static void updateAllOpen() {
        RollPlugin plugin = RollPlugin.getInstance();
        if (plugin == null) return;
        RollGUI gui = plugin.getRollGUI();
        if (gui == null) return;
        for (Map.Entry<Player, Inventory> entry : new HashMap<>(openInventories).entrySet()) {
            Player player = entry.getKey();
            Inventory inv = entry.getValue();
            if (player.isOnline() && player.getOpenInventory() != null &&
                    player.getOpenInventory().getTopInventory() != null &&
                    player.getOpenInventory().getTopInventory().equals(inv)) {
                gui.updateInventory(inv);
                player.updateInventory();
            } else {
                openInventories.remove(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            player.sendMessage("§eВведите: /roll bet <сумма>");
        } else if (slot == 40) {
            if (!gameManager.isActive() || gameManager.isSpinning() || gameManager.isFrozen() || gameManager.isFinished()) {
                player.sendMessage("§cИгра уже завершается или завершена.");
                return;
            }
            UUID uuid = player.getUniqueId();
            UUID first = gameManager.getFirstBetter();
            if (!uuid.equals(first) && !player.hasPermission("roll.admin")) {
                player.sendMessage("§cТолько первый поставивший может завершить игру досрочно.");
                return;
            }
            gameManager.forceFinish(player);
            updateInventory(event.getInventory());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            openInventories.put((Player) event.getPlayer(), event.getInventory());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openInventories.remove(event.getPlayer());
    }
}
