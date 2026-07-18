package com.muddl.riot.lol.status.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.domain.PlatformStatus;

/** Outbound port for Riot LoL-Status-V4 platform status. Platform-routed. */
public interface StatusPort {

    /** The platform's current maintenances and incidents. */
    PlatformStatus getPlatformStatus(RiotApiPlatformUri platform);
}
