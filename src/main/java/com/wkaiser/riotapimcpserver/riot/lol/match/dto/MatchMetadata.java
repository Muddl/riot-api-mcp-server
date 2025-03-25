package com.wkaiser.riotapimcpserver.riot.lol.match.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Metadata information about a League of Legends match.
 */
@Data
@Builder
public class MatchMetadata {
    private String dataVersion;
    private String matchId;
    private List<String> participants; // List of PUUIDs
}
