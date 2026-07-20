package com.muddl.riot.tft.league.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftLeagueAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

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
        adapter = new RiotTftLeagueAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getLeagueEntriesByPuuid_parsesArray_andSendsToken() {
        String url = "/tft/league/v1/by-puuid/puuid-1";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-entries.json"))));

        List<LeagueEntry> entries = adapter.getLeagueEntriesByPuuid(PLATFORM, "puuid-1");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getQueueType()).isEqualTo("RANKED_TFT");
        assertThat(entries.get(0).getTier()).isEqualTo("DIAMOND");
        verify(getRequestedFor(urlEqualTo(url)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getLeagueEntriesByPuuid_returnsEmpty_onEmptyArray() {
        String url = "/tft/league/v1/by-puuid/unranked";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(adapter.getLeagueEntriesByPuuid(PLATFORM, "unranked")).isEmpty();
    }

    @Test
    void getApexLeague_usesTierPath_withNoQueueSuffix() {
        String url = "/tft/league/v1/challenger";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-apex.json"))));

        LeagueList list = adapter.getApexLeague(PLATFORM, ApexTier.CHALLENGER);

        assertThat(list.getTier()).isEqualTo("CHALLENGER");
        assertThat(list.getEntries()).hasSize(1);
        assertThat(list.getEntries().get(0).getLeaguePoints()).isEqualTo(1400);
    }

    @Test
    void getEntriesByTier_buildsPagedPath() {
        String url = "/tft/league/v1/entries/DIAMOND/II?page=1";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-entries.json"))));

        List<LeagueEntry> entries = adapter.getEntriesByTier(PLATFORM, "DIAMOND", "II", 1);

        assertThat(entries).hasSize(1);
        verify(getRequestedFor(urlEqualTo(url)));
    }

    @Test
    void getLeagueById_usesLeaguePath() {
        String url = "/tft/league/v1/leagues/league-uuid-chal";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-apex.json"))));

        assertThat(adapter.getLeagueById(PLATFORM, "league-uuid-chal").getName())
                .isEqualTo("Zed's Assassins");
    }

    @Test
    void getRatedLadder_usesQueuePath() {
        String url = "/tft/league/v1/rated-ladders/RANKED_TFT_TURBO/top";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-rated-ladder.json"))));

        List<RatedLadderEntry> ladder = adapter.getRatedLadder(PLATFORM, "RANKED_TFT_TURBO");

        assertThat(ladder).hasSize(1);
        assertThat(ladder.get(0).getRatedTier()).isEqualTo("BLUE");
        assertThat(ladder.get(0).getRatedRating()).isEqualTo(1800);
    }

    @Test
    void getLeagueById_notFound_mapsToRiotApiException() {
        String url = "/tft/league/v1/leagues/missing-uuid";
        stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getLeagueById(PLATFORM, "missing-uuid"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
