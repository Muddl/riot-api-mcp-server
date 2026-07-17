package com.muddl.riot.lol.summoner.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.domain.Summoner;

/** Outbound port for retrieving League of Legends summoner data. */
public interface SummonerPort {

    Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid);
}
