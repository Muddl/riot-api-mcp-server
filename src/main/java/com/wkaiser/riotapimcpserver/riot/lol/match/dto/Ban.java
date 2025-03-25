package com.wkaiser.riotapimcpserver.riot.lol.match.dto;

import lombok.Data;

/**
 * Represents a champion ban in champion select phase.
 */
@Data
public class Ban {
    private int championId;
    private int pickTurn;
}
