package com.wkaiser.riotapimcpserver.riot.lol.summoner.service;

import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
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
 * Service for interacting with the League of Legends Summoner API.
 * Handles operations related to League of Legends summoner profiles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {
    private final RestClient riotRestClient;

    @Value("${riot.apiKey}")
    private String apiKey;

    private final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    /**
     * Get summoner information by summoner name
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param summonerName The name of the summoner
     * @return Summoner information
     */
    public Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName) {
        log.info("Fetching summoner for name: {} on platform: {}", summonerName, platform);

        RestClient platformClient = createPlatformClient(platform);

        try {
            return platformClient.get()
                    .uri("/lol/summoner/v4/summoners/by-name/{summonerName}", summonerName)
                    .retrieve()
                    .body(Summoner.class);
        } catch (HttpClientErrorException e) {
            log.error("Riot API error fetching summoner by name: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RiotApiException("Riot API error: " + e.getResponseBodyAsString(), e.getStatusCode().value());
        }
    }

    /**
     * Get summoner information by PUUID
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param puuid The PUUID of the summoner
     * @return Summoner information
     */
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching summoner for PUUID: {} on platform: {}", puuid, platform);

        RestClient platformClient = createPlatformClient(platform);

        try {
            return platformClient.get()
                    .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                    .retrieve()
                    .body(Summoner.class);
        } catch (HttpClientErrorException e) {
            log.error("Riot API error fetching summoner by PUUID: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RiotApiException("Riot API error: " + e.getResponseBodyAsString(), e.getStatusCode().value());
        }
    }

    /**
     * Get summoner information by summoner ID
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param summonerId The ID of the summoner
     * @return Summoner information
     */
    public Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId) {
        log.info("Fetching summoner for ID: {} on platform: {}", summonerId, platform);

        RestClient platformClient = createPlatformClient(platform);

        try {
            return platformClient.get()
                    .uri("/lol/summoner/v4/summoners/{summonerId}", summonerId)
                    .retrieve()
                    .body(Summoner.class);
        } catch (HttpClientErrorException e) {
            log.error("Riot API error fetching summoner by ID: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RiotApiException("Riot API error: " + e.getResponseBodyAsString(), e.getStatusCode().value());
        }
    }

    /**
     * Create a platform-specific RestClient with proper authentication headers
     * and error handling for the Summoner API endpoints.
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
                    log.error("Summoner API error: {} - {}", response.getStatusCode(), errorBody);
                    throw new RiotApiException("Summoner API error: " + errorBody,
                            response.getStatusCode().value());
                })
                .build();
    }
}