package com.wkaiser.riotapimcpserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riotapimcpserver.account.adapter.in.mcp.RiotAccountTool;
import com.wkaiser.riotapimcpserver.analytics.adapter.in.mcp.AnalyticsTool;
import com.wkaiser.riotapimcpserver.spectator.adapter.in.mcp.LiveGameTool;
import com.wkaiser.riotapimcpserver.summoner.adapter.in.mcp.SummonerTool;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/**
 * Guards the public MCP contract across the monorepo restructure. The tool surface is
 * explicitly frozen for this cycle: the same 10 tools, same names. If this test fails
 * during a move, the move changed behavior and is wrong.
 */
class McpToolInventoryTest {

    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "get_riot_account_by_riot_id",
            "get_riot_account_by_puuid",
            "get_lol_summoner_by_name",
            "get_lol_summoner_by_puuid",
            "get_lol_summoner_by_id",
            "get_current_game_by_summoner_name",
            "get_current_game_by_summoner_id",
            "get_featured_games",
            "check_if_summoner_in_game",
            "get_lol_player_match_analytics");

    @Test
    void tool_inventory_is_unchanged() {
        Set<String> actual = Stream.of(RiotAccountTool.class, AnalyticsTool.class, LiveGameTool.class, SummonerTool.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
    }
}
