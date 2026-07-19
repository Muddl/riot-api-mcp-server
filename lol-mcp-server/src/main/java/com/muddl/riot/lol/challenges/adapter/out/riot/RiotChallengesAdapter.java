package com.muddl.riot.lol.challenges.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot LoL-Challenges-V1 API adapter. Challenge data is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotChallengesAdapter implements ChallengesPort {

    private final RiotApiClient riotApiClient;

    @Override
    public ChallengesPlayerData getPlayerDataByPuuid(RiotApiPlatformUri platform, String puuid) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/challenges/v1/player-data/{puuid}", puuid)
                .retrieve()
                .body(ChallengesPlayerData.class);
    }
}
