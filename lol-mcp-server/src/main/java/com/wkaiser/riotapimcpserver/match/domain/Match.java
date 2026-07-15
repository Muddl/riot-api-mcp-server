package com.wkaiser.riotapimcpserver.match.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a League of Legends match along with its metadata and detailed information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {
    private MatchMetadata metadata;
    private MatchInfo info;
}
