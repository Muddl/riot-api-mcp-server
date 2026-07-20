package com.muddl.riot.tft.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry on a TFT rated (Hyper Roll) ladder (Riot TFT-League-V1 rated-ladders). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RatedLadderEntry {
    private String puuid;
    private String ratedTier;
    private Integer ratedRating;
    private Integer wins;
    private Integer previousUpdateLadderPosition;
}
