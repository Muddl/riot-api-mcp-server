package com.muddl.riot.lol.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.analytics.domain.PlayerMatchAnalytics;
import com.muddl.riot.lol.match.application.InMemoryMatchPort;
import com.muddl.riot.lol.match.application.MatchService;
import com.muddl.riot.lol.match.domain.Match;
import com.muddl.riot.lol.match.domain.MatchInfo;
import com.muddl.riot.lol.match.domain.Participant;
import com.muddl.riot.lol.summoner.application.InMemorySummonerPort;
import com.muddl.riot.lol.summoner.application.SummonerService;
import com.muddl.riot.lol.summoner.domain.Summoner;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PLAYER = "Player#NA1";
    private static final String PUUID = "puuid-1";

    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final InMemoryMatchPort matchPort = new InMemoryMatchPort();

    private final SummonerService summonerService = new SummonerService(summonerPort, resolver);
    private final AnalyticsService analyticsService =
            new AnalyticsService(resolver, summonerService, new MatchService(matchPort, resolver));

    private void givenPlayer() {
        when(resolver.resolvePuuid(PLAYER)).thenReturn(PUUID);
        summonerPort.putByPuuid(
                PLATFORM,
                PUUID,
                Summoner.builder().name("Player").summonerLevel(100).build());
    }

    @Test
    void returnsEmptyAnalytics_whenNoMatches() {
        givenPlayer();
        matchPort.putMatchIds(PUUID, List.of());

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics(PLAYER, PLATFORM, REGION, 5);

        assertThat(result.getMatchCount()).isZero();
        assertThat(result.getSummonerName()).isEqualTo("Player");
        assertThat(result.getSummonerLevel()).isEqualTo(100L);
        assertThat(result.getWinRate()).isNull();
        assertThat(result.getAvgKda()).isNull();
    }

    @Test
    void computesPerfectKda_whenZeroDeaths() {
        givenPlayer();
        matchPort.putMatchIds(PUUID, List.of("NA1_1"));
        matchPort.putMatch("NA1_1", match(true, 5, 0, 3));

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics(PLAYER, PLATFORM, REGION, 1);

        assertThat(result.getMatchCount()).isEqualTo(1);
        assertThat(result.getWins()).isEqualTo(1);
        assertThat(result.getWinRate()).isEqualTo("100.00%");
        assertThat(result.getAvgDeaths()).isEqualTo("0.00");
        // Zero deaths => perfect KDA == kills + assists == 8.
        assertThat(result.getAvgKda()).isEqualTo("8.00");
    }

    @Test
    void computesWinRateAndAverages_overMultipleMatches() {
        givenPlayer();
        matchPort.putMatchIds(PUUID, List.of("NA1_1", "NA1_2"));
        matchPort.putMatch("NA1_1", match(true, 10, 2, 5));
        matchPort.putMatch("NA1_2", match(false, 4, 6, 3));

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics(PLAYER, PLATFORM, REGION, 2);

        assertThat(result.getMatchCount()).isEqualTo(2);
        assertThat(result.getWins()).isEqualTo(1);
        assertThat(result.getLosses()).isEqualTo(1);
        assertThat(result.getWinRate()).isEqualTo("50.00%");
        assertThat(result.getAvgKills()).isEqualTo("7.00");
    }

    private Match match(boolean win, int kills, int deaths, int assists) {
        Participant p = Participant.builder()
                .puuid(PUUID)
                .win(win)
                .kills(kills)
                .deaths(deaths)
                .assists(assists)
                .championName("Ahri")
                .teamPosition("MIDDLE")
                .visionScore(20)
                .totalMinionsKilled(150)
                .neutralMinionsKilled(10)
                .build();
        MatchInfo info =
                MatchInfo.builder().gameDuration(1800L).participants(List.of(p)).build();
        return Match.builder().info(info).build();
    }
}
