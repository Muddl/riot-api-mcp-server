package com.wkaiser.riotapimcpserver.analytics.adapter.in.mcp;

import com.wkaiser.riotapimcpserver.analytics.application.AnalyticsService;
import com.wkaiser.riotapimcpserver.analytics.domain.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
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

    @McpTool(
            name = "get_lol_player_match_analytics",
            description = "Get detailed analytics of a League of Legends player's recent matches")
    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            @McpToolParam(description = "The player's Riot ID (gameName#tagLine)", required = true) String riotId,
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE", required = true) String regionStr,
            @McpToolParam(description = "Number of recent matches to analyze, 1-100, defaults to 10", required = false)
                    Integer matchCount) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr);

        // Validate and normalize match count
        int count = matchCount == null ? 10 : Math.min(100, Math.max(1, matchCount));

        log.info("MCP Tool - Generating match analytics for player: {} on platform: {}", riotId, platform);
        return analyticsService.getPlayerMatchAnalytics(riotId, platform, region, count);
    }
}
