package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One trait a participant activated. {@code tierCurrent > 0} means the trait was active. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trait {
    private String name;

    @JsonProperty("num_units")
    private Integer numUnits;

    private Integer style;

    @JsonProperty("tier_current")
    private Integer tierCurrent;

    @JsonProperty("tier_total")
    private Integer tierTotal;
}
