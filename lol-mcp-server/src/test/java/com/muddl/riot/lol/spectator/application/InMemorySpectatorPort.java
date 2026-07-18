package com.muddl.riot.lol.spectator.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.spectator.application.port.SpectatorPort;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SpectatorPort} for fast, HTTP-free service tests. */
public class InMemorySpectatorPort implements SpectatorPort {

    private final Map<String, CurrentGameInfo> gamesByPuuid = new HashMap<>();

    public InMemorySpectatorPort putGame(String puuid, CurrentGameInfo game) {
        gamesByPuuid.put(puuid, game);
        return this;
    }

    @Override
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String puuid) {
        return gamesByPuuid.get(puuid);
    }
}
