# TFT MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up `tft-mcp-server` — a second Riot game server exposing the Teamfight Tactics TFT-v1 API as 11 MCP tools across 6 bounded contexts, built entirely on the existing `riot-api-core` and `riot-account-core` libraries.

**Architecture:** A new Gradle module `tft-mcp-server` (package root `com.muddl.riot.tft`), following the LoL server's mini-hexagon-per-context template: each context has `domain/`, `application/` (+ `application/port/`), `adapter/in/mcp/`, `adapter/out/riot/`. Contexts: `account` (tool-only, delegates to `riot-account-core`), `summoner`, `league`, `match`, `status`, `analytics`. TFT reuses LoL's platform/region host enums, so no routing changes.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring AI 2.0 MCP (`@McpTool`), Gradle, Lombok, JUnit 5 + AssertJ, WireMock (adapter tests), Mockito (service tests), ArchUnit, mcp-eval (Python live evals).

## Global Constraints

- **Zero changes to `riot-api-core` or `riot-account-core`.** This is the falsifiable success criterion (spec §Goal). If a library change seems forced, STOP and record it as a finding in `docs/knowledge/gotchas.md` rather than editing a library.
- **Full test suite runs offline, no Riot API key.** WireMock for outbound adapters, in-memory port fakes for application services. Never write a test needing a live key or network.
- **Tool contract (ADR-0009):** every tool name is `tft_<context>_<action>`, snake_case, stable. Every player-keyed tool takes a single `player` param (a Riot ID `GameName#TAG` or a raw PUUID). Do not deviate.
- **DTO convention:** `@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)`. Nested `@Builder` static classes also need `@NoArgsConstructor @AllArgsConstructor`. Box numeric fields that Riot may return null (`Long`, not `long`) — see `gotchas.md`.
- **Endpoint paths in this plan are design intent, verified during implementation.** Before finalizing each outbound adapter, confirm the exact TFT-v1 path against the live Riot developer portal (<https://developer.riotgames.com/apis>) and capture a real response shape into the fixture JSON. Never assume from this plan alone.
- **Ports** are interfaces named `<Context>Port` in `<context>.application.port`; services depend on the port, never a `RestClient`. `RestClient` usage only in `adapter.out.riot`; `@McpTool` only in `adapter.in.mcp`.
- **Commit after every green step.** Branch is `feat/tft-mcp-server` (already created).

---

### Task 1: Module scaffold

**Files:**
- Modify: `settings.gradle`
- Create: `tft-mcp-server/build.gradle`
- Create: `tft-mcp-server/src/main/java/com/muddl/riot/tft/TftMcpServerApplication.java`
- Create: `tft-mcp-server/src/main/resources/application.yml`
- Create: `tft-mcp-server/src/main/resources/application-stdio.yml`
- Create: `tft-mcp-server/src/main/resources/application-sse.yml`
- Create: `tft-mcp-server/src/main/resources/additional-spring-configuration-metadata.json`

**Interfaces:**
- Produces: a compilable `tft-mcp-server` Gradle module, package root `com.muddl.riot.tft`, MCP server advertised as `tft-mcp-server`.

- [ ] **Step 1: Register the module in `settings.gradle`**

Append to `settings.gradle`:

```groovy
include 'tft-mcp-server'
```

- [ ] **Step 2: Create `tft-mcp-server/build.gradle`**

Mirror `lol-mcp-server/build.gradle` exactly, but note the module owns its version. Full content:

```groovy
plugins {
	id 'riot-java-conventions'
	id 'org.springframework.boot'
}

// Independent of the other modules — see ADR-0010. First release of a new server.
version = '0.1.0'

dependencies {
	implementation project(':riot-api-core')
	implementation project(':riot-account-core')
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'

	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
	testImplementation testFixtures(project(':riot-api-core'))
	testImplementation testFixtures(project(':riot-account-core'))
}

// Provenance: record which library versions this jar embeds (see ADR-0010).
def apiCoreVersion = providers.provider { project(':riot-api-core').version.toString() }
def accountCoreVersion = providers.provider { project(':riot-account-core').version.toString() }

tasks.named('bootJar') {
	manifest {
		attributes(
			'Implementation-Title': project.name,
			'Implementation-Version': project.version.toString(),
			'Riot-Api-Core-Version': apiCoreVersion,
			'Riot-Account-Core-Version': accountCoreVersion)
	}
}
```

- [ ] **Step 3: Create the application class**

`tft-mcp-server/src/main/java/com/muddl/riot/tft/TftMcpServerApplication.java`:

```java
package com.muddl.riot.tft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Teamfight Tactics MCP server. Exposes the Riot TFT-v1 API to AI
 * models as MCP tools, built on the shared riot-api-core and riot-account-core libraries.
 */
@SpringBootApplication
public class TftMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TftMcpServerApplication.class, args);
    }
}
```

- [ ] **Step 4: Create the three resource YAMLs**

`application.yml` (advertise the TFT server name):

```yaml
spring:
  application:
    name: tft-mcp-server
  profiles:
    default: stdio
  ai:
    mcp:
      server:
        name: tft-mcp-server
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages
        annotation-scanner:
          enabled: true

riot:
  apiKey: ${RIOT_API_KEY} # refreshes every day until you register your product
  region: americas
```

`application-stdio.yml` — copy `lol-mcp-server/src/main/resources/application-stdio.yml` verbatim (its comment block about stdout purity applies unchanged):

```yaml
# STDIO transport: the JSON-RPC protocol stream IS stdout.
#
# Anything else written to stdout — the Spring banner, a log line from any @Slf4j class —
# interleaves with protocol frames and corrupts the stream. The client sees malformed JSON
# and drops the connection, with a failure mode that looks nothing like its cause.
# Every setting below exists to keep stdout clean; do not remove one without understanding this.
spring:
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        stdio: true

logging:
  pattern:
    console: ""  # empty pattern disables the console appender entirely
  file:
    name: ${RIOT_MCP_LOG_FILE:./riot-mcp-server.log}
```

`application-sse.yml` — copy `lol-mcp-server/src/main/resources/application-sse.yml` verbatim:

```yaml
# SSE transport: HTTP on 8080, messages at /mcp/messages.
# Logging to stdout is safe here — the protocol runs over HTTP, not the console.
spring:
  main:
    web-application-type: servlet
  ai:
    mcp:
      server:
        stdio: false

server:
  port: ${SERVER_PORT:8080}
```

`additional-spring-configuration-metadata.json` — copy `lol-mcp-server`'s verbatim:

```json
{
  "properties": [
    {
      "name": "riot.apikey",
      "type": "java.lang.String",
      "description": "Api Key for Riot Developer API."
    },
    {
      "name": "riot.region",
      "type": "com.muddl.riot.core.enums.RiotApiRegionUri",
      "description": "Region Enum to determine URI for the calls to be made against."
    }
  ]
}
```

- [ ] **Step 5: Verify the module compiles**

Run: `./gradlew :tft-mcp-server:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle tft-mcp-server/build.gradle tft-mcp-server/src/main
git commit -m "feat(tft): scaffold tft-mcp-server module"
```

---

### Task 2: `status` context (exemplar vertical slice)

The simplest context — build it end-to-end first as the pattern every later context copies. TFT status-v1 has the same JSON shape as LoL status-v4.

**Files:**
- Create: `.../tft/status/domain/PlatformStatus.java`, `StatusEntry.java`, `StatusContent.java`
- Create: `.../tft/status/application/port/StatusPort.java`
- Create: `.../tft/status/application/StatusService.java`
- Create: `.../tft/status/adapter/out/riot/RiotTftStatusAdapter.java`
- Create: `.../tft/status/adapter/in/mcp/StatusTool.java`
- Test: `.../tft/status/adapter/out/riot/RiotTftStatusAdapterTest.java`
- Test: `.../tft/status/application/StatusServiceTest.java`, `InMemoryStatusPort.java`
- Fixture: `tft-mcp-server/src/test/resources/fixtures/tft-status-platform.json`

(All `.../tft/` paths are under `tft-mcp-server/src/main/java/com/muddl/riot/` or `.../src/test/java/com/muddl/riot/`.)

**Interfaces:**
- Produces: `StatusPort.getPlatformStatus(RiotApiPlatformUri) -> PlatformStatus`; MCP tool `tft_status_platform`.

- [ ] **Step 1: Write the domain DTOs**

`domain/PlatformStatus.java`:

```java
package com.muddl.riot.tft.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A TFT platform's operational status: current maintenances and incidents (Riot TFT-Status-V1). */
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

`domain/StatusEntry.java`:

```java
package com.muddl.riot.tft.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One maintenance or incident from Riot TFT-Status-V1. Riot uses snake_case for two keys. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusEntry {
    // Boxed: Riot number fields can come back null; a primitive long fails deserialization.
    private Long id;

    @JsonProperty("maintenance_status")
    private String maintenanceStatus;

    @JsonProperty("incident_severity")
    private String incidentSeverity;

    private List<StatusContent> titles;
    private List<StatusContent> updates;
}
```

`domain/StatusContent.java`:

```java
package com.muddl.riot.tft.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A localized status message from Riot TFT-Status-V1. */
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

- [ ] **Step 2: Write the port and the in-memory fake**

`application/port/StatusPort.java`:

```java
package com.muddl.riot.tft.status.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.domain.PlatformStatus;

/** Outbound port for Riot TFT-Status-V1 platform status. Platform-routed. */
public interface StatusPort {
    PlatformStatus getPlatformStatus(RiotApiPlatformUri platform);
}
```

`application/InMemoryStatusPort.java` (in test sources):

```java
package com.muddl.riot.tft.status.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
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

- [ ] **Step 3: Write the failing service test**

`application/StatusServiceTest.java`:

```java
package com.muddl.riot.tft.status.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import org.junit.jupiter.api.Test;

class StatusServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryStatusPort port = new InMemoryStatusPort();
    private final StatusService service = new StatusService(port);

    @Test
    void getPlatformStatus_delegatesToPort() {
        PlatformStatus expected =
                PlatformStatus.builder().id("NA1").name("North America").build();
        port.put(PLATFORM, expected);

        assertThat(service.getPlatformStatus(PLATFORM)).isSameAs(expected);
    }
}
```

- [ ] **Step 4: Run the test — expect FAIL (StatusService does not exist)**

Run: `./gradlew :tft-mcp-server:test --tests '*status*StatusServiceTest'`
Expected: FAIL — cannot find symbol `StatusService`.

- [ ] **Step 5: Write the service**

`application/StatusService.java`:

```java
package com.muddl.riot.tft.status.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Application service for TFT platform status. Non-player-keyed (ADR-0014). */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusPort statusPort;

    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        log.info("Fetching TFT platform status on platform: {}", platform);
        return statusPort.getPlatformStatus(platform);
    }
}
```

- [ ] **Step 6: Run the service test — expect PASS**

Run: `./gradlew :tft-mcp-server:test --tests '*status*StatusServiceTest'`
Expected: PASS.

- [ ] **Step 7: Add the fixture JSON**

`src/test/resources/fixtures/tft-status-platform.json` (verify the shape against a live TFT-status-v1 response; representative content):

```json
{
  "id": "NA1",
  "name": "North America",
  "locales": ["en_US"],
  "maintenances": [],
  "incidents": [
    {
      "id": 1,
      "incident_severity": "warning",
      "titles": [{ "locale": "en_US", "content": "Login issues" }],
      "updates": []
    }
  ]
}
```

- [ ] **Step 8: Write the failing WireMock adapter test**

Invoke the `add-adapter-test` skill for the recipe, then use this exact content. `adapter/out/riot/RiotTftStatusAdapterTest.java`:

```java
package com.muddl.riot.tft.status.adapter.out.riot;

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
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftStatusAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String STATUS_URL = "/tft/status/v1/platform-data";

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

        adapter = new RiotTftStatusAdapter(new RiotApiClient(properties));
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
                        .withBody(Fixtures.read("tft-status-platform.json"))));

        PlatformStatus status = adapter.getPlatformStatus(PLATFORM);

        assertThat(status.getId()).isEqualTo("NA1");
        assertThat(status.getIncidents()).hasSize(1);
        assertThat(status.getIncidents().get(0).getIncidentSeverity()).isEqualTo("warning");
        assertThat(status.getIncidents().get(0).getTitles().get(0).getContent()).isEqualTo("Login issues");
        verify(getRequestedFor(urlEqualTo(STATUS_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(STATUS_URL))
                .willReturn(aResponse().withStatus(503).withBody("unavailable")));

        assertThatThrownBy(() -> adapter.getPlatformStatus(PLATFORM))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(503);
    }
}
```

Note: `Fixtures.read` resolves fixtures from this module's own `src/test/resources/fixtures/`.

- [ ] **Step 9: Run the adapter test — expect FAIL (adapter does not exist)**

Run: `./gradlew :tft-mcp-server:test --tests '*RiotTftStatusAdapterTest'`
Expected: FAIL — cannot find symbol `RiotTftStatusAdapter`.

- [ ] **Step 10: Write the outbound adapter**

`adapter/out/riot/RiotTftStatusAdapter.java`:

```java
package com.muddl.riot.tft.status.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.status.application.port.StatusPort;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot TFT-Status-V1 API adapter. Platform status is platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftStatusAdapter implements StatusPort {

    private final RiotApiClient riotApiClient;

    @Override
    public PlatformStatus getPlatformStatus(RiotApiPlatformUri platform) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/status/v1/platform-data")
                .retrieve()
                .body(PlatformStatus.class);
    }
}
```

- [ ] **Step 11: Write the inbound MCP tool**

`adapter/in/mcp/StatusTool.java`:

```java
package com.muddl.riot.tft.status.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.status.application.StatusService;
import com.muddl.riot.tft.status.domain.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for TFT platform status. Non-player-keyed (ADR-0014). */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusTool {

    private final StatusService statusService;

    @McpTool(
            name = "tft_status_platform",
            description = "Get the operational status (current maintenances and incidents) of a Teamfight Tactics platform.")
    public PlatformStatus getPlatformStatus(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT platform status on platform: {}", platform);
        return statusService.getPlatformStatus(platform);
    }
}
```

- [ ] **Step 12: Run the whole context — expect PASS**

Run: `./gradlew :tft-mcp-server:test --tests '*status*'`
Expected: PASS (both adapter tests and the service test).

- [ ] **Step 13: Commit**

```bash
git add tft-mcp-server/src
git commit -m "feat(tft): status context (tft_status_platform)"
```

---

### Task 3: `summoner` context

TFT summoner-v1 is platform-routed, keyed by PUUID. The service resolves `player -> PUUID` via `PlayerIdentityResolver` and keeps a `getSummonerByPuuid` overload for the analytics composer (Task 7).

**Files:**
- Create: `.../tft/summoner/domain/Summoner.java`
- Create: `.../tft/summoner/application/port/SummonerPort.java`
- Create: `.../tft/summoner/application/SummonerService.java`
- Create: `.../tft/summoner/adapter/out/riot/RiotTftSummonerAdapter.java`
- Create: `.../tft/summoner/adapter/in/mcp/SummonerTool.java`
- Test: `.../tft/summoner/adapter/out/riot/RiotTftSummonerAdapterTest.java`
- Test: `.../tft/summoner/application/SummonerServiceTest.java`, `InMemorySummonerPort.java`
- Fixture: `src/test/resources/fixtures/tft-summoner.json`

**Interfaces:**
- Consumes: `PlayerIdentityResolver.resolvePuuid(String) -> String` (from `com.muddl.riot.account.identity`).
- Produces: `SummonerPort.getSummonerByPuuid(RiotApiPlatformUri, String) -> Summoner`; `SummonerService.getSummonerByPlayer(...)` and `getSummonerByPuuid(...)`; MCP tool `tft_summoner_by_player`.

- [ ] **Step 1: Domain DTO** — `domain/Summoner.java`:

```java
package com.muddl.riot.tft.summoner.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A TFT summoner profile (Riot TFT-Summoner-V1). Name / encrypted-id fields are treated as possibly
 * absent under the PUUID migration; the domain does not depend on them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Summoner {
    private String puuid;
    private int profileIconId;
    private long revisionDate;
    private long summonerLevel;
}
```

- [ ] **Step 2: Port + in-memory fake**

`application/port/SummonerPort.java`:

```java
package com.muddl.riot.tft.summoner.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.domain.Summoner;

