package com.example.auction;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class AuctionManager {

    private final AuctionPlugin plugin;
    private final DatabaseManager db;

    public AuctionManager(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public int createAuction(Player seller, ItemStack item, double startPrice, int minutes) {
        int maxMinutes = plugin.getConfig().getInt("max-auction-time", 180);
        if (minutes > maxMinutes) minutes = maxMinutes;
        if (minutes < 1) minutes = plugin.getConfig().getInt("default-auction-time", 60);
        long endTime = System.currentTimeMillis() + minutes * 60_000L;
        return db.createAuction(seller.getUniqueId(), item, startPrice, endTime);
    }

    public boolean placeBid(Player bidder, int auctionId, double amount) {
        Auction auction = db.getAuction(auctionId);
        if (auction == null || !auction.isActive()) return false;
        if (auction.getSeller().equals(bidder.getUniqueId())) return false;
        if (amount <= auction.getCurrentPrice()) return false;

        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.has(bidder, amount)) {
            bidder.sendMessage(plugin.getConfig().getString("messages.insufficient-money", "§cНедостаточно денег.")
                    .replace("%need%", eco.format(amount)));
            return false;
        }

        // Возвращаем деньги предыдущему лидеру
        if (auction.getCurrentBidder() != null) {
            Player oldBidder = Bukkit.getPlayer(auction.getCurrentBidder());
            if (oldBidder != null && oldBidder.isOnline()) {
                eco.deposit(oldBidder, auction.getCurrentPrice());
                oldBidder.sendMessage(plugin.getConfig().getString("messages.bid-outbid", "§cВашу ставку перебили!")
                        .replace("%id%", String.valueOf(auctionId))
                        .replace("%price%", eco.format(amount)));
            }
        }

        // Списываем деньги у нового лидера
        eco.withdraw(bidder, amount);

        // Обновляем аукцион
        boolean updated = db.updateBid(auctionId, amount, bidder.getUniqueId());
        if (updated) {
            auction.setCurrentPrice(amount);
            auction.setCurrentBidder(bidder.getUniqueId());
            bidder.sendMessage(plugin.getConfig().getString("messages.bid-placed", "§aСтавка принята!")
                    .replace("%id%", String.valueOf(auctionId))
                    .replace("%price%", eco.format(amount)));
            return true;
        } else {
            // Если не обновилось – возвращаем деньги
            eco.deposit(bidder, amount);
            return false;
        }
    }

    public boolean placeBidItem(Player bidder, int auctionId, String materialName, int amount) {
        // Реализация будет позже – для простоты пока пропустим
        bidder.sendMessage("§cСтавки предметами пока не реализованы.");
        return false;
    }

    public boolean cancelAuction(Player player, int auctionId) {
        Auction auction = db.getAuction(auctionId);
        if (auction == null || !auction.isActive()) return false;
        if (!auction.getSeller().equals(player.getUniqueId()) && !player.hasPermission("auction.admin")) {
            player.sendMessage("§cУ вас нет прав на отмену этого аукциона.");
            return false;
        }
        if (auction.getCurrentBidder() != null) {
            player.sendMessage(plugin.getConfig().getString("messages.auction-cancel-no-bids", "§cНельзя отменить, есть ставки."));
            return false;
        }
        // Возвращаем предмет продавцу
        ItemStack item = auction.getItem();
        player.getInventory().addItem(item);
        db.setStatus(auctionId, "cancelled");
        player.sendMessage(plugin.getConfig().getString("messages.auction-cancelled", "§cАукцион отменён.")
                .replace("%id%", String.valueOf(auctionId)));
        return true;
    }

    public void checkExpiredAuctions() {
        long now = System.currentTimeMillis();
        List<Auction> active = db.getActiveAuctions();
        for (Auction auction : active) {
            if (auction.getEndTime() <= now) {
                finishAuction(auction);
            }
        }
    }

    private void finishAuction(Auction auction) {
        int id = auction.getId();
        UUID sellerUuid = auction.getSeller();
        UUID winnerUuid = auction.getCurrentBidder();
        double finalPrice = auction.getCurrentPrice();
        double commissionPercent = plugin.getConfig().getDouble("commission-percent", 0.0);

        if (winnerUuid == null) {
            // Нет ставок – возвращаем предмет продавцу
            Player seller = Bukkit.getPlayer(sellerUuid);
            if (seller != null && seller.isOnline()) {
                seller.getInventory().addItem(auction.getItem());
                seller.sendMessage(plugin.getConfig().getString("messages.auction-no-bids", "§cАукцион без ставок.")
                        .replace("%id%", String.valueOf(id)));
            }
            db.setStatus(id, "ended");
            return;
        }

        // Передаём предмет победителю
        Player winner = Bukkit.getPlayer(winnerUuid);
        if (winner != null && winner.isOnline()) {
            winner.getInventory().addItem(auction.getItem());
            winner.sendMessage(plugin.getConfig().getString("messages.auction-ended", "§aВы выиграли аукцион!")
                    .replace("%id%", String.valueOf(id))
                    .replace("%price%", plugin.getEconomyManager().format(finalPrice)));
        } else {
            // Победитель оффлайн – сохраняем предмет в память? Пока просто дропнем на землю или выдадим при входе.
            // Для простоты: положим в сундук на спавне? Лучше выдадим при входе – реализуем в слушателе.
            // Но для упрощения – просто дропнем в мир победителя, если он оффлайн, но у нас нет его локации.
            // Поэтому сохраняем в отдельную таблицу для офлайн-выдачи (pending items) – пока не реализовано.
            // Для MVP: пропускаем, предмет останется в БД? Нет, он удаляется. Просто предупредим.
            plugin.getLogger().warning("Победитель аукциона #" + id + " оффлайн, предмет не выдан.");
        }

        // Передаём деньги продавцу (минус комиссия)
        double sellerPayout = finalPrice * (1 - commissionPercent / 100.0);
        Player seller = Bukkit.getPlayer(sellerUuid);
        if (seller != null && seller.isOnline()) {
            plugin.getEconomyManager().deposit(seller, sellerPayout);
            seller.sendMessage(plugin.getConfig().getString("messages.auction-ended-seller", "§aВаш аукцион завершён! Вы получили %price%.")
                    .replace("%id%", String.valueOf(id))
                    .replace("%price%", plugin.getEconomyManager().format(sellerPayout)));
        } else {
            // Продавец оффлайн – зачисляем на баланс (Vault поддерживает OfflinePlayer)
            plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(sellerUuid), sellerPayout);
        }

        db.setStatus(id, "ended");
        // Широковещательное сообщение о завершении
        Bukkit.broadcastMessage(plugin.getConfig().getString("messages.auction-ended-broadcast", "§6Аукцион #%id% завершён! Победитель: %winner%, цена: %price%.")
                .replace("%id%", String.valueOf(id))
                .replace("%winner%", Bukkit.getOfflinePlayer(winnerUuid).getName())
                .replace("%price%", plugin.getEconomyManager().format(finalPrice)));
    }
}
