package com.wkaiser.riotapimcpserver.riot.lol.summoner.tool;

import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.tool.Tool;
import org.springframework.ai.mcp.server.tool.ToolDescription;
import org.springframework.ai.mcp.server.tool.ToolParameter;
import org.springframework.ai.mcp.server.tool.ToolParameterType;
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
    
    @Tool(name = "get_lol_summoner_by_name")
    @ToolDescription("Get League of Legends summoner information by summoner name")
    public Summoner getSummonerByName(
            @ToolParameter(name = "platform", description = "The game platform (e.g., NA1, EUW1)")
            @ToolParameterType(type = "string")
            String platformStr,
            
            @ToolParameter(name = "summonerName", description = "The name of the summoner")
            @ToolParameterType(type = "string")
            String summonerName) {
        
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by name: {} on platform: {}", summonerName, platform);
        return summonerService.getSummonerByName(platform, summonerName);
    }
    
    @Tool(name = "get_lol_summoner_by_puuid")
    @ToolDescription("Get League of Legends summoner information by PUUID")
    public Summoner getSummonerByPuuid(
            @ToolParameter(name = "platform", description = "The game platform (e.g., NA1, EUW1)")
            @ToolParameterType(type = "string")
            String platformStr,
            
            @ToolParameter(name = "puuid", description = "The PUUID of the summoner")
            @ToolParameterType(type = "string")
            String puuid) {
        
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by PUUID: {} on platform: {}", puuid, platform);
        return summonerService.getSummonerByPuuid(platform, puuid);
    }
    
    @Tool(name = "get_lol_summoner_by_id")
    @ToolDescription("Get League of Legends summoner information by summoner ID")
    public Summoner getSummonerById(
            @ToolParameter(name = "platform", description = "The game platform (e.g., NA1, EUW1)")
            @ToolParameterType(type = "string")
            String platformStr,
            
            @ToolParameter(name = "summonerId", description = "The ID of the summoner")
            @ToolParameterType(type = "string")
            String summonerId) {
        
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by ID: {} on platform: {}", summonerId, platform);
        return summonerService.getSummonerById(platform, summonerId);
    }
}
