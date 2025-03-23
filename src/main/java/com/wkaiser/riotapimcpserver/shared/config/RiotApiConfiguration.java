package com.wkaiser.riotapimcpserver.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class RiotApiConfiguration {

    private RiotApiConfigurationProperties apiConfigurationProperties;

    private final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    @Bean
    public RestClient riotRestClient() {
        return RestClient.builder()
                .baseUrl(apiConfigurationProperties.getRegion().getRegionUri())
                .defaultHeader(RIOT_TOKEN_HEADER, apiConfigurationProperties.getApiKey())
                .build();
    }
}
