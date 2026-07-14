package com.wkaiser.riotapimcpserver.summoner.application;

import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Mock
    private SummonerPort summonerPort;

    @InjectMocks
    private SummonerService summonerService;

    @Test
    void getSummonerByName_delegatesToPort() {
        Summoner expected = Summoner.builder().id("id").name("Name").build();
        when(summonerPort.getSummonerByName(PLATFORM, "Name")).thenReturn(expected);

        assertThat(summonerService.getSummonerByName(PLATFORM, "Name")).isSameAs(expected);
        verify(summonerPort).getSummonerByName(PLATFORM, "Name");
    }

    @Test
    void getSummonerByPuuid_delegatesToPort() {
        Summoner expected = Summoner.builder().puuid("p").build();
        when(summonerPort.getSummonerByPuuid(PLATFORM, "p")).thenReturn(expected);

        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "p")).isSameAs(expected);
        verify(summonerPort).getSummonerByPuuid(PLATFORM, "p");
    }

    @Test
    void getSummonerById_delegatesToPort() {
        Summoner expected = Summoner.builder().id("id").build();
        when(summonerPort.getSummonerById(PLATFORM, "id")).thenReturn(expected);

        assertThat(summonerService.getSummonerById(PLATFORM, "id")).isSameAs(expected);
        verify(summonerPort).getSummonerById(PLATFORM, "id");
    }
}
