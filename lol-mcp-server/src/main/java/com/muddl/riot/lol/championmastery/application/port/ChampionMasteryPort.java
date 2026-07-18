package com.muddl.riot.lol.championmastery.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;

/** Outbound port for Riot Champion-Mastery-V4 data. Platform-routed. */
public interface ChampionMasteryPort {

    /**
     * A player's champion masteries sorted by points (Riot's default). When {@code count} is
     * non-null, only the top {@code count} are returned (Riot's {@code /top} endpoint).
     */
    List<ChampionMastery> getMasteryByPuuid(RiotApiPlatformUri platform, String puuid, Integer count);
}
