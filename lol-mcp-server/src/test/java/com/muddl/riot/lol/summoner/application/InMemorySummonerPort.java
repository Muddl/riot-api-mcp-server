package com.muddl.riot.lol.summoner.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.application.port.SummonerPort;
import com.muddl.riot.lol.summoner.domain.Summoner;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SummonerPort} for fast, HTTP-free service tests. */
public class InMemorySummonerPort implements SummonerPort {

    private final Map<String, Summoner> byPuuid = new HashMap<>();

    public InMemorySummonerPort putByPuuid(RiotApiPlatformUri platform, String puuid, Summoner summoner) {
        byPuuid.put(key(platform, puuid), summoner);
        return this;
    }

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.get(key(platform, puuid));
    }

    private static String key(RiotApiPlatformUri platform, String value) {
        return platform.name() + "|" + value;
    }
}
