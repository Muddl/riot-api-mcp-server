package com.muddl.riot.lol.clash.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClashServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryClashPort port = new InMemoryClashPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final ClashService service = new ClashService(port, resolver);

    @Test
    void getClashByPlayer_resolvesPlayer_thenReturnsRegistrations() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        ClashPlayer player =
                ClashPlayer.builder().teamId("team-1").role("CAPTAIN").build();
        port.put("faker-puuid", List.of(player));

        assertThat(service.getClashByPlayer(PLATFORM, "Faker#KR1")).containsExactly(player);
    }

    @Test
    void getClashByPlayer_returnsEmpty_whenNoRegistrations() {
        when(resolver.resolvePuuid("puuid-raw")).thenReturn("puuid-raw");

        assertThat(service.getClashByPlayer(PLATFORM, "puuid-raw")).isEmpty();
    }
}
