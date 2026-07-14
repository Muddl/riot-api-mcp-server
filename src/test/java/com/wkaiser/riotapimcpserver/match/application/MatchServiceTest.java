package com.wkaiser.riotapimcpserver.match.application;

import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    @Mock
    private MatchPort matchPort;

    @InjectMocks
    private MatchService matchService;

    @Test
    void getMatchIdsByPuuid_delegatesToPort() {
        List<String> ids = List.of("NA1_1", "NA1_2");
        when(matchPort.getMatchIdsByPuuid(REGION, "p", 20, 0, null)).thenReturn(ids);

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 20, 0, null)).isEqualTo(ids);
        verify(matchPort).getMatchIdsByPuuid(REGION, "p", 20, 0, null);
    }

    @Test
    void getMatchById_delegatesToPort() {
        Match expected = Match.builder().build();
        when(matchPort.getMatchById(REGION, "NA1_1")).thenReturn(expected);

        assertThat(matchService.getMatchById(REGION, "NA1_1")).isSameAs(expected);
        verify(matchPort).getMatchById(REGION, "NA1_1");
    }
}
