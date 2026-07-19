package com.muddl.riot.tft.status.adapter.out.riot;

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
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftStatusAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String STATUS_URL = "/tft/status/v1/platform-data";

    private WireMockServer wireMock;
    private StatusPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotTftStatusAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getPlatformStatus_parsesNestedIncident_andSnakeCaseKeys() {
        stubFor(get(urlEqualTo(STATUS_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-status-platform.json"))));

        PlatformStatus status = adapter.getPlatformStatus(PLATFORM);

        assertThat(status.getId()).isEqualTo("NA1");
        assertThat(status.getIncidents()).hasSize(1);
        assertThat(status.getIncidents().get(0).getIncidentSeverity()).isEqualTo("warning");
        assertThat(status.getIncidents().get(0).getTitles().get(0).getContent()).isEqualTo("Login issues");
        verify(getRequestedFor(urlEqualTo(STATUS_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(STATUS_URL))
                .willReturn(aResponse().withStatus(503).withBody("unavailable")));

        assertThatThrownBy(() -> adapter.getPlatformStatus(PLATFORM))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(503);
    }
}
