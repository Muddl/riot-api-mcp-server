package com.wkaiser.riotapimcpserver.riot.lol.spectator.tool;

import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.BannedChampion;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.CurrentGameParticipant;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.FeaturedGames;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.Observer;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.Perks;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.service.SpectatorService;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.service.SpectatorServiceTest;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LiveGameTool.
 * Tests MCP tool methods using mocked services to avoid actual API calls.
 */
@ExtendWith(MockitoExtension.class)
class LiveGameToolTest {

    @Mock
    private SpectatorService mockSpectatorService;

    @Mock
    private SummonerService mockSummonerService;

    @InjectMocks
    private LiveGameTool liveGameTool;

    private static final String TEST_PLATFORM_STRING = "NA1";
    private static final RiotApiPlatformUri TEST_PLATFORM = RiotApiPlatformUri.NA1;
    private static final String TEST_SUMMONER_NAME = "TestSummoner";
    private static final String TEST_SUMMONER_ID = "encrypted_summoner_id_123";

    @Test
    void getCurrentGameBySummonerName_successfulFlow_returnsCurrentGameInfo() {
        // Arrange
        Summoner mockSummoner = createSampleSummoner();
        CurrentGameInfo expectedGameInfo = createSampleCurrentGameInfo();

        when(mockSummonerService.getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME))
                .thenReturn(mockSummoner);
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID))
                .thenReturn(expectedGameInfo);

        // Act
        CurrentGameInfo result = liveGameTool.getCurrentGameBySummonerName(TEST_PLATFORM_STRING, TEST_SUMMONER_NAME);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getGameId()).isEqualTo(expectedGameInfo.getGameId());
        assertThat(result.getGameMode()).isEqualTo(expectedGameInfo.getGameMode());
        assertThat(result.getParticipants()).hasSize(2);

        verify(mockSummonerService).getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME);
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID);
    }

    @Test
    void getCurrentGameBySummonerName_summonerNotInGame_returnsNull() {
        // Arrange
        Summoner mockSummoner = createSampleSummoner();

        when(mockSummonerService.getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME))
                .thenReturn(mockSummoner);
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID))
                .thenReturn(null);

        // Act
        CurrentGameInfo result = liveGameTool.getCurrentGameBySummonerName(TEST_PLATFORM_STRING, TEST_SUMMONER_NAME);

        // Assert
        assertThat(result).isNull();

        verify(mockSummonerService).getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME);
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID);
    }

    @Test
    void getCurrentGameBySummonerName_invalidPlatform_throwsException() {
        // Act & Assert
        assertThatThrownBy(() -> liveGameTool.getCurrentGameBySummonerName("INVALID_PLATFORM", TEST_SUMMONER_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getCurrentGameBySummonerName_summonerServiceException_propagatesException() {
        // Arrange
        when(mockSummonerService.getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME))
                .thenThrow(new RiotApiException("Summoner not found", 404));

        // Act & Assert
        assertThatThrownBy(() -> liveGameTool.getCurrentGameBySummonerName(TEST_PLATFORM_STRING, TEST_SUMMONER_NAME))
                .isInstanceOf(RiotApiException.class)
                .hasMessageContaining("Summoner not found");

        verify(mockSummonerService).getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME);
    }

    @Test
    void getCurrentGameBySummonerId_successfulFlow_returnsCurrentGameInfo() {
        // Arrange
        CurrentGameInfo expectedGameInfo = createSampleCurrentGameInfo();

        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID))
                .thenReturn(expectedGameInfo);

        // Act
        CurrentGameInfo result = liveGameTool.getCurrentGameBySummonerId(TEST_PLATFORM_STRING, TEST_SUMMONER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getGameId()).isEqualTo(expectedGameInfo.getGameId());
        assertThat(result.getGameMode()).isEqualTo(expectedGameInfo.getGameMode());

        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID);
    }

    @Test
    void getCurrentGameBySummonerId_summonerNotInGame_returnsNull() {
        // Arrange
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID))
                .thenReturn(null);

        // Act
        CurrentGameInfo result = liveGameTool.getCurrentGameBySummonerId(TEST_PLATFORM_STRING, TEST_SUMMONER_ID);

        // Assert
        assertThat(result).isNull();

        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID);
    }

    @Test
    void getCurrentGameBySummonerId_invalidPlatform_throwsException() {
        // Act & Assert
        assertThatThrownBy(() -> liveGameTool.getCurrentGameBySummonerId("INVALID_PLATFORM", TEST_SUMMONER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getFeaturedGames_successfulFlow_returnsFeaturedGames() {
        // Arrange
        FeaturedGames expectedFeaturedGames = createSampleFeaturedGames();

        when(mockSpectatorService.getFeaturedGames(TEST_PLATFORM))
                .thenReturn(expectedFeaturedGames);

        // Act
        FeaturedGames result = liveGameTool.getFeaturedGames(TEST_PLATFORM_STRING);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getClientRefreshInterval()).isEqualTo(expectedFeaturedGames.getClientRefreshInterval());
        assertThat(result.getGameList()).hasSize(1);

        verify(mockSpectatorService).getFeaturedGames(TEST_PLATFORM);
    }

    @Test
    void getFeaturedGames_invalidPlatform_throwsException() {
        // Act & Assert
        assertThatThrownBy(() -> liveGameTool.getFeaturedGames("INVALID_PLATFORM"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getFeaturedGames_serviceException_propagatesException() {
        // Arrange
        when(mockSpectatorService.getFeaturedGames(TEST_PLATFORM))
                .thenThrow(new RiotApiException("Featured games unavailable", 503));

        // Act & Assert
        assertThatThrownBy(() -> liveGameTool.getFeaturedGames(TEST_PLATFORM_STRING))
                .isInstanceOf(RiotApiException.class)
                .hasMessageContaining("Featured games unavailable");

        verify(mockSpectatorService).getFeaturedGames(TEST_PLATFORM);
    }

    @Test
    void isSummonerInGame_summonerInGame_returnsTrue() {
        // Arrange
        Summoner mockSummoner = createSampleSummoner();
        CurrentGameInfo mockGameInfo = createSampleCurrentGameInfo();

        when(mockSummonerService.getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME))
                .thenReturn(mockSummoner);
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID))
                .thenReturn(mockGameInfo);

        // Act
        boolean result = liveGameTool.isSummonerInGame(TEST_PLATFORM_STRING, TEST_SUMMONER_NAME);

        // Assert
        assertThat(result).isTrue();

        verify(mockSummonerService).getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME);
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID);
    }

    @Test
    void isSummonerInGame_summonerNotInGame_returnsFalse() {
        // Arrange
        Summoner mockSummoner = createSampleSummoner();

        when(mockSummonerService.getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME))
                .thenReturn(mockSummoner);
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID))
                .thenReturn(null);

        // Act
        boolean result = liveGameTool.isSummonerInGame(TEST_PLATFORM_STRING, TEST_SUMMONER_NAME);

        // Assert
        assertThat(result).isFalse();

        verify(mockSummonerService).getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME);
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_SUMMONER_ID);
    }

    @Test
    void isSummonerInGame_invalidPlatform_throwsException() {
        // Act & Assert
        assertThatThrownBy(() -> liveGameTool.isSummonerInGame("INVALID_PLATFORM", TEST_SUMMONER_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void isSummonerInGame_summonerNotFound_propagatesException() {
        // Arrange
        when(mockSummonerService.getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME))
                .thenThrow(new RiotApiException("Summoner not found", 404));

        // Act & Assert
        assertThatThrownBy(() -> liveGameTool.isSummonerInGame(TEST_PLATFORM_STRING, TEST_SUMMONER_NAME))
                .isInstanceOf(RiotApiException.class)
                .hasMessageContaining("Summoner not found");

        verify(mockSummonerService).getSummonerByName(TEST_PLATFORM, TEST_SUMMONER_NAME);
    }

    // Helper methods for creating test data

    private Summoner createSampleSummoner() {
        return Summoner.builder()
                .id(TEST_SUMMONER_ID)
                .accountId("encrypted_account_id_123")
                .puuid("test_puuid_123")
                .name(TEST_SUMMONER_NAME)
                .profileIconId(1234)
                .revisionDate(1640995200000L)
                .summonerLevel(150)
                .build();
    }

    private CurrentGameInfo createSampleCurrentGameInfo() {
        // Reuse the static helper from SpectatorServiceTest for consistency
        return SpectatorServiceTest.createSampleCurrentGameInfo();
    }

    private CurrentGameParticipant createSampleParticipant(String summonerName, long championId, long teamId) {
        // Reuse the static helper from SpectatorServiceTest for consistency
        return SpectatorServiceTest.createSampleParticipant(summonerName, championId, teamId);
    }

    private FeaturedGames createSampleFeaturedGames() {
        // Reuse the static helper from SpectatorServiceTest for consistency
        return SpectatorServiceTest.createSampleFeaturedGames();
    }
}