package com.muddl.riot.lol.league.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;

/** Outbound port for Riot League-V4 ranked data. League endpoints are platform-routed. */
public interface LeaguePort {

    /** A player's ranked entries, one per ranked queue. Empty when the player is unranked. */
    List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid);

    /** The apex league (challenger/grandmaster/master) for a ranked queue. */
    LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue);
}
