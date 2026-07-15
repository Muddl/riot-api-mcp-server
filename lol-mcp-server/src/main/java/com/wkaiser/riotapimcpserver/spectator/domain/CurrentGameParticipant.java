package com.wkaiser.riotapimcpserver.spectator.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a participant in a live League of Legends game.
 * Contains information about the player, their champion, summoner spells, runes, and game customizations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurrentGameParticipant {
    private long championId;
    private Perks perks;
    private long profileIconId;
    private boolean bot;
    private long teamId;
    private String summonerName;
    private String summonerId;
    private String puuid;
    private long summonerLevel;
    private long spell1Id;
    private long spell2Id;
    private List<GameCustomizationObject> gameCustomizationObjects;
}
