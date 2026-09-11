package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

public class CountryManager {

    private final SovereigntyPlugin plugin;
    private final DatabaseManager db;
    private final EnergyManager energyManager;
    private final EconomyManager economyManager;
    private final int baseMaxClaims;

    private static final String NAME_PATTERN = "^[а-яА-Яa-zA-Z0-9 _-]+$";

    public CountryManager(SovereigntyPlugin plugin, DatabaseManager db,
                          EnergyManager energyManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.db = db;
        this.energyManager = energyManager;
        this.economyManager = economyManager;
        this.baseMaxClaims = plugin.getConfig().getInt("base-max-claims", 15);
    }

    // ===== Страны =====

    public boolean hasCountry(UUID playerUuid) {
        String sql = "SELECT name FROM countries WHERE owner_uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean countryExists(String name) {
        String sql = "SELECT name FROM countries WHERE name = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            return ps.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean isValidName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 16 && name.matches(NAME_PATTERN);
    }

    public boolean createCountry(Player player, String name) {
        if (!isValidName(name)) {
            player.sendMessage("§cНазвание содержит недопустимые символы (разрешены буквы, цифры, пробел, - и _).");
            return false;
        }
        if (hasCountry(player.getUniqueId()) || countryExists(name)) return false;
        String sql = "INSERT INTO countries(name, owner_uuid) VALUES(?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, player.getUniqueId().toString());
            ps.executeUpdate();
            try (PreparedStatement bankPs = db.getConnection().prepareStatement(
                    "INSERT OR IGNORE INTO country_bank(country_name, balance, debt, diplomacy_ban_until) VALUES(?, 0, 0, 0)")) {
                bankPs.setString(1, name);
                bankPs.executeUpdate();
            }
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public String getCountryName(UUID playerUuid) {
        String sql = "SELECT name FROM countries WHERE owner_uuid = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public UUID getOwner(String countryName) {
        String sql = "SELECT owner_uuid FROM countries WHERE name = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("owner_uuid"));
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public List<String> getAllCountries() {
        List<String> result = new ArrayList<>();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM countries ORDER BY name")) {
            while (rs.next()) result.add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    public boolean renameCountry(UUID ownerUuid, String newName) {
        if (!isValidName(newName)) {
            plugin.getLogger().warning("Попытка переименовать страну в недопустимое имя: " + newName);
            return false;
        }
        if (countryExists(newName)) return false;
        String oldName = getCountryName(ownerUuid);
        if (oldName == null) return false;
        if (oldName.equals(newName)) return true;

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE countries SET name=? WHERE owner_uuid=?")) {
                    ps.setString(1, newName);
                    ps.setString(2, ownerUuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE chunks SET country_name=? WHERE country_name=?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE relations SET country_a=? WHERE country_a=?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE relations SET country_b=? WHERE country_b=?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE country_bank SET country_name=? WHERE country_name=?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE co_rulers SET country_name=? WHERE country_name=?")) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                conn.commit();
                plugin.getLogger().info("Страна переименована: " + oldName + " -> " + newName);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Ошибка при переименовании страны " + oldName + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось открыть транзакцию для переименования: " + e.getMessage());
            return false;
        }
    }

    // ===== Чанки =====

    public int getClaimCount(String countryName) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT COUNT(*) FROM chunks WHERE country_name=?")) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getMaxClaims(UUID playerUuid) {
        EnergyManager.PlayerData data = energyManager.getData(playerUuid);
        int step = plugin.getConfig().getInt("chunk-limit-upgrade-step", 10);
        int max = baseMaxClaims + data.chunkLimitLevel * step;
        if (plugin.getScienceManager().isResearched(playerUuid, "logistics")) max += 5;
        return max;
    }

    public boolean claimChunk(Player player) {
        String countryName = getCountryName(player.getUniqueId());
        if (countryName == null) return false;

        int maxClaims = getMaxClaims(player.getUniqueId());
        if (getClaimCount(countryName) >= maxClaims) return false;

        Chunk chunk = player.getLocation().getChunk();
        String existingOwner = getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ());

        if (existingOwner != null && !existingOwner.equals(countryName)) {
            // Проверка пакта о ненападении
            if (plugin.getPactManager().hasNonAggressionPact(countryName, existingOwner)) {
                player.sendMessage("§cВы не можете захватывать чанки страны, с которой у вас пакт о ненападении.");
                return false;
            }

            // Должны быть врагами
            if (!isEnemy(countryName, existingOwner)) {
                player.sendMessage("§cВы не в состоянии войны с этой страной.");
                return false;
            }

            // Нельзя трогать защищённые чанки
            if (plugin.getDefensiveManager().isDefended(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
                player.sendMessage("§cЭтот чанк защищён! Сначала убейте владельца.");
                return false;
            }

            // ВАЖНО: Нельзя захватывать чанки, если владелец страны оффлайн
            UUID enemyOwner = getOwner(existingOwner);
            if (enemyOwner != null && Bukkit.getPlayer(enemyOwner) == null) {
                player.sendMessage("§cВладелец страны сейчас оффлайн — подождите, пока он зайдёт.");
                return false;
            }
        }

        double energyCost = plugin.getConfig().getDouble("claim-energy-cost", 2.0);
        if (energyManager.getEnergy(player.getUniqueId()) < energyCost) return false;

        double moneyCost = plugin.getConfig().getDouble("claim-money-cost", 0.0);
        if (moneyCost > 0 && (!economyManager.isEnabled() || !economyManager.has(player, moneyCost))) return false;

        energyManager.consumeEnergy(player.getUniqueId(), energyCost);
        if (moneyCost > 0) economyManager.withdraw(player, moneyCost);

        String sql = "INSERT OR REPLACE INTO chunks(country_name, world, chunk_x, chunk_z) VALUES(?,?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ps.setString(2, chunk.getWorld().getName());
            ps.setInt(3, chunk.getX());
            ps.setInt(4, chunk.getZ());
            ps.executeUpdate();
            if (existingOwner != null) plugin.getPactManager().checkAutoVictory(countryName, existingOwner);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            energyManager.addEnergy(player.getUniqueId(), energyCost);
            if (moneyCost > 0) economyManager.deposit(player, moneyCost);
            return false;
        }
    }

