package com.muddl.riot.lol.challenges.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;

/** Outbound port for Riot LoL-Challenges-V1 player data. Platform-routed. */
public interface ChallengesPort {

    /** A player's challenge standing (totals, category points, per-challenge progress). */
    ChallengesPlayerData getPlayerDataByPuuid(RiotApiPlatformUri platform, String puuid);
}
