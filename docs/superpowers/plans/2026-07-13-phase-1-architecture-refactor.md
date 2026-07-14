# Phase 1: Architecture Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the codebase into bounded-context hexagons with a single shared Riot HTTP client, eliminating the copy-pasted HTTP/auth/error plumbing, while keeping the build green at every commit.

**Architecture:** Each Riot context (`account`, `summoner`, `match`, `spectator`, `analytics`) becomes a self-contained mini-hexagon: `domain/` (relocated DTOs), `application/` (thin service + outbound `port/` interface), `adapter/in/mcp/` (`@McpTool`), `adapter/out/riot/` (`RestClient` adapter implementing the port). All HTTP/auth/error handling is centralized in `shared/http/RiotApiClient`, driven by typed `shared/config/RiotApiProperties`. `analytics` is a composing context — it calls the other contexts' application services and has no outbound Riot adapter.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0 (MCP server), Lombok, JUnit 5, Mockito, AssertJ, WireMock (introduced here for the shared-client test).

## Global Constraints

- Package root: `com.wkaiser.riotapimcpserver`. New context packages are **top-level** (e.g. `com.wkaiser.riotapimcpserver.summoner.*`) — drop the old `riot`/`riot.lol` nesting.
- Java toolchain: 21. Spring Boot: `4.1.0`. Spring AI BOM: `2.0.0`. Do not change these versions.
- All DTOs keep the established Lombok pattern: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (and `@JsonIgnoreProperties(ignoreUnknown = true)` where already present). `RiotAccount` currently has only `@Data @Builder` — leave it as-is (do not add annotations in this phase).
- Behavior must be preserved. The one intentional change: requests that previously omitted the `X-RIOT-TOKEN` header (match endpoints) now include it, because all requests go through `RiotApiClient`.
- MCP tool names, `@McpTool`/`@McpToolParam` descriptions, and method signatures must not change — only their package location and the services they call.
- Ports are interfaces in `<context>.application.port`. Outbound adapters are `@Component` classes named `Riot<Context>Adapter` in `<context>.adapter.out.riot`. Application services are `@Service` classes in `<context>.application`. Tools stay `@Component` in `<context>.adapter.in.mcp`.
- Every task ends with `./gradlew build` passing (run from Git Bash; the `@Disabled` integration tests compile but do not execute).
- The Riot API key must never be hard-coded or logged. It is read from `RIOT_API_KEY` via `RiotApiProperties`.

---

### Task 1: Typed configuration — `RiotApiProperties`

**Files:**
- Create: `src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiProperties.java`
- Modify: `src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiConfiguration.java`
- Test: `src/test/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiPropertiesTest.java`

