package com.muddl.riot.lol.championmastery.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Champion-Mastery-V4 API adapter. Mastery endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotChampionMasteryAdapter implements ChampionMasteryPort {

    private static final String BY_PUUID = "/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}";

    private final RiotApiClient riotApiClient;

    @Override
    public List<ChampionMastery> getMasteryByPuuid(RiotApiPlatformUri platform, String puuid, Integer count) {
        String uri = count == null ? BY_PUUID : BY_PUUID + "/top?count=" + count;
        ChampionMastery[] masteries = riotApiClient
                .platform(platform)
                .get()
                .uri(uri, puuid)
                .retrieve()
                .body(ChampionMastery[].class);
        return masteries == null ? List.of() : List.of(masteries);
    }
}
