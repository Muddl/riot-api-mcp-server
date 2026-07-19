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
    // Boxed numerics: Riot returns null for these on challenges a player has not progressed on or
    // achieved (notably achievedTime), and a primitive long/double fails deserialization
    // ("Cannot map null into type long"). The live eval caught achievedTime. See gotchas.md.
    private Long challengeId;
    private String level;
    private Double value;
    private Double percentile;
    private Long achievedTime;
}
