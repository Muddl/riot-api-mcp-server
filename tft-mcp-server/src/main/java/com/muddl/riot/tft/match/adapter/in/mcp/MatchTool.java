package com.muddl.riot.tft.match.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.application.MatchService;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for TFT match data (TFT-Match-V1, region-routed). {@code tft_match_ids_by_player} is
 * player-keyed; {@code tft_match_by_id} is keyed by match ID and takes no player (ADR-0014).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTool {

    private final MatchService matchService;

    @McpTool(
            name = "tft_match_ids_by_player",
            description = "Get a Teamfight Tactics player's recent match IDs, most recent first.")
    public List<String> getMatchIdsByPlayer(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "Number of match IDs to return, 1-100, defaults to 20", required = false)
                    Integer count,
            @McpToolParam(description = "Number of match IDs to skip (for paging), defaults to 0", required = false)
                    Integer start) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        int resolvedCount = count == null ? 20 : count;
        int resolvedStart = start == null ? 0 : start;
        log.info("MCP Tool - Getting TFT match IDs for a player in region: {}", region);
        return matchService.getMatchIdsByPlayer(region, player, resolvedCount, resolvedStart);
    }

    @McpTool(
            name = "tft_match_by_id",
            description = "Get the full detail of one Teamfight Tactics match by its match ID.")
    public TftMatch getMatchById(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The match ID, e.g. NA1_4600000001", required = true) String matchId) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        log.info("MCP Tool - Getting TFT match detail for match ID: {}", matchId);
        return matchService.getMatchById(region, matchId);
    }
}
