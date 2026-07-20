package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** TFT game info: timing, set/version, and per-player participation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchInfo {
    @JsonProperty("game_datetime")
    private long gameDatetime;

    @JsonProperty("game_length")
    private double gameLength;

    @JsonProperty("game_version")
    private String gameVersion;

    @JsonProperty("queue_id")
    private int queueId;

    @JsonProperty("tft_set_number")
    private int tftSetNumber;

    @JsonProperty("tft_game_type")
    private String tftGameType;

    private List<Participant> participants;
}
