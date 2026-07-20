package com.muddl.riot.tft.account.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;

class RiotAccountToolTest {

    private final RiotAccountService service = mock(RiotAccountService.class);
    private final RiotAccountTool tool = new RiotAccountTool(service);

    @Test
    void riotId_isSplitOnHash_andRoutedToByRiotId() {
        RiotAccount account = RiotAccount.builder()
                .puuid("puuid-1")
                .gameName("Player")
                .tagLine("NA1")
                .build();
        when(service.getAccountByRiotId("Player", "NA1")).thenReturn(account);

        assertThat(tool.getAccountByPlayer("Player#NA1")).isSameAs(account);
    }

    @Test
    void rawPuuid_isRoutedToByPuuid() {
        RiotAccount account = RiotAccount.builder().puuid("puuid-raw").build();
        when(service.getAccountByPuuid("puuid-raw")).thenReturn(account);

        assertThat(tool.getAccountByPlayer("puuid-raw")).isSameAs(account);
    }

    @Test
    void blankOrMalformed_throwsIllegalArgument() {
        assertThatThrownBy(() -> tool.getAccountByPlayer("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.getAccountByPlayer("no-hash-and-not-blank#"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
