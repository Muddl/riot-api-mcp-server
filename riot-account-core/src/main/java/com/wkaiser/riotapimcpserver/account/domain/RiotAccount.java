package com.wkaiser.riotapimcpserver.account.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Riot Games account with global identity information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiotAccount {
    private String puuid;
    private String gameName;
    private String tagLine;
}
