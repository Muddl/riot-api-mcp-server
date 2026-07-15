package com.wkaiser.riotapimcpserver.spectator.application;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.spectator.SpectatorTestFixtures;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpectatorServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySpectatorPort spectatorPort = new InMemorySpectatorPort();
    private final SpectatorService spectatorService = new SpectatorService(spectatorPort);

    @Test
    void getCurrentGameInfo_returnsStoredGame() {
        CurrentGameInfo game = SpectatorTestFixtures.createSampleCurrentGameInfo();
        spectatorPort.putGame("summoner-in-game", game);

        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "summoner-in-game")).isSameAs(game);
    }

    @Test
    void getCurrentGameInfo_returnsNull_whenSummonerNotInGame() {
        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "summoner-not-in-game")).isNull();
    }

    @Test
    void getFeaturedGames_returnsStoredFeaturedGames() {
        FeaturedGames featured = SpectatorTestFixtures.createSampleFeaturedGames();
        spectatorPort.putFeatured(PLATFORM, featured);

        assertThat(spectatorService.getFeaturedGames(PLATFORM)).isSameAs(featured);
    }
}
