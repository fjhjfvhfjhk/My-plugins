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

public class DiplomacyGUI implements Listener {

    private static final String LIST_TITLE = "§6Дипломатия";
    private static final String ACTION_PREFIX = "§6Дипломатия: ";

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final AllianceRequestManager requestManager;
    private final PactRequestManager pactRequestManager;
    private final PeaceRequestManager peaceRequestManager;
    private final Map<UUID, String> selectedCountry = new HashMap<>();

    public DiplomacyGUI(SovereigntyPlugin plugin, CountryManager countryManager,
                        AllianceRequestManager requestManager,
                        PactRequestManager pactRequestManager,
                        PeaceRequestManager peaceRequestManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
        this.requestManager = requestManager;
        this.pactRequestManager = pactRequestManager;
        this.peaceRequestManager = peaceRequestManager;
    }

    public void open(Player player) {
        String myCountry = countryManager.getCountryName(player.getUniqueId());
        if (myCountry == null) {
            player.sendMessage("§cСоздайте страну, чтобы вести дипломатию.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, LIST_TITLE);
        fillEmptyWithGlass(inv);

        List<String> countries = countryManager.getAllCountries();
        int slot = 0;
        for (String country : countries) {
            if (country.equals(myCountry)) continue;
            if (slot >= 45) break;

            ItemStack icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§f" + country);

            String relation = countryManager.getRelation(myCountry, country);
            List<String> lore = new ArrayList<>();
            lore.add("§7Отношение: " + relationColor(relation) + relation);
            lore.add("§eНажмите, чтобы выбрать действие");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        inv.setItem(49, createButton(Material.BARRIER, "§cНазад", List.of()));
        player.openInventory(inv);
    }

    private void openActions(Player player, String targetCountry) {
        String myCountry = countryManager.getCountryName(player.getUniqueId());
        if (myCountry == null) return;

        selectedCountry.put(player.getUniqueId(), targetCountry);

        Inventory inv = Bukkit.createInventory(null, 45, ACTION_PREFIX + targetCountry);
        fillEmptyWithGlass(inv);

        UUID targetOwner = countryManager.getOwner(targetCountry);
        boolean incomingAlly = targetOwner != null && requestManager.hasIncomingRequest(player.getUniqueId(), targetOwner);
        boolean atWar = plugin.getPactManager().isAtWar(myCountry, targetCountry);

        inv.setItem(10, incomingAlly
                ? createButton(Material.EMERALD, "§aПринять альянс", List.of("§7" + targetCountry + " предлагает альянс"))
                : createButton(Material.EMERALD, "§aПредложить альянс", List.of("§7Запросить союз")));
        inv.setItem(11, createButton(Material.REDSTONE, "§cОтклонить альянс", List.of("§7Отклонить входящий запрос")));
        inv.setItem(12, createButton(Material.PAPER, "§7Нейтралитет", List.of("§7Установить нейтральные отношения")));
        inv.setItem(13, createButton(Material.REDSTONE_BLOCK, "§cОбъявить войну", List.of("§cВойна с " + targetCountry)));

        inv.setItem(28, createButton(Material.GOLD_INGOT, "§eПакт: Торговый",
                List.of("§7Бонус к пассивному доходу", "§7обеих стран на 5% за каждый пакт.")));
        inv.setItem(29, createButton(Material.IRON_SWORD, "§eПакт: Военный",
                List.of("§7Совместные войны,", "§7координация атак.")));
        inv.setItem(30, createButton(Material.SHIELD, "§eПакт: Оборонительный",
                List.of("§7Взаимная защита,", "§7нельзя захватывать друг друга.")));
        inv.setItem(32, createButton(Material.GREEN_WOOL, "§eПакт: Ненападение",
                List.of("§7Запрещает атаки и захват чанков", "§7между странами.")));
        inv.setItem(31, createButton(Material.BARRIER, "§cРасторгнуть пакты",
                List.of("§7Сбросить все пакты с этой страной.")));

        if (atWar) {
            inv.setItem(38, createButton(Material.PAPER, "§eМирный договор",
                    List.of("§7Предложить мир врагу.")));
            inv.setItem(39, createButton(Material.REDSTONE_BLOCK, "§cКапитуляция",
                    List.of("§7Немедленное поражение с репарациями 20%.")));
            inv.setItem(40, createButton(Material.EMERALD_BLOCK, "§aПринять мир",
                    List.of("§7Принять входящий мирный договор.")));
            inv.setItem(41, createButton(Material.REDSTONE, "§cОтклонить мир",
                    List.of("§7Отклонить мирный договор.")));
        }

        inv.setItem(44, createButton(Material.BARRIER, "§cНазад", List.of()));
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(LIST_TITLE) && !title.startsWith(ACTION_PREFIX)) return;
        event.setCancelled(true);

        if (title.equals(LIST_TITLE)) {
            int slot = event.getRawSlot();
            if (slot == 49) {
                player.closeInventory();
                player.performCommand("country");
                return;
            }
            if (slot >= 0 && slot < 45) {
                ItemStack clicked = event.getInventory().getItem(slot);
                if (clicked != null && clicked.getType() == Material.PAPER) {
                    ItemMeta meta = clicked.getItemMeta();
                    String country = meta.getDisplayName().substring(2);
                    openActions(player, country);
                }
            }
            return;
        }

        if (title.startsWith(ACTION_PREFIX)) {
            String targetCountry = title.substring(ACTION_PREFIX.length());
            int slot = event.getRawSlot();
            String myCountry = countryManager.getCountryName(player.getUniqueId());
            if (myCountry == null) return;
            UUID targetOwner = countryManager.getOwner(targetCountry);

            // Проверка дипломатического бана для действий, связанных с дипломатией
            if (slot == 10 || slot == 12 || slot == 13 || slot == 28 || slot == 29 || slot == 30 || slot == 32) {
                if (countryManager.isDiplomacyBanned(myCountry)) {
                    player.sendMessage("§cВаша дипломатия временно заблокирована санкцией!");
                    openActions(player, targetCountry);
                    return;
                }
            }

            // Проверка пакта о ненападении для объявления войны
            if (slot == 13) {
                if (plugin.getPactManager().hasNonAggressionPact(myCountry, targetCountry)) {
                    player.sendMessage("§cВы не можете объявить войну, так как у вас действует пакт о ненападении!");
                    openActions(player, targetCountry);
                    return;
                }
            }

            switch (slot) {
                case 10 -> {
                    boolean incoming = targetOwner != null && requestManager.hasIncomingRequest(player.getUniqueId(), targetOwner);
                    if (incoming) {
                        countryManager.setAlly(myCountry, targetCountry);
                        requestManager.acceptRequest(player.getUniqueId(), targetOwner);
                        player.sendMessage("§aАльянс заключён!");
                    } else {
                        if (targetOwner != null) requestManager.sendRequest(player.getUniqueId(), targetOwner);
                        player.sendMessage("§aЗапрос альянса отправлен.");
                        Player target = targetOwner != null ? Bukkit.getPlayer(targetOwner) : null;
                        if (target != null && target.isOnline()) {
                            target.sendMessage("§e" + player.getName() + " (§f" + myCountry + "§e) отправил вам запрос на альянс.");
                        }
                    }
                    openActions(player, targetCountry);
                }
                case 11 -> {
                    if (targetOwner != null && requestManager.hasIncomingRequest(player.getUniqueId(), targetOwner)) {
                        requestManager.declineRequest(player.getUniqueId());
                        player.sendMessage("§cЗапрос отклонён.");
                    }
                    openActions(player, targetCountry);
                }
                case 12 -> {
                    countryManager.setNeutral(myCountry, targetCountry);
                    player.sendMessage("§7Нейтралитет.");
                    openActions(player, targetCountry);
                }
                case 13 -> {
                    countryManager.setEnemy(myCountry, targetCountry);
                    plugin.getPactManager().declareWar(myCountry, targetCountry);
                    player.sendMessage("§cВойна объявлена!");
                    Player target = targetOwner != null ? Bukkit.getPlayer(targetOwner) : null;
                    if (target != null && target.isOnline()) target.sendMessage("§4" + myCountry + " объявил вам войну!");
                    openActions(player, targetCountry);
                }
                case 28 -> handlePactRequest(player, targetCountry, "trade");
                case 29 -> handlePactRequest(player, targetCountry, "military");
                case 30 -> handlePactRequest(player, targetCountry, "defense");
                case 32 -> handlePactRequest(player, targetCountry, "nonaggression");
                case 31 -> {
                    for (String type : new String[]{"trade","military","defense","nonaggression"}) {
                        plugin.getPactManager().removePact(myCountry, targetCountry, type);
                    }
                    player.sendMessage("§cПакты расторгнуты.");
                    openActions(player, targetCountry);
                }
                case 38 -> {
                    peaceRequestManager.sendRequest(myCountry, targetCountry);
                    player.sendMessage("§eМирный договор предложен.");
                    Player target = targetOwner != null ? Bukkit.getPlayer(targetOwner) : null;
                    if (target != null && target.isOnline()) target.sendMessage("§e" + myCountry + " предлагает мирный договор.");
                    openActions(player, targetCountry);
                }
                case 39 -> {
                    plugin.getPactManager().surrender(player);
                    openActions(player, targetCountry);
                }
                case 40 -> {
                    if (targetOwner != null && peaceRequestManager.hasIncomingRequest(myCountry, targetCountry)) {
                        countryManager.setNeutral(myCountry, targetCountry);
                        plugin.getPactManager().removeWar(myCountry, targetCountry);
                        peaceRequestManager.removeRequest(targetCountry, myCountry);
                        player.sendMessage("§aМир заключён!");
                        Player target = Bukkit.getPlayer(targetOwner);
                        if (target != null && target.isOnline()) target.sendMessage("§a" + myCountry + " принял мирный договор!");
                    }
                    openActions(player, targetCountry);
                }
                case 41 -> {
                    peaceRequestManager.removeRequest(targetCountry, myCountry);
                    player.sendMessage("§cМирный договор отклонён.");
                    openActions(player, targetCountry);
                }
                case 44 -> open(player);
            }
        }
    }

    private void handlePactRequest(Player player, String targetCountry, String type) {
        String myCountry = countryManager.getCountryName(player.getUniqueId());
        if (myCountry == null) return;

        if (plugin.getPactManager().hasPact(myCountry, targetCountry, type)) {
            player.sendMessage("§cВы уже заключили этот пакт с " + targetCountry + "!");
            openActions(player, targetCountry);
            return;
        }

        boolean incoming = pactRequestManager.hasIncomingRequest(myCountry, targetCountry, type);
        UUID targetOwner = countryManager.getOwner(targetCountry);

        if (incoming) {
            plugin.getPactManager().addPact(myCountry, targetCountry, type);
            pactRequestManager.removeRequest(myCountry, targetCountry, type);
            player.sendMessage("§aПакт принят!");
            Player target = targetOwner != null ? Bukkit.getPlayer(targetOwner) : null;
            if (target != null && target.isOnline()) target.sendMessage("§a" + myCountry + " принял ваш пакт (" + type + ")!");
        } else {
            pactRequestManager.sendRequest(myCountry, targetCountry, type);
            player.sendMessage("§aЗапрос пакта отправлен.");
            Player target = targetOwner != null ? Bukkit.getPlayer(targetOwner) : null;
            if (target != null && target.isOnline()) {
                String typeName = switch (type) {
                    case "trade" -> "торговый пакт";
                    case "military" -> "военный пакт";
                    case "defense" -> "оборонительный пакт";
                    case "nonaggression" -> "пакт о ненападении";
                    default -> type;
                };
                target.sendMessage("§e" + player.getName() + " (§f" + myCountry + "§e) отправил вам запрос на " + typeName + ".");
            }
        }
        openActions(player, targetCountry);
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

    private String relationColor(String relation) {
        return switch (relation) {
            case "ally" -> "§a";
            case "enemy" -> "§c";
            default -> "§7";
        };
    }
}
