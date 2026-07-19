package com.muddl.riot.lol.championmastery.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.ChampionMasteryService;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionMasteryToolTest {

    @Mock
    private ChampionMasteryService mockService;

    @InjectMocks
    private ChampionMasteryTool tool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getMasteryByPlayer_passesArgsThrough_withNullCount() {
        ChampionMastery m = ChampionMastery.builder().championId(157L).build();
        when(mockService.getMasteryByPlayer(PLATFORM, "Faker#KR1", null)).thenReturn(List.of(m));

        assertThat(tool.getMasteryByPlayer("NA1", "Faker#KR1", null)).containsExactly(m);
        verify(mockService).getMasteryByPlayer(PLATFORM, "Faker#KR1", null);
    }

    @Test
    void getMasteryByPlayer_passesCountThrough() {
        when(mockService.getMasteryByPlayer(PLATFORM, "Faker#KR1", 3)).thenReturn(List.of());

        assertThat(tool.getMasteryByPlayer("NA1", "Faker#KR1", 3)).isEmpty();
        verify(mockService).getMasteryByPlayer(PLATFORM, "Faker#KR1", 3);
    }

    @Test
    void getMasteryByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> tool.getMasteryByPlayer("INVALID", "Faker#KR1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
