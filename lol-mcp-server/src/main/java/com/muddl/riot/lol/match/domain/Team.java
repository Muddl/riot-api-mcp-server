package com.muddl.riot.lol.match.domain;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a team in a League of Legends match.
 */
@Data
@Builder
public class Team {
    private List<Ban> bans;
    private Objectives objectives;
    private int teamId;
    private boolean win;
}
