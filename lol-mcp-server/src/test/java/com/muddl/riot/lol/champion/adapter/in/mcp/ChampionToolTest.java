package com.muddl.riot.lol.champion.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.ChampionService;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionToolTest {

    @Mock
    private ChampionService mockChampionService;

    @InjectMocks
    private ChampionTool championTool;

    @Test
    void getChampionRotation_passesPlatformThrough() {
        ChampionRotation rotation =
                ChampionRotation.builder().maxNewPlayerLevel(10).build();
        when(mockChampionService.getChampionRotation(RiotApiPlatformUri.NA1)).thenReturn(rotation);

        assertThat(championTool.getChampionRotation("NA1")).isSameAs(rotation);
        verify(mockChampionService).getChampionRotation(RiotApiPlatformUri.NA1);
    }

    @Test
    void getChampionRotation_invalidPlatform_throws() {
        assertThatThrownBy(() -> championTool.getChampionRotation("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
