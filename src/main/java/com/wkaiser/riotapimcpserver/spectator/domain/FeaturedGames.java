package com.wkaiser.riotapimcpserver.spectator.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a collection of featured League of Legends games.
 * Contains a list of currently featured/highlighted live games and refresh interval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeaturedGames {
    private List<CurrentGameInfo> gameList;
    private long clientRefreshInterval;
}
