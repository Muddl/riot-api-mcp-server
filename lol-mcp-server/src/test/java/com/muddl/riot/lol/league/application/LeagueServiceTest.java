package com.muddl.riot.lol.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeagueServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryLeaguePort leaguePort = new InMemoryLeaguePort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final LeagueService leagueService = new LeagueService(leaguePort, resolver);

    @Test
    void getLeagueEntriesByPlayer_resolvesPlayer_thenReturnsEntries() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        LeagueEntry entry = LeagueEntry.builder()
                .queueType("RANKED_SOLO_5x5")
                .tier("CHALLENGER")
                .rank("I")
                .puuid("faker-puuid")
                .build();
        leaguePort.putEntries("faker-puuid", List.of(entry));

        assertThat(leagueService.getLeagueEntriesByPlayer(PLATFORM, "Faker#KR1"))
                .containsExactly(entry);
    }

    @Test
    void getLeagueEntriesByPlayer_returnsEmpty_whenUnranked() {
        when(resolver.resolvePuuid("puuid-raw")).thenReturn("puuid-raw");

        assertThat(leagueService.getLeagueEntriesByPlayer(PLATFORM, "puuid-raw"))
                .isEmpty();
    }

    @Test
    void getApexLeague_delegatesToPort() {
        LeagueList expected = LeagueList.builder().tier("CHALLENGER").build();
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", expected);

        assertThat(leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5"))
                .isSameAs(expected);
    }
}
