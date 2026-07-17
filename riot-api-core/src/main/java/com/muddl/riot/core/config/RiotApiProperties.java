package com.muddl.riot.core.config;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import java.time.Duration;
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

    /** How many times to retry a 429 before giving up and surfacing the error. */
    private int maxRetries = 3;

    /** Backoff used between 429 retries when the response carries no usable {@code Retry-After}. */
    private Duration retryBackoff = Duration.ofSeconds(1);

    /**
     * Upper bound on a single 429 backoff, even when {@code Retry-After} asks for longer. Riot's
     * application-rate-limit 429 can legitimately specify up to ~120s; this caps pathological or
     * hostile header values so one tool call cannot block a thread indefinitely.
     */
    private Duration maxRetryBackoff = Duration.ofSeconds(120);
}
