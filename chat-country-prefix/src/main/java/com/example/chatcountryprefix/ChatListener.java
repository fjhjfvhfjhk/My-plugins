package com.example.chatcountryprefix;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Добавляет префикс <Страна> в начало сообщения.
 * Приоритет LOWEST гарантирует, что префикс будет первым.
 */
public class ChatListener implements Listener {

    private final ChatCountryPrefixPlugin plugin;

    public ChatListener(ChatCountryPrefixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        String country = PlaceholderAPI.setPlaceholders(player, "%sovereignty_country%");
        if (country == null || country.isEmpty()) {
            country = "Без страны";
        }

        Component prefix = Component.text("<" + country + "> ", NamedTextColor.GRAY);
        event.message(prefix.append(event.message()));
    }
}
