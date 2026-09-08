package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;

public class CourtManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final CountryManager countryManager;

    public CourtManager(SovereigntyPlugin plugin, DatabaseManager db, CountryManager countryManager) {
        this.plugin = plugin;
        this.db = db;
        this.countryManager = countryManager;
    }

    public int fileComplaint(UUID plaintiff, UUID defendant, String reason) {
        String plaintiffCountry = countryManager.getCountryName(plaintiff);
        String defendantCountry = countryManager.getCountryName(defendant);
        if (plaintiffCountry == null || defendantCountry == null) return -1;

        long now = System.currentTimeMillis();
        String sql = "INSERT INTO court_cases(plaintiff, defendant, reason, created_at, active) VALUES(?,?,?,?,1)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, plaintiff.toString());
            ps.setString(2, defendant.toString());
            ps.setString(3, reason);
            ps.setLong(4, now);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public boolean vote(UUID voter, int caseId, boolean agree, Set<String> sanctions) {
        CaseInfo caseInfo = getCase(caseId);
        if (caseInfo == null) return false;
        if (voter.equals(caseInfo.defendant)) return false;

        String joined = agree ? String.join(",", sanctions) : "";
        String sql = "INSERT OR REPLACE INTO court_votes(case_id, voter, vote, sanctions) VALUES(?,?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, caseId);
            ps.setString(2, voter.toString());
            ps.setInt(3, agree ? 1 : 0);
            ps.setString(4, joined);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean hasVoted(UUID voter, int caseId) {
        String sql = "SELECT 1 FROM court_votes WHERE case_id=? AND voter=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, caseId);
            ps.setString(2, voter.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<CaseInfo> getActiveCases() {
        List<CaseInfo> cases = new ArrayList<>();
        String sql = "SELECT id, plaintiff, defendant, reason, created_at FROM court_cases WHERE active=1 ORDER BY created_at DESC";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                cases.add(new CaseInfo(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("plaintiff")),
                        UUID.fromString(rs.getString("defendant")),
                        rs.getString("reason"),
                        rs.getLong("created_at")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return cases;
    }

    public CaseInfo getCase(int caseId) {
        String sql = "SELECT id, plaintiff, defendant, reason, created_at FROM court_cases WHERE id=? AND active=1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, caseId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new CaseInfo(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("plaintiff")),
                        UUID.fromString(rs.getString("defendant")),
                        rs.getString("reason"),
                        rs.getLong("created_at")
                );
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public Map<String, Integer> getSanctionVotes(int caseId) {
        Map<String, Integer> result = new HashMap<>();
        String sql = "SELECT sanctions FROM court_votes WHERE case_id=? AND vote=1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, caseId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String s = rs.getString("sanctions");
                if (s != null && !s.isEmpty()) {
                    for (String type : s.split(",")) {
                        result.merge(type.trim(), 1, Integer::sum);
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    public int getTotalVoters(int caseId) {
        String sql = "SELECT COUNT(*) FROM court_votes WHERE case_id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, caseId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getAgreeVoters(int caseId) {
        String sql = "SELECT COUNT(*) FROM court_votes WHERE case_id=? AND vote=1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, caseId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /**
     * Применяет санкции. Для штрафа: сначала казна, потом личный счёт владельца, потом долг.
     */
    public void applySanctions(int caseId, Set<String> sanctions) {
        CaseInfo caseInfo = getCase(caseId);
        if (caseInfo == null) return;

        String defendantCountry = countryManager.getCountryName(caseInfo.defendant);
        if (defendantCountry == null) return;

        for (String sanction : sanctions) {
            switch (sanction) {
                case "fine" -> {
                    double fine = plugin.getConfig().getDouble("court.fine-amount", 5000.0);
                    // 1. Казна
                    double treasuryBalance = countryManager.getBankBalance(defendantCountry);
                    double fromTreasury = Math.min(fine, treasuryBalance);
                    if (fromTreasury > 0) countryManager.withdrawFromBank(defendantCountry, fromTreasury);

                    double remaining = fine - fromTreasury;
                    if (remaining <= 0) break;

                    // 2. Личный счёт владельца
                    UUID ownerUuid = countryManager.getOwner(defendantCountry);
                    if (ownerUuid != null) {
                        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
                        EconomyManager eco = plugin.getEconomyManager();
                        if (eco.isEnabled() && eco.has(owner, remaining)) {
                            eco.withdraw(owner, remaining);
                            remaining = 0;
                        }
                    }

                    // 3. Долг страны
                    if (remaining > 0) {
                        countryManager.addCountryDebt(defendantCountry, remaining);
                    }
                }
                case "embargo" -> {
                    for (String other : countryManager.getAllCountries()) {
                        if (!other.equals(defendantCountry)) {
                            plugin.getPactManager().removePact(defendantCountry, other, "trade");
                        }
                    }
                }
                case "diplomacy" -> {
                    for (String other : countryManager.getAllCountries()) {
                        if (!other.equals(defendantCountry)) {
                            countryManager.setNeutral(defendantCountry, other);
                        }
                    }
                    int durationMin = plugin.getConfig().getInt("court.diplomacy-ban-minutes", 120);
                    countryManager.setDiplomacyBan(defendantCountry, System.currentTimeMillis() + durationMin * 60000L);
                }
            }
        }
        closeCase(caseId);
    }

    public void closeCase(int caseId) {
        String sql = "UPDATE court_cases SET active=0 WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, caseId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void startAutoClose() {
        long checkIntervalTicks = 20L * 10L;
        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredCases();
            }
        }.runTaskTimer(plugin, checkIntervalTicks, checkIntervalTicks);
    }

    private void checkExpiredCases() {
        long votingDurationMs = plugin.getConfig().getLong("court.voting-duration-seconds", 300) * 1000L;
        int requiredPercent = plugin.getConfig().getInt("court.required-votes-percent", 60);
        int minVotes = plugin.getConfig().getInt("court.min-votes", 2);
        long now = System.currentTimeMillis();

        for (CaseInfo caseInfo : getActiveCases()) {
            if (now - caseInfo.createdAt >= votingDurationMs) {
                int totalVoters = getTotalVoters(caseInfo.id);
                if (totalVoters < minVotes) {
                    closeCase(caseInfo.id);
                    Bukkit.broadcastMessage("§6[Суд] §eДело #" + caseInfo.id + " закрыто: недостаточно голосов.");
                    continue;
                }

                Map<String, Integer> sanctionVotes = getSanctionVotes(caseInfo.id);
                Set<String> applied = new HashSet<>();
                for (Map.Entry<String, Integer> entry : sanctionVotes.entrySet()) {
                    double support = 100.0 * entry.getValue() / totalVoters;
                    if (support >= requiredPercent) {
                        applied.add(entry.getKey());
                    }
                }

                if (applied.isEmpty()) {
                    closeCase(caseInfo.id);
                    Bukkit.broadcastMessage("§6[Суд] §eДело #" + caseInfo.id + " закрыто: санкции не набрали достаточной поддержки.");
                    continue;
                }

                applySanctions(caseInfo.id, applied);
                String defendantName = Bukkit.getOfflinePlayer(caseInfo.defendant).getName();
                Bukkit.broadcastMessage("§6[Суд] §eСтрана " + defendantName + " получила санкции: " + String.join(", ", applied));
            }
        }
    }

    public static class CaseInfo {
        public final int id;
        public final UUID plaintiff;
        public final UUID defendant;
        public final String reason;
        public final long createdAt;

        public CaseInfo(int id, UUID plaintiff, UUID defendant, String reason, long createdAt) {
            this.id = id;
            this.plaintiff = plaintiff;
            this.defendant = defendant;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }
}
