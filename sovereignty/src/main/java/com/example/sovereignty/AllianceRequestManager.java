package com.example.sovereignty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит входящие запросы на альянс (отправитель -> получатель).
 */
public class AllianceRequestManager {

    private final Map<UUID, UUID> pendingRequests = new HashMap<>();

    public void sendRequest(UUID from, UUID to) {
        pendingRequests.put(to, from);
    }

    public boolean hasIncomingRequest(UUID recipient, UUID from) {
        UUID sender = pendingRequests.get(recipient);
        return sender != null && sender.equals(from);
    }

    public void acceptRequest(UUID recipient, UUID from) {
        pendingRequests.remove(recipient);
    }

    public void declineRequest(UUID recipient) {
        pendingRequests.remove(recipient);
    }

    public void removeAllFor(UUID uuid) {
        pendingRequests.remove(uuid);
        pendingRequests.entrySet().removeIf(e -> e.getValue().equals(uuid));
    }
}
