package com.wkaiser.riot.lol.spectator.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riot.core.enums.RiotApiPlatformUri;
import com.wkaiser.riot.lol.spectator.SpectatorTestFixtures;
import com.wkaiser.riot.lol.spectator.domain.CurrentGameInfo;
import com.wkaiser.riot.lol.spectator.domain.FeaturedGames;
import org.junit.jupiter.api.Test;

class SpectatorServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySpectatorPort spectatorPort = new InMemorySpectatorPort();
    private final SpectatorService spectatorService = new SpectatorService(spectatorPort);

    @Test
    void getCurrentGameInfo_returnsStoredGame() {
        CurrentGameInfo game = SpectatorTestFixtures.createSampleCurrentGameInfo();
        spectatorPort.putGame("summoner-in-game", game);

        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "summoner-in-game"))
                .isSameAs(game);
    }

    @Test
    void getCurrentGameInfo_returnsNull_whenSummonerNotInGame() {
        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "summoner-not-in-game"))
                .isNull();
    }

    @Test
    void getFeaturedGames_returnsStoredFeaturedGames() {
        FeaturedGames featured = SpectatorTestFixtures.createSampleFeaturedGames();
        spectatorPort.putFeatured(PLATFORM, featured);

        assertThat(spectatorService.getFeaturedGames(PLATFORM)).isSameAs(featured);
    }
}
