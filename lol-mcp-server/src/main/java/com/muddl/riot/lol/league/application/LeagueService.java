package com.muddl.riot.lol.league.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot League-V4 ranked data. Depends on its own {@link LeaguePort} and the
 * shared {@link PlayerIdentityResolver} — never on a {@code RestClient} or another context's
 * service. This is the reference shape every player-keyed context in sub-project 1b copies (see the
 * 1a handoff contract): the tool passes a single {@code player}, and the service resolves it to a
 * PUUID here before calling the port.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeaguePort leaguePort;
    private final PlayerIdentityResolver identityResolver;

    public List<LeagueEntry> getLeagueEntriesByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching league entries on platform: {}", platform);
        return leaguePort.getLeagueEntriesByPuuid(platform, puuid);
    }

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue) {
        log.info("Fetching {} apex league for queue {} on platform: {}", tier, queue, platform);
        return leaguePort.getApexLeague(platform, tier, queue);
    }
}
