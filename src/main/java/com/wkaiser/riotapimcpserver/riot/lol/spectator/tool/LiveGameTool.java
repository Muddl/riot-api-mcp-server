package com.wkaiser.riotapimcpserver.riot.lol.spectator.tool;

import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.FeaturedGames;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.service.SpectatorService;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing League of Legends live game functionality.
 * Exposes methods that can be called by AI models via the MCP server for
 * retrieving current game information and featured games.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveGameTool {

    private final SpectatorService spectatorService;
    private final SummonerService summonerService;

    @Tool(name = "get_current_game_by_summoner_name",
          description = "Get current live game information for a summoner by name. Returns live game details if summoner is in game, null if not in game.")
    public CurrentGameInfo getCurrentGameBySummonerName(String platformStr, String summonerName) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting current game by summoner name: {} on platform: {}", summonerName, platform);

        // Get summoner information to extract encrypted summoner ID
        Summoner summoner = summonerService.getSummonerByName(platform, summonerName);
        String encryptedSummonerId = summoner.getId();

        // Get current game info using encrypted summoner ID
        return spectatorService.getCurrentGameInfo(platform, encryptedSummonerId);
    }

    @Tool(name = "get_current_game_by_summoner_id",
          description = "Get current live game information for a summoner by encrypted summoner ID. Returns live game details if summoner is in game, null if not in game.")
    public CurrentGameInfo getCurrentGameBySummonerId(String platformStr, String encryptedSummonerId) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting current game by summoner ID: {} on platform: {}", encryptedSummonerId, platform);

        return spectatorService.getCurrentGameInfo(platform, encryptedSummonerId);
    }

    @Tool(name = "get_featured_games",
          description = "Get list of current featured games on a platform. Featured games are high-profile matches selected by Riot.")
    public FeaturedGames getFeaturedGames(String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting featured games for platform: {}", platform);

        return spectatorService.getFeaturedGames(platform);
    }

    @Tool(name = "check_if_summoner_in_game",
          description = "Check if a summoner is currently in a live game. Returns true if in game, false if not in game.")
    public boolean isSummonerInGame(String platformStr, String summonerName) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Checking if summoner is in game: {} on platform: {}", summonerName, platform);

        CurrentGameInfo currentGame = getCurrentGameBySummonerName(platformStr, summonerName);
        return currentGame != null;
    }
}