**Interfaces:**
- Produces: `RiotApiProperties` with `String getApiKey()`, `RiotApiRegionUri getRegion()` (default `AMERICAS`), `String getBaseUrlOverride()` (nullable). Bean is registered via `@EnableConfigurationProperties(RiotApiProperties.class)` on `RiotApiConfiguration`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiPropertiesTest.java`:

```java
package com.wkaiser.riotapimcpserver.shared.config;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RiotApiPropertiesTest {

    @Test
    void binds_apiKey_and_region_from_properties() {
        var source = new MapConfigurationPropertySource(Map.of(
                "riot.api-key", "test-key-123",
                "riot.region", "europe"));

        RiotApiProperties props = new Binder(source)
                .bind("riot", RiotApiProperties.class)
                .get();

        assertThat(props.getApiKey()).isEqualTo("test-key-123");
        assertThat(props.getRegion()).isEqualTo(RiotApiRegionUri.EUROPE);
        assertThat(props.getBaseUrlOverride()).isNull();
    }

    @Test
    void region_defaults_to_americas_when_absent() {
        var source = new MapConfigurationPropertySource(Map.of("riot.api-key", "k"));

        RiotApiProperties props = new Binder(source)
                .bind("riot", RiotApiProperties.class)
                .get();

        assertThat(props.getRegion()).isEqualTo(RiotApiRegionUri.AMERICAS);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.shared.config.RiotApiPropertiesTest"`
Expected: FAIL — compilation error, `RiotApiProperties` does not exist.

- [ ] **Step 3: Create `RiotApiProperties`**

Create `src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiProperties.java`:

```java
package com.wkaiser.riotapimcpserver.shared.config;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the Riot API integration, bound from the {@code riot.*}
 * configuration namespace. Replaces scattered {@code @Value("${riot.apiKey}")} usage.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "riot")
public class RiotApiProperties {

    /** Riot API key, sourced from the {@code RIOT_API_KEY} environment variable. */
    private String apiKey;

    /** Default region used for region-routed endpoints (account, match). */
    private RiotApiRegionUri region = RiotApiRegionUri.AMERICAS;

    /**
     * Optional base URL that, when set, overrides the {@code https://<host>} base URL
     * for every Riot client. Used by tests to point requests at a local mock server.
     */
    private String baseUrlOverride;
}
```

- [ ] **Step 4: Register the properties bean**

In `src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiConfiguration.java`, add the import and annotation. Add this import with the other imports:

```java
import org.springframework.boot.context.properties.EnableConfigurationProperties;
```

Change the class annotations from:

```java
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RiotApiConfiguration {
```

to:

```java
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RiotApiProperties.class)
public class RiotApiConfiguration {
```

Leave the existing `riotRestClient` bean untouched for now (it is removed in Task 9).

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.shared.config.RiotApiPropertiesTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiProperties.java \
        src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiConfiguration.java \
        src/test/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiPropertiesTest.java
git commit -m "feat: add typed RiotApiProperties configuration"
```

---

### Task 2: Shared HTTP client — `RiotApiClient`

**Files:**
- Create: `src/main/java/com/wkaiser/riotapimcpserver/shared/http/RiotApiClient.java`
- Modify: `build.gradle` (add WireMock test dependency)
- Test: `src/test/java/com/wkaiser/riotapimcpserver/shared/http/RiotApiClientTest.java`

**Interfaces:**
- Consumes: `RiotApiProperties` (Task 1).
- Produces: `RiotApiClient` (a `@Component`) with:
  - `RestClient regional(RiotApiRegionUri region)`
  - `RestClient platform(RiotApiPlatformUri platform)`

  Both return a `RestClient` pre-configured with the `X-RIOT-TOKEN` header and an error handler that throws `RiotApiException(message, statusCode)` for any non-2xx response. Base URL is `properties.getBaseUrlOverride()` if set, else `https://<host>`.

> **Note:** WireMock is introduced here (not deferred to Phase 2) because `RiotApiClient` is the first genuinely new component and deserves a real HTTP test. Phase 2 reuses this dependency for the per-adapter tests.

- [ ] **Step 1: Add the WireMock test dependency**

In `build.gradle`, inside the `dependencies { ... }` block, add below the existing `testRuntimeOnly` line:

```groovy
	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/wkaiser/riotapimcpserver/shared/http/RiotApiClientTest.java`:

```java
package com.wkaiser.riotapimcpserver.shared.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiotApiClientTest {

    private WireMockServer wireMock;
    private RiotApiClient riotApiClient;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        riotApiClient = new RiotApiClient(properties);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void sends_api_key_header_and_parses_body_on_success() {
        stubFor(get(urlEqualTo("/ping"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"pong\"")));

        String body = riotApiClient.platform(RiotApiPlatformUri.NA1).get()
                .uri("/ping")
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("pong");
        verify(getRequestedFor(urlEqualTo("/ping"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void maps_error_response_to_RiotApiException_with_status() {
        stubFor(get(urlEqualTo("/boom"))
                .willReturn(aResponse().withStatus(429).withBody("rate limited")));

        assertThatThrownBy(() -> riotApiClient.platform(RiotApiPlatformUri.NA1).get()
                .uri("/boom")
                .retrieve()
                .body(String.class))
                .isInstanceOf(RiotApiException.class)
                .hasMessageContaining("rate limited")
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(429);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.shared.http.RiotApiClientTest"`
Expected: FAIL — compilation error, `RiotApiClient` does not exist.

- [ ] **Step 4: Create `RiotApiClient`**

Create `src/main/java/com/wkaiser/riotapimcpserver/shared/http/RiotApiClient.java`:

```java
package com.wkaiser.riotapimcpserver.shared.http;

import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * Central factory for Riot API {@link RestClient} instances. This is the single place
 * that knows the Riot authentication header, base-URL assembly, and error-to-exception
 * mapping — replacing the per-service HTTP plumbing that was previously copy-pasted.
 */
@Component
@RequiredArgsConstructor
public class RiotApiClient {

    private static final String RIOT_TOKEN_HEADER = "X-RIOT-TOKEN";

    private final RiotApiProperties properties;

    /** A client for region-routed endpoints (account, match). */
    public RestClient regional(RiotApiRegionUri region) {
        return clientFor(region.getRegionUri());
    }

    /** A client for platform-routed endpoints (summoner, spectator). */
    public RestClient platform(RiotApiPlatformUri platform) {
        return clientFor(platform.getPlatformUri());
    }

    private RestClient clientFor(String host) {
        return RestClient.builder()
                .baseUrl(resolveBaseUrl(host))
                .defaultHeader(RIOT_TOKEN_HEADER, properties.getApiKey())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RiotApiException("Riot API error: " + body, response.getStatusCode().value());
                })
                .build();
    }

    private String resolveBaseUrl(String host) {
        String override = properties.getBaseUrlOverride();
        if (override != null && !override.isBlank()) {
            return override;
        }
        return "https://" + host;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.shared.http.RiotApiClientTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add build.gradle \
        src/main/java/com/wkaiser/riotapimcpserver/shared/http/RiotApiClient.java \
        src/test/java/com/wkaiser/riotapimcpserver/shared/http/RiotApiClientTest.java
git commit -m "feat: add shared RiotApiClient with centralized auth and error handling"
```

---

### Task 3: Relocate all domain models to `<context>.domain`

This is a pure, behavior-preserving move: each DTO's package declaration changes and every reference is repointed. No logic changes. Services and tools stay in their old packages for now (later tasks move them) but are updated to import the new domain locations.

**Files — move (via `git mv`) and change the `package` line in each:**

| From | To | New package declaration |
|------|----|-----|
| `riot/account/dto/RiotAccount.java` | `account/domain/RiotAccount.java` | `com.wkaiser.riotapimcpserver.account.domain` |
| `riot/lol/summoner/dto/Summoner.java` | `summoner/domain/Summoner.java` | `com.wkaiser.riotapimcpserver.summoner.domain` |
| `riot/lol/match/dto/Ban.java` | `match/domain/Ban.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/match/dto/Match.java` | `match/domain/Match.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/match/dto/MatchInfo.java` | `match/domain/MatchInfo.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/match/dto/MatchMetadata.java` | `match/domain/MatchMetadata.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/match/dto/ObjectiveDetails.java` | `match/domain/ObjectiveDetails.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/match/dto/Objectives.java` | `match/domain/Objectives.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/match/dto/Participant.java` | `match/domain/Participant.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/match/dto/Team.java` | `match/domain/Team.java` | `com.wkaiser.riotapimcpserver.match.domain` |
| `riot/lol/spectator/dto/BannedChampion.java` | `spectator/domain/BannedChampion.java` | `com.wkaiser.riotapimcpserver.spectator.domain` |
| `riot/lol/spectator/dto/CurrentGameInfo.java` | `spectator/domain/CurrentGameInfo.java` | `com.wkaiser.riotapimcpserver.spectator.domain` |
| `riot/lol/spectator/dto/CurrentGameParticipant.java` | `spectator/domain/CurrentGameParticipant.java` | `com.wkaiser.riotapimcpserver.spectator.domain` |
| `riot/lol/spectator/dto/FeaturedGames.java` | `spectator/domain/FeaturedGames.java` | `com.wkaiser.riotapimcpserver.spectator.domain` |
| `riot/lol/spectator/dto/GameCustomizationObject.java` | `spectator/domain/GameCustomizationObject.java` | `com.wkaiser.riotapimcpserver.spectator.domain` |
| `riot/lol/spectator/dto/Observer.java` | `spectator/domain/Observer.java` | `com.wkaiser.riotapimcpserver.spectator.domain` |
| `riot/lol/spectator/dto/Perks.java` | `spectator/domain/Perks.java` | `com.wkaiser.riotapimcpserver.spectator.domain` |
| `riot/lol/analytics/dto/PlayerMatchAnalytics.java` | `analytics/domain/PlayerMatchAnalytics.java` | `com.wkaiser.riotapimcpserver.analytics.domain` |

(Base path for all: `src/main/java/com/wkaiser/riotapimcpserver/`. DTOs reference their same-context siblings via the shared package, so no intra-context imports change.)

**Files — update imports only (no move in this task):**
- `riot/account/service/RiotAccountService.java`
- `riot/account/tool/RiotAccountTool.java`
- `riot/lol/summoner/service/SummonerService.java`
- `riot/lol/summoner/tool/SummonerTool.java`
- `riot/lol/match/service/MatchService.java`
- `riot/lol/spectator/service/SpectatorService.java`
- `riot/lol/spectator/tool/LiveGameTool.java`
- `riot/lol/analytics/service/AnalyticsService.java`
- `riot/lol/analytics/tool/AnalyticsTool.java`
- Test: `CompilationVerificationTest.java`, `riot/account/tool/RiotAccountToolTest.java`, `riot/lol/analytics/tool/AnalyticsToolTest.java`, `riot/lol/spectator/service/SpectatorServiceTest.java`, `riot/lol/spectator/service/SpectatorServiceIntegrationTest.java`, `riot/lol/spectator/tool/LiveGameToolTest.java`, `riot/lol/spectator/tool/LiveGameToolIntegrationTest.java`

**Interfaces:** No signatures change. Only fully-qualified type locations move, per this mapping (apply as a repo-wide find/replace of the FQN prefixes):

```
com.wkaiser.riotapimcpserver.riot.account.dto.RiotAccount
  -> com.wkaiser.riotapimcpserver.account.domain.RiotAccount
com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner
  -> com.wkaiser.riotapimcpserver.summoner.domain.Summoner
com.wkaiser.riotapimcpserver.riot.lol.match.dto.
  -> com.wkaiser.riotapimcpserver.match.domain.
com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.
  -> com.wkaiser.riotapimcpserver.spectator.domain.
com.wkaiser.riotapimcpserver.riot.lol.analytics.dto.PlayerMatchAnalytics
  -> com.wkaiser.riotapimcpserver.analytics.domain.PlayerMatchAnalytics
```

- [ ] **Step 1: Move the domain files and update their package declarations**

For each row in the move table: create the target directory, `git mv` the file, then edit line 1 (`package ...;`) to the new package. Example for one file (repeat for all 18):

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/account/domain
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/account/dto/RiotAccount.java \
       src/main/java/com/wkaiser/riotapimcpserver/account/domain/RiotAccount.java
```

Then in `account/domain/RiotAccount.java` change:
`package com.wkaiser.riotapimcpserver.riot.account.dto;` → `package com.wkaiser.riotapimcpserver.account.domain;`

Do the equivalent for every file in the table.

- [ ] **Step 2: Repoint all imports across the codebase**

Apply the FQN prefix mapping above to every `.java` file under `src/`. From Git Bash:

```bash
grep -rl 'com.wkaiser.riotapimcpserver.riot.account.dto.RiotAccount' src | \
  xargs sed -i 's#com\.wkaiser\.riotapimcpserver\.riot\.account\.dto\.RiotAccount#com.wkaiser.riotapimcpserver.account.domain.RiotAccount#g'

grep -rl 'com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner' src | \
  xargs sed -i 's#com\.wkaiser\.riotapimcpserver\.riot\.lol\.summoner\.dto\.Summoner#com.wkaiser.riotapimcpserver.summoner.domain.Summoner#g'

grep -rl 'com.wkaiser.riotapimcpserver.riot.lol.match.dto' src | \
  xargs sed -i 's#com\.wkaiser\.riotapimcpserver\.riot\.lol\.match\.dto#com.wkaiser.riotapimcpserver.match.domain#g'

grep -rl 'com.wkaiser.riotapimcpserver.riot.lol.spectator.dto' src | \
  xargs sed -i 's#com\.wkaiser\.riotapimcpserver\.riot\.lol\.spectator\.dto#com.wkaiser.riotapimcpserver.spectator.domain#g'

grep -rl 'com.wkaiser.riotapimcpserver.riot.lol.analytics.dto.PlayerMatchAnalytics' src | \
  xargs sed -i 's#com\.wkaiser\.riotapimcpserver\.riot\.lol\.analytics\.dto\.PlayerMatchAnalytics#com.wkaiser.riotapimcpserver.analytics.domain.PlayerMatchAnalytics#g'
```

(The `spectator.dto` replacement also fixes `CompilationVerificationTest`'s wildcard import `...spectator.dto.*` → `...spectator.domain.*`.)

- [ ] **Step 3: Verify no stale DTO package references remain**

Run:

```bash
grep -rn 'riot\.\(account\|lol\)\.[a-z]*\.dto' src || echo "clean"
```

Expected: `clean` (no matches).

- [ ] **Step 4: Verify the old dto directories are empty and removed**

Run:

```bash
find src/main/java/com/wkaiser/riotapimcpserver/riot -type d -name dto
```

Expected: no output (the `git mv` removed each `dto` directory once its last file moved). If any remain, they are empty leftover dirs — remove them.

- [ ] **Step 5: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (Active unit tests: `RiotApiPropertiesTest`, `RiotApiClientTest`, `SpectatorServiceTest`, `LiveGameToolTest`, `CompilationVerificationTest` — all pass. `@Disabled` integration tests compile but do not run.)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: relocate DTOs into bounded-context domain packages"
```

---

### Task 4: Account context — port, adapter, application service, tool

**Files:**
- Create: `account/application/port/RiotAccountPort.java`
- Create: `account/adapter/out/riot/RiotAccountRiotAdapter.java`
- Move + rewrite: `riot/account/service/RiotAccountService.java` → `account/application/RiotAccountService.java`
- Move + update imports: `riot/account/tool/RiotAccountTool.java` → `account/adapter/in/mcp/RiotAccountTool.java`
- Move + update imports: `riot/account/tool/RiotAccountToolTest.java` → `account/adapter/in/mcp/RiotAccountToolTest.java`

**Interfaces:**
- Consumes: `RiotApiClient.regional(RiotApiRegionUri)` (Task 2), `RiotApiProperties.getRegion()` (Task 1), `account.domain.RiotAccount` (Task 3).
- Produces:
  - `RiotAccountPort`: `RiotAccount getAccountByRiotId(String gameName, String tagLine)`, `RiotAccount getAccountByPuuid(String puuid)`.
  - `account.application.RiotAccountService`: same two method signatures (unchanged from today), now delegating to the port.

- [ ] **Step 1: Create the outbound port**

Create `src/main/java/com/wkaiser/riotapimcpserver/account/application/port/RiotAccountPort.java`:

```java
package com.wkaiser.riotapimcpserver.account.application.port;

import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;

/** Outbound port for retrieving Riot account data. */
public interface RiotAccountPort {

    RiotAccount getAccountByRiotId(String gameName, String tagLine);

    RiotAccount getAccountByPuuid(String puuid);
}
```

- [ ] **Step 2: Create the outbound adapter**

Create `src/main/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot/RiotAccountRiotAdapter.java`:

```java
package com.wkaiser.riotapimcpserver.account.adapter.out.riot;

import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Riot Account API adapter. Account endpoints are region-routed. */
@Component
@RequiredArgsConstructor
public class RiotAccountRiotAdapter implements RiotAccountPort {

    private final RiotApiClient riotApiClient;
    private final RiotApiProperties properties;

    @Override
    public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
        RestClient client = riotApiClient.regional(properties.getRegion());
        return client.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(RiotAccount.class);
    }

    @Override
    public RiotAccount getAccountByPuuid(String puuid) {
        RestClient client = riotApiClient.regional(properties.getRegion());
        return client.get()
                .uri("/riot/account/v1/accounts/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(RiotAccount.class);
    }
}
```

- [ ] **Step 3: Move and rewrite the application service**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/account/application
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/account/service/RiotAccountService.java \
       src/main/java/com/wkaiser/riotapimcpserver/account/application/RiotAccountService.java
```

Replace the entire contents of `account/application/RiotAccountService.java` with:

```java
package com.wkaiser.riotapimcpserver.account.application;

import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot account lookups. Orchestrates account retrieval
 * through the outbound {@link RiotAccountPort}; holds no HTTP concerns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiotAccountService {

    private final RiotAccountPort accountPort;

    public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
        log.info("Fetching account for Riot ID: {}#{}", gameName, tagLine);
        return accountPort.getAccountByRiotId(gameName, tagLine);
    }

    public RiotAccount getAccountByPuuid(String puuid) {
        log.info("Fetching account for PUUID: {}", puuid);
        return accountPort.getAccountByPuuid(puuid);
    }
}
```

- [ ] **Step 4: Move the tool and update its import**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/account/adapter/in/mcp
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/account/tool/RiotAccountTool.java \
       src/main/java/com/wkaiser/riotapimcpserver/account/adapter/in/mcp/RiotAccountTool.java
```

In `account/adapter/in/mcp/RiotAccountTool.java`:
- Change the package line to: `package com.wkaiser.riotapimcpserver.account.adapter.in.mcp;`
- Change the service import from `com.wkaiser.riotapimcpserver.riot.account.service.RiotAccountService` to `com.wkaiser.riotapimcpserver.account.application.RiotAccountService`.
- The `RiotAccount` import is already `com.wkaiser.riotapimcpserver.account.domain.RiotAccount` (from Task 3). Leave the class body unchanged.

- [ ] **Step 5: Move the (disabled) tool integration test and fix its package**

```bash
mkdir -p src/test/java/com/wkaiser/riotapimcpserver/account/adapter/in/mcp
git mv src/test/java/com/wkaiser/riotapimcpserver/riot/account/tool/RiotAccountToolTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/account/adapter/in/mcp/RiotAccountToolTest.java
```

In that test, change the package line to `package com.wkaiser.riotapimcpserver.account.adapter.in.mcp;`. The `RiotAccount` import already points to `account.domain` (Task 3). Leave `@Disabled` in place.

- [ ] **Step 6: Write a unit test for the application service**

Create `src/test/java/com/wkaiser/riotapimcpserver/account/application/RiotAccountServiceTest.java`:

```java
package com.wkaiser.riotapimcpserver.account.application;

import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiotAccountServiceTest {

    @Mock
    private RiotAccountPort accountPort;

    @InjectMocks
    private RiotAccountService accountService;

    @Test
    void getAccountByRiotId_delegatesToPort() {
        RiotAccount expected = RiotAccount.builder().puuid("p").gameName("Name").tagLine("NA1").build();
        when(accountPort.getAccountByRiotId("Name", "NA1")).thenReturn(expected);

        RiotAccount result = accountService.getAccountByRiotId("Name", "NA1");

        assertThat(result).isSameAs(expected);
        verify(accountPort).getAccountByRiotId("Name", "NA1");
    }

    @Test
    void getAccountByPuuid_delegatesToPort() {
        RiotAccount expected = RiotAccount.builder().puuid("p").build();
        when(accountPort.getAccountByPuuid("p")).thenReturn(expected);

        RiotAccount result = accountService.getAccountByPuuid("p");

        assertThat(result).isSameAs(expected);
        verify(accountPort).getAccountByPuuid("p");
    }
}
```

- [ ] **Step 7: Run the account tests and the build**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.account.*"`
Expected: PASS.

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: convert account context to ports and adapters"
```

---

### Task 5: Summoner context — port, adapter, application service, tool

**Files:**
- Create: `summoner/application/port/SummonerPort.java`
- Create: `summoner/adapter/out/riot/RiotSummonerAdapter.java`
- Move + rewrite: `riot/lol/summoner/service/SummonerService.java` → `summoner/application/SummonerService.java`
- Move + update imports: `riot/lol/summoner/tool/SummonerTool.java` → `summoner/adapter/in/mcp/SummonerTool.java`
- Test: `summoner/application/SummonerServiceTest.java`

**Interfaces:**
- Consumes: `RiotApiClient.platform(RiotApiPlatformUri)` (Task 2), `summoner.domain.Summoner` (Task 3).
- Produces:
  - `SummonerPort`: `Summoner getSummonerByName(RiotApiPlatformUri, String)`, `Summoner getSummonerByPuuid(RiotApiPlatformUri, String)`, `Summoner getSummonerById(RiotApiPlatformUri, String)`.
  - `summoner.application.SummonerService`: same three signatures (unchanged from today), delegating to the port.

> These signatures are relied on by `AnalyticsService` (Task 8) and `LiveGameTool` (Task 7).

- [ ] **Step 1: Create the outbound port**

Create `src/main/java/com/wkaiser/riotapimcpserver/summoner/application/port/SummonerPort.java`:

```java
package com.wkaiser.riotapimcpserver.summoner.application.port;

import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;

/** Outbound port for retrieving League of Legends summoner data. */
public interface SummonerPort {

    Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName);

    Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid);

    Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId);
}
```

- [ ] **Step 2: Create the outbound adapter**

Create `src/main/java/com/wkaiser/riotapimcpserver/summoner/adapter/out/riot/RiotSummonerAdapter.java`:

```java
package com.wkaiser.riotapimcpserver.summoner.adapter.out.riot;

