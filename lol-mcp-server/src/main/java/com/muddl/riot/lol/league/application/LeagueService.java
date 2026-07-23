package com.muddl.riot.lol.league.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueItem;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.Comparator;
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

    /** Default number of apex entries returned when the caller does not specify a count. */
    private static final int DEFAULT_APEX_ENTRIES = 10;

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue, Integer count) {
        log.info("Fetching {} apex league for queue {} on platform: {}", tier, queue, platform);
        int limit = (count == null || count <= 0) ? DEFAULT_APEX_ENTRIES : count;
        return boundEntries(leaguePort.getApexLeague(platform, tier, queue), limit);
    }

    /**
     * Returns a copy of {@code league} holding only the top {@code limit} entries by league points,
     * with {@code totalEntries} stamped to the pre-truncation size.
     *
     * <p>Riot's League-V4 apex endpoint has no server-side count parameter (unlike
     * Champion-Mastery-V4, where the bound is pushed down to the port), so the bound is applied
     * here in the application layer. Riot does not guarantee entry order, so entries are sorted
     * before slicing — otherwise "top N" is meaningless and a discovered subject would change
     * between runs. See ADR-0016.
     */
    private static LeagueList boundEntries(LeagueList league, int limit) {
        if (league == null) {
            return null;
        }
        List<LeagueItem> entries = league.getEntries();
        if (entries == null) {
            return league.toBuilder().entries(List.of()).totalEntries(0).build();
        }
        return league.toBuilder()
                .entries(entries.stream()
                        .sorted(Comparator.comparingInt(LeagueItem::getLeaguePoints)
                                .reversed())
                        .limit(limit)
                        .toList())
                .totalEntries(entries.size())
                .build();
    }
}
