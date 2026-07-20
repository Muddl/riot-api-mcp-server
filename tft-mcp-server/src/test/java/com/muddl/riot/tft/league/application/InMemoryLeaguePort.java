package com.muddl.riot.tft.league.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link LeaguePort}. */
public class InMemoryLeaguePort implements LeaguePort {

    private final Map<String, List<LeagueEntry>> entriesByPuuid = new HashMap<>();
    private final Map<ApexTier, LeagueList> apexByTier = new HashMap<>();
    private final Map<String, List<LeagueEntry>> entriesByTierDivisionPage = new HashMap<>();
    private final Map<String, LeagueList> leagueById = new HashMap<>();
    private final Map<String, List<RatedLadderEntry>> ladderByQueue = new HashMap<>();

    public InMemoryLeaguePort putEntries(String puuid, List<LeagueEntry> entries) {
        entriesByPuuid.put(puuid, entries);
        return this;
    }

    public InMemoryLeaguePort putApex(ApexTier tier, LeagueList list) {
        apexByTier.put(tier, list);
        return this;
    }

    public InMemoryLeaguePort putEntriesByTier(String tier, String division, int page, List<LeagueEntry> entries) {
        entriesByTierDivisionPage.put(tier + "|" + division + "|" + page, entries);
        return this;
    }

    public InMemoryLeaguePort putLeague(String leagueId, LeagueList list) {
        leagueById.put(leagueId, list);
        return this;
    }

    public InMemoryLeaguePort putLadder(String queue, List<RatedLadderEntry> ladder) {
        ladderByQueue.put(queue, ladder);
        return this;
    }

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        return entriesByPuuid.getOrDefault(puuid, List.of());
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier) {
        return apexByTier.get(tier);
    }

    @Override
    public List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page) {
        return entriesByTierDivisionPage.getOrDefault(tier + "|" + division + "|" + page, List.of());
    }

    @Override
    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        return leagueById.get(leagueId);
    }

    @Override
    public List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue) {
        return ladderByQueue.getOrDefault(queue, List.of());
    }
}
