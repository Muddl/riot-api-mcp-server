package com.muddl.riot.lol.champion.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.domain.ChampionRotation;

/** Outbound port for Riot Champion-V3 rotation data. Platform-routed. */
public interface ChampionPort {

    /** The current free-to-play champion rotation for a platform. */
    ChampionRotation getChampionRotation(RiotApiPlatformUri platform);
}
