package com.wkaiser.riotapimcpserver.riot.lol.match.dto;

import lombok.Data;
import java.util.List;

/**
 * Represents a League of Legends match along with its metadata and detailed information.
 */
@Data
public class Match {
    private MatchMetadata metadata;
    private MatchInfo info;
}
