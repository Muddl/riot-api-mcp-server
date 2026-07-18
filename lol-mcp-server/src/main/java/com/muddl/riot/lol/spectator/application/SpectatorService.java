package com.muddl.riot.lol.spectator.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.spectator.application.port.SpectatorPort;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends live-game data. Resolves the caller's {@code player}
 * reference via {@link PlayerIdentityResolver}, then delegates to the outbound {@link SpectatorPort};
 * holds no HTTP concerns. Returns {@code null} when the player is not currently in a game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectatorService {

    private final SpectatorPort spectatorPort;
    private final PlayerIdentityResolver identityResolver;

    public CurrentGameInfo getCurrentGameByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        return getCurrentGameInfo(platform, puuid);
    }

    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching current game info for PUUID: {} on platform: {}", puuid, platform);
        return spectatorPort.getCurrentGameInfo(platform, puuid);
    }
}
