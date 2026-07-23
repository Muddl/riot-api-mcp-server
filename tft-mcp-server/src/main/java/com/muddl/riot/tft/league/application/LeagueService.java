package com.muddl.riot.tft.league.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueItem;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Application service for Riot TFT-League-V1 ranked data. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueService {

    /** Default number of apex entries returned when the caller does not specify a count. */
    private static final int DEFAULT_APEX_ENTRIES = 10;

    private final LeaguePort leaguePort;
    private final PlayerIdentityResolver identityResolver;

    public List<LeagueEntry> getLeagueEntriesByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching TFT league entries on platform: {}", platform);
        return leaguePort.getLeagueEntriesByPuuid(platform, puuid);
    }

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, Integer count) {
        log.info("Fetching TFT {} apex league on platform: {}", tier, platform);
        int limit = (count == null || count <= 0) ? DEFAULT_APEX_ENTRIES : count;
        return boundEntries(leaguePort.getApexLeague(platform, tier), limit);
    }

    public List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page) {
        log.info("Fetching TFT entries for {} {} page {} on platform: {}", tier, division, page, platform);
        return leaguePort.getEntriesByTier(platform, tier, division, page);
    }

    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        log.info("Fetching TFT league by id on platform: {}", platform);
        LeagueList league = leaguePort.getLeagueById(platform, leagueId);
        if (league == null) {
            return null;
        }
        // Stamp the total for consistency with the apex response, but neither slice nor reorder:
        // tft_league_by_id is deliberately out of scope for the bound (ADR-0016).
        return league.toBuilder()
                .totalEntries(
                        league.getEntries() == null ? 0 : league.getEntries().size())
                .build();
    }

    /**
     * Returns a copy of {@code league} holding only the top {@code limit} entries by league points,
     * with {@code totalEntries} stamped to the pre-truncation size. Riot's TFT-League-V1 apex
     * endpoint has no server-side count parameter, so the bound is applied here in the application
     * layer; entries are sorted first because Riot does not guarantee order. See ADR-0016.
     *
     * <p>Unlike the LoL server's equivalent, {@code leaguePoints} here is a boxed {@link Integer}
     * and Riot may omit it, so the comparator is null-safe (nulls sort last) rather than a
     * {@code comparingInt}, which would unbox and throw.
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
                        .sorted(Comparator.comparing(
                                LeagueItem::getLeaguePoints, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(limit)
                        .toList())
                .totalEntries(entries.size())
                .build();
    }

    public List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue) {
        log.info("Fetching TFT rated ladder for queue {} on platform: {}", queue, platform);
        return leaguePort.getRatedLadder(platform, queue);
    }
}
