package com.wkaiser.riotapimcpserver.riot.lol.spectator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents observer/spectator information for a live game.
 * Contains the encryption key needed for spectating the game.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Observer {
    private String encryptionKey;
}