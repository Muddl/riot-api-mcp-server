package com.wkaiser.riotapimcpserver.riot.lol.match.dto;

import lombok.Data;
import java.util.List;

/**
 * Detailed information about a League of Legends match.
 */
@Data
public class MatchInfo {
    private long gameCreation;
    private long gameDuration;
    private long gameEndTimestamp;
    private long gameId;
    private String gameMode;
    private String gameName;
    private long gameStartTimestamp;
    private String gameType;
    private String gameVersion;
    private int mapId;
    private List<Participant> participants;
    private String platformId;
    private int queueId;
    private List<Team> teams;
    private String tournamentCode;
}
