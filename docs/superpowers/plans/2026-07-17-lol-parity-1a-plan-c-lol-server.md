# LoL Parity 1a — Plan C: LoL Server (correctness, League exemplar, contract sweep)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring `lol-mcp-server` to a correct, conventional base against the now-hardened libraries — delete the dead by-name/`encryptedSummonerId` tools, move spectator to Spectator-V5 (PUUID-keyed), add **League** as the one exemplar context every 1b context copies, and sweep every tool to `<game>_<context>_<action>` with a single `player` param — taking ten tools to seven.

**Architecture:** Phases 4–6 of the [1a spec](../specs/2026-07-15-lol-parity-foundation-design.md). Server production code only — no library change (that is the falsifiable 1a success criterion). Player-keyed contexts resolve the `player` param in their **application service** via `PlayerIdentityResolver` (Plan B) — the handoff-contract shape 1b inherits — so tools stay thin pass-throughs and services depend on their own port plus the resolver, never on a `RestClient` or another context's service. The account tool disambiguates `#` locally because it needs account **data** both ways and must not round-trip through the resolver. League is built directly on the final naming convention ("born correct"), so Phase 6 only renames the four pre-existing tools; League is the first LoL context to depend on the resolver, exercising the open half of Plan B's ArchUnit split for real.

**Tech Stack:** Gradle 9.6.1 (Groovy DSL, `buildSrc` convention plugin), Spring Boot 4.1.0, Spring AI 2.0.0 (`@McpTool`), Java 21, JUnit 5, AssertJ, Mockito (already on the test classpath), WireMock 3.9.2, ArchUnit 1.3.0, Spotless (palantir-java-format), JaCoCo.

## Global Constraints

- **The suite runs offline with no Riot API key.** WireMock for outbound adapters; hand-written in-memory port fakes for services; Mockito for the external `PlayerIdentityResolver` and for service-mocked tool tests. Never add a test needing a live key or network.
- **No library modification.** Only `lol-mcp-server` production code and its tests change (plus `docs/`). If a task seems to need an edit in `riot-api-core` or `riot-account-core`, **stop and record it as a finding** — 1a's success criterion is that servers extend without touching either library.
- **Resolution lives in the service, not the tool.** Every player-keyed service depends on `PlayerIdentityResolver` and calls `resolvePuuid(player)` itself. The only exception is the account tool (see Task 6).
- **`PlayerIdentityResolver` is mocked in LoL tests, never constructed.** Its constructor takes a Caffeine `Ticker`, and Caffeine is an `implementation` dependency of `riot-account-core` — it is **not** on `lol-mcp-server`'s test classpath. `mock(PlayerIdentityResolver.class)` bypasses the constructor and needs no Caffeine. Do not add Caffeine to this module to construct a real one.
- **Riot endpoint paths are verified against the live developer portal**, never assumed from Context7 or model memory (a standing 1a constraint). Each adapter task has an explicit verify-first step; if the portal disagrees with a path below, change the stub URL and the adapter together and note it in the commit.
- **`McpToolInventoryTest` is the contract gate and it changes in this plan.** Update `EXPECTED_TOOL_NAMES` (and, in Task 5, the scanned-class list) in the same task that changes a tool, so every commit is green.
- **Region vs platform routing:** summoner, spectator, and league are **platform**-routed (`riotApiClient.platform(...)`); account and match are **region**-routed. Do not mix them (see `gotchas.md`).
- **Spectator `404` means "not in a game" → `null`**, mapped in the adapter, not `RiotApiClient` (see `gotchas.md`). This survives the v4→v5 move unchanged.
- **DTO convention:** `@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)`. Prefer separate top-level DTOs over nested `@Builder` classes; a nested `@Builder` class needs all four annotations too (see `gotchas.md`).
- **`lol-mcp-server` stays version `0.1.0`.** Entries accumulate under the existing `## [0.1.0] - unreleased` heading — `verifyRelease` matches the heading, not a date. The contract break is logged here when Task 10 lands it.
- **Green at every commit.** `./gradlew build` runs tests + ArchUnit + JaCoCo + `verifyRelease` + Spotless check. **Run `./gradlew spotlessApply` before committing Java changes.**
- **Hydrate before starting:** `docs/knowledge/README.md`, `docs/knowledge/roadmap.md`, `docs/knowledge/gotchas.md`, the patterns `add-a-bounded-context.md` / `add-an-mcp-tool.md` / `add-an-adapter-test.md`, and ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0008.

## Tool inventory across this plan

The gate (`McpToolInventoryTest`) asserts this exact set at each phase boundary:

| After Phase 4 (Tasks 1–2), 6 tools | After Phase 5 (Task 5), 8 tools | After Phase 6 (Tasks 6–10), 7 tools |
|---|---|---|
| `get_riot_account_by_riot_id` | `get_riot_account_by_riot_id` | `lol_account_by_player` |
| `get_riot_account_by_puuid` | `get_riot_account_by_puuid` | `lol_summoner_by_player` |
| `get_lol_summoner_by_puuid` | `get_lol_summoner_by_puuid` | `lol_spectator_current_game_by_player` |
| `get_current_game_by_summoner_id` | `get_current_game_by_summoner_id` | `lol_spectator_featured_games` |
| `get_featured_games` | `get_featured_games` | `lol_analytics_player_matches` |
| `get_lol_player_match_analytics` | `get_lol_player_match_analytics` | `lol_league_entries_by_player` |
| | `lol_league_entries_by_player` | `lol_league_apex_by_tier` |
| | `lol_league_apex_by_tier` | |

Removed in Phase 4: `get_lol_summoner_by_name`, `get_lol_summoner_by_id`, `get_current_game_by_summoner_name`, `check_if_summoner_in_game`. Collapsed in Phase 6: the two `get_riot_account_by_*` tools → `lol_account_by_player`.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `.../summoner/adapter/in/mcp/SummonerTool.java` | Modify — drop by-name/by-id tools (T1); rename remaining → `lol_summoner_by_player`, take `player` (T7) | 1, 7 |
| `.../summoner/application/SummonerService.java` | Modify — drop by-name/by-id (T1); inject resolver, add `getSummonerByPlayer` (T7) | 1, 7 |
| `.../summoner/application/port/SummonerPort.java` | Modify — drop by-name/by-id methods | 1 |
| `.../summoner/adapter/out/riot/RiotSummonerAdapter.java` | Modify — drop by-name/by-id methods | 1 |
| `.../summoner/application/InMemorySummonerPort.java` (test) | Modify — drop by-name/by-id maps | 1 |
| `.../summoner/application/SummonerServiceTest.java` (test) | Modify — drop by-name/by-id tests (T1); mock resolver, add by-player (T7) | 1, 7 |
| `.../summoner/adapter/out/riot/RiotSummonerAdapterTest.java` (test) | Modify — drop by-name/by-id tests, retarget error test to by-puuid | 1 |
| `.../spectator/adapter/out/riot/RiotSpectatorAdapter.java` | Modify — v4 → v5 paths, param `puuid` | 2 |
| `.../spectator/application/SpectatorService.java` | Modify — param `puuid` (T2); inject resolver, add `getCurrentGameByPlayer` (T8) | 2, 8 |
| `.../spectator/application/port/SpectatorPort.java` | Modify — param `puuid` | 2 |
| `.../spectator/adapter/in/mcp/LiveGameTool.java` | Modify — drop by-name/in-game tools + SummonerService dep, current-game takes `puuid` (T2); rename → `lol_spectator_*`, take `player` (T8) | 2, 8 |
| `.../spectator/application/InMemorySpectatorPort.java` (test) | Modify — rename param to `puuid` | 2 |
| `.../spectator/adapter/out/riot/RiotSpectatorAdapterTest.java` (test) | Modify — v5 URLs | 2 |
| `.../spectator/application/SpectatorServiceTest.java` (test) | Modify — param rename (T2); mock resolver, by-player (T8) | 2, 8 |
| `.../spectator/adapter/in/mcp/LiveGameToolTest.java` (test) | Modify — drop by-name/in-game tests, drop SummonerService mock (T2); by-player (T8) | 2, 8 |
| `src/test/resources/fixtures/current-game.json` (test) | Modify — v5-shaped participant (PUUID-keyed, no `summonerId`) | 2 |
| `.../league/domain/LeagueEntry.java` | Create — a ranked-queue entry (League-V4 by-puuid) | 3 |
| `.../league/domain/LeagueList.java` | Create — an apex league | 3 |
| `.../league/domain/LeagueItem.java` | Create — one player within an apex league | 3 |
| `.../league/domain/ApexTier.java` | Create — CHALLENGER/GRANDMASTER/MASTER + path segment | 3 |
| `.../league/application/port/LeaguePort.java` | Create — outbound port | 3 |
| `.../league/adapter/out/riot/RiotLeagueAdapter.java` | Create — League-V4 adapter | 3 |
| `.../league/adapter/out/riot/RiotLeagueAdapterTest.java` (test) | Create — WireMock adapter test | 3 |
| `src/test/resources/fixtures/league-entries.json` (test) | Create — by-puuid sample | 3 |
| `src/test/resources/fixtures/league-apex.json` (test) | Create — apex sample | 3 |
| `.../league/application/LeagueService.java` | Create — port + resolver | 4 |
| `.../league/application/InMemoryLeaguePort.java` (test) | Create — hand fake | 4 |
| `.../league/application/LeagueServiceTest.java` (test) | Create — port-fake + mocked resolver | 4 |
| `.../league/adapter/in/mcp/LeagueTool.java` | Create — the two league tools, born on the convention | 5 |
| `.../league/adapter/in/mcp/LeagueToolTest.java` (test) | Create — service-mocked tool test | 5 |
| `.../account/adapter/in/mcp/RiotAccountTool.java` | Modify — collapse to `lol_account_by_player` | 6 |
| `.../account/adapter/in/mcp/RiotAccountToolTest.java` (test) | Create — both `player` forms | 6 |
| `.../analytics/adapter/in/mcp/AnalyticsTool.java` | Modify — rename → `lol_analytics_player_matches`, `player` param | 9 |
| `.../analytics/application/AnalyticsService.java` | Modify — resolver instead of manual split + account lookup | 9 |
| `.../analytics/application/AnalyticsServiceTest.java` (test) | Modify — mock resolver, drop account port | 9 |
| `.../McpToolInventoryTest.java` (test) | Modify — expected set (T1, T2, T5, T6, T7, T8, T9) + scanned classes (T5) + javadoc (T10) | 1,2,5,6,7,8,9,10 |
| `lol-mcp-server/README.md`, `ARCHITECTURE.md` | Not in scope — Plan D (Phase 7) owns per-module docs | — |
| `lol-mcp-server/CHANGELOG.md` | Modify — the contract break + correctness under `## [0.1.0]` | 10 |
| `docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md` | Create | 10 |
| `docs/knowledge/README.md` | Modify — link ADR-0009 | 10 |
| `docs/knowledge/patterns/add-a-bounded-context.md` | Modify — name League the reference implementation; add the handoff contract | 10 |
| `docs/knowledge/roadmap.md` | Modify — mark Plan C done in the 1a progress note | 10 |

