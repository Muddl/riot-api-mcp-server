package com.muddl.riot.lol.challenges.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's progress on one individual challenge (Riot LoL-Challenges-V1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeProgress {
    private long challengeId;
    private String level;
    private double value;
    private double percentile;
    private long achievedTime;
}
