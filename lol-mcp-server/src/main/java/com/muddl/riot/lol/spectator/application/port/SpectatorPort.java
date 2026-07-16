package com.muddl.riot.lol.spectator.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.FeaturedGames;

/** Outbound port for retrieving League of Legends live-game (spectator) data. */
public interface SpectatorPort {

    /** Returns the current game, or {@code null} if the summoner is not in a game. */
    CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId);

    FeaturedGames getFeaturedGames(RiotApiPlatformUri platform);
}
