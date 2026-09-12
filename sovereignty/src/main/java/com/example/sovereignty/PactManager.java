package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.*;

public class PactManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;

    public PactManager(SovereigntyPlugin plugin, DatabaseManager db, CountryManager countryManager) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = countryManager;
    }

    public boolean hasPact(String a, String b, String type) {
        String sql = "SELECT 1 FROM pacts WHERE ((country_a=? AND country_b=?) OR (country_a=? AND country_b=?)) AND pact_type=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, b);
            ps.setString(4, a);
            ps.setString(5, type);
            return ps.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean addPact(String a, String b, String type) {
        if (hasPact(a, b, type)) return false;
        String sql = "INSERT OR IGNORE INTO pacts(country_a, country_b, pact_type) VALUES(?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, type);
            ps.executeUpdate();
            if (plugin.getWebPanelUploader() != null) plugin.getWebPanelUploader().scheduleForcePush();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public void removePact(String a, String b, String type) {
        String sql = "DELETE FROM pacts WHERE ((country_a=? AND country_b=?) OR (country_a=? AND country_b=?)) AND pact_type=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, b);
            ps.setString(4, a);
            ps.setString(5, type);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Пакт о ненападении
    public boolean hasNonAggressionPact(String a, String b) {
        return hasPact(a, b, "nonaggression");
    }

    public boolean addNonAggressionPact(String a, String b) {
        return addPact(a, b, "nonaggression");
    }

    public void removeNonAggressionPact(String a, String b) {
        removePact(a, b, "nonaggression");
    }

    public boolean declareWar(String attacker, String defender) {
        String sql = "INSERT OR REPLACE INTO wars(attacker, defender, started_at) VALUES(?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, attacker);
            ps.setString(2, defender);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            if (plugin.getWebPanelUploader() != null) plugin.getWebPanelUploader().scheduleForcePush();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean isAtWar(String a, String b) {
        String sql = "SELECT 1 FROM wars WHERE (attacker=? AND defender=?) OR (attacker=? AND defender=?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, b);
            ps.setString(4, a);
            return ps.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public String getAttacker(String defender) {
        String sql = "SELECT attacker FROM wars WHERE defender=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, defender);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("attacker");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean surrender(Player defenderPlayer) {
        String defender = countryManager.getCountryName(defenderPlayer.getUniqueId());
        if (defender == null) return false;
        String attacker = getAttacker(defender);
        if (attacker == null) return false;

        double percent = plugin.getConfig().getDouble("reparations-percent", 20.0);
        double treasuryBalance = countryManager.getBankBalance(defender);
        double reparations = treasuryBalance * (percent / 100.0);

        double fromTreasury = Math.min(reparations, treasuryBalance);
        if (fromTreasury > 0) countryManager.withdrawFromBank(defender, fromTreasury);

        double remaining = reparations - fromTreasury;

        if (remaining > 0) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(defenderPlayer.getUniqueId());
            EconomyManager eco = plugin.getEconomyManager();
            if (eco.isEnabled() && eco.has(owner, remaining)) {
                eco.withdraw(owner, remaining);
                remaining = 0;
            }
        }

        if (remaining > 0) {
            countryManager.addCountryDebt(defender, remaining);
        }

        double collected = reparations - remaining;
        if (collected > 0) {
            countryManager.depositToBank(attacker, collected);
        }

        countryManager.setNeutral(defender, attacker);
        removeWar(attacker, defender);

        Player attackerPlayer = Bukkit.getPlayer(countryManager.getOwner(attacker));
        if (attackerPlayer != null && attackerPlayer.isOnline()) {
            attackerPlayer.sendMessage("§a" + defender + " капитулировал! Вы получили репарации: " +
                    plugin.getEconomyManager().format(collected));
        }
        defenderPlayer.sendMessage("§cВы капитулировали. Репарации: " +
                plugin.getEconomyManager().format(collected));
        return true;
    }

    public void removeWar(String a, String b) {
        String sql = "DELETE FROM wars WHERE (attacker=? AND defender=?) OR (attacker=? AND defender=?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, b);
            ps.setString(4, a);
            ps.executeUpdate();
            if (plugin.getWebPanelUploader() != null) plugin.getWebPanelUploader().scheduleForcePush();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void checkAutoVictory(String winner, String loser) {
        if (!isAtWar(winner, loser)) return;
        int thresholdPercent = plugin.getConfig().getInt("war-victory-threshold-percent", 40);
        int currentClaims = countryManager.getClaimCount(loser);
        int maxClaims = countryManager.getMaxClaims(countryManager.getOwner(loser));
        if (maxClaims <= 0) return;
        double remainingPercent = 100.0 * currentClaims / maxClaims;
        if (remainingPercent <= (100 - thresholdPercent)) {
            boolean trophy = countryManager.transferRandomUpgradedChunk(loser, winner);
            Player winnerPlayer = Bukkit.getPlayer(countryManager.getOwner(winner));
            if (winnerPlayer != null) {
                winnerPlayer.sendMessage(trophy ? "§aПобеда! Вы захватили улучшенный чанк врага!" : "§aПобеда в войне!");
            }
            countryManager.setNeutral(winner, loser);
            removeWar(winner, loser);
            Player loserPlayer = Bukkit.getPlayer(countryManager.getOwner(loser));
            if (loserPlayer != null) loserPlayer.sendMessage("§cВы проиграли войну!");
        }
    }
}
