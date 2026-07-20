package com.muddl.riot.tft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Teamfight Tactics MCP server. Exposes the Riot TFT-v1 API to AI
 * models as MCP tools, built on the shared riot-api-core and riot-account-core libraries.
 */
@SpringBootApplication
public class TftMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TftMcpServerApplication.class, args);
    }
}
