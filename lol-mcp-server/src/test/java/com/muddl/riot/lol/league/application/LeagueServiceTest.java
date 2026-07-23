package com.muddl.riot.lol.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueItem;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import java.util.stream.IntStream;
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

    private static LeagueList ladderOf(int size) {
        // leaguePoints ascending with index, so the *last* entries are the top ones —
        // a service that slices without sorting will fail these tests.
        return LeagueList.builder()
                .tier("CHALLENGER")
                .queue("RANKED_SOLO_5x5")
                .entries(IntStream.range(0, size)
                        .mapToObj(i -> LeagueItem.builder()
                                .puuid("puuid-" + i)
                                .leaguePoints(i)
                                .build())
                        .toList())
                .build();
    }

    @Test
    void getApexLeague_capsAtTen_whenCountIsNull() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null);

        assertThat(result.getEntries()).hasSize(10);
        assertThat(result.getTotalEntries()).isEqualTo(300);
        assertThat(result.getTier()).isEqualTo("CHALLENGER");
    }

    @Test
    void getApexLeague_sortsByLeaguePointsDescending() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 3);

        assertThat(result.getEntries()).extracting(LeagueItem::getLeaguePoints).containsExactly(299, 298, 297);
    }

    @Test
    void getApexLeague_honoursExplicitCount() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        assertThat(leagueService
                        .getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 50)
                        .getEntries())
                .hasSize(50);
    }

    @Test
    void getApexLeague_countExceedingSize_returnsEverything() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(7));

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 5000);

        assertThat(result.getEntries()).hasSize(7);
        assertThat(result.getTotalEntries()).isEqualTo(7);
    }

    @Test
    void getApexLeague_zeroOrNegativeCount_clampsToDefault() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        assertThat(leagueService
                        .getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 0)
                        .getEntries())
                .hasSize(10);
        assertThat(leagueService
                        .getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", -4)
                        .getEntries())
                .hasSize(10);
    }

    @Test
    void getApexLeague_nullEntries_yieldsEmptyListAndZeroTotal() {
        leaguePort.putApex(
                ApexTier.CHALLENGER,
                "RANKED_SOLO_5x5",
                LeagueList.builder().tier("CHALLENGER").build());

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null);

        assertThat(result.getEntries()).isEmpty();
        assertThat(result.getTotalEntries()).isZero();
    }

    @Test
    void getApexLeague_nullLeague_returnsNull() {
        assertThat(leagueService.getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_SOLO_5x5", null))
                .isNull();
    }
}
