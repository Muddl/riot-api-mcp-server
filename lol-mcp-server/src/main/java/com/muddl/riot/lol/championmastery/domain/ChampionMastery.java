package com.muddl.riot.lol.championmastery.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's mastery of one champion (Riot Champion-Mastery-V4, by-puuid). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChampionMastery {
    private String puuid;
    private long championId;
    private int championLevel;
    private int championPoints;
    private long lastPlayTime;
    private long championPointsSinceLastLevel;
    private long championPointsUntilNextLevel;
    private boolean chestGranted;
    private int tokensEarned;
}
