package com.example.sovereignty;

import java.util.*;

public class PeaceRequestManager {

    private final Set<String> peaceRequests = new HashSet<>();

    public void sendRequest(String fromCountry, String toCountry) {
        peaceRequests.add(fromCountry + "->" + toCountry);
    }

    public boolean hasIncomingRequest(String recipientCountry, String fromCountry) {
        return peaceRequests.contains(fromCountry + "->" + recipientCountry);
    }

    public void removeRequest(String fromCountry, String toCountry) {
        peaceRequests.remove(fromCountry + "->" + toCountry);
    }
}
