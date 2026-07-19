package com.muddl.riot.lol.challenges.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.ChallengesService;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for League of Legends challenge data. Player-keyed: a single {@code player} param. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengesTool {

    private final ChallengesService challengesService;

    @McpTool(
            name = "lol_challenges_by_player",
            description =
                    "Get a League of Legends player's challenge standing: total and per-category points, and per-challenge progress.")
    public ChallengesPlayerData getChallengesByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting challenge data for a player on platform: {}", platform);
        return challengesService.getChallengesByPlayer(platform, player);
    }
}
