package com.muddl.riot.tft.summoner.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A TFT summoner profile (Riot TFT-Summoner-V1). Name / encrypted-id fields are treated as possibly
 * absent under the PUUID migration; the domain does not depend on them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Summoner {
    private String puuid;
    private int profileIconId;
    private long revisionDate;
    private long summonerLevel;
}
