package com.muddl.riot.tft.match.application.port;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;

/** Outbound port for Riot TFT-Match-V1 data. Region-routed. */
public interface MatchPort {

    List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start);

    TftMatch getMatchById(RiotApiRegionUri region, String matchId);
}