import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Summoner-V4 API adapter. Summoner endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotSummonerAdapter implements SummonerPort {

    private final RiotApiClient riotApiClient;

    @Override
    public Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName) {
        return riotApiClient.platform(platform).get()
                .uri("/lol/summoner/v4/summoners/by-name/{summonerName}", summonerName)
                .retrieve()
                .body(Summoner.class);
    }

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return riotApiClient.platform(platform).get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(Summoner.class);
    }

    @Override
    public Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId) {
        return riotApiClient.platform(platform).get()
                .uri("/lol/summoner/v4/summoners/{summonerId}", summonerId)
                .retrieve()
                .body(Summoner.class);
    }
}
```

- [ ] **Step 3: Move and rewrite the application service**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/summoner/application
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/lol/summoner/service/SummonerService.java \
       src/main/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerService.java
```

Replace the entire contents of `summoner/application/SummonerService.java` with:

```java
package com.wkaiser.riotapimcpserver.summoner.application;

import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends summoner lookups. Delegates retrieval
 * to the outbound {@link SummonerPort}; holds no HTTP concerns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final SummonerPort summonerPort;

    public Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName) {
        log.info("Fetching summoner for name: {} on platform: {}", summonerName, platform);
        return summonerPort.getSummonerByName(platform, summonerName);
    }

    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching summoner for PUUID: {} on platform: {}", puuid, platform);
        return summonerPort.getSummonerByPuuid(platform, puuid);
    }

    public Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId) {
        log.info("Fetching summoner for ID: {} on platform: {}", summonerId, platform);
        return summonerPort.getSummonerById(platform, summonerId);
    }
}
```