/** Outbound port for Riot TFT-Summoner-V1 data. Platform-routed. */
public interface SummonerPort {
    Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid);
}
```

`application/InMemorySummonerPort.java` (test sources):

```java
package com.muddl.riot.tft.summoner.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SummonerPort}, keyed by PUUID. */
public class InMemorySummonerPort implements SummonerPort {

    private final Map<String, Summoner> byPuuid = new HashMap<>();

    public InMemorySummonerPort put(String puuid, Summoner summoner) {
        byPuuid.put(puuid, summoner);
        return this;
    }

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.get(puuid);
    }
}
```

- [ ] **Step 3: Failing service test** — `application/SummonerServiceTest.java`:

```java
package com.muddl.riot.tft.summoner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySummonerPort port = new InMemorySummonerPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SummonerService service = new SummonerService(port, resolver);

    @Test
    void getSummonerByPlayer_resolvesPlayer_thenReturnsSummoner() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn("puuid-1");
        Summoner expected = Summoner.builder().puuid("puuid-1").summonerLevel(200).build();
        port.put("puuid-1", expected);

        assertThat(service.getSummonerByPlayer(PLATFORM, "Player#NA1")).isSameAs(expected);
    }

    @Test
    void getSummonerByPuuid_delegatesToPort_withoutResolving() {
        Summoner expected = Summoner.builder().puuid("puuid-2").build();
        port.put("puuid-2", expected);

        assertThat(service.getSummonerByPuuid(PLATFORM, "puuid-2")).isSameAs(expected);
    }
}
```

- [ ] **Step 4: Run — expect FAIL** (`SummonerService` missing).

Run: `./gradlew :tft-mcp-server:test --tests '*summoner*SummonerServiceTest'`
Expected: FAIL.

- [ ] **Step 5: Service** — `application/SummonerService.java`:

```java
package com.muddl.riot.tft.summoner.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for TFT summoner lookups. Resolves the caller's {@code player} to a PUUID via
 * the shared {@link PlayerIdentityResolver}. {@code getSummonerByPuuid} is retained for the
 * analytics composer, which already holds a PUUID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final SummonerPort summonerPort;
    private final PlayerIdentityResolver identityResolver;

    public Summoner getSummonerByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        return getSummonerByPuuid(platform, puuid);
    }

    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching TFT summoner for PUUID: {} on platform: {}", puuid, platform);
        return summonerPort.getSummonerByPuuid(platform, puuid);
    }
}
```

- [ ] **Step 6: Run — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*summoner*SummonerServiceTest'`

- [ ] **Step 7: Fixture** — `src/test/resources/fixtures/tft-summoner.json` (verify shape against live TFT-summoner-v1):

```json
{
  "puuid": "test-puuid-abc",
  "profileIconId": 29,
  "revisionDate": 1699999999000,
  "summonerLevel": 350
}
```

- [ ] **Step 8: Failing WireMock adapter test.** Invoke `add-adapter-test`, then `adapter/out/riot/RiotTftSummonerAdapterTest.java`. Model it on `RiotTftStatusAdapterTest` (Task 2 Step 8) with these differences: constant `SUMMONER_URL = "/tft/summoner/v1/summoners/by-puuid/test-puuid-abc"`; construct `adapter = new RiotTftSummonerAdapter(new RiotApiClient(properties))`; call `adapter.getSummonerByPuuid(PLATFORM, "test-puuid-abc")`; assert `summoner.getSummonerLevel()` is `350` and `summoner.getProfileIconId()` is `29`; the header-verify and the 404→`RiotApiException` case are identical in structure to Task 2.

```java
package com.muddl.riot.tft.summoner.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftSummonerAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc";
    private static final String SUMMONER_URL = "/tft/summoner/v1/summoners/by-puuid/" + PUUID;

    private WireMockServer wireMock;
    private SummonerPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());
        adapter = new RiotTftSummonerAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getSummonerByPuuid_parsesResponse_andSendsToken() {
        stubFor(get(urlEqualTo(SUMMONER_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-summoner.json"))));

        Summoner summoner = adapter.getSummonerByPuuid(PLATFORM, PUUID);

        assertThat(summoner.getSummonerLevel()).isEqualTo(350);
        assertThat(summoner.getProfileIconId()).isEqualTo(29);
        verify(getRequestedFor(urlEqualTo(SUMMONER_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void notFound_mapsToRiotApiException() {
        stubFor(get(urlEqualTo(SUMMONER_URL)).willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getSummonerByPuuid(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
```

- [ ] **Step 9: Run — expect FAIL** (adapter missing). `./gradlew :tft-mcp-server:test --tests '*RiotTftSummonerAdapterTest'`

- [ ] **Step 10: Outbound adapter** — `adapter/out/riot/RiotTftSummonerAdapter.java`:

```java
package com.muddl.riot.tft.summoner.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.summoner.application.port.SummonerPort;
import com.muddl.riot.tft.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot TFT-Summoner-V1 API adapter. Platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftSummonerAdapter implements SummonerPort {

    private final RiotApiClient riotApiClient;

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/summoner/v1/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(Summoner.class);
    }
}
```

