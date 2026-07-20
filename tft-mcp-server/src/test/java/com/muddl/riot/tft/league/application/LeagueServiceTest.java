package com.muddl.riot.tft.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeagueServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryLeaguePort port = new InMemoryLeaguePort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final LeagueService service = new LeagueService(port, resolver);

    @Test
    void getLeagueEntriesByPlayer_resolvesPlayer_thenReturnsEntries() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn("puuid-1");
        LeagueEntry entry = LeagueEntry.builder()
                .queueType("RANKED_TFT")
                .tier("DIAMOND")
                .puuid("puuid-1")
                .build();
        port.putEntries("puuid-1", List.of(entry));

        assertThat(service.getLeagueEntriesByPlayer(PLATFORM, "Player#NA1")).containsExactly(entry);
    }

    @Test
    void getLeagueEntriesByPlayer_returnsEmpty_whenUnranked() {
        when(resolver.resolvePuuid("raw")).thenReturn("raw");
        assertThat(service.getLeagueEntriesByPlayer(PLATFORM, "raw")).isEmpty();
    }

    @Test
    void getApexLeague_delegatesToPort() {
        LeagueList expected = LeagueList.builder().tier("CHALLENGER").build();
        port.putApex(ApexTier.CHALLENGER, expected);
        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER)).isSameAs(expected);
    }

    @Test
    void getLeagueById_delegatesToPort() {
        LeagueList expected = LeagueList.builder().leagueId("league-uuid").build();
        port.putLeague("league-uuid", expected);
        assertThat(service.getLeagueById(PLATFORM, "league-uuid")).isSameAs(expected);
    }

    @Test
    void getRatedLadder_delegatesToPort() {
        RatedLadderEntry e = RatedLadderEntry.builder()
                .puuid("p")
                .ratedTier("BLUE")
                .ratedRating(1500)
                .build();
        port.putLadder("RANKED_TFT_TURBO", List.of(e));
        assertThat(service.getRatedLadder(PLATFORM, "RANKED_TFT_TURBO")).containsExactly(e);
    }
}
