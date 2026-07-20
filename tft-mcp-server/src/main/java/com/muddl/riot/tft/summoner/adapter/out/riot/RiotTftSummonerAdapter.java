package com.muddl.riot.tft.summoner.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot TFT-Summoner-V1 API adapter. Platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftSummonerAdapter implements SummonerPort {

    private final RiotApiClient riotApiClient;

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/summoner/v1/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(Summoner.class);
    }
}
