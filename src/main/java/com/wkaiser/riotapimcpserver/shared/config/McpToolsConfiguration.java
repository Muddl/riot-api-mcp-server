package com.wkaiser.riotapimcpserver.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.mcp.server.tool.ToolsRegistry;

/**
 * Configuration class for the MCP server tools.
 * Registers all Riot API tools with the MCP server.
 */
@Slf4j
@Configuration
public class McpToolsConfiguration {
    
    /**
     * Registers all tools with the MCP server.
     * Tools are automatically discovered through component scanning,
     * but this registry allows for additional configuration.
     */
    @Bean
    public ToolsRegistry toolsRegistry() {
        log.info("Initializing Riot API MCP tools registry");
        
        // The actual implementation registers tools using Spring component scanning
        // but this configuration provides a namespace and additional metadata
        return new ToolsRegistry()
            .withNamespace("riot")
            .withMetadata("description", "Tools for interacting with the Riot Games API")
            .withMetadata("version", "1.0.0");
    }
}
