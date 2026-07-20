package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** TFT match metadata: the match id and the list of participant PUUIDs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchMetadata {
    @JsonProperty("data_version")
    private String dataVersion;

    @JsonProperty("match_id")
    private String matchId;

    private List<String> participants;
}
