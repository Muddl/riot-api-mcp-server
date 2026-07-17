package com.muddl.riot.lol.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An apex league (challenger/grandmaster/master) for one queue (Riot League-V4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueList {
    private String leagueId;
    private String tier;
    private String name;
    private String queue;
    private List<LeagueItem> entries;
}
