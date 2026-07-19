package com.muddl.riot.lol.champion.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import java.util.EnumMap;
import java.util.Map;

/** Hand-written in-memory {@link ChampionPort} for fast, HTTP-free service tests. */
public class InMemoryChampionPort implements ChampionPort {

    private final Map<RiotApiPlatformUri, ChampionRotation> byPlatform = new EnumMap<>(RiotApiPlatformUri.class);

    public InMemoryChampionPort put(RiotApiPlatformUri platform, ChampionRotation rotation) {
        byPlatform.put(platform, rotation);
        return this;
    }

    @Override
    public ChampionRotation getChampionRotation(RiotApiPlatformUri platform) {
        return byPlatform.get(platform);
    }
}
