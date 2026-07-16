package com.muddl.riot.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;

class RiotAccountServiceTest {

    private final InMemoryRiotAccountPort accountPort = new InMemoryRiotAccountPort();
    private final RiotAccountService accountService = new RiotAccountService(accountPort);

    @Test
    void getAccountByRiotId_returnsStoredAccount() {
        RiotAccount stored =
                RiotAccount.builder().puuid("p").gameName("Name").tagLine("NA1").build();
        accountPort.add(stored);

        assertThat(accountService.getAccountByRiotId("Name", "NA1")).isSameAs(stored);
    }

    @Test
    void getAccountByPuuid_returnsStoredAccount() {
        RiotAccount stored =
                RiotAccount.builder().puuid("p").gameName("Name").tagLine("NA1").build();
        accountPort.add(stored);

        assertThat(accountService.getAccountByPuuid("p")).isSameAs(stored);
    }

    @Test
    void getAccountByRiotId_returnsNull_whenUnknown() {
        assertThat(accountService.getAccountByRiotId("Ghost", "NA1")).isNull();
    }
}
