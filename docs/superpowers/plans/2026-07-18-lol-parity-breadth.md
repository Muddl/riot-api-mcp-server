# LoL Parity — Breadth (Sub-project 1b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add five new LoL bounded contexts (`champion`, `status`, `championmastery`, `challenges`, `clash`) and the existing `match` context's first inbound MCP tools, growing the tool surface from 6 to 13, built mechanically against the `league` template with no change to either shared library.

**Architecture:** Each context is a full mini-hexagon under `com.muddl.riot.lol.<context>`: a Lombok `domain/` DTO, an `application/port/` interface, an `application/` service, an `adapter/out/riot/` Riot adapter, and an `adapter/in/mcp/` tool. Player-keyed services inject `PlayerIdentityResolver` and resolve a single `player` param to a PUUID (the `league` shape); non-player-keyed contexts (`champion` rotation, `status`, and `match`'s `by_id` tool) take only domain params and never touch the resolver (ADR-0014, created here). The `match` context already has a domain/service/port/adapter — this plan adds the resolver + a player-resolving method to `MatchService` and a new `MatchTool`, leaving `analytics`' existing PUUID path untouched.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring AI 2.0 (`@McpTool`), Gradle, Lombok, Jackson, JUnit 5, AssertJ, Mockito, WireMock.

## Global Constraints

- **No modification to `riot-api-core` or `riot-account-core`.** This is 1b's falsifiable success criterion. If a task appears to need a library change, stop and record it as a finding rather than making the change.
- **The full test suite runs offline with no Riot API key.** WireMock for outbound adapters, hand-written in-memory port fakes for services. No live keys, no network, no real sleeps.
- **DTOs** carry `@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)` and no framework imports beyond Jackson/Lombok. Nested builder classes also need `@NoArgsConstructor @AllArgsConstructor` (see `docs/knowledge/gotchas.md`).
- **Tool names** are `lol_<context>_<action>`, snake_case, stable. The single `player` param accepts a Riot ID (`GameName#TAG`) or a raw PUUID and is resolved in the **service** via `PlayerIdentityResolver`. Non-player-keyed tools take only domain params.
- **Routing:** platform-routed endpoints use `riotApiClient.platform(platform)`; region-routed use `riotApiClient.regional(region)`. `RiotApiClient` already handles the `X-RIOT-TOKEN` header, base URL, and non-2xx → `RiotApiException` mapping — never re-implement these in an adapter.
- **Endpoint paths are verified against the live Riot developer portal**, never assumed. Each adapter task states the documented path; confirm it on the portal before trusting it. The live eval harness catches a wrong path post-merge (404), but the portal is the source of truth.
- **TDD, green at every commit.** Failing test first, minimal implementation, green, commit. Every task that adds a tool also updates `McpToolInventoryTest` in the same commit, or the build goes red.
- **`lol-mcp-server` version is `0.2.0` for this cycle** (additive minor, pre-1.0). The `verifyRelease` gate (in `check`) fails unless `lol-mcp-server/CHANGELOG.md` has a `## [0.2.0]` heading — Task 1 creates it so the gate stays green while later tasks accumulate `### Added` bullets under it.

**Build/test commands:**
- Whole gate: `./gradlew build`
- One module's tests: `./gradlew :lol-mcp-server:test`
- One test class: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.champion.adapter.out.riot.RiotChampionAdapterTest'`
- Format: `./gradlew spotlessApply`

---

### Task 1: Version bump + changelog heading scaffold

Bumps `lol-mcp-server` to `0.2.0` and opens the `## [0.2.0]` changelog section so `verifyRelease` passes for every subsequent task while entries accumulate under it.

**Files:**
- Modify: `lol-mcp-server/build.gradle:8`
- Modify: `lol-mcp-server/CHANGELOG.md:9`

**Interfaces:**
- Consumes: nothing.
- Produces: the `## [0.2.0]` heading later tasks append `### Added` bullets to.

- [ ] **Step 1: Bump the module version**

In `lol-mcp-server/build.gradle`, change line 8 from:

```groovy
version = '0.1.0'
```

to:

```groovy
version = '0.2.0'
```

- [ ] **Step 2: Run the release gate and watch it fail**

Run: `./gradlew :lol-mcp-server:verifyRelease`
Expected: FAIL — `version is 0.2.0 but CHANGELOG.md has no '## [0.2.0]' heading.`

- [ ] **Step 3: Add the 0.2.0 changelog heading**

In `lol-mcp-server/CHANGELOG.md`, insert immediately after line 8 (the blurb ending `versioning: ... (breaking → minor).`) and before the existing `## [0.1.0] - unreleased`:

```markdown
## [0.2.0] - unreleased

Sub-project 1b — LoL parity: breadth. Five new contexts plus the match context's first inbound
tools, built against the `league` template. See
[the 1b spec](../docs/superpowers/specs/2026-07-18-lol-parity-breadth-design.md).

### Added

```

- [ ] **Step 4: Run the release gate and watch it pass**

Run: `./gradlew :lol-mcp-server:verifyRelease`
Expected: PASS (no output / `BUILD SUCCESSFUL`).

- [ ] **Step 5: Commit**

```bash
git add lol-mcp-server/build.gradle lol-mcp-server/CHANGELOG.md
git commit -m "chore(lol): open 0.2.0 for sub-project 1b breadth work"
```

---

### Task 2: `champion` context — free-to-play rotation (non-player exemplar)

The simplest possible context: platform-routed, no player, no resolver. It is the worked reference for the non-player-keyed variant of the template.

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/champion/domain/ChampionRotation.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/champion/application/port/ChampionPort.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/champion/application/ChampionService.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/champion/adapter/out/riot/RiotChampionAdapter.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/champion/adapter/in/mcp/ChampionTool.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/champion/adapter/out/riot/RiotChampionAdapterTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/champion/application/InMemoryChampionPort.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/champion/application/ChampionServiceTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/champion/adapter/in/mcp/ChampionToolTest.java`
- Create: `lol-mcp-server/src/test/resources/fixtures/champion-rotation.json`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`
- Modify: `lol-mcp-server/CHANGELOG.md`

**Interfaces:**
- Consumes: `RiotApiClient.platform(RiotApiPlatformUri)`, `RiotApiPlatformUri` enum.
- Produces: `ChampionService.getChampionRotation(RiotApiPlatformUri) → ChampionRotation`; tool `lol_champion_rotation`.

- [ ] **Step 1: Write the adapter's failing WireMock test**

Create `RiotChampionAdapterTest.java`:

```java
package com.muddl.riot.lol.champion.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotChampionAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String ROTATION_URL = "/lol/platform/v3/champion-rotations";

    private WireMockServer wireMock;
    private ChampionPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotChampionAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getChampionRotation_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo(ROTATION_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("champion-rotation.json"))));

        ChampionRotation rotation = adapter.getChampionRotation(PLATFORM);

        assertThat(rotation.getMaxNewPlayerLevel()).isEqualTo(10);
        assertThat(rotation.getFreeChampionIds()).containsExactly(1, 15, 22);
        assertThat(rotation.getFreeChampionIdsForNewPlayers()).containsExactly(18, 81, 92);
        verify(getRequestedFor(urlEqualTo(ROTATION_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(ROTATION_URL)).willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> adapter.getChampionRotation(PLATFORM))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(403);
    }
}
```

- [ ] **Step 2: Create the fixture**

Create `lol-mcp-server/src/test/resources/fixtures/champion-rotation.json`:

```json
{
  "freeChampionIds": [1, 15, 22],
  "freeChampionIdsForNewPlayers": [18, 81, 92],
  "maxNewPlayerLevel": 10
}
```

- [ ] **Step 3: Create the domain DTO**

The endpoint (verify on the portal) is `GET /lol/platform/v3/champion-rotations`. Create `ChampionRotation.java`:

```java
package com.muddl.riot.lol.champion.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The current free-to-play champion rotation for a platform (Riot Champion-V3). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChampionRotation {
    private List<Integer> freeChampionIds;
    private List<Integer> freeChampionIdsForNewPlayers;
    private int maxNewPlayerLevel;
}
```

- [ ] **Step 4: Create the port**

Create `ChampionPort.java`:

```java
package com.muddl.riot.lol.champion.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.domain.ChampionRotation;

/** Outbound port for Riot Champion-V3 rotation data. Platform-routed. */
public interface ChampionPort {

    /** The current free-to-play champion rotation for a platform. */
    ChampionRotation getChampionRotation(RiotApiPlatformUri platform);
}
```

- [ ] **Step 5: Create the adapter**

Create `RiotChampionAdapter.java`:

```java
package com.muddl.riot.lol.champion.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Champion-V3 API adapter. Champion-rotation is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotChampionAdapter implements ChampionPort {

    private final RiotApiClient riotApiClient;

    @Override
    public ChampionRotation getChampionRotation(RiotApiPlatformUri platform) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/platform/v3/champion-rotations")
                .retrieve()
                .body(ChampionRotation.class);
    }
}
```

- [ ] **Step 6: Run the adapter test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.champion.adapter.out.riot.RiotChampionAdapterTest'`
Expected: PASS.

- [ ] **Step 7: Write the service's in-memory port fake and failing test**

Create `InMemoryChampionPort.java`:

```java
package com.muddl.riot.lol.champion.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import java.util.EnumMap;
import java.util.Map;

/** Hand-written in-memory {@link ChampionPort} for fast, HTTP-free service tests. */
public class InMemoryChampionPort implements ChampionPort {

    private final Map<RiotApiPlatformUri, ChampionRotation> byPlatform = new EnumMap<>(RiotApiPlatformUri.class);

    public InMemoryChampionPort put(RiotApiPlatformUri platform, ChampionRotation rotation) {
        byPlatform.put(platform, rotation);
        return this;
    }

    @Override
    public ChampionRotation getChampionRotation(RiotApiPlatformUri platform) {
        return byPlatform.get(platform);
    }
}
```

Create `ChampionServiceTest.java`:

```java
package com.muddl.riot.lol.champion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Non-player-keyed context: the service takes no resolver (ADR-0014). */
class ChampionServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryChampionPort port = new InMemoryChampionPort();
    private final ChampionService service = new ChampionService(port);

    @Test
    void getChampionRotation_delegatesToPort() {
        ChampionRotation expected = ChampionRotation.builder()
                .freeChampionIds(List.of(1, 2, 3))
                .maxNewPlayerLevel(10)
                .build();
        port.put(PLATFORM, expected);

        assertThat(service.getChampionRotation(PLATFORM)).isSameAs(expected);
    }
}
```

- [ ] **Step 8: Run the service test — expect FAIL (ChampionService does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.champion.application.ChampionServiceTest'`
Expected: FAIL — compilation error, `ChampionService` not found.

- [ ] **Step 9: Create the service**

Create `ChampionService.java`:

```java
package com.muddl.riot.lol.champion.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.port.ChampionPort;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for the free-to-play champion rotation. Non-player-keyed: depends only on its
 * own {@link ChampionPort}, never on {@code PlayerIdentityResolver} (ADR-0014).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionService {

    private final ChampionPort championPort;

    public ChampionRotation getChampionRotation(RiotApiPlatformUri platform) {
        log.info("Fetching champion rotation on platform: {}", platform);
        return championPort.getChampionRotation(platform);
    }
}
```

- [ ] **Step 10: Run the service test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.champion.application.ChampionServiceTest'`
Expected: PASS.

- [ ] **Step 11: Write the tool's failing test**

Create `ChampionToolTest.java`:

```java
package com.muddl.riot.lol.champion.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.ChampionService;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionToolTest {

    @Mock
    private ChampionService mockChampionService;

    @InjectMocks
    private ChampionTool championTool;

    @Test
    void getChampionRotation_passesPlatformThrough() {
        ChampionRotation rotation = ChampionRotation.builder().maxNewPlayerLevel(10).build();
        when(mockChampionService.getChampionRotation(RiotApiPlatformUri.NA1)).thenReturn(rotation);

        assertThat(championTool.getChampionRotation("NA1")).isSameAs(rotation);
        verify(mockChampionService).getChampionRotation(RiotApiPlatformUri.NA1);
    }

    @Test
    void getChampionRotation_invalidPlatform_throws() {
        assertThatThrownBy(() -> championTool.getChampionRotation("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
```

- [ ] **Step 12: Run the tool test — expect FAIL (ChampionTool does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.champion.adapter.in.mcp.ChampionToolTest'`
Expected: FAIL — compilation error, `ChampionTool` not found.

- [ ] **Step 13: Create the tool**

Create `ChampionTool.java`:

```java
package com.muddl.riot.lol.champion.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.champion.application.ChampionService;
import com.muddl.riot.lol.champion.domain.ChampionRotation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for the League of Legends free-to-play champion rotation. Non-player-keyed (ADR-0014). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChampionTool {

    private final ChampionService championService;

    @McpTool(
            name = "lol_champion_rotation",
            description = "Get the current free-to-play champion rotation for a League of Legends platform.")
    public ChampionRotation getChampionRotation(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting champion rotation on platform: {}", platform);
        return championService.getChampionRotation(platform);
    }
}
```

- [ ] **Step 14: Run the tool test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.champion.adapter.in.mcp.ChampionToolTest'`
Expected: PASS.

- [ ] **Step 15: Update the tool inventory test (or the build goes red)**

In `McpToolInventoryTest.java`: add the import, the class to the stream, and the name to the set.

Add after the existing `AnalyticsTool` import (keep alphabetical-ish with the others):

```java
import com.muddl.riot.lol.champion.adapter.in.mcp.ChampionTool;
```

Add `"lol_champion_rotation"` to `EXPECTED_TOOL_NAMES`:

```java
    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "lol_account_by_player",
            "lol_summoner_by_player",
            "lol_spectator_current_game_by_player",
            "lol_analytics_player_matches",
            "lol_league_entries_by_player",
            "lol_league_apex_by_tier",
            "lol_champion_rotation");
```

Add `ChampionTool.class` to the `Stream.of(...)`:

```java
        Set<String> actual = Stream.of(
                        RiotAccountTool.class,
                        AnalyticsTool.class,
                        LiveGameTool.class,
                        SummonerTool.class,
                        LeagueTool.class,
                        ChampionTool.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
```

- [ ] **Step 16: Add the changelog bullet**

Under the `## [0.2.0]` → `### Added` section in `lol-mcp-server/CHANGELOG.md`, add:

```markdown
- `lol_champion_rotation` — the current free-to-play champion rotation for a platform (Champion-V3).
  The first non-player-keyed context (ADR-0014).
```

- [ ] **Step 17: Format, run the full module test suite, and commit**

Run: `./gradlew spotlessApply :lol-mcp-server:test`
Expected: PASS (all tests green, including `McpToolInventoryTest`).

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/champion \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/champion \
        lol-mcp-server/src/test/resources/fixtures/champion-rotation.json \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java \
        lol-mcp-server/CHANGELOG.md
git commit -m "feat(champion): add lol_champion_rotation (non-player context)"
```

---

### Task 3: `status` context — platform status/incidents (non-player, nested + snake_case)

Non-player-keyed like `champion`, but its JSON nests incident content and uses `snake_case` keys — the first context that needs `@JsonProperty`. This surfaces a reusable gotcha.

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/status/domain/PlatformStatus.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/status/domain/StatusEntry.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/status/domain/StatusContent.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/status/application/port/StatusPort.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/status/application/StatusService.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/status/adapter/out/riot/RiotStatusAdapter.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/status/adapter/in/mcp/StatusTool.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/status/adapter/out/riot/RiotStatusAdapterTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/status/application/InMemoryStatusPort.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/status/application/StatusServiceTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/status/adapter/in/mcp/StatusToolTest.java`
- Create: `lol-mcp-server/src/test/resources/fixtures/status-platform.json`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`
- Modify: `lol-mcp-server/CHANGELOG.md`

**Interfaces:**
- Consumes: `RiotApiClient.platform(...)`, `RiotApiPlatformUri`.
- Produces: `StatusService.getPlatformStatus(RiotApiPlatformUri) → PlatformStatus`; tool `lol_status_platform`.

- [ ] **Step 1: Write the adapter's failing WireMock test**

Create `RiotStatusAdapterTest.java`:

```java
package com.muddl.riot.lol.status.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.lol.status.application.port.StatusPort;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotStatusAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String STATUS_URL = "/lol/status/v4/platform-data";

    private WireMockServer wireMock;
    private StatusPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotStatusAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getPlatformStatus_parsesNestedIncident_andSnakeCaseKeys() {
        stubFor(get(urlEqualTo(STATUS_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("status-platform.json"))));

        PlatformStatus status = adapter.getPlatformStatus(PLATFORM);

        assertThat(status.getId()).isEqualTo("NA1");
        assertThat(status.getName()).isEqualTo("North America");
        assertThat(status.getIncidents()).hasSize(1);
        assertThat(status.getIncidents().get(0).getIncidentSeverity()).isEqualTo("warning");
        assertThat(status.getIncidents().get(0).getTitles().get(0).getContent()).isEqualTo("Login issues");
        verify(getRequestedFor(urlEqualTo(STATUS_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(STATUS_URL)).willReturn(aResponse().withStatus(503).withBody("unavailable")));

        assertThatThrownBy(() -> adapter.getPlatformStatus(PLATFORM))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(503);
    }
}
```

- [ ] **Step 2: Create the fixture**

Create `lol-mcp-server/src/test/resources/fixtures/status-platform.json`:

```json
{
  "id": "NA1",
  "name": "North America",
  "locales": ["en_US"],
  "maintenances": [],
  "incidents": [
    {
      "id": 100,
      "incident_severity": "warning",
      "titles": [{ "locale": "en_US", "content": "Login issues" }],
      "updates": []
    }
  ]
}
```

- [ ] **Step 3: Create the domain DTOs**

The endpoint (verify on the portal) is `GET /lol/status/v4/platform-data`. Note the `incident_severity` / `maintenance_status` `snake_case` keys need `@JsonProperty` — Jackson is configured for exact-name matching here. Create `StatusContent.java`:

```java
package com.muddl.riot.lol.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A localized status message (a title or update entry) from Riot LoL-Status-V4. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusContent {
    private String locale;
    private String content;
}
```

Create `StatusEntry.java`:

```java
package com.muddl.riot.lol.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One maintenance or incident from Riot LoL-Status-V4. Riot uses snake_case for two keys. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusEntry {
    private long id;

    @JsonProperty("maintenance_status")
    private String maintenanceStatus;

    @JsonProperty("incident_severity")
    private String incidentSeverity;

    private List<StatusContent> titles;
    private List<StatusContent> updates;
}
```

Create `PlatformStatus.java`:

```java
package com.muddl.riot.lol.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A platform's operational status: current maintenances and incidents (Riot LoL-Status-V4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformStatus {
    private String id;
    private String name;
    private List<String> locales;
    private List<StatusEntry> maintenances;
    private List<StatusEntry> incidents;
}
```

- [ ] **Step 4: Create the port**

Create `StatusPort.java`:

```java
package com.muddl.riot.lol.status.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.domain.PlatformStatus;

/** Outbound port for Riot LoL-Status-V4 platform status. Platform-routed. */
public interface StatusPort {

    /** The platform's current maintenances and incidents. */
    PlatformStatus getPlatformStatus(RiotApiPlatformUri platform);
}
```

- [ ] **Step 5: Create the adapter**

Create `RiotStatusAdapter.java`:

```java
package com.muddl.riot.lol.status.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.status.application.port.StatusPort;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot LoL-Status-V4 API adapter. Platform status is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotStatusAdapter implements StatusPort {

    private final RiotApiClient riotApiClient;

    @Override
    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/status/v4/platform-data")
                .retrieve()
                .body(PlatformStatus.class);
    }
}
```

- [ ] **Step 6: Run the adapter test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.status.adapter.out.riot.RiotStatusAdapterTest'`
Expected: PASS. (If `incidentSeverity` is null, the `@JsonProperty` is missing — that is the snake_case gotcha this task exists to catch.)

- [ ] **Step 7: Write the service's in-memory port fake and failing test**

Create `InMemoryStatusPort.java`:

```java
package com.muddl.riot.lol.status.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.application.port.StatusPort;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import java.util.EnumMap;
import java.util.Map;

/** Hand-written in-memory {@link StatusPort} for fast, HTTP-free service tests. */
public class InMemoryStatusPort implements StatusPort {

    private final Map<RiotApiPlatformUri, PlatformStatus> byPlatform = new EnumMap<>(RiotApiPlatformUri.class);

    public InMemoryStatusPort put(RiotApiPlatformUri platform, PlatformStatus status) {
        byPlatform.put(platform, status);
        return this;
    }

    @Override
    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        return byPlatform.get(platform);
    }
}
```

Create `StatusServiceTest.java`:

```java
package com.muddl.riot.lol.status.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import org.junit.jupiter.api.Test;

/** Non-player-keyed context: the service takes no resolver (ADR-0014). */
class StatusServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryStatusPort port = new InMemoryStatusPort();
    private final StatusService service = new StatusService(port);

    @Test
    void getPlatformStatus_delegatesToPort() {
        PlatformStatus expected = PlatformStatus.builder().id("NA1").name("North America").build();
        port.put(PLATFORM, expected);

        assertThat(service.getPlatformStatus(PLATFORM)).isSameAs(expected);
    }
}
```

- [ ] **Step 8: Run the service test — expect FAIL (StatusService does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.status.application.StatusServiceTest'`
Expected: FAIL — compilation error, `StatusService` not found.

- [ ] **Step 9: Create the service**

Create `StatusService.java`:

```java
package com.muddl.riot.lol.status.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.application.port.StatusPort;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for LoL platform status. Non-player-keyed: depends only on its own
 * {@link StatusPort}, never on {@code PlayerIdentityResolver} (ADR-0014).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusPort statusPort;

    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        log.info("Fetching platform status on platform: {}", platform);
        return statusPort.getPlatformStatus(platform);
    }
}
```

- [ ] **Step 10: Run the service test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.status.application.StatusServiceTest'`
Expected: PASS.

- [ ] **Step 11: Write the tool's failing test**

Create `StatusToolTest.java`:

```java
package com.muddl.riot.lol.status.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.application.StatusService;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatusToolTest {

    @Mock
    private StatusService mockStatusService;

    @InjectMocks
    private StatusTool statusTool;

    @Test
    void getPlatformStatus_passesPlatformThrough() {
        PlatformStatus status = PlatformStatus.builder().id("NA1").build();
        when(mockStatusService.getPlatformStatus(RiotApiPlatformUri.NA1)).thenReturn(status);

        assertThat(statusTool.getPlatformStatus("NA1")).isSameAs(status);
        verify(mockStatusService).getPlatformStatus(RiotApiPlatformUri.NA1);
    }

    @Test
    void getPlatformStatus_invalidPlatform_throws() {
        assertThatThrownBy(() -> statusTool.getPlatformStatus("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
```

- [ ] **Step 12: Run the tool test — expect FAIL (StatusTool does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.status.adapter.in.mcp.StatusToolTest'`
Expected: FAIL — compilation error, `StatusTool` not found.

- [ ] **Step 13: Create the tool**

Create `StatusTool.java`:

```java
package com.muddl.riot.lol.status.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.application.StatusService;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for League of Legends platform status. Non-player-keyed (ADR-0014). */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusTool {

    private final StatusService statusService;

    @McpTool(
            name = "lol_status_platform",
            description =
                    "Get the operational status (current maintenances and incidents) of a League of Legends platform.")
    public PlatformStatus getPlatformStatus(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting platform status on platform: {}", platform);
        return statusService.getPlatformStatus(platform);
    }
}
```

- [ ] **Step 14: Run the tool test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.status.adapter.in.mcp.StatusToolTest'`
Expected: PASS.

- [ ] **Step 15: Update the tool inventory test**

In `McpToolInventoryTest.java`, add the import:

```java
import com.muddl.riot.lol.status.adapter.in.mcp.StatusTool;
```

Add `"lol_status_platform"` to `EXPECTED_TOOL_NAMES` and `StatusTool.class` to the `Stream.of(...)` (alongside `ChampionTool.class` from Task 2).

- [ ] **Step 16: Add the changelog bullet**

Under `## [0.2.0]` → `### Added`:

```markdown
- `lol_status_platform` — a platform's current maintenances and incidents (LoL-Status-V4).
```

- [ ] **Step 17: Format, run the full module test suite, and commit**

Run: `./gradlew spotlessApply :lol-mcp-server:test`
Expected: PASS.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/status \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/status \
        lol-mcp-server/src/test/resources/fixtures/status-platform.json \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java \
        lol-mcp-server/CHANGELOG.md
git commit -m "feat(status): add lol_status_platform (non-player context)"
```

---

### Task 4: `championmastery` context — mastery by player (player-keyed, optional count)

The first player-keyed 1b context: exact `league` shape (service injects `PlayerIdentityResolver`), plus an optional `count` param that switches the adapter between the all-masteries and top-N endpoints.

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/championmastery/domain/ChampionMastery.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/championmastery/application/port/ChampionMasteryPort.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/championmastery/application/ChampionMasteryService.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/championmastery/adapter/out/riot/RiotChampionMasteryAdapter.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/championmastery/adapter/in/mcp/ChampionMasteryTool.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/championmastery/adapter/out/riot/RiotChampionMasteryAdapterTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/championmastery/application/InMemoryChampionMasteryPort.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/championmastery/application/ChampionMasteryServiceTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/championmastery/adapter/in/mcp/ChampionMasteryToolTest.java`
- Create: `lol-mcp-server/src/test/resources/fixtures/champion-mastery.json`
- Create: `lol-mcp-server/src/test/resources/fixtures/champion-mastery-top.json`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`
- Modify: `lol-mcp-server/CHANGELOG.md`

**Interfaces:**
- Consumes: `RiotApiClient.platform(...)`, `RiotApiPlatformUri`, `PlayerIdentityResolver.resolvePuuid(String) → String`.
- Produces: `ChampionMasteryService.getMasteryByPlayer(RiotApiPlatformUri, String player, Integer count) → List<ChampionMastery>`; port `getMasteryByPuuid(RiotApiPlatformUri, String puuid, Integer count)`; tool `lol_champion_mastery_by_player`.

- [ ] **Step 1: Write the adapter's failing WireMock test**

The endpoints (verify on the portal): all → `GET /lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}`; top-N → `.../by-puuid/{puuid}/top?count={n}`. Create `RiotChampionMasteryAdapterTest.java`:

```java
package com.muddl.riot.lol.championmastery.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotChampionMasteryAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String ALL_URL = "/lol/champion-mastery/v4/champion-masteries/by-puuid/" + PUUID;
    private static final String TOP_URL = ALL_URL + "/top?count=1";

    private WireMockServer wireMock;
    private ChampionMasteryPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotChampionMasteryAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getMasteryByPuuid_nullCount_hitsAllUrl_andParsesArray() {
        stubFor(get(urlEqualTo(ALL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("champion-mastery.json"))));

        List<ChampionMastery> masteries = adapter.getMasteryByPuuid(PLATFORM, PUUID, null);

        assertThat(masteries).hasSize(2);
        assertThat(masteries.get(0).getChampionId()).isEqualTo(157);
        assertThat(masteries.get(0).getChampionLevel()).isEqualTo(7);
        assertThat(masteries.get(0).getChampionPoints()).isEqualTo(123456);
        verify(getRequestedFor(urlEqualTo(ALL_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getMasteryByPuuid_withCount_hitsTopUrl() {
        stubFor(get(urlEqualTo(TOP_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("champion-mastery-top.json"))));

        List<ChampionMastery> masteries = adapter.getMasteryByPuuid(PLATFORM, PUUID, 1);

        assertThat(masteries).hasSize(1);
        assertThat(masteries.get(0).getChampionId()).isEqualTo(157);
        verify(getRequestedFor(urlEqualTo(TOP_URL)));
    }

    @Test
    void getMasteryByPuuid_emptyArray_returnsEmptyList() {
        stubFor(get(urlEqualTo(ALL_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(adapter.getMasteryByPuuid(PLATFORM, PUUID, null)).isEmpty();
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(ALL_URL)).willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getMasteryByPuuid(PLATFORM, PUUID, null))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
```

- [ ] **Step 2: Create the fixtures**

Create `lol-mcp-server/src/test/resources/fixtures/champion-mastery.json`:

```json
[
  {
    "puuid": "test-puuid-abc123",
    "championId": 157,
    "championLevel": 7,
    "championPoints": 123456,
    "lastPlayTime": 1609459200000,
    "championPointsSinceLastLevel": 0,
    "championPointsUntilNextLevel": 0,
    "chestGranted": true,
    "tokensEarned": 0
  },
  {
    "puuid": "test-puuid-abc123",
    "championId": 64,
    "championLevel": 5,
    "championPoints": 54321,
    "lastPlayTime": 1609459200000,
    "championPointsSinceLastLevel": 100,
    "championPointsUntilNextLevel": 2000,
    "chestGranted": false,
    "tokensEarned": 2
  }
]
```

Create `lol-mcp-server/src/test/resources/fixtures/champion-mastery-top.json`:

```json
[
  {
    "puuid": "test-puuid-abc123",
    "championId": 157,
    "championLevel": 7,
    "championPoints": 123456,
    "lastPlayTime": 1609459200000,
    "chestGranted": true,
    "tokensEarned": 0
  }
]
```

- [ ] **Step 3: Create the domain DTO**

Create `ChampionMastery.java`:

```java
package com.muddl.riot.lol.championmastery.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's mastery of one champion (Riot Champion-Mastery-V4, by-puuid). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChampionMastery {
    private String puuid;
    private long championId;
    private int championLevel;
    private int championPoints;
    private long lastPlayTime;
    private long championPointsSinceLastLevel;
    private long championPointsUntilNextLevel;
    private boolean chestGranted;
    private int tokensEarned;
}
```

- [ ] **Step 4: Create the port**

Create `ChampionMasteryPort.java`:

```java
package com.muddl.riot.lol.championmastery.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;

/** Outbound port for Riot Champion-Mastery-V4 data. Platform-routed. */
public interface ChampionMasteryPort {

    /**
     * A player's champion masteries sorted by points (Riot's default). When {@code count} is
     * non-null, only the top {@code count} are returned (Riot's {@code /top} endpoint).
     */
    List<ChampionMastery> getMasteryByPuuid(RiotApiPlatformUri platform, String puuid, Integer count);
}
```

- [ ] **Step 5: Create the adapter**

Create `RiotChampionMasteryAdapter.java`:

```java
package com.muddl.riot.lol.championmastery.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Champion-Mastery-V4 API adapter. Mastery endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotChampionMasteryAdapter implements ChampionMasteryPort {

    private static final String BY_PUUID = "/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}";

    private final RiotApiClient riotApiClient;

    @Override
    public List<ChampionMastery> getMasteryByPuuid(RiotApiPlatformUri platform, String puuid, Integer count) {
        String uri = count == null ? BY_PUUID : BY_PUUID + "/top?count=" + count;
        ChampionMastery[] masteries = riotApiClient
                .platform(platform)
                .get()
                .uri(uri, puuid)
                .retrieve()
                .body(ChampionMastery[].class);
        return masteries == null ? List.of() : List.of(masteries);
    }
}
```

- [ ] **Step 6: Run the adapter test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.championmastery.adapter.out.riot.RiotChampionMasteryAdapterTest'`
Expected: PASS.

- [ ] **Step 7: Write the service's in-memory port fake and failing test**

Create `InMemoryChampionMasteryPort.java`:

```java
package com.muddl.riot.lol.championmastery.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link ChampionMasteryPort} for fast, HTTP-free service tests. */
public class InMemoryChampionMasteryPort implements ChampionMasteryPort {

    private final Map<String, List<ChampionMastery>> byPuuid = new HashMap<>();

    public InMemoryChampionMasteryPort put(String puuid, List<ChampionMastery> masteries) {
        byPuuid.put(puuid, masteries);
        return this;
    }

    @Override
    public List<ChampionMastery> getMasteryByPuuid(RiotApiPlatformUri platform, String puuid, Integer count) {
        List<ChampionMastery> all = byPuuid.getOrDefault(puuid, List.of());
        return count == null ? all : all.stream().limit(count).toList();
    }
}
```

Create `ChampionMasteryServiceTest.java`:

```java
package com.muddl.riot.lol.championmastery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMasteryServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryChampionMasteryPort port = new InMemoryChampionMasteryPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final ChampionMasteryService service = new ChampionMasteryService(port, resolver);

    @Test
    void getMasteryByPlayer_resolvesPlayer_thenReturnsAll() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        ChampionMastery m1 = ChampionMastery.builder().championId(157).championPoints(999).build();
        ChampionMastery m2 = ChampionMastery.builder().championId(64).championPoints(500).build();
        port.put("faker-puuid", List.of(m1, m2));

        assertThat(service.getMasteryByPlayer(PLATFORM, "Faker#KR1", null)).containsExactly(m1, m2);
    }

    @Test
    void getMasteryByPlayer_honoursCount() {
        when(resolver.resolvePuuid("puuid-raw")).thenReturn("puuid-raw");
        ChampionMastery m1 = ChampionMastery.builder().championId(157).build();
        ChampionMastery m2 = ChampionMastery.builder().championId(64).build();
        port.put("puuid-raw", List.of(m1, m2));

        assertThat(service.getMasteryByPlayer(PLATFORM, "puuid-raw", 1)).containsExactly(m1);
    }
}
```

- [ ] **Step 8: Run the service test — expect FAIL (ChampionMasteryService does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.championmastery.application.ChampionMasteryServiceTest'`
Expected: FAIL — compilation error.

- [ ] **Step 9: Create the service**

Create `ChampionMasteryService.java`:

```java
package com.muddl.riot.lol.championmastery.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot Champion-Mastery-V4 data. Player-keyed: resolves the caller's
 * {@code player} to a PUUID via the shared {@link PlayerIdentityResolver} before calling the port —
 * the {@code league} shape. Depends only on its own port and the resolver.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionMasteryService {

    private final ChampionMasteryPort masteryPort;
    private final PlayerIdentityResolver identityResolver;

    public List<ChampionMastery> getMasteryByPlayer(RiotApiPlatformUri platform, String player, Integer count) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching champion mastery on platform: {}", platform);
        return masteryPort.getMasteryByPuuid(platform, puuid, count);
    }
}
```

- [ ] **Step 10: Run the service test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.championmastery.application.ChampionMasteryServiceTest'`
Expected: PASS.

- [ ] **Step 11: Write the tool's failing test**

Create `ChampionMasteryToolTest.java`:

```java
package com.muddl.riot.lol.championmastery.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.ChampionMasteryService;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionMasteryToolTest {

    @Mock
    private ChampionMasteryService mockService;

    @InjectMocks
    private ChampionMasteryTool tool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getMasteryByPlayer_passesArgsThrough_withNullCount() {
        ChampionMastery m = ChampionMastery.builder().championId(157).build();
        when(mockService.getMasteryByPlayer(PLATFORM, "Faker#KR1", null)).thenReturn(List.of(m));

        assertThat(tool.getMasteryByPlayer("NA1", "Faker#KR1", null)).containsExactly(m);
        verify(mockService).getMasteryByPlayer(PLATFORM, "Faker#KR1", null);
    }

    @Test
    void getMasteryByPlayer_passesCountThrough() {
        when(mockService.getMasteryByPlayer(PLATFORM, "Faker#KR1", 3)).thenReturn(List.of());

        assertThat(tool.getMasteryByPlayer("NA1", "Faker#KR1", 3)).isEmpty();
        verify(mockService).getMasteryByPlayer(PLATFORM, "Faker#KR1", 3);
    }

    @Test
    void getMasteryByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> tool.getMasteryByPlayer("INVALID", "Faker#KR1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
```

- [ ] **Step 12: Run the tool test — expect FAIL (ChampionMasteryTool does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.championmastery.adapter.in.mcp.ChampionMasteryToolTest'`
Expected: FAIL — compilation error.

- [ ] **Step 13: Create the tool**

Create `ChampionMasteryTool.java`:

```java
package com.muddl.riot.lol.championmastery.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.ChampionMasteryService;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for League of Legends champion mastery. Player-keyed: a single {@code player} param. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChampionMasteryTool {

    private final ChampionMasteryService masteryService;

    @McpTool(
            name = "lol_champion_mastery_by_player",
            description =
                    "Get a League of Legends player's champion masteries, sorted by points. Optionally limit to the top N.")
    public List<ChampionMastery> getMasteryByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(
                            description = "Optional: return only the top N champions by mastery points",
                            required = false)
                    Integer count) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting champion mastery for a player on platform: {}", platform);
        return masteryService.getMasteryByPlayer(platform, player, count);
    }
}
```

- [ ] **Step 14: Run the tool test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.championmastery.adapter.in.mcp.ChampionMasteryToolTest'`
Expected: PASS.

- [ ] **Step 15: Update the tool inventory test**

In `McpToolInventoryTest.java`, add the import:

```java
import com.muddl.riot.lol.championmastery.adapter.in.mcp.ChampionMasteryTool;
```

Add `"lol_champion_mastery_by_player"` to `EXPECTED_TOOL_NAMES` and `ChampionMasteryTool.class` to the `Stream.of(...)`.

- [ ] **Step 16: Add the changelog bullet**

Under `## [0.2.0]` → `### Added`:

```markdown
- `lol_champion_mastery_by_player` — a player's champion masteries sorted by points, with an optional
  top-N `count` (Champion-Mastery-V4).
```

- [ ] **Step 17: Format, run the full module test suite, and commit**

Run: `./gradlew spotlessApply :lol-mcp-server:test`
Expected: PASS.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/championmastery \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/championmastery \
        lol-mcp-server/src/test/resources/fixtures/champion-mastery.json \
        lol-mcp-server/src/test/resources/fixtures/champion-mastery-top.json \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java \
        lol-mcp-server/CHANGELOG.md
git commit -m "feat(championmastery): add lol_champion_mastery_by_player"
```

---

### Task 5: `challenges` context — player challenge summary (player-keyed, nested DTOs)

Player-keyed, `league` shape. The player-data JSON nests a points summary and a per-challenge list; model only the useful subset and let `@JsonIgnoreProperties` drop the rest (cosmetic `preferences` are skipped by YAGNI).

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/domain/ChallengesPlayerData.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/domain/ChallengePoints.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/domain/ChallengeProgress.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/application/port/ChallengesPort.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/application/ChallengesService.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/adapter/out/riot/RiotChallengesAdapter.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/adapter/in/mcp/ChallengesTool.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges/adapter/out/riot/RiotChallengesAdapterTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges/application/InMemoryChallengesPort.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges/application/ChallengesServiceTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges/adapter/in/mcp/ChallengesToolTest.java`
- Create: `lol-mcp-server/src/test/resources/fixtures/challenges-player-data.json`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`
- Modify: `lol-mcp-server/CHANGELOG.md`

**Interfaces:**
- Consumes: `RiotApiClient.platform(...)`, `RiotApiPlatformUri`, `PlayerIdentityResolver.resolvePuuid(String)`.
- Produces: `ChallengesService.getChallengesByPlayer(RiotApiPlatformUri, String player) → ChallengesPlayerData`; port `getPlayerDataByPuuid(RiotApiPlatformUri, String puuid)`; tool `lol_challenges_by_player`.

- [ ] **Step 1: Write the adapter's failing WireMock test**

The endpoint (verify on the portal): `GET /lol/challenges/v1/player-data/{puuid}`. Create `RiotChallengesAdapterTest.java`:

```java
package com.muddl.riot.lol.challenges.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotChallengesAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String URL = "/lol/challenges/v1/player-data/" + PUUID;

    private WireMockServer wireMock;
    private ChallengesPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotChallengesAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getPlayerDataByPuuid_parsesNestedPointsAndChallenges() {
        stubFor(get(urlEqualTo(URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("challenges-player-data.json"))));

        ChallengesPlayerData data = adapter.getPlayerDataByPuuid(PLATFORM, PUUID);

        assertThat(data.getTotalPoints().getLevel()).isEqualTo("GOLD");
        assertThat(data.getTotalPoints().getCurrent()).isEqualTo(12345.0);
        assertThat(data.getCategoryPoints()).containsKey("TEAMWORK");
        assertThat(data.getChallenges()).hasSize(1);
        assertThat(data.getChallenges().get(0).getChallengeId()).isEqualTo(101101L);
        assertThat(data.getChallenges().get(0).getLevel()).isEqualTo("GOLD");
        verify(getRequestedFor(urlEqualTo(URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getPlayerDataByPuuid(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
```

- [ ] **Step 2: Create the fixture**

Create `lol-mcp-server/src/test/resources/fixtures/challenges-player-data.json`:

```json
{
  "totalPoints": { "level": "GOLD", "current": 12345, "max": 50000, "percentile": 0.42 },
  "categoryPoints": {
    "TEAMWORK": { "level": "SILVER", "current": 2000, "max": 10000, "percentile": 0.3 }
  },
  "challenges": [
    { "challengeId": 101101, "level": "GOLD", "value": 150.0, "percentile": 0.25, "achievedTime": 1609459200000 }
  ],
  "preferences": { "title": "12345", "bannerAccent": "1" }
}
```

- [ ] **Step 3: Create the domain DTOs**

Create `ChallengePoints.java`:

```java
package com.muddl.riot.lol.challenges.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A points summary for a challenge category or the player total (Riot LoL-Challenges-V1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengePoints {
    private String level;
    private double current;
    private double max;
    private double percentile;
}
```

Create `ChallengeProgress.java`:

```java
package com.muddl.riot.lol.challenges.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's progress on one individual challenge (Riot LoL-Challenges-V1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeProgress {
    private long challengeId;
    private String level;
    private double value;
    private double percentile;
    private long achievedTime;
}
```

Create `ChallengesPlayerData.java`:

```java
package com.muddl.riot.lol.challenges.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's challenge standing: totals, per-category points, and per-challenge progress. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengesPlayerData {
    private ChallengePoints totalPoints;
    private Map<String, ChallengePoints> categoryPoints;
    private List<ChallengeProgress> challenges;
}
```

- [ ] **Step 4: Create the port**

Create `ChallengesPort.java`:

```java
package com.muddl.riot.lol.challenges.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;

/** Outbound port for Riot LoL-Challenges-V1 player data. Platform-routed. */
public interface ChallengesPort {

    /** A player's challenge standing (totals, category points, per-challenge progress). */
    ChallengesPlayerData getPlayerDataByPuuid(RiotApiPlatformUri platform, String puuid);
}
```

- [ ] **Step 5: Create the adapter**

Create `RiotChallengesAdapter.java`:

```java
package com.muddl.riot.lol.challenges.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot LoL-Challenges-V1 API adapter. Challenge data is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotChallengesAdapter implements ChallengesPort {

    private final RiotApiClient riotApiClient;

    @Override
    public ChallengesPlayerData getPlayerDataByPuuid(RiotApiPlatformUri platform, String puuid) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/challenges/v1/player-data/{puuid}", puuid)
                .retrieve()
                .body(ChallengesPlayerData.class);
    }
}
```

- [ ] **Step 6: Run the adapter test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.challenges.adapter.out.riot.RiotChallengesAdapterTest'`
Expected: PASS.

- [ ] **Step 7: Write the service's in-memory port fake and failing test**

Create `InMemoryChallengesPort.java`:

```java
package com.muddl.riot.lol.challenges.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link ChallengesPort} for fast, HTTP-free service tests. */
public class InMemoryChallengesPort implements ChallengesPort {

    private final Map<String, ChallengesPlayerData> byPuuid = new HashMap<>();

    public InMemoryChallengesPort put(String puuid, ChallengesPlayerData data) {
        byPuuid.put(puuid, data);
        return this;
    }

    @Override
    public ChallengesPlayerData getPlayerDataByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.get(puuid);
    }
}
```

Create `ChallengesServiceTest.java`:

```java
package com.muddl.riot.lol.challenges.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.domain.ChallengePoints;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import org.junit.jupiter.api.Test;

class ChallengesServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryChallengesPort port = new InMemoryChallengesPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final ChallengesService service = new ChallengesService(port, resolver);

    @Test
    void getChallengesByPlayer_resolvesPlayer_thenReturnsData() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        ChallengesPlayerData data = ChallengesPlayerData.builder()
                .totalPoints(ChallengePoints.builder().level("GOLD").build())
                .build();
        port.put("faker-puuid", data);

        assertThat(service.getChallengesByPlayer(PLATFORM, "Faker#KR1")).isSameAs(data);
    }
}
```

- [ ] **Step 8: Run the service test — expect FAIL (ChallengesService does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.challenges.application.ChallengesServiceTest'`
Expected: FAIL — compilation error.

- [ ] **Step 9: Create the service**

Create `ChallengesService.java`:

```java
package com.muddl.riot.lol.challenges.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot LoL-Challenges-V1 player data. Player-keyed: resolves {@code player}
 * to a PUUID via {@link PlayerIdentityResolver} before calling the port (the {@code league} shape).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengesService {

    private final ChallengesPort challengesPort;
    private final PlayerIdentityResolver identityResolver;

    public ChallengesPlayerData getChallengesByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching challenge data on platform: {}", platform);
        return challengesPort.getPlayerDataByPuuid(platform, puuid);
    }
}
```

- [ ] **Step 10: Run the service test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.challenges.application.ChallengesServiceTest'`
Expected: PASS.

- [ ] **Step 11: Write the tool's failing test**

Create `ChallengesToolTest.java`:

```java
package com.muddl.riot.lol.challenges.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.ChallengesService;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChallengesToolTest {

    @Mock
    private ChallengesService mockService;

    @InjectMocks
    private ChallengesTool tool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getChallengesByPlayer_passesPlatformAndPlayerThrough() {
        ChallengesPlayerData data = ChallengesPlayerData.builder().build();
        when(mockService.getChallengesByPlayer(PLATFORM, "Faker#KR1")).thenReturn(data);

        assertThat(tool.getChallengesByPlayer("NA1", "Faker#KR1")).isSameAs(data);
        verify(mockService).getChallengesByPlayer(PLATFORM, "Faker#KR1");
    }

    @Test
    void getChallengesByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> tool.getChallengesByPlayer("INVALID", "Faker#KR1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
```

- [ ] **Step 12: Run the tool test — expect FAIL (ChallengesTool does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.challenges.adapter.in.mcp.ChallengesToolTest'`
Expected: FAIL — compilation error.

- [ ] **Step 13: Create the tool**

Create `ChallengesTool.java`:

```java
package com.muddl.riot.lol.challenges.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.ChallengesService;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for League of Legends challenge data. Player-keyed: a single {@code player} param. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengesTool {

    private final ChallengesService challengesService;

    @McpTool(
            name = "lol_challenges_by_player",
            description =
                    "Get a League of Legends player's challenge standing: total and per-category points, and per-challenge progress.")
    public ChallengesPlayerData getChallengesByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting challenge data for a player on platform: {}", platform);
        return challengesService.getChallengesByPlayer(platform, player);
    }
}
```

- [ ] **Step 14: Run the tool test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.challenges.adapter.in.mcp.ChallengesToolTest'`
Expected: PASS.

- [ ] **Step 15: Update the tool inventory test**

In `McpToolInventoryTest.java`, add the import:

```java
import com.muddl.riot.lol.challenges.adapter.in.mcp.ChallengesTool;
```

Add `"lol_challenges_by_player"` to `EXPECTED_TOOL_NAMES` and `ChallengesTool.class` to the `Stream.of(...)`.

- [ ] **Step 16: Add the changelog bullet**

Under `## [0.2.0]` → `### Added`:

```markdown
- `lol_challenges_by_player` — a player's challenge standing: totals, category points, and
  per-challenge progress (LoL-Challenges-V1).
```

- [ ] **Step 17: Format, run the full module test suite, and commit**

Run: `./gradlew spotlessApply :lol-mcp-server:test`
Expected: PASS.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges \
        lol-mcp-server/src/test/resources/fixtures/challenges-player-data.json \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java \
        lol-mcp-server/CHANGELOG.md
git commit -m "feat(challenges): add lol_challenges_by_player"
```

---

### Task 6: `clash` context — player Clash registrations (player-keyed, array return)

Player-keyed, `league` shape. The by-puuid endpoint returns an **array** (a player may be registered in several tournaments), so the port returns `List<ClashPlayer>` and the adapter maps `null → List.of()`.

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/clash/domain/ClashPlayer.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/clash/application/port/ClashPort.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/clash/application/ClashService.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/clash/adapter/out/riot/RiotClashAdapter.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/clash/adapter/in/mcp/ClashTool.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/clash/adapter/out/riot/RiotClashAdapterTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/clash/application/InMemoryClashPort.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/clash/application/ClashServiceTest.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/clash/adapter/in/mcp/ClashToolTest.java`
- Create: `lol-mcp-server/src/test/resources/fixtures/clash-players.json`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`
- Modify: `lol-mcp-server/CHANGELOG.md`

**Interfaces:**
- Consumes: `RiotApiClient.platform(...)`, `RiotApiPlatformUri`, `PlayerIdentityResolver.resolvePuuid(String)`.
- Produces: `ClashService.getClashByPlayer(RiotApiPlatformUri, String player) → List<ClashPlayer>`; port `getPlayersByPuuid(RiotApiPlatformUri, String puuid)`; tool `lol_clash_by_player`.

- [ ] **Step 1: Write the adapter's failing WireMock test**

The endpoint (verify on the portal): `GET /lol/clash/v1/players/by-puuid/{puuid}`. Create `RiotClashAdapterTest.java`:

```java
package com.muddl.riot.lol.clash.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.lol.clash.application.port.ClashPort;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotClashAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String URL = "/lol/clash/v1/players/by-puuid/" + PUUID;

    private WireMockServer wireMock;
    private ClashPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotClashAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getPlayersByPuuid_parsesArray_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo(URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("clash-players.json"))));

        List<ClashPlayer> players = adapter.getPlayersByPuuid(PLATFORM, PUUID);

        assertThat(players).hasSize(1);
        assertThat(players.get(0).getTeamId()).isEqualTo("team-1");
        assertThat(players.get(0).getPosition()).isEqualTo("TOP");
        assertThat(players.get(0).getRole()).isEqualTo("CAPTAIN");
        verify(getRequestedFor(urlEqualTo(URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getPlayersByPuuid_emptyArray_returnsEmptyList() {
        stubFor(get(urlEqualTo(URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(adapter.getPlayersByPuuid(PLATFORM, PUUID)).isEmpty();
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getPlayersByPuuid(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
```

- [ ] **Step 2: Create the fixture**

Create `lol-mcp-server/src/test/resources/fixtures/clash-players.json`:

```json
[
  {
    "summonerId": "sum-1",
    "puuid": "test-puuid-abc123",
    "teamId": "team-1",
    "position": "TOP",
    "role": "CAPTAIN"
  }
]
```

- [ ] **Step 3: Create the domain DTO**

Create `ClashPlayer.java`:

```java
package com.muddl.riot.lol.clash.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's registration in a Clash tournament team (Riot Clash-V1, players by-puuid). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClashPlayer {
    private String summonerId;
    private String puuid;
    private String teamId;
    private String position;
    private String role;
}
```

- [ ] **Step 4: Create the port**

Create `ClashPort.java`:

```java
package com.muddl.riot.lol.clash.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;

/** Outbound port for Riot Clash-V1 player registrations. Platform-routed. */
public interface ClashPort {

    /** A player's active Clash team registrations. Empty when the player has none. */
    List<ClashPlayer> getPlayersByPuuid(RiotApiPlatformUri platform, String puuid);
}
```

- [ ] **Step 5: Create the adapter**

Create `RiotClashAdapter.java`:

```java
package com.muddl.riot.lol.clash.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.clash.application.port.ClashPort;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot Clash-V1 API adapter. Clash endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotClashAdapter implements ClashPort {

    private final RiotApiClient riotApiClient;

    @Override
    public List<ClashPlayer> getPlayersByPuuid(RiotApiPlatformUri platform, String puuid) {
        ClashPlayer[] players = riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/clash/v1/players/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(ClashPlayer[].class);
        return players == null ? List.of() : List.of(players);
    }
}
```

- [ ] **Step 6: Run the adapter test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.clash.adapter.out.riot.RiotClashAdapterTest'`
Expected: PASS.

- [ ] **Step 7: Write the service's in-memory port fake and failing test**

Create `InMemoryClashPort.java`:

```java
package com.muddl.riot.lol.clash.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.port.ClashPort;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link ClashPort} for fast, HTTP-free service tests. */
public class InMemoryClashPort implements ClashPort {

    private final Map<String, List<ClashPlayer>> byPuuid = new HashMap<>();

    public InMemoryClashPort put(String puuid, List<ClashPlayer> players) {
        byPuuid.put(puuid, players);
        return this;
    }

    @Override
    public List<ClashPlayer> getPlayersByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.getOrDefault(puuid, List.of());
    }
}
```

Create `ClashServiceTest.java`:

```java
package com.muddl.riot.lol.clash.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClashServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryClashPort port = new InMemoryClashPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final ClashService service = new ClashService(port, resolver);

    @Test
    void getClashByPlayer_resolvesPlayer_thenReturnsRegistrations() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        ClashPlayer player = ClashPlayer.builder().teamId("team-1").role("CAPTAIN").build();
        port.put("faker-puuid", List.of(player));

        assertThat(service.getClashByPlayer(PLATFORM, "Faker#KR1")).containsExactly(player);
    }

    @Test
    void getClashByPlayer_returnsEmpty_whenNoRegistrations() {
        when(resolver.resolvePuuid("puuid-raw")).thenReturn("puuid-raw");

        assertThat(service.getClashByPlayer(PLATFORM, "puuid-raw")).isEmpty();
    }
}
```

- [ ] **Step 8: Run the service test — expect FAIL (ClashService does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.clash.application.ClashServiceTest'`
Expected: FAIL — compilation error.

- [ ] **Step 9: Create the service**

Create `ClashService.java`:

```java
package com.muddl.riot.lol.clash.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.port.ClashPort;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot Clash-V1 player registrations. Player-keyed: resolves {@code player}
 * to a PUUID via {@link PlayerIdentityResolver} before calling the port (the {@code league} shape).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClashService {

    private final ClashPort clashPort;
    private final PlayerIdentityResolver identityResolver;

    public List<ClashPlayer> getClashByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching clash registrations on platform: {}", platform);
        return clashPort.getPlayersByPuuid(platform, puuid);
    }
}
```

- [ ] **Step 10: Run the service test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.clash.application.ClashServiceTest'`
Expected: PASS.

- [ ] **Step 11: Write the tool's failing test**

Create `ClashToolTest.java`:

```java
package com.muddl.riot.lol.clash.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.ClashService;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClashToolTest {

    @Mock
    private ClashService mockService;

    @InjectMocks
    private ClashTool tool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getClashByPlayer_passesPlatformAndPlayerThrough() {
        ClashPlayer player = ClashPlayer.builder().teamId("team-1").build();
        when(mockService.getClashByPlayer(PLATFORM, "Faker#KR1")).thenReturn(List.of(player));

        assertThat(tool.getClashByPlayer("NA1", "Faker#KR1")).containsExactly(player);
        verify(mockService).getClashByPlayer(PLATFORM, "Faker#KR1");
    }

    @Test
    void getClashByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> tool.getClashByPlayer("INVALID", "Faker#KR1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
```

- [ ] **Step 12: Run the tool test — expect FAIL (ClashTool does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.clash.adapter.in.mcp.ClashToolTest'`
Expected: FAIL — compilation error.

- [ ] **Step 13: Create the tool**

Create `ClashTool.java`:

```java
package com.muddl.riot.lol.clash.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.ClashService;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for League of Legends Clash registrations. Player-keyed: a single {@code player} param. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClashTool {

    private final ClashService clashService;

    @McpTool(
            name = "lol_clash_by_player",
            description =
                    "Get a League of Legends player's active Clash team registrations (team, position, and role per tournament).")
    public List<ClashPlayer> getClashByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting clash registrations for a player on platform: {}", platform);
        return clashService.getClashByPlayer(platform, player);
    }
}
```

- [ ] **Step 14: Run the tool test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.clash.adapter.in.mcp.ClashToolTest'`
Expected: PASS.

- [ ] **Step 15: Update the tool inventory test**

In `McpToolInventoryTest.java`, add the import:

```java
import com.muddl.riot.lol.clash.adapter.in.mcp.ClashTool;
```

Add `"lol_clash_by_player"` to `EXPECTED_TOOL_NAMES` and `ClashTool.class` to the `Stream.of(...)`.

- [ ] **Step 16: Add the changelog bullet**

Under `## [0.2.0]` → `### Added`:

```markdown
- `lol_clash_by_player` — a player's active Clash team registrations (Clash-V1).
```

- [ ] **Step 17: Format, run the full module test suite, and commit**

Run: `./gradlew spotlessApply :lol-mcp-server:test`
Expected: PASS.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/clash \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/clash \
        lol-mcp-server/src/test/resources/fixtures/clash-players.json \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java \
        lol-mcp-server/CHANGELOG.md
git commit -m "feat(clash): add lol_clash_by_player"
```

---

### Task 7: `match` inbound tools — expose the existing match context

`match` already has a domain, `MatchService`, `MatchPort`, and a WireMock-tested adapter, but no tool. Add a `PlayerIdentityResolver` dependency + a player-resolving method to `MatchService`, then a `MatchTool` with two tools: `lol_match_ids_by_player` (region-routed, player-keyed) and `lol_match_by_id` (region-routed, match-id-keyed, non-player). `analytics`' existing PUUID path is left untouched.

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/match/application/MatchService.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/match/adapter/in/mcp/MatchTool.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/match/application/MatchServiceTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/analytics/application/AnalyticsServiceTest.java:35` (constructor call-site update)
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/match/adapter/in/mcp/MatchToolTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`
- Modify: `lol-mcp-server/CHANGELOG.md`

**Interfaces:**
- Consumes: existing `MatchPort.getMatchIdsByPuuid(RiotApiRegionUri, String, Integer, Integer, Integer)`, `MatchPort.getMatchById(RiotApiRegionUri, String)`; `PlayerIdentityResolver.resolvePuuid(String)`; `RiotApiRegionUri` enum; `Match` domain.
- Produces: `MatchService.getMatchIdsByPlayer(RiotApiRegionUri, String player, Integer count, Integer start, Integer queue) → List<String>` (new); tools `lol_match_ids_by_player`, `lol_match_by_id`. **Note:** the existing `MatchService(MatchPort)` constructor becomes `MatchService(MatchPort, PlayerIdentityResolver)` — the `MatchServiceTest` and Spring wiring pick up the new arg; `analytics` is unaffected because it depends on the Spring-managed bean.

- [ ] **Step 1: Add the failing service test for `getMatchIdsByPlayer`**

Edit `MatchServiceTest.java`. Replace the whole file with the version below — it adds a mocked resolver, updates the constructor call to the two-arg form, and adds the resolve-then-fetch test (existing tests keep exercising the PUUID path):

```java
package com.muddl.riot.lol.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.match.domain.Match;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    private final InMemoryMatchPort matchPort = new InMemoryMatchPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final MatchService matchService = new MatchService(matchPort, resolver);

    @Test
    void getMatchIdsByPuuid_returnsStoredIds() {
        matchPort.putMatchIds("p", List.of("NA1_1", "NA1_2"));

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 20, 0, null)).containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchIdsByPuuid_honoursCountLimit() {
        matchPort.putMatchIds("p", List.of("NA1_1", "NA1_2", "NA1_3"));

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 2, 0, null)).containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchById_returnsStoredMatch() {
        Match expected = Match.builder().build();
        matchPort.putMatch("NA1_1", expected);

        assertThat(matchService.getMatchById(REGION, "NA1_1")).isSameAs(expected);
    }

    @Test
    void getMatchIdsByPuuid_returnsEmpty_whenUnknownPuuid() {
        assertThat(matchService.getMatchIdsByPuuid(REGION, "unknown", 20, 0, null))
                .isEmpty();
    }

    @Test
    void getMatchIdsByPlayer_resolvesPlayer_thenReturnsIds() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        matchPort.putMatchIds("faker-puuid", List.of("NA1_1", "NA1_2"));

        assertThat(matchService.getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0, null))
                .containsExactly("NA1_1", "NA1_2");
    }
}
```

- [ ] **Step 2: Run the service test — expect FAIL (constructor arity + missing method)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.match.application.MatchServiceTest'`
Expected: FAIL — compilation errors: `MatchService(MatchPort, PlayerIdentityResolver)` and `getMatchIdsByPlayer` do not exist.

- [ ] **Step 3: Add the resolver and the player-resolving method to `MatchService`**

Replace `MatchService.java` with:

```java
package com.muddl.riot.lol.match.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.match.application.port.MatchPort;
import com.muddl.riot.lol.match.domain.Match;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends match data. Delegates retrieval to the outbound
 * {@link MatchPort}; holds no HTTP concerns.
 *
 * <p>Player-keyed access ({@link #getMatchIdsByPlayer}) resolves the caller's {@code player} to a
 * PUUID via the shared {@link PlayerIdentityResolver} — the {@code league} shape. The PUUID-keyed
 * methods remain for internal composers such as {@code analytics}, which resolve identity themselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPort matchPort;
    private final PlayerIdentityResolver identityResolver;

    public List<String> getMatchIdsByPlayer(
            RiotApiRegionUri region, String player, Integer count, Integer start, Integer queue) {
        String puuid = identityResolver.resolvePuuid(player);
        return getMatchIdsByPuuid(region, puuid, count, start, queue);
    }

    public List<String> getMatchIdsByPuuid(
            RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        log.info("Fetching match IDs for PUUID: {}", puuid);
        return matchPort.getMatchIdsByPuuid(region, puuid, count, start, queue);
    }

    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        log.info("Fetching match details for match ID: {}", matchId);
        return matchPort.getMatchById(region, matchId);
    }
}
```

- [ ] **Step 4: Run the service test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.match.application.MatchServiceTest'`
Expected: PASS.

- [ ] **Step 5: Fix the `AnalyticsServiceTest` call-site and confirm analytics still passes (coexistence check)**

`AnalyticsService` (production) injects the Spring-managed `MatchService` bean and calls `getMatchIdsByPuuid`, which is unchanged — production is unaffected. But `AnalyticsServiceTest.java:35` constructs `MatchService` **directly** with the old one-arg constructor, so it will fail to compile after Step 3. That test already has a `PlayerIdentityResolver resolver = mock(...)` field in scope (line 29). Update line 35 from:

```java
            new AnalyticsService(resolver, summonerService, new MatchService(matchPort));
```

to:

```java
            new AnalyticsService(resolver, summonerService, new MatchService(matchPort, resolver));
```

Then run the analytics tests to prove the change is non-disturbing:

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.analytics.*'`
Expected: PASS.

- [ ] **Step 6: Write the tool's failing test**

Create `MatchToolTest.java`:

```java
package com.muddl.riot.lol.match.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.match.application.MatchService;
import com.muddl.riot.lol.match.domain.Match;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchToolTest {

    @Mock
    private MatchService mockMatchService;

    @InjectMocks
    private MatchTool matchTool;

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    @Test
    void getMatchIdsByPlayer_passesArgsThrough() {
        when(mockMatchService.getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0, null))
                .thenReturn(List.of("NA1_1", "NA1_2"));

        assertThat(matchTool.getMatchIdsByPlayer("AMERICAS", "Faker#KR1", 20, 0, null))
                .containsExactly("NA1_1", "NA1_2");
        verify(mockMatchService).getMatchIdsByPlayer(REGION, "Faker#KR1", 20, 0, null);
    }

    @Test
    void getMatchById_passesArgsThrough() {
        Match match = Match.builder().build();
        when(mockMatchService.getMatchById(REGION, "NA1_1")).thenReturn(match);

        assertThat(matchTool.getMatchById("AMERICAS", "NA1_1")).isSameAs(match);
        verify(mockMatchService).getMatchById(REGION, "NA1_1");
    }

    @Test
    void getMatchIdsByPlayer_invalidRegion_throws() {
        assertThatThrownBy(() -> matchTool.getMatchIdsByPlayer("INVALID", "Faker#KR1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
```

- [ ] **Step 7: Run the tool test — expect FAIL (MatchTool does not exist)**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.match.adapter.in.mcp.MatchToolTest'`
Expected: FAIL — compilation error, `MatchTool` not found.

- [ ] **Step 8: Create the tool**

Create `MatchTool.java`:

```java
package com.muddl.riot.lol.match.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.match.application.MatchService;
import com.muddl.riot.lol.match.domain.Match;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for League of Legends match data (Match-V5, region-routed). {@code lol_match_ids_by_player}
 * is player-keyed; {@code lol_match_by_id} is keyed by a match ID and takes no player (ADR-0014).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTool {

    private final MatchService matchService;

    @McpTool(
            name = "lol_match_ids_by_player",
            description = "Get a League of Legends player's recent match IDs, most recent first.")
    public List<String> getMatchIdsByPlayer(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "Number of match IDs to return, 1-100, defaults to 20", required = false)
                    Integer count,
            @McpToolParam(description = "Number of match IDs to skip (for paging), defaults to 0", required = false)
                    Integer start,
            @McpToolParam(description = "Optional: filter by Riot queue ID, e.g. 420 for ranked solo", required = false)
                    Integer queue) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        log.info("MCP Tool - Getting match IDs for a player in region: {}", region);
        return matchService.getMatchIdsByPlayer(region, player, count, start, queue);
    }

    @McpTool(
            name = "lol_match_by_id",
            description = "Get the full detail of one League of Legends match by its match ID.")
    public Match getMatchById(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The match ID, e.g. NA1_4567890123", required = true) String matchId) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        log.info("MCP Tool - Getting match detail for match ID: {}", matchId);
        return matchService.getMatchById(region, matchId);
    }
}
```

- [ ] **Step 9: Run the tool test — expect PASS**

Run: `./gradlew :lol-mcp-server:test --tests 'com.muddl.riot.lol.match.adapter.in.mcp.MatchToolTest'`
Expected: PASS.

- [ ] **Step 10: Update the tool inventory test (two new names, one new class)**

In `McpToolInventoryTest.java`, add the import:

```java
import com.muddl.riot.lol.match.adapter.in.mcp.MatchTool;
```

Add both `"lol_match_ids_by_player"` and `"lol_match_by_id"` to `EXPECTED_TOOL_NAMES`, and `MatchTool.class` to the `Stream.of(...)`. After this task the set has **13** names. For reference, the full expected set is now:

```java
    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "lol_account_by_player",
            "lol_summoner_by_player",
            "lol_spectator_current_game_by_player",
            "lol_analytics_player_matches",
            "lol_league_entries_by_player",
            "lol_league_apex_by_tier",
            "lol_champion_rotation",
            "lol_status_platform",
            "lol_champion_mastery_by_player",
            "lol_challenges_by_player",
            "lol_clash_by_player",
            "lol_match_ids_by_player",
            "lol_match_by_id");
```

- [ ] **Step 11: Add the changelog bullets**

Under `## [0.2.0]` → `### Added`:

```markdown
- `lol_match_ids_by_player` and `lol_match_by_id` — the match context's first inbound tools: a
  player's recent match IDs and full match detail by ID (Match-V5). `lol_match_by_id` is
  non-player-keyed (ADR-0014).
```

- [ ] **Step 12: Format, run the full module test suite, and commit**

Run: `./gradlew spotlessApply :lol-mcp-server:test`
Expected: PASS (13-tool `McpToolInventoryTest` green).

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/match \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/match \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/analytics/application/AnalyticsServiceTest.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java \
        lol-mcp-server/CHANGELOG.md
git commit -m "feat(match): expose match via lol_match_ids_by_player and lol_match_by_id"
```

---

### Task 8: ADR-0014, knowledge base, and module docs

Record the non-player-keyed contract extension in the decision log and propagate the five new contexts through the docs. No code; this is the "persist" half of the hydrate/persist protocol and the sanity pass.

**Files:**
- Create: `docs/knowledge/decisions/ADR-0014-non-player-keyed-tools.md`
- Modify: `docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md` (add a forward cross-link)
- Modify: `docs/knowledge/patterns/add-a-bounded-context.md` (non-player variant)
- Modify: `docs/knowledge/gotchas.md` (snake_case `@JsonProperty` note)
- Modify: `docs/knowledge/roadmap.md` (mark 1b ✅, record the falsifiable-criterion result)
- Modify: `lol-mcp-server/ARCHITECTURE.md` (context list + tool count)

**Interfaces:**
- Consumes: nothing (docs).
- Produces: the durable record servers 2–4 inherit.

- [ ] **Step 1: Write ADR-0014**

Create `docs/knowledge/decisions/ADR-0014-non-player-keyed-tools.md`:

```markdown
# ADR-0014 — Non-player-keyed tools extend the tool contract

**Status:** Accepted (sub-project 1b, 2026-07-18)

## Context

[ADR-0009](ADR-0009-mcp-tool-contract.md) established the tool contract on the `league` exemplar:
every tool is named `<game>_<context>_<action>`, and every **player-keyed** tool takes a single
`player` param (a Riot ID `GameName#TAG` or a raw PUUID) resolved to a PUUID in the application
service via `PlayerIdentityResolver`.

Sub-project 1b added the first tools that are keyed by something other than a player:

- `lol_champion_rotation` and `lol_status_platform` — keyed only by platform.
- `lol_match_by_id` — keyed by a match ID.

The `player` param convention has nothing to say about these, and forcing one would be nonsense.

## Decision

The `player` param convention of ADR-0009 applies **only to player-keyed endpoints**. A tool takes
**domain-appropriate params**: platform alone, a match ID, a tier, and so on. A non-player-keyed
context's service does **not** depend on `PlayerIdentityResolver`.

Everything else in ADR-0009 still holds for every tool: the `<game>_<context>_<action>` name, the
thin tool delegating to a service, the WireMock-adapter-plus-port-fake test pair, and portal-verified
endpoint paths.

## Consequences

- The handoff contract is no longer implicitly "player-keyed only." Servers 2–4 inherit both shapes.
- `McpToolInventoryTest` no longer assumes every tool carries a `player` param.
- The `add-a-bounded-context` pattern documents both the player-keyed (`league`) and non-player-keyed
  (`champion`/`status`) variants.
- **1a's falsifiable criterion held:** 1b added five contexts and the match tools with **no change to
  `riot-api-core` or `riot-account-core`**. Non-player contexts simply omit the resolver; player-keyed
  contexts reuse it exactly as `league` does.
```

- [ ] **Step 2: Cross-link ADR-0009**

In `docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md`, add a line near the top (under the status/context) noting the extension. Add this sentence at the end of the first section:

```markdown
> **Extended by [ADR-0014](ADR-0014-non-player-keyed-tools.md):** the single `player` param applies
> only to player-keyed endpoints; non-player-keyed tools (e.g. `lol_champion_rotation`,
> `lol_status_platform`, `lol_match_by_id`) take domain-appropriate params and no resolver.
```

- [ ] **Step 3: Add the non-player variant to the bounded-context pattern**

In `docs/knowledge/patterns/add-a-bounded-context.md`, in the handoff-contract callout near the top (after the existing "The tool takes a single `player` param ..." sentence), add:

```markdown
>
> **Non-player-keyed contexts** ([ADR-0014](../decisions/ADR-0014-non-player-keyed-tools.md)): when
> an endpoint is not keyed by a player (e.g. `champion` rotation, `status`, or a `match`-by-id
> lookup), the tool takes domain-appropriate params instead of `player`, and the service does **not**
> depend on `PlayerIdentityResolver`. `champion` and `status` in `lol-mcp-server` are the reference
> non-player-keyed contexts, as `league` is for player-keyed ones. Everything else — package
> skeleton, `<game>_<context>_<action>` name, WireMock + port-fake tests — is identical.
```

- [ ] **Step 4: Record the snake_case gotcha**

Append to `docs/knowledge/gotchas.md` (follow the file's existing entry format — add a new section at the end):

```markdown
## Riot JSON with snake_case keys needs `@JsonProperty`

Most Riot LoL DTOs are camelCase and map by field name, but **LoL-Status-V4** uses `snake_case` for
some keys (`incident_severity`, `maintenance_status`). Jackson here matches exact names, so those
fields need an explicit `@JsonProperty("incident_severity")` on the camelCase Java field or they
deserialize to `null` silently — the adapter's WireMock test asserts the parsed value to catch it.
Seen in `status`'s `StatusEntry` (sub-project 1b). Do not switch on a global naming strategy for one
context; annotate the specific fields.
```

- [ ] **Step 5: Mark 1b done in the roadmap**

In `docs/knowledge/roadmap.md`:

Change the status-table row (line ~17) from:

```markdown
| 1b | LoL parity — breadth | ⏳ Not started | — |
```

to:

```markdown
| 1b | LoL parity — breadth | ✅ Done | [2026-07-18](../superpowers/specs/2026-07-18-lol-parity-breadth-design.md) |
```

Then replace the body of the `### 1b — LoL parity: breadth` section's status line. After the existing description paragraphs, add:

```markdown
**Progress:** ✅ Complete. Five contexts added — `champion` (rotation), `status`, `championmastery`,
`challenges`, `clash` — plus the `match` context's first inbound tools (`lol_match_ids_by_player`,
`lol_match_by_id`). The tool surface grew from 6 to 13. Non-player-keyed tools extend the contract
([ADR-0014](decisions/ADR-0014-non-player-keyed-tools.md)). **1a's falsifiable criterion held:** the
five contexts and the match tools landed with no change to `riot-api-core` or `riot-account-core`.
`lol-mcp-server` released as 0.2.0.
```

- [ ] **Step 6: Update the server ARCHITECTURE context list**

In `lol-mcp-server/ARCHITECTURE.md`, extend the bounded-contexts tree (the block around lines 12–17) to include the five new contexts and note match now has a tool. Change the `match/` line and append the new contexts so the tree reads:

```
├── account/          Thin @McpTool only — the real context lives in riot-account-core (platform N/A)
├── summoner/         Summoner profiles (platform-routed)
├── match/            Match IDs and detail, Match-V5 (region-routed) — now has MatchTool
├── spectator/        Live-game (current-game) data, Spectator-V5, PUUID-keyed (platform-routed)
├── analytics/        Composing context — aggregates account + summoner + match; has no Riot adapter
├── league/           Ranked entries + apex leagues, League-V4 (platform-routed) — the exemplar context
├── championmastery/  Champion mastery by player, Champion-Mastery-V4 (platform-routed)
├── champion/         Free-to-play rotation, Champion-V3 (platform-routed) — non-player-keyed
├── challenges/       Player challenge standing, LoL-Challenges-V1 (platform-routed)
├── status/           Platform status/incidents, LoL-Status-V4 (platform-routed) — non-player-keyed
└── clash/            Player Clash registrations, Clash-V1 (platform-routed)
```

Also update any prose that says the server ships "six tools" to "13 tools", and update the exceptions note: `match` is no longer tool-less (it now has `MatchTool`); the deliberate-exception note about match "no MCP tool — consumed only by analytics" should be removed or rephrased to "region-routed; both an inbound tool and an analytics consumer."

- [ ] **Step 7: Run the full build (the CI gate) to confirm docs gates + ArchUnit + coverage pass**

Run: `./gradlew build`
Expected: PASS — `verifyModuleDocs`, `verifyRelease`, ArchUnit (`HexagonalArchitectureTest` including the context-independence slice and the account-domain confinement rule), JaCoCo, and Spotless all green with the five new contexts present.

- [ ] **Step 8: Commit**

```bash
git add docs/knowledge/decisions/ADR-0014-non-player-keyed-tools.md \
        docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md \
        docs/knowledge/patterns/add-a-bounded-context.md \
        docs/knowledge/gotchas.md \
        docs/knowledge/roadmap.md \
        lol-mcp-server/ARCHITECTURE.md
git commit -m "docs: record 1b — ADR-0014, non-player-keyed pattern, roadmap, contexts"
```

---

### Task 9: Live eval coverage (post-merge, non-gating)

Add one live mcp-eval scenario per new tool per the `add-live-eval` skill, so every new context ships live coverage over both transports. This never gates a merge and needs `ANTHROPIC_API_KEY`.

**Files:**
- Modify/Create: eval scenario files under `eval/tests/` (exact paths per the `add-live-eval` skill and the existing eval layout).

**Interfaces:**
- Consumes: the 13 shipped tools.
- Produces: live scenarios for the 7 new tools.

- [ ] **Step 1: Invoke the `add-live-eval` skill and follow it for the new tools**

Run the skill and add a scenario for each of: `lol_champion_rotation`, `lol_status_platform`, `lol_champion_mastery_by_player`, `lol_challenges_by_player`, `lol_clash_by_player`, `lol_match_ids_by_player`, `lol_match_by_id`. Follow the skill's file layout and canary conventions — do not hand-author scenarios outside its pattern.

- [ ] **Step 2: Commit**

```bash
git add eval/
git commit -m "test(eval): add live scenarios for the seven 1b tools"
```

---

### Task 10: Release `lol-mcp-server` 0.2.0

Finalize the changelog date and tag the module. Follow the `prepare-release` skill, which encodes the bump classification and the library→server cascade check.

**Files:**
- Modify: `lol-mcp-server/CHANGELOG.md` (fill the release date)

**Interfaces:**
- Consumes: the green build from Task 8.
- Produces: the `lol-mcp-server/v0.2.0` tag.

- [ ] **Step 1: Invoke the `prepare-release` skill**

Run the `prepare-release` skill for `lol-mcp-server`. It will confirm the bump is a minor (additive, seven new tools, pre-1.0), confirm no library changed (so no cascade), and walk the tag flow.

- [ ] **Step 2: Set the changelog release date**

In `lol-mcp-server/CHANGELOG.md`, change `## [0.2.0] - unreleased` to `## [0.2.0] - 2026-07-18` (or the actual release date).

- [ ] **Step 3: Verify the release gate and full build**

Run: `./gradlew build`
Expected: PASS.

- [ ] **Step 4: Commit and tag**

```bash
git add lol-mcp-server/CHANGELOG.md
git commit -m "chore(lol): release lol-mcp-server 0.2.0"
git tag lol-mcp-server/v0.2.0
```

---

## Self-Review

**Spec coverage:**

- Five new contexts (champion-mastery, champion, challenges, status, clash) → Tasks 2–6. ✅
- Match context's first inbound tool(s) → Task 7. ✅
- Non-player-keyed contract extension + ADR-0014 → Task 8 (built into Tasks 2, 3, 7 tool shapes). ✅
- Pragmatic-breadth inventory (7 new, 13 total) → inventory test reaches 13 in Task 7. ✅
- No library change (falsifiable criterion) → asserted by `./gradlew build` (ArchUnit + compile) in Task 8; recorded in ADR-0014 and roadmap. ✅
- `lol-mcp-server` 0.1.0 → 0.2.0, no library bump → Task 1 (open) + Task 10 (release). ✅
- Testing: WireMock adapter test + port-fake service test per context; resolver mocked only for player-keyed; inventory test to 13 → each of Tasks 2–7. ✅
- Live eval per new tool → Task 9. ✅
- Persistence: roadmap, ARCHITECTURE, CHANGELOG, ADR-0014, pattern, gotchas → Task 8. ✅
- Sequencing: non-player first, then player-keyed, then match, then docs/release → Task order 2→3→4→5→6→7→8→9→10. ✅

**Placeholder scan:** No TBD/TODO. Every code step shows complete code; every test step shows the command and expected result. Endpoint paths are stated with a portal-verify instruction (a standing constraint, not a placeholder). ✅

**Type consistency:**
- `PlayerIdentityResolver.resolvePuuid(String) → String` used identically in Tasks 4, 5, 6, 7. ✅
- Port method names match across port/adapter/fake/service/test in each task (`getChampionRotation`, `getPlatformStatus`, `getMasteryByPuuid`, `getPlayerDataByPuuid`, `getPlayersByPuuid`, `getMatchIdsByPuuid`/`getMatchIdsByPlayer`). ✅
- `RiotApiClient.platform(...)` for platform-routed contexts, `.regional(...)` already used by the existing match adapter (unchanged in Task 7). ✅
- Tool names in each `@McpTool` match the strings added to `EXPECTED_TOOL_NAMES` and the final 13-name set in Task 7 Step 10. ✅
- `MatchService` two-arg constructor introduced in Task 7 Step 3 matches the test's `new MatchService(matchPort, resolver)` in Step 1 and the `@RequiredArgsConstructor` field order (`matchPort`, `identityResolver`). ✅
```
