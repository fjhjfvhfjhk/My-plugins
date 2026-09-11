package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class CountryCommand implements CommandExecutor, TabCompleter {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;
    private final EnergyManager energyManager;
    private final EconomyManager economyManager;
    private final CountryGUI countryGUI;

    public CountryCommand(SovereigntyPlugin plugin, CountryManager countryManager,
                          EnergyManager energyManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
        this.energyManager = energyManager;
        this.economyManager = economyManager;
        this.countryGUI = new CountryGUI(plugin, countryManager, energyManager, economyManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        if (args.length == 0) {
            countryGUI.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!player.hasPermission("sovereignty.admin")) {
                    player.sendMessage("§cУ вас нет прав.");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getWebPanelUploader().loadConfig();
                player.sendMessage("§aКонфигурация Sovereignty и веб-панели перезагружена.");
                return true;
            }
            case "panel" -> {
                return panelCommand(player, args);
            }
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /country create <название>");
                    return true;
                }
                if (countryManager.hasCountry(player.getUniqueId())) {
                    player.sendMessage("§cУ вас уже есть страна.");
                    return true;
                }
                if (countryManager.createCountry(player, args[1])) {
                    player.sendMessage("§aСтрана §e" + args[1] + "§a создана!");
                } else {
                    player.sendMessage("§cНазвание занято.");
                }
                return true;
            }
            case "claim" -> {
                String country = countryManager.getCountryName(player.getUniqueId());
                if (country == null) {
                    player.sendMessage("§cСоздайте страну: /country create <название>");
                    return true;
                }
                if (countryManager.claimChunk(player)) {
                    player.sendMessage("§aЧанк захвачен! Осталось энергии: §f" +
                            String.format("%.1f", energyManager.getEnergy(player.getUniqueId())));
                    plugin.getAchievementManager().checkAchievements(player);
                } else {
                    player.sendMessage("§cНе удалось захватить. Проверьте энергию, деньги, лимит или защиту.");
                }
                return true;
            }
            case "unclaim" -> {
                if (countryManager.unclaimChunk(player)) {
                    player.sendMessage("§aЧанк освобождён.");
                } else {
                    player.sendMessage("§cЭтот чанк не ваш.");
                }
                return true;
            }
            case "ally", "enemy", "neutral" -> {
                if (args.length < 2) {
                    player.sendMessage("§cУкажите игрока.");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage("§cИгрок не в сети."); return true; }
                String my = countryManager.getCountryName(player.getUniqueId());
                String their = countryManager.getCountryName(target.getUniqueId());
                if (my == null || their == null) { player.sendMessage("§cУ обоих должны быть страны."); return true; }

                if (countryManager.isDiplomacyBanned(my)) {
                    player.sendMessage("§cВаша дипломатия временно заблокирована санкцией!");
                    return true;
                }

                if (args[0].equalsIgnoreCase("enemy")) {
                    if (plugin.getPactManager().hasNonAggressionPact(my, their)) {
                        player.sendMessage("§cВы не можете объявить войну, так как у вас действует пакт о ненападении!");
                        return true;
                    }
                }

                switch (args[0].toLowerCase()) {
                    case "ally" -> { countryManager.setAlly(my, their); player.sendMessage("§aСоюз!"); target.sendMessage("§a" + player.getName() + " предлагает союз!"); }
                    case "enemy" -> { countryManager.setEnemy(my, their); plugin.getPactManager().declareWar(my, their); player.sendMessage("§cВойна!"); target.sendMessage("§c" + player.getName() + " объявил войну!"); }
                    case "neutral" -> { countryManager.setNeutral(my, their); player.sendMessage("§aНейтралитет."); }
                }
                return true;
            }
            case "map" -> { showMap(player); return true; }
            case "info" -> { showInfo(player, args.length >= 2 ? args[1] : null); return true; }
            case "list" -> { showList(player); return true; }
            case "buyenergy" -> { buyEnergy(player, args); return true; }
            case "boost" -> { buyBoost(player); return true; }
            case "upgrade" -> { plugin.getUpgradeGUI().open(player); return true; }
            case "bank" -> {
                String country = countryManager.getCountryName(player.getUniqueId());
                if (country == null) { player.sendMessage("§cСоздайте страну."); return true; }
                if (args.length < 2) {
                    double bal = countryManager.getBankBalance(country);
                    player.sendMessage("§7Баланс банка: §f" + economyManager.format(bal));
                    player.sendMessage("§eИспользование: /country bank deposit <сумма> или /country bank withdraw <сумма> или /country bank withdraw all");
                    return true;
                }
                if (args[1].equalsIgnoreCase("deposit")) {
                    if (args.length < 3) { player.sendMessage("§cУкажите сумму."); return true; }
                    double amount;
                    try { amount = Double.parseDouble(args[2]); if (amount <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { player.sendMessage("§cВведите положительное число."); return true; }
                    if (!economyManager.has(player, amount)) { player.sendMessage("§cНедостаточно денег."); return true; }
                    economyManager.withdraw(player, amount);
                    countryManager.depositToBank(country, amount);
                    player.sendMessage("§aВнесено в банк: " + economyManager.format(amount));
                    plugin.getAchievementManager().checkAchievements(player);
                    return true;
                } else if (args[1].equalsIgnoreCase("withdraw")) {
                    if (args.length >= 3 && args[2].equalsIgnoreCase("all")) { handleWithdrawAll(player); return true; }
                    if (args.length < 3) { player.sendMessage("§cУкажите сумму или all."); return true; }
                    double amount;
                    try { amount = Double.parseDouble(args[2]); if (amount <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { player.sendMessage("§cВведите положительное число."); return true; }
                    if (!countryManager.withdrawFromBank(country, amount)) { player.sendMessage("§cНедостаточно в банке."); return true; }
                    economyManager.deposit(player, amount);
                    player.sendMessage("§aСнято из банка: " + economyManager.format(amount));
                    return true;
                } else {
                    player.sendMessage("§cИспользуйте deposit или withdraw.");
                    return true;
                }
            }
            case "rename" -> { renameCommand(player, args); return true; }
            case "autoclaim" -> {
                plugin.toggleAutoClaim(player.getUniqueId());
                if (plugin.isAutoClaimEnabled(player.getUniqueId())) player.sendMessage("§aАвтозахват включён.");
                else player.sendMessage("§cАвтозахват выключен.");
                return true;
            }
            case "unstuck" -> { handleUnstuck(player); return true; }
            case "seechunk" -> { plugin.getChunkBorderGUI().open(player); return true; }
            case "chunkupgrade" -> { plugin.getChunkUpgradeGUI().open(player); return true; }
            case "miningboost" -> { plugin.getMiningBoostManager().activate(player); return true; }
            case "surrender" -> { plugin.getPactManager().surrender(player); return true; }
            case "top" -> { String sort = args.length >= 2 ? args[1].toLowerCase() : "claims"; plugin.getTopGUI().open(player, sort); return true; }
            case "achievements" -> { showAchievements(player); return true; }
            case "research" -> { plugin.getTechnologyGUI().open(player); return true; }
            case "court" -> { courtCommand(player, args); return true; }
            case "pact" -> { pactCommand(player, args); return true; }
            case "admin" -> { return adminCommand(player, args); }
            case "invite" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /country invite <игрок>"); return true; }
                handleInvite(player, args[1]);
                return true;
            }
            case "kick" -> {
                if (args.length < 2) { player.sendMessage("§cИспользование: /country kick <игрок>"); return true; }
                handleKick(player, args[1]);
                return true;
            }
            case "accept" -> { handleAccept(player); return true; }
            case "decline" -> { handleDecline(player); return true; }
            default -> {
                player.sendMessage("§cИспользование: /country [reload|create|claim|unclaim|ally|enemy|neutral|map|info|list|buyenergy|boost|upgrade|bank|rename|autoclaim|unstuck|seechunk|chunkupgrade|miningboost|surrender|top|achievements|research|court|pact|invite|kick|accept|decline|panel]");
                return true;
            }
        }
    }

    // ==================== АДМИН-КОМАНДА /country panel ====================

    private boolean panelCommand(Player player, String[] args) {
        if (!player.hasPermission("sovereignty.admin")) {
            player.sendMessage("§cУ вас нет прав.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§6=== Управление веб-панелью ===");
            player.sendMessage("§f/country panel push §7— принудительно отправить данные и карту");
            player.sendMessage("§f/country panel status §7— статус веб-панели и кэша");
            player.sendMessage("§f/country panel prerender <радиус> §7— прогрузить чанки вокруг всех стран");
            player.sendMessage("§f/country panel prerender cancel §7— отменить прогрузку");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "push" -> {
                if (!plugin.getWebPanelUploader().isEnabled()) {
                    player.sendMessage("§cВеб-панель отключена. Настройте webpanel.yml.");
                    return true;
                }
                player.sendMessage("§eОтправляю данные и карту на GitHub...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getWebPanelUploader().push();
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§aГотово. Проверьте логи сервера."));
                });
                return true;
            }
            case "status" -> {
                player.sendMessage("§6=== Статус веб-панели ===");
                player.sendMessage("§7Веб-панель: " +
                        (plugin.getWebPanelUploader().isEnabled() ? "§aвключена" : "§cотключена"));
                player.sendMessage("§7Чанков в terrain-кэше: §f" +
                        plugin.getTerrainRenderer().getCacheSize());
                player.sendMessage("§7Новых с последней отправки: §f" +
                        plugin.getTerrainRenderer().getNewChunksSinceLastPush());
                player.sendMessage("§7Прогрузка: §f" +
                        (plugin.getPrerenderManager().isRunning()
                                ? "идёт (" + plugin.getPrerenderManager().getDone() + "/" +
                                  plugin.getPrerenderManager().getTotal() + ")"
                                : "не идёт"));
                return true;
            }
            case "prerender" -> {
                if (args.length >= 3 && args[2].equalsIgnoreCase("cancel")) {
                    if (plugin.getPrerenderManager().cancel()) {
                        player.sendMessage("§aОтмена выполнена.");
                    } else {
                        player.sendMessage("§cПрогрузка сейчас не идёт.");
                    }
                    return true;
                }
                int radius = 5;
                if (args.length >= 3) {
                    try {
                        radius = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cРадиус должен быть числом (1-20).");
                        return true;
                    }
                } else {
                    player.sendMessage("§7Радиус по умолчанию: §f5§7. Использование: §f/country panel prerender <радиус>");
                }
                plugin.getPrerenderManager().start(player, radius);
                return true;
            }
            default -> {
                player.sendMessage("§cНеизвестная подкоманда. /country panel");
                return true;
            }
        }
    }

    private void courtCommand(Player player, String[] args) {
        if (args.length == 1) { plugin.getCourtGUI().open(player); return; }
        if (args[1].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("sovereignty.court.admin")) { player.sendMessage("§cУ вас нет прав администратора суда."); return; }
            if (args.length < 4) { player.sendMessage("§cИспользование: /country court admin <close|cancel> <id>"); return; }
            String action = args[2].toLowerCase();
            int caseId;
            try { caseId = Integer.parseInt(args[3]); } catch (NumberFormatException e) { player.sendMessage("§cID дела должно быть числом."); return; }
            CourtManager courtManager = plugin.getCourtManager();
            CourtManager.CaseInfo caseInfo = courtManager.getCase(caseId);
            if (caseInfo == null) { player.sendMessage("§cДело не найдено или уже закрыто."); return; }
            if (action.equalsIgnoreCase("close")) {
                int totalVoters = courtManager.getTotalVoters(caseId);
                int minVotes = plugin.getConfig().getInt("court.min-votes", 2);
                int requiredPercent = plugin.getConfig().getInt("court.required-votes-percent", 60);
                if (totalVoters < minVotes) { courtManager.closeCase(caseId); player.sendMessage("§aДело #" + caseId + " закрыто без санкций (мало голосов)."); }
                else {
                    Map<String, Integer> sanctionVotes = courtManager.getSanctionVotes(caseId);
                    Set<String> applied = new HashSet<>();
                    for (Map.Entry<String, Integer> entry : sanctionVotes.entrySet()) {
                        if (100.0 * entry.getValue() / totalVoters >= requiredPercent) applied.add(entry.getKey());
                    }
                    if (applied.isEmpty()) { courtManager.closeCase(caseId); player.sendMessage("§aДело #" + caseId + " закрыто: санкции не набрали поддержки."); }
                    else { courtManager.applySanctions(caseId, applied); player.sendMessage("§aСанкции применены: " + String.join(", ", applied)); }
                }
                return;
            } else if (action.equalsIgnoreCase("cancel")) {
                courtManager.closeCase(caseId);
                player.sendMessage("§aДело #" + caseId + " отменено.");
                Bukkit.broadcastMessage("§6[Суд] §eДело #" + caseId + " было отменено администратором.");
                return;
            } else { player.sendMessage("§cИспользуйте close или cancel."); return; }
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("file")) {
            Player defendant = Bukkit.getPlayer(args[2]);
            if (defendant == null) { player.sendMessage("§cИгрок не в сети."); return; }
            String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            if (reason.isEmpty()) reason = "Без причины";
            int caseId = plugin.getCourtManager().fileComplaint(player.getUniqueId(), defendant.getUniqueId(), reason);
            if (caseId != -1) {
                player.sendMessage("§aЖалоба подана! Дело #" + caseId);
                Bukkit.broadcastMessage("§6[Суд] §e" + player.getName() + " подал жалобу на " + defendant.getName() + ". Причина: " + reason);
            } else { player.sendMessage("§cНе удалось подать жалобу."); }
            return;
        }
        player.sendMessage("§cИспользование: /country court [file <ник> <причина>] или /country court admin <close|cancel> <id>");
    }

    private void pactCommand(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cИспользование: /country pact <тип> <игрок>"); return; }
        String type = args[1].toLowerCase();
        if (!type.equals("trade") && !type.equals("military") && !type.equals("defense") && !type.equals("nonaggression")) {
            player.sendMessage("§cНеизвестный тип пакта.");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { player.sendMessage("§cИгрок не в сети."); return; }
        String my = countryManager.getCountryName(player.getUniqueId());
        String their = countryManager.getCountryName(target.getUniqueId());
        if (my == null || their == null) { player.sendMessage("§cУ обоих должны быть страны."); return; }
        if (countryManager.isDiplomacyBanned(my)) { player.sendMessage("§cВаша дипломатия временно заблокирована санкцией!"); return; }
        if (plugin.getPactManager().hasPact(my, their, type)) { player.sendMessage("§cВы уже заключили этот пакт!"); return; }
        plugin.getPactRequestManager().sendRequest(my, their, type);
        player.sendMessage("§aЗапрос пакта отправлен.");
        target.sendMessage("§e" + player.getName() + " (§f" + my + "§e) отправил вам запрос на пакт: " + type);
    }

    private void showAchievements(Player player) {
        Map<String, Boolean> achievements = plugin.getAchievementManager().getAchievements(player.getUniqueId());
        Map<String, String[]> descriptions = AchievementManager.getAchievementDescriptions();
        player.sendMessage("§6=== Достижения ===");
        for (Map.Entry<String, String[]> entry : descriptions.entrySet()) {
            String id = entry.getKey();
            String name = entry.getValue()[0];
            String desc = entry.getValue()[1];
            boolean claimed = achievements.getOrDefault(id, false);
            String icon = claimed ? "§a✅" : "§7⬜";
            player.sendMessage(icon + " §f" + name + " §7- " + desc);
        }
    }

    private void showMap(Player player) {
        int radius = plugin.getConfig().getInt("map-radius", 7);
        String my = countryManager.getCountryName(player.getUniqueId());
        var chunk = player.getLocation().getChunk();
        var world = player.getWorld();
        player.sendMessage("§6Карта территорий (§7радиус " + radius + "§6):");
        player.sendMessage("§7Формат: §fA§7f §7— страна A, тип §af§7(ферма). §7m§7-шахта, §7v§7-воен, §7t§7-торг, §7-§7-обычный");
        for (int dz = radius; dz >= -radius; dz--) {
            StringBuilder sb = new StringBuilder();
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) { sb.append("§e+§r "); continue; }
                String owner = countryManager.getChunkOwner(world, chunk.getX() + dx, chunk.getZ() + dz);
                if (owner == null) { sb.append("§8-§r  "); continue; }

                char letter = Character.toUpperCase(owner.charAt(0));
                String color;
                if (owner.equals(my)) color = "§a";
                else if (my != null && countryManager.isAlly(my, owner)) color = "§a";
                else if (my != null && countryManager.isEnemy(my, owner)) color = "§c";
                else color = "§7";

                String type = plugin.getChunkUpgradeManager().getChunkType(world, chunk.getX() + dx, chunk.getZ() + dz);
                String typeLetter = switch (type) {
                    case "farm" -> "§af";
                    case "mining" -> "§bm";
                    case "military" -> "§cv";
                    case "trade" -> "§6t";
                    default -> "§7-";
                };
                sb.append(color).append(letter).append(typeLetter).append("§r ");
            }
            player.sendMessage(sb.toString());
        }
    }

    private void showInfo(Player player, String targetName) {
        UUID uuid = targetName == null ? player.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
        String country = countryManager.getCountryName(uuid);
        if (country == null) { player.sendMessage("§cСтрана не найдена."); return; }

        int claims = countryManager.getClaimCount(country);
        int maxClaims = countryManager.getMaxClaims(uuid);
        double energy = energyManager.getEnergy(uuid);
        double maxEnergy = energyManager.getMaxEnergy(uuid);
        double regen = energyManager.getCurrentRegenPerHour(uuid);
        double bank = countryManager.getBankBalance(country);
        boolean boost = energyManager.isBoostActive(uuid);

        int farm = plugin.getChunkUpgradeManager().countType(country, "farm");
        int mining = plugin.getChunkUpgradeManager().countType(country, "mining");
        int military = plugin.getChunkUpgradeManager().countType(country, "military");
        int trade = plugin.getChunkUpgradeManager().countType(country, "trade");
        double farmIncome = plugin.getChunkUpgradeManager().calculatePassiveIncome(country);

        player.sendMessage("§6=== " + country + " ===");
        player.sendMessage("§7Лидер: §f" + Bukkit.getOfflinePlayer(uuid).getName());
        player.sendMessage("§7Территория: §f" + claims + " / " + maxClaims);
        player.sendMessage("§7Энергия: §f" + String.format("%.1f", energy) + " / " + String.format("%.1f", maxEnergy));
        player.sendMessage("§7Реген: §f" + String.format("%.1f", regen) + "/час" + (boost ? " §a(буст активен)" : ""));
        player.sendMessage("§7Банк: §f" + economyManager.format(bank));
        player.sendMessage("§7Улучшения: §aФерм: " + farm + " §7| §bШахт: " + mining + " §7| §cВоен: " + military + " §7| §6Торг: " + trade);
        player.sendMessage("§7Доход ферм/час: §f" + economyManager.format(farmIncome));
    }

    private void showList(Player player) {
        List<String> countries = countryManager.getAllCountries();
        player.sendMessage("§6=== Страны сервера ===");
        if (countries.isEmpty()) { player.sendMessage("§7Стран пока нет."); return; }
        for (String c : countries) {
            int claims = countryManager.getClaimCount(c);
            player.sendMessage("§f- " + c + " §7(" + claims + " чанков)");
        }
    }

    private void buyEnergy(Player player, String[] args) {
        if (!economyManager.isEnabled()) { player.sendMessage("§cЭкономика отключена."); return; }
        double cost = plugin.getConfig().getDouble("energy.buy-energy-cost", 1000.0);
        double amount;
        if (args.length >= 2) {
            try { amount = Double.parseDouble(args[1]); if (amount <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { player.sendMessage("§cВведите положительное число."); return; }
        } else {
            amount = energyManager.getCurrentRegenPerHour(player.getUniqueId());
            amount = Math.max(1, amount);
        }
        double totalCost = cost * amount;
        if (!economyManager.has(player, totalCost)) { player.sendMessage("§cНедостаточно денег. Нужно: " + economyManager.format(totalCost)); return; }
        if (!energyManager.addEnergy(player.getUniqueId(), amount)) { player.sendMessage("§cВы уже на максимуме энергии."); return; }
        economyManager.withdraw(player, totalCost);
        player.sendMessage("§aКуплено энергии: " + String.format("%.1f", amount) + ". Баланс энергии: " + String.format("%.1f", energyManager.getEnergy(player.getUniqueId())));
    }

    private void buyBoost(Player player) {
        if (!economyManager.isEnabled()) { player.sendMessage("§cЭкономика отключена."); return; }
        if (energyManager.buyBoost(player.getUniqueId())) {
            int hours = plugin.getConfig().getInt("energy.boost-duration-hours", 1);
            player.sendMessage("§aБуст регенерации активирован на " + hours + " час(а)!");
        } else { player.sendMessage("§cНедостаточно денег или ошибка."); }
    }

    private void renameCommand(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cИспользование: /country rename <новое>"); return; }
        String newName = args[1];
        if (countryManager.renameCountry(player.getUniqueId(), newName)) player.sendMessage("§aСтрана переименована в " + newName + "!");
        else player.sendMessage("§cНе удалось переименовать.");
    }

    private void handleUnstuck(Player player) {
        var chunk = player.getLocation().getChunk();
        String owner = countryManager.getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());
        String myCountry = countryManager.getCountryName(player.getUniqueId());
        if (owner == null || owner.equals(myCountry)) { player.sendMessage("§cВы не на чужой территории."); return; }
        Random random = new Random();
        int offsetX = (random.nextBoolean() ? 1 : -1) * (50 + random.nextInt(51));
        int offsetZ = (random.nextBoolean() ? 1 : -1) * (50 + random.nextInt(51));
        Location current = player.getLocation();
        Location target = current.clone().add(offsetX, 0, offsetZ);
        target.setY(target.getWorld().getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1);
        player.teleport(target);
        player.sendMessage("§aВы телепортированы от чужой территории.");
    }

    private boolean adminCommand(Player player, String[] args) {
        if (!player.hasPermission("sovereignty.admin")) { player.sendMessage("§cУ вас нет прав администратора."); return true; }
        if (args.length < 4) {
            player.sendMessage("§cИспользование:");
            player.sendMessage("§c/country admin giveenergy <ник> <кол-во>");
            player.sendMessage("§c/country admin removeenergy <ник> <кол-во>");
            player.sendMessage("§c/country admin removechunk <игрок>");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "giveenergy" -> {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { player.sendMessage("§cИгрок не в сети."); return true; }
                double amount;
                try { amount = Double.parseDouble(args[3]); if (amount <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) { player.sendMessage("§cВведите положительное число."); return true; }
                energyManager.forceAddEnergy(target.getUniqueId(), amount);
                player.sendMessage("§aВыдано " + String.format("%.1f", amount) + " энергии игроку " + target.getName());
                target.sendMessage("§aВам выдано " + String.format("%.1f", amount) + " энергии.");
                return true;
            }
            case "removeenergy" -> {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { player.sendMessage("§cИгрок не в сети."); return true; }
                double amount;
                try { amount = Double.parseDouble(args[3]); if (amount <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) { player.sendMessage("§cВведите положительное число."); return true; }
                energyManager.forceRemoveEnergy(target.getUniqueId(), amount);
                player.sendMessage("§aСнято " + String.format("%.1f", amount) + " энергии у игрока " + target.getName());
                target.sendMessage("§cУ вас снято " + String.format("%.1f", amount) + " энергии.");
                return true;
            }
            case "removechunk" -> {
                String targetCountry = countryManager.getCountryName(Bukkit.getOfflinePlayer(args[2]).getUniqueId());
                if (targetCountry == null) { player.sendMessage("§cУ игрока нет страны."); return true; }
                if (countryManager.removeRandomChunk(targetCountry)) player.sendMessage("§aЧанк страны " + targetCountry + " изъят.");
                else player.sendMessage("§cНе удалось изъять чанк.");
                return true;
            }
            default -> { player.sendMessage("§cНеизвестная админ-подкоманда."); return true; }
        }
    }

    private void handleInvite(Player player, String targetName) {
        String country = countryManager.getCountryName(player.getUniqueId());
        if (country == null) { player.sendMessage("§cУ вас нет страны."); return; }
        if (!countryManager.isLeader(player.getUniqueId(), country)) { player.sendMessage("§cТолько лидер страны может приглашать соправителей."); return; }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage("§cИгрок не в сети."); return; }
        if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage("§cНельзя пригласить самого себя."); return; }
        if (countryManager.getCountryName(target.getUniqueId()) != null) { player.sendMessage("§cЭтот игрок уже имеет свою страну."); return; }
        if (countryManager.isCoRuler(target.getUniqueId(), country)) { player.sendMessage("§cЭтот игрок уже является соправителем."); return; }
        plugin.getInviteManager().sendInvite(player.getUniqueId(), target.getUniqueId(), country);
        player.sendMessage("§aПриглашение отправлено игроку " + target.getName());
        target.sendMessage("§eВас пригласили стать соправителем страны §f" + country + "§e от игрока §f" + player.getName() + "§e.");
        target.sendMessage("§eИспользуйте §f/country accept§e или §f/country decline§e.");
    }

    private void handleKick(Player player, String targetName) {
        String country = countryManager.getCountryName(player.getUniqueId());
        if (country == null) { player.sendMessage("§cУ вас нет страны."); return; }
        if (!countryManager.isLeader(player.getUniqueId(), country)) { player.sendMessage("§cТолько лидер страны может кикать соправителей."); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || target.getName() == null) { player.sendMessage("§cИгрок не найден."); return; }
        if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage("§cНельзя кикнуть самого себя."); return; }
        if (!countryManager.isCoRuler(target.getUniqueId(), country)) { player.sendMessage("§cЭтот игрок не является соправителем."); return; }
        countryManager.removeCoRuler(country, target.getUniqueId());
        player.sendMessage("§aСоправитель " + target.getName() + " исключён.");
        if (target.isOnline() && target.getPlayer() != null) target.getPlayer().sendMessage("§cВы были исключены из соправителей страны " + country + ".");
    }

    private void handleAccept(Player player) {
        InviteManager.Invite invite = plugin.getInviteManager().getInvite(player.getUniqueId());
        if (invite == null) { player.sendMessage("§cУ вас нет активных приглашений."); return; }
        String country = invite.countryName;
        if (!countryManager.countryExists(country)) { player.sendMessage("§cСтрана больше не существует."); plugin.getInviteManager().removeInvite(player.getUniqueId()); return; }
        if (countryManager.getCountryName(player.getUniqueId()) != null) { player.sendMessage("§cУ вас уже есть своя страна."); plugin.getInviteManager().removeInvite(player.getUniqueId()); return; }
        if (countryManager.isCoRuler(player.getUniqueId(), country)) { player.sendMessage("§cВы уже соправитель."); plugin.getInviteManager().removeInvite(player.getUniqueId()); return; }
        countryManager.addCoRuler(country, player.getUniqueId());
        plugin.getInviteManager().removeInvite(player.getUniqueId());
        player.sendMessage("§aВы стали соправителем страны " + country + "!");
        Player inviter = Bukkit.getPlayer(invite.from);
        if (inviter != null) inviter.sendMessage("§aИгрок " + player.getName() + " принял приглашение и стал соправителем.");
    }

    private void handleDecline(Player player) {
        InviteManager.Invite invite = plugin.getInviteManager().getInvite(player.getUniqueId());
        if (invite == null) { player.sendMessage("§cУ вас нет активных приглашений."); return; }
        plugin.getInviteManager().removeInvite(player.getUniqueId());
        player.sendMessage("§cВы отклонили приглашение.");
        Player inviter = Bukkit.getPlayer(invite.from);
        if (inviter != null) inviter.sendMessage("§cИгрок " + player.getName() + " отклонил приглашение.");
    }

    private void handleWithdrawAll(Player player) {
        String country = countryManager.getCountryName(player.getUniqueId());
        if (country == null) { player.sendMessage("§cУ вас нет страны."); return; }
        if (!countryManager.isLeaderOrCoRuler(player.getUniqueId(), country)) { player.sendMessage("§cВы не имеете права управлять банком."); return; }
        double amount = countryManager.withdrawAllFromBank(country);
        if (amount <= 0) { player.sendMessage("§cВ банке нет средств."); return; }
        economyManager.deposit(player, amount);
        player.sendMessage("§aВы сняли все средства из банка: " + economyManager.format(amount));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("reload","create","claim","unclaim","ally","enemy","neutral","map","info","list",
                    "buyenergy","boost","upgrade","bank","rename","autoclaim","unstuck","seechunk","chunkupgrade","miningboost","surrender","top",
                    "achievements","research","court","pact","admin","invite","kick","accept","decline","panel"));
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("bank")) return List.of("deposit","withdraw");
            if (args[0].equalsIgnoreCase("admin")) return List.of("giveenergy","removeenergy","removechunk");
            if (args[0].equalsIgnoreCase("chunkupgrade")) return List.of("normal","farm","mining","military","trade");
            if (args[0].equalsIgnoreCase("pact")) return List.of("trade","military","defense","nonaggression");
            if (args[0].equalsIgnoreCase("top")) return List.of("claims","bank","energy");
            if (args[0].equalsIgnoreCase("court")) return List.of("file","admin");
            if (args[0].equalsIgnoreCase("panel")) return List.of("push","status","prerender");
            if (args[0].equalsIgnoreCase("ally") || args[0].equalsIgnoreCase("enemy") || args[0].equalsIgnoreCase("neutral") ||
                    args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("court")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bank") && args[1].equalsIgnoreCase("withdraw")) {
            return List.of("all");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("panel") && args[1].equalsIgnoreCase("prerender")) {
            return List.of("5", "10", "15", "cancel");
        }
        return List.of();
    }
}
