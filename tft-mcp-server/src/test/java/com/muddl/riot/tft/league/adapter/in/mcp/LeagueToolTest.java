package com.muddl.riot.tft.league.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.LeagueService;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueToolTest {

    @Mock
    private LeagueService mockLeagueService;

    @InjectMocks
    private LeagueTool leagueTool;

    // tft_league_entries_by_player

    @Test
    void getLeagueEntriesByPlayer_passesPlatformAndPlayerThrough() {
        LeagueEntry entry =
                LeagueEntry.builder().puuid("puuid-1").tier("DIAMOND").build();
        when(mockLeagueService.getLeagueEntriesByPlayer(RiotApiPlatformUri.NA1, "Player#NA1"))
                .thenReturn(List.of(entry));

        assertThat(leagueTool.getLeagueEntriesByPlayer("NA1", "Player#NA1")).containsExactly(entry);
        verify(mockLeagueService).getLeagueEntriesByPlayer(RiotApiPlatformUri.NA1, "Player#NA1");
    }

    @Test
    void getLeagueEntriesByPlayer_normalizesPlatformCase() {
        LeagueEntry entry = LeagueEntry.builder().puuid("puuid-1").build();
        when(mockLeagueService.getLeagueEntriesByPlayer(RiotApiPlatformUri.NA1, "Player#NA1"))
                .thenReturn(List.of(entry));

        assertThat(leagueTool.getLeagueEntriesByPlayer("na1", "Player#NA1")).containsExactly(entry);
        verify(mockLeagueService).getLeagueEntriesByPlayer(RiotApiPlatformUri.NA1, "Player#NA1");
    }

    @Test
    void getLeagueEntriesByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getLeagueEntriesByPlayer("INVALID", "Player#NA1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    // tft_league_apex_by_tier

    @Test
    void getApexLeague_passesPlatformAndTierThrough() {
        LeagueList list = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, null))
                .thenReturn(list);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", null)).isSameAs(list);
        verify(mockLeagueService).getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, null);
    }

    @Test
    void getApexLeague_normalizesCaseForPlatformAndTier() {
        LeagueList list = LeagueList.builder().tier("MASTER").build();
        when(mockLeagueService.getApexLeague(RiotApiPlatformUri.NA1, ApexTier.MASTER, null))
                .thenReturn(list);

        assertThat(leagueTool.getApexLeague("na1", "master", null)).isSameAs(list);
        verify(mockLeagueService).getApexLeague(RiotApiPlatformUri.NA1, ApexTier.MASTER, null);
    }

    @Test
    void getApexLeague_passesCountThrough() {
        LeagueList list = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, 3))
                .thenReturn(list);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", 3)).isSameAs(list);
        verify(mockLeagueService).getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, 3);
    }

    @Test
    void getApexLeague_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getApexLeague("INVALID", "CHALLENGER", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getApexLeague_invalidTier_throws() {
        assertThatThrownBy(() -> leagueTool.getApexLeague("NA1", "BRONZE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    // tft_league_entries_by_tier

    @Test
    void getEntriesByTier_passesArgsThrough_normalizingTierAndDivision() {
        LeagueEntry entry = LeagueEntry.builder().tier("DIAMOND").rank("II").build();
        when(mockLeagueService.getEntriesByTier(RiotApiPlatformUri.NA1, "DIAMOND", "II", 1))
                .thenReturn(List.of(entry));

        assertThat(leagueTool.getEntriesByTier("NA1", "diamond", "ii", 1)).containsExactly(entry);
        verify(mockLeagueService).getEntriesByTier(RiotApiPlatformUri.NA1, "DIAMOND", "II", 1);
    }

    @Test
    void getEntriesByTier_defaultsPageToOne_whenNull() {
        when(mockLeagueService.getEntriesByTier(RiotApiPlatformUri.NA1, "DIAMOND", "II", 1))
                .thenReturn(List.of());

        assertThat(leagueTool.getEntriesByTier("NA1", "DIAMOND", "II", null)).isEmpty();
        verify(mockLeagueService).getEntriesByTier(RiotApiPlatformUri.NA1, "DIAMOND", "II", 1);
    }

    @Test
    void getEntriesByTier_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getEntriesByTier("INVALID", "DIAMOND", "II", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    // tft_league_by_id

    @Test
    void getLeagueById_passesPlatformAndLeagueIdThrough() {
        LeagueList list = LeagueList.builder().leagueId("league-uuid").build();
        when(mockLeagueService.getLeagueById(RiotApiPlatformUri.NA1, "league-uuid"))
                .thenReturn(list);

        assertThat(leagueTool.getLeagueById("NA1", "league-uuid")).isSameAs(list);
        verify(mockLeagueService).getLeagueById(RiotApiPlatformUri.NA1, "league-uuid");
    }

    @Test
    void getLeagueById_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getLeagueById("INVALID", "league-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    // tft_league_rated_ladder_by_queue

    @Test
    void getRatedLadder_passesPlatformAndQueueThrough() {
        RatedLadderEntry e =
                RatedLadderEntry.builder().puuid("p").ratedTier("BLUE").build();
        when(mockLeagueService.getRatedLadder(RiotApiPlatformUri.NA1, "RANKED_TFT_TURBO"))
                .thenReturn(List.of(e));

        assertThat(leagueTool.getRatedLadder("NA1", "RANKED_TFT_TURBO")).containsExactly(e);
        verify(mockLeagueService).getRatedLadder(RiotApiPlatformUri.NA1, "RANKED_TFT_TURBO");
    }

    @Test
    void getRatedLadder_defaultsQueue_whenNull() {
        RatedLadderEntry e = RatedLadderEntry.builder().puuid("p").build();
        when(mockLeagueService.getRatedLadder(RiotApiPlatformUri.NA1, "RANKED_TFT_TURBO"))
                .thenReturn(List.of(e));

        assertThat(leagueTool.getRatedLadder("NA1", null)).containsExactly(e);
        verify(mockLeagueService).getRatedLadder(RiotApiPlatformUri.NA1, "RANKED_TFT_TURBO");
    }

    @Test
    void getRatedLadder_defaultsQueue_whenBlank() {
        RatedLadderEntry e = RatedLadderEntry.builder().puuid("p").build();
        when(mockLeagueService.getRatedLadder(RiotApiPlatformUri.NA1, "RANKED_TFT_TURBO"))
                .thenReturn(List.of(e));

        assertThat(leagueTool.getRatedLadder("NA1", "  ")).containsExactly(e);
        verify(mockLeagueService).getRatedLadder(RiotApiPlatformUri.NA1, "RANKED_TFT_TURBO");
    }

    @Test
    void getRatedLadder_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getRatedLadder("INVALID", "RANKED_TFT_TURBO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
