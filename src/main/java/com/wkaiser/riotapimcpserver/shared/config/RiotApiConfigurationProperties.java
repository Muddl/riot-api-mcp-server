package com.wkaiser.riotapimcpserver.shared.config;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Riot API.
 * These properties are loaded from application.yml or application.properties
 * with the prefix "riot".
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "riot")
public class RiotApiConfigurationProperties {
    private String apiKey;
    private RiotApiRegionUri region;
}
