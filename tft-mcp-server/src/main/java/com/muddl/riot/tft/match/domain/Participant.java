package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One player's participation in a TFT match: placement, board level, and the comp they fielded. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Participant {
    private String puuid;
    private Integer placement;
    private Integer level;

    @JsonProperty("gold_left")
    private Integer goldLeft;

    @JsonProperty("last_round")
    private Integer lastRound;

    @JsonProperty("players_eliminated")
    private Integer playersEliminated;

    @JsonProperty("time_eliminated")
    private Double timeEliminated;

    @JsonProperty("total_damage_to_players")
    private Integer totalDamageToPlayers;

    private Companion companion;
    private List<Trait> traits;
    private List<Unit> units;
    private List<String> augments;
}
