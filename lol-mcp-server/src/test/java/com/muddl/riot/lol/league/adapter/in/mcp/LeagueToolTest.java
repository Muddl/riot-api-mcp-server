package com.muddl.riot.lol.league.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.LeagueService;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for LeagueTool with a mocked LeagueService (no HTTP, no resolver). */
@ExtendWith(MockitoExtension.class)
class LeagueToolTest {

    @Mock
    private LeagueService mockLeagueService;

    @InjectMocks
    private LeagueTool leagueTool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getLeagueEntriesByPlayer_passesPlatformAndPlayerThrough() {
        LeagueEntry entry = LeagueEntry.builder().tier("GOLD").build();
        when(mockLeagueService.getLeagueEntriesByPlayer(PLATFORM, "Faker#KR1")).thenReturn(List.of(entry));

        assertThat(leagueTool.getLeagueEntriesByPlayer("NA1", "Faker#KR1")).containsExactly(entry);
        verify(mockLeagueService).getLeagueEntriesByPlayer(PLATFORM, "Faker#KR1");
    }

    @Test
    void getApexLeague_defaultsQueue_whenNull() {
        LeagueList league = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", null, null)).isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null);
    }

    @Test
    void getApexLeague_honoursExplicitQueue() {
        LeagueList league = LeagueList.builder().tier("MASTER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_FLEX_SR", null))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "master", "RANKED_FLEX_SR", null))
                .isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_FLEX_SR", null);
    }

    @Test
    void getApexLeague_passesCountThrough() {
        LeagueList league = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 3))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", null, 3)).isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 3);
    }

    @Test
    void getApexLeague_invalidTier_throws() {
        assertThatThrownBy(() -> leagueTool.getApexLeague("NA1", "DIAMOND", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getLeagueEntriesByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getLeagueEntriesByPlayer("INVALID_PLATFORM", "Faker#KR1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
