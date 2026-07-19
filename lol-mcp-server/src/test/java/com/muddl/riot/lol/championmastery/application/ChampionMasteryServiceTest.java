package com.muddl.riot.lol.championmastery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMasteryServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryChampionMasteryPort port = new InMemoryChampionMasteryPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final ChampionMasteryService service = new ChampionMasteryService(port, resolver);

    @Test
    void getMasteryByPlayer_resolvesPlayer_thenReturnsAll() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        ChampionMastery m1 =
                ChampionMastery.builder().championId(157).championPoints(999).build();
        ChampionMastery m2 =
                ChampionMastery.builder().championId(64).championPoints(500).build();
        port.put("faker-puuid", List.of(m1, m2));

        assertThat(service.getMasteryByPlayer(PLATFORM, "Faker#KR1", null)).containsExactly(m1, m2);
    }

    @Test
    void getMasteryByPlayer_honoursCount() {
        when(resolver.resolvePuuid("puuid-raw")).thenReturn("puuid-raw");
        ChampionMastery m1 = ChampionMastery.builder().championId(157).build();
        ChampionMastery m2 = ChampionMastery.builder().championId(64).build();
        port.put("puuid-raw", List.of(m1, m2));

        assertThat(service.getMasteryByPlayer(PLATFORM, "puuid-raw", 1)).containsExactly(m1);
    }
}
