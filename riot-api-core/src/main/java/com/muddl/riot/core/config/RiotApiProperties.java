package com.muddl.riot.core.config;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the Riot API integration, bound from the {@code riot.*}
 * configuration namespace. Replaces scattered {@code @Value("${riot.apiKey}")} usage.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "riot")
public class RiotApiProperties {

    /** Riot API key, sourced from the {@code RIOT_API_KEY} environment variable. */
    private String apiKey;

    /** Default region used for region-routed endpoints (account, match). */
    private RiotApiRegionUri region = RiotApiRegionUri.AMERICAS;

    /**
     * Optional base URL that, when set, overrides the {@code https://<host>} base URL
     * for every Riot client. Used by tests to point requests at a local mock server.
     */
    private String baseUrlOverride;
}
