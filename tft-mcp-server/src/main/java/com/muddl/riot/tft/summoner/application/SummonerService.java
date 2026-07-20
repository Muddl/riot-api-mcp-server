package com.muddl.riot.tft.summoner.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for TFT summoner lookups. Resolves the caller's {@code player} to a PUUID via
 * the shared {@link PlayerIdentityResolver}. {@code getSummonerByPuuid} is retained for the
 * analytics composer, which already holds a PUUID.
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
        log.info("Fetching TFT summoner for PUUID: {} on platform: {}", puuid, platform);
        return summonerPort.getSummonerByPuuid(platform, puuid);
    }
}