- [ ] **Step 11: Inbound tool** — `adapter/in/mcp/SummonerTool.java`:

```java
package com.muddl.riot.tft.summoner.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.summoner.application.SummonerService;
import com.muddl.riot.tft.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for TFT summoner lookups. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerTool {

    private final SummonerService summonerService;

    @McpTool(
            name = "tft_summoner_by_player",
            description = "Get Teamfight Tactics summoner information by player (a Riot ID as GameName#TAG, or a raw PUUID).")
    public Summoner getSummonerByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT summoner for a player on platform: {}", platform);
        return summonerService.getSummonerByPlayer(platform, player);
    }
}
```

- [ ] **Step 12: Run the context — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*summoner*'`

- [ ] **Step 13: Commit**

```bash
git add tft-mcp-server/src
git commit -m "feat(tft): summoner context (tft_summoner_by_player)"
```

---

### Task 4: `match` context

TFT match-v1 is region-routed, two tools. The match DTO graph is net-new and is the bulk of the modeling effort. Match-ids takes `start`/`count` only (no `queue` filter, unlike LoL).

**Files:**
- Create DTOs: `.../tft/match/domain/` — `TftMatch.java`, `MatchMetadata.java`, `MatchInfo.java`, `Participant.java`, `Trait.java`, `Unit.java`, `Companion.java`
- Create: `.../tft/match/application/port/MatchPort.java`, `.../application/MatchService.java`
- Create: `.../tft/match/adapter/out/riot/RiotTftMatchAdapter.java`, `.../adapter/in/mcp/MatchTool.java`
- Test: `.../tft/match/adapter/out/riot/RiotTftMatchAdapterTest.java`
- Test: `.../tft/match/application/MatchServiceTest.java`, `InMemoryMatchPort.java`
- Fixtures: `src/test/resources/fixtures/tft-match-ids.json`, `tft-match.json`

**Interfaces:**
- Consumes: `PlayerIdentityResolver.resolvePuuid`.
- Produces: `MatchPort.getMatchIdsByPuuid(RiotApiRegionUri, String puuid, Integer count, Integer start) -> List<String>` and `getMatchById(RiotApiRegionUri, String matchId) -> TftMatch`; `MatchService` with `getMatchIdsByPlayer`, `getMatchIdsByPuuid`, `getMatchById`; MCP tools `tft_match_ids_by_player`, `tft_match_by_id`. `TftMatch.getInfo().getParticipants() -> List<Participant>`, each with `getPuuid()`, `getPlacement()`, `getLevel()`, `getGoldLeft()`, `getTraits()`, `getUnits()` — consumed by analytics (Task 7).

- [ ] **Step 1: Write the domain DTOs**

`domain/TftMatch.java`:

```java
package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A full TFT match (Riot TFT-Match-V1): routing metadata plus the game info. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TftMatch {
    private MatchMetadata metadata;
    private MatchInfo info;
}
```

`domain/MatchMetadata.java`:

```java
package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** TFT match metadata: the match id and the list of participant PUUIDs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchMetadata {
    @JsonProperty("data_version")
    private String dataVersion;

    @JsonProperty("match_id")
    private String matchId;

    private List<String> participants;
}
```

`domain/MatchInfo.java`:

```java
package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** TFT game info: timing, set/version, and per-player participation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchInfo {
    @JsonProperty("game_datetime")
    private long gameDatetime;

    @JsonProperty("game_length")
    private double gameLength;

    @JsonProperty("game_version")
    private String gameVersion;

    @JsonProperty("queue_id")
    private int queueId;

    @JsonProperty("tft_set_number")
    private int tftSetNumber;

    @JsonProperty("tft_game_type")
    private String tftGameType;

    private List<Participant> participants;
}
```

`domain/Participant.java`:

```java
package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One player's participation in a TFT match: placement, board level, and the comp they fielded. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Participant {
    private String puuid;
    private int placement;
    private int level;

    @JsonProperty("gold_left")
    private int goldLeft;

    @JsonProperty("last_round")
    private int lastRound;

    @JsonProperty("players_eliminated")
    private int playersEliminated;

    @JsonProperty("time_eliminated")
    private double timeEliminated;

    @JsonProperty("total_damage_to_players")
    private int totalDamageToPlayers;

    private Companion companion;
    private List<Trait> traits;
    private List<Unit> units;
    private List<String> augments;
}
```

`domain/Trait.java`:

```java
package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One trait a participant activated. {@code tierCurrent > 0} means the trait was active. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trait {
    private String name;

    @JsonProperty("num_units")
    private int numUnits;

    private int style;

    @JsonProperty("tier_current")
    private int tierCurrent;

    @JsonProperty("tier_total")
    private int tierTotal;
}
```

`domain/Unit.java`:

```java
package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One champion a participant fielded. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Unit {
    @JsonProperty("character_id")
    private String characterId;

    private int tier;
    private int rarity;
    private List<String> itemNames;
}
```

`domain/Companion.java`:

```java
package com.muddl.riot.tft.match.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The participant's Little Legend / companion. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Companion {
    @JsonProperty("content_ID")
    private String contentId;

    @JsonProperty("skin_ID")
    private int skinId;

    private String species;
}
```

- [ ] **Step 2: Port + in-memory fake**

`application/port/MatchPort.java`:

```java
package com.muddl.riot.tft.match.application.port;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;

/** Outbound port for Riot TFT-Match-V1 data. Region-routed. */
public interface MatchPort {

    List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start);

    TftMatch getMatchById(RiotApiRegionUri region, String matchId);
}
```

`application/InMemoryMatchPort.java` (test sources):

```java
package com.muddl.riot.tft.match.application;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link MatchPort}. */
public class InMemoryMatchPort implements MatchPort {

    private final Map<String, List<String>> idsByPuuid = new HashMap<>();
    private final Map<String, TftMatch> matchById = new HashMap<>();

    public InMemoryMatchPort putIds(String puuid, List<String> ids) {
        idsByPuuid.put(puuid, ids);
        return this;
    }

    public InMemoryMatchPort putMatch(String matchId, TftMatch match) {
        matchById.put(matchId, match);
        return this;
    }

    @Override
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start) {
        return idsByPuuid.getOrDefault(puuid, List.of());
    }

    @Override
    public TftMatch getMatchById(RiotApiRegionUri region, String matchId) {
        return matchById.get(matchId);
    }
}
```

- [ ] **Step 3: Failing service test** — `application/MatchServiceTest.java`:

```java
package com.muddl.riot.tft.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    private final InMemoryMatchPort port = new InMemoryMatchPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final MatchService service = new MatchService(port, resolver);

    @Test
    void getMatchIdsByPlayer_resolvesPlayer_thenReturnsIds() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn("puuid-1");
        port.putIds("puuid-1", List.of("NA1_1", "NA1_2"));

        assertThat(service.getMatchIdsByPlayer(REGION, "Player#NA1", 20, 0))
                .containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchById_delegatesToPort() {
        TftMatch match = TftMatch.builder().build();
        port.putMatch("NA1_1", match);

        assertThat(service.getMatchById(REGION, "NA1_1")).isSameAs(match);
    }
}
```

- [ ] **Step 4: Run — expect FAIL** (`MatchService` missing). `./gradlew :tft-mcp-server:test --tests '*match*MatchServiceTest'`

- [ ] **Step 5: Service** — `application/MatchService.java`:

```java
package com.muddl.riot.tft.match.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for TFT match data. Player-keyed access resolves {@code player -> PUUID} via
 * the shared {@link PlayerIdentityResolver}; the PUUID-keyed overload remains for the analytics
 * composer, which resolves identity itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPort matchPort;
    private final PlayerIdentityResolver identityResolver;

    public List<String> getMatchIdsByPlayer(RiotApiRegionUri region, String player, Integer count, Integer start) {
        String puuid = identityResolver.resolvePuuid(player);
        return getMatchIdsByPuuid(region, puuid, count, start);
    }

    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start) {
        log.info("Fetching TFT match IDs for PUUID: {}", puuid);
        return matchPort.getMatchIdsByPuuid(region, puuid, count, start);
    }

    public TftMatch getMatchById(RiotApiRegionUri region, String matchId) {
        log.info("Fetching TFT match detail for match ID: {}", matchId);
        return matchPort.getMatchById(region, matchId);
    }
}
```

- [ ] **Step 6: Run — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*match*MatchServiceTest'`

- [ ] **Step 7: Fixtures** (verify shapes against live TFT-match-v1).

`src/test/resources/fixtures/tft-match-ids.json`:

```json
["NA1_4600000001", "NA1_4600000002"]
```

`src/test/resources/fixtures/tft-match.json`:

```json
{
  "metadata": {
    "data_version": "5",
    "match_id": "NA1_4600000001",
    "participants": ["puuid-a", "puuid-b"]
  },
  "info": {
    "game_datetime": 1699999999000,
    "game_length": 2100.5,
    "game_version": "Version 14.1",
    "queue_id": 1100,
    "tft_set_number": 10,
    "tft_game_type": "standard",
    "participants": [
      {
        "puuid": "puuid-a",
        "placement": 1,
        "level": 9,
        "gold_left": 3,
        "last_round": 35,
        "players_eliminated": 4,
        "time_eliminated": 2100.0,
        "total_damage_to_players": 140,
        "companion": { "content_ID": "abc", "skin_ID": 1, "species": "Silverwing" },
        "traits": [
          { "name": "Set10_Punk", "num_units": 4, "style": 2, "tier_current": 2, "tier_total": 3 }
        ],
        "units": [
          { "character_id": "TFT10_Jinx", "tier": 3, "rarity": 4, "itemNames": ["TFT_Item_InfinityEdge"] }
        ],
        "augments": ["TFT9_Augment_Example"]
      },
      {
        "puuid": "puuid-b",
        "placement": 8,
        "level": 7,
        "gold_left": 0,
        "last_round": 24,
        "players_eliminated": 0,
        "time_eliminated": 1400.0,
        "total_damage_to_players": 20,
        "companion": { "content_ID": "def", "skin_ID": 2, "species": "Choncc" },
        "traits": [],
        "units": []
      }
    ]
  }
}
```

- [ ] **Step 8: Failing WireMock adapter test.** Invoke `add-adapter-test`, then `adapter/out/riot/RiotTftMatchAdapterTest.java`:

