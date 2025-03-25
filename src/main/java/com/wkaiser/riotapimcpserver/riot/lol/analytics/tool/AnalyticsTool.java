package com.wkaiser.riotapimcpserver.riot.lol.analytics.tool;

import com.wkaiser.riotapimcpserver.riot.lol.analytics.dto.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.riot.lol.analytics.service.AnalyticsService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.tool.Tool;
import org.springframework.ai.mcp.server.tool.ToolDescription;
import org.springframework.ai.mcp.server.tool.ToolParameter;
import org.springframework.ai.mcp.server.tool.ToolParameterType;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing League of Legends analytics functionality.
 * Exposes methods that can be called by AI models via the MCP server.
 * This tool provides advanced analytics by combining data from multiple Riot API endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsTool {
    
    private final AnalyticsService analyticsService;
    
    @Tool(name = "get_lol_player_match_analytics")
    @ToolDescription("Get detailed analytics of a League of Legends player's recent matches")
    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            @ToolParameter(name = "riotId", description = "The player's Riot ID (format: 'gameName#tagLine')")
            @ToolParameterType(type = "string")
            String riotId,
            
            @ToolParameter(name = "platform", description = "The game platform (e.g., NA1, EUW1)")
            @ToolParameterType(type = "string")
            String platformStr,
            
            @ToolParameter(name = "region", description = "The game region (e.g., AMERICAS, EUROPE)")
            @ToolParameterType(type = "string")
            String regionStr,
            
            @ToolParameter(name = "matchCount", description = "Number of recent matches to analyze (default: 10, max: 100)")
            @ToolParameterType(type = "integer")
            Integer matchCount) {
        
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr);
        
        // Validate and normalize match count
        int count = matchCount == null ? 10 : Math.min(100, Math.max(1, matchCount));
        
        log.info("MCP Tool - Generating match analytics for player: {} on platform: {}", riotId, platform);
        return analyticsService.getPlayerMatchAnalytics(riotId, platform, region, count);
    }
}
