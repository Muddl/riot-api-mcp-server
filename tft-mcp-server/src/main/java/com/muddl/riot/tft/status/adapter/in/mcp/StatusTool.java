package com.muddl.riot.tft.status.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.application.StatusService;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for TFT platform status. Non-player-keyed (ADR-0014). */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusTool {

    private final StatusService statusService;

    @McpTool(
            name = "tft_status_platform",
            description =
                    "Get the operational status (current maintenances and incidents) of a Teamfight Tactics platform.")
    public PlatformStatus getPlatformStatus(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT platform status on platform: {}", platform);
        return statusService.getPlatformStatus(platform);
    }
}
