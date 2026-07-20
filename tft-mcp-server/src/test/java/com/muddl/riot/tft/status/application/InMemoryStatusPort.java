package com.muddl.riot.tft.status.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import java.util.EnumMap;
import java.util.Map;

/** Hand-written in-memory {@link StatusPort} for fast, HTTP-free service tests. */
public class InMemoryStatusPort implements StatusPort {

    private final Map<RiotApiPlatformUri, PlatformStatus> byPlatform = new EnumMap<>(RiotApiPlatformUri.class);

    public InMemoryStatusPort put(RiotApiPlatformUri platform, PlatformStatus status) {
        byPlatform.put(platform, status);
        return this;
    }

    @Override
    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        return byPlatform.get(platform);
    }
}
