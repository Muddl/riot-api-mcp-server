package com.wkaiser.riot.account.adapter.out.riot;

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
import com.wkaiser.riot.account.application.port.RiotAccountPort;
import com.wkaiser.riot.account.domain.RiotAccount;
import com.wkaiser.riot.core.config.RiotApiProperties;
import com.wkaiser.riot.core.exception.RiotApiException;
import com.wkaiser.riot.core.http.RiotApiClient;
import com.wkaiser.riot.core.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotAccountRiotAdapterTest {

    private WireMockServer wireMock;
    private RiotAccountPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        RiotApiClient client = new RiotApiClient(properties);
        adapter = new RiotAccountRiotAdapter(client, properties);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getAccountByRiotId_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo("/riot/account/v1/accounts/by-riot-id/Bjergsen/NA1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("account.json"))));

        RiotAccount account = adapter.getAccountByRiotId("Bjergsen", "NA1");

        assertThat(account.getPuuid()).isEqualTo("test-puuid-abc123");
        assertThat(account.getGameName()).isEqualTo("Bjergsen");
        assertThat(account.getTagLine()).isEqualTo("NA1");
        verify(getRequestedFor(urlEqualTo("/riot/account/v1/accounts/by-riot-id/Bjergsen/NA1"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getAccountByPuuid_parsesBody_andHitsExpectedUrl() {
        stubFor(get(urlEqualTo("/riot/account/v1/accounts/by-puuid/test-puuid-abc123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("account.json"))));

        RiotAccount account = adapter.getAccountByPuuid("test-puuid-abc123");

        assertThat(account.getPuuid()).isEqualTo("test-puuid-abc123");
        verify(getRequestedFor(urlEqualTo("/riot/account/v1/accounts/by-puuid/test-puuid-abc123"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo("/riot/account/v1/accounts/by-riot-id/Ghost/NA1"))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getAccountByRiotId("Ghost", "NA1"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
