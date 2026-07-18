package com.muddl.riot.lol.status.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import org.junit.jupiter.api.Test;

/** Non-player-keyed context: the service takes no resolver (ADR-0014). */
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
