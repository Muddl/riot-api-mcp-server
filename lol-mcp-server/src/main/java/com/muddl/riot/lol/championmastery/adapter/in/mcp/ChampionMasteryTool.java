package com.muddl.riot.lol.championmastery.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.ChampionMasteryService;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for League of Legends champion mastery. Player-keyed: a single {@code player} param. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChampionMasteryTool {

    private final ChampionMasteryService masteryService;

    @McpTool(
            name = "lol_champion_mastery_by_player",
            description =
                    "Get a League of Legends player's champion masteries, sorted by points. Optionally limit to the top N.")
    public List<ChampionMastery> getMasteryByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "Optional: return only the top N champions by mastery points", required = false)
                    Integer count) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting champion mastery for a player on platform: {}", platform);
        return masteryService.getMasteryByPlayer(platform, player, count);
    }
}
