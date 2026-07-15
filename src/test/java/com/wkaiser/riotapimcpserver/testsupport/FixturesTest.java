package com.wkaiser.riotapimcpserver.testsupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixturesTest {

    @Test
    void read_returnsFixtureContents() {
        String json = Fixtures.read("account.json");

        assertThat(json).contains("\"puuid\"").contains("test-puuid-abc123");
    }

    @Test
    void read_failsFast_whenFixtureMissing() {
        assertThatThrownBy(() -> Fixtures.read("does-not-exist.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist.json");
    }
}
