package com.muddl.riot.lol.challenges.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's challenge standing: totals, per-category points, and per-challenge progress. */
@Data
@Builder(toBuilder = true)
// Forces bean-style deserialization: without it Jackson picks the Lombok all-args constructor as a
// properties-based creator and maps the absent totalChallenges to null, failing on the primitive.
// See gotchas.md, "Adding a primitive field to an existing Riot-mapped DTO".
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengesPlayerData {
    private ChallengePoints totalPoints;
    private Map<String, ChallengePoints> categoryPoints;
    private List<ChallengeProgress> challenges;

    /**
     * Total challenges returned by Riot before any {@code count} bound was applied. Lets a caller
     * that received a capped {@link #challenges} list know how many exist.
     */
    private int totalChallenges;
}
