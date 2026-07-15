package com.wkaiser.riot.lol.match.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata information about a League of Legends match.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchMetadata {
    private String dataVersion;
    private String matchId;
    private List<String> participants; // List of PUUIDs
}
