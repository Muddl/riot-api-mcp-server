package com.muddl.riot.lol.league.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.LeagueService;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for League of Legends ranked-league data. Born on sub-project 1a's final tool
 * convention: {@code lol_league_*} names and a single {@code player} param resolved in the service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueTool {

    private static final String DEFAULT_QUEUE = "RANKED_SOLO_5x5";

    private final LeagueService leagueService;

    @McpTool(
            name = "lol_league_entries_by_player",
            description = "Get a League of Legends player's ranked-league entries (one per ranked queue).")
    public List<LeagueEntry> getLeagueEntriesByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting league entries for a player on platform: {}", platform);
        return leagueService.getLeagueEntriesByPlayer(platform, player);
    }

    @McpTool(
            name = "lol_league_apex_by_tier",
            description =
                    "Get a League of Legends apex league (CHALLENGER, GRANDMASTER, or MASTER) for a ranked queue. Returns the top 10 entries by league points unless a larger count is requested.")
    public LeagueList getApexLeague(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The apex tier: CHALLENGER, GRANDMASTER, or MASTER", required = true)
                    String tierStr,
            @McpToolParam(
                            description = "The ranked queue, e.g. RANKED_SOLO_5x5 (default) or RANKED_FLEX_SR",
                            required = false)
                    String queueStr,
            @McpToolParam(
                            description = "Optional: return only the top N entries by league points; defaults to 10",
                            required = false)
                    Integer count) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        ApexTier tier = ApexTier.valueOf(tierStr.toUpperCase());
        String queue = (queueStr == null || queueStr.isBlank()) ? DEFAULT_QUEUE : queueStr;
        log.info("MCP Tool - Getting {} apex league for queue {} on platform: {}", tier, queue, platform);
        return leagueService.getApexLeague(platform, tier, queue, count);
    }
}