```java
package com.muddl.riot.tft.match.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftMatchAdapterTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "puuid-a";
    private static final String MATCH_ID = "NA1_4600000001";

    private WireMockServer wireMock;
    private MatchPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());
        adapter = new RiotTftMatchAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getMatchIdsByPuuid_buildsPagedUrl_andParsesArray() {
        String url = "/tft/match/v1/matches/by-puuid/" + PUUID + "/ids?count=20&start=0";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-match-ids.json"))));

        List<String> ids = adapter.getMatchIdsByPuuid(REGION, PUUID, 20, 0);

        assertThat(ids).containsExactly("NA1_4600000001", "NA1_4600000002");
        verify(getRequestedFor(urlEqualTo(url)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getMatchById_parsesSnakeCaseAndNestedComp() {
        String url = "/tft/match/v1/matches/" + MATCH_ID;
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-match.json"))));

        TftMatch match = adapter.getMatchById(REGION, MATCH_ID);

        assertThat(match.getMetadata().getMatchId()).isEqualTo(MATCH_ID);
        assertThat(match.getInfo().getTftSetNumber()).isEqualTo(10);
        assertThat(match.getInfo().getParticipants()).hasSize(2);
        assertThat(match.getInfo().getParticipants().get(0).getPlacement()).isEqualTo(1);
        assertThat(match.getInfo().getParticipants().get(0).getGoldLeft()).isEqualTo(3);
        assertThat(match.getInfo().getParticipants().get(0).getUnits().get(0).getCharacterId())
                .isEqualTo("TFT10_Jinx");
    }
}
```

- [ ] **Step 9: Run — expect FAIL** (adapter missing). `./gradlew :tft-mcp-server:test --tests '*RiotTftMatchAdapterTest'`

- [ ] **Step 10: Outbound adapter** — `adapter/out/riot/RiotTftMatchAdapter.java`. Build the paged URL the same way LoL's match adapter does, minus the `queue` param:

```java
package com.muddl.riot.tft.match.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Riot TFT-Match-V1 API adapter. Match endpoints are region-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftMatchAdapter implements MatchPort {

    private final RiotApiClient riotApiClient;

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start) {
        RestClient client = riotApiClient.regional(region);

        String uri = "/tft/match/v1/matches/by-puuid/{puuid}/ids?";
        if (count != null) {
            uri += "count=" + Math.min(count, 100) + "&";
        }
        if (start != null) {
            uri += "start=" + start + "&";
        }
        if (uri.endsWith("&") || uri.endsWith("?")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        return client.get().uri(uri, puuid).retrieve().body(List.class);
    }

    @Override
    public TftMatch getMatchById(RiotApiRegionUri region, String matchId) {
        return riotApiClient
                .regional(region)
                .get()
                .uri("/tft/match/v1/matches/{matchId}", matchId)
                .retrieve()
                .body(TftMatch.class);
    }
}
```

- [ ] **Step 11: Inbound tools** — `adapter/in/mcp/MatchTool.java`:

```java
package com.muddl.riot.tft.match.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.application.MatchService;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for TFT match data (TFT-Match-V1, region-routed). {@code tft_match_ids_by_player} is
 * player-keyed; {@code tft_match_by_id} is keyed by match ID and takes no player (ADR-0014).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTool {

    private final MatchService matchService;

    @McpTool(
            name = "tft_match_ids_by_player",
            description = "Get a Teamfight Tactics player's recent match IDs, most recent first.")
    public List<String> getMatchIdsByPlayer(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "Number of match IDs to return, 1-100, defaults to 20", required = false)
                    Integer count,
            @McpToolParam(description = "Number of match IDs to skip (for paging), defaults to 0", required = false)
                    Integer start) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        int resolvedCount = count == null ? 20 : count;
        int resolvedStart = start == null ? 0 : start;
        log.info("MCP Tool - Getting TFT match IDs for a player in region: {}", region);
        return matchService.getMatchIdsByPlayer(region, player, resolvedCount, resolvedStart);
    }

    @McpTool(
            name = "tft_match_by_id",
            description = "Get the full detail of one Teamfight Tactics match by its match ID.")
    public TftMatch getMatchById(
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE, ASIA", required = true)
                    String regionStr,
            @McpToolParam(description = "The match ID, e.g. NA1_4600000001", required = true) String matchId) {
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        log.info("MCP Tool - Getting TFT match detail for match ID: {}", matchId);
        return matchService.getMatchById(region, matchId);
    }
}
```

- [ ] **Step 12: Run the context — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*match*'`

- [ ] **Step 13: Commit**

```bash
git add tft-mcp-server/src
git commit -m "feat(tft): match context (tft_match_ids_by_player, tft_match_by_id)"
```

---

### Task 5: `league` context (5 tools)

The largest context: ranked entries by player, apex leagues, paged tier entries, league-by-id, and the TFT-specific rated ladder. TFT apex paths have **no** `by-queue` suffix (unlike LoL) — e.g. `/tft/league/v1/challenger`.

**Files:**
- Create DTOs: `.../tft/league/domain/` — `LeagueEntry.java`, `LeagueList.java`, `LeagueItem.java`, `RatedLadderEntry.java`, `ApexTier.java`
- Create: `.../tft/league/application/port/LeaguePort.java`, `.../application/LeagueService.java`
- Create: `.../tft/league/adapter/out/riot/RiotTftLeagueAdapter.java`, `.../adapter/in/mcp/LeagueTool.java`
- Test: `.../tft/league/adapter/out/riot/RiotTftLeagueAdapterTest.java`
- Test: `.../tft/league/application/LeagueServiceTest.java`, `InMemoryLeaguePort.java`
- Fixtures: `tft-league-entries.json`, `tft-league-apex.json`, `tft-rated-ladder.json`

**Interfaces:**
- Consumes: `PlayerIdentityResolver.resolvePuuid`.
- Produces: `LeaguePort` with `getLeagueEntriesByPuuid`, `getApexLeague`, `getEntriesByTier`, `getLeagueById`, `getRatedLadder`; matching `LeagueService` methods; MCP tools `tft_league_entries_by_player`, `tft_league_apex_by_tier`, `tft_league_entries_by_tier`, `tft_league_by_id`, `tft_league_rated_ladder_by_queue`.

- [ ] **Step 1: Domain DTOs**

`domain/ApexTier.java` (note: TFT path segments differ from LoL — no `leagues` suffix):

```java
package com.muddl.riot.tft.league.domain;

/**
 * The three apex tiers, which have dedicated TFT-League-V1 endpoints
 * ({@code /tft/league/v1/challenger} etc.). Unlike League-V4, the TFT paths carry no
 * {@code leagues} suffix and no {@code by-queue} segment.
 */
public enum ApexTier {
    CHALLENGER,
    GRANDMASTER,
    MASTER;

    /** The Riot path segment for this tier, e.g. {@code challenger}. */
    public String leaguePath() {
        return name().toLowerCase();
    }
}
```

`domain/LeagueEntry.java`:

```java
package com.muddl.riot.tft.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's TFT ranked-league standing for one queue (Riot TFT-League-V1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueEntry {
    private String leagueId;
    private String puuid;
    private String queueType;
    private String tier;
    private String rank;
    private int leaguePoints;
    private int wins;
    private int losses;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;
}
```

`domain/LeagueItem.java`:

```java
package com.muddl.riot.tft.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One player's standing within an apex {@link LeagueList}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueItem {
    private String puuid;
    private int leaguePoints;
    private String rank;
    private int wins;
    private int losses;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;
}
```

`domain/LeagueList.java`:

```java
package com.muddl.riot.tft.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A TFT apex league (challenger/grandmaster/master) or a league fetched by id (Riot TFT-League-V1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueList {
    private String leagueId;
    private String tier;
    private String name;
    private String queue;
    private List<LeagueItem> entries;
}
```

`domain/RatedLadderEntry.java`:

```java
package com.muddl.riot.tft.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry on a TFT rated (Hyper Roll) ladder (Riot TFT-League-V1 rated-ladders). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RatedLadderEntry {
    private String puuid;
    private String ratedTier;
    private int ratedRating;
    private int wins;
    private int previousUpdateLadderPosition;
}
```

- [ ] **Step 2: Port + in-memory fake**

`application/port/LeaguePort.java`:

```java
package com.muddl.riot.tft.league.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;

/** Outbound port for Riot TFT-League-V1 ranked data. League endpoints are platform-routed. */
public interface LeaguePort {

    /** A player's ranked entries. Empty when the player is unranked. */
    List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid);

    /** The apex league (challenger/grandmaster/master). */
    LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier);

    /** One page of ranked entries for a tier + division. */
    List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page);

    /** A league by its UUID. */
    LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId);

    /** The top of a rated (Hyper Roll) ladder for a queue. */
    List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue);
}
```

`application/InMemoryLeaguePort.java` (test sources):

```java
package com.muddl.riot.tft.league.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link LeaguePort}. */
public class InMemoryLeaguePort implements LeaguePort {

    private final Map<String, List<LeagueEntry>> entriesByPuuid = new HashMap<>();
    private final Map<ApexTier, LeagueList> apexByTier = new HashMap<>();
    private final Map<String, LeagueList> leagueById = new HashMap<>();
    private final Map<String, List<RatedLadderEntry>> ladderByQueue = new HashMap<>();

    public InMemoryLeaguePort putEntries(String puuid, List<LeagueEntry> entries) {
        entriesByPuuid.put(puuid, entries);
        return this;
    }

    public InMemoryLeaguePort putApex(ApexTier tier, LeagueList list) {
        apexByTier.put(tier, list);
        return this;
    }

    public InMemoryLeaguePort putLeague(String leagueId, LeagueList list) {
        leagueById.put(leagueId, list);
        return this;
    }

    public InMemoryLeaguePort putLadder(String queue, List<RatedLadderEntry> ladder) {
        ladderByQueue.put(queue, ladder);
        return this;
    }

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        return entriesByPuuid.getOrDefault(puuid, List.of());
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier) {
        return apexByTier.get(tier);
    }

    @Override
    public List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page) {
        return List.of();
    }

    @Override
    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        return leagueById.get(leagueId);
    }

    @Override
    public List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue) {
        return ladderByQueue.getOrDefault(queue, List.of());
    }
}
```

- [ ] **Step 3: Failing service test** — `application/LeagueServiceTest.java`:

