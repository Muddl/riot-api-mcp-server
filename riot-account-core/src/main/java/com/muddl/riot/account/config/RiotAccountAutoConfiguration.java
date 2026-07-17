package com.muddl.riot.account.config;

import com.github.benmanes.caffeine.cache.Ticker;
import com.muddl.riot.account.adapter.out.riot.RiotAccountRiotAdapter;
import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.application.port.RiotAccountPort;
import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.config.RiotApiAutoConfiguration;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.http.RiotApiClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the shared Riot account context. Consumed as a library by every
 * game server; deliberately exposes no MCP tool (each server owns its own inbound adapter,
 * so tool names can be namespaced per game without collisions).
 */
@AutoConfiguration(after = RiotApiAutoConfiguration.class)
public class RiotAccountAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RiotAccountPort riotAccountPort(RiotApiClient riotApiClient, RiotApiProperties properties) {
        return new RiotAccountRiotAdapter(riotApiClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RiotAccountService riotAccountService(RiotAccountPort riotAccountPort) {
        return new RiotAccountService(riotAccountPort);
    }

    /**
     * Identity resolution is the open, cross-cutting surface of this library — every game server's
     * player-keyed tools depend on it (see ADR-0008). Defaults: a 5-minute TTL bounds Riot-ID
     * staleness, 10 000 entries bound memory, and the system ticker drives expiry. A consumer can
     * override the whole bean via {@link ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean
    public PlayerIdentityResolver playerIdentityResolver(RiotAccountService riotAccountService) {
        return new PlayerIdentityResolver(riotAccountService, Ticker.systemTicker(), Duration.ofMinutes(5), 10_000);
    }
}
