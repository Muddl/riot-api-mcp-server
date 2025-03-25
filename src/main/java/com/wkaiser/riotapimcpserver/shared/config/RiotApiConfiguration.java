package com.wkaiser.riotapimcpserver.shared.config;

import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * Configuration class for the Riot API REST client.
 * Sets up the RestClient with the appropriate headers and error handling.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RiotApiConfiguration {

    private final RiotApiConfigurationProperties apiConfigurationProperties;

    private final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    @Bean
    public RestClient riotRestClient() {
        log.info("Configuring Riot API client for region: {}", 
                apiConfigurationProperties.getRegion().name());
        
        return RestClient.builder()
                .baseUrl("https://" + apiConfigurationProperties.getRegion().getRegionUri())
                .defaultHeader(RIOT_TOKEN_HEADER, apiConfigurationProperties.getApiKey())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String errorBody = response.getBodyAsString();
                    log.error("Riot API error: {} - {}", response.getStatusCode(), errorBody);
                    throw new RiotApiException("Riot API error: " + errorBody, 
                            response.getStatusCode().value());
                })
                .build();
    }
}
