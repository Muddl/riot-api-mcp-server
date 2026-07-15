package com.wkaiser.riot.lol.summoner.application.port;

import com.wkaiser.riot.core.enums.RiotApiPlatformUri;
import com.wkaiser.riot.lol.summoner.domain.Summoner;

/** Outbound port for retrieving League of Legends summoner data. */
public interface SummonerPort {

    Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName);

    Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid);

    Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId);
}
