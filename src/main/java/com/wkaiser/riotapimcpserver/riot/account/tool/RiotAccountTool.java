package com.wkaiser.riotapimcpserver.riot.account.tool;

import com.wkaiser.riotapimcpserver.riot.account.dto.RiotAccount;
import com.wkaiser.riotapimcpserver.riot.account.service.RiotAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing Riot account functionality.
 * Exposes methods that can be called by AI models via the MCP server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotAccountTool {

    private final RiotAccountService accountService;

    @McpTool(name = "get_riot_account_by_riot_id", description = "Get Riot account information by Riot ID (gameName#tagLine)")
    public RiotAccount getAccountByRiotId(
            @McpToolParam(description = "The player's in-game name", required = true) String gameName,
            @McpToolParam(description = "The player's tag line (e.g. NA1)", required = true) String tagLine) {
        log.info("MCP Tool - Getting account by Riot ID: {}#{}", gameName, tagLine);
        return accountService.getAccountByRiotId(gameName, tagLine);
    }

    @McpTool(name = "get_riot_account_by_puuid", description = "Get Riot account information by PUUID")
    public RiotAccount getAccountByPuuid(
            @McpToolParam(description = "The player's PUUID (encrypted universally unique ID)", required = true) String puuid) {
        log.info("MCP Tool - Getting account by PUUID: {}", puuid);
        return accountService.getAccountByPuuid(puuid);
    }
}
