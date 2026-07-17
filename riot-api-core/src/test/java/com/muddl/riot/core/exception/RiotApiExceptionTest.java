package com.muddl.riot.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiotApiExceptionTest {

    @Test
    void forStatus_403_explains_the_expiring_dev_key() {
        RiotApiException ex = RiotApiException.forStatus(403, "raw forbidden body");

        assertThat(ex.getStatusCode()).isEqualTo(403);
        assertThat(ex.getMessage())
                .isEqualTo("Your Riot API key is invalid or expired — development keys expire every 24 hours");
        assertThat(ex.getRawBody()).isEqualTo("raw forbidden body");
    }

    @Test
    void forStatus_404_says_not_found() {
        assertThat(RiotApiException.forStatus(404, "x").getMessage()).isEqualTo("The requested resource was not found");
    }

    @Test
    void forStatus_429_says_rate_limited() {
        assertThat(RiotApiException.forStatus(429, "x").getMessage()).isEqualTo("Rate limited by the Riot API");
    }

    @Test
    void forStatus_503_says_temporarily_unavailable() {
        assertThat(RiotApiException.forStatus(503, "x").getMessage())
                .isEqualTo("The Riot API is temporarily unavailable");
    }

    @Test
    void forStatus_unmapped_status_falls_back_to_the_code_and_keeps_the_body() {
        RiotApiException ex = RiotApiException.forStatus(418, "teapot");

        assertThat(ex.getMessage()).isEqualTo("Riot API returned HTTP 418");
        assertThat(ex.getRawBody()).isEqualTo("teapot");
    }
}
