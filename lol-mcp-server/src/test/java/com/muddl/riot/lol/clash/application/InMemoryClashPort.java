package com.muddl.riot.lol.clash.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.port.ClashPort;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link ClashPort} for fast, HTTP-free service tests. */
public class InMemoryClashPort implements ClashPort {

    private final Map<String, List<ClashPlayer>> byPuuid = new HashMap<>();

    public InMemoryClashPort put(String puuid, List<ClashPlayer> players) {
        byPuuid.put(puuid, players);
        return this;
    }

    @Override
    public List<ClashPlayer> getPlayersByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.getOrDefault(puuid, List.of());
    }
}
