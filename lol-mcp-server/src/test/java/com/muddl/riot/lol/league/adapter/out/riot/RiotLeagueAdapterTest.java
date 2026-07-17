package com.muddl.riot.lol.league.adapter.out.riot;

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
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotLeagueAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String ENTRIES_URL = "/lol/league/v4/entries/by-puuid/" + PUUID;
    private static final String CHALLENGER_URL = "/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5";

    private WireMockServer wireMock;
    private LeaguePort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotLeagueAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getLeagueEntriesByPuuid_parsesArray_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo(ENTRIES_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("league-entries.json"))));

        List<LeagueEntry> entries = adapter.getLeagueEntriesByPuuid(PLATFORM, PUUID);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getQueueType()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(entries.get(0).getTier()).isEqualTo("GOLD");
        assertThat(entries.get(0).getRank()).isEqualTo("II");
        assertThat(entries.get(0).getPuuid()).isEqualTo(PUUID);
        verify(getRequestedFor(urlEqualTo(ENTRIES_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getLeagueEntriesByPuuid_emptyArray_returnsEmptyList() {
        stubFor(get(urlEqualTo(ENTRIES_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(adapter.getLeagueEntriesByPuuid(PLATFORM, PUUID)).isEmpty();
    }

    @Test
    void getApexLeague_challenger_hitsExpectedUrl_andParsesBody() {
        stubFor(get(urlEqualTo(CHALLENGER_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("league-apex.json"))));

        LeagueList league = adapter.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5");

        assertThat(league.getTier()).isEqualTo("CHALLENGER");
        assertThat(league.getQueue()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(league.getEntries()).hasSize(1);
        assertThat(league.getEntries().get(0).getLeaguePoints()).isEqualTo(1355);
        verify(getRequestedFor(urlEqualTo(CHALLENGER_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(ENTRIES_URL))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> adapter.getLeagueEntriesByPuuid(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(403);
    }
}
