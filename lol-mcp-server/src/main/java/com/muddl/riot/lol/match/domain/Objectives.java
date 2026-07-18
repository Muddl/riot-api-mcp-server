package com.muddl.riot.lol.match.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents objectives taken by a team in a League of Legends match.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Objectives {
    private ObjectiveDetails baron;
    private ObjectiveDetails champion;
    private ObjectiveDetails dragon;
    private ObjectiveDetails inhibitor;
    private ObjectiveDetails riftHerald;
    private ObjectiveDetails tower;
}
