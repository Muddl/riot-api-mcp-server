package com.muddl.riot.lol.challenges.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's challenge standing: totals, per-category points, and per-challenge progress. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengesPlayerData {
    private ChallengePoints totalPoints;
    private Map<String, ChallengePoints> categoryPoints;
    private List<ChallengeProgress> challenges;
}
