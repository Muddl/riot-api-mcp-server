package com.wkaiser.riotapimcpserver.summoner.adapter.in.mcp;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.summoner.application.SummonerService;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
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
            name = "get_lol_summoner_by_name",
            description = "Get League of Legends summoner information by summoner name")
    public Summoner getSummonerByName(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The summoner's name", required = true) String summonerName) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by name: {} on platform: {}", summonerName, platform);
        return summonerService.getSummonerByName(platform, summonerName);
    }

    @McpTool(name = "get_lol_summoner_by_puuid", description = "Get League of Legends summoner information by PUUID")
    public Summoner getSummonerByPuuid(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player's PUUID (encrypted universally unique ID)", required = true)
                    String puuid) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by PUUID: {} on platform: {}", puuid, platform);
        return summonerService.getSummonerByPuuid(platform, puuid);
    }

    @McpTool(name = "get_lol_summoner_by_id", description = "Get League of Legends summoner information by summoner ID")
    public Summoner getSummonerById(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The encrypted summoner ID", required = true) String summonerId) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by ID: {} on platform: {}", summonerId, platform);
        return summonerService.getSummonerById(platform, summonerId);
    }
}
