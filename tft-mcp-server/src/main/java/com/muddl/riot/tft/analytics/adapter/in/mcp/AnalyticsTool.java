package com.muddl.riot.tft.analytics.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.analytics.application.AnalyticsService;
import com.muddl.riot.tft.analytics.domain.PlayerMatchAnalytics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for aggregated TFT recent-match analytics (composes summoner + match). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsTool {

    private final AnalyticsService analyticsService;

    @McpTool(
            name = "tft_analytics_player_matches",
            description = "Get aggregated analytics of a Teamfight Tactics player's recent matches "
                    + "(average placement, top-4 rate, most-played traits and units).")
    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE", required = true) String regionStr,
            @McpToolParam(description = "Number of recent matches to analyze, 1-100, defaults to 10", required = false)
                    Integer matchCount) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        int count = matchCount == null ? 10 : Math.min(100, Math.max(1, matchCount));
        log.info("MCP Tool - Generating TFT match analytics for a player on platform: {}", platform);
        return analyticsService.getPlayerMatchAnalytics(player, platform, region, count);
    }
}
