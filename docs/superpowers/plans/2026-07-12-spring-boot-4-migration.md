# Spring Boot 4.1 / Spring AI 2.0 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the project from Spring Boot 3.4.4 / Spring AI 1.0.0-M6 (both past their support window) to Spring Boot 4.1.0 / Spring AI 2.0.0 GA, keeping all four MCP tools working and the existing test suite green throughout.

**Architecture:** No architectural change — this is a dependency-version and annotation migration. The `@Tool` annotation used to expose methods via the MCP server is replaced with Spring AI 2.0's `@McpTool`/`@McpToolParam`, which the MCP server auto-configuration scans for instead. Everything else (services, DTOs, controllers) is untouched.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, Gradle 9.6.1, JUnit 5, Lombok.

## Global Constraints

- Spring Boot: `3.4.4` → `4.1.0` (plugin in `build.gradle`).
- Gradle wrapper: `8.13` → `9.6.1` — **required**, Spring Boot 4.x's Gradle plugin needs Gradle 8.14+ or 9.x, confirmed against the current 8.13 pin.
- Spring AI: `1.0.0-M6` → `2.0.0` (`springAiVersion` ext property in `build.gradle`).
- Java toolchain: unchanged, stays `21`.
- Dependency artifact renames (old artifact IDs stopped publishing past `1.0.0-M6` — confirmed via Maven Central):
  - `org.springframework.ai:spring-ai-anthropic-spring-boot-starter` → `org.springframework.ai:spring-ai-starter-model-anthropic`
  - `org.springframework.ai:spring-ai-mcp-server-webmvc-spring-boot-starter` → `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`
- Every method exposed as an MCP tool must move from `@Tool`/`org.springframework.ai.tool.annotation.Tool` to `@McpTool`/`org.springframework.ai.mcp.annotation.McpTool`, with each parameter annotated `@McpToolParam` (`org.springframework.ai.mcp.annotation.McpToolParam`). Methods left on the old `@Tool` annotation still compile but are silently dropped from MCP registration — verified empirically in a throwaway spike build (see below), not just from docs.
- `application.yml` needs `spring.ai.mcp.server.annotation-scanner.enabled: true` added — without it the annotation scanner does not run.
- Expected final state: application logs `Registered tools: 10` at startup (2 in `RiotAccountTool` + 3 in `SummonerTool` + 1 in `AnalyticsTool` + 4 in `LiveGameTool`). This exact log line and count is the primary correctness signal for this migration — it was observed directly in a spike run with only `RiotAccountTool` migrated (`Registered tools: 2`), confirming the mechanism works and that non-migrated classes contribute zero.
- The existing test suite (JUnit 5 unit tests with mocked Riot API responses; `@Disabled` `@SpringBootTest` integration tests) must stay green throughout. No test is added, removed, or re-enabled in this pass.
- `ANTHROPIC_API_KEY` and `RIOT_API_KEY` environment variables must be set in the shell before running `./gradlew bootRun` for the manual smoke test (the app now reads these from the environment, per an earlier fix in this branch — see `application.yml`). Dummy values are sufficient for the smoke test since it only checks tool registration at startup, not live API calls.

---

