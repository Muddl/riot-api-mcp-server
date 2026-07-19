package com.muddl.riot.lol.championmastery.adapter.out.riot;

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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotChampionMasteryAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String ALL_URL = "/lol/champion-mastery/v4/champion-masteries/by-puuid/" + PUUID;
    private static final String TOP_URL = ALL_URL + "/top?count=1";

    private WireMockServer wireMock;
    private ChampionMasteryPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotChampionMasteryAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getMasteryByPuuid_nullCount_hitsAllUrl_andParsesArray() {
        stubFor(get(urlEqualTo(ALL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("champion-mastery.json"))));

        List<ChampionMastery> masteries = adapter.getMasteryByPuuid(PLATFORM, PUUID, null);

        assertThat(masteries).hasSize(2);
        assertThat(masteries.get(0).getChampionId()).isEqualTo(157);
        assertThat(masteries.get(0).getChampionLevel()).isEqualTo(7);
        assertThat(masteries.get(0).getChampionPoints()).isEqualTo(123456);
        verify(getRequestedFor(urlEqualTo(ALL_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getMasteryByPuuid_withCount_hitsTopUrl() {
        stubFor(get(urlEqualTo(TOP_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("champion-mastery-top.json"))));

        List<ChampionMastery> masteries = adapter.getMasteryByPuuid(PLATFORM, PUUID, 1);

        assertThat(masteries).hasSize(1);
        assertThat(masteries.get(0).getChampionId()).isEqualTo(157);
        verify(getRequestedFor(urlEqualTo(TOP_URL)));
    }

    @Test
    void getMasteryByPuuid_emptyArray_returnsEmptyList() {
        stubFor(get(urlEqualTo(ALL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(adapter.getMasteryByPuuid(PLATFORM, PUUID, null)).isEmpty();
    }

    @Test
    void getMasteryByPuuid_nullRetiredPrimitives_parseWithoutError() {
        // Regression for the live-eval failure: Riot's 2024 mastery/chest revamp returns chestGranted
        // (and other retired-system fields) as null; a primitive boolean/int threw "Cannot map null
        // into type ...". The boxed fields must tolerate null while the core fields still parse.
        stubFor(get(urlEqualTo(ALL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"puuid\":\"" + PUUID + "\",\"championId\":157,\"championLevel\":7,"
                                + "\"championPoints\":123456,\"lastPlayTime\":1609459200000,"
                                + "\"championPointsSinceLastLevel\":null,\"championPointsUntilNextLevel\":null,"
                                + "\"chestGranted\":null,\"tokensEarned\":null}]")));

        List<ChampionMastery> masteries = adapter.getMasteryByPuuid(PLATFORM, PUUID, null);

        assertThat(masteries).hasSize(1);
        assertThat(masteries.get(0).getChestGranted()).isNull();
        assertThat(masteries.get(0).getTokensEarned()).isNull();
        assertThat(masteries.get(0).getChampionPoints()).isEqualTo(123456);
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(ALL_URL)).willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getMasteryByPuuid(PLATFORM, PUUID, null))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
