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
                .thenReturn(Summoner.builder().summonerLevel(300L).build());
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
                .thenReturn(Summoner.builder().summonerLevel(10L).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any()))
                .thenReturn(List.of());

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getMatchCount()).isEqualTo(0);
        assertThat(a.getRiotId()).isEqualTo("Player#NA1");
        assertThat(a.getSummonerLevel()).isEqualTo(10);
    }

    @Test
    void nullNumericFields_doNotThrow_andYieldSafeDefaults() {
        // Riot can return null for boxed numeric fields; the boxed DTOs survive deserialization, and
        // the analytics consumer must not re-introduce the NPE when it unboxes them.
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().build()); // summonerLevel null
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any()))
                .thenReturn(List.of("NA1_1"));
        Participant p = Participant.builder()
                .puuid(PUUID)
                // placement / level / goldLeft left null
                .traits(List.of(Trait.builder().name("Set10_Punk").build())) // tierCurrent null
                .units(List.of(Unit.builder().characterId("TFT10_Jinx").build()))
                .build();
        when(matchService.getMatchById(REGION, "NA1_1"))
                .thenReturn(TftMatch.builder()
                        .info(MatchInfo.builder().participants(List.of(p)).build())
                        .build());

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getMatchCount()).isEqualTo(1);
        assertThat(a.getSummonerLevel()).isEqualTo(0);
        assertThat(a.getAvgPlacement()).isEqualTo("0.00");
        assertThat(a.getTop4Rate()).isEqualTo("0.00%");
        assertThat(a.getFirstPlaceRate()).isEqualTo("0.00%");
        assertThat(a.getAvgLevel()).isEqualTo("0.00");
        assertThat(a.getAvgGoldLeft()).isEqualTo("0.00");
        assertThat(a.getMostPlayedTraits()).isEmpty(); // tierCurrent null -> not an active trait
        assertThat(a.getMostPlayedUnits()).hasSize(1); // characterId non-null -> still counted
    }

    @Test
    void singleGame_firstPlace_isAllTop4() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().summonerLevel(1L).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any()))
                .thenReturn(List.of("NA1_1"));
        when(matchService.getMatchById(REGION, "NA1_1")).thenReturn(matchWith(1, 9, 4, "Set10_Punk", "TFT10_Jinx"));

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getAvgPlacement()).isEqualTo("1.00");
        assertThat(a.getTop4Rate()).isEqualTo("100.00%");
        assertThat(a.getFirstPlaceRate()).isEqualTo("100.00%");
    }
}
