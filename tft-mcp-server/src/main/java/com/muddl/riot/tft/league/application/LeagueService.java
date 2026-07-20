package com.muddl.riot.tft.league.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Application service for Riot TFT-League-V1 ranked data. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeaguePort leaguePort;
    private final PlayerIdentityResolver identityResolver;

    public List<LeagueEntry> getLeagueEntriesByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching TFT league entries on platform: {}", platform);
        return leaguePort.getLeagueEntriesByPuuid(platform, puuid);
    }

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier) {
        log.info("Fetching TFT {} apex league on platform: {}", tier, platform);
        return leaguePort.getApexLeague(platform, tier);
    }

    public List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page) {
        log.info("Fetching TFT entries for {} {} page {} on platform: {}", tier, division, page, platform);
        return leaguePort.getEntriesByTier(platform, tier, division, page);
    }

    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        log.info("Fetching TFT league by id on platform: {}", platform);
        return leaguePort.getLeagueById(platform, leagueId);
    }

    public List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue) {
        log.info("Fetching TFT rated ladder for queue {} on platform: {}", queue, platform);
        return leaguePort.getRatedLadder(platform, queue);
    }
}
