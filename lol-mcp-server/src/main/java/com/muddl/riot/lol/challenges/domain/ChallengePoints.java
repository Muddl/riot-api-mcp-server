package com.muddl.riot.lol.challenges.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A points summary for a challenge category or the player total (Riot LoL-Challenges-V1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengePoints {
    // Boxed numerics: Riot returns null for these on categories a player has no progress in, and a
    // primitive double fails deserialization ("Cannot map null into type double"). See gotchas.md.
    private String level;
    private Double current;
    private Double max;
    private Double percentile;
}
