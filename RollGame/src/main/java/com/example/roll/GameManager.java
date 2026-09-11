package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {

    private final RollPlugin plugin;
    private final File dataFile;

    private boolean active = false;
    private final Map<UUID, Double> bets = new ConcurrentHashMap<>();
    private long startTime = 0;
    private BukkitRunnable timerTask;
    private BukkitRunnable countdownTask;
    private UUID firstBetter = null;

    private boolean spinning = false;
    private boolean frozen = false;
    private boolean finished = false;
    private UUID winner = null;
    private int animationTicks = 0;
    private int animationDuration = 60;

    private final List<UUID> spinSequence = new ArrayList<>();
    private int maxShift = 0;
    private int currentShift = 0;
    private static final int WINDOW_SIZE = 9;

    private int freezeTicks = 0;
    private static final int FREEZE_DURATION = 20;

    private final Map<UUID, Material> playerItems = new HashMap<>();
    private final RollHistory history;

    private int waitSeconds;
    private static final DecimalFormat CHANCE_FORMAT = new DecimalFormat("#0.00");

    public GameManager(RollPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "game.yml");
        this.waitSeconds = plugin.getConfig().getInt("wait-time-seconds", 300);
        this.animationDuration = plugin.getConfig().getInt("animation-duration-ticks", 60);
        this.history = new RollHistory(plugin);
        loadGame();
    }

    public boolean isActive() { return active; }
    public boolean isSpinning() { return spinning; }
    public boolean isFrozen() { return frozen; }
    public boolean isFinished() { return finished; }
    public UUID getWinner() { return winner; }
    public UUID getFirstBetter() { return firstBetter; }
    public Map<UUID, Double> getBets() { return new HashMap<>(bets); }
    public double getTotalPool() {
        return bets.values().stream().mapToDouble(Double::doubleValue).sum();
    }
    public int getBetCount() { return bets.size(); }
    public long getStartTime() { return startTime; }
    public boolean hasBet(UUID uuid) { return bets.containsKey(uuid); }
    public Map<UUID, Material> getPlayerItems() { return new HashMap<>(playerItems); }
    public RollHistory getHistory() { return history; }

    public double getChance(UUID uuid) {
        double total = getTotalPool();
        if (total <= 0) return 0.0;
        Double bet = bets.get(uuid);
        if (bet == null) return 0.0;
        return bet / total * 100.0;
    }

    public String getChanceFormatted(UUID uuid) {
        return CHANCE_FORMAT.format(getChance(uuid)) + "%";
    }

    public List<UUID> getVisibleSlots() {
        List<UUID> result = new ArrayList<>();
        if (spinSequence.isEmpty()) return result;
        int size = spinSequence.size();
        for (int i = 0; i < WINDOW_SIZE; i++) {
            int idx = (currentShift + i) % size;
            if (idx < 0) idx += size;
            result.add(spinSequence.get(idx));
        }
        return result;
    }

    /**
     * Новая ставка от игрока (первая ставка или игрок не участвовал).
     */
    public void addBet(Player player, double amount) {
        if (active && bets.containsKey(player.getUniqueId())) {
            player.sendMessage("§cВы уже сделали ставку. Используйте §f/roll raise <сумма>§c, чтобы увеличить её.");
            return;
        }
        if (spinning || frozen || finished) {
            player.sendMessage("§cИгра уже завершается, ставки не принимаются.");
            return;
        }

        double minBet = plugin.getConfig().getDouble("min-bet", 0);
        double maxBet = plugin.getConfig().getDouble("max-bet", 0);
        if (minBet > 0 && amount < minBet) {
            player.sendMessage("§cМинимальная ставка: " + plugin.getEconomyManager().format(minBet));
            return;
        }
        if (maxBet > 0 && amount > maxBet) {
            player.sendMessage("§cМаксимальная ставка: " + plugin.getEconomyManager().format(maxBet));
            return;
        }

        if (!plugin.getEconomyManager().withdraw(player, amount)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(amount)));
            return;
        }

        if (!active) {
            active = true;
            startTime = System.currentTimeMillis();
            firstBetter = player.getUniqueId();
            startTimer();
            startCountdown();
            plugin.getLogger().info("Игра начата первой ставкой от " + player.getName());
            Bukkit.broadcastMessage(plugin.getConfig().getString("messages.game-started", "§6Игра началась!")
                    .replace("%player%", player.getName()));
        }

        bets.put(player.getUniqueId(), amount);
        if (!playerItems.containsKey(player.getUniqueId())) {
            Random rand = new Random();
            List<Material> pool = getItemPool();
            Material randomMat = pool.get(rand.nextInt(pool.size()));
            playerItems.put(player.getUniqueId(), randomMat);
        }
        saveGame();

        double total = getTotalPool();
        String chanceStr = CHANCE_FORMAT.format(getChance(player.getUniqueId())) + "%";

        Bukkit.broadcastMessage("§aИгрок §f" + player.getName() + " §aпоставил §f" +
                plugin.getEconomyManager().format(amount) +
                "§a! Общий банк: §f" + plugin.getEconomyManager().format(total) +
                "§a. Шанс выиграть: §e" + chanceStr);

        RollGUI.updateAllOpen();
    }

    /**
     * Увеличить свою существующую ставку.
     */
    public void addToBet(Player player, double amount) {
        if (!active) {
            player.sendMessage("§cИгра не активна. Сделайте первую ставку через §f/roll bet <сумма>§c.");
            return;
        }
        if (spinning || frozen || finished) {
            player.sendMessage("§cИгра уже завершается, ставки не принимаются.");
            return;
        }
        if (!bets.containsKey(player.getUniqueId())) {
            player.sendMessage("§cУ вас нет ставки. Сначала сделайте ставку через §f/roll bet <сумма>§c.");
            return;
        }
        if (amount <= 0) {
            player.sendMessage("§cСумма должна быть положительной.");
            return;
        }

        double maxBet = plugin.getConfig().getDouble("max-bet", 0);
        double newTotal = bets.get(player.getUniqueId()) + amount;
        if (maxBet > 0 && newTotal > maxBet) {
            player.sendMessage("§cМаксимальная общая ставка: " + plugin.getEconomyManager().format(maxBet));
            return;
        }

        if (!plugin.getEconomyManager().withdraw(player, amount)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(amount)));
            return;
        }

        bets.put(player.getUniqueId(), newTotal);
        saveGame();

        double total = getTotalPool();
        String chanceStr = CHANCE_FORMAT.format(getChance(player.getUniqueId())) + "%";

        Bukkit.broadcastMessage("§aИгрок §f" + player.getName() + " §aувеличил ставку на §f" +
                plugin.getEconomyManager().format(amount) +
                "§a! Теперь его ставка: §f" + plugin.getEconomyManager().format(newTotal) +
                "§a. Общий банк: §f" + plugin.getEconomyManager().format(total) +
                "§a. Новый шанс: §e" + chanceStr);

        RollGUI.updateAllOpen();
    }

    private List<Material> getItemPool() {
        return List.of(
                Material.DIAMOND,
                Material.EMERALD,
                Material.NETHERITE_INGOT,
                Material.GOLDEN_APPLE,
                Material.ENCHANTED_GOLDEN_APPLE,
                Material.TOTEM_OF_UNDYING,
                Material.ELYTRA,
                Material.DRAGON_EGG,
                Material.NETHER_STAR,
                Material.BEACON,
                Material.CONDUIT,
                Material.HEART_OF_THE_SEA,
                Material.NAUTILUS_SHELL,
                Material.SHULKER_SHELL,
                Material.END_CRYSTAL
        );
    }

    private void startTimer() {
        if (timerTask != null) timerTask.cancel();
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                finishGame();
            }
        };
        timerTask.runTaskLater(plugin, waitSeconds * 20L);
    }

    private void startCountdown() {
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = new BukkitRunnable() {
            int secondsLeft = waitSeconds;
            @Override
            public void run() {
                if (!active || spinning || frozen || finished) {
                    this.cancel();
                    return;
                }
                secondsLeft--;
                if (secondsLeft % 10 == 0 || secondsLeft <= 5) {
                    Bukkit.broadcastMessage(plugin.getConfig().getString("messages.time-left", "§eОсталось времени: %time% сек.")
                            .replace("%time%", String.valueOf(secondsLeft)));
                }
                if (secondsLeft <= 0) {
                    this.cancel();
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void finishGame() {
        if (!active) return;
        int minBets = plugin.getConfig().getInt("min-bets", 2);
        if (bets.size() < minBets) {
            cancelGame(true);
            return;
        }
        startSpin();
    }

    public void forceFinish(Player player) {
        if (!active) {
            player.sendMessage("§cИгра не активна.");
            return;
        }
        if (spinning || frozen || finished) {
            player.sendMessage("§cИгра уже завершается или завершена.");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!uuid.equals(firstBetter) && !player.hasPermission("roll.admin")) {
            player.sendMessage("§cТолько первый поставивший может завершить игру досрочно.");
            return;
        }
        int minBets = plugin.getConfig().getInt("min-bets", 2);
        if (bets.size() < minBets) {
            cancelGame(true);
            player.sendMessage("§aИгра отменена (недостаточно ставок).");
        } else {
            startSpin();
            player.sendMessage("§aИгра завершается досрочно!");
        }
    }

    private void startSpin() {
        winner = selectWinner();
        if (winner == null) {
            cancelGame(true);
            return;
        }

        buildSpinSequence();

        spinning = true;
        frozen = false;
        finished = false;
        animationTicks = 0;
        currentShift = 0;
        freezeTicks = 0;

        playSound("spin-start");
        Bukkit.broadcastMessage("§6Колесо вращается! Определяем победителя...");
        RollGUI.updateAllOpen();
        saveGame();
    }

    private void buildSpinSequence() {
        spinSequence.clear();
        double total = getTotalPool();
        Random rand = new Random();
        int sequenceLength = 80 + rand.nextInt(30);

        for (int i = 0; i < sequenceLength - 1; i++) {
            spinSequence.add(weightedPick(rand, total));
        }

        if (!spinSequence.isEmpty() && spinSequence.get(spinSequence.size() - 1).equals(winner)) {
            UUID replacement = null;
            for (UUID p : bets.keySet()) {
                if (!p.equals(winner)) {
                    replacement = p;
                    break;
                }
            }
            if (replacement != null) {
                spinSequence.set(spinSequence.size() - 1, replacement);
            }
        }
        spinSequence.add(winner);

        maxShift = sequenceLength - 5;
    }

    private UUID weightedPick(Random rand, double total) {
        double roll = rand.nextDouble() * total;
        double cumulative = 0;
        for (Map.Entry<UUID, Double> entry : bets.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) return entry.getKey();
        }
        return bets.keySet().iterator().next();
    }

    public void updateAnimation() {
        if (frozen) {
            freezeTicks++;
            if (freezeTicks >= FREEZE_DURATION) {
                frozen = false;
                spinning = false;
                finished = true;
                awardWinner();
                RollGUI.updateAllOpen();
            } else {
                RollGUI.updateAllOpen();
            }
            return;
        }

        if (!spinning) return;
        animationTicks++;

        if (animationTicks >= animationDuration) {
            currentShift = maxShift;
            frozen = true;
            freezeTicks = 0;
            RollGUI.updateAllOpen();
            return;
        }

        double progress = (double) animationTicks / animationDuration;
        double eased = 1 - Math.pow(1 - progress, 3);
        currentShift = (int) Math.round(eased * maxShift);
        if (currentShift > maxShift) currentShift = maxShift;

        RollGUI.updateAllOpen();
    }

    private void awardWinner() {
        double total = getTotalPool();
        double commissionPercent = plugin.getConfig().getDouble("commission-percent", 0);
        double commission = total * commissionPercent / 100.0;
        double winnerAmount = total - commission;

        Player winnerPlayer = Bukkit.getPlayer(winner);
        if (winnerPlayer != null && winnerPlayer.isOnline()) {
            plugin.getEconomyManager().deposit(winnerPlayer, winnerAmount);
            if (commission > 0) depositCommission(winner, commission);
            Bukkit.broadcastMessage(plugin.getConfig().getString("messages.game-won", "§a🎉 Победитель: %player%! Выигрыш: %amount%! 🎉")
                    .replace("%player%", winnerPlayer.getName())
                    .replace("%amount%", plugin.getEconomyManager().format(winnerAmount)));
            playSound("win");
        } else {
            plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(winner), winnerAmount);
            if (commission > 0) depositCommission(winner, commission);
            Bukkit.broadcastMessage(plugin.getConfig().getString("messages.game-won", "§a🎉 Победитель: %player%! Выигрыш: %amount%! 🎉")
                    .replace("%player%", Bukkit.getOfflinePlayer(winner).getName())
                    .replace("%amount%", plugin.getEconomyManager().format(winnerAmount)));
            playSound("win");
        }

        history.addRecord(new RollHistory.GameRecord(
                System.currentTimeMillis(),
                winner,
                total,
                new HashMap<>(bets)
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetGame();
            RollGUI.updateAllOpen();
        }, 100L);
        saveGame();
    }

    private void playSound(String type) {
        String soundName = plugin.getConfig().getString("sounds." + type);
        if (soundName == null) return;
        try {
            Sound sound = Sound.valueOf(soundName);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        } catch (IllegalArgumentException ignored) {}
    }

    private void depositCommission(UUID winnerUuid, double amount) {
        try {
            Object sovereignty = Bukkit.getPluginManager().getPlugin("Sovereignty");
            if (sovereignty != null) {
                Object countryManager = sovereignty.getClass().getMethod("getCountryManager").invoke(sovereignty);
                String countryName = (String) countryManager.getClass().getMethod("getCountryName", UUID.class).invoke(countryManager, winnerUuid);
                if (countryName != null) {
                    countryManager.getClass().getMethod("depositToBank", String.class, double.class)
                            .invoke(countryManager, countryName, amount);
                }
            }
        } catch (Exception ignored) {}
    }

    private UUID selectWinner() {
        if (bets.isEmpty()) return null;
        List<UUID> weightedList = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : bets.entrySet()) {
            int weight = (int) Math.round(entry.getValue());
            for (int i = 0; i < weight; i++) {
                weightedList.add(entry.getKey());
            }
        }
        if (weightedList.isEmpty()) return null;
        Random rand = new Random();
        return weightedList.get(rand.nextInt(weightedList.size()));
    }

    public void cancelGame(boolean refund) {
        if (!active) return;
        if (refund) {
            for (Map.Entry<UUID, Double> entry : bets.entrySet()) {
                UUID uuid = entry.getKey();
                double amount = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getEconomyManager().deposit(player, amount);
                    player.sendMessage(plugin.getConfig().getString("messages.bet-cancelled", "§cВаша ставка отменена."));
                } else {
                    plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(uuid), amount);
                }
            }
            Bukkit.broadcastMessage(plugin.getConfig().getString("messages.game-cancelled", "§cИгра отменена.")
                    .replace("%time%", String.valueOf(waitSeconds)));
        } else {
            Bukkit.broadcastMessage(plugin.getConfig().getString("messages.admin-cancel", "§cИгра отменена администратором."));
        }
        resetGame();
    }

    private void resetGame() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        active = false;
        spinning = false;
        frozen = false;
        finished = false;
        bets.clear();
        playerItems.clear();
        spinSequence.clear();
        startTime = 0;
        winner = null;
        firstBetter = null;
        currentShift = 0;
        maxShift = 0;
        animationTicks = 0;
        freezeTicks = 0;
        saveGame();
        RollGUI.updateAllOpen();
    }

    public void saveGame() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", active);
        yaml.set("startTime", startTime);
        yaml.set("spinning", spinning);
        yaml.set("frozen", frozen);
        yaml.set("finished", finished);
        yaml.set("winner", winner != null ? winner.toString() : null);
        yaml.set("firstBetter", firstBetter != null ? firstBetter.toString() : null);
        yaml.set("animationTicks", animationTicks);
        yaml.set("currentShift", currentShift);
        yaml.set("maxShift", maxShift);
        for (Map.Entry<UUID, Double> entry : bets.entrySet()) {
            yaml.set("bets." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Material> entry : playerItems.entrySet()) {
            yaml.set("items." + entry.getKey().toString(), entry.getValue().name());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить игру: " + e.getMessage());
        }
    }

    public void loadGame() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        boolean savedActive = yaml.getBoolean("active", false);
        if (!savedActive) return;

        if (yaml.contains("bets")) {
            for (String key : yaml.getConfigurationSection("bets").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double amount = yaml.getDouble("bets." + key);
                    bets.put(uuid, amount);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (yaml.contains("items")) {
            for (String key : yaml.getConfigurationSection("items").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String matName = yaml.getString("items." + key);
                    Material mat = Material.matchMaterial(matName);
                    if (mat != null) playerItems.put(uuid, mat);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        startTime = yaml.getLong("startTime", 0L);
        spinning = yaml.getBoolean("spinning", false);
        frozen = yaml.getBoolean("frozen", false);
        finished = yaml.getBoolean("finished", false);
        animationTicks = yaml.getInt("animationTicks", 0);
        currentShift = yaml.getInt("currentShift", 0);
        maxShift = yaml.getInt("maxShift", 0);
        String firstStr = yaml.getString("firstBetter");
        if (firstStr != null) {
            try { firstBetter = UUID.fromString(firstStr); } catch (IllegalArgumentException ignored) {}
        }
        String winnerStr = yaml.getString("winner");
        if (winnerStr != null) {
            try { winner = UUID.fromString(winnerStr); } catch (IllegalArgumentException ignored) {}
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed < waitSeconds * 1000L && !spinning && !frozen) {
            active = true;
            long remaining = waitSeconds * 1000L - elapsed;
            int remainingTicks = (int) (remaining / 50);
            if (remainingTicks > 0) {
                startTimer();
                startCountdown();
            } else {
                finishGame();
            }
        } else if (spinning || frozen) {
            active = true;
            if (spinSequence.isEmpty() && winner != null) {
                buildSpinSequence();
            }
        } else {
            finishGame();
        }
    }
}
