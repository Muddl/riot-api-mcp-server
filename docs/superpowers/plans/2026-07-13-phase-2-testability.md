# Phase 2: Testability — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every outbound Riot adapter a WireMock HTTP test and every application service a hand-written in-memory port fake, then delete the `@Disabled` integration tests and `CompilationVerificationTest`, so `./gradlew build` runs a real, fast, offline test suite with no Riot API key.

**Architecture:** This phase builds on the post-Phase-1 bounded-context hexagons. Each `Riot*Adapter` in `<context>.adapter.out.riot` is exercised against a local WireMock server (the real `RestClient` from `shared/http/RiotApiClient` hits `http://localhost:<port>` via `RiotApiProperties.baseUrlOverride`), asserting request URL/query/header, JSON→DTO parsing, and error mapping. Each application service is retested against a hand-written `InMemory*Port` fake instead of a Mockito mock; `AnalyticsService` is wired to real `RiotAccountService`/`SummonerService`/`MatchService` backed by those same fakes.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0 (MCP server), Lombok, JUnit 5, AssertJ, WireMock (`org.wiremock:wiremock-standalone:3.9.2`, already on the test classpath from Phase 1). Mockito is retired from the service tests in favour of in-memory fakes.

## Global Constraints

- Package root: `com.wkaiser.riotapimcpserver`. Context packages are top-level (`account`, `summoner`, `match`, `spectator`, `analytics`, `shared`).
- Java toolchain: 21. Spring Boot: `4.1.0`. Spring AI BOM: `2.0.0`. Do not change these versions.
- WireMock is already a `testImplementation` dependency (`org.wiremock:wiremock-standalone:3.9.2`, added in Phase 1 Task 2). Do **not** re-add it; reuse it. No `build.gradle` change occurs in this phase.
- Tests must run **offline with no `RIOT_API_KEY`**. WireMock binds a dynamic port; `RiotApiProperties.setBaseUrlOverride("http://localhost:" + port)` points every `RestClient` at that server. The API key is a fixed literal (`"test-key-123"`) set on `RiotApiProperties` in each test.
- WireMock tests use the same static-import style as Phase 1's `RiotApiClientTest` (`import static com.github.tomakehurst.wiremock.client.WireMock.*` members, `options()` from `WireMockConfiguration`).
- Fixtures live in `src/test/resources/fixtures/` and are loaded from the classpath via `com.wkaiser.riotapimcpserver.testsupport.Fixtures`. Fixture JSON uses **only real DTO field names** (the `Match`/`MatchInfo`/`Participant` DTOs lack `@JsonIgnoreProperties`, so an unknown field would fail deserialization — never add fields the DTO does not declare).
- Port fakes are hand-written `public` classes implementing the Phase-1 port interfaces (`RiotAccountPort`, `SummonerPort`, `MatchPort`, `SpectatorPort`); no Mockito, no HTTP. They live in the matching context's application **test** package.
- Adapter constructors are exactly as built in Phase 1: `RiotAccountRiotAdapter(RiotApiClient, RiotApiProperties)`; `RiotSummonerAdapter(RiotApiClient)`; `RiotMatchAdapter(RiotApiClient)`; `RiotSpectatorAdapter(RiotApiClient)`.
- Every task ends with `./gradlew build` passing (run from Git Bash).
- The Riot API key must never be hard-coded in `main` or logged; the test literal `"test-key-123"` lives only in test code.

---

### Task 1: Test fixtures and a classpath fixture loader

Introduces the canned JSON responses used by all four WireMock adapter tests, plus a tiny loader so tests read fixtures by name. No production code changes; this task is self-contained and verified by a loader self-test.

**Files:**
- Create: `src/test/resources/fixtures/account.json`
- Create: `src/test/resources/fixtures/summoner.json`
- Create: `src/test/resources/fixtures/match-ids.json`
- Create: `src/test/resources/fixtures/match.json`
- Create: `src/test/resources/fixtures/current-game.json`
- Create: `src/test/resources/fixtures/featured-games.json`
- Create: `src/test/java/com/wkaiser/riotapimcpserver/testsupport/Fixtures.java`
- Test: `src/test/java/com/wkaiser/riotapimcpserver/testsupport/FixturesTest.java`

**Interfaces:**
- Produces: `Fixtures.read(String name)` → the UTF-8 contents of `/fixtures/<name>` from the test classpath; throws `IllegalArgumentException` if the resource is missing.

- [ ] **Step 1: Create the account fixture**

Create `src/test/resources/fixtures/account.json` (fields match `account.domain.RiotAccount`: `puuid`, `gameName`, `tagLine`):

```json
{
  "puuid": "test-puuid-abc123",
  "gameName": "Bjergsen",
  "tagLine": "NA1"
}
```

- [ ] **Step 2: Create the summoner fixture**

Create `src/test/resources/fixtures/summoner.json` (fields match `summoner.domain.Summoner`):

```json
{
  "accountId": "acct-1",
  "profileIconId": 4567,
  "revisionDate": 1690000000000,
  "name": "Bjergsen",
  "id": "summoner-id-xyz",
  "puuid": "test-puuid-abc123",
  "summonerLevel": 350
}
```

- [ ] **Step 3: Create the match-ids fixture**

