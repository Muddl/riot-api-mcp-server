package com.wkaiser.riotapimcpserver.riot.lol.summoner.service;

import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    
    /**
     * Get summoner information by summoner name
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param summonerName The name of the summoner
     * @return Summoner information
     */
    public Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName) {
        log.info("Fetching summoner for name: {} on platform: {}", summonerName, platform);
        
        // We need to switch base URL for platform-specific endpoints
        RestClient platformClient = RestClient.builder()
                .baseUrl("https://" + platform.getPlatformUri())
                .headers(headers -> headers.addAll(riotRestClient.headersToApply()))
                .build();
        
        return platformClient.get()
                .uri("/lol/summoner/v4/summoners/by-name/{summonerName}", summonerName)
                .retrieve()
                .body(Summoner.class);
    }
    
    /**
     * Get summoner information by PUUID
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param puuid The PUUID of the summoner
     * @return Summoner information
     */
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching summoner for PUUID: {} on platform: {}", puuid, platform);
        
        RestClient platformClient = RestClient.builder()
                .baseUrl("https://" + platform.getPlatformUri())
                .headers(headers -> headers.addAll(riotRestClient.headersToApply()))
                .build();
        
        return platformClient.get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(Summoner.class);
    }
    
    /**
     * Get summoner information by summoner ID
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param summonerId The ID of the summoner
     * @return Summoner information
     */
    public Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId) {
        log.info("Fetching summoner for ID: {} on platform: {}", summonerId, platform);
        
        RestClient platformClient = RestClient.builder()
                .baseUrl("https://" + platform.getPlatformUri())
                .headers(headers -> headers.addAll(riotRestClient.headersToApply()))
                .build();
        
        return platformClient.get()
                .uri("/lol/summoner/v4/summoners/{summonerId}", summonerId)
                .retrieve()
                .body(Summoner.class);
    }
}
