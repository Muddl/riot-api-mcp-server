package com.wkaiser.riotapimcpserver.riot.lol.match.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Represents details of a specific objective type in a League of Legends match.
 */
@Data
@Builder
public class ObjectiveDetails {
    private boolean first;
    private int kills;
}
