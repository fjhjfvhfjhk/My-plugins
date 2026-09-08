package com.example.sovereignty;

import java.util.*;

public class PactRequestManager {

    private final Map<String, Set<String>> incomingRequests = new HashMap<>();

    public void sendRequest(String fromCountry, String toCountry, String pactType) {
        incomingRequests.computeIfAbsent(toCountry, k -> new HashSet<>())
                .add(fromCountry + ":" + pactType);
    }

    public boolean hasIncomingRequest(String recipientCountry, String fromCountry, String pactType) {
        Set<String> requests = incomingRequests.get(recipientCountry);
        return requests != null && requests.contains(fromCountry + ":" + pactType);
    }

    public void removeRequest(String recipientCountry, String fromCountry, String pactType) {
        Set<String> requests = incomingRequests.get(recipientCountry);
        if (requests != null) {
            requests.remove(fromCountry + ":" + pactType);
            if (requests.isEmpty()) incomingRequests.remove(recipientCountry);
        }
    }
}
