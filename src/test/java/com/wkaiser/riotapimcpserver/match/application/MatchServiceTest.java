package com.wkaiser.riotapimcpserver.match.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    private final InMemoryMatchPort matchPort = new InMemoryMatchPort();
    private final MatchService matchService = new MatchService(matchPort);

    @Test
    void getMatchIdsByPuuid_returnsStoredIds() {
        matchPort.putMatchIds("p", List.of("NA1_1", "NA1_2"));

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 20, 0, null)).containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchIdsByPuuid_honoursCountLimit() {
        matchPort.putMatchIds("p", List.of("NA1_1", "NA1_2", "NA1_3"));

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 2, 0, null)).containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchById_returnsStoredMatch() {
        Match expected = Match.builder().build();
        matchPort.putMatch("NA1_1", expected);

        assertThat(matchService.getMatchById(REGION, "NA1_1")).isSameAs(expected);
    }

    @Test
    void getMatchIdsByPuuid_returnsEmpty_whenUnknownPuuid() {
        assertThat(matchService.getMatchIdsByPuuid(REGION, "unknown", 20, 0, null))
                .isEmpty();
    }
}