Create `src/test/resources/fixtures/match-ids.json` (a raw JSON array of match-id strings, matching `MatchPort.getMatchIdsByPuuid`'s `List<String>` return):

```json
["NA1_4600000001", "NA1_4600000002", "NA1_4600000003"]
```

- [ ] **Step 4: Create the match fixture**

Create `src/test/resources/fixtures/match.json`. Every key is a declared field on `match.domain.Match` / `MatchInfo` / `MatchMetadata` / `Participant` — do not add others (these DTOs are not `@JsonIgnoreProperties`):

```json
{
  "metadata": {
    "dataVersion": "2",
    "matchId": "NA1_4600000001",
    "participants": ["test-puuid-abc123", "other-puuid-2"]
  },
  "info": {
    "gameCreation": 1690000000000,
    "gameDuration": 1834,
    "gameId": 4600000001,
    "gameMode": "CLASSIC",
    "gameType": "MATCHED_GAME",
    "gameVersion": "14.1.1",
    "mapId": 11,
    "queueId": 420,
    "platformId": "NA1",
    "participants": [
      {
        "puuid": "test-puuid-abc123",
        "summonerName": "Bjergsen",
        "championName": "Ahri",
        "teamId": 100,
        "teamPosition": "MIDDLE",
        "kills": 10,
        "deaths": 2,
        "assists": 8,
        "win": true,
        "visionScore": 25,
        "totalMinionsKilled": 210,
        "neutralMinionsKilled": 12
      },
      {
        "puuid": "other-puuid-2",
        "summonerName": "Opponent",
        "championName": "Zed",
        "teamId": 200,
        "teamPosition": "MIDDLE",
        "kills": 5,
        "deaths": 6,
        "assists": 3,
        "win": false,
        "visionScore": 18,
        "totalMinionsKilled": 190,
        "neutralMinionsKilled": 4
      }
    ]
  }
}
```

- [ ] **Step 5: Create the current-game fixture**

Create `src/test/resources/fixtures/current-game.json` (fields match `spectator.domain.CurrentGameInfo` and its nested types; these DTOs are `@JsonIgnoreProperties`, but keep it tight anyway):

```json
{
  "gameId": 4600000999,
  "gameType": "MATCHED_GAME",
  "gameStartTime": 1690000000000,
  "mapId": 11,
  "gameLength": 620,
  "platformId": "NA1",
  "gameMode": "CLASSIC",
  "gameQueueConfigId": 420,
  "bannedChampions": [
    { "championId": 266, "teamId": 100, "pickTurn": 1 }
  ],
  "observers": { "encryptionKey": "sample-encryption-key" },
  "participants": [
    {
      "championId": 103,
      "summonerName": "Bjergsen",
      "summonerId": "encrypted-summoner-id-1",
      "puuid": "test-puuid-abc123",
      "teamId": 100,
      "bot": false,
      "spell1Id": 4,
      "spell2Id": 7,
      "profileIconId": 4567,
      "summonerLevel": 350,
      "perks": { "perkIds": [8112, 8126], "perkStyle": 8100, "perkSubStyle": 8200 }
    }
  ]
}
```

- [ ] **Step 6: Create the featured-games fixture**

Create `src/test/resources/fixtures/featured-games.json` (fields match `spectator.domain.FeaturedGames`):

```json
{
  "clientRefreshInterval": 300,
  "gameList": [
    {
      "gameId": 4600001234,
      "gameType": "MATCHED_GAME",
      "gameStartTime": 1690000000000,
      "mapId": 11,
      "gameLength": 900,
      "platformId": "NA1",
      "gameMode": "CLASSIC",
      "gameQueueConfigId": 420,
      "bannedChampions": [],
      "observers": { "encryptionKey": "featured-encryption-key" },
      "participants": [
        {
          "championId": 64,
          "summonerName": "FeaturedPlayer1",
          "summonerId": "encrypted-summoner-id-2",
          "puuid": "featured-puuid-1",
          "teamId": 100,
          "bot": false,
          "spell1Id": 11,
          "spell2Id": 4,
          "profileIconId": 1111,
          "summonerLevel": 200
        }
      ]
    }
  ]
}
```

- [ ] **Step 7: Create the fixture loader**

Create `src/test/java/com/wkaiser/riotapimcpserver/testsupport/Fixtures.java`:

```java
package com.wkaiser.riotapimcpserver.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads canned JSON fixtures from {@code src/test/resources/fixtures/} on the test
 * classpath. Used by the WireMock adapter tests to stub Riot API responses.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /** Returns the UTF-8 contents of {@code /fixtures/<name>}; fails fast if absent. */
    public static String read(String name) {
        String path = "/fixtures/" + name;
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fixture: " + path, e);
        }
    }
}
```

- [ ] **Step 8: Write the loader self-test**

Create `src/test/java/com/wkaiser/riotapimcpserver/testsupport/FixturesTest.java`:

```java
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
```

- [ ] **Step 9: Run the loader test and the build**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.testsupport.FixturesTest"`
Expected: PASS (2 tests).

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add src/test/resources/fixtures \
        src/test/java/com/wkaiser/riotapimcpserver/testsupport/Fixtures.java \
        src/test/java/com/wkaiser/riotapimcpserver/testsupport/FixturesTest.java
git commit -m "test: add JSON fixtures and classpath fixture loader"
```

---

### Task 2: WireMock test — `RiotAccountRiotAdapter`

The account adapter already exists (Phase 1 Task 4). This adds its HTTP-level test: URL, `X-RIOT-TOKEN` header, JSON→DTO parsing, and error mapping. The adapter selects its client via `properties.getRegion()`, but because `baseUrlOverride` is set, all requests go to WireMock regardless of region.

**Files:**
- Test: `src/test/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot/RiotAccountRiotAdapterTest.java`

**Interfaces:**
- Consumes: `RiotAccountRiotAdapter(RiotApiClient, RiotApiProperties)`, `RiotApiClient(RiotApiProperties)`, `RiotAccountPort` (Phase 1), `Fixtures.read` (Task 1).
- Produces: no production code — a test asserting the account adapter's HTTP contract.

- [ ] **Step 1: Write the adapter test**

Create `src/test/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot/RiotAccountRiotAdapterTest.java`:

```java
package com.wkaiser.riotapimcpserver.account.adapter.out.riot;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import com.wkaiser.riotapimcpserver.testsupport.Fixtures;
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

class RiotAccountRiotAdapterTest {

    private WireMockServer wireMock;
    private RiotAccountPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        RiotApiClient client = new RiotApiClient(properties);
        adapter = new RiotAccountRiotAdapter(client, properties);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getAccountByRiotId_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo("/riot/account/v1/accounts/by-riot-id/Bjergsen/NA1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("account.json"))));

        RiotAccount account = adapter.getAccountByRiotId("Bjergsen", "NA1");

        assertThat(account.getPuuid()).isEqualTo("test-puuid-abc123");
        assertThat(account.getGameName()).isEqualTo("Bjergsen");
        assertThat(account.getTagLine()).isEqualTo("NA1");
        verify(getRequestedFor(urlEqualTo("/riot/account/v1/accounts/by-riot-id/Bjergsen/NA1"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getAccountByPuuid_parsesBody_andHitsExpectedUrl() {
        stubFor(get(urlEqualTo("/riot/account/v1/accounts/by-puuid/test-puuid-abc123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("account.json"))));

        RiotAccount account = adapter.getAccountByPuuid("test-puuid-abc123");

        assertThat(account.getPuuid()).isEqualTo("test-puuid-abc123");
        verify(getRequestedFor(urlEqualTo("/riot/account/v1/accounts/by-puuid/test-puuid-abc123"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo("/riot/account/v1/accounts/by-riot-id/Ghost/NA1"))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.getAccountByRiotId("Ghost", "NA1"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
```

- [ ] **Step 2: Run the account adapter test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.account.adapter.out.riot.RiotAccountRiotAdapterTest"`
Expected: PASS (3 tests). (The adapter is already implemented; this test exercises it over HTTP.)

- [ ] **Step 3: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot/RiotAccountRiotAdapterTest.java
git commit -m "test: add WireMock test for RiotAccountRiotAdapter"
```

---

### Task 3: WireMock test — `RiotSummonerAdapter`

Summoner endpoints are platform-routed; the adapter takes only `RiotApiClient`. Tests cover all three lookups plus error mapping.

**Files:**
- Test: `src/test/java/com/wkaiser/riotapimcpserver/summoner/adapter/out/riot/RiotSummonerAdapterTest.java`

**Interfaces:**
- Consumes: `RiotSummonerAdapter(RiotApiClient)`, `SummonerPort` (Phase 1), `RiotApiPlatformUri`, `Fixtures.read` (Task 1).
- Produces: no production code — a test asserting the summoner adapter's HTTP contract.

- [ ] **Step 1: Write the adapter test**

Create `src/test/java/com/wkaiser/riotapimcpserver/summoner/adapter/out/riot/RiotSummonerAdapterTest.java`:

```java
package com.wkaiser.riotapimcpserver.summoner.adapter.out.riot;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import com.wkaiser.riotapimcpserver.testsupport.Fixtures;
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

class RiotSummonerAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private WireMockServer wireMock;
    private SummonerPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotSummonerAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getSummonerByName_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/by-name/Bjergsen"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("summoner.json"))));

        Summoner summoner = adapter.getSummonerByName(PLATFORM, "Bjergsen");

        assertThat(summoner.getName()).isEqualTo("Bjergsen");
        assertThat(summoner.getId()).isEqualTo("summoner-id-xyz");
        assertThat(summoner.getPuuid()).isEqualTo("test-puuid-abc123");
        assertThat(summoner.getSummonerLevel()).isEqualTo(350L);
        verify(getRequestedFor(urlEqualTo("/lol/summoner/v4/summoners/by-name/Bjergsen"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getSummonerByPuuid_hitsExpectedUrl() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/by-puuid/test-puuid-abc123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("summoner.json"))));

        Summoner summoner = adapter.getSummonerByPuuid(PLATFORM, "test-puuid-abc123");

        assertThat(summoner.getPuuid()).isEqualTo("test-puuid-abc123");
        verify(getRequestedFor(urlEqualTo("/lol/summoner/v4/summoners/by-puuid/test-puuid-abc123"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getSummonerById_hitsExpectedUrl() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/summoner-id-xyz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("summoner.json"))));

        Summoner summoner = adapter.getSummonerById(PLATFORM, "summoner-id-xyz");

        assertThat(summoner.getId()).isEqualTo("summoner-id-xyz");
        verify(getRequestedFor(urlEqualTo("/lol/summoner/v4/summoners/summoner-id-xyz"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/by-name/Ghost"))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> adapter.getSummonerByName(PLATFORM, "Ghost"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(403);
    }
}
```

- [ ] **Step 2: Run the summoner adapter test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.summoner.adapter.out.riot.RiotSummonerAdapterTest"`
Expected: PASS (4 tests).

- [ ] **Step 3: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/summoner/adapter/out/riot/RiotSummonerAdapterTest.java
git commit -m "test: add WireMock test for RiotSummonerAdapter"
```

---

### Task 4: WireMock test — `RiotMatchAdapter`

Match endpoints are region-routed and the id-list endpoint assembles query parameters (`count` clamped to 100, optional `start`, optional `queue`). Tests assert the path plus query params (and omission when a param is `null`), parse the nested `Match`, and cover error mapping. Note Phase 1 fixed the previously-missing auth header on match requests, so the header assertion here is a genuine regression guard.

**Files:**
- Test: `src/test/java/com/wkaiser/riotapimcpserver/match/adapter/out/riot/RiotMatchAdapterTest.java`

**Interfaces:**
- Consumes: `RiotMatchAdapter(RiotApiClient)`, `MatchPort` (Phase 1), `RiotApiRegionUri`, `match.domain.Match`, `Fixtures.read` (Task 1).
- Produces: no production code — a test asserting the match adapter's HTTP contract.

- [ ] **Step 1: Write the adapter test**

Create `src/test/java/com/wkaiser/riotapimcpserver/match/adapter/out/riot/RiotMatchAdapterTest.java`:

```java
package com.wkaiser.riotapimcpserver.match.adapter.out.riot;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import com.wkaiser.riotapimcpserver.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiotMatchAdapterTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "test-puuid-abc123";
    private static final String IDS_PATH = "/lol/match/v5/matches/by-puuid/" + PUUID + "/ids";

    private WireMockServer wireMock;
    private MatchPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotMatchAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getMatchIdsByPuuid_sendsAllQueryParams_andSendsApiKeyHeader() {
        stubFor(get(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("5"))
                .withQueryParam("start", equalTo("0"))
                .withQueryParam("queue", equalTo("420"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("match-ids.json"))));

        List<String> ids = adapter.getMatchIdsByPuuid(REGION, PUUID, 5, 0, 420);

        assertThat(ids).containsExactly("NA1_4600000001", "NA1_4600000002", "NA1_4600000003");
        verify(getRequestedFor(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("5"))
                .withQueryParam("start", equalTo("0"))
                .withQueryParam("queue", equalTo("420"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getMatchIdsByPuuid_omitsQueueParam_whenQueueNull() {
        stubFor(get(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("10"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("match-ids.json"))));

        adapter.getMatchIdsByPuuid(REGION, PUUID, 10, null, null);

        verify(getRequestedFor(urlPathEqualTo(IDS_PATH))
                .withQueryParam("count", equalTo("10"))
                .withQueryParam("start", absent())
                .withQueryParam("queue", absent()));
    }

    @Test
    void getMatchById_parsesNestedMatch() {
        stubFor(get(urlEqualTo("/lol/match/v5/matches/NA1_4600000001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("match.json"))));

        Match match = adapter.getMatchById(REGION, "NA1_4600000001");

        assertThat(match.getMetadata().getMatchId()).isEqualTo("NA1_4600000001");
        assertThat(match.getInfo().getGameDuration()).isEqualTo(1834L);
        assertThat(match.getInfo().getParticipants()).hasSize(2);
        assertThat(match.getInfo().getParticipants().get(0).getChampionName()).isEqualTo("Ahri");
        assertThat(match.getInfo().getParticipants().get(0).isWin()).isTrue();
        verify(getRequestedFor(urlEqualTo("/lol/match/v5/matches/NA1_4600000001"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo("/lol/match/v5/matches/NA1_MISSING"))
                .willReturn(aResponse().withStatus(500).withBody("server error")));

        assertThatThrownBy(() -> adapter.getMatchById(REGION, "NA1_MISSING"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(500);
    }
}
```

- [ ] **Step 2: Run the match adapter test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.match.adapter.out.riot.RiotMatchAdapterTest"`
Expected: PASS (4 tests).

- [ ] **Step 3: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/match/adapter/out/riot/RiotMatchAdapterTest.java
git commit -m "test: add WireMock test for RiotMatchAdapter"
```

---

### Task 5: WireMock test — `RiotSpectatorAdapter`

The spectator adapter has the one context-specific rule that the shared error handler does not: `404 → null` (summoner not in a game), while any other non-2xx propagates as `RiotApiException`. This test pins that behaviour, plus URL/header/parsing for both endpoints.

**Files:**
- Test: `src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot/RiotSpectatorAdapterTest.java`

**Interfaces:**
- Consumes: `RiotSpectatorAdapter(RiotApiClient)`, `SpectatorPort` (Phase 1), `spectator.domain.{CurrentGameInfo,FeaturedGames}`, `RiotApiPlatformUri`, `Fixtures.read` (Task 1).
- Produces: no production code — a test asserting the spectator adapter's HTTP contract, including `404 → null`.

- [ ] **Step 1: Write the adapter test**

Create `src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot/RiotSpectatorAdapterTest.java`:

```java
package com.wkaiser.riotapimcpserver.spectator.adapter.out.riot;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.exception.RiotApiException;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import com.wkaiser.riotapimcpserver.spectator.application.port.SpectatorPort;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import com.wkaiser.riotapimcpserver.testsupport.Fixtures;
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

class RiotSpectatorAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String SUMMONER_ID = "encrypted-summoner-id-1";
    private static final String ACTIVE_GAME_URL =
            "/lol/spectator/v4/active-games/by-summoner/" + SUMMONER_ID;

    private WireMockServer wireMock;
    private SpectatorPort adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RiotApiProperties properties = new RiotApiProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new RiotSpectatorAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getCurrentGameInfo_parsesBody_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo(ACTIVE_GAME_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("current-game.json"))));

        CurrentGameInfo game = adapter.getCurrentGameInfo(PLATFORM, SUMMONER_ID);

        assertThat(game).isNotNull();
        assertThat(game.getGameId()).isEqualTo(4600000999L);
        assertThat(game.getGameMode()).isEqualTo("CLASSIC");
        assertThat(game.getParticipants()).hasSize(1);
        assertThat(game.getParticipants().get(0).getSummonerName()).isEqualTo("Bjergsen");
        verify(getRequestedFor(urlEqualTo(ACTIVE_GAME_URL))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getCurrentGameInfo_returnsNull_whenNotInGame_404() {
        stubFor(get(urlEqualTo(ACTIVE_GAME_URL))
                .willReturn(aResponse().withStatus(404).withBody("not in game")));

        CurrentGameInfo game = adapter.getCurrentGameInfo(PLATFORM, SUMMONER_ID);

        assertThat(game).isNull();
    }

    @Test
    void getCurrentGameInfo_propagatesNon404Errors() {
        stubFor(get(urlEqualTo(ACTIVE_GAME_URL))
                .willReturn(aResponse().withStatus(500).withBody("server error")));

        assertThatThrownBy(() -> adapter.getCurrentGameInfo(PLATFORM, SUMMONER_ID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(500);
    }

    @Test
    void getFeaturedGames_parsesBody() {
        stubFor(get(urlEqualTo("/lol/spectator/v4/featured-games"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("featured-games.json"))));

        FeaturedGames featured = adapter.getFeaturedGames(PLATFORM);

        assertThat(featured.getClientRefreshInterval()).isEqualTo(300L);
        assertThat(featured.getGameList()).hasSize(1);
        assertThat(featured.getGameList().get(0).getGameId()).isEqualTo(4600001234L);
        verify(getRequestedFor(urlEqualTo("/lol/spectator/v4/featured-games"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }
}
```

- [ ] **Step 2: Run the spectator adapter test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.spectator.adapter.out.riot.RiotSpectatorAdapterTest"`
Expected: PASS (4 tests).

- [ ] **Step 3: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot/RiotSpectatorAdapterTest.java
git commit -m "test: add WireMock test for RiotSpectatorAdapter including 404-not-in-game"
```

---

### Task 6: In-memory `RiotAccountPort` fake + rewrite account service test

Replaces the Phase-1 Mockito mock in `RiotAccountServiceTest` with a hand-written in-memory fake. The fake is `public` so `AnalyticsServiceTest` (Task 10) can reuse it.

**Files:**
- Create: `src/test/java/com/wkaiser/riotapimcpserver/account/application/InMemoryRiotAccountPort.java`
- Modify (full rewrite): `src/test/java/com/wkaiser/riotapimcpserver/account/application/RiotAccountServiceTest.java`

**Interfaces:**
- Consumes: `RiotAccountPort`, `account.domain.RiotAccount`, `account.application.RiotAccountService` (all Phase 1).
- Produces: `InMemoryRiotAccountPort` with fluent `add(RiotAccount)`; returns `null` for unknown lookups (mirroring a real 404-less miss).

- [ ] **Step 1: Create the in-memory fake**

Create `src/test/java/com/wkaiser/riotapimcpserver/account/application/InMemoryRiotAccountPort.java`:

```java
package com.wkaiser.riotapimcpserver.account.application;

import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;

import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link RiotAccountPort} for fast, HTTP-free service tests. */
public class InMemoryRiotAccountPort implements RiotAccountPort {

    private final Map<String, RiotAccount> byRiotId = new HashMap<>();
    private final Map<String, RiotAccount> byPuuid = new HashMap<>();

    /** Registers an account under both its Riot ID and its PUUID (whichever are present). */
    public InMemoryRiotAccountPort add(RiotAccount account) {
        if (account.getGameName() != null && account.getTagLine() != null) {
            byRiotId.put(riotIdKey(account.getGameName(), account.getTagLine()), account);
        }
        if (account.getPuuid() != null) {
            byPuuid.put(account.getPuuid(), account);
        }
        return this;
    }

    @Override
    public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
        return byRiotId.get(riotIdKey(gameName, tagLine));
    }

    @Override
    public RiotAccount getAccountByPuuid(String puuid) {
        return byPuuid.get(puuid);
    }

    private static String riotIdKey(String gameName, String tagLine) {
        return gameName + "#" + tagLine;
    }
}
```

- [ ] **Step 2: Rewrite the service test to use the fake**

Replace the entire contents of `src/test/java/com/wkaiser/riotapimcpserver/account/application/RiotAccountServiceTest.java` with:

```java
package com.wkaiser.riotapimcpserver.account.application;

import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiotAccountServiceTest {

    private final InMemoryRiotAccountPort accountPort = new InMemoryRiotAccountPort();
    private final RiotAccountService accountService = new RiotAccountService(accountPort);

    @Test
    void getAccountByRiotId_returnsStoredAccount() {
        RiotAccount stored = RiotAccount.builder().puuid("p").gameName("Name").tagLine("NA1").build();
        accountPort.add(stored);

        assertThat(accountService.getAccountByRiotId("Name", "NA1")).isSameAs(stored);
    }

    @Test
    void getAccountByPuuid_returnsStoredAccount() {
        RiotAccount stored = RiotAccount.builder().puuid("p").gameName("Name").tagLine("NA1").build();
        accountPort.add(stored);

        assertThat(accountService.getAccountByPuuid("p")).isSameAs(stored);
    }

    @Test
    void getAccountByRiotId_returnsNull_whenUnknown() {
        assertThat(accountService.getAccountByRiotId("Ghost", "NA1")).isNull();
    }
}
```

- [ ] **Step 3: Run the account service test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.account.application.RiotAccountServiceTest"`
Expected: PASS (3 tests).

- [ ] **Step 4: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/account/application/InMemoryRiotAccountPort.java \
        src/test/java/com/wkaiser/riotapimcpserver/account/application/RiotAccountServiceTest.java
git commit -m "test: replace account service Mockito mock with in-memory port fake"
```

---

### Task 7: In-memory `SummonerPort` fake + rewrite summoner service test

**Files:**
- Create: `src/test/java/com/wkaiser/riotapimcpserver/summoner/application/InMemorySummonerPort.java`
- Modify (full rewrite): `src/test/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerServiceTest.java`

**Interfaces:**
- Consumes: `SummonerPort`, `summoner.domain.Summoner`, `summoner.application.SummonerService`, `RiotApiPlatformUri` (all Phase 1).
- Produces: `InMemorySummonerPort` with fluent `putByName/putByPuuid/putById`, keyed by `(platform, key)`; returns `null` for unknown lookups.

- [ ] **Step 1: Create the in-memory fake**

Create `src/test/java/com/wkaiser/riotapimcpserver/summoner/application/InMemorySummonerPort.java`:

```java
package com.wkaiser.riotapimcpserver.summoner.application;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.summoner.application.port.SummonerPort;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;

import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SummonerPort} for fast, HTTP-free service tests. */
public class InMemorySummonerPort implements SummonerPort {

    private final Map<String, Summoner> byName = new HashMap<>();
    private final Map<String, Summoner> byPuuid = new HashMap<>();
    private final Map<String, Summoner> byId = new HashMap<>();

    public InMemorySummonerPort putByName(RiotApiPlatformUri platform, String name, Summoner summoner) {
        byName.put(key(platform, name), summoner);
        return this;
    }

    public InMemorySummonerPort putByPuuid(RiotApiPlatformUri platform, String puuid, Summoner summoner) {
        byPuuid.put(key(platform, puuid), summoner);
        return this;
    }

    public InMemorySummonerPort putById(RiotApiPlatformUri platform, String id, Summoner summoner) {
        byId.put(key(platform, id), summoner);
        return this;
    }

    @Override
    public Summoner getSummonerByName(RiotApiPlatformUri platform, String summonerName) {
        return byName.get(key(platform, summonerName));
    }

    @Override
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        return byPuuid.get(key(platform, puuid));
    }

    @Override
    public Summoner getSummonerById(RiotApiPlatformUri platform, String summonerId) {
        return byId.get(key(platform, summonerId));
    }

    private static String key(RiotApiPlatformUri platform, String value) {
        return platform.name() + "|" + value;
    }
}
```

- [ ] **Step 2: Rewrite the service test to use the fake**

Replace the entire contents of `src/test/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerServiceTest.java` with:

```java
package com.wkaiser.riotapimcpserver.summoner.application;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final SummonerService summonerService = new SummonerService(summonerPort);

    @Test
    void getSummonerByName_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().id("id").name("Name").build();
        summonerPort.putByName(PLATFORM, "Name", expected);

        assertThat(summonerService.getSummonerByName(PLATFORM, "Name")).isSameAs(expected);
    }

    @Test
    void getSummonerByPuuid_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().puuid("p").build();
        summonerPort.putByPuuid(PLATFORM, "p", expected);

        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "p")).isSameAs(expected);
    }

    @Test
    void getSummonerById_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().id("id").build();
        summonerPort.putById(PLATFORM, "id", expected);

        assertThat(summonerService.getSummonerById(PLATFORM, "id")).isSameAs(expected);
    }

    @Test
    void getSummonerByName_returnsNull_whenUnknown() {
        assertThat(summonerService.getSummonerByName(PLATFORM, "Ghost")).isNull();
    }
}
```

- [ ] **Step 3: Run the summoner service test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.summoner.application.SummonerServiceTest"`
Expected: PASS (4 tests).

- [ ] **Step 4: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/summoner/application/InMemorySummonerPort.java \
        src/test/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerServiceTest.java
git commit -m "test: replace summoner service Mockito mock with in-memory port fake"
```

---

### Task 8: In-memory `MatchPort` fake + rewrite match service test

The fake honours `count` (via `limit`) and `start` (via `skip`) so `AnalyticsServiceTest` (Task 10) gets realistic paging behaviour; `queue` is ignored (the fake stores one list per PUUID).

**Files:**
- Create: `src/test/java/com/wkaiser/riotapimcpserver/match/application/InMemoryMatchPort.java`
- Modify (full rewrite): `src/test/java/com/wkaiser/riotapimcpserver/match/application/MatchServiceTest.java`

**Interfaces:**
- Consumes: `MatchPort`, `match.domain.Match`, `match.application.MatchService`, `RiotApiRegionUri` (all Phase 1).
- Produces: `InMemoryMatchPort` with fluent `putMatchIds(puuid, ids)` and `putMatch(matchId, match)`; unknown match → `null`, unknown PUUID → empty list.

- [ ] **Step 1: Create the in-memory fake**

Create `src/test/java/com/wkaiser/riotapimcpserver/match/application/InMemoryMatchPort.java`:

```java
package com.wkaiser.riotapimcpserver.match.application;

import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link MatchPort} for fast, HTTP-free service tests. */
public class InMemoryMatchPort implements MatchPort {

    private final Map<String, List<String>> idsByPuuid = new HashMap<>();
    private final Map<String, Match> matchesById = new HashMap<>();

    public InMemoryMatchPort putMatchIds(String puuid, List<String> ids) {
        idsByPuuid.put(puuid, ids);
        return this;
    }

    public InMemoryMatchPort putMatch(String matchId, Match match) {
        matchesById.put(matchId, match);
        return this;
    }

    @Override
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        List<String> all = idsByPuuid.getOrDefault(puuid, List.of());
        return all.stream()
                .skip(start == null ? 0 : start)
                .limit(count == null ? Long.MAX_VALUE : count)
                .toList();
    }

    @Override
    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        return matchesById.get(matchId);
    }
}
```

- [ ] **Step 2: Rewrite the service test to use the fake**

Replace the entire contents of `src/test/java/com/wkaiser/riotapimcpserver/match/application/MatchServiceTest.java` with:

```java
package com.wkaiser.riotapimcpserver.match.application;

import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchServiceTest {

    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;

    private final InMemoryMatchPort matchPort = new InMemoryMatchPort();
    private final MatchService matchService = new MatchService(matchPort);

    @Test
    void getMatchIdsByPuuid_returnsStoredIds() {
        matchPort.putMatchIds("p", List.of("NA1_1", "NA1_2"));

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 20, 0, null))
                .containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchIdsByPuuid_honoursCountLimit() {
        matchPort.putMatchIds("p", List.of("NA1_1", "NA1_2", "NA1_3"));

        assertThat(matchService.getMatchIdsByPuuid(REGION, "p", 2, 0, null))
                .containsExactly("NA1_1", "NA1_2");
    }

    @Test
    void getMatchById_returnsStoredMatch() {
        Match expected = Match.builder().build();
        matchPort.putMatch("NA1_1", expected);

        assertThat(matchService.getMatchById(REGION, "NA1_1")).isSameAs(expected);
    }

    @Test
    void getMatchIdsByPuuid_returnsEmpty_whenUnknownPuuid() {
        assertThat(matchService.getMatchIdsByPuuid(REGION, "unknown", 20, 0, null)).isEmpty();
    }
}
```

- [ ] **Step 3: Run the match service test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.match.application.MatchServiceTest"`
Expected: PASS (4 tests).

- [ ] **Step 4: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/match/application/InMemoryMatchPort.java \
        src/test/java/com/wkaiser/riotapimcpserver/match/application/MatchServiceTest.java
git commit -m "test: replace match service Mockito mock with in-memory port fake"
```

---

### Task 9: In-memory `SpectatorPort` fake + spectator service test

Phase 1 deleted the brittle `SpectatorServiceTest`. This restores real coverage for `SpectatorService` at the service level using an in-memory fake and the existing `SpectatorTestFixtures` (introduced in Phase 1 Task 7). The fake models "not in a game" as a `null` return.

**Files:**
- Create: `src/test/java/com/wkaiser/riotapimcpserver/spectator/application/InMemorySpectatorPort.java`
- Create: `src/test/java/com/wkaiser/riotapimcpserver/spectator/application/SpectatorServiceTest.java`

**Interfaces:**
- Consumes: `SpectatorPort`, `spectator.domain.{CurrentGameInfo,FeaturedGames}`, `spectator.application.SpectatorService`, `RiotApiPlatformUri`, `spectator.SpectatorTestFixtures` (Phase 1).
- Produces: `InMemorySpectatorPort` with fluent `putGame(summonerId, game)` and `putFeatured(platform, featured)`; unknown summoner → `null` (not in game).

- [ ] **Step 1: Create the in-memory fake**

Create `src/test/java/com/wkaiser/riotapimcpserver/spectator/application/InMemorySpectatorPort.java`:

```java
package com.wkaiser.riotapimcpserver.spectator.application;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.spectator.application.port.SpectatorPort;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;

import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link SpectatorPort} for fast, HTTP-free service tests. */
public class InMemorySpectatorPort implements SpectatorPort {

    private final Map<String, CurrentGameInfo> gamesBySummonerId = new HashMap<>();
    private final Map<RiotApiPlatformUri, FeaturedGames> featuredByPlatform = new HashMap<>();

    public InMemorySpectatorPort putGame(String encryptedSummonerId, CurrentGameInfo game) {
        gamesBySummonerId.put(encryptedSummonerId, game);
        return this;
    }

    public InMemorySpectatorPort putFeatured(RiotApiPlatformUri platform, FeaturedGames featured) {
        featuredByPlatform.put(platform, featured);
        return this;
    }

    /** Returns the stored game, or {@code null} to model "summoner not in a game". */
    @Override
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId) {
        return gamesBySummonerId.get(encryptedSummonerId);
    }

    @Override
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        return featuredByPlatform.get(platform);
    }
}
```

- [ ] **Step 2: Write the spectator service test**

Create `src/test/java/com/wkaiser/riotapimcpserver/spectator/application/SpectatorServiceTest.java`:

```java
package com.wkaiser.riotapimcpserver.spectator.application;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.spectator.SpectatorTestFixtures;
import com.wkaiser.riotapimcpserver.spectator.domain.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.spectator.domain.FeaturedGames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpectatorServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySpectatorPort spectatorPort = new InMemorySpectatorPort();
    private final SpectatorService spectatorService = new SpectatorService(spectatorPort);

    @Test
    void getCurrentGameInfo_returnsStoredGame() {
        CurrentGameInfo game = SpectatorTestFixtures.createSampleCurrentGameInfo();
        spectatorPort.putGame("summoner-in-game", game);

        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "summoner-in-game")).isSameAs(game);
    }

    @Test
    void getCurrentGameInfo_returnsNull_whenSummonerNotInGame() {
        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "summoner-not-in-game")).isNull();
    }

    @Test
    void getFeaturedGames_returnsStoredFeaturedGames() {
        FeaturedGames featured = SpectatorTestFixtures.createSampleFeaturedGames();
        spectatorPort.putFeatured(PLATFORM, featured);

        assertThat(spectatorService.getFeaturedGames(PLATFORM)).isSameAs(featured);
    }
}
```

- [ ] **Step 3: Run the spectator service test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.spectator.application.SpectatorServiceTest"`
Expected: PASS (3 tests).

- [ ] **Step 4: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/spectator/application/InMemorySpectatorPort.java \
        src/test/java/com/wkaiser/riotapimcpserver/spectator/application/SpectatorServiceTest.java
git commit -m "test: add spectator service test backed by in-memory port fake"
```

---

### Task 10: Analytics service test with fakes — zero-games and zero-deaths edges

`AnalyticsService` depends on the concrete `RiotAccountService`, `SummonerService`, and `MatchService`. This test wires those **real** services to the in-memory port fakes from Tasks 6–8, so the composition is exercised end-to-end with no Mockito and no HTTP. The two required edge cases from the spec — zero games and zero-deaths KDA — are covered explicitly, plus a normal two-match win-rate/KDA path. The expected string formats are read straight from the current `AnalyticsService`: `winRate` = `String.format("%.2f%%", …)`, `avgKills/avgDeaths/avgKda` = `String.format("%.2f", …)`, and zero-deaths KDA returns `kills + assists` (perfect KDA).

**Files:**
- Modify (full rewrite): `src/test/java/com/wkaiser/riotapimcpserver/analytics/application/AnalyticsServiceTest.java`

**Interfaces:**
- Consumes: `analytics.application.AnalyticsService` (Phase 1) via constructor `AnalyticsService(RiotAccountService, SummonerService, MatchService)`; the three real services; `InMemoryRiotAccountPort` (Task 6), `InMemorySummonerPort` (Task 7), `InMemoryMatchPort` (Task 8); domain types `RiotAccount`, `Summoner`, `Match`, `MatchInfo`, `Participant`; `PlayerMatchAnalytics`.
- Produces: no production code — the analytics unit test rewritten onto fakes.

- [ ] **Step 1: Rewrite the analytics test onto the fakes**

Replace the entire contents of `src/test/java/com/wkaiser/riotapimcpserver/analytics/application/AnalyticsServiceTest.java` with:

```java
package com.wkaiser.riotapimcpserver.analytics.application;

import com.wkaiser.riotapimcpserver.account.application.InMemoryRiotAccountPort;
import com.wkaiser.riotapimcpserver.account.application.RiotAccountService;
import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;
import com.wkaiser.riotapimcpserver.analytics.domain.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.match.application.InMemoryMatchPort;
import com.wkaiser.riotapimcpserver.match.application.MatchService;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.match.domain.MatchInfo;
import com.wkaiser.riotapimcpserver.match.domain.Participant;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import com.wkaiser.riotapimcpserver.summoner.application.InMemorySummonerPort;
import com.wkaiser.riotapimcpserver.summoner.application.SummonerService;
import com.wkaiser.riotapimcpserver.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PUUID = "puuid-1";

    private final InMemoryRiotAccountPort accountPort = new InMemoryRiotAccountPort();
    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final InMemoryMatchPort matchPort = new InMemoryMatchPort();

    private final AnalyticsService analyticsService = new AnalyticsService(
            new RiotAccountService(accountPort),
            new SummonerService(summonerPort),
            new MatchService(matchPort));

    private void givenPlayer() {
        accountPort.add(RiotAccount.builder().puuid(PUUID).gameName("Player").tagLine("NA1").build());
        summonerPort.putByPuuid(PLATFORM, PUUID,
                Summoner.builder().name("Player").summonerLevel(100).build());
    }

    @Test
    void returnsEmptyAnalytics_whenNoMatches() {
        givenPlayer();
        matchPort.putMatchIds(PUUID, List.of());

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 5);

        assertThat(result.getMatchCount()).isZero();
        assertThat(result.getSummonerName()).isEqualTo("Player");
        assertThat(result.getSummonerLevel()).isEqualTo(100L);
        assertThat(result.getWinRate()).isNull();
        assertThat(result.getAvgKda()).isNull();
    }

    @Test
    void computesPerfectKda_whenZeroDeaths() {
        givenPlayer();
        matchPort.putMatchIds(PUUID, List.of("NA1_1"));
        matchPort.putMatch("NA1_1", match(true, 5, 0, 3));

        PlayerMatchAnalytics result = analyticsService.getPlayerMatchAnalytics("Player#NA1", PLATFORM, REGION, 1);

        assertThat(result.getMatchCount()).isEqualTo(1);
        assertThat(result.getWins()).isEqualTo(1);
        assertThat(result.getWinRate()).isEqualTo("100.00%");
        assertThat(result.getAvgDeaths()).isEqualTo("0.00");
        // Zero deaths => perfect KDA == kills + assists == 8.
        assertThat(result.getAvgKda()).isEqualTo("8.00");
    }

    @Test
    void computesWinRateAndAverages_overMultipleMatches() {
        givenPlayer();
        matchPort.putMatchIds(PUUID, List.of("NA1_1", "NA1_2"));
        matchPort.putMatch("NA1_1", match(true, 10, 2, 5));
        matchPort.putMatch("NA1_2", match(false, 4, 6, 3));

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

- [ ] **Step 2: Run the analytics service test**

Run: `./gradlew test --tests "com.wkaiser.riotapimcpserver.analytics.application.AnalyticsServiceTest"`
Expected: PASS (3 tests).

- [ ] **Step 3: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/analytics/application/AnalyticsServiceTest.java
git commit -m "test: rewrite analytics service test on in-memory fakes with edge cases"
```

---

### Task 11: Cleanup — delete `CompilationVerificationTest` and the `@Disabled` integration tests

The Lombok-wiring proxy (`CompilationVerificationTest`) is now superseded by real WireMock parsing tests, and the four live-API `@Disabled` integration tests cannot run offline and are fully covered by the WireMock adapter tests (adapter HTTP behaviour) and the fake-backed service/tool tests. Per the spec ("remove all `@Disabled`; delete `CompilationVerificationTest`"), delete all five. Because these are the only `@Disabled` files, deletion satisfies "remove all `@Disabled`" while keeping the offline build green.

**Files:**
- Delete: `src/test/java/com/wkaiser/riotapimcpserver/CompilationVerificationTest.java`
- Delete: `src/test/java/com/wkaiser/riotapimcpserver/account/adapter/in/mcp/RiotAccountToolTest.java`
- Delete: `src/test/java/com/wkaiser/riotapimcpserver/analytics/adapter/in/mcp/AnalyticsToolTest.java`
- Delete: `src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot/SpectatorServiceIntegrationTest.java`
- Delete: `src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/in/mcp/LiveGameToolIntegrationTest.java`

**Interfaces:** none produced — this task only removes superseded tests.

> **Note on paths:** these are the post-Phase-1 locations. `RiotAccountToolTest` moved to `account/adapter/in/mcp/` (Phase 1 Task 4 Step 5), `AnalyticsToolTest` to `analytics/adapter/in/mcp/` (Task 8 Step 3), and the two spectator integration tests to `spectator/adapter/out/riot/` and `spectator/adapter/in/mcp/` (Task 7 Step 8).

- [ ] **Step 1: Confirm exactly these five files carry `@Disabled` or are the compilation proxy**

Run:

```bash
grep -rl '@Disabled' src/test && echo "---" && \
  ls src/test/java/com/wkaiser/riotapimcpserver/CompilationVerificationTest.java
```

Expected: the `@Disabled` matches are exactly the four integration tests listed above, and the `ls` prints the `CompilationVerificationTest.java` path. If any other file carries `@Disabled`, stop and reconcile before deleting.

- [ ] **Step 2: Delete the five superseded tests**

```bash
git rm src/test/java/com/wkaiser/riotapimcpserver/CompilationVerificationTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/account/adapter/in/mcp/RiotAccountToolTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/analytics/adapter/in/mcp/AnalyticsToolTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/out/riot/SpectatorServiceIntegrationTest.java \
       src/test/java/com/wkaiser/riotapimcpserver/spectator/adapter/in/mcp/LiveGameToolIntegrationTest.java
```

- [ ] **Step 3: Verify no `@Disabled` and no compilation-proxy test remain**

Run:

```bash
grep -rn '@Disabled' src || echo "no @Disabled remaining"
grep -rln 'CompilationVerificationTest' src || echo "no CompilationVerificationTest references"
```

Expected: `no @Disabled remaining` and `no CompilationVerificationTest references`.

- [ ] **Step 4: Confirm the whole suite is green offline with no API key**

Run (Git Bash, key explicitly unset):

```bash
RIOT_API_KEY= ./gradlew clean build
```

Expected: BUILD SUCCESSFUL. The executed test suite includes (all active, none skipped): `FixturesTest`, `RiotApiPropertiesTest`, `RiotApiClientTest`, `RiotAccountRiotAdapterTest`, `RiotSummonerAdapterTest`, `RiotMatchAdapterTest`, `RiotSpectatorAdapterTest`, `RiotAccountServiceTest`, `SummonerServiceTest`, `MatchServiceTest`, `SpectatorServiceTest`, `AnalyticsServiceTest`, and `LiveGameToolTest`.

- [ ] **Step 5: Confirm the test tree has no skipped tests**

Run:

```bash
grep -rn 'org.junit.jupiter.api.Disabled' src/test || echo "clean"
```

Expected: `clean`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "test: delete CompilationVerificationTest and disabled live-API integration tests"
```

---

## Self-Review

**Spec coverage (Phase 2 slice — Decision 2):**
- *WireMock outbound adapter tests, asserting request URL, `X-RIOT-TOKEN` header, JSON→DTO parsing, and error mapping* — Tasks 2 (account), 3 (summoner), 4 (match, incl. query params), 5 (spectator). ✓
- *Spectator `404 → null`; other `4xx/5xx → RiotApiException` with status preserved* — Task 5 Steps (404-not-in-game returns null; 500 propagates); status preservation also asserted in Tasks 2–4. ✓
- *Canned JSON fixtures in `src/test/resources/fixtures/`* — Task 1 (six fixtures + `Fixtures` loader, loaded from the classpath). ✓
- *Application-service tests use hand-written in-memory fakes implementing the port interfaces (replacing Mockito)* — Tasks 6 (`InMemoryRiotAccountPort`), 7 (`InMemorySummonerPort`), 8 (`InMemoryMatchPort`), 9 (`InMemorySpectatorPort`), each rewriting its service test off Mockito. ✓
- *`AnalyticsService` tested with fake account/summoner/match collaborators, covering zero games and zero-deaths KDA* — Task 10 wires the real services to the in-memory fakes and asserts both edges explicitly (`returnsEmptyAnalytics_whenNoMatches`, `computesPerfectKda_whenZeroDeaths`) plus a normal multi-match path. ✓
- *Remove all `@Disabled`; delete `CompilationVerificationTest`* — Task 11 deletes both, then greps to prove none remain. ✓
- *Dependency `org.wiremock:wiremock-standalone` already present* — reused from Phase 1 Task 2; no `build.gradle` change (Global Constraints). ✓
- *Success criterion: `./gradlew build` passes offline with no `RIOT_API_KEY`* — Task 11 Step 4 runs `RIOT_API_KEY= ./gradlew clean build`. ✓

**Placeholder scan:** none. Every created file (six fixtures, `Fixtures`, four adapter tests, four port fakes, one new + four rewritten service/analytics tests) shows complete content; every verification step shows an exact command and expected output. No "TBD", "similar to", or "add appropriate X".

**Type / name consistency with Phase 1:**
- Adapter class names and constructors match Phase 1 exactly: `RiotAccountRiotAdapter(RiotApiClient, RiotApiProperties)`, `RiotSummonerAdapter(RiotApiClient)`, `RiotMatchAdapter(RiotApiClient)`, `RiotSpectatorAdapter(RiotApiClient)`.
- Port interfaces and signatures match Phase 1 Tasks 4–7: `RiotAccountPort`, `SummonerPort(platform,…)`, `MatchPort(region, puuid, count, start, queue)`, `SpectatorPort` (nullable `getCurrentGameInfo`).
- Service constructors used by the fakes match Phase 1 `@RequiredArgsConstructor` field order: `RiotAccountService(RiotAccountPort)`, `SummonerService(SummonerPort)`, `MatchService(MatchPort)`, `SpectatorService(SpectatorPort)`, `AnalyticsService(RiotAccountService, SummonerService, MatchService)`.
- `RiotApiProperties` accessors used (`setApiKey`, `setBaseUrlOverride`, default `getRegion()==AMERICAS`) and `RiotApiClient(RiotApiProperties)` + `regional(...)`/`platform(...)` match Phase 1 Tasks 1–2; the `X-RIOT-TOKEN` header literal and `RiotApiException(message, statusCode)`/`getStatusCode()` match `shared/http/RiotApiClient` and `shared/exception/RiotApiException`.
- Endpoint paths match the Phase-1 adapters verbatim (account by-riot-id/by-puuid, summoner by-name/by-puuid/{id}, match by-puuid ids + {matchId}, spectator active-games/by-summoner + featured-games).
- Fixture JSON uses only declared DTO field names (verified against `RiotAccount`, `Summoner`, `Match`/`MatchInfo`/`MatchMetadata`/`Participant`, `CurrentGameInfo`/`CurrentGameParticipant`/`Observer`/`Perks`/`BannedChampion`/`FeaturedGames`); the non-`@JsonIgnoreProperties` match DTOs receive no unknown fields.
- `SpectatorTestFixtures` (reused in Task 9) is the Phase-1 Task-7 helper in package `com.wkaiser.riotapimcpserver.spectator`.

**Cross-phase note:** the test-only helper package `com.wkaiser.riotapimcpserver.testsupport` and the `InMemory*Port` fakes live under `src/test`; Phase 3's ArchUnit suite is expected to import main classes only (`DoNotIncludeTests`), so these do not participate in the dependency/naming rules.
