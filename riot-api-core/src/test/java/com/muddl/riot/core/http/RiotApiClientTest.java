package com.muddl.riot.core.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    void maps_error_response_to_RiotApiException_with_actionable_message_and_raw_body() {
        stubFor(get(urlEqualTo("/boom")).willReturn(aResponse().withStatus(403).withBody("forbidden raw body")));

        assertThatThrownBy(() -> riotApiClient
                        .platform(RiotApiPlatformUri.NA1)
                        .get()
                        .uri("/boom")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RiotApiException.class)
                .hasMessage("Your Riot API key is invalid or expired — development keys expire every 24 hours")
                .extracting(e -> ((RiotApiException) e).getRawBody())
                .isEqualTo("forbidden raw body");
    }

    /** Records requested backoff durations without ever sleeping. */
    private static final class RecordingSleeper implements BackoffSleeper {
        private final List<Duration> waits = new ArrayList<>();

        @Override
        public void sleep(Duration duration) {
            waits.add(duration);
        }
    }

    private RiotApiClient clientWith(RecordingSleeper sleeper, int maxRetries, Duration backoff) {
        RiotApiProperties props = new RiotApiProperties();
        props.setApiKey("test-key-123");
        props.setBaseUrlOverride("http://localhost:" + wireMock.port());
        props.setMaxRetries(maxRetries);
        props.setRetryBackoff(backoff);
        return new RiotApiClient(props, sleeper);
    }

    @Test
    void retries_after_a_429_then_succeeds() {
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2"))
                .willSetStateTo("recovered"));
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        RecordingSleeper sleeper = new RecordingSleeper();
        String body = clientWith(sleeper, 3, Duration.ofSeconds(1))
                .platform(RiotApiPlatformUri.NA1)
                .get()
                .uri("/retry")
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("ok");
        assertThat(sleeper.waits).containsExactly(Duration.ofSeconds(2)); // Retry-After honoured
    }

    @Test
    void falls_back_to_the_default_backoff_when_no_retry_after_header() {
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429)) // no Retry-After
                .willSetStateTo("recovered"));
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        RecordingSleeper sleeper = new RecordingSleeper();
        clientWith(sleeper, 3, Duration.ofMillis(250))
                .platform(RiotApiPlatformUri.NA1)
                .get()
                .uri("/retry")
                .retrieve()
                .body(String.class);

        assertThat(sleeper.waits).containsExactly(Duration.ofMillis(250));
    }

    @Test
    void bounds_the_attempts_then_maps_the_final_429_to_the_rate_limited_message() {
        stubFor(get(urlEqualTo("/always"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")));

        RecordingSleeper sleeper = new RecordingSleeper();

        assertThatThrownBy(() -> clientWith(sleeper, 3, Duration.ofSeconds(5))
                        .platform(RiotApiPlatformUri.NA1)
                        .get()
                        .uri("/always")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RiotApiException.class)
                .hasMessage("Rate limited by the Riot API")
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(429);

        assertThat(sleeper.waits).hasSize(3); // three retries, bounded by maxRetries
        verify(exactly(4), getRequestedFor(urlEqualTo("/always"))); // 1 initial + 3 retries
    }

    @Test
    void does_not_retry_a_non_429_error() {
        stubFor(get(urlEqualTo("/forbidden"))
                .willReturn(aResponse().withStatus(403).withBody("nope")));

        RecordingSleeper sleeper = new RecordingSleeper();

        assertThatThrownBy(() -> clientWith(sleeper, 3, Duration.ofSeconds(1))
                        .platform(RiotApiPlatformUri.NA1)
                        .get()
                        .uri("/forbidden")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RiotApiException.class);

        assertThat(sleeper.waits).isEmpty();
        verify(exactly(1), getRequestedFor(urlEqualTo("/forbidden")));
    }
}