    public boolean unclaimChunk(Player player) {
        String countryName = getCountryName(player.getUniqueId());
        if (countryName == null) return false;
        Chunk chunk = player.getLocation().getChunk();
        if (!countryName.equals(getChunkOwner(chunk.getWorld(), chunk.getX(), chunk.getZ()))) return false;
        String sql = "DELETE FROM chunks WHERE country_name=? AND world=? AND chunk_x=? AND chunk_z=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ps.setString(2, chunk.getWorld().getName());
            ps.setInt(3, chunk.getX());
            ps.setInt(4, chunk.getZ());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean removeRandomChunk(String countryName) {
        String selectSql = "SELECT world, chunk_x, chunk_z FROM chunks WHERE country_name=? ORDER BY RANDOM() LIMIT 1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(selectSql)) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("chunk_x");
                int z = rs.getInt("chunk_z");
                try (PreparedStatement del = db.getConnection().prepareStatement(
                        "DELETE FROM chunks WHERE world=? AND chunk_x=? AND chunk_z=?")) {
                    del.setString(1, world);
                    del.setInt(2, x);
                    del.setInt(3, z);
                    del.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean transferRandomUpgradedChunk(String fromCountry, String toCountry) {
        String selectSql = "SELECT world, chunk_x, chunk_z, chunk_type FROM chunks " +
                "WHERE country_name=? AND chunk_type != 'normal' ORDER BY RANDOM() LIMIT 1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(selectSql)) {
            ps.setString(1, fromCountry);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("chunk_x");
                int z = rs.getInt("chunk_z");
                String type = rs.getString("chunk_type");
                String transferSql = "INSERT OR REPLACE INTO chunks(country_name, world, chunk_x, chunk_z, chunk_type) VALUES(?,?,?,?,?)";
                try (PreparedStatement ins = db.getConnection().prepareStatement(transferSql)) {
                    ins.setString(1, toCountry);
                    ins.setString(2, world);
                    ins.setInt(3, x);
                    ins.setInt(4, z);
                    ins.setString(5, type);
                    ins.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public String getChunkOwner(World world, int x, int z) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT country_name FROM chunks WHERE world=? AND chunk_x=? AND chunk_z=?")) {
            ps.setString(1, world.getName());
            ps.setInt(2, x);
            ps.setInt(3, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("country_name");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // ===== Отношения =====

    public String getRelation(String a, String b) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT relation FROM relations WHERE (country_a=? AND country_b=?) OR (country_a=? AND country_b=?)")) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, b);
            ps.setString(4, a);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("relation");
        } catch (SQLException e) { e.printStackTrace(); }
        return "neutral";
    }

    public boolean isAlly(String a, String b) { return getRelation(a, b).equals("ally"); }
    public boolean isEnemy(String a, String b) { return getRelation(a, b).equals("enemy"); }

    public void setAlly(String a, String b) { setRelationInternal(a, b, "ally"); }
    public void setEnemy(String a, String b) {
        setRelationInternal(a, b, "enemy");
        plugin.getPactManager().removeNonAggressionPact(a, b);
    }
    public void setNeutral(String a, String b) { setRelationInternal(a, b, "neutral"); }

    private void setRelationInternal(String a, String b, String relation) {
        try {
            deleteRelation(a, b);
            String sql = "INSERT INTO relations(country_a, country_b, relation) VALUES(?,?,?)";
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
                ps.setString(1, a);
                ps.setString(2, b);
                ps.setString(3, relation);
                ps.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deleteRelation(String a, String b) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM relations WHERE (country_a=? AND country_b=?) OR (country_a=? AND country_b=?)")) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, b);
            ps.setString(4, a);
            ps.executeUpdate();
        }
    }

    // ===== Банк и долг =====

    public double getBankBalance(String countryName) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT balance FROM country_bank WHERE country_name=?")) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0;
    }

    public double getCountryDebt(String countryName) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT debt FROM country_bank WHERE country_name=?")) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("debt");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0;
    }

    public boolean depositToBank(String countryName, double amount) {
        return changeBankBalance(countryName, amount);
    }

    public boolean withdrawFromBank(String countryName, double amount) {
        double current = getBankBalance(countryName);
        if (current < amount) return false;
        return changeBankBalance(countryName, -amount);
    }

    public double withdrawAllFromBank(String countryName) {
        double balance = getBankBalance(countryName);
        if (balance <= 0) return 0.0;
        if (changeBankBalance(countryName, -balance)) {
            return balance;
        }
        return -1.0;
    }

    public void addCountryDebt(String countryName, double amount) {
        changeDebt(countryName, amount);
    }

    public boolean reduceCountryDebt(String countryName, double amount) {
        double current = getCountryDebt(countryName);
        if (current < amount) return false;
        changeDebt(countryName, -amount);
        return true;
    }

    private boolean changeBankBalance(String countryName, double delta) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO country_bank(country_name, balance, debt, diplomacy_ban_until) VALUES(?,?,0,0) " +
                "ON CONFLICT(country_name) DO UPDATE SET balance=balance+?")) {
            ps.setString(1, countryName);
            ps.setDouble(2, delta);
            ps.setDouble(3, delta);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    private void changeDebt(String countryName, double delta) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO country_bank(country_name, balance, debt, diplomacy_ban_until) VALUES(?,0,?,0) " +
                "ON CONFLICT(country_name) DO UPDATE SET debt=debt+?")) {
            ps.setString(1, countryName);
            ps.setDouble(2, delta);
            ps.setDouble(3, delta);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ===== Дипломатический бан =====

    public void setDiplomacyBan(String countryName, long until) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE country_bank SET diplomacy_ban_until=? WHERE country_name=?")) {
            ps.setLong(1, until);
            ps.setString(2, countryName);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean isDiplomacyBanned(String countryName) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT diplomacy_ban_until FROM country_bank WHERE country_name=?")) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long until = rs.getLong("diplomacy_ban_until");
                return until > System.currentTimeMillis();
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // ===== Соправители =====

    public boolean isLeader(UUID playerUuid, String countryName) {
        return countryName.equals(getCountryName(playerUuid));
    }

    public boolean isCoRuler(UUID playerUuid, String countryName) {
        String sql = "SELECT 1 FROM co_rulers WHERE country_name=? AND player_uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ps.setString(2, playerUuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean isLeaderOrCoRuler(UUID playerUuid, String countryName) {
        return isLeader(playerUuid, countryName) || isCoRuler(playerUuid, countryName);
    }

    public boolean addCoRuler(String countryName, UUID playerUuid) {
        String sql = "INSERT OR IGNORE INTO co_rulers(country_name, player_uuid) VALUES(?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean removeCoRuler(String countryName, UUID playerUuid) {
        String sql = "DELETE FROM co_rulers WHERE country_name=? AND player_uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<UUID> getCoRulers(String countryName) {
        List<UUID> result = new ArrayList<>();
        String sql = "SELECT player_uuid FROM co_rulers WHERE country_name=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, countryName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(UUID.fromString(rs.getString("player_uuid")));
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    // ===== Удаление страны =====

    public boolean deleteCountry(String countryName) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM countries WHERE name=?")) {
                    ps.setString(1, countryName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chunks WHERE country_name=?")) {
                    ps.setString(1, countryName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM relations WHERE country_a=? OR country_b=?")) {
                    ps.setString(1, countryName);
                    ps.setString(2, countryName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM country_bank WHERE country_name=?")) {
                    ps.setString(1, countryName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM pacts WHERE country_a=? OR country_b=?")) {
                    ps.setString(1, countryName);
                    ps.setString(2, countryName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM wars WHERE attacker=? OR defender=?")) {
                    ps.setString(1, countryName);
                    ps.setString(2, countryName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM co_rulers WHERE country_name=?")) {
                    ps.setString(1, countryName);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("Ошибка удаления страны " + countryName + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось открыть транзакцию для удаления страны: " + e.getMessage());
            return false;
        }
    }
}
