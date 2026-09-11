package com.example.roll;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class DuelManager {

    private final RollPlugin plugin;
    private final DuelHistory history;

    private Player player = null;
    private double bet = 0;
    private boolean spinning = false;
    private boolean frozen = false;
    private boolean finished = false;
    private boolean win = false;
    private int animationTicks = 0;
    private int animationDuration = 60;
    private static final int FREEZE_DURATION = 20;
    private int freezeTicks = 0;

    private final List<Material> spinSequence = new ArrayList<>();
    private int maxShift = 0;
    private int currentShift = 0;
    private static final int WINDOW_SIZE = 7;

    public DuelManager(RollPlugin plugin) {
        this.plugin = plugin;
        this.history = new DuelHistory(plugin);
    }

    public boolean isActive() { return player != null && !finished; }
    public boolean isSpinning() { return spinning; }
    public boolean isFrozen() { return frozen; }
    public boolean isFinished() { return finished; }
    public boolean getWin() { return win; }
    public Player getPlayer() { return player; }
    public double getBet() { return bet; }
    public double getWinAmount() { return win ? bet * 2 : 0; }
    public DuelHistory getHistory() { return history; }
    public UUID getPlayerUuid() { return player != null ? player.getUniqueId() : null; }

    public List<Material> getVisibleSlots() {
        List<Material> result = new ArrayList<>();
        if (spinSequence.isEmpty()) return result;
        int size = spinSequence.size();
        for (int i = 0; i < WINDOW_SIZE; i++) {
            int idx = (currentShift + i) % size;
            if (idx < 0) idx += size;
            result.add(spinSequence.get(idx));
        }
        return result;
    }

    public void startDuel(Player player, double amount) {
        if (isActive()) {
            player.sendMessage(plugin.getConfig().getString("messages.duel-already", "§cВы уже участвуете в дуэли."));
            return;
        }
        if (amount <= 0) { player.sendMessage("§cСумма должна быть положительной."); return; }
        if (!plugin.getEconomyManager().withdraw(player, amount)) {
            player.sendMessage(plugin.getConfig().getString("messages.not-enough-money", "§cНедостаточно денег.")
                    .replace("%need%", plugin.getEconomyManager().format(amount)));
            return;
        }

        this.player = player;
        this.bet = amount;
        this.spinning = false;
        this.frozen = false;
        this.finished = false;
        this.win = false;
        this.animationTicks = 0;
        this.freezeTicks = 0;
        this.currentShift = 0;

        player.sendMessage(plugin.getConfig().getString("messages.duel-started", "§6Игра 'Дуэль' началась! Ваша ставка: %amount%.")
                .replace("%amount%", plugin.getEconomyManager().format(amount)));

        Bukkit.getScheduler().runTaskLater(plugin, this::startSpin, 20L);
    }

    private void startSpin() {
        if (player == null) return;

        win = new Random().nextBoolean();
        Material winnerMat = win ? Material.EMERALD : Material.REDSTONE;
        Material loserMat = win ? Material.REDSTONE : Material.EMERALD;

        spinSequence.clear();
        Random rand = new Random();
        int sequenceLength = 80 + rand.nextInt(30);

        Material current = rand.nextBoolean() ? Material.EMERALD : Material.REDSTONE;
        for (int i = 0; i < sequenceLength - 1; i++) {
            spinSequence.add(current);
            current = (current == Material.EMERALD) ? Material.REDSTONE : Material.EMERALD;
        }
        if (spinSequence.get(spinSequence.size() - 1) == winnerMat) {
            spinSequence.set(spinSequence.size() - 1, loserMat);
        }
        spinSequence.add(winnerMat);

        maxShift = sequenceLength - 4;

        spinning = true;
        frozen = false;
        finished = false;
        animationTicks = 0;
        freezeTicks = 0;
        currentShift = 0;

        playSound("duel-spin");
        RollDuelGUI.updateAllOpen();
    }

    public void updateAnimation() {
        if (frozen) {
            freezeTicks++;
            if (freezeTicks >= FREEZE_DURATION) {
                frozen = false;
                spinning = false;
                finished = true;
                awardResult();
                RollDuelGUI.updateAllOpen();
            } else {
                RollDuelGUI.updateAllOpen();
            }
            return;
        }
        if (!spinning) return;
        animationTicks++;
        if (animationTicks >= animationDuration) {
            currentShift = maxShift;
            frozen = true;
            freezeTicks = 0;
            RollDuelGUI.updateAllOpen();
            return;
        }
        double progress = (double) animationTicks / animationDuration;
        double eased = 1 - Math.pow(1 - progress, 3);
        currentShift = (int) Math.round(eased * maxShift);
        if (currentShift > maxShift) currentShift = maxShift;
        RollDuelGUI.updateAllOpen();
    }

    private void awardResult() {
        double commissionPercent = plugin.getConfig().getDouble("duel-commission-percent", 5);
        double resultAmount = win ? bet * 2 : 0;

        if (win) {
            plugin.getEconomyManager().deposit(player, resultAmount);
            player.sendMessage(plugin.getConfig().getString("messages.duel-win", "§a🎉 Вы выиграли! %amount% x 2 = %win%!")
                    .replace("%amount%", plugin.getEconomyManager().format(bet))
                    .replace("%win%", plugin.getEconomyManager().format(resultAmount)));
            playSound("duel-win");
            history.addRecord(new DuelHistory.DuelRecord(
                    System.currentTimeMillis(), player.getUniqueId(), bet, true, resultAmount));
            plugin.getRollData().recordResult(player, "duel", bet, resultAmount, true);
        } else {
            double commission = bet * commissionPercent / 100.0;
            if (commission > 0) depositCommission(player.getUniqueId(), commission);
            player.sendMessage(plugin.getConfig().getString("messages.duel-lose", "§cВы проиграли. Потеряно: %amount%.")
                    .replace("%amount%", plugin.getEconomyManager().format(bet)));
            playSound("duel-lose");
            history.addRecord(new DuelHistory.DuelRecord(
                    System.currentTimeMillis(), player.getUniqueId(), bet, false, 0));
            plugin.getRollData().recordResult(player, "duel", bet, 0, false);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reset();
            RollDuelGUI.updateAllOpen();
        }, 60L);
    }

    private void playSound(String type) {
        String soundName = plugin.getConfig().getString("sounds." + type);
        if (soundName == null) return;
        try {
            Sound sound = Sound.valueOf(soundName);
            if (player != null && player.isOnline()) player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    private void depositCommission(UUID playerUuid, double amount) {
        try {
            Object sovereignty = Bukkit.getPluginManager().getPlugin("Sovereignty");
            if (sovereignty != null) {
                Object cm = sovereignty.getClass().getMethod("getCountryManager").invoke(sovereignty);
                String cName = (String) cm.getClass().getMethod("getCountryName", UUID.class).invoke(cm, playerUuid);
                if (cName != null) {
                    cm.getClass().getMethod("depositToBank", String.class, double.class).invoke(cm, cName, amount);
                }
            }
        } catch (Exception ignored) {}
    }

    public void cancel() {
        if (player != null && !finished) {
            plugin.getEconomyManager().deposit(player, bet);
            player.sendMessage("§cДуэль отменена, деньги возвращены.");
        }
        reset();
    }

    private void reset() {
        player = null;
        bet = 0;
        spinning = false;
        frozen = false;
        finished = false;
        win = false;
        animationTicks = 0;
        freezeTicks = 0;
        currentShift = 0;
        maxShift = 0;
        spinSequence.clear();
        RollDuelGUI.updateAllOpen();
    }
}
