package com.muddl.riot.lol.clash.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's registration in a Clash tournament team (Riot Clash-V1, players by-puuid). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClashPlayer {
    private String summonerId;
    private String puuid;
    private String teamId;
    private String position;
    private String role;
}
