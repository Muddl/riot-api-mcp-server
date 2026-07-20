package com.muddl.riot.tft.league.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;

/** Outbound port for Riot TFT-League-V1 ranked data. League endpoints are platform-routed. */
public interface LeaguePort {

    /** A player's ranked entries. Empty when the player is unranked. */
    List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid);

    /** The apex league (challenger/grandmaster/master). */
    LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier);

    /** One page of ranked entries for a tier + division. */
    List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page);

    /** A league by its UUID. */
    LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId);

    /** The top of a rated (Hyper Roll) ladder for a queue. */
    List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue);
}
