package com.wkaiser.riotapimcpserver.match.application.port;

import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;

import java.util.List;

/** Outbound port for retrieving League of Legends match data. */
public interface MatchPort {

    List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue);

    Match getMatchById(RiotApiRegionUri region, String matchId);
}
