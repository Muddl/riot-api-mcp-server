package com.wkaiser.riotapimcpserver.riot.lol.spectator.service;

import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.FeaturedGames;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Service for interacting with the League of Legends Spectator API v4.
 * Handles operations related to live game data and featured games.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectatorService {
    private final RestClient riotRestClient;

    @Value("${riot.apiKey}")
    private String apiKey;

    private final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    /**
     * Get current game information for a summoner by their encrypted summoner ID.
     * Returns null if the summoner is not currently in a game (404 response).
     *
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param encryptedSummonerId The encrypted summoner ID
     * @return CurrentGameInfo if summoner is in game, null if not in game
     * @throws RiotApiException for other API errors (rate limits, server errors, etc.)
     */
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId) {
        log.info("Fetching current game info for summoner ID: {} on platform: {}", encryptedSummonerId, platform);

        RestClient platformClient = createPlatformClient(platform);

        try {
            return platformClient.get()
                    .uri("/lol/spectator/v4/active-games/by-summoner/{encryptedSummonerId}", encryptedSummonerId)
                    .retrieve()
                    .body(CurrentGameInfo.class);
        } catch (HttpClientErrorException e) {
            // Handle 404 specifically - summoner is not currently in a game
            if (e.getStatusCode().value() == 404) {
                log.debug("Summoner {} is not currently in a game (404 response)", encryptedSummonerId);
                return null;
            }
            // Re-throw other client errors as RiotApiException
            log.error("Riot API error fetching current game info: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RiotApiException("Riot API error: " + e.getResponseBodyAsString(), e.getStatusCode().value());
        }
    }

    /**
     * Get featured games for a platform.
     * Featured games are a list of high-profile current games selected by Riot.
     *
     * @param platform The game platform (e.g., NA1, EUW1)
     * @return FeaturedGames containing list of featured games and refresh interval
     * @throws RiotApiException for API errors
     */
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        log.info("Fetching featured games for platform: {}", platform);

        RestClient platformClient = createPlatformClient(platform);

        try {
            return platformClient.get()
                    .uri("/lol/spectator/v4/featured-games")
                    .retrieve()
                    .body(FeaturedGames.class);
        } catch (HttpClientErrorException e) {
            log.error("Riot API error fetching featured games: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RiotApiException("Riot API error: " + e.getResponseBodyAsString(), e.getStatusCode().value());
        }
    }

    /**
     * Create a platform-specific RestClient with proper authentication headers
     * and error handling for the Spectator API endpoints.
     *
     * @param platform The platform to create the client for
     * @return Configured RestClient for the platform
     */
    private RestClient createPlatformClient(RiotApiPlatformUri platform) {
        return RestClient.builder()
                .baseUrl("https://" + platform.getPlatformUri())
                .defaultHeader(RIOT_TOKEN_HEADER, apiKey)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String errorBody = response.getBody().toString();
                    log.error("Spectator API error: {} - {}", response.getStatusCode(), errorBody);
                    throw new RiotApiException("Spectator API error: " + errorBody,
                            response.getStatusCode().value());
                })
                .build();
    }
}