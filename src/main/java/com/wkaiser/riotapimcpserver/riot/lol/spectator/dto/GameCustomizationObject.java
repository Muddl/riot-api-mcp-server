package com.wkaiser.riotapimcpserver.riot.lol.spectator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents game customization data for a participant in a live game.
 * Contains information about custom game settings and configurations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameCustomizationObject {
    private String category;
    private String content;
}