package com.muddl.riot.tft.analytics.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.analytics.application.AnalyticsService;
import com.muddl.riot.tft.analytics.domain.PlayerMatchAnalytics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsToolTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    @Mock
    private AnalyticsService mockAnalyticsService;

    @InjectMocks
    private AnalyticsTool analyticsTool;

    @Test
    void getPlayerMatchAnalytics_passesArgsThrough() {
        PlayerMatchAnalytics analytics =
                PlayerMatchAnalytics.builder().riotId("Player#NA1").build();
        when(mockAnalyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 20))
                .thenReturn(analytics);

        assertThat(analyticsTool.getPlayerMatchAnalytics("Player#NA1", "NA1", "AMERICAS", 20))
                .isSameAs(analytics);
        verify(mockAnalyticsService).getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 20);
    }

    @Test
    void getPlayerMatchAnalytics_normalizesCaseBeforeDelegating() {
        PlayerMatchAnalytics analytics =
                PlayerMatchAnalytics.builder().riotId("Player#NA1").build();
        when(mockAnalyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 20))
                .thenReturn(analytics);

        assertThat(analyticsTool.getPlayerMatchAnalytics("Player#NA1", "na1", "americas", 20))
                .isSameAs(analytics);
        verify(mockAnalyticsService).getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 20);
    }

    @Test
    void getPlayerMatchAnalytics_defaultsMatchCount_whenNull() {
        PlayerMatchAnalytics analytics =
                PlayerMatchAnalytics.builder().riotId("Player#NA1").build();
        when(mockAnalyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10))
                .thenReturn(analytics);

        assertThat(analyticsTool.getPlayerMatchAnalytics("Player#NA1", "NA1", "AMERICAS", null))
                .isSameAs(analytics);
        verify(mockAnalyticsService).getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);
    }

    @Test
    void getPlayerMatchAnalytics_clampsMatchCount_aboveMax() {
        PlayerMatchAnalytics analytics =
                PlayerMatchAnalytics.builder().riotId("Player#NA1").build();
        when(mockAnalyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 100))
                .thenReturn(analytics);

        assertThat(analyticsTool.getPlayerMatchAnalytics("Player#NA1", "NA1", "AMERICAS", 500))
                .isSameAs(analytics);
        verify(mockAnalyticsService).getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 100);
    }

    @Test
    void getPlayerMatchAnalytics_clampsMatchCount_belowMin() {
        PlayerMatchAnalytics analytics =
                PlayerMatchAnalytics.builder().riotId("Player#NA1").build();
        when(mockAnalyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 1))
                .thenReturn(analytics);

        assertThat(analyticsTool.getPlayerMatchAnalytics("Player#NA1", "NA1", "AMERICAS", -5))
                .isSameAs(analytics);
        verify(mockAnalyticsService).getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 1);
    }

    @Test
    void getPlayerMatchAnalytics_invalidPlatform_throws() {
        assertThatThrownBy(() -> analyticsTool.getPlayerMatchAnalytics("Player#NA1", "INVALID", "AMERICAS", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getPlayerMatchAnalytics_invalidRegion_throws() {
        assertThatThrownBy(() -> analyticsTool.getPlayerMatchAnalytics("Player#NA1", "NA1", "INVALID", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
