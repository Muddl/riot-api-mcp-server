package com.muddl.riot.lol.league.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link LeaguePort} for fast, HTTP-free service tests. */
public class InMemoryLeaguePort implements LeaguePort {

    private final Map<String, List<LeagueEntry>> entriesByPuuid = new HashMap<>();
    private final Map<String, LeagueList> apexByKey = new HashMap<>();

    public InMemoryLeaguePort putEntries(String puuid, List<LeagueEntry> entries) {
        entriesByPuuid.put(puuid, entries);
        return this;
    }

    public InMemoryLeaguePort putApex(ApexTier tier, String queue, LeagueList league) {
        apexByKey.put(tier + "|" + queue, league);
        return this;
    }

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        return entriesByPuuid.getOrDefault(puuid, List.of());
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue) {
        return apexByKey.get(tier + "|" + queue);
    }
}
