package com.wkaiser.riotapimcpserver;

import com.wkaiser.riotapimcpserver.shared.config.RiotApiConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main application class for the Riot API MCP Server.
 * This application serves as a middleware between AI models and the Riot Games API,
 * providing tools for retrieving and analyzing game data.
 */
@SpringBootApplication
@EnableConfigurationProperties(RiotApiConfigurationProperties.class)
public class RiotApiMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiotApiMcpServerApplication.class, args);
	}

}
