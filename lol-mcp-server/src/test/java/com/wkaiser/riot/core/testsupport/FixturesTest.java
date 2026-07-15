package com.wkaiser.riot.core.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FixturesTest {

    @Test
    void read_returnsFixtureContents() {
        String json = Fixtures.read("summoner.json");

        assertThat(json).contains("\"puuid\"").contains("test-puuid-abc123");
    }

    @Test
    void read_failsFast_whenFixtureMissing() {
        assertThatThrownBy(() -> Fixtures.read("does-not-exist.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist.json");
    }
}
