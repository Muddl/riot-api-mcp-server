package com.muddl.riot.tft.account.adapter.in.mcp;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool for Riot account lookups from the TFT server. Takes a single {@code player} — a
 * {@code GameName#TAG} Riot ID or a raw PUUID — and returns the account. Disambiguates on {@code #}
 * and calls the account service directly; this tool is on the account-domain allow-list.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotAccountTool {

    private final RiotAccountService accountService;

    @McpTool(
            name = "tft_account_by_player",
            description = "Get Riot account information by player (a Riot ID as GameName#TAG, or a raw PUUID).")
    public RiotAccount getAccountByPlayer(
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        String trimmed = player.trim();
        if (trimmed.indexOf('#') < 0) {
            log.info("MCP Tool - Getting account by PUUID");
            return accountService.getAccountByPuuid(trimmed);
        }
        String[] parts = trimmed.split("#", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        String gameName = parts[0].trim();
        String tagLine = parts[1].trim();
        log.info("MCP Tool - Getting account by Riot ID: {}#{}", gameName, tagLine);
        return accountService.getAccountByRiotId(gameName, tagLine);
    }

    private static String unparseableMessage(String player) {
        return "Cannot parse player '" + player + "'. Provide a Riot ID as GameName#TAG "
                + "(for example Faker#KR1) or a raw PUUID.";
    }
}