- [ ] **Step 4: Move the tool and update its import**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/summoner/adapter/in/mcp
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/lol/summoner/tool/SummonerTool.java \
       src/main/java/com/wkaiser/riotapimcpserver/summoner/adapter/in/mcp/SummonerTool.java
```

In `summoner/adapter/in/mcp/SummonerTool.java`:
- Change the package line to `package com.wkaiser.riotapimcpserver.summoner.adapter.in.mcp;`
- Change the service import from `com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService` to `com.wkaiser.riotapimcpserver.summoner.application.SummonerService`.
- The `Summoner` and `RiotApiPlatformUri` imports are already correct. Leave the class body unchanged.

- [ ] **Step 5: Write a unit test for the application service**

Create `src/test/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerServiceTest.java`:

```java
package com.wkaiser.riotapimcpserver.summoner.application;

import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Mock
    private SummonerPort summonerPort;

    @InjectMocks
    private SummonerService summonerService;

    @Test
    void getSummonerByName_delegatesToPort() {
        Summoner expected = Summoner.builder().id("id").name("Name").build();
        when(summonerPort.getSummonerByName(PLATFORM, "Name")).thenReturn(expected);

        assertThat(summonerService.getSummonerByName(PLATFORM, "Name")).isSameAs(expected);
        verify(summonerPort).getSummonerByName(PLATFORM, "Name");
    }

    @Test
    void getSummonerByPuuid_delegatesToPort() {
        Summoner expected = Summoner.builder().puuid("p").build();
        when(summonerPort.getSummonerByPuuid(PLATFORM, "p")).thenReturn(expected);

        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "p")).isSameAs(expected);
        verify(summonerPort).getSummonerByPuuid(PLATFORM, "p");
    }

    @Test
    void getSummonerById_delegatesToPort() {
        Summoner expected = Summoner.builder().id("id").build();
        when(summonerPort.getSummonerById(PLATFORM, "id")).thenReturn(expected);

        assertThat(summonerService.getSummonerById(PLATFORM, "id")).isSameAs(expected);
        verify(summonerPort).getSummonerById(PLATFORM, "id");
    }
}
```

- [ ] **Step 6: Run the summoner tests and the build**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.summoner.*"`
Expected: PASS.

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: convert summoner context to ports and adapters"
```

---

### Task 6: Match context — port, adapter, application service

Match has no MCP tool (it is consumed only by analytics). Note: the old `MatchService` built its own region client **without** the API key header — routing through `RiotApiClient` now correctly authenticates match requests.

**Files:**
- Create: `match/application/port/MatchPort.java`
- Create: `match/adapter/out/riot/RiotMatchAdapter.java`
- Move + rewrite: `riot/lol/match/service/MatchService.java` → `match/application/MatchService.java`
- Test: `match/application/MatchServiceTest.java`

**Interfaces:**
- Consumes: `RiotApiClient.regional(RiotApiRegionUri)` (Task 2), `match.domain.Match` (Task 3).
- Produces:
  - `MatchPort`: `List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue)`, `Match getMatchById(RiotApiRegionUri region, String matchId)`.
  - `match.application.MatchService`: same two signatures (unchanged from today), delegating to the port.

> These signatures are relied on by `AnalyticsService` (Task 8).

- [ ] **Step 1: Create the outbound port**

Create `src/main/java/com/wkaiser/riotapimcpserver/match/application/port/MatchPort.java`:

```java
package com.wkaiser.riotapimcpserver.match.application.port;

