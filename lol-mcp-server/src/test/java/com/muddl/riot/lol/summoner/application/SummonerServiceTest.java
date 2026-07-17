package com.muddl.riot.lol.summoner.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final SummonerService summonerService = new SummonerService(summonerPort);

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
}
