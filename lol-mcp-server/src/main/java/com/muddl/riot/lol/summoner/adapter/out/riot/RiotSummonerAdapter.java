package com.muddl.riot.lol.summoner.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.summoner.application.port.SummonerPort;
import com.muddl.riot.lol.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Summoner-V4 API adapter. Summoner endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotSummonerAdapter implements SummonerPort {

    private final RiotApiClient riotApiClient;

    @Override
    public Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/summoner/v4/summoners/by-name/{summonerName}", summonerName)
                .retrieve()
                .body(Summoner.class);
    }

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(Summoner.class);
    }

    @Override
    public Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/summoner/v4/summoners/{summonerId}", summonerId)
                .retrieve()
                .body(Summoner.class);
    }
}
