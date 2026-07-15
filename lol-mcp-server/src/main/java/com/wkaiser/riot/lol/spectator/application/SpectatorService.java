package com.wkaiser.riot.lol.spectator.application;

import com.wkaiser.riot.core.enums.RiotApiPlatformUri;
import com.wkaiser.riot.lol.spectator.application.port.SpectatorPort;
import com.wkaiser.riot.lol.spectator.domain.CurrentGameInfo;
import com.wkaiser.riot.lol.spectator.domain.FeaturedGames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends live-game data. Delegates retrieval to the
 * outbound {@link SpectatorPort}; holds no HTTP concerns. Returns {@code null} from
 * {@link #getCurrentGameInfo} when the summoner is not currently in a game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectatorService {

    private final SpectatorPort spectatorPort;

    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId) {
        log.info("Fetching current game info for summoner ID: {} on platform: {}", encryptedSummonerId, platform);
        return spectatorPort.getCurrentGameInfo(platform, encryptedSummonerId);
    }

    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        log.info("Fetching featured games for platform: {}", platform);
        return spectatorPort.getFeaturedGames(platform);
    }
}