import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;

import java.util.List;

/** Outbound port for retrieving League of Legends match data. */
public interface MatchPort {

    List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue);

    Match getMatchById(RiotApiRegionUri region, String matchId);
}
```

- [ ] **Step 2: Create the outbound adapter**

Create `src/main/java/com/wkaiser/riotapimcpserver/match/adapter/out/riot/RiotMatchAdapter.java`. This preserves the exact query-parameter assembly from the old `MatchService`:

```java
package com.wkaiser.riotapimcpserver.match.adapter.out.riot;

import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Riot Match-V5 API adapter. Match endpoints are region-routed. */
@Component
@RequiredArgsConstructor
public class RiotMatchAdapter implements MatchPort {

    private final RiotApiClient riotApiClient;

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        RestClient client = riotApiClient.regional(region);

        String uri = "/lol/match/v5/matches/by-puuid/{puuid}/ids?";
        if (count != null) {
            uri += "count=" + Math.min(count, 100) + "&";
        }
        if (start != null) {
            uri += "start=" + start + "&";
        }
        if (queue != null) {
            uri += "queue=" + queue;
        }
        if (uri.endsWith("&") || uri.endsWith("?")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        return client.get()
                .uri(uri, puuid)
                .retrieve()
                .body(List.class);
    }

    @Override
    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        return riotApiClient.regional(region).get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(Match.class);
    }
}
```

- [ ] **Step 3: Move and rewrite the application service**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/match/application
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/lol/match/service/MatchService.java \
       src/main/java/com/wkaiser/riotapimcpserver/match/application/MatchService.java
```

Replace the entire contents of `match/application/MatchService.java` with:

```java
package com.wkaiser.riotapimcpserver.match.application;

import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for League of Legends match data. Delegates retrieval to the
 * outbound {@link MatchPort}; holds no HTTP concerns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPort matchPort;

    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        log.info("Fetching match IDs for PUUID: {}", puuid);
        return matchPort.getMatchIdsByPuuid(region, puuid, count, start, queue);
    }

    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        log.info("Fetching match details for match ID: {}", matchId);
        return matchPort.getMatchById(region, matchId);
    }
}
```

- [ ] **Step 4: Write a unit test for the application service**

Create `src/test/java/com/wkaiser/riotapimcpserver/match/application/MatchServiceTest.java`:

```java
package com.wkaiser.riotapimcpserver.match.application;

import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    @Mock
    private MatchPort matchPort;

    @InjectMocks
    private MatchService matchService;

    @Test
    void getMatchIdsByPuuid_delegatesToPort() {
        List<String> ids = List.of("NA1_1", "NA1_2");
        when(matchPort.getMatchIdsByPuuid(REGION, "p", 20, 0, null)).thenReturn(ids);

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 20, 0, null)).isEqualTo(ids);
        verify(matchPort).getMatchIdsByPuuid(REGION, "p", 20, 0, null);
    }

    @Test
    void getMatchById_delegatesToPort() {
        Match expected = Match.builder().build();
        when(matchPort.getMatchById(REGION, "NA1_1")).thenReturn(expected);

        assertThat(matchService.getMatchById(REGION, "NA1_1")).isSameAs(expected);
        verify(matchPort).getMatchById(REGION, "NA1_1");
    }
}
```

- [ ] **Step 5: Run the match tests and the build**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.match.*"`
Expected: PASS.

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: convert match context to ports and adapters (fixes missing auth header)"
```

---

### Task 7: Spectator context — port, adapter, application service, tool, test cleanup

The old `SpectatorService` handled `404 → null` (summoner not in game) by catching `HttpClientErrorException`. Because `RiotApiClient` now throws `RiotApiException` for all non-2xx responses, the adapter catches `RiotApiException` and returns `null` on status 404.

The old `SpectatorServiceTest` is coupled to the removed `new SpectatorService(RestClient)` constructor and the `@Value apiKey` field, and holds the sample-data builders that `LiveGameToolTest` reuses. Those builders move to a new `SpectatorTestFixtures`; the brittle test is deleted (its real coverage returns as WireMock adapter tests in Phase 2).

**Files:**
- Create: `spectator/application/port/SpectatorPort.java`
- Create: `spectator/adapter/out/riot/RiotSpectatorAdapter.java`
- Move + rewrite: `riot/lol/spectator/service/SpectatorService.java` → `spectator/application/SpectatorService.java`
- Move + update imports: `riot/lol/spectator/tool/LiveGameTool.java` → `spectator/adapter/in/mcp/LiveGameTool.java`
- Create: `src/test/.../spectator/SpectatorTestFixtures.java`
- Delete: `riot/lol/spectator/service/SpectatorServiceTest.java`
- Move + update: `riot/lol/spectator/tool/LiveGameToolTest.java` → `spectator/adapter/in/mcp/LiveGameToolTest.java`
- Move + update: `riot/lol/spectator/service/SpectatorServiceIntegrationTest.java` → `spectator/adapter/out/riot/SpectatorServiceIntegrationTest.java` (kept `@Disabled`)
- Move + update: `riot/lol/spectator/tool/LiveGameToolIntegrationTest.java` → `spectator/adapter/in/mcp/LiveGameToolIntegrationTest.java` (kept `@Disabled`)

**Interfaces:**
- Consumes: `RiotApiClient.platform(RiotApiPlatformUri)` (Task 2), `RiotApiException.getStatusCode()`, `spectator.domain.{CurrentGameInfo,FeaturedGames}` (Task 3), `summoner.application.SummonerService` (Task 5).
- Produces:
  - `SpectatorPort`: `CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId)` (returns `null` if not in game), `FeaturedGames getFeaturedGames(RiotApiPlatformUri platform)`.
  - `spectator.application.SpectatorService`: same two signatures (unchanged from today), delegating to the port.
  - `SpectatorTestFixtures` (test scope): `static CurrentGameInfo createSampleCurrentGameInfo()`, `static CurrentGameParticipant createSampleParticipant(String, long, long)`, `static FeaturedGames createSampleFeaturedGames()`.

- [ ] **Step 1: Create the outbound port**

Create `src/main/java/com/wkaiser/riotapimcpserver/spectator/application/port/SpectatorPort.java`:

```java
package com.wkaiser.riotapimcpserver.spectator.application.port;

