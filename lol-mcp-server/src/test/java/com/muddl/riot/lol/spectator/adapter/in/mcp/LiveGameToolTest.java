package com.muddl.riot.lol.spectator.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.lol.spectator.SpectatorTestFixtures;
import com.muddl.riot.lol.spectator.application.SpectatorService;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.FeaturedGames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for LiveGameTool with a mocked SpectatorService (no HTTP). */
@ExtendWith(MockitoExtension.class)
class LiveGameToolTest {

    @Mock
    private SpectatorService mockSpectatorService;

    @InjectMocks
    private LiveGameTool liveGameTool;

    private static final String TEST_PLATFORM_STRING = "NA1";
    private static final RiotApiPlatformUri TEST_PLATFORM = RiotApiPlatformUri.NA1;
    private static final String TEST_PUUID = "test-puuid-abc123";

    @Test
    void getCurrentGameBySummonerId_inGame_returnsCurrentGameInfo() {
        CurrentGameInfo expected = SpectatorTestFixtures.createSampleCurrentGameInfo();
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID)).thenReturn(expected);

        CurrentGameInfo result = liveGameTool.getCurrentGameBySummonerId(TEST_PLATFORM_STRING, TEST_PUUID);

        assertThat(result).isNotNull();
        assertThat(result.getGameId()).isEqualTo(expected.getGameId());
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID);
    }

    @Test
    void getCurrentGameBySummonerId_notInGame_returnsNull() {
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID)).thenReturn(null);

        assertThat(liveGameTool.getCurrentGameBySummonerId(TEST_PLATFORM_STRING, TEST_PUUID))
                .isNull();
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID);
    }

    @Test
    void getCurrentGameBySummonerId_invalidPlatform_throws() {
        assertThatThrownBy(() -> liveGameTool.getCurrentGameBySummonerId("INVALID_PLATFORM", TEST_PUUID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getFeaturedGames_returnsFeaturedGames() {
        FeaturedGames expected = SpectatorTestFixtures.createSampleFeaturedGames();
        when(mockSpectatorService.getFeaturedGames(TEST_PLATFORM)).thenReturn(expected);

        FeaturedGames result = liveGameTool.getFeaturedGames(TEST_PLATFORM_STRING);

        assertThat(result).isNotNull();
        assertThat(result.getGameList()).hasSize(1);
        verify(mockSpectatorService).getFeaturedGames(TEST_PLATFORM);
    }

    @Test
    void getFeaturedGames_serviceException_propagates() {
        when(mockSpectatorService.getFeaturedGames(TEST_PLATFORM))
                .thenThrow(new RiotApiException("The Riot API is temporarily unavailable", 503));

        assertThatThrownBy(() -> liveGameTool.getFeaturedGames(TEST_PLATFORM_STRING))
                .isInstanceOf(RiotApiException.class);
        verify(mockSpectatorService).getFeaturedGames(TEST_PLATFORM);
    }
}
