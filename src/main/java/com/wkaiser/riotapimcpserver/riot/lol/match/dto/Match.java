package com.wkaiser.riotapimcpserver.riot.lol.match.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Represents a League of Legends match along with its metadata and detailed information.
 */
@Data
@Builder
public class Match {
    private MatchMetadata metadata;
    private MatchInfo info;
}
