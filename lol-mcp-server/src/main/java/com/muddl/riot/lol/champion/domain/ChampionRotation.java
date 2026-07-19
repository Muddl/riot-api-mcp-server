package com.muddl.riot.lol.champion.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    // The live Champion-V3 endpoint returns the compact keys `sr` / `newplayer`, NOT the
    // developer-portal-documented `freeChampionIds` / `freeChampionIdsForNewPlayers`. The live eval
    // caught this (our fixture had encoded the documented shape). @JsonAlias accepts both so either
    // shape maps — see gotchas.md.
    @JsonAlias("sr")
    private List<Integer> freeChampionIds;

    @JsonAlias("newplayer")
    private List<Integer> freeChampionIdsForNewPlayers;

    // Absent from the live compact shape; present (sometimes null) in the documented shape. Boxed so
    // both "absent" and "present null" deserialize cleanly.
    private Integer maxNewPlayerLevel;
}
