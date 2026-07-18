package com.muddl.riot.lol.challenges.adapter.out.riot;

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
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotChallengesAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String URL = "/lol/challenges/v1/player-data/" + PUUID;

    private WireMockServer wireMock;
    private ChallengesPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotChallengesAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getPlayerDataByPuuid_parsesNestedPointsAndChallenges() {
        stubFor(get(urlEqualTo(URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("challenges-player-data.json"))));

        ChallengesPlayerData data = adapter.getPlayerDataByPuuid(PLATFORM, PUUID);

        assertThat(data.getTotalPoints().getLevel()).isEqualTo("GOLD");
        assertThat(data.getTotalPoints().getCurrent()).isEqualTo(12345.0);
        assertThat(data.getCategoryPoints()).containsKey("TEAMWORK");
        assertThat(data.getChallenges()).hasSize(1);
        assertThat(data.getChallenges().get(0).getChallengeId()).isEqualTo(101101L);
        assertThat(data.getChallenges().get(0).getLevel()).isEqualTo("GOLD");
        verify(getRequestedFor(urlEqualTo(URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getPlayerDataByPuuid(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
