package com.muddl.riot.account.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.account.adapter.out.riot.RiotAccountRiotAdapter;
import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.application.port.RiotAccountPort;
import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.config.RiotApiAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RiotAccountAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(RiotApiAutoConfiguration.class, RiotAccountAutoConfiguration.class))
            .withPropertyValues("riot.api-key=test-key-123");

    @Test
    void registers_account_service_and_port() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RiotAccountService.class);
            assertThat(context).hasSingleBean(RiotAccountPort.class);
            assertThat(context.getBean(RiotAccountPort.class)).isInstanceOf(RiotAccountRiotAdapter.class);
        });
    }

    @Test
    void registers_the_player_identity_resolver() {
        runner.run(context -> assertThat(context).hasSingleBean(PlayerIdentityResolver.class));
    }

    @Test
    void a_consumer_supplied_port_wins_over_the_default_adapter() {
        runner.withBean(RiotAccountPort.class, () -> new StubAccountPort()).run(context -> {
            assertThat(context).hasSingleBean(RiotAccountPort.class);
            assertThat(context.getBean(RiotAccountPort.class)).isInstanceOf(StubAccountPort.class);
        });
    }

    /** Minimal stand-in proving @ConditionalOnMissingBean lets a consumer override the adapter. */
    private static final class StubAccountPort implements RiotAccountPort {
        @Override
        public com.muddl.riot.account.domain.RiotAccount getAccountByRiotId(String gameName, String tagLine) {
            return null;
        }

        @Override
        public com.muddl.riot.account.domain.RiotAccount getAccountByPuuid(String puuid) {
            return null;
        }
    }
}
