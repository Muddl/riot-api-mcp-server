package com.wkaiser.riotapimcpserver.account.application;

import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiotAccountServiceTest {

    @Mock
    private RiotAccountPort accountPort;

    @InjectMocks
    private RiotAccountService accountService;

    @Test
    void getAccountByRiotId_delegatesToPort() {
        RiotAccount expected = RiotAccount.builder().puuid("p").gameName("Name").tagLine("NA1").build();
        when(accountPort.getAccountByRiotId("Name", "NA1")).thenReturn(expected);

        RiotAccount result = accountService.getAccountByRiotId("Name", "NA1");

        assertThat(result).isSameAs(expected);
        verify(accountPort).getAccountByRiotId("Name", "NA1");
    }

    @Test
    void getAccountByPuuid_delegatesToPort() {
        RiotAccount expected = RiotAccount.builder().puuid("p").build();
        when(accountPort.getAccountByPuuid("p")).thenReturn(expected);

        RiotAccount result = accountService.getAccountByPuuid("p");

        assertThat(result).isSameAs(expected);
        verify(accountPort).getAccountByPuuid("p");
    }
}