---

## Phase 4 — LoL correctness

### Task 1: Summoner — drop the dead by-name and by-id paths

Riot decommissioned Riot-ID-era name lookup, so `get_lol_summoner_by_name` cannot resolve; `get_lol_summoner_by_id` is keyed by `encryptedSummonerId`, which Riot is stripping from responses. Both are dead, not deprecated — deleting beats deprecating a tool that cannot work. Only `getSummonerByPuuid` survives (and it is what `AnalyticsService` already depends on).

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner/adapter/in/mcp/SummonerTool.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner/application/SummonerService.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner/application/port/SummonerPort.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner/adapter/out/riot/RiotSummonerAdapter.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/summoner/application/InMemorySummonerPort.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/summoner/application/SummonerServiceTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/summoner/adapter/out/riot/RiotSummonerAdapterTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `SummonerPort` and `SummonerService` expose **only** `getSummonerByPuuid(RiotApiPlatformUri, String) -> Summoner`. `AnalyticsService.getPlayerMatchAnalytics` (unchanged this task) still compiles against it.

- [ ] **Step 1: Update the inventory gate first (it will fail — that is the red)**

In `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`, remove `"get_lol_summoner_by_name"` and `"get_lol_summoner_by_id"` from `EXPECTED_TOOL_NAMES`, and soften the class javadoc's "frozen … same 10 tools" wording to reflect that Plan C intentionally changes the surface:

```java
/**
 * Guards the public MCP contract. Sub-project 1a Plan C changes this surface deliberately —
 * dead tools are removed (Phase 4), League is added (Phase 5), and every tool is renamed to
 * {@code <game>_<context>_<action>} with a single {@code player} param (Phase 6). This test is
 * updated in lockstep with each of those changes, so a failure means a tool changed without the
 * contract being updated to match.
 */
```

```java
    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "get_riot_account_by_riot_id",
            "get_riot_account_by_puuid",
            "get_lol_summoner_by_puuid",
            "get_current_game_by_summoner_name",
            "get_current_game_by_summoner_id",
            "get_featured_games",
            "check_if_summoner_in_game",
            "get_lol_player_match_analytics");
```

- [ ] **Step 2: Run the gate — it must fail (tools still declare the removed names)**

```bash
./gradlew :lol-mcp-server:test --tests '*McpToolInventoryTest*'
```

Expected: FAIL — actual set still contains `get_lol_summoner_by_name` and `get_lol_summoner_by_id`.

- [ ] **Step 3: Remove the two tool methods**

Replace `SummonerTool.java` with the by-puuid-only version:

```java
package com.muddl.riot.lol.summoner.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.application.SummonerService;
import com.muddl.riot.lol.summoner.domain.Summoner;
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

    @McpTool(name = "get_lol_summoner_by_puuid", description = "Get League of Legends summoner information by PUUID")
    public Summoner getSummonerByPuuid(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player's PUUID (encrypted universally unique ID)", required = true)
                    String puuid) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner by PUUID: {} on platform: {}", puuid, platform);
        return summonerService.getSummonerByPuuid(platform, puuid);
    }
}
```

- [ ] **Step 4: Remove the service, port, and adapter methods**

In `SummonerService.java`, delete `getSummonerByName` and `getSummonerById`, leaving only:

```java
    public Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching summoner for PUUID: {} on platform: {}", puuid, platform);
        return summonerPort.getSummonerByPuuid(platform, puuid);
    }
```

In `SummonerPort.java`, leave only:

```java
public interface SummonerPort {

    Summoner getSummonerByPuuid(RiotApiPlatformUri platform, String puuid);
}
```

In `RiotSummonerAdapter.java`, delete `getSummonerByName` and `getSummonerById` (and their `by-name` / `{summonerId}` URIs), leaving only the `getSummonerByPuuid` override.

- [ ] **Step 5: Trim the test doubles and tests**

In `InMemorySummonerPort.java`, remove `byName`, `byId`, `putByName`, `putById`, `getSummonerByName`, `getSummonerById` — keep `byPuuid`, `putByPuuid`, `getSummonerByPuuid`, and `key`.

In `SummonerServiceTest.java`, delete `getSummonerByName_returnsStoredSummoner`, `getSummonerById_returnsStoredSummoner`, and `getSummonerByName_returnsNull_whenUnknown`; keep `getSummonerByPuuid_returnsStoredSummoner` and add an unknown-puuid case:

```java
    @Test
    void getSummonerByPuuid_returnsNull_whenUnknown() {
        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "ghost-puuid")).isNull();
    }
```

In `RiotSummonerAdapterTest.java`, delete `getSummonerByName_parsesBody_andSendsApiKeyHeader` and `getSummonerById_hitsExpectedUrl`; keep `getSummonerByPuuid_hitsExpectedUrl`; retarget the error test to the by-puuid path:

```java
    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo("/lol/summoner/v4/summoners/by-puuid/ghost-puuid"))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> adapter.getSummonerByPuuid(PLATFORM, "ghost-puuid"))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(403);
    }
```

- [ ] **Step 6: Build the module — gate passes, everything green**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. `McpToolInventoryTest` now matches (two summoner names gone). `AnalyticsService` still compiles (`getSummonerByPuuid` retained). ArchUnit unaffected.

- [ ] **Step 7: Commit**

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/summoner \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "feat(lol)!: drop dead summoner-by-name and by-id tools

Riot decommissioned Riot-ID-era name lookup, so get_lol_summoner_by_name
cannot resolve; get_lol_summoner_by_id is keyed by encryptedSummonerId, which
Riot is stripping. Both are dead, not deprecated. Only getSummonerByPuuid
survives — the path AnalyticsService already uses. Inventory gate updated."
```

---

### Task 2: Spectator — Spectator-V4 → V5 (PUUID-keyed), drop dead spectator tools

Spectator-V5 keys the active-game lookup by **PUUID** instead of `encryptedSummonerId`, matching the PUUID migration. `get_current_game_by_summoner_name` routed through the now-dead by-name summoner lookup, and `check_if_summoner_in_game` routed through it too and is redundant — a `null` current-game already answers "in a game?". Both go, which also removes `LiveGameTool`'s only dependency on `SummonerService`. `get_current_game_by_summoner_id` keeps its name this phase (renames are Phase 6) but its parameter becomes a PUUID; the `404 → null` rule stays in the adapter.

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator/adapter/out/riot/RiotSpectatorAdapter.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator/application/port/SpectatorPort.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator/application/SpectatorService.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator/adapter/in/mcp/LiveGameTool.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator/application/InMemorySpectatorPort.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator/adapter/out/riot/RiotSpectatorAdapterTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator/application/SpectatorServiceTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator/adapter/in/mcp/LiveGameToolTest.java`
- Modify: `lol-mcp-server/src/test/resources/fixtures/current-game.json`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `SpectatorPort` / `SpectatorService` expose `getCurrentGameInfo(RiotApiPlatformUri, String puuid) -> CurrentGameInfo` (`null` when not in a game) and `getFeaturedGames(RiotApiPlatformUri) -> FeaturedGames`. `LiveGameTool` depends only on `SpectatorService`.

- [ ] **Step 1: Verify the V5 paths against the live Riot developer portal**

Confirm on the portal (`developer.riotgames.com`, Spectator-V5) before coding:
- active game: `GET /lol/spectator/v5/active-games/by-summoner/{encryptedPUUID}` — note the segment is still literally `by-summoner`, but the path variable is a **PUUID**;
- featured games: `GET /lol/spectator/v5/featured-games`.

If either differs, use the portal's path and adjust both the adapter and the stub URLs below to match.

- [ ] **Step 2: Update the inventory gate first (red)**

In `McpToolInventoryTest.java`, remove `"get_current_game_by_summoner_name"` and `"check_if_summoner_in_game"` from `EXPECTED_TOOL_NAMES`. The set is now the six of the "After Phase 4" column.

```bash
./gradlew :lol-mcp-server:test --tests '*McpToolInventoryTest*'
```

Expected: FAIL — `LiveGameTool` still declares the two removed names.

- [ ] **Step 3: Move the adapter to V5 and rename the param to `puuid`**

Replace `RiotSpectatorAdapter.java`'s two URIs and the parameter name:

```java
    @Override
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String puuid) {
        try {
            return riotApiClient
                    .platform(platform)
                    .get()
                    .uri("/lol/spectator/v5/active-games/by-summoner/{puuid}", puuid)
                    .retrieve()
                    .body(CurrentGameInfo.class);
        } catch (RiotApiException e) {
            if (e.getStatusCode() == NOT_FOUND) {
                log.debug("Player {} is not currently in a game (404)", puuid);
                return null;
            }
            throw e;
        }
    }

    @Override
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/spectator/v5/featured-games")
                .retrieve()
                .body(FeaturedGames.class);
    }
```

Update the class javadoc: `/** Riot Spectator-V5 API adapter. Spectator endpoints are platform-routed and keyed by PUUID. */`.

- [ ] **Step 4: Rename the port and service parameter to `puuid`**

In `SpectatorPort.java`:

```java
    /** Returns the current game, or {@code null} if the player is not in a game. */
    CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String puuid);
```

In `SpectatorService.java`, rename the parameter and its log line:

```java
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching current game info for PUUID: {} on platform: {}", puuid, platform);
        return spectatorPort.getCurrentGameInfo(platform, puuid);
    }
```

- [ ] **Step 5: Reduce `LiveGameTool` to two tools, drop the SummonerService dependency**

Replace `LiveGameTool.java` with:

```java
package com.muddl.riot.lol.spectator.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.spectator.application.SpectatorService;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.FeaturedGames;
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

    @McpTool(
            name = "get_current_game_by_summoner_id",
            description =
                    "Get current live game information for a player by PUUID. Returns live game details if the player is in a game, null if not.")
    public CurrentGameInfo getCurrentGameBySummonerId(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player's PUUID", required = true) String puuid) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting current game by PUUID: {} on platform: {}", puuid, platform);
        return spectatorService.getCurrentGameInfo(platform, puuid);
    }

    @McpTool(
            name = "get_featured_games",
            description =
                    "Get list of current featured games on a platform. Featured games are high-profile matches selected by Riot.")
    public FeaturedGames getFeaturedGames(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting featured games for platform: {}", platform);
        return spectatorService.getFeaturedGames(platform);
    }
}
```

