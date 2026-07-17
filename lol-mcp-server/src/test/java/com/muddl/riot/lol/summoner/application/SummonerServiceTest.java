package com.muddl.riot.lol.summoner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SummonerService summonerService = new SummonerService(summonerPort, resolver);

    @Test
    void getSummonerByPuuid_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().puuid("p").build();
        summonerPort.putByPuuid(PLATFORM, "p", expected);

        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "p")).isSameAs(expected);
    }

    @Test
    void getSummonerByPuuid_returnsNull_whenUnknown() {
        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "ghost-puuid")).isNull();
    }

    @Test
    void getSummonerByPlayer_resolvesPlayer_thenReturnsSummoner() {
        Summoner expected = Summoner.builder().puuid("faker-puuid").build();
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        summonerPort.putByPuuid(PLATFORM, "faker-puuid", expected);

        assertThat(summonerService.getSummonerByPlayer(PLATFORM, "Faker#KR1")).isSameAs(expected);
    }
}
