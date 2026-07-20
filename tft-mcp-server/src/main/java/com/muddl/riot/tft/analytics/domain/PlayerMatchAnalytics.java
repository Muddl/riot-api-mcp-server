package com.muddl.riot.tft.analytics.domain;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Aggregated analytics over a TFT player's recent matches. TFT-native stats (placement, top-4). */
@Data
@Builder
public class PlayerMatchAnalytics {
    private String riotId;
    private long summonerLevel;
    private int matchCount;
    private String avgPlacement;
    private String top4Rate;
    private String firstPlaceRate;
    private String avgLevel;
    private String avgGoldLeft;
    private List<String> mostPlayedTraits;
    private List<String> mostPlayedUnits;
}
