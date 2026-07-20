package com.muddl.riot.tft.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    private final InMemoryMatchPort port = new InMemoryMatchPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final MatchService service = new MatchService(port, resolver);

    @Test
    void getMatchIdsByPlayer_resolvesPlayer_thenReturnsIds() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn("puuid-1");
        port.putIds("puuid-1", List.of("NA1_1", "NA1_2"));

        assertThat(service.getMatchIdsByPlayer(REGION, "Player#NA1", 20, 0)).containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchById_delegatesToPort() {
        TftMatch match = TftMatch.builder().build();
        port.putMatch("NA1_1", match);

        assertThat(service.getMatchById(REGION, "NA1_1")).isSameAs(match);
    }
}
