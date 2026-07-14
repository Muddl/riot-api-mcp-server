package com.wkaiser.riotapimcpserver.spectator.adapter.out.riot;

import com.wkaiser.riotapimcpserver.spectator.application.port.SpectatorPort;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Riot Spectator-V4 API adapter. Spectator endpoints are platform-routed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotSpectatorAdapter implements SpectatorPort {

    private static final int NOT_FOUND = 404;

    private final RiotApiClient riotApiClient;

    @Override
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId) {
        try {
            return riotApiClient.platform(platform).get()
                    .uri("/lol/spectator/v4/active-games/by-summoner/{encryptedSummonerId}", encryptedSummonerId)
                    .retrieve()
                    .body(CurrentGameInfo.class);
        } catch (RiotApiException e) {
            if (e.getStatusCode() == NOT_FOUND) {
                log.debug("Summoner {} is not currently in a game (404)", encryptedSummonerId);
                return null;
            }
            throw e;
        }
    }

    @Override
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        return riotApiClient.platform(platform).get()
                .uri("/lol/spectator/v4/featured-games")
                .retrieve()
                .body(FeaturedGames.class);
    }
}
