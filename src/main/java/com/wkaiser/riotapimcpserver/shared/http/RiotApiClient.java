package com.wkaiser.riotapimcpserver.shared.http;

import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * Central factory for Riot API {@link RestClient} instances. This is the single place
 * that knows the Riot authentication header, base-URL assembly, and error-to-exception
 * mapping — replacing the per-service HTTP plumbing that was previously copy-pasted.
 */
@Component
@RequiredArgsConstructor
public class RiotApiClient {

    private static final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    private final RiotApiProperties properties;

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
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RiotApiException("Riot API error: " + body, response.getStatusCode().value());
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