```java
package com.muddl.riot.tft.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeagueServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryLeaguePort port = new InMemoryLeaguePort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final LeagueService service = new LeagueService(port, resolver);

    @Test
    void getLeagueEntriesByPlayer_resolvesPlayer_thenReturnsEntries() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn("puuid-1");
        LeagueEntry entry = LeagueEntry.builder().queueType("RANKED_TFT").tier("DIAMOND").puuid("puuid-1").build();
        port.putEntries("puuid-1", List.of(entry));

        assertThat(service.getLeagueEntriesByPlayer(PLATFORM, "Player#NA1")).containsExactly(entry);
    }

    @Test
    void getLeagueEntriesByPlayer_returnsEmpty_whenUnranked() {
        when(resolver.resolvePuuid("raw")).thenReturn("raw");
        assertThat(service.getLeagueEntriesByPlayer(PLATFORM, "raw")).isEmpty();
    }

    @Test
    void getApexLeague_delegatesToPort() {
        LeagueList expected = LeagueList.builder().tier("CHALLENGER").build();
        port.putApex(ApexTier.CHALLENGER, expected);
        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER)).isSameAs(expected);
    }

    @Test
    void getLeagueById_delegatesToPort() {
        LeagueList expected = LeagueList.builder().leagueId("league-uuid").build();
        port.putLeague("league-uuid", expected);
        assertThat(service.getLeagueById(PLATFORM, "league-uuid")).isSameAs(expected);
    }

    @Test
    void getRatedLadder_delegatesToPort() {
        RatedLadderEntry e = RatedLadderEntry.builder().puuid("p").ratedTier("BLUE").ratedRating(1500).build();
        port.putLadder("RANKED_TFT_TURBO", List.of(e));
        assertThat(service.getRatedLadder(PLATFORM, "RANKED_TFT_TURBO")).containsExactly(e);
    }
}
```

- [ ] **Step 4: Run — expect FAIL** (`LeagueService` missing). `./gradlew :tft-mcp-server:test --tests '*league*LeagueServiceTest'`

- [ ] **Step 5: Service** — `application/LeagueService.java`:

```java
package com.muddl.riot.tft.league.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Application service for Riot TFT-League-V1 ranked data. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeaguePort leaguePort;
    private final PlayerIdentityResolver identityResolver;

    public List<LeagueEntry> getLeagueEntriesByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching TFT league entries on platform: {}", platform);
        return leaguePort.getLeagueEntriesByPuuid(platform, puuid);
    }

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier) {
        log.info("Fetching TFT {} apex league on platform: {}", tier, platform);
        return leaguePort.getApexLeague(platform, tier);
    }

    public List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page) {
        log.info("Fetching TFT entries for {} {} page {} on platform: {}", tier, division, page, platform);
        return leaguePort.getEntriesByTier(platform, tier, division, page);
    }

    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        log.info("Fetching TFT league by id on platform: {}", platform);
        return leaguePort.getLeagueById(platform, leagueId);
    }

    public List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue) {
        log.info("Fetching TFT rated ladder for queue {} on platform: {}", queue, platform);
        return leaguePort.getRatedLadder(platform, queue);
    }
}
```

- [ ] **Step 6: Run — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*league*LeagueServiceTest'`

- [ ] **Step 7: Fixtures** (verify against live TFT-league-v1).

`tft-league-entries.json`:

```json
[
  {
    "leagueId": "league-uuid-1",
    "puuid": "puuid-1",
    "queueType": "RANKED_TFT",
    "tier": "DIAMOND",
    "rank": "II",
    "leaguePoints": 42,
    "wins": 120,
    "losses": 110,
    "veteran": false,
    "inactive": false,
    "freshBlood": true,
    "hotStreak": false
  }
]
```

`tft-league-apex.json`:

```json
{
  "leagueId": "league-uuid-chal",
  "tier": "CHALLENGER",
  "name": "Zed's Assassins",
  "queue": "RANKED_TFT",
  "entries": [
    { "puuid": "puuid-top", "leaguePoints": 1400, "rank": "I", "wins": 200, "losses": 150,
      "veteran": true, "inactive": false, "freshBlood": false, "hotStreak": true }
  ]
}
```

`tft-rated-ladder.json`:

```json
[
  { "puuid": "puuid-a", "ratedTier": "BLUE", "ratedRating": 1800, "wins": 90, "previousUpdateLadderPosition": 1 }
]
```

- [ ] **Step 8: Failing WireMock adapter test.** Invoke `add-adapter-test`, then `adapter/out/riot/RiotTftLeagueAdapterTest.java`. Five endpoints; the setUp/teardown mirror Task 2. Exact URLs and assertions:

```java
package com.muddl.riot.tft.league.adapter.out.riot;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.core.testsupport.Fixtures;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotTftLeagueAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private WireMockServer wireMock;
    private LeaguePort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());
        adapter = new RiotTftLeagueAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getLeagueEntriesByPuuid_parsesArray_andSendsToken() {
        String url = "/tft/league/v1/by-puuid/puuid-1";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-entries.json"))));

        List<LeagueEntry> entries = adapter.getLeagueEntriesByPuuid(PLATFORM, "puuid-1");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getQueueType()).isEqualTo("RANKED_TFT");
        assertThat(entries.get(0).getTier()).isEqualTo("DIAMOND");
        verify(getRequestedFor(urlEqualTo(url)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getLeagueEntriesByPuuid_returnsEmpty_onEmptyArray() {
        String url = "/tft/league/v1/by-puuid/unranked";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")));

        assertThat(adapter.getLeagueEntriesByPuuid(PLATFORM, "unranked")).isEmpty();
    }

    @Test
    void getApexLeague_usesTierPath_withNoQueueSuffix() {
        String url = "/tft/league/v1/challenger";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-apex.json"))));

        LeagueList list = adapter.getApexLeague(PLATFORM, ApexTier.CHALLENGER);

        assertThat(list.getTier()).isEqualTo("CHALLENGER");
        assertThat(list.getEntries()).hasSize(1);
        assertThat(list.getEntries().get(0).getLeaguePoints()).isEqualTo(1400);
    }

    @Test
    void getEntriesByTier_buildsPagedPath() {
        String url = "/tft/league/v1/entries/DIAMOND/II?page=1";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-entries.json"))));

        List<LeagueEntry> entries = adapter.getEntriesByTier(PLATFORM, "DIAMOND", "II", 1);

        assertThat(entries).hasSize(1);
        verify(getRequestedFor(urlEqualTo(url)));
    }

    @Test
    void getLeagueById_usesLeaguePath() {
        String url = "/tft/league/v1/leagues/league-uuid-chal";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-league-apex.json"))));

        assertThat(adapter.getLeagueById(PLATFORM, "league-uuid-chal").getName()).isEqualTo("Zed's Assassins");
    }

    @Test
    void getRatedLadder_usesQueuePath() {
        String url = "/tft/league/v1/rated-ladders/RANKED_TFT_TURBO/top";
        stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("tft-rated-ladder.json"))));

        List<RatedLadderEntry> ladder = adapter.getRatedLadder(PLATFORM, "RANKED_TFT_TURBO");

        assertThat(ladder).hasSize(1);
        assertThat(ladder.get(0).getRatedTier()).isEqualTo("BLUE");
        assertThat(ladder.get(0).getRatedRating()).isEqualTo(1800);
    }
}
```

- [ ] **Step 9: Run — expect FAIL** (adapter missing). `./gradlew :tft-mcp-server:test --tests '*RiotTftLeagueAdapterTest'`

- [ ] **Step 10: Outbound adapter** — `adapter/out/riot/RiotTftLeagueAdapter.java`:

```java
package com.muddl.riot.tft.league.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.league.application.port.LeaguePort;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot TFT-League-V1 API adapter. League endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftLeagueAdapter implements LeaguePort {

    private final RiotApiClient riotApiClient;

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        LeagueEntry[] entries = riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(LeagueEntry[].class);
        return entries == null ? List.of() : List.of(entries);
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/{tier}", tier.leaguePath())
                .retrieve()
                .body(LeagueList.class);
    }

    @Override
    public List<LeagueEntry> getEntriesByTier(RiotApiPlatformUri platform, String tier, String division, int page) {
        LeagueEntry[] entries = riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/entries/{tier}/{division}?page={page}", tier, division, page)
                .retrieve()
                .body(LeagueEntry[].class);
        return entries == null ? List.of() : List.of(entries);
    }

    @Override
    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/leagues/{leagueId}", leagueId)
                .retrieve()
                .body(LeagueList.class);
    }

    @Override
    public List<RatedLadderEntry> getRatedLadder(RiotApiPlatformUri platform, String queue) {
        RatedLadderEntry[] ladder = riotApiClient
                .platform(platform)
                .get()
                .uri("/tft/league/v1/rated-ladders/{queue}/top", queue)
                .retrieve()
                .body(RatedLadderEntry[].class);
        return ladder == null ? List.of() : List.of(ladder);
    }
}
```

- [ ] **Step 11: Inbound tools** — `adapter/in/mcp/LeagueTool.java`:

```java
package com.muddl.riot.tft.league.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.tft.league.application.LeagueService;
import com.muddl.riot.tft.league.domain.ApexTier;
import com.muddl.riot.tft.league.domain.LeagueEntry;
import com.muddl.riot.tft.league.domain.LeagueList;
import com.muddl.riot.tft.league.domain.RatedLadderEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tools for TFT ranked-league data (TFT-League-V1, platform-routed). */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueTool {

    private static final String DEFAULT_RATED_QUEUE = "RANKED_TFT_TURBO";

    private final LeagueService leagueService;

    @McpTool(
            name = "tft_league_entries_by_player",
            description = "Get a Teamfight Tactics player's ranked-league entries (one per ranked queue).")
    public List<LeagueEntry> getLeagueEntriesByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT league entries for a player on platform: {}", platform);
        return leagueService.getLeagueEntriesByPlayer(platform, player);
    }

    @McpTool(
            name = "tft_league_apex_by_tier",
            description = "Get a Teamfight Tactics apex league: CHALLENGER, GRANDMASTER, or MASTER.")
    public LeagueList getApexLeague(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The apex tier: CHALLENGER, GRANDMASTER, or MASTER", required = true)
                    String tierStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        ApexTier tier = ApexTier.valueOf(tierStr.toUpperCase());
        log.info("MCP Tool - Getting TFT {} apex league on platform: {}", tier, platform);
        return leagueService.getApexLeague(platform, tier);
    }

    @McpTool(
            name = "tft_league_entries_by_tier",
            description =
                    "Get one page of Teamfight Tactics ranked entries for a tier and division (e.g. DIAMOND II).")
    public List<LeagueEntry> getEntriesByTier(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The tier, e.g. DIAMOND, PLATINUM, GOLD", required = true) String tier,
            @McpToolParam(description = "The division: I, II, III, or IV", required = true) String division,
            @McpToolParam(description = "The page of results, 1-based; defaults to 1", required = false) Integer page) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        int resolvedPage = page == null ? 1 : page;
        log.info("MCP Tool - Getting TFT entries for {} {} page {} on platform: {}",
                tier, division, resolvedPage, platform);
        return leagueService.getEntriesByTier(platform, tier.toUpperCase(), division.toUpperCase(), resolvedPage);
    }

    @McpTool(
            name = "tft_league_by_id",
            description = "Get a Teamfight Tactics league by its league UUID.")
    public LeagueList getLeagueById(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The league UUID (from an apex-league response)", required = true)
                    String leagueId) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting TFT league by id on platform: {}", platform);
        return leagueService.getLeagueById(platform, leagueId);
    }

    @McpTool(
            name = "tft_league_rated_ladder_by_queue",
            description =
                    "Get the top of a Teamfight Tactics rated (Hyper Roll) ladder for a queue, defaults to RANKED_TFT_TURBO.")
    public List<RatedLadderEntry> getRatedLadder(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The rated queue, defaults to RANKED_TFT_TURBO", required = false)
                    String queueStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        String queue = (queueStr == null || queueStr.isBlank()) ? DEFAULT_RATED_QUEUE : queueStr;
        log.info("MCP Tool - Getting TFT rated ladder for queue {} on platform: {}", queue, platform);
        return leagueService.getRatedLadder(platform, queue);
    }
}
```

