package com.muddl.riot.lol.match.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.match.application.MatchService;
import com.muddl.riot.lol.match.domain.Match;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for League of Legends match data (Match-V5, region-routed). {@code lol_match_ids_by_player}
 * is player-keyed; {@code lol_match_by_id} is keyed by a match ID and takes no player (ADR-0014).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTool {

    private final MatchService matchService;

    @McpTool(
            name = "lol_match_ids_by_player",
            description = "Get a League of Legends player's recent match IDs, most recent first.")
    public List<String> getMatchIdsByPlayer(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "Number of match IDs to return, 1-100, defaults to 20", required = false)
                    Integer count,
            @McpToolParam(description = "Number of match IDs to skip (for paging), defaults to 0", required = false)
                    Integer start,
            @McpToolParam(description = "Optional: filter by Riot queue ID, e.g. 420 for ranked solo", required = false)
                    Integer queue) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        log.info("MCP Tool - Getting match IDs for a player in region: {}", region);
        return matchService.getMatchIdsByPlayer(region, player, count, start, queue);
    }

    @McpTool(
            name = "lol_match_by_id",
            description = "Get the full detail of one League of Legends match by its match ID.")
    public Match getMatchById(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The match ID, e.g. NA1_4567890123", required = true) String matchId) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        log.info("MCP Tool - Getting match detail for match ID: {}", matchId);
        return matchService.getMatchById(region, matchId);
    }
}
