package com.wkaiser.riotapimcpserver.riot.account.tool;

import com.wkaiser.riotapimcpserver.riot.account.dto.RiotAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the RiotAccountTool.
 * Note: These tests make actual API calls to the Riot API and require a valid API key.
 */
@SpringBootTest
class RiotAccountToolTest {

    @Autowired
    private RiotAccountTool accountTool;

    /**
     * Tests the getAccountByRiotId method.
     * This is an integration test that requires a valid Riot API key
     * and will make actual API calls. Use with caution.
     */
    @Test
    void getAccountByRiotId() {
        // Replace with a valid game name and tag line for testing
        String gameName = "Example";
        String tagLine = "NA1";
        
        RiotAccount account = accountTool.getAccountByRiotId(gameName, tagLine);
        
        // Basic validation
        assertNotNull(account);
        assertEquals(gameName, account.getGameName());
        assertEquals(tagLine, account.getTagLine());
        assertNotNull(account.getPuuid());
    }
}
