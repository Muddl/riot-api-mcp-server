package com.muddl.riot.lol;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.lol.account.adapter.in.mcp.RiotAccountTool;
import com.muddl.riot.lol.analytics.adapter.in.mcp.AnalyticsTool;
import com.muddl.riot.lol.league.adapter.in.mcp.LeagueTool;
import com.muddl.riot.lol.spectator.adapter.in.mcp.LiveGameTool;
import com.muddl.riot.lol.summoner.adapter.in.mcp.SummonerTool;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/**
 * Guards the public MCP contract: exactly the six tools currently shipped, each named
 * {@code <game>_<context>_<action>}, every player-keyed tool taking a single {@code player} param.
 * See [ADR-0009](../../../../../../../../docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md). If this
 * test fails, a tool's name changed without the contract (and this list) being updated to match.
 */
class McpToolInventoryTest {

    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "lol_account_by_player",
            "lol_summoner_by_player",
            "lol_spectator_current_game_by_player",
            "lol_analytics_player_matches",
            "lol_league_entries_by_player",
            "lol_league_apex_by_tier");

    @Test
    void tool_inventory_is_unchanged() {
        Set<String> actual = Stream.of(
                        RiotAccountTool.class,
                        AnalyticsTool.class,
                        LiveGameTool.class,
                        SummonerTool.class,
                        LeagueTool.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
    }
}
