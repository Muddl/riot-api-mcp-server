package com.muddl.riot.tft.match.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftMatchAdapterTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "puuid-a";
    private static final String MATCH_ID = "NA1_4600000001";

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
        adapter = new RiotTftMatchAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getMatchIdsByPuuid_buildsPagedUrl_andParsesArray() {
        String url = "/tft/match/v1/matches/by-puuid/" + PUUID + "/ids?count=20&start=0";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-match-ids.json"))));

        List<String> ids = adapter.getMatchIdsByPuuid(REGION, PUUID, 20, 0);

        assertThat(ids).containsExactly("NA1_4600000001", "NA1_4600000002");
        verify(getRequestedFor(urlEqualTo(url)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getMatchById_parsesSnakeCaseAndNestedComp() {
        String url = "/tft/match/v1/matches/" + MATCH_ID;
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-match.json"))));

        TftMatch match = adapter.getMatchById(REGION, MATCH_ID);

        assertThat(match.getMetadata().getMatchId()).isEqualTo(MATCH_ID);
        assertThat(match.getInfo().getTftSetNumber()).isEqualTo(10);
        assertThat(match.getInfo().getParticipants()).hasSize(2);
        assertThat(match.getInfo().getParticipants().get(0).getPlacement()).isEqualTo(1);
        assertThat(match.getInfo().getParticipants().get(0).getGoldLeft()).isEqualTo(3);
        assertThat(match.getInfo().getParticipants().get(0).getUnits().get(0).getCharacterId())
                .isEqualTo("TFT10_Jinx");
    }
}
