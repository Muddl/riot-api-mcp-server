package com.muddl.riot.lol.championmastery.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link ChampionMasteryPort} for fast, HTTP-free service tests. */
public class InMemoryChampionMasteryPort implements ChampionMasteryPort {

    private final Map<String, List<ChampionMastery>> byPuuid = new HashMap<>();

    public InMemoryChampionMasteryPort put(String puuid, List<ChampionMastery> masteries) {
        byPuuid.put(puuid, masteries);
        return this;
    }

    @Override
    public List<ChampionMastery> getMasteryByPuuid(RiotApiPlatformUri platform, String puuid, Integer count) {
        List<ChampionMastery> all = byPuuid.getOrDefault(puuid, List.of());
        return count == null ? all : all.stream().limit(count).toList();
    }
}
