package com.wkaiser.riotapimcpserver.riot.lol.spectator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents rune and mastery information for a participant in a live game.
 * Contains both keystones and rune trees (perk styles) as well as stat modifiers.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Perks {
    private List<Long> perkIds;
    private List<PerkStyle> perkStyle;
    private List<Long> perkSubStyle;

    /**
     * Represents a rune tree/style with its selection and sub-selections.
     */
    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PerkStyle {
        private String description;
        private List<PerkStyleSelection> selections;
        private long style;
    }

    /**
     * Represents a specific rune selection within a perk style.
     */
    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PerkStyleSelection {
        private long perk;
        private int var1;
        private int var2;
        private int var3;
    }
}