> Note: after this task the ArchUnit `contexts_do_not_depend_on_each_other` exception `spectator → summoner` is unused (spectator no longer depends on summoner). Leaving an unused `ignoreDependency` is harmless; removing it is Plan D's arch-audit job (Phase 7). Do **not** remove it here.

- [ ] **Step 6: Update the spectator test doubles, fixture, and tests**

In `InMemorySpectatorPort.java`, rename the map and params from `encryptedSummonerId` to `puuid` for readability (behaviour identical):

```java
    private final Map<String, CurrentGameInfo> gamesByPuuid = new HashMap<>();
    private final Map<RiotApiPlatformUri, FeaturedGames> featuredByPlatform = new HashMap<>();

    public InMemorySpectatorPort putGame(String puuid, CurrentGameInfo game) {
        gamesByPuuid.put(puuid, game);
        return this;
    }

    public InMemorySpectatorPort putFeatured(RiotApiPlatformUri platform, FeaturedGames featured) {
        featuredByPlatform.put(platform, featured);
        return this;
    }

    @Override
    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String puuid) {
        return gamesByPuuid.get(puuid);
    }

    @Override
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        return featuredByPlatform.get(platform);
    }
```

Reshape `src/test/resources/fixtures/current-game.json` to the V5 participant shape (PUUID present, no `summonerId`/`summonerName`):

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
      "puuid": "test-puuid-abc123",
      "teamId": 100,
      "bot": false,
      "spell1Id": 4,
      "spell2Id": 7,
      "profileIconId": 4567,
      "riotId": "Bjergsen#NA1",
      "perks": { "perkIds": [8112, 8126], "perkStyle": 8100, "perkSubStyle": 8200 }
    }
  ]
}
```

In `RiotSpectatorAdapterTest.java`, key the constant on a PUUID and point both stubs at V5:

```java
    private static final String PUUID = "test-puuid-abc123";
    private static final String ACTIVE_GAME_URL = "/lol/spectator/v5/active-games/by-summoner/" + PUUID;
```

Replace the featured-games stub URL with `"/lol/spectator/v5/featured-games"` (in both `stubFor` and `verify`), pass `PUUID` to `adapter.getCurrentGameInfo(PLATFORM, PUUID)` in all three current-game tests, and update the participant assertion (the V5 fixture has no `summonerName`) to:

```java
        assertThat(game.getParticipants().get(0).getPuuid()).isEqualTo("test-puuid-abc123");
```

In `SpectatorServiceTest.java`, rename the stored key to a PUUID for readability:

```java
    @Test
    void getCurrentGameInfo_returnsStoredGame() {
        CurrentGameInfo game = SpectatorTestFixtures.createSampleCurrentGameInfo();
        spectatorPort.putGame("player-in-game-puuid", game);

        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "player-in-game-puuid"))
                .isSameAs(game);
    }

    @Test
    void getCurrentGameInfo_returnsNull_whenPlayerNotInGame() {
        assertThat(spectatorService.getCurrentGameInfo(PLATFORM, "player-not-in-game-puuid"))
                .isNull();
    }
```

In `LiveGameToolTest.java`, remove everything tied to the deleted flows: the `SummonerService` `@Mock`, the `Summoner` imports/helper, the `TEST_SUMMONER_NAME` constant, and the `getCurrentGameBySummonerName_*` and `isSummonerInGame_*` tests. Rewrite the remaining tests to drive the PUUID path directly. The class becomes:

```java
package com.muddl.riot.lol.spectator.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.exception.RiotApiException;
import com.muddl.riot.lol.spectator.SpectatorTestFixtures;
import com.muddl.riot.lol.spectator.application.SpectatorService;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.FeaturedGames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for LiveGameTool with a mocked SpectatorService (no HTTP). */
@ExtendWith(MockitoExtension.class)
class LiveGameToolTest {

    @Mock
    private SpectatorService mockSpectatorService;

    @InjectMocks
    private LiveGameTool liveGameTool;

    private static final String TEST_PLATFORM_STRING = "NA1";
    private static final RiotApiPlatformUri TEST_PLATFORM = RiotApiPlatformUri.NA1;
    private static final String TEST_PUUID = "test-puuid-abc123";

