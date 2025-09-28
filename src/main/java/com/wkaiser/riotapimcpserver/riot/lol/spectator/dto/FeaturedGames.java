package com.wkaiser.riotapimcpserver.riot.lol.spectator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents a collection of featured League of Legends games.
 * Contains a list of currently featured/highlighted live games and refresh interval.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeaturedGames {
    private List<CurrentGameInfo> gameList;
    private long clientRefreshInterval;
}