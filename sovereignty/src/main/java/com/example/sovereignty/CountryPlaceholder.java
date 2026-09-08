package com.example.sovereignty;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Плейсхолдер %sovereignty_country% — название страны игрока.
 * Если страны нет, возвращает "Без страны".
 */
public class CountryPlaceholder extends PlaceholderExpansion {

    private final SovereigntyPlugin plugin;
    private final CountryManager countryManager;

    public CountryPlaceholder(SovereigntyPlugin plugin, CountryManager countryManager) {
        this.plugin = plugin;
        this.countryManager = countryManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sovereignty";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Sovereignty";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.3.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        if (params.equalsIgnoreCase("country")) {
            String country = countryManager.getCountryName(player.getUniqueId());
            return country == null ? "Без страны" : country;
        }
        return null;
    }
}
