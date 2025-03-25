package com.wkaiser.riotapimcpserver.riot.lol.summoner.tool;

import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
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
    
    @Tool(name = "get_lol_summoner_by_name", description = "Get League of Legends summoner information by summoner name")
    public Summoner getSummonerByName(String platformStr, String summonerName) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by name: {} on platform: {}", summonerName, platform);
        return summonerService.getSummonerByName(platform, summonerName);
    }
    
    @Tool(name = "get_lol_summoner_by_puuid", description = "Get League of Legends summoner information by PUUID")
    public Summoner getSummonerByPuuid(String platformStr, String puuid) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by PUUID: {} on platform: {}", puuid, platform);
        return summonerService.getSummonerByPuuid(platform, puuid);
    }
    
    @Tool(name = "get_lol_summoner_by_id",description = "Get League of Legends summoner information by summoner ID")
    public Summoner getSummonerById(String platformStr, String summonerId) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by ID: {} on platform: {}", summonerId, platform);
        return summonerService.getSummonerById(platform, summonerId);
    }
}