    @Test
    void getCurrentGameBySummonerId_inGame_returnsCurrentGameInfo() {
        CurrentGameInfo expected = SpectatorTestFixtures.createSampleCurrentGameInfo();
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID)).thenReturn(expected);

        CurrentGameInfo result = liveGameTool.getCurrentGameBySummonerId(TEST_PLATFORM_STRING, TEST_PUUID);

        assertThat(result).isNotNull();
        assertThat(result.getGameId()).isEqualTo(expected.getGameId());
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID);
    }

    @Test
    void getCurrentGameBySummonerId_notInGame_returnsNull() {
        when(mockSpectatorService.getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID)).thenReturn(null);

        assertThat(liveGameTool.getCurrentGameBySummonerId(TEST_PLATFORM_STRING, TEST_PUUID))
                .isNull();
        verify(mockSpectatorService).getCurrentGameInfo(TEST_PLATFORM, TEST_PUUID);
    }

    @Test
    void getCurrentGameBySummonerId_invalidPlatform_throws() {
        assertThatThrownBy(() -> liveGameTool.getCurrentGameBySummonerId("INVALID_PLATFORM", TEST_PUUID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getFeaturedGames_returnsFeaturedGames() {
        FeaturedGames expected = SpectatorTestFixtures.createSampleFeaturedGames();
        when(mockSpectatorService.getFeaturedGames(TEST_PLATFORM)).thenReturn(expected);

        FeaturedGames result = liveGameTool.getFeaturedGames(TEST_PLATFORM_STRING);

        assertThat(result).isNotNull();
        assertThat(result.getGameList()).hasSize(1);
        verify(mockSpectatorService).getFeaturedGames(TEST_PLATFORM);
    }

    @Test
    void getFeaturedGames_serviceException_propagates() {
        when(mockSpectatorService.getFeaturedGames(TEST_PLATFORM))
                .thenThrow(new RiotApiException("The Riot API is temporarily unavailable", 503));

        assertThatThrownBy(() -> liveGameTool.getFeaturedGames(TEST_PLATFORM_STRING))
                .isInstanceOf(RiotApiException.class);
        verify(mockSpectatorService).getFeaturedGames(TEST_PLATFORM);
    }
}
```

> `SpectatorTestFixtures.createSampleParticipant(...)` still sets `summonerName`/`summonerId` on the builder — that is fine, the domain fields remain and the builders are only used to construct in-memory objects, not to parse JSON. Leave the fixtures builder untouched.

- [ ] **Step 7: Build the module — green**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. Inventory gate matches the six-tool set. `RiotSpectatorAdapterTest` proves the V5 URLs, the `X-RIOT-TOKEN` header, and `404 → null`.

- [ ] **Step 8: Commit**

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator \
        lol-mcp-server/src/test/resources/fixtures/current-game.json \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "feat(lol)!: spectator v4 -> v5 (PUUID-keyed); drop dead spectator tools

Spectator-V5 keys the active game by PUUID. get_current_game_by_summoner_name
routed through the dead by-name summoner lookup; check_if_summoner_in_game
routed through it too and is redundant (a null current-game answers it). Both
removed, which drops LiveGameTool's SummonerService dependency. 404 -> null
stays in the adapter. Names unchanged this phase — the sweep is Phase 6."
```

---

## Phase 5 — League context (the exemplar)

League is the artifact 1b copies five times, so it is built to the pattern deliberately and **born on the final convention**: tools named `lol_league_*`, a single `player` param, resolution in the service. It is the first LoL context to depend on `PlayerIdentityResolver`, exercising the open half of Plan B's ArchUnit split for real.

### Task 3: League domain, port, and outbound adapter

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/domain/LeagueEntry.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/domain/LeagueList.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/domain/LeagueItem.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/domain/ApexTier.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/application/port/LeaguePort.java`
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/adapter/out/riot/RiotLeagueAdapter.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/league/adapter/out/riot/RiotLeagueAdapterTest.java`
- Create: `lol-mcp-server/src/test/resources/fixtures/league-entries.json`
- Create: `lol-mcp-server/src/test/resources/fixtures/league-apex.json`

**Interfaces:**
- Consumes: `RiotApiClient.platform(RiotApiPlatformUri)`, `com.muddl.riot.core.testsupport.Fixtures.read(String)`.
- Produces:
  - `ApexTier { CHALLENGER, GRANDMASTER, MASTER }` with `String leaguePath()` (e.g. `"challengerleagues"`).
  - `LeaguePort.getLeagueEntriesByPuuid(RiotApiPlatformUri, String puuid) -> List<LeagueEntry>` (empty list, never null).
  - `LeaguePort.getApexLeague(RiotApiPlatformUri, ApexTier, String queue) -> LeagueList`.
  - `RiotLeagueAdapter implements LeaguePort`.

- [ ] **Step 1: Verify the League-V4 paths against the live Riot developer portal**

Confirm before coding (League-V4):
- entries by PUUID: `GET /lol/league/v4/entries/by-puuid/{encryptedPUUID}` → a JSON **array** of entries;
- apex: `GET /lol/league/v4/challengerleagues/by-queue/{queue}`, `.../grandmasterleagues/by-queue/{queue}`, `.../masterleagues/by-queue/{queue}` → a single league object;
- queue values include `RANKED_SOLO_5x5` and `RANKED_FLEX_SR`.

If a path differs, adjust the adapter URIs and the stub URLs in Step 6 together.

- [ ] **Step 2: Create the domain DTOs**

`LeagueEntry.java`:

```java
package com.muddl.riot.lol.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A player's ranked-league standing for one queue (Riot League-V4 entries-by-puuid). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueEntry {
    private String leagueId;
    private String queueType;
    private String tier;
    private String rank;
    private String puuid;
    private int leaguePoints;
    private int wins;
    private int losses;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;
}
```

`LeagueItem.java` (a separate top-level DTO, not nested — see the nested-`@Builder` gotcha):

```java
package com.muddl.riot.lol.league.domain;

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

`LeagueList.java`:

```java
package com.muddl.riot.lol.league.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An apex league (challenger/grandmaster/master) for one queue (Riot League-V4). */
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

`ApexTier.java`:

```java
package com.muddl.riot.lol.league.domain;

/**
 * The three apex tiers, which have dedicated League-V4 endpoints
 * ({@code challengerleagues}/{@code grandmasterleagues}/{@code masterleagues}).
 */
public enum ApexTier {
    CHALLENGER,
    GRANDMASTER,
    MASTER;

    /** The Riot path segment for this tier, e.g. {@code challengerleagues}. */
    public String leaguePath() {
        return name().toLowerCase() + "leagues";
    }
}
```

- [ ] **Step 3: Create the outbound port**

`LeaguePort.java`:

```java
package com.muddl.riot.lol.league.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;

/** Outbound port for Riot League-V4 ranked data. League endpoints are platform-routed. */
public interface LeaguePort {

    /** A player's ranked entries, one per ranked queue. Empty when the player is unranked. */
    List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid);

    /** The apex league (challenger/grandmaster/master) for a ranked queue. */
    LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue);
}
```

- [ ] **Step 4: Write the failing WireMock adapter test**

Create the fixtures first. `src/test/resources/fixtures/league-entries.json`:

```json
[
  {
    "leagueId": "league-uuid-solo",
    "queueType": "RANKED_SOLO_5x5",
    "tier": "GOLD",
    "rank": "II",
    "puuid": "test-puuid-abc123",
    "leaguePoints": 42,
    "wins": 120,
    "losses": 100,
    "veteran": false,
    "inactive": false,
    "freshBlood": true,
    "hotStreak": false
  },
  {
    "leagueId": "league-uuid-flex",
    "queueType": "RANKED_FLEX_SR",
    "tier": "SILVER",
    "rank": "I",
    "puuid": "test-puuid-abc123",
    "leaguePoints": 15,
    "wins": 30,
    "losses": 28,
    "veteran": false,
    "inactive": false,
    "freshBlood": false,
    "hotStreak": true
  }
]
```

`src/test/resources/fixtures/league-apex.json`:

```json
{
  "leagueId": "apex-league-uuid",
  "tier": "CHALLENGER",
  "name": "Draven's Disciples",
  "queue": "RANKED_SOLO_5x5",
  "entries": [
    {
      "puuid": "challenger-puuid-1",
      "leaguePoints": 1355,
      "rank": "I",
      "wins": 320,
      "losses": 210,
      "veteran": true,
      "inactive": false,
      "freshBlood": false,
      "hotStreak": true
    }
  ]
}
```

Create `RiotLeagueAdapterTest.java`:

```java
package com.muddl.riot.lol.league.adapter.out.riot;

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
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiotLeagueAdapterTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final String PUUID = "test-puuid-abc123";
    private static final String ENTRIES_URL = "/lol/league/v4/entries/by-puuid/" + PUUID;
    private static final String CHALLENGER_URL = "/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5";

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

        adapter = new RiotLeagueAdapter(new RiotApiClient(properties));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void getLeagueEntriesByPuuid_parsesArray_andSendsApiKeyHeader() {
        stubFor(get(urlEqualTo(ENTRIES_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("league-entries.json"))));

        List<LeagueEntry> entries = adapter.getLeagueEntriesByPuuid(PLATFORM, PUUID);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getQueueType()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(entries.get(0).getTier()).isEqualTo("GOLD");
        assertThat(entries.get(0).getRank()).isEqualTo("II");
        assertThat(entries.get(0).getPuuid()).isEqualTo(PUUID);
        verify(getRequestedFor(urlEqualTo(ENTRIES_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void getLeagueEntriesByPuuid_emptyArray_returnsEmptyList() {
        stubFor(get(urlEqualTo(ENTRIES_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(adapter.getLeagueEntriesByPuuid(PLATFORM, PUUID)).isEmpty();
    }

    @Test
    void getApexLeague_challenger_hitsExpectedUrl_andParsesBody() {
        stubFor(get(urlEqualTo(CHALLENGER_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.read("league-apex.json"))));

        LeagueList league = adapter.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5");

        assertThat(league.getTier()).isEqualTo("CHALLENGER");
        assertThat(league.getQueue()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(league.getEntries()).hasSize(1);
        assertThat(league.getEntries().get(0).getLeaguePoints()).isEqualTo(1355);
        verify(getRequestedFor(urlEqualTo(CHALLENGER_URL)).withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }

    @Test
    void nonSuccessResponse_mapsToRiotApiException_withStatusPreserved() {
        stubFor(get(urlEqualTo(ENTRIES_URL)).willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> adapter.getLeagueEntriesByPuuid(PLATFORM, PUUID))
                .isInstanceOf(RiotApiException.class)
                .extracting(e -> ((RiotApiException) e).getStatusCode())
                .isEqualTo(403);
    }
}
```

- [ ] **Step 5: Run it — must fail to compile (no `RiotLeagueAdapter`)**

```bash
./gradlew :lol-mcp-server:test --tests '*RiotLeagueAdapterTest*'
```

Expected: FAIL — `cannot find symbol: class RiotLeagueAdapter`.

- [ ] **Step 6: Implement the adapter**

Create `RiotLeagueAdapter.java`:

```java
package com.muddl.riot.lol.league.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Riot League-V4 API adapter. League endpoints are platform-routed. */
@Component
@RequiredArgsConstructor
public class RiotLeagueAdapter implements LeaguePort {

    private final RiotApiClient riotApiClient;

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        LeagueEntry[] entries = riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/league/v4/entries/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(LeagueEntry[].class);
        return entries == null ? List.of() : List.of(entries);
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue) {
        return riotApiClient
                .platform(platform)
                .get()
                .uri("/lol/league/v4/{leaguePath}/by-queue/{queue}", tier.leaguePath(), queue)
                .retrieve()
                .body(LeagueList.class);
    }
}
```

- [ ] **Step 7: Run the adapter test — must pass**

```bash
./gradlew :lol-mcp-server:test --tests '*RiotLeagueAdapterTest*'
```

Expected: PASS — all four tests.

- [ ] **Step 8: Full module build (ArchUnit sees the new context)**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. The new `league` slice depends on no other LoL context, so `contexts_do_not_depend_on_each_other` needs no new exception; `RestClient` use is confined to `adapter.out.riot`; the `*Adapter`/`*Port` naming holds.

- [ ] **Step 9: Commit**

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/league \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/league \
        lol-mcp-server/src/test/resources/fixtures/league-entries.json \
        lol-mcp-server/src/test/resources/fixtures/league-apex.json
git commit -m "feat(lol): league context — domain, port, League-V4 adapter

The exemplar context 1b copies five times. Ranked entries by PUUID (a JSON
array -> List, empty when unranked) and the three apex leagues via
{tier}leagues/by-queue. Platform-routed. Endpoint paths verified against the
live Riot developer portal. WireMock-tested: URL, X-RIOT-TOKEN, array parsing,
empty list, error mapping."
```

---

### Task 4: League application service (port + resolver)

The service is the reference shape 1b inherits: it depends on its own `LeaguePort` and the shared `PlayerIdentityResolver`, resolves the `player` param itself, and never touches a `RestClient` or another context's service.

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/application/LeagueService.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/league/application/InMemoryLeaguePort.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/league/application/LeagueServiceTest.java`

**Interfaces:**
- Consumes: `LeaguePort` (Task 3), `PlayerIdentityResolver.resolvePuuid(String) -> String`.
- Produces:
  - `LeagueService.getLeagueEntriesByPlayer(RiotApiPlatformUri, String player) -> List<LeagueEntry>`.
  - `LeagueService.getApexLeague(RiotApiPlatformUri, ApexTier, String queue) -> LeagueList`.

- [ ] **Step 1: Write the hand fake and the failing service test**

Create `InMemoryLeaguePort.java`:

```java
package com.muddl.riot.lol.league.application;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-written in-memory {@link LeaguePort} for fast, HTTP-free service tests. */
public class InMemoryLeaguePort implements LeaguePort {

    private final Map<String, List<LeagueEntry>> entriesByPuuid = new HashMap<>();
    private final Map<String, LeagueList> apexByKey = new HashMap<>();

    public InMemoryLeaguePort putEntries(String puuid, List<LeagueEntry> entries) {
        entriesByPuuid.put(puuid, entries);
        return this;
    }

    public InMemoryLeaguePort putApex(ApexTier tier, String queue, LeagueList league) {
        apexByKey.put(tier + "|" + queue, league);
        return this;
    }

    @Override
    public List<LeagueEntry> getLeagueEntriesByPuuid(RiotApiPlatformUri platform, String puuid) {
        return entriesByPuuid.getOrDefault(puuid, List.of());
    }

    @Override
    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue) {
        return apexByKey.get(tier + "|" + queue);
    }
}
```

Create `LeagueServiceTest.java`:

```java
package com.muddl.riot.lol.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeagueServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryLeaguePort leaguePort = new InMemoryLeaguePort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final LeagueService leagueService = new LeagueService(leaguePort, resolver);

    @Test
    void getLeagueEntriesByPlayer_resolvesPlayer_thenReturnsEntries() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        LeagueEntry entry = LeagueEntry.builder()
                .queueType("RANKED_SOLO_5x5")
                .tier("CHALLENGER")
                .rank("I")
                .puuid("faker-puuid")
                .build();
        leaguePort.putEntries("faker-puuid", List.of(entry));

        assertThat(leagueService.getLeagueEntriesByPlayer(PLATFORM, "Faker#KR1")).containsExactly(entry);
    }

    @Test
    void getLeagueEntriesByPlayer_returnsEmpty_whenUnranked() {
        when(resolver.resolvePuuid("puuid-raw")).thenReturn("puuid-raw");

        assertThat(leagueService.getLeagueEntriesByPlayer(PLATFORM, "puuid-raw")).isEmpty();
    }

    @Test
    void getApexLeague_delegatesToPort() {
        LeagueList expected = LeagueList.builder().tier("CHALLENGER").build();
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", expected);

        assertThat(leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5"))
                .isSameAs(expected);
    }
}
```

- [ ] **Step 2: Run it — must fail to compile (no `LeagueService`)**

```bash
./gradlew :lol-mcp-server:test --tests '*LeagueServiceTest*'
```

Expected: FAIL — `cannot find symbol: class LeagueService`.

- [ ] **Step 3: Implement the service**

Create `LeagueService.java`:

```java
package com.muddl.riot.lol.league.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.port.LeaguePort;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot League-V4 ranked data. Depends on its own {@link LeaguePort} and the
 * shared {@link PlayerIdentityResolver} — never on a {@code RestClient} or another context's
 * service. This is the reference shape every player-keyed context in sub-project 1b copies (see the
 * 1a handoff contract): the tool passes a single {@code player}, and the service resolves it to a
 * PUUID here before calling the port.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeaguePort leaguePort;
    private final PlayerIdentityResolver identityResolver;

    public List<LeagueEntry> getLeagueEntriesByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching league entries on platform: {}", platform);
        return leaguePort.getLeagueEntriesByPuuid(platform, puuid);
    }

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue) {
        log.info("Fetching {} apex league for queue {} on platform: {}", tier, queue, platform);
        return leaguePort.getApexLeague(platform, tier, queue);
    }
}
```

- [ ] **Step 4: Run the service test — must pass**

```bash
./gradlew :lol-mcp-server:test --tests '*LeagueServiceTest*'
```

Expected: PASS — all three tests.

- [ ] **Step 5: Full module build, then commit**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. `LeagueService` depends on `..riot.account.identity..` (the open resolver surface) and no account-domain type — the split ArchUnit rule allows it.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/league/application/LeagueService.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/league/application
git commit -m "feat(lol): LeagueService — resolves player, delegates to LeaguePort

The handoff-contract shape 1b copies: a service that depends on its own port
and the shared PlayerIdentityResolver, resolves the single player param to a
PUUID itself, and never touches a RestClient or another context's service.
Port-fake test; the resolver is mocked (it is an external collaborator whose
constructor needs Caffeine, which is not on this module's test classpath)."
```

---

### Task 5: League MCP tools (born on the final convention)

The two league tools are the first to carry the Phase 6 convention — `lol_league_*` names, a single `player` param — so Phase 6 never has to rename them. This also registers `LeagueTool` with the inventory gate.

**Files:**
- Create: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/adapter/in/mcp/LeagueTool.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/league/adapter/in/mcp/LeagueToolTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: `LeagueService` (Task 4), `ApexTier`.
- Produces: two `@McpTool`s — `lol_league_entries_by_player`, `lol_league_apex_by_tier`.

- [ ] **Step 1: Add the two names and the class to the inventory gate (red)**

In `McpToolInventoryTest.java`: import `com.muddl.riot.lol.league.adapter.in.mcp.LeagueTool`; add it to the scanned `Stream.of(...)`; add `"lol_league_entries_by_player"` and `"lol_league_apex_by_tier"` to `EXPECTED_TOOL_NAMES` (now the eight of the "After Phase 5" column).

```java
        Set<String> actual = Stream.of(
                        RiotAccountTool.class,
                        AnalyticsTool.class,
                        LiveGameTool.class,
                        SummonerTool.class,
                        LeagueTool.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
```

```bash
./gradlew :lol-mcp-server:test --tests '*McpToolInventoryTest*'
```

Expected: FAIL to compile — `LeagueTool` does not exist yet.

- [ ] **Step 2: Write the failing tool test**

Create `LeagueToolTest.java`:

```java
package com.muddl.riot.lol.league.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.LeagueService;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for LeagueTool with a mocked LeagueService (no HTTP, no resolver). */
@ExtendWith(MockitoExtension.class)
class LeagueToolTest {

    @Mock
    private LeagueService mockLeagueService;

    @InjectMocks
    private LeagueTool leagueTool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getLeagueEntriesByPlayer_passesPlatformAndPlayerThrough() {
        LeagueEntry entry = LeagueEntry.builder().tier("GOLD").build();
        when(mockLeagueService.getLeagueEntriesByPlayer(PLATFORM, "Faker#KR1")).thenReturn(List.of(entry));

        assertThat(leagueTool.getLeagueEntriesByPlayer("NA1", "Faker#KR1")).containsExactly(entry);
        verify(mockLeagueService).getLeagueEntriesByPlayer(PLATFORM, "Faker#KR1");
    }

    @Test
    void getApexLeague_defaultsQueue_whenNull() {
        LeagueList league = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5"))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", null)).isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5");
    }

    @Test
    void getApexLeague_honoursExplicitQueue() {
        LeagueList league = LeagueList.builder().tier("MASTER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_FLEX_SR"))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "master", "RANKED_FLEX_SR")).isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_FLEX_SR");
    }

    @Test
    void getApexLeague_invalidTier_throws() {
        assertThatThrownBy(() -> leagueTool.getApexLeague("NA1", "DIAMOND", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getLeagueEntriesByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getLeagueEntriesByPlayer("INVALID_PLATFORM", "Faker#KR1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
```

- [ ] **Step 3: Run it — must fail to compile (no `LeagueTool`)**

```bash
./gradlew :lol-mcp-server:test --tests '*LeagueToolTest*'
```

Expected: FAIL — `cannot find symbol: class LeagueTool`.

- [ ] **Step 4: Implement the tool**

Create `LeagueTool.java`:

```java
package com.muddl.riot.lol.league.adapter.in.mcp;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.league.application.LeagueService;
import com.muddl.riot.lol.league.domain.ApexTier;
import com.muddl.riot.lol.league.domain.LeagueEntry;
import com.muddl.riot.lol.league.domain.LeagueList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for League of Legends ranked-league data. Born on sub-project 1a's final tool
 * convention: {@code lol_league_*} names and a single {@code player} param resolved in the service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueTool {

    private static final String DEFAULT_QUEUE = "RANKED_SOLO_5x5";

    private final LeagueService leagueService;

    @McpTool(
            name = "lol_league_entries_by_player",
            description = "Get a League of Legends player's ranked-league entries (one per ranked queue).")
    public List<LeagueEntry> getLeagueEntriesByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting league entries for a player on platform: {}", platform);
        return leagueService.getLeagueEntriesByPlayer(platform, player);
    }

    @McpTool(
            name = "lol_league_apex_by_tier",
            description =
                    "Get a League of Legends apex league (CHALLENGER, GRANDMASTER, or MASTER) for a ranked queue.")
    public LeagueList getApexLeague(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The apex tier: CHALLENGER, GRANDMASTER, or MASTER", required = true)
                    String tierStr,
            @McpToolParam(
                            description = "The ranked queue, e.g. RANKED_SOLO_5x5 (default) or RANKED_FLEX_SR",
                            required = false)
                    String queueStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        ApexTier tier = ApexTier.valueOf(tierStr.toUpperCase());
        String queue = (queueStr == null || queueStr.isBlank()) ? DEFAULT_QUEUE : queueStr;
        log.info("MCP Tool - Getting {} apex league for queue {} on platform: {}", tier, queue, platform);
        return leagueService.getApexLeague(platform, tier, queue);
    }
}
```

- [ ] **Step 5: Run the tool test and the gate — both pass**

```bash
./gradlew :lol-mcp-server:test --tests '*LeagueToolTest*' --tests '*McpToolInventoryTest*'
```

Expected: PASS. The inventory is now the eight tools of the "After Phase 5" column.

- [ ] **Step 6: Full module build, then commit**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. ArchUnit confirms `@McpTool` lives only in `..adapter.in.mcp..` and the `*Tool` name holds.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/league/adapter/in/mcp/LeagueTool.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/league/adapter/in/mcp/LeagueToolTest.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "feat(lol): league MCP tools, born on the final convention

lol_league_entries_by_player (single player param) and lol_league_apex_by_tier
(tier + optional queue, default RANKED_SOLO_5x5). Named the Phase 6 way from
the start so the sweep never has to rename them, and registered with the
inventory gate. Tool tests mock LeagueService — resolution lives in the service."
```

---

## Phase 6 — Contract sweep

Every remaining tool moves to `<game>_<context>_<action>` and, where player-keyed, to a single required `player` param accepting a `GameName#TAG` Riot ID **or** a raw PUUID (disambiguated on `#`), resolved internally. One param, not two optional ones, because "exactly one of" is not expressible in JSON Schema and models routinely fill both or neither. This phase runs after correctness and League so that any failure has an unambiguous cause.

### Task 6: Account — collapse the pair into `lol_account_by_player`

The two account tools (`get_riot_account_by_riot_id`, `get_riot_account_by_puuid`) collapse into one `lol_account_by_player` once the single param accepts either form. This tool disambiguates on `#` **locally** rather than through `PlayerIdentityResolver`: it must return account **data** for both forms, and the resolver returns only a PUUID — routing a Riot ID through the resolver would do a lookup and then a second `getAccountByPuuid`, where a direct `getAccountByRiotId` already has the account. The account tool is on the allow-list for the account domain, so calling `RiotAccountService` directly is legal.

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/account/adapter/in/mcp/RiotAccountTool.java`
- Create: `lol-mcp-server/src/test/java/com/muddl/riot/lol/account/adapter/in/mcp/RiotAccountToolTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: `RiotAccountService.getAccountByRiotId(String, String)`, `RiotAccountService.getAccountByPuuid(String)`.
- Produces: one `@McpTool` `lol_account_by_player` → `RiotAccount`.

- [ ] **Step 1: Update the inventory gate (red)**

In `McpToolInventoryTest.java`, remove `"get_riot_account_by_riot_id"` and `"get_riot_account_by_puuid"`; add `"lol_account_by_player"`.

```bash
./gradlew :lol-mcp-server:test --tests '*McpToolInventoryTest*'
```

Expected: FAIL — `RiotAccountTool` still declares the two old names.

- [ ] **Step 2: Write the failing tool test**

Create `RiotAccountToolTest.java`:

```java
package com.muddl.riot.lol.account.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiotAccountToolTest {

    @Mock
    private RiotAccountService mockAccountService;

    @InjectMocks
    private RiotAccountTool accountTool;

    @Test
    void riotIdForm_routesToGetByRiotId() {
        RiotAccount account = RiotAccount.builder().puuid("p").gameName("Faker").tagLine("KR1").build();
        when(mockAccountService.getAccountByRiotId("Faker", "KR1")).thenReturn(account);

        assertThat(accountTool.getAccountByPlayer("Faker#KR1")).isSameAs(account);
        verify(mockAccountService).getAccountByRiotId("Faker", "KR1");
        verifyNoMoreInteractions(mockAccountService);
    }

    @Test
    void puuidForm_routesToGetByPuuid() {
        RiotAccount account = RiotAccount.builder().puuid("raw-puuid").build();
        when(mockAccountService.getAccountByPuuid("raw-puuid")).thenReturn(account);

        assertThat(accountTool.getAccountByPlayer("raw-puuid")).isSameAs(account);
        verify(mockAccountService).getAccountByPuuid("raw-puuid");
        verifyNoMoreInteractions(mockAccountService);
    }

    @Test
    void blankPlayer_throwsWithBothFormsNamed() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountTool.getAccountByPlayer("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GameName#TAG")
                .hasMessageContaining("PUUID");
    }

    @Test
    void malformedRiotId_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountTool.getAccountByPlayer("Faker#"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountTool.getAccountByPlayer("#KR1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 3: Run it — must fail to compile (no `getAccountByPlayer`)**

```bash
./gradlew :lol-mcp-server:test --tests '*RiotAccountToolTest*'
```

Expected: FAIL — `cannot find symbol: method getAccountByPlayer`.

- [ ] **Step 4: Rewrite the account tool**

Replace `RiotAccountTool.java`:

```java
package com.muddl.riot.lol.account.adapter.in.mcp;

import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool for Riot account lookups. Takes a single {@code player} — a {@code GameName#TAG} Riot ID
 * or a raw PUUID — and returns the account. It disambiguates on {@code #} and calls the account
 * service directly (rather than routing through {@code PlayerIdentityResolver}) because it needs
 * account <em>data</em> for both forms, and the resolver returns only a PUUID; this tool is on the
 * account-domain allow-list, so the direct call is legal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotAccountTool {

    private final RiotAccountService accountService;

    @McpTool(
            name = "lol_account_by_player",
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
        log.info("MCP Tool - Getting account by Riot ID: {}#{}", parts[0], parts[1]);
        return accountService.getAccountByRiotId(parts[0], parts[1]);
    }

    private static String unparseableMessage(String player) {
        return "Cannot parse player '" + player + "'. Provide a Riot ID as GameName#TAG "
                + "(for example Faker#KR1) or a raw PUUID.";
    }
}
```

- [ ] **Step 5: Run the tool test and gate — pass**

```bash
./gradlew :lol-mcp-server:test --tests '*RiotAccountToolTest*' --tests '*McpToolInventoryTest*'
```

Expected: PASS.

- [ ] **Step 6: Full module build, then commit**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/account/adapter/in/mcp/RiotAccountTool.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/account/adapter/in/mcp/RiotAccountToolTest.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "feat(lol)!: collapse account tools into lol_account_by_player

One tool, one player param accepting a Riot ID (GameName#TAG) or a raw PUUID,
disambiguated on '#'. Kept local (not via PlayerIdentityResolver) because it
needs account data for both forms and the tool is allow-listed for the account
domain; routing a Riot ID through the resolver would double the Riot calls."
```

---

### Task 7: Summoner — `lol_summoner_by_player`

`SummonerService` gains `PlayerIdentityResolver` and a `getSummonerByPlayer` that resolves then calls the existing by-puuid port. `getSummonerByPuuid` stays because `AnalyticsService` uses it directly. The tool renames to `lol_summoner_by_player` and takes `player`.

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner/application/SummonerService.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner/adapter/in/mcp/SummonerTool.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/summoner/application/SummonerServiceTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: `PlayerIdentityResolver.resolvePuuid`, `SummonerPort.getSummonerByPuuid`.
- Produces: `SummonerService.getSummonerByPlayer(RiotApiPlatformUri, String player) -> Summoner` (plus the retained `getSummonerByPuuid`); one `@McpTool` `lol_summoner_by_player`.

- [ ] **Step 1: Update the inventory gate (red)**

In `McpToolInventoryTest.java`, replace `"get_lol_summoner_by_puuid"` with `"lol_summoner_by_player"`.

```bash
./gradlew :lol-mcp-server:test --tests '*McpToolInventoryTest*'
```

Expected: FAIL — `SummonerTool` still declares `get_lol_summoner_by_puuid`.

- [ ] **Step 2: Add the resolver and `getSummonerByPlayer` to the service, updating its test**

In `SummonerServiceTest.java`, wire a mocked resolver and add the by-player case (keep the by-puuid cases from Task 1):

```java
package com.muddl.riot.lol.summoner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.domain.Summoner;
import org.junit.jupiter.api.Test;

class SummonerServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SummonerService summonerService = new SummonerService(summonerPort, resolver);

    @Test
    void getSummonerByPuuid_returnsStoredSummoner() {
        Summoner expected = Summoner.builder().puuid("p").build();
        summonerPort.putByPuuid(PLATFORM, "p", expected);

        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "p")).isSameAs(expected);
    }

    @Test
    void getSummonerByPuuid_returnsNull_whenUnknown() {
        assertThat(summonerService.getSummonerByPuuid(PLATFORM, "ghost-puuid")).isNull();
    }

    @Test
    void getSummonerByPlayer_resolvesPlayer_thenReturnsSummoner() {
        Summoner expected = Summoner.builder().puuid("faker-puuid").build();
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        summonerPort.putByPuuid(PLATFORM, "faker-puuid", expected);

        assertThat(summonerService.getSummonerByPlayer(PLATFORM, "Faker#KR1")).isSameAs(expected);
    }
}
```

Update `SummonerService.java`:

```java
package com.muddl.riot.lol.summoner.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.summoner.application.port.SummonerPort;
import com.muddl.riot.lol.summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends summoner lookups. Delegates retrieval to the outbound
 * {@link SummonerPort} and resolves the caller's {@code player} reference via the shared
 * {@link PlayerIdentityResolver}; holds no HTTP concerns. {@code getSummonerByPuuid} is retained for
 * {@code AnalyticsService}, which already holds a PUUID.
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
        log.info("Fetching summoner for PUUID: {} on platform: {}", puuid, platform);
        return summonerPort.getSummonerByPuuid(platform, puuid);
    }
}
```

- [ ] **Step 3: Rename the tool and switch it to `player`**

Replace `SummonerTool.java`'s tool method:

```java
    @McpTool(
            name = "lol_summoner_by_player",
            description = "Get League of Legends summoner information by player (a Riot ID as GameName#TAG, or a raw PUUID).")
    public Summoner getSummonerByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting summoner for a player on platform: {}", platform);
        return summonerService.getSummonerByPlayer(platform, player);
    }
```

- [ ] **Step 4: Full module build (gate + AnalyticsService compile), then commit**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. `AnalyticsService` still compiles against the retained `getSummonerByPuuid`. The gate now shows `lol_summoner_by_player`.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/summoner \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/summoner/application/SummonerServiceTest.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "feat(lol)!: lol_summoner_by_player with a single player param

SummonerService gains PlayerIdentityResolver and getSummonerByPlayer (resolve
then by-puuid). getSummonerByPuuid stays for AnalyticsService, which already
holds a PUUID. Tool renamed and switched to the player param."
```

---

### Task 8: Spectator — `lol_spectator_current_game_by_player` + `lol_spectator_featured_games`

`SpectatorService` gains the resolver and a `getCurrentGameByPlayer`; the two tools take the convention names. Featured games is a rename only (not player-keyed).

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator/application/SpectatorService.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator/adapter/in/mcp/LiveGameTool.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator/application/SpectatorServiceTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator/adapter/in/mcp/LiveGameToolTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: `PlayerIdentityResolver.resolvePuuid`, `SpectatorPort.getCurrentGameInfo`.
- Produces: `SpectatorService.getCurrentGameByPlayer(RiotApiPlatformUri, String player) -> CurrentGameInfo` (plus the retained puuid-keyed `getCurrentGameInfo`); tools `lol_spectator_current_game_by_player`, `lol_spectator_featured_games`.

- [ ] **Step 1: Update the inventory gate (red)**

In `McpToolInventoryTest.java`, replace `"get_current_game_by_summoner_id"` with `"lol_spectator_current_game_by_player"` and `"get_featured_games"` with `"lol_spectator_featured_games"`.

```bash
./gradlew :lol-mcp-server:test --tests '*McpToolInventoryTest*'
```

Expected: FAIL — `LiveGameTool` still declares the old names.

- [ ] **Step 2: Add the resolver and `getCurrentGameByPlayer` to the service, updating its test**

In `SpectatorServiceTest.java`, add a mocked resolver and a by-player test (keep the existing puuid/featured tests, adjusting the constructor):

```java
    private final InMemorySpectatorPort spectatorPort = new InMemorySpectatorPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final SpectatorService spectatorService = new SpectatorService(spectatorPort, resolver);
```

(add imports `com.muddl.riot.account.identity.PlayerIdentityResolver`, `static org.mockito.Mockito.mock`, `static org.mockito.Mockito.when`), and add:

```java
    @Test
    void getCurrentGameByPlayer_resolvesPlayer_thenReturnsGame() {
        CurrentGameInfo game = SpectatorTestFixtures.createSampleCurrentGameInfo();
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        spectatorPort.putGame("faker-puuid", game);

        assertThat(spectatorService.getCurrentGameByPlayer(PLATFORM, "Faker#KR1")).isSameAs(game);
    }
```

Update `SpectatorService.java`:

```java
package com.muddl.riot.lol.spectator.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.spectator.application.port.SpectatorPort;
import com.muddl.riot.lol.spectator.domain.CurrentGameInfo;
import com.muddl.riot.lol.spectator.domain.FeaturedGames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends live-game data. Resolves the caller's {@code player}
 * reference via {@link PlayerIdentityResolver}, then delegates to the outbound {@link SpectatorPort};
 * holds no HTTP concerns. Returns {@code null} when the player is not currently in a game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectatorService {

    private final SpectatorPort spectatorPort;
    private final PlayerIdentityResolver identityResolver;

    public CurrentGameInfo getCurrentGameByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        return getCurrentGameInfo(platform, puuid);
    }

    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String puuid) {
        log.info("Fetching current game info for PUUID: {} on platform: {}", puuid, platform);
        return spectatorPort.getCurrentGameInfo(platform, puuid);
    }

    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform) {
        log.info("Fetching featured games for platform: {}", platform);
        return spectatorPort.getFeaturedGames(platform);
    }
}
```

- [ ] **Step 3: Rename the tools and switch current-game to `player`**

In `LiveGameTool.java`, rename the two `@McpTool`s and change the current-game method to take `player` and call `getCurrentGameByPlayer`:

```java
    @McpTool(
            name = "lol_spectator_current_game_by_player",
            description =
                    "Get current live game information for a player (a Riot ID as GameName#TAG, or a raw PUUID). Returns live game details if in a game, null if not.")
    public CurrentGameInfo getCurrentGameByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting current game for a player on platform: {}", platform);
        return spectatorService.getCurrentGameByPlayer(platform, player);
    }

    @McpTool(
            name = "lol_spectator_featured_games",
            description =
                    "Get list of current featured games on a platform. Featured games are high-profile matches selected by Riot.")
    public FeaturedGames getFeaturedGames(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        log.info("MCP Tool - Getting featured games for platform: {}", platform);
        return spectatorService.getFeaturedGames(platform);
    }
```

In `LiveGameToolTest.java`, rename the current-game test methods to drive `getCurrentGameByPlayer(TEST_PLATFORM_STRING, "Faker#KR1")` and stub `mockSpectatorService.getCurrentGameByPlayer(TEST_PLATFORM, "Faker#KR1")`; rename the featured-games verifications' target method stays `getFeaturedGames`. The in-game test becomes:

```java
    @Test
    void getCurrentGameByPlayer_inGame_returnsCurrentGameInfo() {
        CurrentGameInfo expected = SpectatorTestFixtures.createSampleCurrentGameInfo();
        when(mockSpectatorService.getCurrentGameByPlayer(TEST_PLATFORM, "Faker#KR1")).thenReturn(expected);

        CurrentGameInfo result = liveGameTool.getCurrentGameByPlayer("NA1", "Faker#KR1");

        assertThat(result).isNotNull();
        assertThat(result.getGameId()).isEqualTo(expected.getGameId());
        verify(mockSpectatorService).getCurrentGameByPlayer(TEST_PLATFORM, "Faker#KR1");
    }
```

Apply the same rename to the not-in-game and invalid-platform current-game tests (`getCurrentGameByPlayer`, a `player` string), leaving the featured-games tests otherwise unchanged. The now-unused `TEST_PUUID` constant can be removed.

- [ ] **Step 4: Full module build, then commit**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. The gate shows both `lol_spectator_*` names.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/spectator \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/spectator \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "feat(lol)!: lol_spectator_current_game_by_player + featured_games

SpectatorService gains PlayerIdentityResolver and getCurrentGameByPlayer
(resolve then by-puuid); the puuid-keyed method stays as its delegate. Tools
renamed to the convention; current-game switches to the single player param,
featured-games is a rename only."
```

---

### Task 9: Analytics — `lol_analytics_player_matches` via the resolver

`AnalyticsService` currently splits the Riot ID by hand and calls `RiotAccountService.getAccountByRiotId`. Replacing that with `PlayerIdentityResolver.resolvePuuid(player)` removes the manual parsing, drops the analytics→account-**domain** dependency (it now depends only on the open identity surface), and accepts a raw PUUID too. The tool renames to `lol_analytics_player_matches` and its first param becomes `player`.

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/analytics/application/AnalyticsService.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/analytics/adapter/in/mcp/AnalyticsTool.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/analytics/application/AnalyticsServiceTest.java`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: `PlayerIdentityResolver.resolvePuuid`, `SummonerService.getSummonerByPuuid`, `MatchService.getMatchIdsByPuuid`, `MatchService.getMatchById`.
- Produces: `AnalyticsService.getPlayerMatchAnalytics(String player, RiotApiPlatformUri, RiotApiRegionUri, int) -> PlayerMatchAnalytics`; one `@McpTool` `lol_analytics_player_matches`.

- [ ] **Step 1: Update the inventory gate (red)**

In `McpToolInventoryTest.java`, replace `"get_lol_player_match_analytics"` with `"lol_analytics_player_matches"`. This is the **final** seven-tool set of the "After Phase 6" column.

```bash
./gradlew :lol-mcp-server:test --tests '*McpToolInventoryTest*'
```

Expected: FAIL — `AnalyticsTool` still declares `get_lol_player_match_analytics`.

- [ ] **Step 2: Rewrite the service test to mock the resolver (no account port)**

Replace the fields, constructor, and `givenPlayer()` in `AnalyticsServiceTest.java`; the three test bodies are unchanged except that `givenPlayer` no longer seeds an account:

```java
package com.muddl.riot.lol.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.analytics.domain.PlayerMatchAnalytics;
import com.muddl.riot.lol.match.application.InMemoryMatchPort;
import com.muddl.riot.lol.match.application.MatchService;
import com.muddl.riot.lol.match.domain.Match;
import com.muddl.riot.lol.match.domain.MatchInfo;
import com.muddl.riot.lol.match.domain.Participant;
import com.muddl.riot.lol.summoner.application.InMemorySummonerPort;
import com.muddl.riot.lol.summoner.application.SummonerService;
import com.muddl.riot.lol.summoner.domain.Summoner;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;
    private static final RiotApiRegionUri REGION = RiotApiRegionUri.AMERICAS;
    private static final String PLAYER = "Player#NA1";
    private static final String PUUID = "puuid-1";

    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final InMemorySummonerPort summonerPort = new InMemorySummonerPort();
    private final InMemoryMatchPort matchPort = new InMemoryMatchPort();

    private final SummonerService summonerService = new SummonerService(summonerPort, resolver);
    private final AnalyticsService analyticsService =
            new AnalyticsService(resolver, summonerService, new MatchService(matchPort));

    private void givenPlayer() {
        when(resolver.resolvePuuid(PLAYER)).thenReturn(PUUID);
        summonerPort.putByPuuid(
                PLATFORM,
                PUUID,
                Summoner.builder().name("Player").summonerLevel(100).build());
    }
```

(Keep the three `@Test` methods and the `match(...)` helper exactly as they are — they already call `getPlayerMatchAnalytics("Player#NA1", ...)`, which is now `PLAYER`; you may substitute the constant or leave the literal.)

> Note: `SummonerService` here is constructed with the same mocked `resolver`, but analytics only calls `summonerService.getSummonerByPuuid(...)`, which does not touch the resolver — so no extra stubbing is needed.

- [ ] **Step 3: Rewrite the service to use the resolver**

In `AnalyticsService.java`: replace the `RiotAccountService accountService` field with `PlayerIdentityResolver identityResolver`; drop the `RiotAccount`/`RiotAccountService` imports; add `import com.muddl.riot.account.identity.PlayerIdentityResolver;`. Replace the Riot-ID parsing and account lookup (the old Steps 1–2) with a single resolve, and use `puuid` throughout:

```java
    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            String player, RiotApiPlatformUri platform, RiotApiRegionUri region, int matchCount) {
        log.info("Generating match analytics for player on platform: {}", platform);

        // Resolve the caller's player reference (Riot ID or raw PUUID) to a PUUID once.
        String puuid = identityResolver.resolvePuuid(player);

        // Summoner name/level for the summary.
        Summoner summoner = summonerService.getSummonerByPuuid(platform, puuid);

        // Recent match IDs, then details.
        List<String> matchIds = matchService.getMatchIdsByPuuid(region, puuid, matchCount, 0, null);
```

In the participant loop and both `PlayerMatchAnalytics.builder()` calls, replace `account.getPuuid()` with `puuid` and `.riotId(riotId)` with `.riotId(player)`. Nothing else in the method changes (the KDA/average logic is untouched). Update the method javadoc's `@param riotId` to `@param player`.

- [ ] **Step 4: Rename the tool and its first param**

In `AnalyticsTool.java`, rename the `@McpTool` and switch `riotId` → `player`:

```java
    @McpTool(
            name = "lol_analytics_player_matches",
            description = "Get detailed analytics of a League of Legends player's recent matches")
    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The Riot region, e.g. AMERICAS, EUROPE", required = true) String regionStr,
            @McpToolParam(description = "Number of recent matches to analyze, 1-100, defaults to 10", required = false)
                    Integer matchCount) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr);
        RiotApiRegionUri region = RiotApiRegionUri.valueOf(regionStr);
        int count = matchCount == null ? 10 : Math.min(100, Math.max(1, matchCount));
        log.info("MCP Tool - Generating match analytics for a player on platform: {}", platform);
        return analyticsService.getPlayerMatchAnalytics(player, platform, region, count);
    }
