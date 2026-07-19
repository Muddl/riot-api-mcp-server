package com.muddl.riot.lol.clash.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.ClashService;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for League of Legends Clash registrations. Player-keyed: a single {@code player} param. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClashTool {

    private final ClashService clashService;

    @McpTool(
            name = "lol_clash_by_player",
            description =
                    "Get a League of Legends player's active Clash team registrations (team, position, and role per tournament).")
    public List<ClashPlayer> getClashByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting clash registrations for a player on platform: {}", platform);
        return clashService.getClashByPlayer(platform, player);
    }
}
