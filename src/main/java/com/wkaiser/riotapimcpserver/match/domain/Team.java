package com.wkaiser.riotapimcpserver.match.domain;

import lombok.Builder;
import lombok.Data;
import java.util.List;

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
