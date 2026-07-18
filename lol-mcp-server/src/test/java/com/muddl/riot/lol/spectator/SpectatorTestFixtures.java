package com.muddl.riot.lol.spectator;

import com.muddl.riot.lol.spectator.domain.BannedChampion;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.CurrentGameParticipant;
import com.muddl.riot.lol.spectator.domain.Observer;
import com.muddl.riot.lol.spectator.domain.Perks;
import java.util.List;

/** Shared sample-data builders for spectator tests. */
public final class SpectatorTestFixtures {

    private SpectatorTestFixtures() {}

    public static CurrentGameInfo createSampleCurrentGameInfo() {
        return CurrentGameInfo.builder()
                .gameId(123456789L)
                .gameType("MATCHED_GAME")
                .gameStartTime(1640995200000L)
                .mapId(11L)
                .gameLength(450L)
                .platformId("NA1")
                .gameMode("CLASSIC")
                .gameQueueConfigId(420L)
                .bannedChampions(List.of(BannedChampion.builder()
                        .championId(266L)
                        .teamId(100L)
                        .pickTurn(1)
                        .build()))
                .observers(Observer.builder()
                        .encryptionKey("sample_encryption_key")
                        .build())
                .participants(List.of(
                        createSampleParticipant("TestSummoner1", 1L, 100L),
                        createSampleParticipant("TestSummoner2", 2L, 200L)))
                .build();
    }

    public static CurrentGameParticipant createSampleParticipant(String summonerName, long championId, long teamId) {
        return CurrentGameParticipant.builder()
                .championId(championId)
                .perks(Perks.builder()
                        .perkIds(List.of(8112L, 8126L, 8138L, 8106L, 8275L, 8210L))
                        .perkStyle(8100L)
                        .perkSubStyle(8200L)
                        .build())
                .profileIconId(1234L)
                .bot(false)
                .teamId(teamId)
                .summonerName(summonerName)
                .summonerId("encrypted_summoner_id_" + summonerName)
                .puuid("test_puuid_" + summonerName)
                .summonerLevel(150L)
                .spell1Id(4L)
                .spell2Id(7L)
                .gameCustomizationObjects(List.of())
                .build();
    }
}
