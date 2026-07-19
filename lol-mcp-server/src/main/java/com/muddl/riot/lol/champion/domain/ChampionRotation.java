package com.muddl.riot.lol.champion.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The current free-to-play champion rotation for a platform (Riot Champion-V3). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChampionRotation {
    private List<Integer> freeChampionIds;
    private List<Integer> freeChampionIdsForNewPlayers;
    // Boxed: Riot returns this as null in some responses, and a primitive int fails deserialization
    // ("Cannot map null into type int"). The live eval caught it — see gotchas.md.
    private Integer maxNewPlayerLevel;
}
