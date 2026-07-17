package com.muddl.riot.lol.spectator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.spectator.SpectatorTestFixtures;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.FeaturedGames;
import org.junit.jupiter.api.Test;

class SpectatorServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySpectatorPort spectatorPort = new InMemorySpectatorPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SpectatorService spectatorService = new SpectatorService(spectatorPort, resolver);

    @Test
    void getCurrentGameByPlayer_resolvesPlayer_thenReturnsGame() {
        CurrentGameInfo game = SpectatorTestFixtures.createSampleCurrentGameInfo();
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        spectatorPort.putGame("faker-puuid", game);

        assertThat(spectatorService.getCurrentGameByPlayer(PLATFORM, "Faker#KR1"))
                .isSameAs(game);
    }

    @Test
    void getCurrentGameInfo_returnsStoredGame() {
        CurrentGameInfo game = SpectatorTestFixtures.createSampleCurrentGameInfo();
        spectatorPort.putGame("player-in-game-puuid", game);

        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "player-in-game-puuid"))
                .isSameAs(game);
    }

    @Test
    void getCurrentGameInfo_returnsNull_whenPlayerNotInGame() {
        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "player-not-in-game-puuid"))
                .isNull();
    }

    @Test
    void getFeaturedGames_returnsStoredFeaturedGames() {
        FeaturedGames featured = SpectatorTestFixtures.createSampleFeaturedGames();
        spectatorPort.putFeatured(PLATFORM, featured);

        assertThat(spectatorService.getFeaturedGames(PLATFORM)).isSameAs(featured);
    }
}
