# LoL Parity 1a — Plan B: Library Hardening (core retry/errors + identity resolver)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn both libraries into a real shared kernel — `riot-api-core` gains 429 retry (honouring `Retry-After`) and an actionable error taxonomy; `riot-account-core` gains a Caffeine-cached `PlayerIdentityResolver` — so that Plan C's LoL server and 1b's five contexts inherit them without touching either library.

**Architecture:** Phases 2–3 of the [1a spec](../specs/2026-07-15-lol-parity-foundation-design.md). No server production code. Retry lives in a `ClientHttpRequestInterceptor` (reads status + `Retry-After` only, never the body, so it does not consume the stream the existing `defaultStatusHandler` later reads). The error taxonomy moves the actionable message onto `RiotApiException` while preserving the raw body. Identity resolution lives in a new, deliberately-open `com.muddl.riot.account.identity` package, backed by a bounded, TTL-expiring **Caffeine** cache on an injected `Ticker` — the first stateful component in the codebase. The one ArchUnit rule this breaks (`only_analytics_and_the_account_tool_use_the_account_library`, in `lol-mcp-server`) is split, not widened: the account **domain** stays deny-by-default, identity resolution goes open — the only edit this plan makes outside the two libraries, and it is test-only.

**Tech Stack:** Gradle 9.6.1 (Groovy DSL, `buildSrc` convention plugin), Spring Boot 4.1.0, Spring AI 2.0.0, Java 21, JUnit 5, AssertJ, WireMock 3.9.2, Caffeine (version-managed by the Spring Boot BOM), ArchUnit 1.3.0, Spotless (palantir-java-format), JaCoCo.

## Global Constraints

