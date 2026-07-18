package com.muddl.riot.lol.champion.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Champion-V3 API adapter. Champion-rotation is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotChampionAdapter implements ChampionPort {

    private final RiotApiClient riotApiClient;

    @Override
    public ChampionRotation getChampionRotation(RiotApiPlatformUri platform) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/platform/v3/champion-rotations")
                .retrieve()
                .body(ChampionRotation.class);
    }
}
