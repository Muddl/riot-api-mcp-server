package com.muddl.riot.tft;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.tft.account.adapter.in.mcp.RiotAccountTool;
import com.muddl.riot.tft.analytics.adapter.in.mcp.AnalyticsTool;
import com.muddl.riot.tft.league.adapter.in.mcp.LeagueTool;
import com.muddl.riot.tft.match.adapter.in.mcp.MatchTool;
import com.muddl.riot.tft.status.adapter.in.mcp.StatusTool;
import com.muddl.riot.tft.summoner.adapter.in.mcp.SummonerTool;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/**
 * Guards the public MCP contract: exactly the eleven TFT tools, each named
 * {@code tft_<context>_<action>}. If this fails, a tool name changed without this list being updated.
 */
class McpToolInventoryTest {

    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "tft_account_by_player",
            "tft_summoner_by_player",
            "tft_league_entries_by_player",
            "tft_league_apex_by_tier",
            "tft_league_entries_by_tier",
            "tft_league_by_id",
            "tft_league_rated_ladder_by_queue",
            "tft_match_ids_by_player",
            "tft_match_by_id",
            "tft_status_platform",
            "tft_analytics_player_matches");

    @Test
    void tool_inventory_is_unchanged() {
        Set<String> actual = Stream.of(
                        RiotAccountTool.class,
                        SummonerTool.class,
                        LeagueTool.class,
                        MatchTool.class,
                        StatusTool.class,
                        AnalyticsTool.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
    }
}
