package com.muddl.riot.tft.league.domain;

/**
 * The three apex tiers, which have dedicated TFT-League-V1 endpoints
 * ({@code /tft/league/v1/challenger} etc.). Unlike League-V4, the TFT paths carry no
 * {@code leagues} suffix and no {@code by-queue} segment.
 */
public enum ApexTier {
    CHALLENGER,
    GRANDMASTER,
    MASTER;

    /** The Riot path segment for this tier, e.g. {@code challenger}. */
    public String leaguePath() {
        return name().toLowerCase();
    }
}
