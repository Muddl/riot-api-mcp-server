package com.wkaiser.riot.lol.match.application;

import com.wkaiser.riot.core.enums.RiotApiRegionUri;
import com.wkaiser.riot.lol.match.application.port.MatchPort;
import com.wkaiser.riot.lol.match.domain.Match;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends match data. Delegates retrieval to the
 * outbound {@link MatchPort}; holds no HTTP concerns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPort matchPort;

    public List<String> getMatchIdsByPuuid(
            RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        log.info("Fetching match IDs for PUUID: {}", puuid);
        return matchPort.getMatchIdsByPuuid(region, puuid, count, start, queue);
    }

    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        log.info("Fetching match details for match ID: {}", matchId);
        return matchPort.getMatchById(region, matchId);
    }
}
