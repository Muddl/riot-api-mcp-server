package com.muddl.riot.account.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.application.port.RiotAccountPort;
import com.muddl.riot.account.domain.RiotAccount;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlayerIdentityResolverTest {

    /** A port that counts Riot-ID lookups, so a cache hit is provable as "one call across two lookups". */
    private static final class CountingPort implements RiotAccountPort {
        private int riotIdLookups = 0;
        private final RiotAccount account;

        CountingPort(RiotAccount account) {
            this.account = account;
        }

        @Override
        public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
            riotIdLookups++;
            return account;
        }

        @Override
        public RiotAccount getAccountByPuuid(String puuid) {
            return account;
        }
    }

    private PlayerIdentityResolver resolver(RiotAccountPort port, MutableTicker ticker) {
        return new PlayerIdentityResolver(new RiotAccountService(port), ticker, Duration.ofMinutes(5), 10);
    }

    @Test
    void a_raw_puuid_is_returned_unchanged_with_no_riot_call() {
        CountingPort port = new CountingPort(null);
        PlayerIdentityResolver resolver = resolver(port, new MutableTicker());

        assertThat(resolver.resolvePuuid("abc-puuid-no-hash")).isEqualTo("abc-puuid-no-hash");
        assertThat(port.riotIdLookups).isZero();
    }

    @Test
    void a_riot_id_is_resolved_to_its_puuid() {
        RiotAccount account = RiotAccount.builder()
                .puuid("resolved-puuid")
                .gameName("Faker")
                .tagLine("KR1")
                .build();
        PlayerIdentityResolver resolver = resolver(new CountingPort(account), new MutableTicker());

        assertThat(resolver.resolvePuuid("Faker#KR1")).isEqualTo("resolved-puuid");
    }

    @Test
    void a_repeated_riot_id_lookup_is_served_from_cache() {
        RiotAccount account = RiotAccount.builder()
                .puuid("resolved-puuid")
                .gameName("Faker")
                .tagLine("KR1")
                .build();
        CountingPort port = new CountingPort(account);
        PlayerIdentityResolver resolver = resolver(port, new MutableTicker());

        resolver.resolvePuuid("Faker#KR1");
        resolver.resolvePuuid("Faker#KR1");

        assertThat(port.riotIdLookups).isEqualTo(1); // one Riot call across two lookups
    }

    @Test
    void a_cached_riot_id_is_re_fetched_after_the_ttl_expires() {
        RiotAccount account = RiotAccount.builder()
                .puuid("resolved-puuid")
                .gameName("Faker")
                .tagLine("KR1")
                .build();
        CountingPort port = new CountingPort(account);
        MutableTicker ticker = new MutableTicker();
        PlayerIdentityResolver resolver = resolver(port, ticker);

        resolver.resolvePuuid("Faker#KR1");
        ticker.advance(Duration.ofMinutes(6)); // past the 5-minute TTL
        resolver.resolvePuuid("Faker#KR1");

        assertThat(port.riotIdLookups).isEqualTo(2); // staleness bounded — mutable Riot IDs re-checked
    }

    @Test
    void a_blank_value_is_rejected_with_both_accepted_forms_named() {
        PlayerIdentityResolver resolver = resolver(new CountingPort(null), new MutableTicker());

        assertThatThrownBy(() -> resolver.resolvePuuid("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GameName#TAG")
                .hasMessageContaining("PUUID");
    }

    @Test
    void a_malformed_riot_id_is_rejected() {
        PlayerIdentityResolver resolver = resolver(new CountingPort(null), new MutableTicker());

        assertThatThrownBy(() -> resolver.resolvePuuid("Faker#")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolvePuuid("#KR1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolvePuuid("a#b#c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_unknown_riot_id_is_rejected_and_not_cached() {
        CountingPort port = new CountingPort(null); // service returns null → no such account
        PlayerIdentityResolver resolver = resolver(port, new MutableTicker());

        assertThatThrownBy(() -> resolver.resolvePuuid("Ghost#NA1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolvePuuid("Ghost#NA1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(port.riotIdLookups).isEqualTo(2); // a failed lookup is not cached
    }
}
