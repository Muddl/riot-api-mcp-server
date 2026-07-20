package com.muddl.riot.tft.summoner.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.application.SummonerService;
import com.muddl.riot.tft.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for TFT summoner lookups. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerTool {

    private final SummonerService summonerService;

    @McpTool(
            name = "tft_summoner_by_player",
            description =
                    "Get Teamfight Tactics summoner information by player (a Riot ID as GameName#TAG, or a raw PUUID).")
    public Summoner getSummonerByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT summoner for a player on platform: {}", platform);
        return summonerService.getSummonerByPlayer(platform, player);
    }
}
