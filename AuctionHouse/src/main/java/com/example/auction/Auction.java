package com.example.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Auction {
    private final int id;
    private final UUID seller;
    private final ItemStack item;
    private final double startPrice;
    private double currentPrice;
    private UUID currentBidder;
    private final long endTime;
    private String status;

    public Auction(int id, UUID seller, ItemStack item, double startPrice, double currentPrice,
                   UUID currentBidder, long endTime, String status) {
        this.id = id;
        this.seller = seller;
        this.item = item;
        this.startPrice = startPrice;
        this.currentPrice = currentPrice;
        this.currentBidder = currentBidder;
        this.endTime = endTime;
        this.status = status;
    }

    public int getId() { return id; }
    public UUID getSeller() { return seller; }
    public ItemStack getItem() { return item.clone(); }
    public double getStartPrice() { return startPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
    public UUID getCurrentBidder() { return currentBidder; }
    public void setCurrentBidder(UUID currentBidder) { this.currentBidder = currentBidder; }
    public long getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isActive() { return "active".equals(status); }
}
