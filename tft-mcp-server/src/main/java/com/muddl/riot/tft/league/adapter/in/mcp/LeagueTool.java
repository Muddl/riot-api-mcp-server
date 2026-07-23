package com.muddl.riot.tft.league.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.LeagueService;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tools for TFT ranked-league data (TFT-League-V1, platform-routed). */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueTool {

    private static final String DEFAULT_RATED_QUEUE = "RANKED_TFT_TURBO";

    private final LeagueService leagueService;

    @McpTool(
            name = "tft_league_entries_by_player",
            description = "Get a Teamfight Tactics player's ranked-league entries (one per ranked queue).")
    public List<LeagueEntry> getLeagueEntriesByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT league entries for a player on platform: {}", platform);
        return leagueService.getLeagueEntriesByPlayer(platform, player);
    }

    @McpTool(
            name = "tft_league_apex_by_tier",
            description =
                    "Get a Teamfight Tactics apex league: CHALLENGER, GRANDMASTER, or MASTER. Returns the top 10 entries by league points unless a larger count is requested.")
    public LeagueList getApexLeague(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The apex tier: CHALLENGER, GRANDMASTER, or MASTER", required = true)
                    String tierStr,
            @McpToolParam(
                            description = "Optional: return only the top N entries by league points; defaults to 10",
                            required = false)
                    Integer count) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        ApexTier tier = ApexTier.valueOf(tierStr.toUpperCase());
        log.info("MCP Tool - Getting TFT {} apex league on platform: {}", tier, platform);
        return leagueService.getApexLeague(platform, tier, count);
    }

    @McpTool(
            name = "tft_league_entries_by_tier",
            description = "Get one page of Teamfight Tactics ranked entries for a tier and division (e.g. DIAMOND II).")
    public List<LeagueEntry> getEntriesByTier(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The tier, e.g. DIAMOND, PLATINUM, GOLD", required = true) String tier,
            @McpToolParam(description = "The division: I, II, III, or IV", required = true) String division,
            @McpToolParam(description = "The page of results, 1-based; defaults to 1", required = false) Integer page) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        int resolvedPage = page == null ? 1 : page;
        log.info(
                "MCP Tool - Getting TFT entries for {} {} page {} on platform: {}",
                tier,
                division,
                resolvedPage,
                platform);
        return leagueService.getEntriesByTier(platform, tier.toUpperCase(), division.toUpperCase(), resolvedPage);
    }

    @McpTool(name = "tft_league_by_id", description = "Get a Teamfight Tactics league by its league UUID.")
    public LeagueList getLeagueById(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The league UUID (from an apex-league response)", required = true)
                    String leagueId) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT league by id on platform: {}", platform);
        return leagueService.getLeagueById(platform, leagueId);
    }

    @McpTool(
            name = "tft_league_rated_ladder_by_queue",
            description =
                    "Get the top of a Teamfight Tactics rated (Hyper Roll) ladder for a queue, defaults to RANKED_TFT_TURBO.")
    public List<RatedLadderEntry> getRatedLadder(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The rated queue, defaults to RANKED_TFT_TURBO", required = false)
                    String queueStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        String queue = (queueStr == null || queueStr.isBlank()) ? DEFAULT_RATED_QUEUE : queueStr;
        log.info("MCP Tool - Getting TFT rated ladder for queue {} on platform: {}", queue, platform);
        return leagueService.getRatedLadder(platform, queue);
    }
}
