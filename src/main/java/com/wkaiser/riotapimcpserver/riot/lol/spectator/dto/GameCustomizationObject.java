package com.wkaiser.riotapimcpserver.riot.lol.spectator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

/**
 * Represents game customization data for a participant in a live game.
 * Contains information about custom game settings and configurations.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameCustomizationObject {
    private String category;
    private String content;
}