package com.wkaiser.riot.lol.summoner.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riot.core.enums.RiotApiPlatformUri;
import com.wkaiser.riot.lol.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final SummonerService summonerService = new SummonerService(summonerPort);

    @Test
    void getSummonerByName_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().id("id").name("Name").build();
        summonerPort.putByName(PLATFORM, "Name", expected);

        assertThat(summonerService.getSummonerByName(PLATFORM, "Name")).isSameAs(expected);
    }

    @Test
    void getSummonerByPuuid_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().puuid("p").build();
        summonerPort.putByPuuid(PLATFORM, "p", expected);

        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "p")).isSameAs(expected);
    }

    @Test
    void getSummonerById_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().id("id").build();
        summonerPort.putById(PLATFORM, "id", expected);

        assertThat(summonerService.getSummonerById(PLATFORM, "id")).isSameAs(expected);
    }

    @Test
    void getSummonerByName_returnsNull_whenUnknown() {
        assertThat(summonerService.getSummonerByName(PLATFORM, "Ghost")).isNull();
    }
}
