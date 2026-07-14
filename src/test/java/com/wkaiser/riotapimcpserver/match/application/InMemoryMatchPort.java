package com.wkaiser.riotapimcpserver.match.application;

import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link MatchPort} for fast, HTTP-free service tests. */
public class InMemoryMatchPort implements MatchPort {

    private final Map<String, List<String>> idsByPuuid = new HashMap<>();
    private final Map<String, Match> matchesById = new HashMap<>();

    public InMemoryMatchPort putMatchIds(String puuid, List<String> ids) {
        idsByPuuid.put(puuid, ids);
        return this;
    }

    public InMemoryMatchPort putMatch(String matchId, Match match) {
        matchesById.put(matchId, match);
        return this;
    }

    @Override
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        List<String> all = idsByPuuid.getOrDefault(puuid, List.of());
        return all.stream()
                .skip(start == null ? 0 : start)
                .limit(count == null ? Long.MAX_VALUE : count)
                .toList();
    }

    @Override
    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        return matchesById.get(matchId);
    }
}
