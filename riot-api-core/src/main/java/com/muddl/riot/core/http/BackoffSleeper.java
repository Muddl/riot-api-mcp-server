package com.muddl.riot.core.http;

import java.time.Duration;

/**
 * Pauses between retry attempts. Injected so tests never sleep for real — the whole retry path
 * stays deterministic and fast. Production uses {@link #realTime()}; tests pass a fake that records
 * the requested durations without sleeping.
 */
@FunctionalInterface
public interface BackoffSleeper {

    void sleep(Duration duration);

    /** Real implementation backed by {@link Thread#sleep}, preserving the interrupt flag. */
    static BackoffSleeper realTime() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while backing off before a Riot API retry", e);
            }
        };
    }
}
