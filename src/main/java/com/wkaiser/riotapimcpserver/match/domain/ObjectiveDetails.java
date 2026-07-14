package com.wkaiser.riotapimcpserver.match.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents details of a specific objective type in a League of Legends match.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectiveDetails {
    private boolean first;
    private int kills;
}
