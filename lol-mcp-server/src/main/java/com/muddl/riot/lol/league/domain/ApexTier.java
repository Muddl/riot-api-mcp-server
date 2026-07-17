package com.muddl.riot.lol.league.domain;

/**
 * The three apex tiers, which have dedicated League-V4 endpoints
 * ({@code challengerleagues}/{@code grandmasterleagues}/{@code masterleagues}).
 */
public enum ApexTier {
    CHALLENGER,
    GRANDMASTER,
    MASTER;

    /** The Riot path segment for this tier, e.g. {@code challengerleagues}. */
    public String leaguePath() {
        return name().toLowerCase() + "leagues";
    }
}
