package com.muddl.riot.lol.champion.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.ChampionService;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for the League of Legends free-to-play champion rotation. Non-player-keyed (ADR-0014). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChampionTool {

    private final ChampionService championService;

    @McpTool(
            name = "lol_champion_rotation",
            description = "Get the current free-to-play champion rotation for a League of Legends platform.")
    public ChampionRotation getChampionRotation(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting champion rotation on platform: {}", platform);
        return championService.getChampionRotation(platform);
    }
}
