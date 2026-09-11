package com.example.roll;

import java.util.*;

public class PokerTable {

    public enum Stage {
        WAITING, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, FINISHED
    }

    public static class Player {
        public final UUID uuid;
        public final String name;
        public long chips;
        public long contributed;
        public long roundBet;
        public List<PokerHand.Card> hole = new ArrayList<>();
        public boolean folded = false;
        public boolean allIn = false;
        public boolean hasActedThisRound = false;
        public long lastActionTime;

        public Player(UUID uuid, String name, long buyIn) {
            this.uuid = uuid;
            this.name = name;
            this.chips = buyIn;
        }
    }

    public final int id;
    public final long buyIn;
    public Stage stage = Stage.WAITING;
    public final Map<UUID, Player> players = new LinkedHashMap<>();
    public final List<PokerHand.Card> community = new ArrayList<>();
    public long pot = 0;
    public long highestBet = 0;
    public int dealerIndex = -1;
    public UUID currentTurn = null;
    public UUID lastAggressor = null;
    public int smallBlind = 50;
    public int bigBlind = 100;
    public long handStartTime = 0;
    public long turnStartTime = 0;
    public int handNumber = 0;
    public boolean dealing = false;
    public int dealtCards = 0;
    public int revealingIndex = -1;
    public final Map<UUID, Long> chipsAtHandStart = new HashMap<>();

    public PokerTable(int id, long buyIn) {
        this.id = id;
        this.buyIn = buyIn;
        this.smallBlind = (int) Math.max(1, buyIn / 100);
        this.bigBlind = smallBlind * 2;
    }

    public Player getPlayer(UUID uuid) { return players.get(uuid); }
    public List<Player> orderedPlayers() { return new ArrayList<>(players.values()); }

    public Player nextActivePlayer(int startIndex) {
        List<Player> list = orderedPlayers();
        if (list.isEmpty()) return null;
        int n = list.size();
        for (int i = 1; i <= n; i++) {
            int idx = (startIndex + i) % n;
            Player p = list.get(idx);
            if (!p.folded && !p.allIn) return p;
        }
        return null;
    }

    public int indexOf(UUID uuid) {
        List<Player> list = orderedPlayers();
        for (int i = 0; i < list.size(); i++) if (list.get(i).uuid.equals(uuid)) return i;
        return -1;
    }

    public int activePlayerCount() {
        int c = 0;
        for (Player p : players.values()) if (!p.folded) c++;
        return c;
    }

    public int bettingPlayerCount() {
        int c = 0;
        for (Player p : players.values()) if (!p.folded && !p.allIn) c++;
        return c;
    }
}