```

- [ ] **Step 5: Full module build, then commit**

```bash
./gradlew spotlessApply && ./gradlew :lol-mcp-server:build
```

Expected: `BUILD SUCCESSFUL`. The gate now shows the final seven tools. Analytics depends only on the open identity surface, not the account domain — `only_analytics_and_the_account_tool_use_the_account_domain` still holds (analytics is allow-listed regardless), and the analytics→account-domain edge is gone.

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/analytics \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/analytics/application/AnalyticsServiceTest.java \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "feat(lol)!: lol_analytics_player_matches, player param via resolver

AnalyticsService drops its hand-rolled Riot-ID split and account lookup for
PlayerIdentityResolver.resolvePuuid(player) — one resolve, accepts a raw PUUID
too, and no longer depends on the account domain (only the open identity
surface). Tool renamed; first param is now player."
```

---

### Task 10: Record the contract (ADR-0009), changelog, and knowledge base

The sweep is a public break; it gets its ADR (named as a cycle artifact in the 1a spec), a CHANGELOG entry, the reference-implementation note in the bounded-context pattern, and the inventory gate's javadoc frozen at seven. This is Plan C's exit gate.

**Files:**
- Create: `docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md`
- Modify: `docs/knowledge/README.md`
- Modify: `docs/knowledge/patterns/add-a-bounded-context.md`
- Modify: `docs/knowledge/roadmap.md`
- Modify: `lol-mcp-server/CHANGELOG.md`
- Modify: `lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java`

