package com.muddl.riot.lol.champion.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for the free-to-play champion rotation. Non-player-keyed: depends only on its
 * own {@link ChampionPort}, never on {@code PlayerIdentityResolver} (ADR-0014).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionService {

    private final ChampionPort championPort;

    public ChampionRotation getChampionRotation(RiotApiPlatformUri platform) {
        log.info("Fetching champion rotation on platform: {}", platform);
        return championPort.getChampionRotation(platform);
    }
}