import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;

/** Outbound port for retrieving League of Legends live-game (spectator) data. */
public interface SpectatorPort {

    /** Returns the current game, or {@code null} if the summoner is not in a game. */
    CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId);

    FeaturedGames getFeaturedGames(RiotApiPlatformUri platform);
}
```

- [ ] **Step 2: Create the outbound adapter**

Create `src/main/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot/RiotSpectatorAdapter.java`:

```java
package com.wkaiser.riotapimcpserver.spectator.adapter.out.riot;

import com.wkaiser.riotapimcpserver.spectator.application.port.SpectatorPort;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Riot Spectator-V4 API adapter. Spectator endpoints are platform-routed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotSpectatorAdapter implements SpectatorPort {

    private static final int NOT_FOUND = 404;

    private final RiotApiClient riotApiClient;

    @Override
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId) {
        try {
            return riotApiClient.platform(platform).get()
                    .uri("/lol/spectator/v4/active-games/by-summoner/{encryptedSummonerId}", encryptedSummonerId)
                    .retrieve()
                    .body(CurrentGameInfo.class);
        } catch (RiotApiException e) {
            if (e.getStatusCode() == NOT_FOUND) {
                log.debug("Summoner {} is not currently in a game (404)", encryptedSummonerId);
                return null;
            }
            throw e;
        }
    }

    @Override
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        return riotApiClient.platform(platform).get()
                .uri("/lol/spectator/v4/featured-games")
                .retrieve()
                .body(FeaturedGames.class);
    }
}
```

- [ ] **Step 3: Move and rewrite the application service**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/spectator/application
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/service/SpectatorService.java \
       src/main/java/com/wkaiser/riotapimcpserver/spectator/application/SpectatorService.java
```

Replace the entire contents of `spectator/application/SpectatorService.java` with:

```java
package com.wkaiser.riotapimcpserver.spectator.application;

import com.wkaiser.riotapimcpserver.spectator.application.port.SpectatorPort;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends live-game data. Delegates retrieval to the
 * outbound {@link SpectatorPort}; holds no HTTP concerns. Returns {@code null} from
 * {@link #getCurrentGameInfo} when the summoner is not currently in a game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectatorService {

    private final SpectatorPort spectatorPort;

    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId) {
        log.info("Fetching current game info for summoner ID: {} on platform: {}", encryptedSummonerId, platform);
        return spectatorPort.getCurrentGameInfo(platform, encryptedSummonerId);
    }

    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        log.info("Fetching featured games for platform: {}", platform);
        return spectatorPort.getFeaturedGames(platform);
    }
}
```

- [ ] **Step 4: Move the tool and update its imports**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/spectator/adapter/in/mcp
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/tool/LiveGameTool.java \
       src/main/java/com/wkaiser/riotapimcpserver/spectator/adapter/in/mcp/LiveGameTool.java
```

In `spectator/adapter/in/mcp/LiveGameTool.java`:
- Change the package line to `package com.wkaiser.riotapimcpserver.spectator.adapter.in.mcp;`
- Change the `SpectatorService` import to `com.wkaiser.riotapimcpserver.spectator.application.SpectatorService`.
- Change the `SummonerService` import to `com.wkaiser.riotapimcpserver.summoner.application.SummonerService`.
- The `CurrentGameInfo`, `FeaturedGames`, `Summoner`, and `RiotApiPlatformUri` imports already point to their new locations (Task 3). Leave the class body unchanged.

- [ ] **Step 5: Create `SpectatorTestFixtures`**

Create `src/test/java/com/wkaiser/riotapimcpserver/spectator/SpectatorTestFixtures.java` (the builders lifted verbatim from the old `SpectatorServiceTest`, now with domain imports):

```java
package com.wkaiser.riotapimcpserver.spectator;

import com.wkaiser.riotapimcpserver.spectator.domain.BannedChampion;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameParticipant;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import com.wkaiser.riotapimcpserver.spectator.domain.Observer;
import com.wkaiser.riotapimcpserver.spectator.domain.Perks;

import java.util.List;

/** Shared sample-data builders for spectator tests. */
public final class SpectatorTestFixtures {

    private SpectatorTestFixtures() {
    }

    public static CurrentGameInfo createSampleCurrentGameInfo() {
        return CurrentGameInfo.builder()
                .gameId(123456789L)
                .gameType("MATCHED_GAME")
                .gameStartTime(1640995200000L)
                .mapId(11L)
                .gameLength(450L)
                .platformId("NA1")
                .gameMode("CLASSIC")
                .gameQueueConfigId(420L)
                .bannedChampions(List.of(
                        BannedChampion.builder().championId(266L).teamId(100L).pickTurn(1).build()))
                .observers(Observer.builder().encryptionKey("sample_encryption_key").build())
                .participants(List.of(
                        createSampleParticipant("TestSummoner1", 1L, 100L),
                        createSampleParticipant("TestSummoner2", 2L, 200L)))
                .build();
    }

    public static CurrentGameParticipant createSampleParticipant(String summonerName, long championId, long teamId) {
        return CurrentGameParticipant.builder()
                .championId(championId)
                .perks(Perks.builder()
                        .perkIds(List.of(8112L, 8126L, 8138L, 8106L, 8275L, 8210L))
                        .perkStyle(8100L)
                        .perkSubStyle(8200L)
                        .build())
                .profileIconId(1234L)
                .bot(false)
                .teamId(teamId)
                .summonerName(summonerName)
                .summonerId("encrypted_summoner_id_" + summonerName)
                .puuid("test_puuid_" + summonerName)
                .summonerLevel(150L)
                .spell1Id(4L)
                .spell2Id(7L)
                .gameCustomizationObjects(List.of())
                .build();
    }

