package com.wkaiser.riotapimcpserver.riot.lol.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Contains comprehensive analytics about a player's recent League of Legends matches.
 * This class aggregates statistics from multiple matches to provide insight into
 * a player's performance trends.
 */
@Data
@Builder
public class PlayerMatchAnalytics {
    private String riotId;
    private String summonerName;
    private long summonerLevel;
    private int matchCount;
    private int wins;
    private int losses;
    private String winRate;
    private String avgKills;
    private String avgDeaths;
    private String avgAssists;
    private String avgKda;
    private List<String> mostPlayedChampions;
    private double avgVisionScore;
    private String avgCreepScore;
    private String avgGameDuration;
    private List<String> mostPlayedRoles;
}
