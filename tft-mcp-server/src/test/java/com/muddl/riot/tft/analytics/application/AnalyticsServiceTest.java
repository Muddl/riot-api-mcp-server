package com.muddl.riot.tft.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.analytics.domain.PlayerMatchAnalytics;
import com.muddl.riot.tft.match.application.MatchService;
import com.muddl.riot.tft.match.domain.MatchInfo;
import com.muddl.riot.tft.match.domain.Participant;
import com.muddl.riot.tft.match.domain.TftMatch;
import com.muddl.riot.tft.match.domain.Trait;
import com.muddl.riot.tft.match.domain.Unit;
import com.muddl.riot.tft.summoner.application.SummonerService;
import com.muddl.riot.tft.summoner.domain.Summoner;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "puuid-1";

    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SummonerService summonerService = mock(SummonerService.class);
    private final MatchService matchService = mock(MatchService.class);
    private final AnalyticsService service = new AnalyticsService(resolver, summonerService, matchService);

    private TftMatch matchWith(int placement, int level, int goldLeft, String traitName, String unitId) {
        Participant p = Participant.builder()
                .puuid(PUUID)
                .placement(placement)
                .level(level)
                .goldLeft(goldLeft)
                .traits(List.of(Trait.builder().name(traitName).tierCurrent(2).build()))
                .units(List.of(Unit.builder().characterId(unitId).build()))
                .build();
        return TftMatch.builder()
                .info(MatchInfo.builder().participants(List.of(p)).build())
                .build();
    }

    @Test
    void aggregatesPlacementTop4AndComps_overTwoMatches() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().summonerLevel(300).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any()))
                .thenReturn(List.of("NA1_1", "NA1_2"));
        when(matchService.getMatchById(REGION, "NA1_1")).thenReturn(matchWith(1, 9, 2, "Set10_Punk", "TFT10_Jinx"));
        when(matchService.getMatchById(REGION, "NA1_2")).thenReturn(matchWith(5, 8, 0, "Set10_Punk", "TFT10_Sona"));

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getMatchCount()).isEqualTo(2);
        assertThat(a.getAvgPlacement()).isEqualTo("3.00");
        assertThat(a.getTop4Rate()).isEqualTo("50.00%");
        assertThat(a.getFirstPlaceRate()).isEqualTo("50.00%");
        assertThat(a.getSummonerLevel()).isEqualTo(300);
        assertThat(a.getMostPlayedTraits().get(0)).contains("Set10_Punk");
    }

    @Test
    void zeroGames_returnsEmptySummary_withoutDivideByZero() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().summonerLevel(10).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any()))
                .thenReturn(List.of());

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getMatchCount()).isEqualTo(0);
        assertThat(a.getRiotId()).isEqualTo("Player#NA1");
        assertThat(a.getSummonerLevel()).isEqualTo(10);
    }

    @Test
    void singleGame_firstPlace_isAllTop4() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().summonerLevel(1).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any()))
                .thenReturn(List.of("NA1_1"));
        when(matchService.getMatchById(REGION, "NA1_1")).thenReturn(matchWith(1, 9, 4, "Set10_Punk", "TFT10_Jinx"));

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getAvgPlacement()).isEqualTo("1.00");
        assertThat(a.getTop4Rate()).isEqualTo("100.00%");
        assertThat(a.getFirstPlaceRate()).isEqualTo("100.00%");
    }
}
