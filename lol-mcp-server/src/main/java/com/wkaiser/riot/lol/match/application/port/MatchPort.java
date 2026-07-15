package com.wkaiser.riot.lol.match.application.port;

import com.wkaiser.riot.core.enums.RiotApiRegionUri;
import com.wkaiser.riot.lol.match.domain.Match;
import java.util.List;

/** Outbound port for retrieving League of Legends match data. */
public interface MatchPort {

    List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue);

    Match getMatchById(RiotApiRegionUri region, String matchId);
}
