package com.muddl.riot.lol.summoner.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.application.SummonerService;
import com.muddl.riot.lol.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing League of Legends summoner functionality.
 * Exposes methods that can be called by AI models via the MCP server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerTool {

    private final SummonerService summonerService;

    @McpTool(
            name = "lol_summoner_by_player",
            description =
                    "Get League of Legends summoner information by player (a Riot ID as GameName#TAG, or a raw PUUID).")
    public Summoner getSummonerByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner for a player on platform: {}", platform);
        return summonerService.getSummonerByPlayer(platform, player);
    }
}
