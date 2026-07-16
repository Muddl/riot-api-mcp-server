package com.muddl.riot.lol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Riot API MCP Server.
 * This application serves as a middleware between AI models and the Riot Games API,
 * providing tools for retrieving and analyzing game data.
 */
@SpringBootApplication
public class LolMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LolMcpServerApplication.class, args);
    }
}
