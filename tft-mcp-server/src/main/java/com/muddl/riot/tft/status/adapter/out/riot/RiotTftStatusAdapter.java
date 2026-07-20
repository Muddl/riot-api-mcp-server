package com.muddl.riot.tft.status.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot TFT-Status-V1 API adapter. Platform status is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftStatusAdapter implements StatusPort {

    private final RiotApiClient riotApiClient;

    @Override
    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/status/v1/platform-data")
                .retrieve()
                .body(PlatformStatus.class);
    }
}
