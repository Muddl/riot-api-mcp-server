package com.muddl.riot.core.http;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Retries a request on HTTP 429, waiting the server's {@code Retry-After} where present and a
 * configured default otherwise, up to a bounded number of attempts. Riot documents {@code
 * Retry-After} on 429, so this reacts to a real signal rather than guessing — it is deliberately
 * not a proactive rate limiter (see the roadmap's deferred items and ADR-0007).
 *
 * <p>It reads only the status code and the {@code Retry-After} header — never the response body —
 * so it does not consume the stream {@code RiotApiClient}'s status handler reads afterward. When
 * attempts are exhausted the last 429 response is returned unchanged and the status handler maps it
 * to {@code RiotApiException.forStatus(429, ...)} — the clear exhaustion error.
 */
class RetryOn429Interceptor implements ClientHttpRequestInterceptor {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final String RETRY_AFTER = "Retry-After";

    private final int maxRetries;
    private final Duration defaultBackoff;
    private final BackoffSleeper sleeper;

    RetryOn429Interceptor(int maxRetries, Duration defaultBackoff, BackoffSleeper sleeper) {
        this.maxRetries = maxRetries;
        this.defaultBackoff = defaultBackoff;
        this.sleeper = sleeper;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        int attempt = 0;
        while (response.getStatusCode().value() == TOO_MANY_REQUESTS && attempt < maxRetries) {
            Duration wait = retryAfter(response).orElse(defaultBackoff);
            response.close();
            sleeper.sleep(wait);
            attempt++;
            response = execution.execute(request, body);
        }
        return response;
    }

    private Optional<Duration> retryAfter(ClientHttpResponse response) {
        String header = response.getHeaders().getFirst(RETRY_AFTER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(header.trim())));
        } catch (NumberFormatException e) {
            // Riot sends Retry-After as delta-seconds; anything else falls back to the default.
            return Optional.empty();
        }
    }
}
