package com.wkaiser.riotapimcpserver.summoner.application.port;

import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;

/** Outbound port for retrieving League of Legends summoner data. */
public interface SummonerPort {

    Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName);

    Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid);

    Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId);
}
