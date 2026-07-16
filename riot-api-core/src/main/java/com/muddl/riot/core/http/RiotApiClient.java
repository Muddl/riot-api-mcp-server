package com.muddl.riot.core.http;

import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.core.exception.RiotApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * Central factory for Riot API {@link RestClient} instances. This is the single place
 * that knows the Riot authentication header, base-URL assembly, and error-to-exception
 * mapping — replacing the per-service HTTP plumbing that was previously copy-pasted.
 */
public class RiotApiClient {

    private static final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    private final RiotApiProperties properties;
    private final BackoffSleeper sleeper;

    public RiotApiClient(RiotApiProperties properties, BackoffSleeper sleeper) {
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /**
     * Convenience for callers that do not control backoff timing (production default). Uses a real
     * {@link BackoffSleeper#realTime()} sleeper. Tests that exercise retry pass a recording sleeper
     * through the two-arg constructor instead.
     */
    public RiotApiClient(RiotApiProperties properties) {
        this(properties, BackoffSleeper.realTime());
    }

    /** A client for region-routed endpoints (account, match). */
    public RestClient regional(RiotApiRegionUri region) {
        return clientFor(region.getRegionUri());
    }

    /** A client for platform-routed endpoints (summoner, spectator). */
    public RestClient platform(RiotApiPlatformUri platform) {
        return clientFor(platform.getPlatformUri());
    }

    private RestClient clientFor(String host) {
        return RestClient.builder()
                .baseUrl(resolveBaseUrl(host))
                .defaultHeader(RIOT_TOKEN_HEADER, properties.getApiKey())
                .requestInterceptor(
                        new RetryOn429Interceptor(properties.getMaxRetries(), properties.getRetryBackoff(), sleeper))
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw RiotApiException.forStatus(response.getStatusCode().value(), body);
                })
                .build();
    }

    private String resolveBaseUrl(String host) {
        String override = properties.getBaseUrlOverride();
        if (override != null && !override.isBlank()) {
            return override;
        }
        return "https://" + host;
    }
}
