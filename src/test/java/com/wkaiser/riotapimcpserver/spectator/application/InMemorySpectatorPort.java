package com.wkaiser.riotapimcpserver.spectator.application;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.spectator.application.port.SpectatorPort;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;

import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SpectatorPort} for fast, HTTP-free service tests. */
public class InMemorySpectatorPort implements SpectatorPort {

    private final Map<String, CurrentGameInfo> gamesBySummonerId = new HashMap<>();
    private final Map<RiotApiPlatformUri, FeaturedGames> featuredByPlatform = new HashMap<>();

    public InMemorySpectatorPort putGame(String encryptedSummonerId, CurrentGameInfo game) {
        gamesBySummonerId.put(encryptedSummonerId, game);
        return this;
    }

    public InMemorySpectatorPort putFeatured(RiotApiPlatformUri platform, FeaturedGames featured) {
        featuredByPlatform.put(platform, featured);
        return this;
    }

    /** Returns the stored game, or {@code null} to model "summoner not in a game". */
    @Override
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId) {
        return gamesBySummonerId.get(encryptedSummonerId);
    }

    @Override
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        return featuredByPlatform.get(platform);
    }
}
