package com.muddl.riot.lol.status.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.status.application.port.StatusPort;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot LoL-Status-V4 API adapter. Platform status is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotStatusAdapter implements StatusPort {

    private final RiotApiClient riotApiClient;

    @Override
    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/status/v4/platform-data")
                .retrieve()
                .body(PlatformStatus.class);
    }
}