    public static FeaturedGames createSampleFeaturedGames() {
        return FeaturedGames.builder()
                .clientRefreshInterval(300L)
                .gameList(List.of(
                        CurrentGameInfo.builder()
                                .gameId(987654321L)
                                .gameStartTime(1640995200000L)
                                .platformId("NA1")
                                .gameMode("CLASSIC")
                                .mapId(11L)
                                .gameType("MATCHED_GAME")
                                .gameQueueConfigId(420L)
                                .gameLength(600L)
                                .participants(List.of(
                                        createSampleParticipant("FeaturedPlayer1", 1L, 100L),
                                        createSampleParticipant("FeaturedPlayer2", 2L, 200L)))
                                .bannedChampions(List.of(
                                        BannedChampion.builder().championId(266L).teamId(100L).pickTurn(1).build()))
                                .observers(Observer.builder().encryptionKey("featured_encryption_key").build())
                                .build()))
                .build();
    }
}
```

- [ ] **Step 6: Delete the brittle SpectatorServiceTest**

```bash
git rm src/test/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/service/SpectatorServiceTest.java
```

- [ ] **Step 7: Move and rewire `LiveGameToolTest`**

```bash
mkdir -p src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/in/mcp
git mv src/test/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/tool/LiveGameToolTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/in/mcp/LiveGameToolTest.java
```

In `spectator/adapter/in/mcp/LiveGameToolTest.java` make these edits:
- Package line → `package com.wkaiser.riotapimcpserver.spectator.adapter.in.mcp;`
- Update imports:
  - `SpectatorService` → `com.wkaiser.riotapimcpserver.spectator.application.SpectatorService`
  - `SummonerService` → `com.wkaiser.riotapimcpserver.summoner.application.SummonerService`
  - Remove the import `com.wkaiser.riotapimcpserver.riot.lol.spectator.service.SpectatorServiceTest`.
  - (`CurrentGameInfo`, `CurrentGameParticipant`, `FeaturedGames`, `Summoner` already point to their domain packages from Task 3.)
- Replace the three private helper methods at the bottom (`createSampleCurrentGameInfo`, `createSampleParticipant`, `createSampleFeaturedGames`, which delegate to `SpectatorServiceTest.*`) by deleting them and instead calling `SpectatorTestFixtures` directly. Concretely:
  - Add import `com.wkaiser.riotapimcpserver.spectator.SpectatorTestFixtures;`
  - Delete the three private helper methods.
  - Replace the two `createSampleCurrentGameInfo()` call sites and the `createSampleFeaturedGames()` call site with `SpectatorTestFixtures.createSampleCurrentGameInfo()` and `SpectatorTestFixtures.createSampleFeaturedGames()`. (The `createSampleParticipant` helper is unused in this file; deleting it is fine.)

- [ ] **Step 8: Move and fix the two disabled integration tests**

```bash
mkdir -p src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot
git mv src/test/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/service/SpectatorServiceIntegrationTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot/SpectatorServiceIntegrationTest.java
git mv src/test/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/tool/LiveGameToolIntegrationTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/in/mcp/LiveGameToolIntegrationTest.java
```

In `SpectatorServiceIntegrationTest.java`:
- Package line → `package com.wkaiser.riotapimcpserver.spectator.adapter.out.riot;`
- Add import `com.wkaiser.riotapimcpserver.spectator.application.SpectatorService;`
- (`CurrentGameInfo`, `FeaturedGames` already point to `spectator.domain`.) Keep `@Disabled`.

In `LiveGameToolIntegrationTest.java`:
- Package line → `package com.wkaiser.riotapimcpserver.spectator.adapter.in.mcp;`
- Add import `com.wkaiser.riotapimcpserver.spectator.adapter.in.mcp.LiveGameTool;` is unnecessary (same package now) — instead ensure there is no stale import of the old `riot.lol.spectator.tool.LiveGameTool`. (`CurrentGameInfo`, `FeaturedGames` already point to `spectator.domain`.) Keep `@Disabled`.

- [ ] **Step 9: Run the spectator tests and the build**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.spectator.*"`
Expected: PASS (`LiveGameToolTest` runs and passes; integration tests are skipped).

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: convert spectator context to ports and adapters; extract test fixtures"
```

---

### Task 8: Analytics context — application service and tool

`analytics` is a composing context: it has no outbound Riot adapter. Its service depends on the `account`, `summoner`, and `match` application services (migrated in Tasks 4–6). The analytics logic is unchanged; a real unit test is added to protect it during the move.

**Files:**
- Move + rewrite imports: `riot/lol/analytics/service/AnalyticsService.java` → `analytics/application/AnalyticsService.java`
- Move + update imports: `riot/lol/analytics/tool/AnalyticsTool.java` → `analytics/adapter/in/mcp/AnalyticsTool.java`
- Move + update imports: `riot/lol/analytics/tool/AnalyticsToolTest.java` → `analytics/adapter/in/mcp/AnalyticsToolTest.java` (kept `@Disabled`)
- Create: `analytics/application/AnalyticsServiceTest.java`

**Interfaces:**
- Consumes: `RiotAccountService` (Task 4), `SummonerService` (Task 5), `MatchService` (Task 6), domain types `RiotAccount`, `Summoner`, `Match`, `Participant`, `PlayerMatchAnalytics` (Task 3).
- Produces: `analytics.application.AnalyticsService` with `PlayerMatchAnalytics getPlayerMatchAnalytics(String riotId, RiotApiPlatformUri platform, RiotApiRegionUri region, int matchCount)` (unchanged signature).

- [ ] **Step 1: Move the service and update its package + collaborator imports**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/analytics/application
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/lol/analytics/service/AnalyticsService.java \
       src/main/java/com/wkaiser/riotapimcpserver/analytics/application/AnalyticsService.java
```

In `analytics/application/AnalyticsService.java`, change **only** the package line and the import block; the method bodies are unchanged. Set the package to:

```java
package com.wkaiser.riotapimcpserver.analytics.application;
```

and ensure the import block reads exactly (replacing the old `riot.*` service/dto imports):

```java
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import com.wkaiser.riotapimcpserver.account.application.RiotAccountService;
import com.wkaiser.riotapimcpserver.analytics.domain.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.match.domain.Participant;
import com.wkaiser.riotapimcpserver.match.application.MatchService;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.summoner.application.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
```

(Note: the old file imported `java.util.Map` but never used it — omit it. Everything below the imports, from `@Slf4j` onward, stays byte-for-byte as it was.)

- [ ] **Step 2: Move the tool and update its imports**

```bash
mkdir -p src/main/java/com/wkaiser/riotapimcpserver/analytics/adapter/in/mcp
git mv src/main/java/com/wkaiser/riotapimcpserver/riot/lol/analytics/tool/AnalyticsTool.java \
       src/main/java/com/wkaiser/riotapimcpserver/analytics/adapter/in/mcp/AnalyticsTool.java
```

In `analytics/adapter/in/mcp/AnalyticsTool.java`:
- Package line → `package com.wkaiser.riotapimcpserver.analytics.adapter.in.mcp;`
- Change the `AnalyticsService` import to `com.wkaiser.riotapimcpserver.analytics.application.AnalyticsService`.
- The `PlayerMatchAnalytics` import already points to `analytics.domain` (Task 3); the enum imports are unchanged. Leave the class body unchanged.

- [ ] **Step 3: Move the disabled tool integration test**

```bash
mkdir -p src/test/java/com/wkaiser/riotapimcpserver/analytics/adapter/in/mcp
git mv src/test/java/com/wkaiser/riotapimcpserver/riot/lol/analytics/tool/AnalyticsToolTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/analytics/adapter/in/mcp/AnalyticsToolTest.java
```

In that test, set the package line to `package com.wkaiser.riotapimcpserver.analytics.adapter.in.mcp;`. (`PlayerMatchAnalytics` already points to `analytics.domain`.) Keep `@Disabled`.

- [ ] **Step 4: Write a unit test for the analytics logic**

Create `src/test/java/com/wkaiser/riotapimcpserver/analytics/application/AnalyticsServiceTest.java`:

