package com.wkaiser.riot.lol.match.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riot.core.config.RiotApiProperties;
import com.wkaiser.riot.core.enums.RiotApiRegionUri;
import com.wkaiser.riot.core.exception.RiotApiException;
import com.wkaiser.riot.core.http.RiotApiClient;
import com.wkaiser.riot.core.testsupport.Fixtures;
import com.wkaiser.riot.lol.match.application.port.MatchPort;
import com.wkaiser.riot.lol.match.domain.Match;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotMatchAdapterTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "test-puuid-abc123";
    private static final String IDS_PATH = "/lol/match/v5/matches/by-puuid/" + PUUID + "/ids";

    private WireMockServer wireMock;
    private MatchPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotMatchAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getMatchIdsByPuuid_sendsAllQueryParams_andSendsApiKeyHeader() {
        stubFor(get(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("5"))
                .withQueryParam("start", equalTo("0"))
                .withQueryParam("queue", equalTo("420"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("match-ids.json"))));

        List<String> ids = adapter.getMatchIdsByPuuid(REGION, PUUID, 5, 0, 420);

        assertThat(ids).containsExactly("NA1_4600000001", "NA1_4600000002", "NA1_4600000003");
        verify(getRequestedFor(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("5"))
                .withQueryParam("start", equalTo("0"))
                .withQueryParam("queue", equalTo("420"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getMatchIdsByPuuid_omitsQueueParam_whenQueueNull() {
        stubFor(get(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("10"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("match-ids.json"))));

        adapter.getMatchIdsByPuuid(REGION, PUUID, 10, null, null);

        verify(getRequestedFor(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("10"))
                .withQueryParam("start", absent())
                .withQueryParam("queue", absent()));
    }

    @Test
    void getMatchById_parsesNestedMatch() {
        stubFor(get(urlEqualTo("/lol/match/v5/matches/NA1_4600000001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("match.json"))));

        Match match = adapter.getMatchById(REGION, "NA1_4600000001");

        assertThat(match.getMetadata().getMatchId()).isEqualTo("NA1_4600000001");
        assertThat(match.getInfo().getGameDuration()).isEqualTo(1834L);
        assertThat(match.getInfo().getParticipants()).hasSize(2);
        assertThat(match.getInfo().getParticipants().get(0).getChampionName()).isEqualTo("Ahri");
        assertThat(match.getInfo().getParticipants().get(0).isWin()).isTrue();
        verify(getRequestedFor(urlEqualTo("/lol/match/v5/matches/NA1_4600000001"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo("/lol/match/v5/matches/NA1_MISSING"))
                .willReturn(aResponse().withStatus(500).withBody("server error")));

        assertThatThrownBy(() -> adapter.getMatchById(REGION, "NA1_MISSING"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(500);
    }
}