**Interfaces:**
- Consumes: the seven-tool inventory (Tasks 1–9).
- Produces: the durable record; Plan D (docs + sanity check) picks up per-module docs.

- [ ] **Step 1: Freeze the inventory gate javadoc at seven**

In `McpToolInventoryTest.java`, replace the class javadoc with the settled statement (the set itself is already the final seven from Task 9):

```java
/**
 * Guards the public MCP contract: exactly the seven tools sub-project 1a settled on, each named
 * {@code <game>_<context>_<action>}, every player-keyed tool taking a single {@code player} param.
 * See [ADR-0009](../../../../../../docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md). If this
 * test fails, a tool's name changed without the contract (and this list) being updated to match.
 */
```

- [ ] **Step 2: Write ADR-0009**

Create `docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md`:

```markdown
# ADR-0009: MCP tool contract — naming and the single `player` param

- **Status:** Accepted
- **Date:** 2026-07-17

## Context

The pre-1a tool surface had grown ad hoc: mixed name shapes (`get_lol_summoner_by_puuid`,
`get_current_game_by_summoner_name`, `get_riot_account_by_riot_id`), paired by-riot-id/by-puuid
tools, and three tools whose underlying Riot path no longer resolves. Tool-selection accuracy was
the reason for the monorepo ([ADR-0006](ADR-0006-monorepo-split.md)); an inconsistent surface works
against it. Sub-project 1a is the place to take the break, on a small surface, before four more
servers copy the shape.

## Decision

**Naming: every tool is `<game>_<context>_<action>`** — e.g. `lol_summoner_by_player`,
`lol_league_entries_by_player`, `lol_account_by_player`.

**Boundary identity: every player-keyed tool takes one required `player` param** accepting a
`GameName#TAG` Riot ID or a raw PUUID, disambiguated on `#`, resolved internally via
`PlayerIdentityResolver` ([ADR-0008](ADR-0008-shared-player-identity-resolution.md)). The model
never chains `account → summoner → match` itself — that chaining is both a rate-limit multiplier and
a common way models flail.

