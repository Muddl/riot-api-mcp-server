package com.wkaiser.riotapimcpserver.riot.lol.match.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Represents objectives taken by a team in a League of Legends match.
 */
@Data
@Builder
public class Objectives {
    private ObjectiveDetails baron;
    private ObjectiveDetails champion;
    private ObjectiveDetails dragon;
    private ObjectiveDetails inhibitor;
    private ObjectiveDetails riftHerald;
    private ObjectiveDetails tower;
}
