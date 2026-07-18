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
    private String level;
    private double current;
    private double max;
    private double percentile;
}
