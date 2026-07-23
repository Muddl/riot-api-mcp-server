package com.muddl.riot.tft.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueItem;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
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

    private static LeagueList ladderOf(int size) {
        return LeagueList.builder()
                .tier("CHALLENGER")
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
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        LeagueList result = service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, null);

        assertThat(result.getEntries()).hasSize(10);
        assertThat(result.getTotalEntries()).isEqualTo(300);
    }

    @Test
    void getApexLeague_sortsByLeaguePointsDescending() {
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, 3).getEntries())
                .extracting(LeagueItem::getLeaguePoints)
                .containsExactly(299, 298, 297);
    }

    @Test
    void getApexLeague_honoursExplicitCount() {
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, 50).getEntries())
                .hasSize(50);
    }

    @Test
    void getApexLeague_zeroOrNegativeCount_clampsToDefault() {
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, 0).getEntries())
                .hasSize(10);
        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, -1).getEntries())
                .hasSize(10);
    }

    @Test
    void getApexLeague_nullLeaguePoints_sortLastWithoutThrowing() {
        // TFT's LeagueItem.leaguePoints is a boxed Integer and Riot may omit it —
        // a comparingInt comparator would NPE here.
        List<LeagueItem> mixed = new ArrayList<>();
        mixed.add(LeagueItem.builder().puuid("no-lp").leaguePoints(null).build());
        mixed.add(LeagueItem.builder().puuid("low").leaguePoints(100).build());
        mixed.add(LeagueItem.builder().puuid("high").leaguePoints(900).build());
        port.putApex(
                ApexTier.CHALLENGER,
                LeagueList.builder().tier("CHALLENGER").entries(mixed).build());

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, null).getEntries())
                .extracting(LeagueItem::getPuuid)
                .containsExactly("high", "low", "no-lp");
    }

    @Test
    void getApexLeague_nullLeague_returnsNull() {
        assertThat(service.getApexLeague(PLATFORM, ApexTier.MASTER, null)).isNull();
    }

    @Test
    void getEntriesByTier_delegatesToPort() {
        LeagueEntry entry =
                LeagueEntry.builder().queueType("RANKED_TFT").tier("GOLD").build();
        port.putEntriesByTier("GOLD", "II", 2, List.of(entry));

        assertThat(service.getEntriesByTier(PLATFORM, "GOLD", "II", 2)).containsExactly(entry);
        assertThat(service.getEntriesByTier(PLATFORM, "GOLD", "I", 2)).isEmpty();
    }

    @Test
    void getLeagueById_stampsTotal_withoutTruncatingOrReordering() {
        port.putLeague("league-uuid", ladderOf(300));

        LeagueList result = service.getLeagueById(PLATFORM, "league-uuid");

        assertThat(result.getEntries()).hasSize(300);
        assertThat(result.getTotalEntries()).isEqualTo(300);
        // Original ascending order is preserved — league-by-id is out of scope for the bound.
        assertThat(result.getEntries().get(0).getLeaguePoints()).isZero();
    }

    @Test
    void getLeagueById_nullLeague_returnsNull() {
        assertThat(service.getLeagueById(PLATFORM, "missing")).isNull();
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
