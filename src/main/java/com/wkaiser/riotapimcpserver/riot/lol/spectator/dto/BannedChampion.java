package com.wkaiser.riotapimcpserver.riot.lol.spectator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a banned champion in a live game.
 * Contains information about which champion was banned by which team and when.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BannedChampion {
    private long championId;
    private long teamId;
    private int pickTurn;
}