package com.example.roll;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class PokerHand {

    public static class Card {
        public final int rank;
        public final int suit;

        public Card(int rank, int suit) {
            this.rank = rank;
            this.suit = suit;
        }

        public String rankName() {
            return switch (rank) {
                case 11 -> "J";
                case 12 -> "Q";
                case 13 -> "K";
                case 14 -> "A";
                default -> String.valueOf(rank);
            };
        }

        public String suitSymbol() {
            return switch (suit) {
                case 0 -> "♠";
                case 1 -> "♥";
                case 2 -> "♦";
                default -> "♣";
            };
        }

        public boolean isRed() { return suit == 1 || suit == 2; }

        public String display() {
            return (isRed() ? "§c" : "§f") + suitSymbol() + "§f " + rankName();
        }

        public ItemStack toItem() {
            Material mat = switch (suit) {
                case 0 -> Material.WHITE_DYE;
                case 1 -> Material.RED_DYE;
                case 2 -> Material.ORANGE_DYE;
                default -> Material.BLACK_DYE;
            };
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(display());
            List<String> lore = new ArrayList<>();
            lore.add("§7Масть: §f" + suitName());
            lore.add("§7Значение: §f" + rankName());
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }

        public String suitName() {
            return switch (suit) {
                case 0 -> "♠ пики";
                case 1 -> "♥ черви";
                case 2 -> "♦ бубны";
                default -> "♣ трефы";
            };
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Card c)) return false;
            return rank == c.rank && suit == c.suit;
        }
        @Override public int hashCode() { return Objects.hash(rank, suit); }
    }

    public static List<Card> newDeck() {
        List<Card> deck = new ArrayList<>();
        for (int suit = 0; suit < 4; suit++)
            for (int rank = 2; rank <= 14; rank++)
                deck.add(new Card(rank, suit));
        Collections.shuffle(deck);
        return deck;
    }

    public static int[] evaluate5(List<Card> hand) {
        int[] ranks = hand.stream().mapToInt(c -> c.rank).sorted().toArray();
        int[] r = new int[5];
        for (int i = 0; i < 5; i++) r[i] = ranks[4 - i];

        boolean flush = hand.stream().map(c -> c.suit).distinct().count() == 1;
        boolean wheel = ranks[0] == 2 && ranks[1] == 3 && ranks[2] == 4 && ranks[3] == 5 && ranks[4] == 14;
        boolean straight = wheel;
        if (!straight) {
            straight = true;
            for (int i = 1; i < 5; i++) if (ranks[i] != ranks[i - 1] + 1) { straight = false; break; }
        }
        int straightHigh = wheel ? 5 : r[0];

        Map<Integer, Integer> counts = new HashMap<>();
        for (int rank : ranks) counts.merge(rank, 1, Integer::sum);

        List<int[]> groups = counts.entrySet().stream()
                .map(e -> new int[]{e.getKey(), e.getValue()})
                .sorted((a, b) -> a[1] != b[1] ? b[1] - a[1] : b[0] - a[0])
                .collect(Collectors.toList());

        if (straight && flush) {
            if (straightHigh == 14) return new int[]{9, 14};
            return new int[]{8, straightHigh};
        }
        if (groups.get(0)[1] == 4) return new int[]{7, groups.get(0)[0], groups.get(1)[0]};
        if (groups.get(0)[1] == 3 && groups.get(1)[1] == 2) return new int[]{6, groups.get(0)[0], groups.get(1)[0]};
        if (flush) return new int[]{5, r[0], r[1], r[2], r[3], r[4]};
        if (straight) return new int[]{4, straightHigh};
        if (groups.get(0)[1] == 3) {
            int k1 = Math.max(groups.get(1)[0], groups.get(2)[0]);
            int k2 = Math.min(groups.get(1)[0], groups.get(2)[0]);
            return new int[]{3, groups.get(0)[0], k1, k2};
        }
        if (groups.get(0)[1] == 2 && groups.get(1)[1] == 2) {
            int hi = Math.max(groups.get(0)[0], groups.get(1)[0]);
            int lo = Math.min(groups.get(0)[0], groups.get(1)[0]);
            return new int[]{2, hi, lo, groups.get(2)[0]};
        }
        if (groups.get(0)[1] == 2) {
            int[] k = new int[3];
            int idx = 0;
            for (int i = 1; i < groups.size(); i++) k[idx++] = groups.get(i)[0];
            Arrays.sort(k);
            return new int[]{1, groups.get(0)[0], k[2], k[1], k[0]};
        }
        return new int[]{0, r[0], r[1], r[2], r[3], r[4]};
    }

    public static int[] evaluateBest(List<Card> seven) {
        if (seven.size() < 5) return new int[]{-1};
        int[] best = null;
        int n = seven.size();
        for (int a = 0; a < n - 4; a++)
            for (int b = a + 1; b < n - 3; b++)
                for (int c = b + 1; c < n - 2; c++)
                    for (int d = c + 1; d < n - 1; d++)
                        for (int e = d + 1; e < n; e++) {
                            List<Card> five = List.of(seven.get(a), seven.get(b), seven.get(c), seven.get(d), seven.get(e));
                            int[] ev = evaluate5(five);
                            if (best == null || compare(ev, best) > 0) best = ev;
                        }
        return best != null ? best : new int[]{-1};
    }

    public static int compare(int[] a, int[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return Integer.compare(a.length, b.length);
    }

    public static String handName(int[] eval) {
        if (eval.length == 0) return "???";
        return switch (eval[0]) {
            case 9 -> "Флеш-рояль";
            case 8 -> "Стрит-флеш";
            case 7 -> "Каре";
            case 6 -> "Фулл-хаус";
            case 5 -> "Флеш";
            case 4 -> "Стрит";
            case 3 -> "Сет";
            case 2 -> "Две пары";
            case 1 -> "Пара";
            case 0 -> "Старшая карта";
            default -> "???";
        };
    }
}