- [ ] **Step 12: Run the context — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*league*'`

- [ ] **Step 13: Commit**

```bash
git add tft-mcp-server/src
git commit -m "feat(tft): league context (5 tools incl. rated ladder)"
```

---

### Task 6: `account` tool

Tool-only context that delegates to `riot-account-core`'s `RiotAccountService`. On the account-domain allowlist, so it may reference the account domain directly (like LoL's `RiotAccountTool`).

**Files:**
- Create: `.../tft/account/adapter/in/mcp/RiotAccountTool.java`
- Test: `.../tft/account/adapter/in/mcp/RiotAccountToolTest.java`

**Interfaces:**
- Consumes: `RiotAccountService.getAccountByPuuid(String)` and `getAccountByRiotId(String gameName, String tagLine)`, returning `com.muddl.riot.account.domain.RiotAccount` (from `riot-account-core`).
- Produces: MCP tool `tft_account_by_player`.

- [ ] **Step 1: Failing tool test** — `adapter/in/mcp/RiotAccountToolTest.java`:

```java
package com.muddl.riot.tft.account.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;

class RiotAccountToolTest {

    private final RiotAccountService service = mock(RiotAccountService.class);
    private final RiotAccountTool tool = new RiotAccountTool(service);

    @Test
    void riotId_isSplitOnHash_andRoutedToByRiotId() {
        RiotAccount account = RiotAccount.builder().puuid("puuid-1").gameName("Player").tagLine("NA1").build();
        when(service.getAccountByRiotId("Player", "NA1")).thenReturn(account);

        assertThat(tool.getAccountByPlayer("Player#NA1")).isSameAs(account);
    }

    @Test
    void rawPuuid_isRoutedToByPuuid() {
        RiotAccount account = RiotAccount.builder().puuid("puuid-raw").build();
        when(service.getAccountByPuuid("puuid-raw")).thenReturn(account);

        assertThat(tool.getAccountByPlayer("puuid-raw")).isSameAs(account);
    }

    @Test
    void blankOrMalformed_throwsIllegalArgument() {
        assertThatThrownBy(() -> tool.getAccountByPlayer("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.getAccountByPlayer("no-hash-and-not-blank#"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

Note: confirm `RiotAccount`'s builder field names (`puuid`, `gameName`, `tagLine`) by reading `riot-account-core`'s `RiotAccount` before running; adjust the test builder if they differ.

- [ ] **Step 2: Run — expect FAIL** (`RiotAccountTool` missing). `./gradlew :tft-mcp-server:test --tests '*account*RiotAccountToolTest'`

- [ ] **Step 3: The tool** — `adapter/in/mcp/RiotAccountTool.java` (TFT rename of LoL's, identical disambiguation logic):

```java
package com.muddl.riot.tft.account.adapter.in.mcp;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool for Riot account lookups from the TFT server. Takes a single {@code player} — a
 * {@code GameName#TAG} Riot ID or a raw PUUID — and returns the account. Disambiguates on {@code #}
 * and calls the account service directly; this tool is on the account-domain allow-list.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotAccountTool {

    private final RiotAccountService accountService;

    @McpTool(
            name = "tft_account_by_player",
            description = "Get Riot account information by player (a Riot ID as GameName#TAG, or a raw PUUID).")
    public RiotAccount getAccountByPlayer(
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        String trimmed = player.trim();
        if (trimmed.indexOf('#') < 0) {
            log.info("MCP Tool - Getting account by PUUID");
            return accountService.getAccountByPuuid(trimmed);
        }
        String[] parts = trimmed.split("#", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        log.info("MCP Tool - Getting account by Riot ID: {}#{}", parts[0].trim(), parts[1].trim());
        return accountService.getAccountByRiotId(parts[0].trim(), parts[1].trim());
    }

    private static String unparseableMessage(String player) {
        return "Cannot parse player '" + player + "'. Provide a Riot ID as GameName#TAG "
                + "(for example Faker#KR1) or a raw PUUID.";
    }
}
```

- [ ] **Step 4: Run — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*account*RiotAccountToolTest'`

- [ ] **Step 5: Commit**

```bash
git add tft-mcp-server/src
git commit -m "feat(tft): account tool (tft_account_by_player)"
```

---

### Task 7: `analytics` context

The value-add tool: composes `SummonerService` + `MatchService`, resolves the PUUID once, and aggregates TFT-native stats over recent matches. Depends on Tasks 3 and 4.

**Files:**
- Create: `.../tft/analytics/domain/PlayerMatchAnalytics.java`
- Create: `.../tft/analytics/application/AnalyticsService.java`
- Create: `.../tft/analytics/adapter/in/mcp/AnalyticsTool.java`
- Test: `.../tft/analytics/application/AnalyticsServiceTest.java`

**Interfaces:**
- Consumes: `PlayerIdentityResolver.resolvePuuid`; `SummonerService.getSummonerByPuuid`; `MatchService.getMatchIdsByPuuid`, `getMatchById`; `Participant` getters (`getPuuid`, `getPlacement`, `getLevel`, `getGoldLeft`, `getTraits`, `getUnits`).
- Produces: `AnalyticsService.getPlayerMatchAnalytics(String player, RiotApiPlatformUri, RiotApiRegionUri, int matchCount) -> PlayerMatchAnalytics`; MCP tool `tft_analytics_player_matches`.

- [ ] **Step 1: Domain DTO** — `domain/PlayerMatchAnalytics.java`:

```java
package com.muddl.riot.tft.analytics.domain;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Aggregated analytics over a TFT player's recent matches. TFT-native stats (placement, top-4). */
@Data
@Builder
public class PlayerMatchAnalytics {
    private String riotId;
    private long summonerLevel;
    private int matchCount;
    private String avgPlacement;
    private String top4Rate;
    private String firstPlaceRate;
    private String avgLevel;
    private String avgGoldLeft;
    private List<String> mostPlayedTraits;
    private List<String> mostPlayedUnits;
}
```

- [ ] **Step 2: Failing service test** — `application/AnalyticsServiceTest.java`. Covers the happy path, the **zero-games** guard, and a **single-game** boundary:

```java
package com.muddl.riot.tft.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.analytics.domain.PlayerMatchAnalytics;
import com.muddl.riot.tft.match.application.MatchService;
import com.muddl.riot.tft.match.domain.MatchInfo;
import com.muddl.riot.tft.match.domain.Participant;
import com.muddl.riot.tft.match.domain.TftMatch;
import com.muddl.riot.tft.match.domain.Trait;
import com.muddl.riot.tft.match.domain.Unit;
import com.muddl.riot.tft.summoner.application.SummonerService;
import com.muddl.riot.tft.summoner.domain.Summoner;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "puuid-1";

    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SummonerService summonerService = mock(SummonerService.class);
    private final MatchService matchService = mock(MatchService.class);
    private final AnalyticsService service = new AnalyticsService(resolver, summonerService, matchService);

    private TftMatch matchWith(int placement, int level, int goldLeft, String traitName, String unitId) {
        Participant p = Participant.builder()
                .puuid(PUUID)
                .placement(placement)
                .level(level)
                .goldLeft(goldLeft)
                .traits(List.of(Trait.builder().name(traitName).tierCurrent(2).build()))
                .units(List.of(Unit.builder().characterId(unitId).build()))
                .build();
        return TftMatch.builder()
                .info(MatchInfo.builder().participants(List.of(p)).build())
                .build();
    }

    @Test
    void aggregatesPlacementTop4AndComps_overTwoMatches() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().summonerLevel(300).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any()))
                .thenReturn(List.of("NA1_1", "NA1_2"));
        when(matchService.getMatchById(REGION, "NA1_1")).thenReturn(matchWith(1, 9, 2, "Set10_Punk", "TFT10_Jinx"));
        when(matchService.getMatchById(REGION, "NA1_2")).thenReturn(matchWith(5, 8, 0, "Set10_Punk", "TFT10_Sona"));

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getMatchCount()).isEqualTo(2);
        assertThat(a.getAvgPlacement()).isEqualTo("3.00");
        assertThat(a.getTop4Rate()).isEqualTo("50.00%");
        assertThat(a.getFirstPlaceRate()).isEqualTo("50.00%");
        assertThat(a.getSummonerLevel()).isEqualTo(300);
        assertThat(a.getMostPlayedTraits().get(0)).contains("Set10_Punk");
    }

    @Test
    void zeroGames_returnsEmptySummary_withoutDivideByZero() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().summonerLevel(10).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any())).thenReturn(List.of());

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getMatchCount()).isEqualTo(0);
        assertThat(a.getRiotId()).isEqualTo("Player#NA1");
        assertThat(a.getSummonerLevel()).isEqualTo(10);
    }

    @Test
    void singleGame_firstPlace_isAllTop4() {
        when(resolver.resolvePuuid("Player#NA1")).thenReturn(PUUID);
        when(summonerService.getSummonerByPuuid(PLATFORM, PUUID))
                .thenReturn(Summoner.builder().summonerLevel(1).build());
        when(matchService.getMatchIdsByPuuid(eq(REGION), eq(PUUID), anyInt(), any())).thenReturn(List.of("NA1_1"));
        when(matchService.getMatchById(REGION, "NA1_1")).thenReturn(matchWith(1, 9, 4, "Set10_Punk", "TFT10_Jinx"));

        PlayerMatchAnalytics a = service.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 10);

        assertThat(a.getAvgPlacement()).isEqualTo("1.00");
        assertThat(a.getTop4Rate()).isEqualTo("100.00%");
        assertThat(a.getFirstPlaceRate()).isEqualTo("100.00%");
    }
}
```

- [ ] **Step 3: Run — expect FAIL** (`AnalyticsService` missing). `./gradlew :tft-mcp-server:test --tests '*analytics*AnalyticsServiceTest'`

- [ ] **Step 4: The service** — `application/AnalyticsService.java`:

```java
package com.muddl.riot.tft.analytics.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.analytics.domain.PlayerMatchAnalytics;
import com.muddl.riot.tft.match.application.MatchService;
import com.muddl.riot.tft.match.domain.Participant;
import com.muddl.riot.tft.match.domain.TftMatch;
import com.muddl.riot.tft.match.domain.Trait;
import com.muddl.riot.tft.match.domain.Unit;
import com.muddl.riot.tft.summoner.application.SummonerService;
import com.muddl.riot.tft.summoner.domain.Summoner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Composes summoner + match data into TFT-native analytics: average placement, top-4 rate, and the
 * player's most-played traits and units over recent games.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PlayerIdentityResolver identityResolver;
    private final SummonerService summonerService;
    private final MatchService matchService;

    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            String player, RiotApiPlatformUri platform, RiotApiRegionUri region, int matchCount) {
        log.info("Generating TFT match analytics for player on platform: {}", platform);

        String puuid = identityResolver.resolvePuuid(player);
        Summoner summoner = summonerService.getSummonerByPuuid(platform, puuid);

        List<String> matchIds = matchService.getMatchIdsByPuuid(region, puuid, matchCount, 0);

        List<Participant> parts = new ArrayList<>();
        for (String matchId : matchIds) {
            TftMatch match = matchService.getMatchById(region, matchId);
            if (match == null || match.getInfo() == null || match.getInfo().getParticipants() == null) {
                continue;
            }
            for (Participant p : match.getInfo().getParticipants()) {
                if (puuid.equals(p.getPuuid())) {
                    parts.add(p);
                    break;
                }
            }
        }

        int total = parts.size();
        if (total == 0) {
            return PlayerMatchAnalytics.builder()
                    .riotId(player)
                    .summonerLevel(summoner == null ? 0 : summoner.getSummonerLevel())
                    .matchCount(0)
                    .build();
        }

        double avgPlacement =
                parts.stream().mapToInt(Participant::getPlacement).average().orElse(0);
        long top4 = parts.stream().filter(p -> p.getPlacement() <= 4).count();
        long firsts = parts.stream().filter(p -> p.getPlacement() == 1).count();
        double avgLevel = parts.stream().mapToInt(Participant::getLevel).average().orElse(0);
        double avgGoldLeft =
                parts.stream().mapToInt(Participant::getGoldLeft).average().orElse(0);

        return PlayerMatchAnalytics.builder()
                .riotId(player)
                .summonerLevel(summoner == null ? 0 : summoner.getSummonerLevel())
                .matchCount(total)
                .avgPlacement(String.format("%.2f", avgPlacement))
                .top4Rate(String.format("%.2f%%", (double) top4 / total * 100))
                .firstPlaceRate(String.format("%.2f%%", (double) firsts / total * 100))
                .avgLevel(String.format("%.2f", avgLevel))
                .avgGoldLeft(String.format("%.2f", avgGoldLeft))
                .mostPlayedTraits(topThreeTraits(parts))
                .mostPlayedUnits(topThreeUnits(parts))
                .build();
    }

    /** Top-3 active traits (tier_current > 0) by frequency across the analysed games. */
    private List<String> topThreeTraits(List<Participant> parts) {
        Map<String, Long> counts = parts.stream()
                .flatMap(p -> p.getTraits() == null ? java.util.stream.Stream.<Trait>empty() : p.getTraits().stream())
                .filter(t -> t.getTierCurrent() > 0)
                .collect(Collectors.groupingBy(Trait::getName, Collectors.counting()));
        return topThree(counts);
    }

    /** Top-3 fielded units by frequency across the analysed games. */
    private List<String> topThreeUnits(List<Participant> parts) {
        Map<String, Long> counts = parts.stream()
                .flatMap(p -> p.getUnits() == null ? java.util.stream.Stream.<Unit>empty() : p.getUnits().stream())
                .collect(Collectors.groupingBy(Unit::getCharacterId, Collectors.counting()));
        return topThree(counts);
    }

    private List<String> topThree(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(e -> e.getKey() + " (" + e.getValue() + " games)")
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 5: Run — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*analytics*AnalyticsServiceTest'`

- [ ] **Step 6: The tool** — `adapter/in/mcp/AnalyticsTool.java`:

```java
package com.muddl.riot.tft.analytics.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.analytics.application.AnalyticsService;
import com.muddl.riot.tft.analytics.domain.PlayerMatchAnalytics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tool for aggregated TFT recent-match analytics (composes summoner + match). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsTool {

    private final AnalyticsService analyticsService;

    @McpTool(
            name = "tft_analytics_player_matches",
            description = "Get aggregated analytics of a Teamfight Tactics player's recent matches "
                    + "(average placement, top-4 rate, most-played traits and units).")
    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE", required = true) String regionStr,
            @McpToolParam(description = "Number of recent matches to analyze, 1-100, defaults to 10", required = false)
                    Integer matchCount) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr.toUpperCase());
        int count = matchCount == null ? 10 : Math.min(100, Math.max(1, matchCount));
        log.info("MCP Tool - Generating TFT match analytics for a player on platform: {}", platform);
        return analyticsService.getPlayerMatchAnalytics(player, platform, region, count);
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add tft-mcp-server/src
git commit -m "feat(tft): analytics context (tft_analytics_player_matches)"
```

---

### Task 8: Architecture tests (ArchUnit + negative control)

Enforce the hexagon and the account-domain confinement for the TFT server. Composition edges: `analytics → summoner`, `analytics → match`.

**Files:**
- Test: `.../tft/architecture/HexagonalArchitectureTest.java`
- Test: `.../tft/architecture/HexagonalArchitectureNegativeControlTest.java`
- Test: `.../tft/architecture/ArchFixtureIllegalAccountUser.java`, `ArchFixtureLegalResolverUser.java`

**Interfaces:**
- Consumes: `com.muddl.riot.core.testsupport.HexagonRules` (shared rules from `riot-api-core` test fixtures).

- [ ] **Step 1: Write the architecture test** — `architecture/HexagonalArchitectureTest.java` (mirrors LoL's, retargeted to `com.muddl.riot.tft`):

```java
package com.muddl.riot.tft.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.muddl.riot.core.testsupport.HexagonRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the bounded-context hexagon for the TFT server. Layering/placement/naming rules come from
 * {@link HexagonRules} (shared via riot-api-core test fixtures). Only the cross-context rule and the
 * account-domain confinement are local, because a server's context graph is its own business.
 */
@AnalyzeClasses(
        packages = "com.muddl.riot.tft",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeGradleTestFixtures.class})
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule layers_respect_inward_dependency_rule = HexagonRules.LAYERS_RESPECT_INWARD_DEPENDENCY_RULE;

    @ArchTest
    static final ArchRule restclient_confined_to_outbound_adapters =
            HexagonRules.RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule mcp_tools_only_in_inbound_adapters = HexagonRules.MCP_TOOLS_ONLY_IN_INBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule ports_are_interfaces = HexagonRules.PORTS_ARE_INTERFACES;

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces =
            HexagonRules.PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES;

    @ArchTest
    static final ArchRule services_live_in_application = HexagonRules.SERVICES_LIVE_IN_APPLICATION;

    @ArchTest
    static final ArchRule tools_live_in_inbound_adapters = HexagonRules.TOOLS_LIVE_IN_INBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot = HexagonRules.ADAPTERS_LIVE_IN_OUTBOUND_RIOT;

    /**
     * Contexts are independent except for the two deliberate composition edges analytics -> summoner
     * and analytics -> match: analytics composes those two contexts' application services.
     */
    @ArchTest
    static final ArchRule contexts_do_not_depend_on_each_other = slices().matching("..riot.tft.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(resideInAPackage("..tft.analytics.."), resideInAPackage("..tft.summoner.."))
            .ignoreDependency(resideInAPackage("..tft.analytics.."), resideInAPackage("..tft.match.."));

    /**
     * Only analytics (which composes it) and this server's thin account tool may reach into the
     * shared account <em>domain</em>. Identity resolution ({@code ..riot.account.identity..}) is
     * excluded from the confinement — every player-keyed context is supposed to depend on it
     * (ADR-0008); it returns a plain PUUID string, not a {@code RiotAccount}.
     */
    @ArchTest
    static final ArchRule only_analytics_and_the_account_tool_use_the_account_domain = noClasses()
            .that()
            .resideOutsideOfPackages("..tft.analytics..", "..tft.account..")
            .should()
            .dependOnClassesThat(
                    resideInAPackage("..riot.account..").and(resideOutsideOfPackages("..riot.account.identity..")))
            .as("only analytics and the account tool use the account domain "
                    + "(identity resolution is open to every context)");
}
```

- [ ] **Step 2: Write the negative-control fixtures**

`architecture/ArchFixtureIllegalAccountUser.java`:

```java
package com.muddl.riot.tft.architecture;

import com.muddl.riot.account.domain.RiotAccount;

/**
 * A deliberate architecture violation, used only as a negative control by {@link
 * HexagonalArchitectureNegativeControlTest}: a non-allowlisted context referencing the account
 * domain. Do not "fix" this class — its violation is the point.
 */
@SuppressWarnings("unused")
class ArchFixtureIllegalAccountUser {
    private RiotAccount account;
}
```

`architecture/ArchFixtureLegalResolverUser.java`:

```java
package com.muddl.riot.tft.architecture;

import com.muddl.riot.account.identity.PlayerIdentityResolver;

/**
 * A deliberately non-allowlisted context depending on {@link PlayerIdentityResolver}. This is
 * <em>legal</em>: identity resolution is the open surface of the account library. Do not "fix" this
 * class — its legality is the point.
 */
@SuppressWarnings("unused")
class ArchFixtureLegalResolverUser {
    private PlayerIdentityResolver resolver;
}
```

- [ ] **Step 3: Write the negative-control test** — `architecture/HexagonalArchitectureNegativeControlTest.java`:

```java
package com.muddl.riot.tft.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Proves the split account rule in {@link HexagonalArchitectureTest} bites on both sides. */
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

- [ ] **Step 4: Run — expect PASS.**

Run: `./gradlew :tft-mcp-server:test --tests '*architecture*'`
Expected: PASS. If `contexts_do_not_depend_on_each_other` fails, a context is importing another context outside the two allowed edges — fix the offending import, do not add an ignore.

- [ ] **Step 5: Commit**

```bash
git add tft-mcp-server/src
git commit -m "test(tft): ArchUnit hexagon rules + account-domain negative control"
```

---

### Task 9: Tool-inventory guard

Lock the public MCP contract to exactly the 11 TFT tools.

**Files:**
- Test: `.../tft/McpToolInventoryTest.java`

- [ ] **Step 1: Write the inventory test** — `McpToolInventoryTest.java`:

```java
package com.muddl.riot.tft;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.tft.account.adapter.in.mcp.RiotAccountTool;
import com.muddl.riot.tft.analytics.adapter.in.mcp.AnalyticsTool;
import com.muddl.riot.tft.league.adapter.in.mcp.LeagueTool;
import com.muddl.riot.tft.match.adapter.in.mcp.MatchTool;
import com.muddl.riot.tft.status.adapter.in.mcp.StatusTool;
import com.muddl.riot.tft.summoner.adapter.in.mcp.SummonerTool;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/**
 * Guards the public MCP contract: exactly the eleven TFT tools, each named
 * {@code tft_<context>_<action>}. If this fails, a tool name changed without this list being updated.
 */
class McpToolInventoryTest {

    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "tft_account_by_player",
            "tft_summoner_by_player",
            "tft_league_entries_by_player",
            "tft_league_apex_by_tier",
            "tft_league_entries_by_tier",
            "tft_league_by_id",
            "tft_league_rated_ladder_by_queue",
            "tft_match_ids_by_player",
            "tft_match_by_id",
            "tft_status_platform",
            "tft_analytics_player_matches");

    @Test
    void tool_inventory_is_unchanged() {
        Set<String> actual = Stream.of(
                        RiotAccountTool.class,
                        SummonerTool.class,
                        LeagueTool.class,
                        MatchTool.class,
                        StatusTool.class,
                        AnalyticsTool.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
    }
}
```

- [ ] **Step 2: Run — expect PASS.** `./gradlew :tft-mcp-server:test --tests '*McpToolInventoryTest'`

- [ ] **Step 3: Run the full module gate — expect PASS.**

Run: `./gradlew :tft-mcp-server:build`
Expected: `BUILD SUCCESSFUL` — compiles, all unit + ArchUnit tests, JaCoCo coverage, and Spotless all green. If Spotless fails, run `./gradlew spotlessApply` and re-commit.

- [ ] **Step 4: Commit**

```bash
git add tft-mcp-server/src
git commit -m "test(tft): tool-inventory guard (11 tools)"
```

---

### Task 10: Live eval harness — generalize to multi-server + TFT evals

Generalize `eval/` from single-server (LoL-only) to N servers, then add TFT scenarios. These live evals need real keys and never run in the offline gate.

**Files:**
- Modify: `eval/mcpeval.stdio.yaml`, `eval/mcpeval.sse.yaml`
- Modify: `eval/README.md`
- Modify: `.github/workflows/live-eval.yml`
- Create: `eval/tests/test_tft_summoner.py`, `test_tft_league.py`, `test_tft_match.py`, `test_tft_status.py`, `test_tft_analytics.py`, `test_tft_account.py`

**Interfaces:**
- Consumes env vars: `TFT_MCP_JAR` (stdio), `TFT_MCP_SSE_URL` (sse), plus the existing `LOL_MCP_JAR` / `LOL_MCP_SSE_URL`.

- [ ] **Step 1: Add a `tft_server` to `eval/mcpeval.stdio.yaml`**

Under `mcp.servers`, add alongside `lol_server`:

```yaml
    tft_server:
      transport: stdio
      command: java
      args: ["-jar", "${TFT_MCP_JAR}", "--spring.profiles.active=stdio"]
      env:
        RIOT_API_KEY: "${RIOT_API_KEY}"
```

Then add a TFT tester agent to `agents.definitions` (mirror `riot_tester`, but `server_names: ["tft_server"]` and a TFT-flavored instruction):

```yaml
    - name: tft_tester
      instruction: |
        You are a precise QA agent for a Teamfight Tactics MCP server.
        Use the provided tools to answer. Prefer NA1 as the platform and
        AMERICAS as the region unless told otherwise. If a tool call returns
        an error, do NOT retry it — report the failure plainly and stop,
        rather than inventing data or calling it repeatedly.
      server_names: ["tft_server"]
      max_iterations: 4
```

- [ ] **Step 2: Mirror the same two additions in `eval/mcpeval.sse.yaml`**, but the TFT server block uses the SSE transport:

```yaml
    tft_server:
      transport: sse
      url: "${TFT_MCP_SSE_URL}"
```

(Match the exact `transport`/`url` key shape already used by `lol_server` in that file.)

- [ ] **Step 3: Add the TFT status eval** — `eval/tests/test_tft_status.py` (non-player-keyed, simplest):

```python
"""TFT platform status live eval."""

from mcp_eval import task, Expect


@task("TFT platform status returns a usable status for NA1")
async def test_tft_status_platform(agent, session):
    response = await agent.generate_str(
        "Get the Teamfight Tactics platform status for NA1. "
        "Report whether there are any active incidents or maintenances."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_status_platform"),
        name="tft_status_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports the TFT platform status for NA1 — either that "
                "there are active incidents/maintenances (with some detail) or that "
                "the platform is operational. It must not surface a raw error or stack trace."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_status_usable",
    )
```

- [ ] **Step 4: Add the TFT league + summoner + match + analytics + account evals.** Each follows the discovery-seed pattern from `add-live-eval` (seed a subject from a keyless tool, then chain). Create the five files with these tasks; assert `Expect.tools.was_called(<tool>)` plus an invariant rubric (never an exact live value):

`eval/tests/test_tft_league.py` — task: "Get the CHALLENGER TFT apex league on NA1, report that it lists ranked players with league points." Assert `tft_league_apex_by_tier` called; rubric: a non-empty apex ladder with LP values, no error.

`eval/tests/test_tft_summoner.py` — task: "Get the CHALLENGER TFT apex league on NA1, pick any one player, look up that player's TFT summoner on NA1, report the summoner level." Assert `tft_summoner_by_player` called; rubric: a positive summoner level, no error.

`eval/tests/test_tft_match.py` — task: "From the CHALLENGER TFT apex league on NA1, pick a player, get their recent TFT match IDs in AMERICAS, then fetch one match and report that player's placement (1–8)." Assert `tft_match_ids_by_player` and `tft_match_by_id` called; rubric: a placement between 1 and 8, no error.

`eval/tests/test_tft_analytics.py` — task: "Analyze a CHALLENGER TFT player's recent matches on NA1 / AMERICAS and report their average placement and top-4 rate." Assert `tft_analytics_player_matches` called; rubric: an average placement between 1 and 8 and a top-4 rate as a percentage; the zero-games case reads cleanly, not as an error.

`eval/tests/test_tft_account.py` — task: "Look up the Riot account for a well-known Riot ID and report the PUUID." Assert `tft_account_by_player` called; rubric: a PUUID is reported OR a clean not-found; no fabricated data.

- [ ] **Step 5: Update `eval/README.md`** — document `TFT_MCP_JAR` / `TFT_MCP_SSE_URL`, add a `./gradlew :tft-mcp-server:bootJar` build step, and note that both servers are evaluated. Add the export line to the run instructions:

```bash
export TFT_MCP_JAR="$(ls tft-mcp-server/build/libs/tft-mcp-server-*.jar | grep -v plain)"
```

- [ ] **Step 6: Update `.github/workflows/live-eval.yml`** — build both jars (`./gradlew :lol-mcp-server:bootJar :tft-mcp-server:bootJar`) and export `TFT_MCP_JAR` (and `TFT_MCP_SSE_URL` for the sse leg) alongside the existing LoL vars. Follow the exact step structure already present for LoL.

- [ ] **Step 7: Commit** (evals are not run in the offline gate; commit without executing them here):

```bash
git add eval .github/workflows/live-eval.yml
git commit -m "test(eval): generalize harness to multi-server; add TFT live evals"
```

---

### Task 11: Documentation + release

Persist the knowledge and cut the release.

**Files:**
- Create: `tft-mcp-server/README.md`
- Modify: root `README.md`, `ARCHITECTURE.md`, `CHANGELOG.md`
- Modify: `docs/knowledge/roadmap.md`, `docs/knowledge/glossary.md`
- Modify: `tft-mcp-server/build.gradle` (version, at release)

- [ ] **Step 1: Write `tft-mcp-server/README.md`** — model it on `lol-mcp-server/README.md`: intro, an MCP-tools table listing all 11 tools with their purpose, and the quick-start block. The `verifyModuleDocs` gate requires this file. Verify the exact required sections by running `./gradlew :tft-mcp-server:verifyModuleDocs` and satisfying whatever it reports missing.

- [ ] **Step 2: Update root `README.md` and `ARCHITECTURE.md`** — describe the repo as hosting **two** servers (`lol-mcp-server`, `tft-mcp-server`); add TFT to any server list/diagram; keep the dependency-rule description (servers → account-core → api-core) intact.

- [ ] **Step 3: Update `docs/knowledge/roadmap.md`** — set row #2 status to ✅ Done with a link to `docs/superpowers/specs/2026-07-19-tft-server-design.md`, and add a progress note stating whether the falsifiable criterion held (TFT shipped with zero changes to `riot-api-core` / `riot-account-core`). If a library change was forced, record it as a finding in `docs/knowledge/gotchas.md` and reference it here.

- [ ] **Step 4: Update `docs/knowledge/glossary.md`** — add TFT domain terms: *set*, *trait*, *unit*, *augment*, *placement*, *top-4*, *rated ladder / Hyper Roll*.

- [ ] **Step 5: Run the full repo gate — expect PASS.**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` across all modules, including `verifyModuleDocs`.

- [ ] **Step 6: Cut the release with the `prepare-release` skill** — invoke `prepare-release` for `tft-mcp-server`: it works out what changed, classifies the bump (initial `0.1.0`), writes the `CHANGELOG.md` entry, confirms the `build.gradle` version, and creates the module-scoped tag. Follow the skill; do not hand-edit the version/tag outside it.

- [ ] **Step 7: Commit any remaining docs**

```bash
git add tft-mcp-server/README.md README.md ARCHITECTURE.md CHANGELOG.md docs
git commit -m "docs(tft): module README, roadmap #2 done, glossary, changelog"
```

- [ ] **Step 8: Open the PR**

```bash
git push -u origin feat/tft-mcp-server
gh pr create --title "feat: TFT MCP server (sub-project 2)" --body "Implements docs/superpowers/specs/2026-07-19-tft-server-design.md — a second Riot game server (11 tools, 6 contexts) on the shared core, with a TFT-native analytics tool. Zero library changes."
```

---

## Self-review notes (for the implementer)

- **Standing user-owed gates** (from the spec) are NOT covered by any task because they need a live key + a human: the live stdio+sse handshake, and endpoint-path verification against the Riot developer portal. Do the portal verification opportunistically while writing each adapter's fixture (Global Constraints), and flag the handshake for the maintainer at PR time.
- **Verify before trusting this plan's field names** in two places the plan could not confirm offline: `RiotAccount`'s builder fields (Task 6 Step 1) and the exact `transport`/`url` key shape in `mcpeval.sse.yaml` (Task 10 Step 2). Read the real file first, adjust if needed.
- **Endpoint paths** throughout are design intent; the WireMock fixtures encode them, so a wrong path fails its adapter test loudly once you correct the fixture to a real response.
