package com.muddl.riot.lol.match.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a champion ban in champion select phase.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ban {
    private int championId;
    private int pickTurn;
}