```java
package com.wkaiser.riotapimcpserver.analytics.application;

import com.wkaiser.riotapimcpserver.account.application.RiotAccountService;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import com.wkaiser.riotapimcpserver.analytics.domain.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.match.application.MatchService;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.match.domain.MatchInfo;
import com.wkaiser.riotapimcpserver.match.domain.Participant;
import com.wkaiser.riotapimcpserver.summoner.application.SummonerService;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "puuid-1";

    @Mock
    private RiotAccountService accountService;
    @Mock
    private SummonerService summonerService;
    @Mock
    private MatchService matchService;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void returns_empty_analytics_when_no_matches() {
        when(accountService.getAccountByRiotId("Player", "NA1"))
                .thenReturn(RiotAccount.builder().puuid(PUUID).gameName("Player").tagLine("NA1").build());
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().name("Player").summonerLevel(100).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), eq(0), any()))
                .thenReturn(List.of());

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 5);

        assertThat(result.getMatchCount()).isZero();
        assertThat(result.getSummonerName()).isEqualTo("Player");
    }

    @Test
    void computes_win_rate_and_kda_over_matches() {
        when(accountService.getAccountByRiotId("Player", "NA1"))
                .thenReturn(RiotAccount.builder().puuid(PUUID).gameName("Player").tagLine("NA1").build());
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().name("Player").summonerLevel(100).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), eq(0), any()))
                .thenReturn(List.of("NA1_1", "NA1_2"));
        when(matchService.getMatchById(REGION, "NA1_1")).thenReturn(match(true, 10, 2, 5));
        when(matchService.getMatchById(REGION, "NA1_2")).thenReturn(match(false, 4, 6, 3));

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 2);

        assertThat(result.getMatchCount()).isEqualTo(2);
        assertThat(result.getWins()).isEqualTo(1);
        assertThat(result.getLosses()).isEqualTo(1);
        assertThat(result.getWinRate()).isEqualTo("50.00%");
        assertThat(result.getAvgKills()).isEqualTo("7.00");
    }

    private Match match(boolean win, int kills, int deaths, int assists) {
        Participant p = Participant.builder()
                .puuid(PUUID)
                .win(win)
                .kills(kills)
                .deaths(deaths)
                .assists(assists)
                .championName("Ahri")
                .teamPosition("MIDDLE")
                .visionScore(20)
                .totalMinionsKilled(150)
                .neutralMinionsKilled(10)
                .build();
        MatchInfo info = MatchInfo.builder()
                .gameDuration(1800L)
                .participants(List.of(p))
                .build();
        return Match.builder().info(info).build();
    }
}
```

> **Before running:** open `match/domain/Participant.java`, `match/domain/MatchInfo.java`, and `match/domain/Match.java` to confirm the builder field names used above (`win`, `kills`, `deaths`, `assists`, `championName`, `teamPosition`, `visionScore`, `totalMinionsKilled`, `neutralMinionsKilled`, `gameDuration`, `participants`, `info`). If any differ, adjust the test to match the actual field names — do not change the DTOs.

- [ ] **Step 5: Run the analytics tests and the build**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.analytics.*"`
Expected: PASS (2 tests in `AnalyticsServiceTest`; `AnalyticsToolTest` skipped).

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: relocate analytics context and add analytics unit test"
```

---

### Task 9: Remove the obsolete RestClient bean and finalize configuration

With every context migrated to `RiotApiClient`, the old `riotRestClient` bean is unused. Remove it, leaving `RiotApiConfiguration` as a pure properties-enabler.

**Files:**
- Modify: `src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiConfiguration.java`

**Interfaces:** none produced. This task only removes dead code.

- [ ] **Step 1: Confirm the bean is unused**

Run:

```bash
grep -rn 'riotRestClient' src || echo "no references"
```

Expected: matches appear **only** inside `RiotApiConfiguration.java` (the bean definition). If any other file references `riotRestClient`, stop — that context was not fully migrated; fix it before continuing.

- [ ] **Step 2: Replace `RiotApiConfiguration` with the properties-enabler only**

Replace the entire contents of `src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiConfiguration.java` with:

```java
package com.wkaiser.riotapimcpserver.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables typed Riot API configuration. All Riot HTTP client construction now lives in
 * {@link com.wkaiser.riotapimcpserver.shared.http.RiotApiClient}.
 */
@Configuration
@EnableConfigurationProperties(RiotApiProperties.class)
public class RiotApiConfiguration {
}
```

- [ ] **Step 3: Confirm the old empty package tree is gone**

Run:

```bash
find src/main/java/com/wkaiser/riotapimcpserver/riot -type f 2>/dev/null || echo "riot package removed"
```

Expected: no files listed (ideally `riot package removed`). If empty directories linger, remove them.

- [ ] **Step 4: Run the full build and confirm the whole suite is green**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. Confirm the test output includes `RiotApiPropertiesTest`, `RiotApiClientTest`, `RiotAccountServiceTest`, `SummonerServiceTest`, `MatchServiceTest`, `LiveGameToolTest`, `AnalyticsServiceTest`, and `CompilationVerificationTest`.

- [ ] **Step 5: Sanity-check the new structure**

Run:

```bash
find src/main/java/com/wkaiser/riotapimcpserver -type d | sort
```

Expected: top-level `account`, `summoner`, `match`, `spectator`, `analytics`, `shared`, each with the appropriate `domain` / `application` / `application/port` / `adapter/in/mcp` / `adapter/out/riot` subfolders (analytics has no `adapter/out`, match has no `adapter/in`).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove obsolete riotRestClient bean; finalize RiotApiConfiguration"
```

---

## Notes for Phase 2 (not in scope here)

- Real per-adapter WireMock tests (`RiotAccountRiotAdapter`, `RiotSummonerAdapter`, `RiotMatchAdapter`, `RiotSpectatorAdapter`) using `baseUrlOverride`, asserting URLs, the `X-RIOT-TOKEN` header, JSON parsing, and error mapping (including spectator `404 → null`).
- Replace the Mockito port mocks in the application-service tests with hand-written in-memory port fakes (per the spec's testing philosophy), if desired.
- Removal of `CompilationVerificationTest` and the remaining `@Disabled` integration tests, superseded by WireMock coverage.

## Self-Review

- **Spec coverage (Phase 1 slice):** typed config (`RiotApiProperties`, Task 1) ✓; shared HTTP client eliminating duplication (`RiotApiClient`, Task 2) ✓; bounded-context hexagons with domain/application/port/adapters for all five contexts (Tasks 3–8) ✓; `analytics` composes application services with no outbound adapter (Task 8) ✓; `12-factor` key via `RIOT_API_KEY` preserved ✓; behavior preserved with the noted match-auth fix ✓; obsolete bean removed (Task 9) ✓. Deferred-by-design to Phase 2: WireMock adapter tests, hand-written fakes, deletion of `CompilationVerificationTest`/`@Disabled` tests (documented above).
- **Placeholder scan:** none — every step shows full code or exact edits/commands.
- **Type consistency:** port method signatures in Tasks 4–7 match the application-service signatures that Task 8's `AnalyticsService` and Task 7's `LiveGameTool` consume; `RiotApiClient.regional/platform` (Task 2) are the only client entry points used by every adapter; `RiotApiProperties` accessors (Task 1) are used consistently in Task 2 and the account adapter (Task 4). The `AnalyticsServiceTest` includes an explicit field-name verification step because those DTO builder names were not all read during planning.
