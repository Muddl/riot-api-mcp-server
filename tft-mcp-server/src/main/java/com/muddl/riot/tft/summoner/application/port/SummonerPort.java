package com.muddl.riot.tft.summoner.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.domain.Summoner;

/** Outbound port for Riot TFT-Summoner-V1 data. Platform-routed. */
public interface SummonerPort {
    Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid);
}
