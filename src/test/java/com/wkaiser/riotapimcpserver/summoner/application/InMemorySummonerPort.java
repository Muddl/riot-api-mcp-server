package com.wkaiser.riotapimcpserver.summoner.application;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SummonerPort} for fast, HTTP-free service tests. */
public class InMemorySummonerPort implements SummonerPort {

    private final Map<String, Summoner> byName = new HashMap<>();
    private final Map<String, Summoner> byPuuid = new HashMap<>();
    private final Map<String, Summoner> byId = new HashMap<>();

    public InMemorySummonerPort putByName(RiotApiPlatformUri platform, String name, Summoner summoner) {
        byName.put(key(platform, name), summoner);
        return this;
    }

    public InMemorySummonerPort putByPuuid(RiotApiPlatformUri platform, String puuid, Summoner summoner) {
        byPuuid.put(key(platform, puuid), summoner);
        return this;
    }

    public InMemorySummonerPort putById(RiotApiPlatformUri platform, String id, Summoner summoner) {
        byId.put(key(platform, id), summoner);
        return this;
    }

    @Override
    public Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName) {
        return byName.get(key(platform, summonerName));
    }

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.get(key(platform, puuid));
    }

    @Override
    public Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId) {
        return byId.get(key(platform, summonerId));
    }

    private static String key(RiotApiPlatformUri platform, String value) {
        return platform.name() + "|" + value;
    }
}
