package com.muddl.riot.lol.summoner.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.application.port.SummonerPort;
import com.muddl.riot.lol.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends summoner lookups. Delegates retrieval to the outbound
 * {@link SummonerPort} and resolves the caller's {@code player} reference via the shared
 * {@link PlayerIdentityResolver}; holds no HTTP concerns. {@code getSummonerByPuuid} is retained for
 * {@code AnalyticsService}, which already holds a PUUID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final SummonerPort summonerPort;
    private final PlayerIdentityResolver identityResolver;

    public Summoner getSummonerByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        return getSummonerByPuuid(platform, puuid);
    }

    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching summoner for PUUID: {} on platform: {}", puuid, platform);
        return summonerPort.getSummonerByPuuid(platform, puuid);
    }
}
