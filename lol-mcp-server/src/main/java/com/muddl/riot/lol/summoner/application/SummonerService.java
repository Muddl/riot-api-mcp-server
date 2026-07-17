package com.muddl.riot.lol.summoner.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.application.port.SummonerPort;
import com.muddl.riot.lol.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends summoner lookups. Delegates retrieval
 * to the outbound {@link SummonerPort}; holds no HTTP concerns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final SummonerPort summonerPort;

    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching summoner for PUUID: {} on platform: {}", puuid, platform);
        return summonerPort.getSummonerByPuuid(platform, puuid);
    }
}
