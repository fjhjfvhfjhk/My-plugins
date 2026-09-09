package com.example.sovereignty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InviteManager {

    private final Map<UUID, Invite> invites = new HashMap<>();

    public void sendInvite(UUID from, UUID to, String countryName) {
        invites.put(to, new Invite(from, countryName));
    }

    public Invite getInvite(UUID to) {
        return invites.get(to);
    }

    public void removeInvite(UUID to) {
        invites.remove(to);
    }

    public static class Invite {
        public final UUID from;
        public final String countryName;

        public Invite(UUID from, String countryName) {
            this.from = from;
            this.countryName = countryName;
        }
    }
}
