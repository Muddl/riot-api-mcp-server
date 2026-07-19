package com.muddl.riot.lol.status.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.application.port.StatusPort;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for LoL platform status. Non-player-keyed: depends only on its own
 * {@link StatusPort}, never on {@code PlayerIdentityResolver} (ADR-0014).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusPort statusPort;

    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        log.info("Fetching platform status on platform: {}", platform);
        return statusPort.getPlatformStatus(platform);
    }
}
