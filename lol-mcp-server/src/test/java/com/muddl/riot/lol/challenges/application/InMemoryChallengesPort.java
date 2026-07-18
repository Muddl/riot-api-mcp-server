package com.muddl.riot.lol.challenges.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link ChallengesPort} for fast, HTTP-free service tests. */
public class InMemoryChallengesPort implements ChallengesPort {

    private final Map<String, ChallengesPlayerData> byPuuid = new HashMap<>();

    public InMemoryChallengesPort put(String puuid, ChallengesPlayerData data) {
        byPuuid.put(puuid, data);
        return this;
    }

    @Override
    public ChallengesPlayerData getPlayerDataByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.get(puuid);
    }
}
