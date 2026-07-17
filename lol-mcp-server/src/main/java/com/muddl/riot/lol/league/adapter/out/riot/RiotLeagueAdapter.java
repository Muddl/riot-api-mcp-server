package com.muddl.riot.lol.league.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot League-V4 API adapter. League endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotLeagueAdapter implements LeaguePort {

    private final RiotApiClient riotApiClient;

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        LeagueEntry[] entries = riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/league/v4/entries/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(LeagueEntry[].class);
        return entries == null ? List.of() : List.of(entries);
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/league/v4/{leaguePath}/by-queue/{queue}", tier.leaguePath(), queue)
                .retrieve()
                .body(LeagueList.class);
    }
}
