package com.wkaiser.riotapimcpserver.riot.lol.summoner.dto;

import lombok.Data;

/**
 * Represents a League of Legends summoner profile.
 * Contains summoner-specific information like level, name, and IDs.
 */
@Data
public class Summoner {
    private String accountId;
    private int profileIconId;
    private long revisionDate;
    private String name;
    private String id;
    private String puuid;
    private long summonerLevel;
}
