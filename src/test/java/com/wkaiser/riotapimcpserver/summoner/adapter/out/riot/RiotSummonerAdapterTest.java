package com.wkaiser.riotapimcpserver.summoner.adapter.out.riot;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiotSummonerAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

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

        adapter = new RiotSummonerAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getSummonerByName_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/by-name/Bjergsen"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("summoner.json"))));

        Summoner summoner = adapter.getSummonerByName(PLATFORM, "Bjergsen");

        assertThat(summoner.getName()).isEqualTo("Bjergsen");
        assertThat(summoner.getId()).isEqualTo("summoner-id-xyz");
        assertThat(summoner.getPuuid()).isEqualTo("test-puuid-abc123");
        assertThat(summoner.getSummonerLevel()).isEqualTo(350L);
        verify(getRequestedFor(urlEqualTo("/lol/summoner/v4/summoners/by-name/Bjergsen"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getSummonerByPuuid_hitsExpectedUrl() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/by-puuid/test-puuid-abc123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("summoner.json"))));

        Summoner summoner = adapter.getSummonerByPuuid(PLATFORM, "test-puuid-abc123");

        assertThat(summoner.getPuuid()).isEqualTo("test-puuid-abc123");
        verify(getRequestedFor(urlEqualTo("/lol/summoner/v4/summoners/by-puuid/test-puuid-abc123"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getSummonerById_hitsExpectedUrl() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/summoner-id-xyz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("summoner.json"))));

        Summoner summoner = adapter.getSummonerById(PLATFORM, "summoner-id-xyz");

        assertThat(summoner.getId()).isEqualTo("summoner-id-xyz");
        verify(getRequestedFor(urlEqualTo("/lol/summoner/v4/summoners/summoner-id-xyz"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/by-name/Ghost"))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> adapter.getSummonerByName(PLATFORM, "Ghost"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(403);
    }
}
