package com.muddl.riot.lol.match.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.match.application.MatchService;
import com.muddl.riot.lol.match.domain.Match;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchToolTest {

    @Mock
    private MatchService mockMatchService;

    @InjectMocks
    private MatchTool matchTool;

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    @Test
    void getMatchIdsByPlayer_passesArgsThrough() {
        when(mockMatchService.getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0, null))
                .thenReturn(List.of("NA1_1", "NA1_2"));

        assertThat(matchTool.getMatchIdsByPlayer("AMERICAS", "Faker#KR1", 20, 0, null))
                .containsExactly("NA1_1", "NA1_2");
        verify(mockMatchService).getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0, null);
    }

    @Test
    void getMatchById_passesArgsThrough() {
        Match match = Match.builder().build();
        when(mockMatchService.getMatchById(REGION, "NA1_1")).thenReturn(match);

        assertThat(matchTool.getMatchById("AMERICAS", "NA1_1")).isSameAs(match);
        verify(mockMatchService).getMatchById(REGION, "NA1_1");
    }

    @Test
    void getMatchIdsByPlayer_invalidRegion_throws() {
        assertThatThrownBy(() -> matchTool.getMatchIdsByPlayer("INVALID", "Faker#KR1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