- **The suite runs offline with no Riot API key.** WireMock for outbound HTTP; port fakes for services. Never add a test needing a live key or network.
- **No real sleeps, no real waiting.** The backoff clock (`BackoffSleeper`) and the cache time source (Caffeine's `Ticker`) are injected. Tests use recording/mutable fakes; the suite stays fast and deterministic.
- **No server production code.** Only `riot-api-core` and `riot-account-core` `src/main` change. The single `lol-mcp-server` edit (Task 4) is in `src/test` — an ArchUnit rule split — and adds no behaviour.
- **Green at every commit.** `./gradlew build` runs tests + ArchUnit + JaCoCo + `verifyRelease` + Spotless check.
- **Run Spotless before committing Java changes:** `./gradlew spotlessApply`.
- **Coordinates are `com.muddl`** (Plan A). Module list: `riot-api-core`, `riot-account-core`, `lol-mcp-server`; deps point one way: server → `riot-account-core` → `riot-api-core`.
- **Both libraries stay at version `0.1.0`** (set in Plan A). Do **not** bump. Entries accumulate under the existing `## [0.1.0] - unreleased` heading — that is the Plan A gate's design (`verifyRelease` matches the heading, not a date).
- **Do not change any `@McpTool` name or `@McpToolParam` description.** The contract sweep is Plan C. `McpToolInventoryTest` must stay green.
- **Hydrate before starting:** `docs/knowledge/README.md`, `docs/knowledge/roadmap.md`, `docs/knowledge/gotchas.md`, and ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0006.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `riot-api-core/src/main/java/.../exception/RiotApiException.java` | Modify — add `rawBody` + `forStatus` factory with the status→message table | 1 |
| `riot-api-core/src/test/java/.../exception/RiotApiExceptionTest.java` | Create — unit test the message mapping and raw-body preservation | 1 |
| `riot-api-core/src/main/java/.../http/RiotApiClient.java` | Modify — status handler uses `forStatus`; (Task 2) wire the interceptor + sleeper | 1, 2 |
| `riot-api-core/src/test/java/.../http/RiotApiClientTest.java` | Modify — retarget the error test to 403; (Task 2) add retry scenarios | 1, 2 |
| `riot-api-core/src/main/java/.../http/BackoffSleeper.java` | Create — injected sleep seam (real impl + test fakes) | 2 |
| `riot-api-core/src/main/java/.../http/RetryOn429Interceptor.java` | Create — the bounded 429 retry loop | 2 |
| `riot-api-core/src/main/java/.../config/RiotApiProperties.java` | Modify — `maxRetries`, `retryBackoff` | 2 |
| `riot-api-core/src/main/java/.../config/RiotApiAutoConfiguration.java` | Modify — `BackoffSleeper` bean; `riotApiClient` takes the sleeper | 2 |
| `riot-api-core/src/test/java/.../config/RiotApiAutoConfigurationTest.java` | Modify — assert the `BackoffSleeper` bean and the retry defaults | 2 |
| `riot-api-core/CHANGELOG.md` | Modify — error taxonomy (Changed), retry (Added) under `## [0.1.0]` | 1, 2 |
| `riot-account-core/build.gradle` | Modify — add the Caffeine dependency | 3 |
| `riot-account-core/src/main/java/.../identity/PlayerIdentityResolver.java` | Create — Riot ID / PUUID → PUUID, Caffeine-cached | 3 |
| `riot-account-core/src/test/java/.../identity/MutableTicker.java` | Create — hand-advanced Caffeine `Ticker` for deterministic TTL tests | 3 |
| `riot-account-core/src/test/java/.../identity/PlayerIdentityResolverTest.java` | Create — both forms, invalid form, cache hit, TTL expiry, unknown | 3 |
| `riot-account-core/src/main/java/.../config/RiotAccountAutoConfiguration.java` | Modify — register `PlayerIdentityResolver` | 3 |
| `riot-account-core/src/test/java/.../config/RiotAccountAutoConfigurationTest.java` | Modify — assert the resolver bean | 3 |
| `riot-account-core/CHANGELOG.md` | Modify — resolver (Added) under `## [0.1.0]` | 3 |
| `docs/knowledge/gotchas.md` | Modify — Riot-ID mutability + Caffeine size-eviction notes | 3 |
| `lol-mcp-server/src/test/java/.../architecture/HexagonalArchitectureTest.java` | Modify — split the account rule (domain confined, identity open) | 4 |
| `lol-mcp-server/src/test/java/.../architecture/ArchFixtureLegalResolverUser.java` | Create — positive control: a non-allowlisted context using the resolver is legal | 4 |
| `lol-mcp-server/src/test/java/.../architecture/HexagonalArchitectureNegativeControlTest.java` | Modify — new rule name; add the positive control | 4 |
| `docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md` | Create | 5 |
| `docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md` | Create | 5 |
| `docs/knowledge/README.md` | Modify — link ADR-0007, ADR-0008 | 5 |

---

### Task 1: Actionable error taxonomy on `RiotApiException`

`RiotApiClient` throws `RiotApiException("Riot API error: " + rawBody, status)` and Spring AI hands that message to the model verbatim (`RiotApiClient.java:38-42`). The intended consumer is a third party installing against their own key, so the message must be actionable. This task moves the message to a status-derived table and keeps the raw body reachable on the exception. It goes **before** retry (Task 2), because retry's exhaustion path asserts the mapped 429 message.

**Files:**
- Modify: `riot-api-core/src/main/java/com/muddl/riot/core/exception/RiotApiException.java`
- Create: `riot-api-core/src/test/java/com/muddl/riot/core/exception/RiotApiExceptionTest.java`
- Modify: `riot-api-core/src/main/java/com/muddl/riot/core/http/RiotApiClient.java:38-42`
- Modify: `riot-api-core/src/test/java/com/muddl/riot/core/http/RiotApiClientTest.java`
- Modify: `riot-api-core/CHANGELOG.md`

**Interfaces:**
- Consumes: nothing from earlier tasks (first task).
- Produces:
  - `RiotApiException.forStatus(int statusCode, String rawBody) -> RiotApiException` — sets `getMessage()` from the status table, `getStatusCode()`, and `getRawBody()`.
  - `RiotApiException.getRawBody() -> String` (may be `null`).
  - Existing constructors `RiotApiException(String, int)` and `RiotApiException(String, Throwable, int)` stay (both leave `rawBody == null`). Task 2 relies on `forStatus`.

- [ ] **Step 1: Write the failing unit test for the message table**

Create `riot-api-core/src/test/java/com/muddl/riot/core/exception/RiotApiExceptionTest.java`:

```java
package com.muddl.riot.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiotApiExceptionTest {

    @Test
    void forStatus_403_explains_the_expiring_dev_key() {
        RiotApiException ex = RiotApiException.forStatus(403, "raw forbidden body");

        assertThat(ex.getStatusCode()).isEqualTo(403);
        assertThat(ex.getMessage()).isEqualTo("Your Riot API key is invalid or expired — development keys expire every 24 hours");
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
        assertThat(RiotApiException.forStatus(503, "x").getMessage()).isEqualTo("The Riot API is temporarily unavailable");
    }

    @Test
    void forStatus_unmapped_status_falls_back_to_the_code_and_keeps_the_body() {
        RiotApiException ex = RiotApiException.forStatus(418, "teapot");

        assertThat(ex.getMessage()).isEqualTo("Riot API returned HTTP 418");
        assertThat(ex.getRawBody()).isEqualTo("teapot");
    }
}
```

- [ ] **Step 2: Run it — must fail to compile (no `forStatus`, no `getRawBody`)**

```bash
./gradlew :riot-api-core:test --tests '*RiotApiExceptionTest*'
```

Expected: FAIL — `cannot find symbol: method forStatus` / `getRawBody`.

- [ ] **Step 3: Implement the taxonomy on `RiotApiException`**

Replace `riot-api-core/src/main/java/com/muddl/riot/core/exception/RiotApiException.java` with:

```java
package com.muddl.riot.core.exception;

import lombok.Getter;

/**
 * Thrown for any non-2xx Riot API response. Carries the HTTP status code and the raw response
 * body, but its {@code message} is an actionable, human-readable explanation derived from the
 * status — not the raw body. The intended consumer is a third party installing against their own
 * key, so the message is what they (and the model) act on; the body remains reachable via {@link
 * #getRawBody()} for diagnostics. See ADR-0007.
 */
@Getter
public class RiotApiException extends RuntimeException {

    private final int statusCode;

    /** The raw Riot response body, preserved for diagnostics. May be {@code null}. */
    private final String rawBody;

    public RiotApiException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public RiotApiException(String message, int statusCode, String rawBody) {
        super(message);
        this.statusCode = statusCode;
        this.rawBody = rawBody;
    }

    public RiotApiException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
        this.rawBody = null;
    }

    /**
     * Builds an exception whose message is the actionable explanation for {@code statusCode},
     * keeping {@code rawBody} for diagnostics. This is the single place the status→message table
     * lives, so the wording is consistent wherever an error surfaces.
     */
    public static RiotApiException forStatus(int statusCode, String rawBody) {
        return new RiotApiException(messageFor(statusCode), statusCode, rawBody);
    }

    private static String messageFor(int statusCode) {
        return switch (statusCode) {
            case 403 -> "Your Riot API key is invalid or expired — development keys expire every 24 hours";
            case 404 -> "The requested resource was not found";
            case 429 -> "Rate limited by the Riot API";
            case 503 -> "The Riot API is temporarily unavailable";
            default -> "Riot API returned HTTP " + statusCode;
        };
    }
}
```

- [ ] **Step 4: Run the unit test — must pass**

```bash
./gradlew :riot-api-core:test --tests '*RiotApiExceptionTest*'
```

Expected: PASS.

- [ ] **Step 5: Wire the status handler to `forStatus`**

In `riot-api-core/src/main/java/com/muddl/riot/core/http/RiotApiClient.java`, replace the `defaultStatusHandler` lambda (lines 38-42) so the raw body becomes `rawBody` and the message comes from the table:

```java
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw RiotApiException.forStatus(response.getStatusCode().value(), body);
                })
```

`StandardCharsets` is already imported. No other change in this file for Task 1.

- [ ] **Step 6: Update the existing client error test**

`RiotApiClientTest.maps_error_response_to_RiotApiException_with_status` currently stubs `429` and asserts the message contains the raw body `"rate limited"`. After this task the message is the table entry, and after Task 2 a `429` would trigger retries — so retarget it to `403`, which never retries and exercises the new message + raw-body split. In `riot-api-core/src/test/java/com/muddl/riot/core/http/RiotApiClientTest.java`, replace the `maps_error_response_to_RiotApiException_with_status` method with:

```java
    @Test
    void maps_error_response_to_RiotApiException_with_actionable_message_and_raw_body() {
        stubFor(get(urlEqualTo("/boom")).willReturn(aResponse().withStatus(403).withBody("forbidden raw body")));

        assertThatThrownBy(() -> riotApiClient
                        .platform(RiotApiPlatformUri.NA1)
                        .get()
                        .uri("/boom")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RiotApiException.class)
                .hasMessage("Your Riot API key is invalid or expired — development keys expire every 24 hours")
                .extracting(e -> ((RiotApiException) e).getRawBody())
                .isEqualTo("forbidden raw body");
    }
```

- [ ] **Step 7: Run the whole module — everything green**

```bash
./gradlew spotlessApply && ./gradlew :riot-api-core:build
```

Expected: `BUILD SUCCESSFUL`. The `sends_api_key_header_and_parses_body_on_success` test is untouched and still passes.

- [ ] **Step 8: Log it in the changelog**

In `riot-api-core/CHANGELOG.md`, under the `## [0.1.0] - unreleased` heading, add an actionable-errors line to the existing `### Changed` section so it reads:

```markdown
### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.core`.
- **Breaking:** `RiotApiException` messages are now actionable, status-derived text (e.g. a 403
  explains that development keys expire every 24 hours) instead of the raw Riot body. The raw body
  moves to `RiotApiException.getRawBody()`. See [ADR-0007](../docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md).
```

- [ ] **Step 9: Commit**

```bash
git add riot-api-core/src/main/java/com/muddl/riot/core/exception/RiotApiException.java \
        riot-api-core/src/test/java/com/muddl/riot/core/exception/RiotApiExceptionTest.java \
        riot-api-core/src/main/java/com/muddl/riot/core/http/RiotApiClient.java \
        riot-api-core/src/test/java/com/muddl/riot/core/http/RiotApiClientTest.java \
        riot-api-core/CHANGELOG.md
git commit -m "feat(core): actionable RiotApiException messages, raw body preserved

The status handler passed Riot's raw body straight to the model as the
exception message. The intended consumer is a third party on their own key,
so a 403 now explains that dev keys expire every 24 hours; 404/429/503 get
their own actionable text. The raw body moves to getRawBody() for diagnostics.

forStatus() is the single home of the status->message table."
```

---

### Task 2: 429 retry honouring `Retry-After`

Riot documents `Retry-After` on a 429, so reacting to it is the behaviour with a specification behind it (this is **not** a proactive rate limiter — that is a deferred item, see the roadmap). Retry belongs in a `ClientHttpRequestInterceptor`, **not** the `defaultStatusHandler`: the handler throws, so by the time it runs the decision to fail is already made. The interceptor reads only the status code and the `Retry-After` header — never the body — so it does not consume the stream the status handler later reads. When retries are exhausted the final 429 flows through to the handler and maps to `RiotApiException.forStatus(429, ...)` from Task 1 — that **is** the "clear exception when exhausted."

**Files:**
- Create: `riot-api-core/src/main/java/com/muddl/riot/core/http/BackoffSleeper.java`
- Create: `riot-api-core/src/main/java/com/muddl/riot/core/http/RetryOn429Interceptor.java`
- Modify: `riot-api-core/src/main/java/com/muddl/riot/core/config/RiotApiProperties.java`
- Modify: `riot-api-core/src/main/java/com/muddl/riot/core/http/RiotApiClient.java`
- Modify: `riot-api-core/src/main/java/com/muddl/riot/core/config/RiotApiAutoConfiguration.java`
- Modify: `riot-api-core/src/test/java/com/muddl/riot/core/http/RiotApiClientTest.java`
- Modify: `riot-api-core/src/test/java/com/muddl/riot/core/config/RiotApiAutoConfigurationTest.java`
- Modify: `riot-api-core/CHANGELOG.md`

**Interfaces:**
- Consumes: `RiotApiException.forStatus` (Task 1) — the exhausted-429 message.
- Produces:
  - `interface BackoffSleeper { void sleep(java.time.Duration duration); static BackoffSleeper realTime(); }`
  - `RiotApiClient(RiotApiProperties properties, BackoffSleeper sleeper)` — the DI constructor. The existing `RiotApiClient(RiotApiProperties)` stays as a convenience that supplies `BackoffSleeper.realTime()`, so the four other `new RiotApiClient(properties)` call sites compile unchanged.
  - `RiotApiProperties.getMaxRetries() -> int` (default `3`), `getRetryBackoff() -> java.time.Duration` (default `1s`).
  - Auto-config registers a `@ConditionalOnMissingBean BackoffSleeper` and passes it into `riotApiClient`.

- [ ] **Step 1: Write the `BackoffSleeper` seam**

Create `riot-api-core/src/main/java/com/muddl/riot/core/http/BackoffSleeper.java`:

```java
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
```

- [ ] **Step 2: Write the retry interceptor**

Create `riot-api-core/src/main/java/com/muddl/riot/core/http/RetryOn429Interceptor.java`:

```java
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
```

- [ ] **Step 3: Add the retry knobs to `RiotApiProperties`**

In `riot-api-core/src/main/java/com/muddl/riot/core/config/RiotApiProperties.java`, add the import and two fields. After `import org.springframework.boot.context.properties.ConfigurationProperties;` add:

```java
import java.time.Duration;
```

And after the `baseUrlOverride` field (before the closing brace), add:

```java
    /** How many times to retry a 429 before giving up and surfacing the error. */
    private int maxRetries = 3;

    /** Backoff used between 429 retries when the response carries no usable {@code Retry-After}. */
    private Duration retryBackoff = Duration.ofSeconds(1);
```

- [ ] **Step 4: Wire the interceptor into `RiotApiClient`**

In `riot-api-core/src/main/java/com/muddl/riot/core/http/RiotApiClient.java`:

Remove the `@RequiredArgsConstructor` (and its import) and replace the field + constructor region so the class holds an injected `BackoffSleeper` and offers both constructors. The top of the class becomes:

```java
public class RiotApiClient {

    private static final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    private final RiotApiProperties properties;
    private final BackoffSleeper sleeper;

    public RiotApiClient(RiotApiProperties properties, BackoffSleeper sleeper) {
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /**
     * Convenience for callers that do not control backoff timing (production default). Uses a real
     * {@link BackoffSleeper#realTime()} sleeper. Tests that exercise retry pass a recording sleeper
     * through the two-arg constructor instead.
     */
    public RiotApiClient(RiotApiProperties properties) {
        this(properties, BackoffSleeper.realTime());
    }
```

Then register the interceptor in `clientFor`, before `defaultStatusHandler`:

```java
    private RestClient clientFor(String host) {
        return RestClient.builder()
                .baseUrl(resolveBaseUrl(host))
                .defaultHeader(RIOT_TOKEN_HEADER, properties.getApiKey())
                .requestInterceptor(
                        new RetryOn429Interceptor(properties.getMaxRetries(), properties.getRetryBackoff(), sleeper))
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw RiotApiException.forStatus(response.getStatusCode().value(), body);
                })
                .build();
    }
```

Delete `import lombok.RequiredArgsConstructor;`. Leave the other imports.

- [ ] **Step 5: Register the sleeper bean in auto-config**

In `riot-api-core/src/main/java/com/muddl/riot/core/config/RiotApiAutoConfiguration.java`, add the `BackoffSleeper` import and bean, and pass it into `riotApiClient`:

```java
package com.muddl.riot.core.config;

import com.muddl.riot.core.http.BackoffSleeper;
import com.muddl.riot.core.http.RiotApiClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the shared Riot HTTP layer. This library is consumed as a
 * dependency, never component-scanned, so every bean is declared explicitly here rather
 * than discovered via stereotypes. {@link ConditionalOnMissingBean} lets a consuming
 * application override any of them.
 */
@AutoConfiguration
@EnableConfigurationProperties(RiotApiProperties.class)
public class RiotApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BackoffSleeper backoffSleeper() {
        return BackoffSleeper.realTime();
    }

    @Bean
    @ConditionalOnMissingBean
    public RiotApiClient riotApiClient(RiotApiProperties properties, BackoffSleeper sleeper) {
        return new RiotApiClient(properties, sleeper);
    }
}
```

- [ ] **Step 6: Write the failing retry tests**

In `riot-api-core/src/test/java/com/muddl/riot/core/http/RiotApiClientTest.java`, add imports and a nested recording sleeper plus four retry tests. Add these imports alongside the existing ones:

```java
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
```

(`RiotApiException` is already imported — do not add a second import.) Add a recording sleeper and a helper to build a client with explicit retry config, plus the tests, inside the class:

```java
    /** Records requested backoff durations without ever sleeping. */
    private static final class RecordingSleeper implements BackoffSleeper {
        private final List<Duration> waits = new ArrayList<>();

        @Override
        public void sleep(Duration duration) {
            waits.add(duration);
        }
    }

    private RiotApiClient clientWith(RecordingSleeper sleeper, int maxRetries, Duration backoff) {
        RiotApiProperties props = new RiotApiProperties();
        props.setApiKey("test-key-123");
        props.setBaseUrlOverride("http://localhost:" + wireMock.port());
        props.setMaxRetries(maxRetries);
        props.setRetryBackoff(backoff);
        return new RiotApiClient(props, sleeper);
    }

    @Test
    void retries_after_a_429_then_succeeds() {
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2"))
                .willSetStateTo("recovered"));
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        RecordingSleeper sleeper = new RecordingSleeper();
        String body = clientWith(sleeper, 3, Duration.ofSeconds(1))
                .platform(RiotApiPlatformUri.NA1)
                .get()
                .uri("/retry")
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("ok");
        assertThat(sleeper.waits).containsExactly(Duration.ofSeconds(2)); // Retry-After honoured
    }

    @Test
    void falls_back_to_the_default_backoff_when_no_retry_after_header() {
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429)) // no Retry-After
                .willSetStateTo("recovered"));
        stubFor(get(urlEqualTo("/retry"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        RecordingSleeper sleeper = new RecordingSleeper();
        clientWith(sleeper, 3, Duration.ofMillis(250))
                .platform(RiotApiPlatformUri.NA1)
                .get()
                .uri("/retry")
                .retrieve()
                .body(String.class);

        assertThat(sleeper.waits).containsExactly(Duration.ofMillis(250));
    }

    @Test
    void bounds_the_attempts_then_maps_the_final_429_to_the_rate_limited_message() {
        stubFor(get(urlEqualTo("/always")).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")));

        RecordingSleeper sleeper = new RecordingSleeper();

        assertThatThrownBy(() -> clientWith(sleeper, 3, Duration.ofSeconds(5))
                        .platform(RiotApiPlatformUri.NA1)
                        .get()
                        .uri("/always")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RiotApiException.class)
                .hasMessage("Rate limited by the Riot API")
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(429);

        assertThat(sleeper.waits).hasSize(3); // three retries, bounded by maxRetries
        verify(exactly(4), getRequestedFor(urlEqualTo("/always"))); // 1 initial + 3 retries
    }

    @Test
    void does_not_retry_a_non_429_error() {
        stubFor(get(urlEqualTo("/forbidden")).willReturn(aResponse().withStatus(403).withBody("nope")));

        RecordingSleeper sleeper = new RecordingSleeper();

        assertThatThrownBy(() -> clientWith(sleeper, 3, Duration.ofSeconds(1))
                        .platform(RiotApiPlatformUri.NA1)
                        .get()
                        .uri("/forbidden")
                        .retrieve()
                        .body(String.class))
                .isInstanceOf(RiotApiException.class);

        assertThat(sleeper.waits).isEmpty();
        verify(exactly(1), getRequestedFor(urlEqualTo("/forbidden")));
    }
```

- [ ] **Step 7: Run the retry tests — must pass, and stay fast**

```bash
./gradlew :riot-api-core:test --tests '*RiotApiClientTest*'
```

Expected: PASS, in well under a second (no real sleeps — `RecordingSleeper` never blocks). If this run takes seconds, a real sleeper leaked in: confirm every retry test builds its client via `clientWith(...)` (two-arg constructor), not `new RiotApiClient(props)`.

- [ ] **Step 8: Extend the auto-config test**

In `riot-api-core/src/test/java/com/muddl/riot/core/config/RiotApiAutoConfigurationTest.java`, add an import and two assertions:

```java
import com.muddl.riot.core.http.BackoffSleeper;
```

```java
    @Test
    void registers_backoff_sleeper_bean() {
        runner.run(context -> assertThat(context).hasSingleBean(BackoffSleeper.class));
    }

    @Test
    void retry_defaults_are_three_attempts_and_one_second() {
        runner.run(context -> {
            assertThat(context.getBean(RiotApiProperties.class).getMaxRetries()).isEqualTo(3);
            assertThat(context.getBean(RiotApiProperties.class).getRetryBackoff())
                    .isEqualTo(java.time.Duration.ofSeconds(1));
        });
    }
```

- [ ] **Step 9: Full module build**

```bash
./gradlew spotlessApply && ./gradlew :riot-api-core:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Log it in the changelog**

In `riot-api-core/CHANGELOG.md`, under `## [0.1.0] - unreleased`, add an `### Added` section (above `### Changed`):

```markdown
### Added
- Automatic retry on HTTP 429, honouring the `Retry-After` header (falling back to a configurable
  `riot.retry-backoff`, default 1s) up to `riot.max-retries` attempts (default 3). This is reactive
  retry, not a proactive rate limiter — see [ADR-0007](../docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md).
```

- [ ] **Step 11: Commit**

```bash
git add riot-api-core/src/main/java/com/muddl/riot/core/http/BackoffSleeper.java \
        riot-api-core/src/main/java/com/muddl/riot/core/http/RetryOn429Interceptor.java \
        riot-api-core/src/main/java/com/muddl/riot/core/config/RiotApiProperties.java \
        riot-api-core/src/main/java/com/muddl/riot/core/http/RiotApiClient.java \
        riot-api-core/src/main/java/com/muddl/riot/core/config/RiotApiAutoConfiguration.java \
        riot-api-core/src/test/java/com/muddl/riot/core/http/RiotApiClientTest.java \
        riot-api-core/src/test/java/com/muddl/riot/core/config/RiotApiAutoConfigurationTest.java \
        riot-api-core/CHANGELOG.md
git commit -m "feat(core): retry 429 honouring Retry-After, in an interceptor

The status handler throws, so a retry there is too late. A request
interceptor retries on 429 up to riot.max-retries, waiting Retry-After (or
riot.retry-backoff), reading only status + header so it never consumes the
body the status handler reads next. Exhaustion surfaces the mapped 429.

BackoffSleeper is injected so the tests never really sleep. Reactive retry,
not a token bucket — that stays deferred (ADR-0007)."
```

---

### Task 3: `PlayerIdentityResolver` on a Caffeine cache

Every game server needs to turn a caller-supplied player reference — a `GameName#TAG` Riot ID or a raw PUUID — into a PUUID, and account-v1 already lives here and is already cross-game, so resolution lives here once, not re-implemented per server per game. It **returns a plain PUUID string, not a `RiotAccount`**: that is what lets the ArchUnit split (Task 4) open identity resolution to every context without opening the account domain through the resolver's return type.

Caching keys on Riot ID → PUUID (PUUIDs are stable; Riot IDs are mutable, so the TTL bounds staleness) and is backed by **Caffeine** — the de-facto standard JVM cache, version-managed by the Spring Boot BOM — with its time source injected as a `Ticker` so the suite is deterministic and never really waits. Caffeine over a hand-rolled cache is a deliberate call recorded in ADR-0008 (Task 5): a managed dependency with correct eviction and a clean test seam beats reimplementing, slightly worse, what the ecosystem already standardizes on. Caffeine stays an **implementation detail** — it appears in the resolver's constructor and the auto-config, never in `resolvePuuid`'s signature or any server's code.

**Files:**
- Modify: `riot-account-core/build.gradle`
- Create: `riot-account-core/src/main/java/com/muddl/riot/account/identity/PlayerIdentityResolver.java`
- Create: `riot-account-core/src/test/java/com/muddl/riot/account/identity/MutableTicker.java`
- Create: `riot-account-core/src/test/java/com/muddl/riot/account/identity/PlayerIdentityResolverTest.java`
- Modify: `riot-account-core/src/main/java/com/muddl/riot/account/config/RiotAccountAutoConfiguration.java`
- Modify: `riot-account-core/src/test/java/com/muddl/riot/account/config/RiotAccountAutoConfigurationTest.java`
- Modify: `docs/knowledge/gotchas.md`
- Modify: `riot-account-core/CHANGELOG.md`

**Interfaces:**
- Consumes: `RiotAccountService.getAccountByRiotId(String, String)` and `RiotAccount.getPuuid()` (existing).
- Produces:
  - `PlayerIdentityResolver(RiotAccountService accountService, com.github.benmanes.caffeine.cache.Ticker ticker, java.time.Duration cacheTtl, int cacheMaxSize)`.
  - `String resolvePuuid(String player)` — a value with no `#` is returned as-is (a PUUID; no Riot call); a `GameName#TAG` is resolved via the account service and cached; a blank or malformed value throws `IllegalArgumentException` naming both accepted forms; a Riot ID with no matching account throws `IllegalArgumentException` and is not cached.
  - `MutableTicker` (test): `void advance(java.time.Duration amount)`.
  - Auto-config registers a `@ConditionalOnMissingBean PlayerIdentityResolver` (5-minute TTL, 10 000 entries, `Ticker.systemTicker()`).

- [ ] **Step 1: Add the Caffeine dependency**

In `riot-account-core/build.gradle`, add Caffeine to the `dependencies` block, immediately after `api project(':riot-api-core')`:

```groovy
	// Caffeine backs PlayerIdentityResolver's Riot ID -> PUUID cache. implementation, not api: it is
	// an internal detail — the resolver's public surface is resolvePuuid(String), and no Caffeine type
	// appears in it. Version is managed by the Spring Boot BOM. See ADR-0008.
	implementation 'com.github.ben-manes.caffeine:caffeine'
```

Verify the version resolves off the BOM (no explicit version needed):

```bash
./gradlew -q :riot-account-core:dependencies --configuration runtimeClasspath | grep -i caffeine
```

Expected: a line like `com.github.ben-manes.caffeine:caffeine -> 3.x.y` (a concrete version, resolved by Spring Boot's dependency management). If it shows `FAILED` or no version, the BOM does not manage Caffeine on this Spring Boot version — pin the version explicitly (`com.github.ben-manes.caffeine:caffeine:3.1.8`) and note it in the commit.

- [ ] **Step 2: Write the hand-advanced test ticker**

Create `riot-account-core/src/test/java/com/muddl/riot/account/identity/MutableTicker.java`:

```java
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
```

- [ ] **Step 3: Write the failing resolver test**

Create `riot-account-core/src/test/java/com/muddl/riot/account/identity/PlayerIdentityResolverTest.java`:

```java
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
        RiotAccount account =
                RiotAccount.builder().puuid("resolved-puuid").gameName("Faker").tagLine("KR1").build();
        PlayerIdentityResolver resolver = resolver(new CountingPort(account), new MutableTicker());

        assertThat(resolver.resolvePuuid("Faker#KR1")).isEqualTo("resolved-puuid");
    }

    @Test
    void a_repeated_riot_id_lookup_is_served_from_cache() {
        RiotAccount account =
                RiotAccount.builder().puuid("resolved-puuid").gameName("Faker").tagLine("KR1").build();
        CountingPort port = new CountingPort(account);
        PlayerIdentityResolver resolver = resolver(port, new MutableTicker());

        resolver.resolvePuuid("Faker#KR1");
        resolver.resolvePuuid("Faker#KR1");

        assertThat(port.riotIdLookups).isEqualTo(1); // one Riot call across two lookups
    }

    @Test
    void a_cached_riot_id_is_re_fetched_after_the_ttl_expires() {
        RiotAccount account =
                RiotAccount.builder().puuid("resolved-puuid").gameName("Faker").tagLine("KR1").build();
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
```

- [ ] **Step 4: Run it — must fail to compile (no `PlayerIdentityResolver`)**

```bash
./gradlew :riot-account-core:test --tests '*PlayerIdentityResolverTest*'
```

Expected: FAIL — `cannot find symbol: class PlayerIdentityResolver`.

- [ ] **Step 5: Implement the resolver**

Create `riot-account-core/src/main/java/com/muddl/riot/account/identity/PlayerIdentityResolver.java`:

```java
package com.muddl.riot.account.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import java.time.Duration;

/**
 * Resolves a caller-supplied {@code player} — either a {@code GameName#TAG} Riot ID or a raw PUUID —
 * to a PUUID. This is the one part of {@code riot-account-core} every game server is meant to depend
 * on: it lets tools take a single {@code player} parameter instead of forcing the model to chain
 * account → summoner → match itself.
 *
 * <p>It returns a plain PUUID {@code String}, deliberately not a {@link RiotAccount}: the account
 * domain stays confined (ArchUnit-enforced), and identity resolution is the open surface. Contexts
 * that want account <em>data</em> still go through {@code RiotAccountService}. See ADR-0008.
 *
 * <p>Resolution of a Riot ID is cached (Riot ID → PUUID) in a bounded, TTL-expiring Caffeine cache.
 * PUUIDs are stable, but Riot IDs are mutable, so the TTL bounds how stale a mapping can get. A raw
 * PUUID needs no lookup. The cache's {@link Ticker} is injected so tests advance time by hand.
 * Caffeine is an implementation detail — it appears here and in the auto-configuration, never in the
 * public {@link #resolvePuuid(String)} contract.
 */
public class PlayerIdentityResolver {

    private final RiotAccountService accountService;
    private final Cache<String, String> puuidByRiotId;

    public PlayerIdentityResolver(
            RiotAccountService accountService, Ticker ticker, Duration cacheTtl, int cacheMaxSize) {
        this.accountService = accountService;
        this.puuidByRiotId = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(cacheTtl)
                .ticker(ticker)
                .build();
    }

    /**
     * @param player a {@code GameName#TAG} Riot ID or a raw PUUID
     * @return the resolved PUUID
     * @throws IllegalArgumentException if {@code player} is blank, malformed, or names no account
     */
    public String resolvePuuid(String player) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        String trimmed = player.trim();
        if (trimmed.indexOf('#') < 0) {
            return trimmed; // already a PUUID — nothing to resolve, no Riot call, no cache entry
        }
        String[] parts = trimmed.split("#", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        String gameName = parts[0];
        String tagLine = parts[1];
        // get(key, loader) is atomic per key; a loader that throws (unknown Riot ID) propagates and
        // caches nothing, which is exactly the "do not cache failed lookups" behaviour we want.
        return puuidByRiotId.get(gameName + "#" + tagLine, key -> lookupPuuid(gameName, tagLine));
    }

    private String lookupPuuid(String gameName, String tagLine) {
        RiotAccount account = accountService.getAccountByRiotId(gameName, tagLine);
        if (account == null || account.getPuuid() == null || account.getPuuid().isBlank()) {
            throw new IllegalArgumentException("No Riot account found for Riot ID '" + gameName + "#" + tagLine + "'.");
        }
        return account.getPuuid();
    }

    private static String unparseableMessage(String player) {
        return "Cannot resolve player '" + player + "'. Provide a Riot ID as GameName#TAG "
                + "(for example Faker#KR1) or a raw PUUID.";
    }
}
```

- [ ] **Step 6: Run the resolver test — must pass**

```bash
./gradlew :riot-account-core:test --tests '*PlayerIdentityResolverTest*'
```

Expected: PASS — all seven tests. The unknown-Riot-ID test relies on Caffeine's `get(key, loader)` not storing anything when the loader throws (the exception propagates), so the second call loads again.

- [ ] **Step 7: Register the resolver bean**

In `riot-account-core/src/main/java/com/muddl/riot/account/config/RiotAccountAutoConfiguration.java`, add imports and a bean. Add these imports:

```java
import com.github.benmanes.caffeine.cache.Ticker;
import com.muddl.riot.account.identity.PlayerIdentityResolver;
import java.time.Duration;
```

Add this bean inside the class (after `riotAccountService`):

```java
    /**
     * Identity resolution is the open, cross-cutting surface of this library — every game server's
     * player-keyed tools depend on it (see ADR-0008). Defaults: a 5-minute TTL bounds Riot-ID
     * staleness, 10 000 entries bound memory, and the system ticker drives expiry. A consumer can
     * override the whole bean via {@link ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean
    public PlayerIdentityResolver playerIdentityResolver(RiotAccountService riotAccountService) {
        return new PlayerIdentityResolver(riotAccountService, Ticker.systemTicker(), Duration.ofMinutes(5), 10_000);
    }
```

- [ ] **Step 8: Extend the auto-config test**

In `riot-account-core/src/test/java/com/muddl/riot/account/config/RiotAccountAutoConfigurationTest.java`, add an import and a test:

```java
import com.muddl.riot.account.identity.PlayerIdentityResolver;
```

```java
    @Test
    void registers_the_player_identity_resolver() {
        runner.run(context -> assertThat(context).hasSingleBean(PlayerIdentityResolver.class));
    }
```

- [ ] **Step 9: Record the gotchas**

Append to the bottom of `docs/knowledge/gotchas.md`:

```markdown
## The identity cache keys on Riot ID → PUUID, never the reverse — Riot IDs are mutable

`PlayerIdentityResolver` (riot-account-core) caches to avoid a second Riot call per player-keyed
tool invocation. It caches **Riot ID → PUUID**, with a bounded TTL (Caffeine `expireAfterWrite`),
and deliberately does not cache PUUID → anything:

- **PUUIDs are stable.** A raw PUUID needs no lookup at all, so it is returned as-is with no cache
  entry.
- **Riot IDs are mutable.** A player can change their `GameName#TAG`. So a `GameName#TAG → PUUID`
  mapping can go stale, and the TTL (default 5 minutes) bounds how stale. Do not raise the TTL to
  "improve the hit rate" without accepting more staleness — that is the trade the TTL exists to make.

A failed lookup (no such account) is **not** cached: Caffeine's `get(key, loader)` stores nothing
when the loader throws, so a later retry re-checks. Don't add negative caching without a reason — a
mistyped Riot ID that later becomes valid should resolve.

## Caffeine `maximumSize` is approximate and eviction is asynchronous

Caffeine's `maximumSize` is a bound, not a hard cap enforced synchronously — eviction runs on an
executor, so the cache can briefly hold more than `maximumSize` entries, and an entry is not
guaranteed to be gone the instant the bound is exceeded. **Do not write a test asserting exact
size-based eviction** (`put maxSize+1, assert the eldest is gone`) — it is flaky. If you ever must,
build the cache with `.executor(Runnable::run)` and call `cache.cleanUp()` first. TTL expiry, by
contrast, is deterministic on read: `expireAfterWrite` + an injected `Ticker` is exactly why the
identity tests can advance time by hand and assert re-fetch. Size eviction is Caffeine's guarantee to
keep; it is not ours to test.
```

- [ ] **Step 10: Log it in the changelog**

In `riot-account-core/CHANGELOG.md`, under `## [0.1.0] - unreleased`, add an `### Added` section (above `### Changed`):

```markdown
### Added
- `PlayerIdentityResolver`: resolves a `GameName#TAG` Riot ID or a raw PUUID to a PUUID, cached
  (Riot ID → PUUID) in a bounded, TTL-expiring Caffeine cache on an injected ticker. The open,
  cross-cutting identity surface every game server depends on — see
  [ADR-0008](../docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md).
```

- [ ] **Step 11: Full module build and commit**

```bash
./gradlew spotlessApply && ./gradlew :riot-account-core:build
```

Expected: `BUILD SUCCESSFUL`, including `AccountArchitectureTest` (a class in `..account.identity..` named `PlayerIdentityResolver` violates none of the hexagon rules: it is not a `*Service`, `*Port`, `*Tool`, or `*Adapter`, and uses no `RestClient`) and the auto-config slice test.

```bash
git add riot-account-core/build.gradle \
        riot-account-core/src/main/java/com/muddl/riot/account/identity/PlayerIdentityResolver.java \
        riot-account-core/src/test/java/com/muddl/riot/account/identity/MutableTicker.java \
        riot-account-core/src/test/java/com/muddl/riot/account/identity/PlayerIdentityResolverTest.java \
        riot-account-core/src/main/java/com/muddl/riot/account/config/RiotAccountAutoConfiguration.java \
        riot-account-core/src/test/java/com/muddl/riot/account/config/RiotAccountAutoConfigurationTest.java \
        docs/knowledge/gotchas.md riot-account-core/CHANGELOG.md
git commit -m "feat(account): PlayerIdentityResolver, Caffeine-cached on an injected ticker

Turns a GameName#TAG or a raw PUUID into a PUUID so every server's tools can
take one player param instead of the model chaining account->summoner->match.
Returns a plain PUUID string, not a RiotAccount, so identity resolution can be
opened to every context (Task 4) without opening the account domain.

Caches Riot ID -> PUUID with a bounded TTL (Caffeine, ticker injected so tests
never wait): PUUIDs are stable, Riot IDs are mutable, so the TTL bounds
staleness. Failed lookups are not cached. Caffeine chosen over a hand-rolled
cache (ADR-0008) and kept an implementation detail."
```

---

### Task 4: Split the account ArchUnit rule — domain confined, identity open (test-only)

Task 3 created the surface every context is *supposed* to depend on. But `lol-mcp-server`'s `only_analytics_and_the_account_tool_use_the_account_library` denies **all** of `..riot.account..` to any context outside `analytics`/`account`. Widening the allowlist to "everyone" would pass and throw the guarantee away. Instead the rule **splits at the right granularity**: the account **domain** stays deny-by-default; identity resolution (`..riot.account.identity..`) goes open. This is the only edit outside the two libraries, and it is test-only — no server behaviour changes, and no LoL context depends on the resolver yet (that is Plan C). The split lands now so Plan C's contexts are born able to inject the resolver without touching ArchUnit.

The evidence discipline from Plan A holds: a green build cannot tell a real rule from a vacuous one, so both halves get a control — the **domain** half must still fail on a violation (`ArchFixtureIllegalAccountUser`, unchanged), and the **open** half must *not* fail on a legal resolver dependency (`ArchFixtureLegalResolverUser`, new).

**Files:**
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/HexagonalArchitectureTest.java:56-99`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/ArchFixtureLegalResolverUser.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/HexagonalArchitectureNegativeControlTest.java`

**Interfaces:**
- Consumes: `PlayerIdentityResolver` (Task 3) — on `lol-mcp-server`'s compile classpath already, since the server depends on `riot-account-core`.
- Produces: `HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_domain` (the renamed, split rule). The negative control references this new name.

- [ ] **Step 1: Split the rule**

In `lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/HexagonalArchitectureTest.java`:

First add the `resideOutsideOfPackages` predicate import next to the existing `resideInAPackage` import at the top:

```java
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
```

In the javadoc of `contexts_do_not_depend_on_each_other` (around line 62), update the `{@link}` to the new rule name so it reads:

```java
     * analytics -> account needs no exception here: RiotAccountService lives in
     * com.muddl.riot.account (riot-account-core), outside this matcher. That same fact is why
     * {@link #only_analytics_and_the_account_tool_use_the_account_domain} exists — see below.
```

Then replace the entire `only_analytics_and_the_account_tool_use_the_account_library` field (its javadoc and rule, lines 73-99) with the split version:

```java
    /**
     * Only analytics (which composes it) and this server's thin account tool may reach into the
     * shared account <em>domain</em>. Identity resolution is deliberately excluded from this
     * confinement — see the {@code identity} carve-out below.
     * <p>
     * Before the monorepo split, the account context lived under this server's package root, so the
     * cross-context matrix forbade summoner/match/spectator from touching it. Extracting it to the
     * account library moved it outside {@link #contexts_do_not_depend_on_each_other}'s matcher,
     * which silently retired those three prohibitions — nothing violated them, so nothing failed.
     * This restores the guarantee.
     * <p>
     * The condition confines {@code ..riot.account..} but excludes {@code ..riot.account.identity..}:
     * {@code PlayerIdentityResolver} is the one part of the account library every player-keyed
     * context is <em>supposed</em> to depend on (ADR-0008). It returns a plain PUUID string, not a
     * {@code RiotAccount}, so opening it does not open the account domain through its return type.
     * Widening the allowlist instead would have thrown the domain guarantee away.
     * <p>
     * Matchers here are deliberately relative ({@code ..riot.account..}, not a fully-qualified
     * name). This rule's package sits in its <em>condition</em>, not its selector, so a
     * fully-qualified name would make the rule pass vacuously the moment the group changed — the
     * same silent-retirement failure it exists to prevent. {@link
     * HexagonalArchitectureNegativeControlTest} proves both halves still bite: the domain stays
     * forbidden, the resolver stays allowed.
     * <p>
     * riot-account-core is a domain context, not infrastructure (that distinction is why it is its
     * own module rather than part of riot-api-core), so "any module may consume it" is not the
     * intent. Stated as deny-by-default: a context added later is forbidden from the domain until
     * listed here.
     */
    @ArchTest
    static final ArchRule only_analytics_and_the_account_tool_use_the_account_domain = noClasses()
            .that()
            .resideOutsideOfPackages("..lol.analytics..", "..lol.account..")
            .should()
            .dependOnClassesThat(
                    resideInAPackage("..riot.account..").and(resideOutsideOfPackages("..riot.account.identity..")))
            .as("only analytics and the account tool use the account domain "
                    + "(identity resolution is open to every context)");
```

- [ ] **Step 2: Write the positive-control fixture**

Create `lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/ArchFixtureLegalResolverUser.java`:

```java
package com.muddl.riot.lol.architecture;

import com.muddl.riot.account.identity.PlayerIdentityResolver;

/**
 * A deliberately non-allowlisted context that depends on {@link PlayerIdentityResolver}. Unlike
 * {@link ArchFixtureIllegalAccountUser}, this dependency is <em>legal</em>: identity resolution is
 * the open surface of the account library, so the split rule {@code
 * only_analytics_and_the_account_tool_use_the_account_domain} must NOT flag it. {@link
 * HexagonalArchitectureNegativeControlTest} asserts exactly that — it is the positive control for
 * the "identity is open" half of the split.
 *
 * <p>Do not "fix" this class by removing the dependency. Its legality is the point: if the rule ever
 * starts flagging it, the split has collapsed back into blanket confinement.
 */
@SuppressWarnings("unused")
class ArchFixtureLegalResolverUser {

    /** The allowed dependency: a non-allowlisted context using the open identity resolver. */
    private PlayerIdentityResolver resolver;
}
```

- [ ] **Step 3: Update the negative control and add the positive control**

Replace `lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/HexagonalArchitectureNegativeControlTest.java` with:

```java
package com.muddl.riot.lol.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Proves the split account rule in {@link HexagonalArchitectureTest} bites on both sides.
 *
 * <p>A green build is not evidence for a package-string rule, because the failure mode <em>is</em> a
 * green build: {@code only_analytics_and_the_account_tool_use_the_account_domain} carries its package
 * in the rule's <em>condition</em>, so if that package moved the condition would match nothing, zero
 * violations would be found, and the rule would pass while guarding nothing. So this test asserts the
 * rule FAILS on a domain violation and, separately, does NOT fail on a legal identity dependency —
 * the two halves of the split.
 */
class HexagonalArchitectureNegativeControlTest {

    @Test
    void account_domain_rule_rejects_a_non_allowlisted_context_using_the_domain() {
        JavaClasses violating = new ClassFileImporter().importClasses(ArchFixtureIllegalAccountUser.class);

        assertThatThrownBy(() ->
                        HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_domain.check(
                                violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ArchFixtureIllegalAccountUser");
    }

    @Test
    void account_domain_rule_allows_a_non_allowlisted_context_using_the_identity_resolver() {
        JavaClasses legal = new ClassFileImporter().importClasses(ArchFixtureLegalResolverUser.class);

        assertThatCode(() -> HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_domain.check(
                        legal))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 4: Run both controls — one fails-on-violation, one passes-on-legal**

```bash
./gradlew :lol-mcp-server:test --tests '*HexagonalArchitectureNegativeControlTest*'
```

Expected: PASS (both methods). `ArchFixtureIllegalAccountUser` references `RiotAccount` in `..account.domain..` (still forbidden), so the first method's rule check throws as asserted. `ArchFixtureLegalResolverUser` references `PlayerIdentityResolver` in `..account.identity..` (carved out), so the second method's rule check does not throw.

- [ ] **Step 5: Run the full server test suite — the real scan is unaffected**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. `HexagonalArchitectureTest`'s real scan (`@AnalyzeClasses(... DoNotIncludeTests, DoNotIncludeGradleTestFixtures)`) excludes both fixtures, so the split rule finds no production violation — no LoL context depends on the account library outside `analytics`/`account` yet. `McpToolInventoryTest` is untouched: no tool changed.

- [ ] **Step 6: Commit**

```bash
git add lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/HexagonalArchitectureTest.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/ArchFixtureLegalResolverUser.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/architecture/HexagonalArchitectureNegativeControlTest.java
git commit -m "test(lol): split the account rule — domain confined, identity open

PlayerIdentityResolver is meant to be used by every context, but the account
library was confined wholesale to analytics/account. Rather than widen the
allowlist (which throws the guarantee away), the rule now confines the account
domain and carves out ..riot.account.identity.. — the resolver returns a plain
PUUID, so opening it doesn't open the domain through its return type.

Both halves are controlled: the domain still fails on a violation, and a legal
resolver dependency now provably does not. Test-only; no LoL context uses the
resolver yet (that is Plan C)."
```

---

### Task 5: Record the decisions — ADR-0007 and ADR-0008

Both libraries now carry decisions worth their own ADRs (named as cycle artifacts in the 1a spec): the core-hardening boundary (what `riot-api-core` is allowed to know, and why retry is reactive not proactive) and shared identity resolution (why in the account library, why a plain PUUID, why the rule split, and **why Caffeine over a hand-rolled cache**). ADR-0009 (the tool contract) is Plan C's, not this plan's.

**Files:**
- Create: `docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md`
- Create: `docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md`
- Modify: `docs/knowledge/README.md`

**Interfaces:**
- Consumes: everything from Tasks 1–4 (the ADRs describe the decisions those tasks implemented; the CHANGELOG entries from Tasks 1, 2, 3 already link these ADR paths).
- Produces: the durable record; ADR-0009 is left for Plan C.

- [ ] **Step 1: Write ADR-0007**

Create `docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md`:

```markdown
# ADR-0007: Core hardening boundary — what `riot-api-core` is allowed to know

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

`riot-api-core` is the shared HTTP kernel every game server inherits. Sub-project 1a hardens it
twice: automatic retry on HTTP 429, and an actionable error taxonomy. Both must land without letting
the kernel learn about any game's domain — [ADR-0006](ADR-0006-monorepo-split.md) warned about core
becoming a junk drawer.

Two concrete defects motivated the work. Errors surfaced raw: `RiotApiException("Riot API error: " +
rawBody, status)` went straight to the model, and the intended consumer is a third party on their own
key. And a 429 was a hard failure, though Riot documents a `Retry-After` header that says exactly how
long to wait.

## Decision

**The boundary: `riot-api-core` knows HTTP and Riot's error protocol, never a game's domain.** No
game DTOs, no game routing beyond the existing platform/region enums.

**429 retry lives in a `ClientHttpRequestInterceptor`, not the status handler.** The handler throws,
so by the time it runs the decision to fail is made. The interceptor retries on 429 up to
`riot.max-retries` (default 3), waiting the response's `Retry-After` when present and
`riot.retry-backoff` (default 1s) otherwise. It reads only the status code and that one header —
never the body — so it does not consume the stream the status handler reads next. When attempts are
exhausted the final 429 flows through and maps to the taxonomy's message.

**This is reactive retry, not a proactive rate limiter.** Riot documents `Retry-After` on 429, so
reacting to it has a specification behind it. A token bucket needs a real design (per-method limits,
shared buckets across contexts, concurrency) and would be guessing at this stage — it stays deferred
(see the roadmap).

**The error taxonomy lives on `RiotApiException`.** `forStatus(status, rawBody)` sets an actionable,
status-derived message (403 explains the 24-hour dev-key expiry; 404/429/503 each get their own),
keeps the raw body on `getRawBody()`, and is the single home of the status→message table.

**Backoff timing is injected (`BackoffSleeper`).** The retry path is deterministic and the suite
never really sleeps.

## Consequences

**Makes easy:** every future server inherits correct 429 handling and actionable errors for free.
Message wording lives in one place. Tests are fast and deterministic.

**Costs:** one more collaborator on `RiotApiClient` (the sleeper) and two more config knobs.

**Watch for:**

- **The interceptor must never read the response body** — doing so consumes the stream the status
  handler needs, and the failure is a confusing empty/failed error mapping rather than a loud one.
- **`Retry-After` is delta-seconds.** Riot sends an integer count of seconds; anything else falls back
  to the default backoff.
- **Junk-drawer pressure.** Any temptation to put a game DTO or game-specific routing here violates
  this ADR — that belongs in a server module.
```

- [ ] **Step 2: Write ADR-0008**

Create `docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md`:

```markdown
# ADR-0008: Shared player-identity resolution

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

Every Riot game server needs to turn a caller-supplied player reference — a `GameName#TAG` Riot ID
or a raw PUUID — into a PUUID before it can call a game endpoint. If that logic lived in each game's
contexts, every server (LoL, TFT, Valorant, LoR) would re-implement it, and each player-keyed tool
call would cost an extra Riot request. account-v1 is already cross-game and already lives in
`riot-account-core`.

## Decision

**`PlayerIdentityResolver` lives in `riot-account-core`.** It accepts a `GameName#TAG` or a raw
PUUID (disambiguated on the `#`) and returns a PUUID. Registered via the module's existing
auto-configuration, so a server gets it by declaring the dependency and nothing else.

**It returns a plain PUUID string, not a `RiotAccount`.** This is load-bearing. The account **domain**
stays confined (only `analytics` and the account tool may touch it, ArchUnit-enforced), while identity
resolution is the deliberately-open surface every context may use. If the resolver returned a
`RiotAccount`, every caller would touch the account domain through its return type and the confinement
would be defeated through the back door. Contexts that want account *data* still go through
`RiotAccountService` and remain subject to the deny-by-default list.

**The ArchUnit rule splits rather than widening.** `only_analytics_and_the_account_tool_use_the_
account_domain` confines `..riot.account..` but carves out `..riot.account.identity..`. Both halves
are controlled: a domain violation still fails the rule, and a legal resolver dependency provably does
not (see `HexagonalArchitectureNegativeControlTest`).

**Resolution is cached with Caffeine, bounded and TTL'd, on an injected `Ticker`.** The cache keys on
**Riot ID → PUUID**. PUUIDs are stable, so a raw PUUID needs no lookup; Riot IDs are **mutable**, so
`expireAfterWrite` (default 5 minutes) bounds staleness and `maximumSize` (default 10 000) bounds
memory. Caffeine's `Ticker` is injected so tests advance time by hand and never really wait. A failed
lookup is not cached (Caffeine's `get(key, loader)` stores nothing when the loader throws). This is
the first stateful component in the codebase.

**Why Caffeine rather than a hand-rolled cache.** The first draft of this work hand-rolled a
`synchronized LinkedHashMap` TTL cache (~30 lines). Caffeine replaced it deliberately: it is the
de-facto standard JVM cache, its version is managed by the Spring Boot BOM (a one-line dependency),
and its `Ticker` seam meets the deterministic-test requirement exactly as a hand-rolled injected clock
would. The hand-rolled version was correct-on-read but naive on eviction (insertion-order, not
recency/frequency) and carried a global lock — reimplementing, slightly worse, what a managed
dependency does correctly. "Don't roll your own cache" applies: the only thing hand-rolling bought was
one fewer dependency, and this repo already uses standard libraries (Spring, WireMock, ArchUnit,
Lombok, AssertJ) freely, so that saving did not justify the correctness and idiom cost. Caffeine stays
an **implementation detail** — it appears in the resolver's constructor and the auto-configuration,
never in `resolvePuuid`'s signature or any server's code.

## Consequences

**Makes easy:** a single `player` parameter on every tool across every server (the contract Plan C
and 1b build on), with the model never chaining account → summoner → match itself — which is both a
rate-limit multiplier and a common way models flail. One Riot call amortized across repeat lookups.

**Costs:** the first stateful thing to reason about, and one managed dependency (Caffeine).

**Watch for:**

- **Raising the TTL trades hit-rate for staleness.** Riot IDs change; the TTL is that trade made
  explicit. See `gotchas.md`.
- **Do not return `RiotAccount` from the resolver** — it reopens the confined domain through the back
  door.
- **Do not cache PUUID inputs or failed lookups** — the first needs no lookup, the second should
  re-check.
- **`maximumSize` is approximate and eviction is asynchronous** — do not write a test asserting exact
  size-based eviction without a same-thread executor; it will be flaky (see `gotchas.md`). TTL expiry
  on the injected ticker, by contrast, is deterministic.
```

- [ ] **Step 3: Link both ADRs from the knowledge index**

In `docs/knowledge/README.md`, in the "Decisions (ADRs)" list, insert the two entries in number order (between ADR-0006 and ADR-0010):

```markdown
- [ADR-0007 — Core hardening boundary](decisions/ADR-0007-core-hardening-boundary.md)
- [ADR-0008 — Shared player-identity resolution](decisions/ADR-0008-shared-player-identity-resolution.md)
```

- [ ] **Step 4: Verify the ADR links resolve**

```bash
ls docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md
grep -c 'ADR-0007\|ADR-0008' docs/knowledge/README.md
```

Expected: both files listed; `grep -c` prints `2`.

- [ ] **Step 5: Final full build — Plan B exit gate**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. This is Plan B's exit gate: both libraries hardened, the resolver in place and open, every new package-string rule proven to bite on both sides, `verifyRelease` green (both libraries still `0.1.0` with matching headings), and no server behaviour changed.

- [ ] **Step 6: Commit**

```bash
git add docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md \
        docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md \
        docs/knowledge/README.md
git commit -m "docs: ADR-0007 (core hardening boundary) and ADR-0008 (identity resolution)

ADR-0007 records what riot-api-core may know (HTTP + Riot's error protocol,
never game domain), why 429 retry lives in an interceptor, and why it's
reactive not a token bucket. ADR-0008 records why identity resolution lives in
the account library, why it returns a plain PUUID (so the rule can open it
without opening the domain), the mutable-Riot-ID TTL trade, and why Caffeine
over a hand-rolled cache."
```

---

## Plan B exit criteria

- `./gradlew build` green.
- **`riot-api-core`:** a 429 with `Retry-After` is retried and then succeeds; retries are bounded and exhaustion maps to "Rate limited by the Riot API"; a non-429 error does not retry — all proven with a recording sleeper (no real sleeps). `RiotApiException.forStatus` maps 403/404/429/503 to actionable messages and preserves the raw body.
- **`riot-account-core`:** `PlayerIdentityResolver` returns a raw PUUID unchanged with no Riot call, resolves a Riot ID to its PUUID, serves a repeat lookup from cache (one Riot call across two lookups), re-fetches after TTL expiry, and rejects blank/malformed/unknown inputs — all on an injected ticker. The resolver bean is registered. Caffeine is present and version-managed.
- **`lol-mcp-server` (test-only):** the account rule is split; `HexagonalArchitectureNegativeControlTest` proves the domain half still fails on a violation and the identity half does not fail on a legal resolver dependency. `McpToolInventoryTest` green and unchanged — ten tools, same names.
- Both libraries remain `0.1.0` with a `## [0.1.0]` heading carrying the new entries; `verifyRelease` green.
- ADR-0007 and ADR-0008 exist and are linked from the knowledge index.

## What Plan C picks up

Phases 4–6 — the LoL server (behaviour), against the now-hardened libraries:

- **LoL correctness:** delete the three dead by-name tools, spectator v4 → v5 (PUUID-keyed), summoner/spectator off `encryptedSummonerId` to PUUID.
- **League context (the exemplar):** a full mini-hexagon (ranked entries by player + apex leagues), the artifact 1b copies five times. Endpoint paths verified against the live Riot developer portal.
- **Contract sweep:** every tool → `<game>_<context>_<action>`; the single required `player` param on every player-keyed tool, resolved via `PlayerIdentityResolver` (Plan B) — this is where LoL contexts first depend on the resolver, exercising the open half of Task 4's rule split for real. Ten tools become seven. ADR-0009 records it.
