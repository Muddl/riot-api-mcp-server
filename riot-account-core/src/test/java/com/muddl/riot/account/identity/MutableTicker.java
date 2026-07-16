package com.muddl.riot.account.identity;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;

/**
 * A Caffeine {@link Ticker} whose reading is advanced by hand, so TTL behaviour is tested
 * deterministically with no real waiting. Caffeine reads nanoseconds; {@link #advance(Duration)}
 * moves the clock forward by a {@link Duration} for readability at the call site.
 */
final class MutableTicker implements Ticker {

    private long nanos = 0L;

    void advance(Duration amount) {
        nanos += amount.toNanos();
    }

    @Override
    public long read() {
        return nanos;
    }
}