**One param, not two optional ones.** "Exactly one of `riot_id` / `puuid`" is not expressible in
JSON Schema, so it degrades to a runtime check models routinely get wrong by filling both or
neither. One param keeps the schema small across a surface 1b will grow. An unparseable value
returns an actionable error naming both accepted forms.

**Resolution lives in the application service**, not the tool (the handoff-contract shape 1b
copies): a player-keyed service depends on its own port and `PlayerIdentityResolver`, and resolves
`player` itself. The **account tool** is the sole exception — it disambiguates `#` locally and calls
`RiotAccountService` directly, because it needs account *data* for both forms and is allow-listed
for the account domain; routing a Riot ID through the resolver would double the Riot calls.

**No aliases.** Pre-1.0, with no public consumers, the break is taken deliberately and documented
rather than preserved behind aliases that would double the surface.

**The surface shrinks from ten tools to seven while capability grows:**

| After | From |
|---|---|
| `lol_account_by_player` | `get_riot_account_by_puuid` + `get_riot_account_by_riot_id` (collapsed) |
| `lol_summoner_by_player` | `get_lol_summoner_by_puuid` |
| `lol_spectator_current_game_by_player` | `get_current_game_by_summoner_id` (+ dead by-name/in-game) |
| `lol_spectator_featured_games` | `get_featured_games` (rename) |
| `lol_analytics_player_matches` | `get_lol_player_match_analytics` (rename) |
| `lol_league_entries_by_player` | new (League exemplar) |
| `lol_league_apex_by_tier` | new (League exemplar) |

