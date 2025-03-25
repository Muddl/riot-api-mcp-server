package com.wkaiser.riotapimcpserver.riot.lol.analytics.tool;

import com.wkaiser.riotapimcpserver.riot.lol.analytics.dto.PlayerMatchAnalytics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Analytics Tool.
 * Note: These tests make actual API calls to the Riot API and require a valid API key.
 */
@SpringBootTest
class AnalyticsToolTest {

    @Autowired
    private AnalyticsTool analyticsTool;

    /**
     * Tests the getPlayerMatchAnalytics method.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     */
    @Test
    void getPlayerMatchAnalytics() {
        // Replace with a valid Riot ID, platform, and region for testing
        String riotId = "Example#NA1";
        String platform = "NA1";
        String region = "AMERICAS";
        Integer matchCount = 5;
        
        PlayerMatchAnalytics analytics = analyticsTool.getPlayerMatchAnalytics(
                riotId, platform, region, matchCount);
        
        // Basic validation
        assertNotNull(analytics);
        assertEquals(riotId, analytics.getRiotId());
        assertTrue(analytics.getMatchCount() <= matchCount);
        assertNotNull(analytics.getWinRate());
        assertNotNull(analytics.getAvgKills());
        assertNotNull(analytics.getAvgDeaths());
        assertNotNull(analytics.getAvgAssists());
        assertNotNull(analytics.getAvgKda());
        assertNotNull(analytics.getMostPlayedChampions());
    }
}
