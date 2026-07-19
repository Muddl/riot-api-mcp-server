package com.muddl.riot.lol.champion.adapter.out.riot;

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
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotChampionAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String ROTATION_URL = "/lol/platform/v3/champion-rotations";

    private WireMockServer wireMock;
    private ChampionPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotChampionAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getChampionRotation_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo(ROTATION_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("champion-rotation.json"))));

        ChampionRotation rotation = adapter.getChampionRotation(PLATFORM);

        assertThat(rotation.getMaxNewPlayerLevel()).isEqualTo(10);
        assertThat(rotation.getFreeChampionIds()).containsExactly(1, 15, 22);
        assertThat(rotation.getFreeChampionIdsForNewPlayers()).containsExactly(18, 81, 92);
        verify(getRequestedFor(urlEqualTo(ROTATION_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getChampionRotation_nullMaxNewPlayerLevel_parsesWithoutError() {
        // Regression for the live-eval failure: Riot returned maxNewPlayerLevel as null and a
        // primitive int threw "Cannot map null into type int". The boxed field must tolerate it.
        stubFor(
                get(urlEqualTo(ROTATION_URL))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"freeChampionIds\":[1,2],\"freeChampionIdsForNewPlayers\":[3],\"maxNewPlayerLevel\":null}")));

        ChampionRotation rotation = adapter.getChampionRotation(PLATFORM);

        assertThat(rotation.getMaxNewPlayerLevel()).isNull();
        assertThat(rotation.getFreeChampionIds()).containsExactly(1, 2);
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(ROTATION_URL))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> adapter.getChampionRotation(PLATFORM))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(403);
    }
}
