package com.wkaiser.riotapimcpserver.riot.lol.analytics.service;

import com.wkaiser.riotapimcpserver.riot.account.dto.RiotAccount;
import com.wkaiser.riotapimcpserver.riot.account.service.RiotAccountService;
import com.wkaiser.riotapimcpserver.riot.lol.analytics.dto.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.riot.lol.match.dto.Match;
import com.wkaiser.riotapimcpserver.riot.lol.match.dto.Participant;
import com.wkaiser.riotapimcpserver.riot.lol.match.service.MatchService;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating advanced analytics based on League of Legends match data.
 * This service combines data from multiple API endpoints to provide comprehensive insights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    
    private final RiotAccountService accountService;
    private final SummonerService summonerService;
    private final MatchService matchService;
    
    /**
     * Get match analytics for a player by Riot ID (e.g., PlayerName#TAG)
     * @param riotId The full Riot ID (format: "gameName#tagLine")
     * @param platform The game platform (e.g., NA1, EUW1)
     * @param region The game region (e.g., AMERICAS, EUROPE)
     * @param matchCount Number of recent matches to analyze
     * @return Analytics of the player's recent matches
     */
    public PlayerMatchAnalytics getPlayerMatchAnalytics(String riotId, RiotApiPlatformUri platform, 
                                                        RiotApiRegionUri region, int matchCount) {
        log.info("Generating match analytics for player: {} on platform: {}", riotId, platform);
        
        // Parse the Riot ID into game name and tag line
        String[] riotIdParts = riotId.split("#");
        if (riotIdParts.length != 2) {
            throw new IllegalArgumentException("Invalid Riot ID format. Expected format: 'gameName#tagLine'");
        }
        
        String gameName = riotIdParts[0];
        String tagLine = riotIdParts[1];
        
        // Step 1: Get the account information
        RiotAccount account = accountService.getAccountByRiotId(gameName, tagLine);
        
        // Step 2: Get summoner information
        Summoner summoner = summonerService.getSummonerByPuuid(platform, account.getPuuid());
        
        // Step 3: Get recent match IDs
        List<String> matchIds = matchService.getMatchIdsByPuuid(region, account.getPuuid(), matchCount, 0, null);
        
        // Step 4: Get match details and extract player data
        List<Match> matches = new ArrayList<>();
        List<Participant> playerParticipations = new ArrayList<>();
        
        for (String matchId : matchIds) {
            Match match = matchService.getMatchById(region, matchId);
            matches.add(match);
            
            // Find the player in the participants
            for (Participant participant : match.getInfo().getParticipants()) {
                if (participant.getPuuid().equals(account.getPuuid())) {
                    playerParticipations.add(participant);
                    break;
                }
            }
        }
        
        // Step 5: Calculate basic analytics
        int totalGames = playerParticipations.size();
        
        // Handle case where no matches were found
        if (totalGames == 0) {
            return PlayerMatchAnalytics.builder()
                    .riotId(riotId)
                    .summonerName(summoner.getName())
                    .summonerLevel(summoner.getSummonerLevel())
                    .matchCount(0)
                    .build();
        }
        
        int wins = (int) playerParticipations.stream()
                .filter(Participant::isWin)
                .count();
        int losses = totalGames - wins;
        
        double winRate = (double) wins / totalGames * 100;
        
        double avgKills = playerParticipations.stream()
                .mapToInt(Participant::getKills)
                .average()
                .orElse(0);
        
        double avgDeaths = playerParticipations.stream()
                .mapToInt(Participant::getDeaths)
                .average()
                .orElse(0);
        
        double avgAssists = playerParticipations.stream()
                .mapToInt(Participant::getAssists)
                .average()
                .orElse(0);
        
        // Calculate average vision score
        double avgVisionScore = playerParticipations.stream()
                .mapToInt(Participant::getVisionScore)
                .average()
                .orElse(0);
        
        // Calculate average creep score (CS)
        double avgCreepScore = playerParticipations.stream()
                .mapToInt(p -> p.getTotalMinionsKilled() + p.getNeutralMinionsKilled())
                .average()
                .orElse(0);
        
        // Calculate average game duration
        double avgGameDurationSeconds = matches.stream()
                .mapToLong(match -> match.getInfo().getGameDuration())
                .average()
                .orElse(0);
        
        String formattedGameDuration = formatDuration((long) avgGameDurationSeconds);
        
        // Step 6: Determine most played champions
        List<String> mostPlayedChampions = playerParticipations.stream()
                .collect(Collectors.groupingBy(Participant::getChampionName, Collectors.counting()))
                .entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(3)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + " games)")
                .collect(Collectors.toList());
        
        // Step 7: Determine most played roles
        List<String> mostPlayedRoles = playerParticipations.stream()
                .collect(Collectors.groupingBy(Participant::getTeamPosition, Collectors.counting()))
                .entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(2)
                .map(entry -> {
                    String role = entry.getKey().isEmpty() ? "Unknown" : entry.getKey();
                    return role + " (" + entry.getValue() + " games)";
                })
                .collect(Collectors.toList());
        
        // Step 8: Create and return the analytics object
        return PlayerMatchAnalytics.builder()
                .riotId(riotId)
                .summonerName(summoner.getName())
                .summonerLevel(summoner.getSummonerLevel())
                .matchCount(totalGames)
                .wins(wins)
                .losses(losses)
                .winRate(String.format("%.2f%%", winRate))
                .avgKills(String.format("%.2f", avgKills))
                .avgDeaths(String.format("%.2f", avgDeaths))
                .avgAssists(String.format("%.2f", avgAssists))
                .avgKda(String.format("%.2f", calculateKda(avgKills, avgDeaths, avgAssists)))
                .avgVisionScore(avgVisionScore)
                .avgCreepScore(String.format("%.1f", avgCreepScore))
                .avgGameDuration(formattedGameDuration)
                .mostPlayedChampions(mostPlayedChampions)
                .mostPlayedRoles(mostPlayedRoles)
                .build();
    }
    
    /**
     * Calculate KDA (Kills/Deaths/Assists ratio)
     * Formula: (Kills + Assists) / Deaths, with special handling for zero deaths
     */
    private double calculateKda(double kills, double deaths, double assists) {
        if (deaths == 0) {
            return kills + assists; // Perfect KDA
        }
        return (kills + assists) / deaths;
    }
    
    /**
     * Format a duration in seconds to a readable string (e.g., "32m 45s")
     */
    private String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        long minutes = duration.toMinutes();
        long remainingSeconds = duration.minusMinutes(minutes).getSeconds();
        return String.format("%dm %ds", minutes, remainingSeconds);
    }
}
