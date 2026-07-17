package com.muddl.riot.core.config;

import com.muddl.riot.core.http.BackoffSleeper;
import com.muddl.riot.core.http.RiotApiClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the shared Riot HTTP layer. This library is consumed as a
 * dependency, never component-scanned, so every bean is declared explicitly here rather
 * than discovered via stereotypes. {@link ConditionalOnMissingBean} lets a consuming
 * application override any of them.
 */
@AutoConfiguration
@EnableConfigurationProperties(RiotApiProperties.class)
public class RiotApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BackoffSleeper backoffSleeper() {
        return BackoffSleeper.realTime();
    }

    @Bean
    @ConditionalOnMissingBean
    public RiotApiClient riotApiClient(RiotApiProperties properties, BackoffSleeper sleeper) {
        return new RiotApiClient(properties, sleeper);
    }
}
