package com.wkaiser.riotapimcpserver.riot.account.dto;

import lombok.Data;

/**
 * Represents a Riot Games account with global identity information.
 */
@Data
public class RiotAccount {
    private String puuid;
    private String gameName;
    private String tagLine;
}
