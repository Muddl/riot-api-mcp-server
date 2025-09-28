package com.wkaiser.riotapimcpserver.riot.lol.summoner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a League of Legends summoner profile.
 * Contains summoner-specific information like level, name, and IDs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Summoner {
    private String accountId;
    private int profileIconId;
    private long revisionDate;
    private String name;
    private String id;
    private String puuid;
    private long summonerLevel;
}