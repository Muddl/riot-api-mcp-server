package com.wkaiser.riot.lol.spectator.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the main live game data structure from the Riot Spectator API.
 * Contains comprehensive information about an ongoing League of Legends match
 * including participants, bans, observers, and game metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurrentGameInfo {
    private long gameId;
    private String gameType;
    private long gameStartTime;
    private long mapId;
    private long gameLength;
    private String platformId;
    private String gameMode;
    private List<BannedChampion> bannedChampions;
    private long gameQueueConfigId;
    private Observer observers;
    private List<CurrentGameParticipant> participants;
}
