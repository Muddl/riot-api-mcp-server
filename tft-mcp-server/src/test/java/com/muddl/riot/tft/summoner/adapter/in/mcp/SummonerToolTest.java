package com.muddl.riot.tft.summoner.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.application.SummonerService;
import com.muddl.riot.tft.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummonerToolTest {

    @Mock
    private SummonerService mockSummonerService;

    @InjectMocks
    private SummonerTool summonerTool;

    @Test
    void getSummonerByPlayer_passesPlatformAndPlayerThrough() {
        Summoner summoner = Summoner.builder().puuid("puuid-1").build();
        when(mockSummonerService.getSummonerByPlayer(RiotApiPlatformUri.NA1, "Player#NA1"))
                .thenReturn(summoner);

        assertThat(summonerTool.getSummonerByPlayer("NA1", "Player#NA1")).isSameAs(summoner);
        verify(mockSummonerService).getSummonerByPlayer(RiotApiPlatformUri.NA1, "Player#NA1");
    }

    @Test
    void getSummonerByPlayer_normalizesCaseBeforeDelegating() {
        Summoner summoner = Summoner.builder().puuid("puuid-1").build();
        when(mockSummonerService.getSummonerByPlayer(RiotApiPlatformUri.NA1, "Player#NA1"))
                .thenReturn(summoner);

        assertThat(summonerTool.getSummonerByPlayer("na1", "Player#NA1")).isSameAs(summoner);
        verify(mockSummonerService).getSummonerByPlayer(RiotApiPlatformUri.NA1, "Player#NA1");
    }

    @Test
    void getSummonerByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> summonerTool.getSummonerByPlayer("INVALID", "Player#NA1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
