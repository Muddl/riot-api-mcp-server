package com.wkaiser.riotapimcpserver.riot.lol.match.service;

import com.wkaiser.riotapimcpserver.riot.lol.match.dto.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Service for interacting with the League of Legends Match API.
 * Handles operations related to match history and match details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
    private final RestClient riotRestClient;
    
    /**
     * Get match IDs for a player by PUUID
     * @param region The game region
     * @param puuid The PUUID of the player
     * @param count The number of matches to retrieve (default 20, max 100)
     * @param start The start index for pagination
     * @param queue The queue type filter (null for all queues)
     * @return List of match IDs
     */
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        log.info("Fetching match IDs for PUUID: {}", puuid);
        
        RestClient regionClient = RestClient.builder()
                .baseUrl("https://" + region.getRegionUri())
                .build();
        
        String uri = "/lol/match/v5/matches/by-puuid/{puuid}/ids?";
        
        // Add query parameters if provided
        if (count != null) {
            uri += "count=" + Math.min(count, 100) + "&";
        }
        
        if (start != null) {
            uri += "start=" + start + "&";
        }
        
        if (queue != null) {
            uri += "queue=" + queue;
        }
        
        // Remove trailing ampersand or question mark
        if (uri.endsWith("&") || uri.endsWith("?")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        
        return regionClient.get()
                .uri(uri, puuid)
                .retrieve()
                .body(List.class);
    }
    
    /**
     * Get detailed match information by match ID
     * @param region The game region
     * @param matchId The match ID
     * @return Match detail information
     */
    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        log.info("Fetching match details for match ID: {}", matchId);
        
        RestClient regionClient = RestClient.builder()
                .baseUrl("https://" + region.getRegionUri())
                .build();
        
        return regionClient.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(Match.class);
    }
}
