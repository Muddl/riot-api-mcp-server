package com.muddl.riot.lol.clash.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.clash.application.port.ClashPort;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Clash-V1 API adapter. Clash endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotClashAdapter implements ClashPort {

    private final RiotApiClient riotApiClient;

    @Override
    public List<ClashPlayer> getPlayersByPuuid(RiotApiPlatformUri platform, String puuid) {
        ClashPlayer[] players = riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/clash/v1/players/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(ClashPlayer[].class);
        return players == null ? List.of() : List.of(players);
    }
}
