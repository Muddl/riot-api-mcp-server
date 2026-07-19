package com.muddl.riot.lol.champion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Non-player-keyed context: the service takes no resolver (ADR-0014). */
class ChampionServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryChampionPort port = new InMemoryChampionPort();
    private final ChampionService service = new ChampionService(port);

    @Test
    void getChampionRotation_delegatesToPort() {
        ChampionRotation expected = ChampionRotation.builder()
                .freeChampionIds(List.of(1, 2, 3))
                .maxNewPlayerLevel(10)
                .build();
        port.put(PLATFORM, expected);

        assertThat(service.getChampionRotation(PLATFORM)).isSameAs(expected);
    }
}
