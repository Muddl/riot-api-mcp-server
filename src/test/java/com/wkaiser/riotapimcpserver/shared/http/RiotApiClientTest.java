package com.wkaiser.riotapimcpserver.shared.http;

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
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotApiClientTest {

    private WireMockServer wireMock;
    private RiotApiClient riotApiClient;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        riotApiClient = new RiotApiClient(properties);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void sends_api_key_header_and_parses_body_on_success() {
        stubFor(get(urlEqualTo("/ping"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("pong")));

        String body = riotApiClient
                .platform(RiotApiPlatformUri.NA1)
                .get()
                .uri("/ping")
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("pong");
        verify(getRequestedFor(urlEqualTo("/ping")).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void maps_error_response_to_RiotApiException_with_status() {
        stubFor(get(urlEqualTo("/boom")).willReturn(aResponse().withStatus(429).withBody("rate limited")));

        assertThatThrownBy(() -> riotApiClient
                        .platform(RiotApiPlatformUri.NA1)
                        .get()
                        .uri("/boom")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RiotApiException.class)
                .hasMessageContaining("rate limited")
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(429);
    }
}
