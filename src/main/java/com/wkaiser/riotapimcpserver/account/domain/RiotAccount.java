package com.wkaiser.riotapimcpserver.account.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a Riot Games account with global identity information.
 */
@Data
@Builder
public class RiotAccount {
    private String puuid;
    private String gameName;
    private String tagLine;
}
