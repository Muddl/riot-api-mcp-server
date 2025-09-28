package com.wkaiser.riotapimcpserver;

import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.*;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Basic compilation verification test to ensure all DTOs compile correctly
 * with Lombok annotations and can be instantiated without errors.
 */
class CompilationVerificationTest {

    @Test
    void lombok_builders_compile_successfully() {
        // Test compilation of all main DTOs
        Perks perks = Perks.builder()
                .perkIds(List.of(1L, 2L, 3L))
                .perkStyle(100L)
                .perkSubStyle(200L)
                .build();

        CurrentGameParticipant participant = CurrentGameParticipant.builder()
                .championId(1L)
                .perks(perks)
                .summonerName("TestSummoner")
                .puuid("test_puuid")
                .build();

        CurrentGameInfo gameInfo = CurrentGameInfo.builder()
                .gameId(123456L)
                .participants(List.of(participant))
                .build();

        Summoner summoner = Summoner.builder()
                .name("TestSummoner")
                .id("encrypted_id")
                .build();

        // Verify successful compilation
        assertNotNull(perks);
        assertNotNull(participant);
        assertNotNull(gameInfo);
        assertNotNull(summoner);
    }

    @Test
    void enum_values_accessible() {
        RiotApiPlatformUri platform = RiotApiPlatformUri.NA1;
        assertNotNull(platform.getPlatformUri());
    }
}