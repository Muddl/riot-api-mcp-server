package com.wkaiser.riotapimcpserver.riot.lol.spectator.service;

import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.FeaturedGames;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the SpectatorService.
 * Note: These tests make actual API calls to the Riot API and require a valid API key.
 */
@Disabled
@SpringBootTest
class SpectatorServiceIntegrationTest {

    @Autowired
    private SpectatorService spectatorService;

    /**
     * Tests the getCurrentGameInfo method with a real summoner.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     *
     * Note: Replace the test summoner ID with a valid one that is currently in a game
     * for meaningful testing.
     */
    @Test
    void getCurrentGameInfo_realSummonerInGame() {
        // Replace with a valid encrypted summoner ID that is currently in a game
        String testSummonerId = "encrypted_summoner_id_currently_in_game";
        RiotApiPlatformUri platform = RiotApiPlatformUri.NA1;

        CurrentGameInfo currentGame = spectatorService.getCurrentGameInfo(platform, testSummonerId);

        // If summoner is in game, validate the response
        if (currentGame != null) {
            assertNotNull(currentGame.getGameId());
            assertNotNull(currentGame.getGameMode());
            assertNotNull(currentGame.getParticipants());
            assertTrue(currentGame.getParticipants().size() >= 2);
            assertNotNull(currentGame.getPlatformId());
            assertTrue(currentGame.getGameLength() >= 0);
        }
        // If summoner is not in game, currentGame will be null (expected behavior)
    }

    /**
     * Tests the getCurrentGameInfo method with a summoner not in game.
     * This should return null without throwing an exception.
     */
    @Test
    void getCurrentGameInfo_realSummonerNotInGame() {
        // Replace with a valid encrypted summoner ID that is NOT currently in a game
        String testSummonerId = "encrypted_summoner_id_not_in_game";
        RiotApiPlatformUri platform = RiotApiPlatformUri.NA1;

        CurrentGameInfo currentGame = spectatorService.getCurrentGameInfo(platform, testSummonerId);

        // Should return null for summoner not in game
        assertNull(currentGame);
    }

    /**
     * Tests the getFeaturedGames method.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     */
    @Test
    void getFeaturedGames_realApiCall() {
        RiotApiPlatformUri platform = RiotApiPlatformUri.NA1;

        FeaturedGames featuredGames = spectatorService.getFeaturedGames(platform);

        // Basic validation of featured games response
        assertNotNull(featuredGames);
        assertNotNull(featuredGames.getGameList());
        assertTrue(featuredGames.getClientRefreshInterval() > 0);

        // Validate each featured game if any exist
        featuredGames.getGameList().forEach(game -> {
            assertNotNull(game.getGameId());
            assertNotNull(game.getGameMode());
            assertNotNull(game.getParticipants());
            assertTrue(game.getParticipants().size() >= 2);
            assertNotNull(game.getPlatformId());
            assertTrue(game.getGameLength() >= 0);
        });
    }

    /**
     * Tests getFeaturedGames across multiple platforms to verify
     * platform-specific client creation works correctly.
     */
    @Test
    void getFeaturedGames_multiplePlatforms() {
        RiotApiPlatformUri[] platforms = {
                RiotApiPlatformUri.NA1,
                RiotApiPlatformUri.EUW1,
                RiotApiPlatformUri.KR
        };

        for (RiotApiPlatformUri platform : platforms) {
            FeaturedGames featuredGames = spectatorService.getFeaturedGames(platform);

            assertNotNull(featuredGames, "Featured games should not be null for platform: " + platform);
            assertNotNull(featuredGames.getGameList(), "Game list should not be null for platform: " + platform);
            assertTrue(featuredGames.getClientRefreshInterval() > 0,
                    "Refresh interval should be positive for platform: " + platform);
        }
    }
}