package com.muddl.riot.lol.league.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An apex league (challenger/grandmaster/master) for one queue (Riot League-V4). */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueList {
    private String leagueId;
    private String tier;
    private String name;
    private String queue;
    private List<LeagueItem> entries;

    /**
     * Total entries in the league before any {@code count} bound was applied. Lets a caller that
     * received a capped {@link #entries} list still know the real ladder size.
     */
    private int totalEntries;
}