### Task 1: Bump Gradle wrapper, Spring Boot, and Spring AI versions

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yml`
- Modify: `README.md:5` (Spring Boot version badge)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a project that compiles against Spring Boot 4.1.0 / Spring AI 2.0.0, with the `spring.ai.mcp.server.annotation-scanner.enabled` property in place for Task 2–5 to depend on. Tool classes still use the old `@Tool` annotation at the end of this task — that's expected and gets fixed in Tasks 2–5.

- [ ] **Step 1: Bump the Gradle wrapper**

Edit `gradle/wrapper/gradle-wrapper.properties`, changing:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
```
to:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
```

- [ ] **Step 2: Bump Spring Boot plugin and Spring AI BOM version in `build.gradle`**

Change:
```gradle
plugins {
	id 'java'
	id 'org.springframework.boot' version '3.4.4'
	id 'io.spring.dependency-management' version '1.1.7'
}
```
to:
```gradle
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.0'
	id 'io.spring.dependency-management' version '1.1.7'
}
```

Change:
```gradle
ext {
	set('springAiVersion', "1.0.0-M6")
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'io.github.wimdeblauwe:htmx-spring-boot:5.1.0'
	implementation 'org.springframework.ai:spring-ai-anthropic-spring-boot-starter'
	implementation 'org.springframework.ai:spring-ai-mcp-server-webmvc-spring-boot-starter'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```
to:
```gradle
ext {
	set('springAiVersion', "2.0.0")
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'io.github.wimdeblauwe:htmx-spring-boot:5.1.0'
	implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'
	implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 3: Run `./gradlew compileJava` to verify dependency resolution and compilation succeed**

Run: `./gradlew compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`. (Confirmed in a throwaway spike build outside the repo — this step is not a leap of faith, just reproducing that result in the real tree.) The four tool classes still use `@Tool`, which still compiles fine against Spring AI 2.0 — that annotation isn't removed, just no longer picked up by the MCP server's auto-configuration.

- [ ] **Step 4: Add the MCP annotation scanner property to `application.yml`**

Change:
```yaml
    mcp:
      server:
        name: riot-api-mcp-server
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages
```
to:
```yaml
    mcp:
      server:
        name: riot-api-mcp-server
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages
        annotation-scanner:
          enabled: true
```

- [ ] **Step 5: Update the Spring Boot version badge in `README.md`**

Change line 5:
```markdown
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-green.svg)](https://spring.io/projects/spring-boot)
```
to:
```markdown
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-green.svg)](https://spring.io/projects/spring-boot)
```

- [ ] **Step 6: Run the existing test suite to confirm nothing else broke**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`, same tests passing/skipped as before this task (the `@Disabled` integration tests are skipped, not run).

- [ ] **Step 7: Commit**

```bash
git add gradle/wrapper/gradle-wrapper.properties build.gradle src/main/resources/application.yml README.md
git commit -m "Bump Gradle, Spring Boot, and Spring AI to current supported versions

Gradle 8.13 -> 9.6.1 (required for the Spring Boot 4.x Gradle plugin).
Spring Boot 3.4.4 -> 4.1.0, Spring AI 1.0.0-M6 -> 2.0.0. Dependency
artifact IDs updated to match Spring AI 2.0's starter naming. MCP tool
classes still use the old @Tool annotation at this point -- migrated
in the following tasks."
```

---

### Task 2: Migrate `RiotAccountTool` to `@McpTool`/`@McpToolParam`

**Files:**
- Modify: `src/main/java/com/wkaiser/riotapimcpserver/riot/account/tool/RiotAccountTool.java`

**Interfaces:**
- Consumes: `RiotAccountService.getAccountByRiotId(String, String)`, `RiotAccountService.getAccountByPuuid(String)` — unchanged, not touched by this task.
- Produces: `RiotAccountTool` now exposes 2 tools via `@McpTool`. After this task alone (before Tasks 3–5), a smoke test would show `Registered tools: 2`.

- [ ] **Step 1: Replace the `@Tool` import and annotations with `@McpTool`/`@McpToolParam`**

Replace the full contents of `RiotAccountTool.java` with:
```java
package com.wkaiser.riotapimcpserver.riot.account.tool;

import com.wkaiser.riotapimcpserver.riot.account.dto.RiotAccount;
import com.wkaiser.riotapimcpserver.riot.account.service.RiotAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing Riot account functionality.
 * Exposes methods that can be called by AI models via the MCP server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotAccountTool {

    private final RiotAccountService accountService;

    @McpTool(name = "get_riot_account_by_riot_id", description = "Get Riot account information by Riot ID (gameName#tagLine)")
    public RiotAccount getAccountByRiotId(
            @McpToolParam(description = "The player's in-game name", required = true) String gameName,
            @McpToolParam(description = "The player's tag line (e.g. NA1)", required = true) String tagLine) {
        log.info("MCP Tool - Getting account by Riot ID: {}#{}", gameName, tagLine);
        return accountService.getAccountByRiotId(gameName, tagLine);
    }

    @McpTool(name = "get_riot_account_by_puuid", description = "Get Riot account information by PUUID")
    public RiotAccount getAccountByPuuid(
            @McpToolParam(description = "The player's PUUID (encrypted universally unique ID)", required = true) String puuid) {
        log.info("MCP Tool - Getting account by PUUID: {}", puuid);
        return accountService.getAccountByPuuid(puuid);
    }
}
```

- [ ] **Step 2: Compile and run tests**

Run: `./gradlew compileJava test --console=plain`
Expected: `BUILD SUCCESSFUL`. `RiotAccountToolTest` is `@Disabled`, so it's skipped, not executed — its presence just confirms the class still compiles against `RiotAccountTool`'s public API (unchanged method signatures).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wkaiser/riotapimcpserver/riot/account/tool/RiotAccountTool.java
git commit -m "Migrate RiotAccountTool to @McpTool/@McpToolParam"
```

---

### Task 3: Migrate `SummonerTool` to `@McpTool`/`@McpToolParam`

**Files:**
- Modify: `src/main/java/com/wkaiser/riotapimcpserver/riot/lol/summoner/tool/SummonerTool.java`

**Interfaces:**
- Consumes: `SummonerService.getSummonerByName(RiotApiPlatformUri, String)`, `.getSummonerByPuuid(RiotApiPlatformUri, String)`, `.getSummonerById(RiotApiPlatformUri, String)` — unchanged.
- Produces: `SummonerTool` now exposes 3 tools via `@McpTool`. Combined with Task 2, a smoke test at this point would show `Registered tools: 5`.

- [ ] **Step 1: Replace the `@Tool` import and annotations with `@McpTool`/`@McpToolParam`**

Replace the full contents of `SummonerTool.java` with:
```java
package com.wkaiser.riotapimcpserver.riot.lol.summoner.tool;

import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing League of Legends summoner functionality.
 * Exposes methods that can be called by AI models via the MCP server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerTool {

    private final SummonerService summonerService;

    @McpTool(name = "get_lol_summoner_by_name", description = "Get League of Legends summoner information by summoner name")
    public Summoner getSummonerByName(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The summoner's name", required = true) String summonerName) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by name: {} on platform: {}", summonerName, platform);
        return summonerService.getSummonerByName(platform, summonerName);
    }

    @McpTool(name = "get_lol_summoner_by_puuid", description = "Get League of Legends summoner information by PUUID")
    public Summoner getSummonerByPuuid(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player's PUUID (encrypted universally unique ID)", required = true) String puuid) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by PUUID: {} on platform: {}", puuid, platform);
        return summonerService.getSummonerByPuuid(platform, puuid);
    }

    @McpTool(name = "get_lol_summoner_by_id", description = "Get League of Legends summoner information by summoner ID")
    public Summoner getSummonerById(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The encrypted summoner ID", required = true) String summonerId) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by ID: {} on platform: {}", summonerId, platform);
        return summonerService.getSummonerById(platform, summonerId);
    }
}
```

- [ ] **Step 2: Compile and run tests**

Run: `./gradlew compileJava test --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wkaiser/riotapimcpserver/riot/lol/summoner/tool/SummonerTool.java
git commit -m "Migrate SummonerTool to @McpTool/@McpToolParam"
```

---

### Task 4: Migrate `AnalyticsTool` to `@McpTool`/`@McpToolParam`

**Files:**
- Modify: `src/main/java/com/wkaiser/riotapimcpserver/riot/lol/analytics/tool/AnalyticsTool.java`

**Interfaces:**
- Consumes: `AnalyticsService.getPlayerMatchAnalytics(String, RiotApiPlatformUri, RiotApiRegionUri, int)` — unchanged.
- Produces: `AnalyticsTool` now exposes 1 tool via `@McpTool`. Combined with Tasks 2–3, a smoke test at this point would show `Registered tools: 6`.

- [ ] **Step 1: Replace the `@Tool` import and annotation with `@McpTool`/`@McpToolParam`**

`matchCount` is nullable in the existing code (`Integer matchCount`, with `matchCount == null ? 10 : ...` normalization), so its `@McpToolParam` is marked `required = false` — everything else is `required = true`.

Replace the full contents of `AnalyticsTool.java` with:
```java
package com.wkaiser.riotapimcpserver.riot.lol.analytics.tool;

import com.wkaiser.riotapimcpserver.riot.lol.analytics.dto.PlayerMatchAnalytics;
import com.wkaiser.riotapimcpserver.riot.lol.analytics.service.AnalyticsService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing League of Legends analytics functionality.
 * Exposes methods that can be called by AI models via the MCP server.
 * This tool provides advanced analytics by combining data from multiple Riot API endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsTool {

    private final AnalyticsService analyticsService;

    @McpTool(name = "get_lol_player_match_analytics", description = "Get detailed analytics of a League of Legends player's recent matches")
    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            @McpToolParam(description = "The player's Riot ID (gameName#tagLine)", required = true) String riotId,
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE", required = true) String regionStr,
            @McpToolParam(description = "Number of recent matches to analyze, 1-100, defaults to 10", required = false) Integer matchCount
    ) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr);

        // Validate and normalize match count
        int count = matchCount == null ? 10 : Math.min(100, Math.max(1, matchCount));

        log.info("MCP Tool - Generating match analytics for player: {} on platform: {}", riotId, platform);
        return analyticsService.getPlayerMatchAnalytics(riotId, platform, region, count);
    }
}
```

- [ ] **Step 2: Compile and run tests**

Run: `./gradlew compileJava test --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wkaiser/riotapimcpserver/riot/lol/analytics/tool/AnalyticsTool.java
git commit -m "Migrate AnalyticsTool to @McpTool/@McpToolParam"
```

---

### Task 5: Migrate `LiveGameTool` to `@McpTool`/`@McpToolParam`

**Files:**
- Modify: `src/main/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/tool/LiveGameTool.java`

**Interfaces:**
- Consumes: `SpectatorService.getCurrentGameInfo(RiotApiPlatformUri, String)`, `.getFeaturedGames(RiotApiPlatformUri)`, `SummonerService.getSummonerByName(RiotApiPlatformUri, String)` — unchanged.
- Produces: `LiveGameTool` now exposes 4 tools via `@McpTool`. Combined with Tasks 2–4, a smoke test at this point should show `Registered tools: 10` — the target state for this migration.

- [ ] **Step 1: Replace the `@Tool` import and annotations with `@McpTool`/`@McpToolParam`**

`isSummonerInGame` calls `getCurrentGameBySummonerName` directly (a plain Java method call, not an MCP protocol call) — that internal call is untouched by the annotation swap.

Replace the full contents of `LiveGameTool.java` with:
```java
package com.wkaiser.riotapimcpserver.riot.lol.spectator.tool;

import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.CurrentGameInfo;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.dto.FeaturedGames;
import com.wkaiser.riotapimcpserver.riot.lol.spectator.service.SpectatorService;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.dto.Summoner;
import com.wkaiser.riotapimcpserver.riot.lol.summoner.service.SummonerService;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP server tool for accessing League of Legends live game functionality.
 * Exposes methods that can be called by AI models via the MCP server for
 * retrieving current game information and featured games.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveGameTool {

    private final SpectatorService spectatorService;
    private final SummonerService summonerService;

    @McpTool(name = "get_current_game_by_summoner_name",
          description = "Get current live game information for a summoner by name. Returns live game details if summoner is in game, null if not in game.")
    public CurrentGameInfo getCurrentGameBySummonerName(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The summoner's name", required = true) String summonerName) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting current game by summoner name: {} on platform: {}", summonerName, platform);

        // Get summoner information to extract encrypted summoner ID
        Summoner summoner = summonerService.getSummonerByName(platform, summonerName);
        String encryptedSummonerId = summoner.getId();

        // Get current game info using encrypted summoner ID
        return spectatorService.getCurrentGameInfo(platform, encryptedSummonerId);
    }

    @McpTool(name = "get_current_game_by_summoner_id",
          description = "Get current live game information for a summoner by encrypted summoner ID. Returns live game details if summoner is in game, null if not in game.")
    public CurrentGameInfo getCurrentGameBySummonerId(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The encrypted summoner ID", required = true) String encryptedSummonerId) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting current game by summoner ID: {} on platform: {}", encryptedSummonerId, platform);

        return spectatorService.getCurrentGameInfo(platform, encryptedSummonerId);
    }

    @McpTool(name = "get_featured_games",
          description = "Get list of current featured games on a platform. Featured games are high-profile matches selected by Riot.")
    public FeaturedGames getFeaturedGames(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting featured games for platform: {}", platform);

        return spectatorService.getFeaturedGames(platform);
    }

    @McpTool(name = "check_if_summoner_in_game",
          description = "Check if a summoner is currently in a live game. Returns true if in game, false if not in game.")
    public boolean isSummonerInGame(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The summoner's name", required = true) String summonerName) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Checking if summoner is in game: {} on platform: {}", summonerName, platform);

        CurrentGameInfo currentGame = getCurrentGameBySummonerName(platformStr, summonerName);
        return currentGame != null;
    }
}
```

- [ ] **Step 2: Compile and run tests**

Run: `./gradlew compileJava test --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/tool/LiveGameTool.java
git commit -m "Migrate LiveGameTool to @McpTool/@McpToolParam"
```

---

### Task 6: Full verification — build, MCP tool discovery smoke test, Dependabot re-check

**Files:** none modified — this is a validation-only task.

**Interfaces:**
- Consumes: the fully migrated application from Tasks 1–5.
- Produces: confirmation that the migration is complete and correct; no code output.

- [ ] **Step 1: Full build**

Run: `./gradlew build --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Manual MCP tool discovery smoke test**

Set dummy credentials (real ones work too, but aren't required for this check) and start the app:
```bash
export ANTHROPIC_API_KEY="sk-ant-smoke-test"
export RIOT_API_KEY="RGAPI-smoke-test"
./gradlew bootRun --console=plain
```
Expected in the startup log: a line reading `Registered tools: 10` from `McpServerAutoConfiguration`, and `Started RiotApiMcpServerApplication` with no exceptions. Stop the app afterward (Ctrl+C, or `./gradlew --stop` in another shell).

If the count isn't exactly 10, check which tool class is under-reporting — most likely cause is a leftover `@Tool` import that didn't get swapped to `@McpTool` in one of the four files from Tasks 2–5.

- [ ] **Step 3: Re-check Dependabot alerts**

Run: `gh api repos/:owner/:repo/dependabot/alerts --paginate --jq '.[] | select(.state=="open") | .dependency.package.name' | sort -u`
Expected: significantly fewer distinct packages than the ~10 seen before this migration (Tomcat, Logback, Jackson, Spring Framework, commons-compress, commons-lang3, httpclient5, artemis, assertj, spring-boot itself). Any alerts still open after this should be spot-checked — either no fixed version exists yet, or they're unrelated to this dependency tree.

- [ ] **Step 4: Final commit (if anything changed during verification)**

If Steps 1–3 required any fixes not already committed in Tasks 1–5, commit them now with a message describing what was found and fixed. If nothing changed, no commit needed — this task is verification-only.
