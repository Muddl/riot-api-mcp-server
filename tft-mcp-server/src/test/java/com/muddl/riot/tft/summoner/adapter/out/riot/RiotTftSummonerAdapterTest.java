package com.muddl.riot.tft.summoner.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftSummonerAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc";
    private static final String SUMMONER_URL = "/tft/summoner/v1/summoners/by-puuid/" + PUUID;

    private WireMockServer wireMock;
    private SummonerPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());
        adapter = new RiotTftSummonerAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getSummonerByPuuid_parsesResponse_andSendsToken() {
        stubFor(get(urlEqualTo(SUMMONER_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-summoner.json"))));

        Summoner summoner = adapter.getSummonerByPuuid(PLATFORM, PUUID);

        assertThat(summoner.getSummonerLevel()).isEqualTo(350);
        assertThat(summoner.getProfileIconId()).isEqualTo(29);
        verify(getRequestedFor(urlEqualTo(SUMMONER_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void notFound_mapsToRiotApiException() {
        stubFor(get(urlEqualTo(SUMMONER_URL))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getSummonerByPuuid(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
