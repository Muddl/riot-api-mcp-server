package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The participant's Little Legend / companion. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Companion {
    @JsonProperty("content_ID")
    private String contentId;

    @JsonProperty("skin_ID")
    private int skinId;

    private String species;
}
