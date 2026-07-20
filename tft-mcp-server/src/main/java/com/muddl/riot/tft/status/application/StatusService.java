package com.muddl.riot.tft.status.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Application service for TFT platform status. Non-player-keyed (ADR-0014). */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusPort statusPort;

    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        log.info("Fetching TFT platform status on platform: {}", platform);
        return statusPort.getPlatformStatus(platform);
    }
}
