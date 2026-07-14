package com.wkaiser.riotapimcpserver.match.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a champion ban in champion select phase.
 */
@Data
@Builder
public class Ban {
    private int championId;
    private int pickTurn;
}
