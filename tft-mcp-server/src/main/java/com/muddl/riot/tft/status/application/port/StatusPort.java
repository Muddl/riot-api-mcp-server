package com.muddl.riot.tft.status.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.domain.PlatformStatus;

/** Outbound port for Riot TFT-Status-V1 platform status. Platform-routed. */
public interface StatusPort {
    PlatformStatus getPlatformStatus(RiotApiPlatformUri platform);
}
