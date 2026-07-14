package com.wkaiser.riotapimcpserver.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Metadata information about a League of Legends match.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchMetadata {
    private String dataVersion;
    private String matchId;
    private List<String> participants; // List of PUUIDs
}