Removed as dead or superseded: `get_lol_summoner_by_name`, `get_lol_summoner_by_id`,
`get_current_game_by_summoner_name`, `check_if_summoner_in_game`.

## Consequences

**Makes easy:** a uniform, small surface that 1b extends by copying League; better tool-selection
accuracy; one Riot call per player-keyed invocation (amortized by the resolver cache).

**Costs:** a one-time public break with no aliases; `McpToolInventoryTest` must be updated in lockstep
with any future tool change (it is the enforced contract).

**Watch for:**

- **The account tool's local disambiguation is deliberate** — do not "consistency-fix" it to route
  through the resolver; that reopens a second Riot call and buys nothing.
- **New player-keyed tools resolve in the service, not the tool** — keep tools thin.
- **`<game>_<context>_<action>` is the shape every server inherits** — a new tool that breaks it
  should be caught in review and by the inventory test's intent.
```

- [ ] **Step 3: Link ADR-0009 from the knowledge index**

In `docs/knowledge/README.md`, insert into the "Decisions (ADRs)" list between ADR-0008 and ADR-0010:

```markdown
- [ADR-0009 — MCP tool contract](decisions/ADR-0009-mcp-tool-contract.md)
```

- [ ] **Step 4: Name League the reference implementation in the pattern**

At the top of `docs/knowledge/patterns/add-a-bounded-context.md`, after the opening rationale line, add:

```markdown
> **Reference implementation:** the `league` context in `lol-mcp-server` is the worked example this
> pattern describes — a full mini-hexagon with both a by-player tool and an apex tool. Read it
> alongside this guide.
>
> **Handoff contract (sub-project 1a).** A context is one package under the server root with
> `domain/`, `application/` + `application/port/`, `adapter/out/riot/`, and `adapter/in/mcp/`. The
> service depends on `PlayerIdentityResolver` and its own port — never on a `RestClient`, never on
> another context's service. The tool takes a single `player` param and is named
> `<game>_<context>_<action>` (see [ADR-0009](../decisions/ADR-0009-mcp-tool-contract.md)). Tests are
> a WireMock adapter test plus a port-fake service test (mock the resolver). Endpoint paths are
> verified against the live Riot developer portal.
```

- [ ] **Step 5: Record the changelog entry**

In `lol-mcp-server/CHANGELOG.md`, under `## [0.1.0] - unreleased`, add `### Added` and `### Removed` sections and extend `### Changed`, and delete the now-satisfied planning comment:

```markdown
### Added
- `lol_league_entries_by_player` and `lol_league_apex_by_tier` — ranked-league entries by player and
  the apex leagues (challenger/grandmaster/master), the exemplar League context.

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.lol`.
- **Breaking:** every tool is renamed to `<game>_<context>_<action>`, and every player-keyed tool
  takes a single `player` param accepting a Riot ID (`GameName#TAG`) or a raw PUUID. The two account
  tools collapse into `lol_account_by_player`. See
  [ADR-0009](../docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md).
- **Breaking:** the spectator active-game lookup moved to Spectator-V5 (PUUID-keyed).

### Removed
- **Breaking:** `get_lol_summoner_by_name`, `get_lol_summoner_by_id`,
  `get_current_game_by_summoner_name`, and `check_if_summoner_in_game` — the first three routed
  through Riot-ID-era paths that no longer resolve or through `encryptedSummonerId`, which Riot is
  stripping; the fourth is redundant (a null current game answers it).
```

- [ ] **Step 6: Mark Plan C done in the roadmap**

In `docs/knowledge/roadmap.md`, update the 1a **Progress** line:

```markdown
**Progress:** Plans A (coordinates + release engineering), B (library hardening: retry, error
taxonomy, identity resolver), and C (LoL server: correctness, the League exemplar, the tool-contract
sweep) complete. Plan D (per-module docs + the monorepo sanity check) follows.
```

- [ ] **Step 7: Plan C exit gate — full repository build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. This is Plan C's gate: seven tools named on the convention, League added as the exemplar, spectator on V5, the dead tools gone, `verifyRelease` green (`lol-mcp-server` still `0.1.0` with matching heading), ArchUnit green (League slice independent; the account split rule still bites — resolver consumers are legal via the identity carve-out), no library modified.

- [ ] **Step 8: Verify the ADR link and count**

```bash
ls docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md
grep -c 'ADR-0009' docs/knowledge/README.md
```

Expected: file listed; `grep -c` prints `1`.

- [ ] **Step 9: Commit**

```bash
git add docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md \
        docs/knowledge/README.md \
        docs/knowledge/patterns/add-a-bounded-context.md \
        docs/knowledge/roadmap.md \
        lol-mcp-server/CHANGELOG.md \
        lol-mcp-server/src/test/java/com/muddl/riot/lol/McpToolInventoryTest.java
git commit -m "docs: ADR-0009 (MCP tool contract); changelog + KB for Plan C

Records the <game>_<context>_<action> naming, the single player param and why
one param not two, resolution-in-service (with the account tool's deliberate
local disambiguation), no aliases, and ten tools -> seven. Names League the
reference implementation with the 1b handoff contract; logs the break in the
server changelog; marks Plan C done in the roadmap."
```

---

## Verification (beyond green tests)

Green tests prove units behave; they do not prove the server serves. Before calling Plan C done, run sub-project 0's transport bar (record results; these are manual, not part of `./gradlew build`):

- **Both transports handshake.** SSE on 8080 and stdio, listing tools and calling one. Stdio's stdout must stay pure JSON — no stray log line (see the STDIO gotcha; `application-stdio.yml`'s three settings are load-bearing).
- **Tool inventory matches the Phase 6 table exactly** — seven tools, named as listed. Here the change *is* the point: the inventory is expected to differ from before, and `McpToolInventoryTest` is the automated half of this check.
- **Endpoint paths were verified against the live Riot developer portal** (Tasks 2 and 3 verify-first steps) — Spectator-V5 and League-V4.
- **No library was modified.** `git diff --stat <plan-C-base>..HEAD -- riot-api-core riot-account-core` shows nothing under `src/`. If it does, that is the 1a success-criterion finding — record it.

## Plan C exit criteria

- `./gradlew build` green (tests + ArchUnit + JaCoCo + `verifyRelease` + Spotless).
- **Correctness:** the four dead/legacy tools are gone; spectator active-game is Spectator-V5, PUUID-keyed, with `404 → null` intact; summoner is PUUID-only.
- **League exemplar:** a full mini-hexagon (`domain/`, `application/` + `port/`, `adapter/out/riot/`, `adapter/in/mcp/`) with a WireMock adapter test and a port-fake service test; endpoint paths portal-verified; `LeagueService` depends on `LeaguePort` + `PlayerIdentityResolver` and nothing else.
- **Contract sweep:** exactly seven tools, all `<game>_<context>_<action>`, every player-keyed tool on a single `player` param; `McpToolInventoryTest` asserts the set and is frozen with ADR-0009.
- **Boundaries held:** ArchUnit green — League is an independent slice; the split account rule still bites (identity carve-out lets the new resolver consumers pass, the domain stays confined); `@McpTool` only in `..adapter.in.mcp..`; `RestClient` only in `..adapter.out.riot..`.
- **No library modified**; `lol-mcp-server` stays `0.1.0` with a `## [0.1.0]` heading carrying the new entries.
- ADR-0009 exists and is linked; League is named the reference implementation in the bounded-context pattern; the roadmap marks Plan C done.

## What Plan D picks up (Phase 7)

Per-module docs (README/ARCHITECTURE/CHANGELOG for both libraries and the server, enforced by a docs-presence test) and the monorepo sanity check — including retiring the now-unused `spectator → summoner` ArchUnit exception, auditing that no rule carries a fully-qualified package in its condition, and confirming no `com.wkaiser` reference survives.

## Self-review notes

- **Spec coverage.** Phase 4 → Tasks 1–2; Phase 5 → Tasks 3–5; Phase 6 → Tasks 6–10. The spec's Phase 6 inventory table maps one-to-one onto the "After Phase 6" column and ADR-0009's table.
- **Born-correct League.** League is built directly on the final names/`player` convention (Task 5), so the Phase 6 sweep only touches the four pre-existing tools — this is a deliberate reading of the spec's "1b's tools are born correct" applied to the exemplar itself, and it is the first LoL exercise of Plan B's open ArchUnit rule.
- **Library-untouched.** Resolution-in-service uses `PlayerIdentityResolver` (mocked in tests to dodge the Caffeine-`Ticker` classpath gap); the account tool disambiguates locally; `RiotAccountService`/`RiotAccountPort` are consumed, never edited. Nothing in `riot-api-core`/`riot-account-core` changes — the falsifiable 1a criterion holds.
- **Gate discipline.** `McpToolInventoryTest` is edited in the same task as every tool change and goes red-then-green each time, so no commit ships a mismatched contract.
