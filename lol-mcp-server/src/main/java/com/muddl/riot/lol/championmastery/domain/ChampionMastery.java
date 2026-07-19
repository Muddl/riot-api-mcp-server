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
    // Boxed types throughout: Riot returns null for fields tied to systems it has retired — notably
    // chestGranted and tokensEarned after the 2024 mastery/chest revamp — and a primitive fails
    // deserialization ("Cannot map null into type boolean/int"). The live eval caught chestGranted;
    // the rest are boxed defensively so a future null on the revamped mastery shape can't recur. See
    // gotchas.md.
    private String puuid;
    private Long championId;
    private Integer championLevel;
    private Integer championPoints;
    private Long lastPlayTime;
    private Long championPointsSinceLastLevel;
    private Long championPointsUntilNextLevel;
    private Boolean chestGranted;
    private Integer tokensEarned;
}
