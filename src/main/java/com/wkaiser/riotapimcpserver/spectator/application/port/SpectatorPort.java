package com.wkaiser.riotapimcpserver.spectator.application.port;

import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;

/** Outbound port for retrieving League of Legends live-game (spectator) data. */
public interface SpectatorPort {

    /** Returns the current game, or {@code null} if the summoner is not in a game. */
    CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId);

    FeaturedGames getFeaturedGames(RiotApiPlatformUri platform);
}
