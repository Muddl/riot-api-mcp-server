package com.muddl.riot.tft.status.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import org.junit.jupiter.api.Test;

class StatusServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryStatusPort port = new InMemoryStatusPort();
    private final StatusService service = new StatusService(port);

    @Test
    void getPlatformStatus_delegatesToPort() {
        PlatformStatus expected =
                PlatformStatus.builder().id("NA1").name("North America").build();
        port.put(PLATFORM, expected);

        assertThat(service.getPlatformStatus(PLATFORM)).isSameAs(expected);
    }
}
