package com.muddl.riot.tft.match.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.application.MatchService;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchToolTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    @Mock
    private MatchService mockMatchService;

    @InjectMocks
    private MatchTool matchTool;

    @Test
    void getMatchIdsByPlayer_passesArgsThrough() {
        when(mockMatchService.getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0)).thenReturn(List.of("NA1_1", "NA1_2"));

        assertThat(matchTool.getMatchIdsByPlayer("AMERICAS", "Faker#KR1", 20, 0))
                .containsExactly("NA1_1", "NA1_2");
        verify(mockMatchService).getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0);
    }

    @Test
    void getMatchIdsByPlayer_normalizesCaseBeforeDelegating() {
        when(mockMatchService.getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0)).thenReturn(List.of("NA1_1"));

        assertThat(matchTool.getMatchIdsByPlayer("americas", "Faker#KR1", 20, 0))
                .containsExactly("NA1_1");
        verify(mockMatchService).getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0);
    }

    @Test
    void getMatchIdsByPlayer_defaultsCountAndStart_whenNull() {
        when(mockMatchService.getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0)).thenReturn(List.of("NA1_1"));

        assertThat(matchTool.getMatchIdsByPlayer("AMERICAS", "Faker#KR1", null, null))
                .containsExactly("NA1_1");
        verify(mockMatchService).getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0);
    }

    @Test
    void getMatchIdsByPlayer_invalidRegion_throws() {
        assertThatThrownBy(() -> matchTool.getMatchIdsByPlayer("INVALID", "Faker#KR1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getMatchById_passesArgsThrough() {
        TftMatch match = TftMatch.builder().build();
        when(mockMatchService.getMatchById(REGION, "NA1_1")).thenReturn(match);

        assertThat(matchTool.getMatchById("AMERICAS", "NA1_1")).isSameAs(match);
        verify(mockMatchService).getMatchById(REGION, "NA1_1");
    }

    @Test
    void getMatchById_normalizesCaseBeforeDelegating() {
        TftMatch match = TftMatch.builder().build();
        when(mockMatchService.getMatchById(REGION, "NA1_1")).thenReturn(match);

        assertThat(matchTool.getMatchById("americas", "NA1_1")).isSameAs(match);
        verify(mockMatchService).getMatchById(REGION, "NA1_1");
    }

    @Test
    void getMatchById_invalidRegion_throws() {
        assertThatThrownBy(() -> matchTool.getMatchById("INVALID", "NA1_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
