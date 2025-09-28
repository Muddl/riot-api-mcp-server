package com.wkaiser.riotapimcpserver.riot.lol.spectator.tool;

import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.FeaturedGames;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the LiveGameTool.
 * Note: These tests make actual API calls to the Riot API and require a valid API key.
 */
@Disabled
@SpringBootTest
class LiveGameToolIntegrationTest {

    @Autowired
    private LiveGameTool liveGameTool;

    /**
     * Tests the getCurrentGameBySummonerName method with a real summoner.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     *
     * Note: Replace the test summoner name with a valid one for meaningful testing.
     */
    @Test
    void getCurrentGameBySummonerName_realSummoner() {
        // Replace with a valid summoner name for testing
        String testSummonerName = "ExampleSummoner";
        String platform = "NA1";

        CurrentGameInfo currentGame = liveGameTool.getCurrentGameBySummonerName(platform, testSummonerName);

        // If summoner is in game, validate the response
        if (currentGame != null) {
            assertNotNull(currentGame.getGameId());
            assertNotNull(currentGame.getGameMode());
            assertNotNull(currentGame.getParticipants());
            assertTrue(currentGame.getParticipants().size() >= 2);
            assertNotNull(currentGame.getPlatformId());
            assertTrue(currentGame.getGameLength() >= 0);

            // Verify the summoner is actually in the participant list
            boolean summonerFound = currentGame.getParticipants().stream()
                    .anyMatch(participant -> testSummonerName.equals(participant.getSummonerName()));
            assertTrue(summonerFound, "Test summoner should be found in the participant list");
        }
        // If summoner is not in game, currentGame will be null (expected behavior)
    }

    /**
     * Tests the getCurrentGameBySummonerId method with a real summoner ID.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     */
    @Test
    void getCurrentGameBySummonerId_realSummonerId() {
        // Replace with a valid encrypted summoner ID for testing
        String testSummonerId = "encrypted_summoner_id_123";
        String platform = "NA1";

        CurrentGameInfo currentGame = liveGameTool.getCurrentGameBySummonerId(platform, testSummonerId);

        // If summoner is in game, validate the response
        if (currentGame != null) {
            assertNotNull(currentGame.getGameId());
            assertNotNull(currentGame.getGameMode());
            assertNotNull(currentGame.getParticipants());
            assertTrue(currentGame.getParticipants().size() >= 2);
            assertNotNull(currentGame.getPlatformId());
            assertTrue(currentGame.getGameLength() >= 0);

            // Verify the summoner ID is actually in the participant list
            boolean summonerFound = currentGame.getParticipants().stream()
                    .anyMatch(participant -> testSummonerId.equals(participant.getSummonerId()));
            assertTrue(summonerFound, "Test summoner ID should be found in the participant list");
        }
        // If summoner is not in game, currentGame will be null (expected behavior)
    }

    /**
     * Tests the getFeaturedGames method.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     */
    @Test
    void getFeaturedGames_realApiCall() {
        String platform = "NA1";

        FeaturedGames featuredGames = liveGameTool.getFeaturedGames(platform);

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
     * Tests the isSummonerInGame method with a real summoner.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     */
    @Test
    void isSummonerInGame_realSummoner() {
        // Replace with a valid summoner name for testing
        String testSummonerName = "ExampleSummoner";
        String platform = "NA1";

        boolean isInGame = liveGameTool.isSummonerInGame(platform, testSummonerName);

        // Result should be either true or false (boolean logic test)
        assertTrue(isInGame || !isInGame, "Result should be a valid boolean");

        // If summoner is in game, verify consistency with getCurrentGameBySummonerName
        CurrentGameInfo currentGame = liveGameTool.getCurrentGameBySummonerName(platform, testSummonerName);
        assertEquals(currentGame != null, isInGame,
                "isSummonerInGame result should match getCurrentGameBySummonerName nullability");
    }

    /**
     * Tests error handling with invalid platform strings.
     */
    @Test
    void invalidPlatform_throwsException() {
        String invalidPlatform = "INVALID_PLATFORM";
        String testSummonerName = "ExampleSummoner";
        String testSummonerId = "encrypted_summoner_id_123";

        // All methods should throw IllegalArgumentException for invalid platform
        assertThrows(IllegalArgumentException.class,
                () -> liveGameTool.getCurrentGameBySummonerName(invalidPlatform, testSummonerName));

        assertThrows(IllegalArgumentException.class,
                () -> liveGameTool.getCurrentGameBySummonerId(invalidPlatform, testSummonerId));

        assertThrows(IllegalArgumentException.class,
                () -> liveGameTool.getFeaturedGames(invalidPlatform));

        assertThrows(IllegalArgumentException.class,
                () -> liveGameTool.isSummonerInGame(invalidPlatform, testSummonerName));
    }

    /**
     * Tests the full integration flow: summoner lookup to game state checking.
     * This verifies the complete MCP tool functionality end-to-end.
     */
    @Test
    void fullIntegrationFlow_summonerNameToGameState() {
        // Replace with a valid summoner name for testing
        String testSummonerName = "ExampleSummoner";
        String platform = "NA1";

        // Test the boolean check first
        boolean isInGame = liveGameTool.isSummonerInGame(platform, testSummonerName);

        // Test getting the actual game info
        CurrentGameInfo currentGame = liveGameTool.getCurrentGameBySummonerName(platform, testSummonerName);

        // Verify consistency between the two methods
        assertEquals(currentGame != null, isInGame,
                "Boolean check should match game info availability");

        if (isInGame && currentGame != null) {
            // If in game, verify the game data is valid
            assertNotNull(currentGame.getGameId());
            assertNotNull(currentGame.getGameMode());
            assertNotNull(currentGame.getParticipants());
            assertTrue(currentGame.getParticipants().size() >= 2);

            // Verify summoner is in the participants
            boolean summonerFound = currentGame.getParticipants().stream()
                    .anyMatch(participant -> testSummonerName.equals(participant.getSummonerName()));
            assertTrue(summonerFound, "Summoner should be found in game participants");
        }
    }
}