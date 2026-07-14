package com.wkaiser.riotapimcpserver.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables typed Riot API configuration. All Riot HTTP client construction now lives in
 * {@link com.wkaiser.riotapimcpserver.shared.http.RiotApiClient}.
 */
@Configuration
@EnableConfigurationProperties(RiotApiProperties.class)
public class RiotApiConfiguration {
}
