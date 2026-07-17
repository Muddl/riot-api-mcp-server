package com.muddl.riot.lol.account.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiotAccountToolTest {

    @Mock
    private RiotAccountService mockAccountService;

    @InjectMocks
    private RiotAccountTool accountTool;

    @Test
    void riotIdForm_routesToGetByRiotId() {
        RiotAccount account = RiotAccount.builder()
                .puuid("p")
                .gameName("Faker")
                .tagLine("KR1")
                .build();
        when(mockAccountService.getAccountByRiotId("Faker", "KR1")).thenReturn(account);

        assertThat(accountTool.getAccountByPlayer("Faker#KR1")).isSameAs(account);
        verify(mockAccountService).getAccountByRiotId("Faker", "KR1");
        verifyNoMoreInteractions(mockAccountService);
    }

    @Test
    void puuidForm_routesToGetByPuuid() {
        RiotAccount account = RiotAccount.builder().puuid("raw-puuid").build();
        when(mockAccountService.getAccountByPuuid("raw-puuid")).thenReturn(account);

        assertThat(accountTool.getAccountByPlayer("raw-puuid")).isSameAs(account);
        verify(mockAccountService).getAccountByPuuid("raw-puuid");
        verifyNoMoreInteractions(mockAccountService);
    }

    @Test
    void blankPlayer_throwsWithBothFormsNamed() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountTool.getAccountByPlayer("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GameName#TAG")
                .hasMessageContaining("PUUID");
    }

    @Test
    void malformedRiotId_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountTool.getAccountByPlayer("Faker#"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountTool.getAccountByPlayer("#KR1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
