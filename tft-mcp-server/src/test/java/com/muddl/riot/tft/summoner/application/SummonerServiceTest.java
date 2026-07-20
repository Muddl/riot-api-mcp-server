package com.muddl.riot.tft.summoner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySummonerPort port = new InMemorySummonerPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SummonerService service = new SummonerService(port, resolver);

    @Test
    void getSummonerByPlayer_resolvesPlayer_thenReturnsSummoner() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn("puuid-1");
        Summoner expected =
                Summoner.builder().puuid("puuid-1").summonerLevel(200).build();
        port.put("puuid-1", expected);

        assertThat(service.getSummonerByPlayer(PLATFORM, "Player#NA1")).isSameAs(expected);
    }

    @Test
    void getSummonerByPuuid_delegatesToPort_withoutResolving() {
        Summoner expected = Summoner.builder().puuid("puuid-2").build();
        port.put("puuid-2", expected);

        assertThat(service.getSummonerByPuuid(PLATFORM, "puuid-2")).isSameAs(expected);
    }
}
