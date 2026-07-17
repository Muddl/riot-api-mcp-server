package com.muddl.riot.lol.spectator.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.spectator.application.SpectatorService;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.FeaturedGames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
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

    @McpTool(
            name = "lol_spectator_current_game_by_player",
            description =
                    "Get current live game information for a player (a Riot ID as GameName#TAG, or a raw PUUID). Returns live game details if in a game, null if not.")
    public CurrentGameInfo getCurrentGameByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting current game for a player on platform: {}", platform);
        return spectatorService.getCurrentGameByPlayer(platform, player);
    }

    @McpTool(
            name = "lol_spectator_featured_games",
            description =
                    "Get list of current featured games on a platform. Featured games are high-profile matches selected by Riot.")
    public FeaturedGames getFeaturedGames(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting featured games for platform: {}", platform);
        return spectatorService.getFeaturedGames(platform);
    }
}
