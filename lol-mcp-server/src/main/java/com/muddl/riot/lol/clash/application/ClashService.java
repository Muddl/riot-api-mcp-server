package com.muddl.riot.lol.clash.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.port.ClashPort;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot Clash-V1 player registrations. Player-keyed: resolves {@code player}
 * to a PUUID via {@link PlayerIdentityResolver} before calling the port (the {@code league} shape).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClashService {

    private final ClashPort clashPort;
    private final PlayerIdentityResolver identityResolver;

    public List<ClashPlayer> getClashByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching clash registrations on platform: {}", platform);
        return clashPort.getPlayersByPuuid(platform, puuid);
    }
}
