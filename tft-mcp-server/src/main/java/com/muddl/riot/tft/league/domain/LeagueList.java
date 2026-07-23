package com.muddl.riot.tft.league.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A TFT apex league (challenger/grandmaster/master) or a league fetched by id (Riot TFT-League-V1). */
@Data
@Builder(toBuilder = true)
// Forces bean-style deserialization: without it Jackson picks the Lombok all-args constructor as a
// properties-based creator and maps the absent totalEntries to null, failing on the primitive. See
// gotchas.md, "Adding a primitive field to an existing Riot-mapped DTO".
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
