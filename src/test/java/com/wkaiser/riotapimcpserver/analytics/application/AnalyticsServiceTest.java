package com.wkaiser.riotapimcpserver.analytics.application;

import com.wkaiser.riotapimcpserver.account.application.RiotAccountService;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import com.wkaiser.riotapimcpserver.analytics.domain.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.match.application.MatchService;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.match.domain.MatchInfo;
import com.wkaiser.riotapimcpserver.match.domain.Participant;
import com.wkaiser.riotapimcpserver.summoner.application.SummonerService;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "puuid-1";

    @Mock
    private RiotAccountService accountService;
    @Mock
    private SummonerService summonerService;
    @Mock
    private MatchService matchService;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void returns_empty_analytics_when_no_matches() {
        when(accountService.getAccountByRiotId("Player", "NA1"))
                .thenReturn(RiotAccount.builder().puuid(PUUID).gameName("Player").tagLine("NA1").build());
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().name("Player").summonerLevel(100).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), eq(0), any()))
                .thenReturn(List.of());

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 5);

        assertThat(result.getMatchCount()).isZero();
        assertThat(result.getSummonerName()).isEqualTo("Player");
    }

    @Test
    void computes_win_rate_and_kda_over_matches() {
        when(accountService.getAccountByRiotId("Player", "NA1"))
                .thenReturn(RiotAccount.builder().puuid(PUUID).gameName("Player").tagLine("NA1").build());
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().name("Player").summonerLevel(100).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), eq(0), any()))
                .thenReturn(List.of("NA1_1", "NA1_2"));
        when(matchService.getMatchById(REGION, "NA1_1")).thenReturn(match(true, 10, 2, 5));
        when(matchService.getMatchById(REGION, "NA1_2")).thenReturn(match(false, 4, 6, 3));

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 2);

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
        MatchInfo info = MatchInfo.builder()
                .gameDuration(1800L)
                .participants(List.of(p))
                .build();
        return Match.builder().info(info).build();
    }
}
