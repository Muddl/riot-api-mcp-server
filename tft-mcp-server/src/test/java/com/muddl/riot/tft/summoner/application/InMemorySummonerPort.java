package com.muddl.riot.tft.summoner.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SummonerPort}, keyed by PUUID. */
public class InMemorySummonerPort implements SummonerPort {

    private final Map<String, Summoner> byPuuid = new HashMap<>();

    public InMemorySummonerPort put(String puuid, Summoner summoner) {
        byPuuid.put(puuid, summoner);
        return this;
    }

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.get(puuid);
    }
}
