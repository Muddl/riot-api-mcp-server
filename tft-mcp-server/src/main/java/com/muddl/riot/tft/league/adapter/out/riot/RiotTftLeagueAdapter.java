package com.muddl.riot.tft.league.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot TFT-League-V1 API adapter. League endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftLeagueAdapter implements LeaguePort {

    private final RiotApiClient riotApiClient;

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        LeagueEntry[] entries = riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(LeagueEntry[].class);
        return entries == null ? List.of() : List.of(entries);
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/{tier}", tier.leaguePath())
                .retrieve()
                .body(LeagueList.class);
    }

    @Override
    public List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page) {
        LeagueEntry[] entries = riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/entries/{tier}/{division}?page={page}", tier, division, page)
                .retrieve()
                .body(LeagueEntry[].class);
        return entries == null ? List.of() : List.of(entries);
    }

    @Override
    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/leagues/{leagueId}", leagueId)
                .retrieve()
                .body(LeagueList.class);
    }

    @Override
    public List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue) {
        RatedLadderEntry[] ladder = riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/rated-ladders/{queue}/top", queue)
                .retrieve()
                .body(RatedLadderEntry[].class);
        return ladder == null ? List.of() : List.of(ladder);
    }
}
