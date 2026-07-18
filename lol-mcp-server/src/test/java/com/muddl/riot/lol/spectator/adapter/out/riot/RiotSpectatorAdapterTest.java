package com.muddl.riot.lol.spectator.adapter.out.riot;

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
import com.muddl.riot.lol.spectator.application.port.SpectatorPort;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotSpectatorAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String ACTIVE_GAME_URL = "/lol/spectator/v5/active-games/by-summoner/" + PUUID;

    private WireMockServer wireMock;
    private SpectatorPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotSpectatorAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getCurrentGameInfo_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo(ACTIVE_GAME_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("current-game.json"))));

        CurrentGameInfo game = adapter.getCurrentGameInfo(PLATFORM, PUUID);

        assertThat(game).isNotNull();
        assertThat(game.getGameId()).isEqualTo(4600000999L);
        assertThat(game.getGameMode()).isEqualTo("CLASSIC");
        assertThat(game.getParticipants()).hasSize(1);
        assertThat(game.getParticipants().get(0).getPuuid()).isEqualTo("test-puuid-abc123");
        verify(getRequestedFor(urlEqualTo(ACTIVE_GAME_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getCurrentGameInfo_returnsNull_whenNotInGame_404() {
        stubFor(get(urlEqualTo(ACTIVE_GAME_URL))
                .willReturn(aResponse().withStatus(404).withBody("not in game")));

        CurrentGameInfo game = adapter.getCurrentGameInfo(PLATFORM, PUUID);

        assertThat(game).isNull();
    }

    @Test
    void getCurrentGameInfo_propagatesNon404Errors() {
        stubFor(get(urlEqualTo(ACTIVE_GAME_URL))
                .willReturn(aResponse().withStatus(500).withBody("server error")));

        assertThatThrownBy(() -> adapter.getCurrentGameInfo(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(500);
    }
}
