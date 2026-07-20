package com.muddl.riot.tft.match.application;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link MatchPort}. */
public class InMemoryMatchPort implements MatchPort {

    private final Map<String, List<String>> idsByPuuid = new HashMap<>();
    private final Map<String, TftMatch> matchById = new HashMap<>();

    public InMemoryMatchPort putIds(String puuid, List<String> ids) {
        idsByPuuid.put(puuid, ids);
        return this;
    }

    public InMemoryMatchPort putMatch(String matchId, TftMatch match) {
        matchById.put(matchId, match);
        return this;
    }

    @Override
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start) {
        return idsByPuuid.getOrDefault(puuid, List.of());
    }

    @Override
    public TftMatch getMatchById(RiotApiRegionUri region, String matchId) {
        return matchById.get(matchId);
    }
}
