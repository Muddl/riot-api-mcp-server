# Live-Eval Token Cost Reduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut live-eval token spend from ~$2.60 to ≤$0.35 per dispatch by bounding three oversized MCP tool payloads and scoping the `sse` leg to a transport smoke set, then measure the result against a captured baseline.

**Architecture:** Three MCP tools gain an optional `count` parameter with a capped default; truncation happens in the **application service** (Riot's League-V4 / TFT-League-V1 / Challenges-V1 endpoints have no server-side count param, unlike Champion-Mastery-V4 which truncates at the port). Outbound adapters are untouched. The eval harness gains an explicit smoke-set file because `mcp-eval` has no marker/`-k` selection, plus a committed cost-report script.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring AI 2.0, Lombok, Gradle, JUnit 5, AssertJ, Mockito, WireMock; Python 3.11 + `mcpevals` 0.1.10 (`uv`) for the eval harness; GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-07-23-live-eval-token-cost-design.md`](../specs/2026-07-23-live-eval-token-cost-design.md)

## Global Constraints

- **The offline suite must stay key-free and network-free.** `./gradlew build` is the CI gate. Never add a test needing `RIOT_API_KEY` or network access — use WireMock (adapters) or in-memory port fakes (services).
- **Dependency rule:** servers → `riot-account-core` → `riot-api-core`, never back. Within a module: `adapter → application → domain`, inward only. ArchUnit enforces this.
- **`RestClient` only in `adapter.out.riot`; `@McpTool` only in `adapter.in.mcp`.** Truncation logic goes in `application`, never in an adapter.
- **Tool names are the public MCP contract and do not change.** Existing `@McpToolParam` descriptions do not change. New params and updated `@McpTool` descriptions are permitted and required here.
- **DTO convention:** `@Data @Builder @NoArgsConstructor @AllArgsConstructor` + `@JsonIgnoreProperties(ignoreUnknown = true)`.
- **Default cap is `10`** for all three bounded tools.
- **Run `./gradlew spotlessApply` before every Java commit.** The build fails on formatting.
- **Every commit must leave `./gradlew build` green.**

---

## File Structure

**Java — LoL server (`lol-mcp-server/src/main/java/com/muddl/riot/lol/`)**

| File | Responsibility |
|---|---|
| `league/domain/LeagueList.java` | Gains `totalEntries` + `toBuilder` |
| `league/application/LeagueService.java` | Owns apex truncation + sort |
| `league/adapter/in/mcp/LeagueTool.java` | Exposes `count` param |
| `challenges/domain/ChallengesPlayerData.java` | Gains `totalChallenges` + `toBuilder` |
| `challenges/application/ChallengesService.java` | Owns challenge truncation + sort |
| `challenges/adapter/in/mcp/ChallengesTool.java` | Exposes `count` param |

**Java — TFT server (`tft-mcp-server/src/main/java/com/muddl/riot/tft/`)**

| File | Responsibility |
|---|---|
| `league/domain/LeagueList.java` | Gains `totalEntries` + `toBuilder` |
| `league/application/LeagueService.java` | Apex truncation; `getLeagueById` stamps total only |
| `league/adapter/in/mcp/LeagueTool.java` | Exposes `count` param on apex only |

**Eval harness (`eval/`)**

| File | Responsibility |
|---|---|
| `smoke.txt` (new) | The sse transport smoke set, one `file.py::func` per line |
| `tools/report-cost.py` (new) | Token/cost aggregation from a run's JSON report |
| `tests/test_championmastery.py` | Rubric fix + structural `count` assertion |
| `tests/test_league.py` | Apex rubric retargeted at `totalEntries` |
| `README.md` | Documents `smoke.txt` and the cost report |

**CI + docs**

| File | Responsibility |
|---|---|
| `.github/workflows/live-eval.yml` | Transport-scoped spec selection + scope-aware summary |
| `docs/knowledge/decisions/ADR-0016-bounded-list-results.md` (new) | Capped-default rationale |
| `docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md` (new) | Smoke-set rationale |
| `docs/knowledge/gotchas.md` | Two new entries |
| `docs/knowledge/patterns/live-eval-harness.md` | Smoke set + cost report |
| `.claude/skills/add-live-eval/SKILL.md` | Prompt author about `smoke.txt` |
| `CHANGELOG.md` | Three tool behavior changes |

---

## Task 1: Bound the LoL apex league

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/domain/LeagueList.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/application/LeagueService.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/league/adapter/in/mcp/LeagueTool.java`
- Test: `lol-mcp-server/src/test/java/com/muddl/riot/lol/league/application/LeagueServiceTest.java`
- Test: `lol-mcp-server/src/test/java/com/muddl/riot/lol/league/adapter/in/mcp/LeagueToolTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `LeagueList.getTotalEntries()` returns `int` — pre-truncation entry count.
  - `LeagueService.getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue, Integer count)` returns `LeagueList`.
  - `LeagueTool.getApexLeague(String platformStr, String tierStr, String queueStr, Integer count)` returns `LeagueList`.

**Background for the implementer:** `LeagueItem.getLeaguePoints()` returns a primitive `int`, so `Comparator.comparingInt` is safe with no null handling. `InMemoryLeaguePort.getApexLeague` returns `null` for an unknown key, so the service must guard against a null `LeagueList`.

- [ ] **Step 1: Write the failing tests**

Replace the existing `getApexLeague_delegatesToPort` test in `LeagueServiceTest.java` (it asserts `isSameAs`, which can no longer hold — the service now returns a bounded copy) and add the new cases. Add these imports to the file: `com.muddl.riot.lol.league.domain.LeagueItem` and `java.util.stream.IntStream`.

```java
    private static LeagueList ladderOf(int size) {
        // leaguePoints ascending with index, so the *last* entries are the top ones —
        // a service that slices without sorting will fail these tests.
        return LeagueList.builder()
                .tier("CHALLENGER")
                .queue("RANKED_SOLO_5x5")
                .entries(IntStream.range(0, size)
                        .mapToObj(i -> LeagueItem.builder()
                                .puuid("puuid-" + i)
                                .leaguePoints(i)
                                .build())
                        .toList())
                .build();
    }

    @Test
    void getApexLeague_capsAtTen_whenCountIsNull() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null);

        assertThat(result.getEntries()).hasSize(10);
        assertThat(result.getTotalEntries()).isEqualTo(300);
        assertThat(result.getTier()).isEqualTo("CHALLENGER");
    }

    @Test
    void getApexLeague_sortsByLeaguePointsDescending() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 3);

        assertThat(result.getEntries())
                .extracting(LeagueItem::getLeaguePoints)
                .containsExactly(299, 298, 297);
    }

    @Test
    void getApexLeague_honoursExplicitCount() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        assertThat(leagueService
                        .getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 50)
                        .getEntries())
                .hasSize(50);
    }

    @Test
    void getApexLeague_countExceedingSize_returnsEverything() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(7));

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 5000);

        assertThat(result.getEntries()).hasSize(7);
        assertThat(result.getTotalEntries()).isEqualTo(7);
    }

    @Test
    void getApexLeague_zeroOrNegativeCount_clampsToDefault() {
        leaguePort.putApex(ApexTier.CHALLENGER, "RANKED_SOLO_5x5", ladderOf(300));

        assertThat(leagueService
                        .getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 0)
                        .getEntries())
                .hasSize(10);
        assertThat(leagueService
                        .getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", -4)
                        .getEntries())
                .hasSize(10);
    }

    @Test
    void getApexLeague_nullEntries_yieldsEmptyListAndZeroTotal() {
        leaguePort.putApex(
                ApexTier.CHALLENGER,
                "RANKED_SOLO_5x5",
                LeagueList.builder().tier("CHALLENGER").build());

        LeagueList result = leagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null);

        assertThat(result.getEntries()).isEmpty();
        assertThat(result.getTotalEntries()).isZero();
    }

    @Test
    void getApexLeague_nullLeague_returnsNull() {
        assertThat(leagueService.getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_SOLO_5x5", null))
                .isNull();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :lol-mcp-server:test --tests '*league.application.LeagueServiceTest'`
Expected: FAIL — compilation error, `getApexLeague` cannot be applied to 4 arguments, and `getTotalEntries()` / `LeagueList.builder().totalEntries` do not exist.

- [ ] **Step 3: Add `totalEntries` and `toBuilder` to the domain type**

In `league/domain/LeagueList.java`, change the `@Builder` annotation and add the field:

```java
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueList {
    private String leagueId;
    private String tier;
    private String name;
    private String queue;
    private List<LeagueItem> entries;

    /**
     * Total entries in the league before any {@code count} bound was applied. Lets a caller that
     * received a capped {@link #entries} list still know the real ladder size.
     */
    private int totalEntries;
}
```

- [ ] **Step 4: Implement the bound in the application service**

In `league/application/LeagueService.java`, add the imports `com.muddl.riot.lol.league.domain.LeagueItem` and `java.util.Comparator`, then replace `getApexLeague` and add the helper:

```java
    /** Default number of apex entries returned when the caller does not specify a count. */
    private static final int DEFAULT_APEX_ENTRIES = 10;

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, String queue, Integer count) {
        log.info("Fetching {} apex league for queue {} on platform: {}", tier, queue, platform);
        int limit = (count == null || count <= 0) ? DEFAULT_APEX_ENTRIES : count;
        return boundEntries(leaguePort.getApexLeague(platform, tier, queue), limit);
    }

    /**
     * Returns a copy of {@code league} holding only the top {@code limit} entries by league points,
     * with {@code totalEntries} stamped to the pre-truncation size.
     *
     * <p>Riot's League-V4 apex endpoint has no server-side count parameter (unlike
     * Champion-Mastery-V4, where the bound is pushed down to the port), so the bound is applied
     * here in the application layer. Riot does not guarantee entry order, so entries are sorted
     * before slicing — otherwise "top N" is meaningless and a discovered subject would change
     * between runs. See ADR-0016.
     */
    private static LeagueList boundEntries(LeagueList league, int limit) {
        if (league == null) {
            return null;
        }
        List<LeagueItem> entries = league.getEntries();
        if (entries == null) {
            return league.toBuilder().entries(List.of()).totalEntries(0).build();
        }
        return league.toBuilder()
                .entries(entries.stream()
                        .sorted(Comparator.comparingInt(LeagueItem::getLeaguePoints).reversed())
                        .limit(limit)
                        .toList())
                .totalEntries(entries.size())
                .build();
    }
```

- [ ] **Step 5: Run the service tests to verify they pass**

Run: `./gradlew :lol-mcp-server:test --tests '*league.application.LeagueServiceTest'`
Expected: PASS — 8 tests.

- [ ] **Step 6: Expose the parameter on the MCP tool**

In `league/adapter/in/mcp/LeagueTool.java`, replace the `getApexLeague` method:

```java
    @McpTool(
            name = "lol_league_apex_by_tier",
            description =
                    "Get a League of Legends apex league (CHALLENGER, GRANDMASTER, or MASTER) for a ranked queue. Returns the top 10 entries by league points unless a larger count is requested.")
    public LeagueList getApexLeague(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The apex tier: CHALLENGER, GRANDMASTER, or MASTER", required = true)
                    String tierStr,
            @McpToolParam(
                            description = "The ranked queue, e.g. RANKED_SOLO_5x5 (default) or RANKED_FLEX_SR",
                            required = false)
                    String queueStr,
            @McpToolParam(
                            description = "Optional: return only the top N entries by league points; defaults to 10",
                            required = false)
                    Integer count) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        ApexTier tier = ApexTier.valueOf(tierStr.toUpperCase());
        String queue = (queueStr == null || queueStr.isBlank()) ? DEFAULT_QUEUE : queueStr;
        log.info("MCP Tool - Getting {} apex league for queue {} on platform: {}", tier, queue, platform);
        return leagueService.getApexLeague(platform, tier, queue, count);
    }
```

- [ ] **Step 7: Update the tool tests for the new arity**

In `LeagueToolTest.java`, replace the three apex tests. The tool mocks the service, so `isSameAs` still holds here — only the arity changes, plus one new test proving `count` is passed straight through.

```java
    @Test
    void getApexLeague_defaultsQueue_whenNull() {
        LeagueList league = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", null, null)).isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", null);
    }

    @Test
    void getApexLeague_honoursExplicitQueue() {
        LeagueList league = LeagueList.builder().tier("MASTER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_FLEX_SR", null))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "master", "RANKED_FLEX_SR", null))
                .isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.MASTER, "RANKED_FLEX_SR", null);
    }

    @Test
    void getApexLeague_passesCountThrough() {
        LeagueList league = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 3))
                .thenReturn(league);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", null, 3)).isSameAs(league);
        verify(mockLeagueService).getApexLeague(PLATFORM, ApexTier.CHALLENGER, "RANKED_SOLO_5x5", 3);
    }

    @Test
    void getApexLeague_invalidTier_throws() {
        assertThatThrownBy(() -> leagueTool.getApexLeague("NA1", "DIAMOND", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
```

- [ ] **Step 8: Format, then run the full LoL module build**

Run: `./gradlew spotlessApply && ./gradlew :lol-mcp-server:build`
Expected: BUILD SUCCESSFUL. The WireMock test `RiotLeagueAdapterTest` must pass **unchanged** — the adapter still fetches full payloads. If it needed editing, truncation leaked out of the application layer; stop and fix the design.

- [ ] **Step 9: Commit**

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/league lol-mcp-server/src/test/java/com/muddl/riot/lol/league
git commit -m "feat(lol-league)!: cap lol_league_apex_by_tier at top 10 entries by default

Adds an optional count param and stamps totalEntries with the
pre-truncation size. Truncation lives in LeagueService because Riot's
League-V4 apex endpoint has no server-side count parameter."
```

---

## Task 2: Bound the TFT apex league

**Files:**
- Modify: `tft-mcp-server/src/main/java/com/muddl/riot/tft/league/domain/LeagueList.java`
- Modify: `tft-mcp-server/src/main/java/com/muddl/riot/tft/league/application/LeagueService.java`
- Modify: `tft-mcp-server/src/main/java/com/muddl/riot/tft/league/adapter/in/mcp/LeagueTool.java`
- Test: `tft-mcp-server/src/test/java/com/muddl/riot/tft/league/application/LeagueServiceTest.java`
- Test: `tft-mcp-server/src/test/java/com/muddl/riot/tft/league/adapter/in/mcp/LeagueToolTest.java`

**Interfaces:**
- Consumes: nothing from Task 1 (separate module, separate package root — the two `LeagueList` types are unrelated classes).
- Produces:
  - `LeagueList.getTotalEntries()` returns `int`.
  - `LeagueService.getApexLeague(RiotApiPlatformUri platform, ApexTier tier, Integer count)` returns `LeagueList`.
  - `LeagueService.getLeagueById(RiotApiPlatformUri platform, String leagueId)` — **signature unchanged**, but now returns a copy with `totalEntries` stamped.
  - `LeagueTool.getApexLeague(String platformStr, String tierStr, Integer count)` returns `LeagueList`.

**Background for the implementer:** Two traps here, both different from Task 1.

1. TFT's `LeagueList` is shared by `getApexLeague` and `getLeagueById`. Per the spec, `tft_league_by_id` is **out of scope for truncation** — it must stamp `totalEntries` but neither slice nor reorder its entries. Do not route it through the apex helper.
2. **TFT's `LeagueItem.getLeaguePoints()` returns a boxed `Integer`, not the primitive `int` that LoL's returns.** `Comparator.comparingInt(LeagueItem::getLeaguePoints)` unboxes and throws `NullPointerException` whenever Riot omits the field — passing on hand-built fixtures and failing on live data. The comparator must be null-safe. Do not copy Task 1's helper verbatim.

- [ ] **Step 1: Write the failing tests**

In `tft-mcp-server/.../league/application/LeagueServiceTest.java`, replace `getApexLeague_delegatesToPort` and `getLeagueById_delegatesToPort` (both assert `isSameAs`, which no longer holds) and add the new cases. Add imports `com.muddl.riot.tft.league.domain.LeagueItem`, `java.util.ArrayList`, and `java.util.stream.IntStream` (`java.util.List` is already imported).

```java
    private static LeagueList ladderOf(int size) {
        return LeagueList.builder()
                .tier("CHALLENGER")
                .entries(IntStream.range(0, size)
                        .mapToObj(i -> LeagueItem.builder()
                                .puuid("puuid-" + i)
                                .leaguePoints(i)
                                .build())
                        .toList())
                .build();
    }

    @Test
    void getApexLeague_capsAtTen_whenCountIsNull() {
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        LeagueList result = service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, null);

        assertThat(result.getEntries()).hasSize(10);
        assertThat(result.getTotalEntries()).isEqualTo(300);
    }

    @Test
    void getApexLeague_sortsByLeaguePointsDescending() {
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, 3).getEntries())
                .extracting(LeagueItem::getLeaguePoints)
                .containsExactly(299, 298, 297);
    }

    @Test
    void getApexLeague_honoursExplicitCount() {
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, 50).getEntries())
                .hasSize(50);
    }

    @Test
    void getApexLeague_zeroOrNegativeCount_clampsToDefault() {
        port.putApex(ApexTier.CHALLENGER, ladderOf(300));

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, 0).getEntries())
                .hasSize(10);
        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, -1).getEntries())
                .hasSize(10);
    }

    @Test
    void getApexLeague_nullLeaguePoints_sortLastWithoutThrowing() {
        // TFT's LeagueItem.leaguePoints is a boxed Integer and Riot may omit it —
        // a comparingInt comparator would NPE here.
        List<LeagueItem> mixed = new ArrayList<>();
        mixed.add(LeagueItem.builder().puuid("no-lp").leaguePoints(null).build());
        mixed.add(LeagueItem.builder().puuid("low").leaguePoints(100).build());
        mixed.add(LeagueItem.builder().puuid("high").leaguePoints(900).build());
        port.putApex(
                ApexTier.CHALLENGER,
                LeagueList.builder().tier("CHALLENGER").entries(mixed).build());

        assertThat(service.getApexLeague(PLATFORM, ApexTier.CHALLENGER, null).getEntries())
                .extracting(LeagueItem::getPuuid)
                .containsExactly("high", "low", "no-lp");
    }

    @Test
    void getApexLeague_nullLeague_returnsNull() {
        assertThat(service.getApexLeague(PLATFORM, ApexTier.MASTER, null)).isNull();
    }

    @Test
    void getLeagueById_stampsTotal_withoutTruncatingOrReordering() {
        port.putLeague("league-uuid", ladderOf(300));

        LeagueList result = service.getLeagueById(PLATFORM, "league-uuid");

        assertThat(result.getEntries()).hasSize(300);
        assertThat(result.getTotalEntries()).isEqualTo(300);
        // Original ascending order is preserved — league-by-id is out of scope for the bound.
        assertThat(result.getEntries().get(0).getLeaguePoints()).isZero();
    }

    @Test
    void getLeagueById_nullLeague_returnsNull() {
        assertThat(service.getLeagueById(PLATFORM, "missing")).isNull();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :tft-mcp-server:test --tests '*league.application.LeagueServiceTest'`
Expected: FAIL — compilation error, `getApexLeague` cannot be applied to 3 arguments, `getTotalEntries()` does not exist.

- [ ] **Step 3: Add `totalEntries` and `toBuilder` to the domain type**

In `tft-mcp-server/.../league/domain/LeagueList.java`:

```java
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueList {
    private String leagueId;
    private String tier;
    private String name;
    private String queue;
    private List<LeagueItem> entries;

    /**
     * Total entries in the league before any {@code count} bound was applied. Lets a caller that
     * received a capped {@link #entries} list still know the real ladder size.
     */
    private int totalEntries;
}
```

- [ ] **Step 4: Implement the bound in the application service**

In `tft-mcp-server/.../league/application/LeagueService.java`, add imports `com.muddl.riot.tft.league.domain.LeagueItem` and `java.util.Comparator`, then replace `getApexLeague` and `getLeagueById` and add the helper:

```java
    /** Default number of apex entries returned when the caller does not specify a count. */
    private static final int DEFAULT_APEX_ENTRIES = 10;

    public LeagueList getApexLeague(RiotApiPlatformUri platform, ApexTier tier, Integer count) {
        log.info("Fetching TFT {} apex league on platform: {}", tier, platform);
        int limit = (count == null || count <= 0) ? DEFAULT_APEX_ENTRIES : count;
        return boundEntries(leaguePort.getApexLeague(platform, tier), limit);
    }

    public LeagueList getLeagueById(RiotApiPlatformUri platform, String leagueId) {
        log.info("Fetching TFT league by id on platform: {}", platform);
        LeagueList league = leaguePort.getLeagueById(platform, leagueId);
        if (league == null) {
            return null;
        }
        // Stamp the total for consistency with the apex response, but neither slice nor reorder:
        // tft_league_by_id is deliberately out of scope for the bound (ADR-0016).
        return league.toBuilder()
                .totalEntries(league.getEntries() == null ? 0 : league.getEntries().size())
                .build();
    }

    /**
     * Returns a copy of {@code league} holding only the top {@code limit} entries by league points,
     * with {@code totalEntries} stamped to the pre-truncation size. Riot's TFT-League-V1 apex
     * endpoint has no server-side count parameter, so the bound is applied here in the application
     * layer; entries are sorted first because Riot does not guarantee order. See ADR-0016.
     *
     * <p>Unlike the LoL server's equivalent, {@code leaguePoints} here is a boxed {@link Integer}
     * and Riot may omit it, so the comparator is null-safe (nulls sort last) rather than a
     * {@code comparingInt}, which would unbox and throw.
     */
    private static LeagueList boundEntries(LeagueList league, int limit) {
        if (league == null) {
            return null;
        }
        List<LeagueItem> entries = league.getEntries();
        if (entries == null) {
            return league.toBuilder().entries(List.of()).totalEntries(0).build();
        }
        return league.toBuilder()
                .entries(entries.stream()
                        .sorted(Comparator.comparing(
                                LeagueItem::getLeaguePoints,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(limit)
                        .toList())
                .totalEntries(entries.size())
                .build();
    }
```

- [ ] **Step 5: Run the service tests to verify they pass**

Run: `./gradlew :tft-mcp-server:test --tests '*league.application.LeagueServiceTest'`
Expected: PASS.

- [ ] **Step 6: Expose the parameter on the MCP tool**

In `tft-mcp-server/.../league/adapter/in/mcp/LeagueTool.java`, replace `getApexLeague`:

```java
    @McpTool(
            name = "tft_league_apex_by_tier",
            description =
                    "Get a Teamfight Tactics apex league: CHALLENGER, GRANDMASTER, or MASTER. Returns the top 10 entries by league points unless a larger count is requested.")
    public LeagueList getApexLeague(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The apex tier: CHALLENGER, GRANDMASTER, or MASTER", required = true)
                    String tierStr,
            @McpToolParam(
                            description = "Optional: return only the top N entries by league points; defaults to 10",
                            required = false)
                    Integer count) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        ApexTier tier = ApexTier.valueOf(tierStr.toUpperCase());
        log.info("MCP Tool - Getting TFT {} apex league on platform: {}", tier, platform);
        return leagueService.getApexLeague(platform, tier, count);
    }
```

- [ ] **Step 7: Update the tool tests for the new arity**

In `tft-mcp-server/.../league/adapter/in/mcp/LeagueToolTest.java`, replace the four apex tests. Leave the `getLeagueById` tests alone — that service signature is unchanged.

```java
    @Test
    void getApexLeague_passesPlatformAndTierThrough() {
        LeagueList list = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, null))
                .thenReturn(list);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", null)).isSameAs(list);
        verify(mockLeagueService).getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, null);
    }

    @Test
    void getApexLeague_normalizesCaseForPlatformAndTier() {
        LeagueList list = LeagueList.builder().tier("MASTER").build();
        when(mockLeagueService.getApexLeague(RiotApiPlatformUri.NA1, ApexTier.MASTER, null))
                .thenReturn(list);

        assertThat(leagueTool.getApexLeague("na1", "master", null)).isSameAs(list);
        verify(mockLeagueService).getApexLeague(RiotApiPlatformUri.NA1, ApexTier.MASTER, null);
    }

    @Test
    void getApexLeague_passesCountThrough() {
        LeagueList list = LeagueList.builder().tier("CHALLENGER").build();
        when(mockLeagueService.getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, 3))
                .thenReturn(list);

        assertThat(leagueTool.getApexLeague("NA1", "CHALLENGER", 3)).isSameAs(list);
        verify(mockLeagueService).getApexLeague(RiotApiPlatformUri.NA1, ApexTier.CHALLENGER, 3);
    }

    @Test
    void getApexLeague_invalidPlatform_throws() {
        assertThatThrownBy(() -> leagueTool.getApexLeague("INVALID", "CHALLENGER", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void getApexLeague_invalidTier_throws() {
        assertThatThrownBy(() -> leagueTool.getApexLeague("NA1", "BRONZE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
```

- [ ] **Step 8: Format, then run the full TFT module build**

Run: `./gradlew spotlessApply && ./gradlew :tft-mcp-server:build`
Expected: BUILD SUCCESSFUL. `RiotTftLeagueAdapterTest` must pass unchanged.

- [ ] **Step 9: Commit**

```bash
git add tft-mcp-server/src/main/java/com/muddl/riot/tft/league tft-mcp-server/src/test/java/com/muddl/riot/tft/league
git commit -m "feat(tft-league)!: cap tft_league_apex_by_tier at top 10 entries by default

Mirrors the LoL change. tft_league_by_id stamps totalEntries but is
deliberately left unbounded and unsorted."
```

---

## Task 3: Bound the LoL challenges payload

**Files:**
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/domain/ChallengesPlayerData.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/application/ChallengesService.java`
- Modify: `lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges/adapter/in/mcp/ChallengesTool.java`
- Test: `lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges/application/ChallengesServiceTest.java`
- Test: `lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges/adapter/in/mcp/ChallengesToolTest.java`

**Interfaces:**
- Consumes: nothing from Tasks 1–2.
- Produces:
  - `ChallengesPlayerData.getTotalChallenges()` returns `int`.
  - `ChallengesService.getChallengesByPlayer(RiotApiPlatformUri platform, String player, Integer count)` returns `ChallengesPlayerData`.
  - `ChallengesTool.getChallengesByPlayer(String platformStr, String player, Integer count)` returns `ChallengesPlayerData`.

**Background for the implementer:** `ChallengeProgress.getPercentile()` returns a **boxed `Double` that Riot leaves null** on challenges a player has not progressed on. A naive `Comparator.comparing(ChallengeProgress::getPercentile)` throws `NullPointerException` on live data while passing on hand-built fixtures — the comparator must be null-safe. Lower percentile means a rarer achievement, so the sort is **ascending** with nulls last.

- [ ] **Step 1: Write the failing tests**

Replace the existing `getChallengesByPlayer_resolvesPlayer_thenReturnsData` test (it asserts `isSameAs`, no longer true) and add the new cases in `ChallengesServiceTest.java`. Add imports `com.muddl.riot.lol.challenges.domain.ChallengeProgress`, `java.util.ArrayList`, `java.util.List`, `java.util.stream.IntStream`.

```java
    private static ChallengesPlayerData dataWith(List<ChallengeProgress> challenges) {
        return ChallengesPlayerData.builder()
                .totalPoints(ChallengePoints.builder().level("DIAMOND").build())
                .challenges(challenges)
                .build();
    }

    private static List<ChallengeProgress> progressOf(int size) {
        // percentile descending with index, so the *last* entries are the best —
        // a service that slices without sorting will fail these tests.
        return IntStream.range(0, size)
                .mapToObj(i -> ChallengeProgress.builder()
                        .challengeId((long) i)
                        .percentile(1.0 - (i / (double) size))
                        .build())
                .toList();
    }

    @Test
    void getChallengesByPlayer_capsAtTen_andKeepsSummary() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        port.put("faker-puuid", dataWith(progressOf(500)));

        ChallengesPlayerData result = service.getChallengesByPlayer(PLATFORM, "Faker#KR1", null);

        assertThat(result.getChallenges()).hasSize(10);
        assertThat(result.getTotalChallenges()).isEqualTo(500);
        assertThat(result.getTotalPoints().getLevel()).isEqualTo("DIAMOND");
    }

    @Test
    void getChallengesByPlayer_sortsByPercentileAscending() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", dataWith(progressOf(500)));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", 3).getChallenges())
                .extracting(ChallengeProgress::getChallengeId)
                .containsExactly(499L, 498L, 497L);
    }

    @Test
    void getChallengesByPlayer_honoursExplicitCount() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", dataWith(progressOf(500)));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", 42).getChallenges())
                .hasSize(42);
    }

    @Test
    void getChallengesByPlayer_zeroOrNegativeCount_clampsToDefault() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", dataWith(progressOf(500)));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", 0).getChallenges())
                .hasSize(10);
        assertThat(service.getChallengesByPlayer(PLATFORM, "p", -9).getChallenges())
                .hasSize(10);
    }

    @Test
    void getChallengesByPlayer_nullPercentiles_sortLastWithoutThrowing() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        List<ChallengeProgress> mixed = new ArrayList<>();
        mixed.add(ChallengeProgress.builder().challengeId(1L).percentile(null).build());
        mixed.add(ChallengeProgress.builder().challengeId(2L).percentile(0.5).build());
        mixed.add(ChallengeProgress.builder().challengeId(3L).percentile(0.01).build());
        port.put("p", dataWith(mixed));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", null).getChallenges())
                .extracting(ChallengeProgress::getChallengeId)
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void getChallengesByPlayer_nullChallenges_yieldsEmptyListAndZeroTotal() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", ChallengesPlayerData.builder().build());

        ChallengesPlayerData result = service.getChallengesByPlayer(PLATFORM, "p", null);

        assertThat(result.getChallenges()).isEmpty();
        assertThat(result.getTotalChallenges()).isZero();
    }

    @Test
    void getChallengesByPlayer_nullData_returnsNull() {
        when(resolver.resolvePuuid("unknown")).thenReturn("unknown");

        assertThat(service.getChallengesByPlayer(PLATFORM, "unknown", null)).isNull();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :lol-mcp-server:test --tests '*challenges.application.ChallengesServiceTest'`
Expected: FAIL — compilation error, `getChallengesByPlayer` cannot be applied to 3 arguments, `getTotalChallenges()` does not exist.

- [ ] **Step 3: Add `totalChallenges` and `toBuilder` to the domain type**

In `challenges/domain/ChallengesPlayerData.java`:

```java
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengesPlayerData {
    private ChallengePoints totalPoints;
    private Map<String, ChallengePoints> categoryPoints;
    private List<ChallengeProgress> challenges;

    /**
     * Total challenges returned by Riot before any {@code count} bound was applied. Lets a caller
     * that received a capped {@link #challenges} list know how many exist.
     */
    private int totalChallenges;
}
```

- [ ] **Step 4: Implement the bound in the application service**

In `challenges/application/ChallengesService.java`, add imports `com.muddl.riot.lol.challenges.domain.ChallengeProgress`, `java.util.Comparator`, `java.util.List`, then replace the method and add the helper:

```java
    /** Default number of individual challenges returned when the caller does not specify a count. */
    private static final int DEFAULT_CHALLENGES = 10;

    public ChallengesPlayerData getChallengesByPlayer(
            RiotApiPlatformUri platform, String player, Integer count) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching challenge data on platform: {}", platform);
        int limit = (count == null || count <= 0) ? DEFAULT_CHALLENGES : count;
        return boundChallenges(challengesPort.getPlayerDataByPuuid(platform, puuid), limit);
    }

    /**
     * Returns a copy of {@code data} holding only the {@code limit} strongest challenges, with
     * {@code totalChallenges} stamped to the pre-truncation size. {@code totalPoints} and
     * {@code categoryPoints} are always returned in full — they are the summary and cost almost
     * nothing, while Riot's per-challenge array runs to ~500 rows and is ~99% of the payload.
     *
     * <p>Sorted by percentile <em>ascending</em>, because a lower percentile is a rarer
     * achievement. The comparator must tolerate nulls: Riot omits {@code percentile} on challenges
     * a player has not progressed on. See ADR-0016.
     */
    private static ChallengesPlayerData boundChallenges(ChallengesPlayerData data, int limit) {
        if (data == null) {
            return null;
        }
        List<ChallengeProgress> challenges = data.getChallenges();
        if (challenges == null) {
            return data.toBuilder().challenges(List.of()).totalChallenges(0).build();
        }
        return data.toBuilder()
                .challenges(challenges.stream()
                        .sorted(Comparator.comparing(
                                ChallengeProgress::getPercentile,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(limit)
                        .toList())
                .totalChallenges(challenges.size())
                .build();
    }
```

- [ ] **Step 5: Run the service tests to verify they pass**

Run: `./gradlew :lol-mcp-server:test --tests '*challenges.application.ChallengesServiceTest'`
Expected: PASS.

- [ ] **Step 6: Expose the parameter on the MCP tool**

In `challenges/adapter/in/mcp/ChallengesTool.java`, replace the method:

```java
    @McpTool(
            name = "lol_challenges_by_player",
            description =
                    "Get a League of Legends player's challenge standing: total and per-category points, and per-challenge progress. Per-challenge progress returns the 10 strongest challenges unless a larger count is requested; totals and category points are always complete.")
    public ChallengesPlayerData getChallengesByPlayer(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The player as a Riot ID (GameName#TAG) or a raw PUUID", required = true)
                    String player,
            @McpToolParam(
                            description =
                                    "Optional: return only the top N individual challenges by percentile; defaults to 10",
                            required = false)
                    Integer count) {
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        log.info("MCP Tool - Getting challenge data for a player on platform: {}", platform);
        return challengesService.getChallengesByPlayer(platform, player, count);
    }
```

- [ ] **Step 7: Update the tool tests for the new arity**

Replace both tests in `ChallengesToolTest.java`:

```java
    @Test
    void getChallengesByPlayer_passesPlatformAndPlayerThrough() {
        ChallengesPlayerData data = ChallengesPlayerData.builder().build();
        when(mockService.getChallengesByPlayer(PLATFORM, "Faker#KR1", null)).thenReturn(data);

        assertThat(tool.getChallengesByPlayer("NA1", "Faker#KR1", null)).isSameAs(data);
        verify(mockService).getChallengesByPlayer(PLATFORM, "Faker#KR1", null);
    }

    @Test
    void getChallengesByPlayer_passesCountThrough() {
        ChallengesPlayerData data = ChallengesPlayerData.builder().build();
        when(mockService.getChallengesByPlayer(PLATFORM, "Faker#KR1", 5)).thenReturn(data);

        assertThat(tool.getChallengesByPlayer("NA1", "Faker#KR1", 5)).isSameAs(data);
        verify(mockService).getChallengesByPlayer(PLATFORM, "Faker#KR1", 5);
    }

    @Test
    void getChallengesByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> tool.getChallengesByPlayer("INVALID", "Faker#KR1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
```

- [ ] **Step 8: Format and run the whole build**

Run: `./gradlew spotlessApply && ./gradlew build`
Expected: BUILD SUCCESSFUL across all modules — ArchUnit, JaCoCo, and Spotless included. This is the first point where the full gate runs after all three Java changes.

- [ ] **Step 9: Commit**

```bash
git add lol-mcp-server/src/main/java/com/muddl/riot/lol/challenges lol-mcp-server/src/test/java/com/muddl/riot/lol/challenges
git commit -m "feat(lol-challenges)!: cap lol_challenges_by_player per-challenge list at 10

Totals and category points always return in full; the ~500-row
per-challenge array is bounded and sorted by percentile ascending with
a null-safe comparator (Riot omits percentile on unprogressed challenges)."
```

---

## Task 4: Fix the eval rubrics

**Files:**
- Modify: `eval/tests/test_championmastery.py`
- Modify: `eval/tests/test_league.py:7-32` (the `test_apex_challenger` task)

**Interfaces:**
- Consumes: `LeagueList.totalEntries` from Task 1 (serialized as `totalEntries` in the tool's JSON response).
- Produces: nothing consumed by later tasks.

**Background for the implementer:** `test_champion_mastery_for_discovered_player` failed on `stdio` and passed on `sse` in the baseline run. The cause is a **prompt/rubric mismatch, not flakiness**: the prompt asks for *"the top champion and its mastery point value"* (singular), while the rubric grades *"at most 3 champion mastery entries"*. The agent obeyed the prompt, reported one champion, and the judge marked it down. The `count` assertion belongs in a deterministic structural check.

`Expect.tools.called_with(tool_name, arguments)` does **subset** argument matching — it checks `all(call.arguments.get(k) == v for k, v in expected.items())` — so only `count` needs to be named. It costs zero judge tokens.

- [ ] **Step 1: Rewrite the champion mastery task**

Replace the whole body of `eval/tests/test_championmastery.py`:

```python
"""Champion mastery live eval. The subject is discovered from the CHALLENGER
ladder, exercising the player-keyed path plus the optional top-N count param.

The count param is asserted structurally, not through the judge: the prompt asks
for a single champion, so a rubric that graded "reports 3 entries" would penalise
the agent for obeying the prompt. See gotchas.md."""

from mcp_eval import task, Expect


@task("Champion mastery resolves for a discovered player")
async def test_champion_mastery_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any "
        "one player, then get that player's champion mastery on NA1, limited "
        "to their top 3 champions by mastery points. Report the top champion "
        "and its mastery point value."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_champion_mastery_by_player"),
        name="champion_mastery_called",
    )
    # Structural, not judged: proves the top-N count param reached the tool.
    await session.assert_that(
        Expect.tools.called_with("lol_champion_mastery_by_player", {"count": 3}),
        name="champion_mastery_count_passed",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer names the top champion by mastery points and gives "
                "its mastery point value, which must be non-negative. If the "
                "player has no mastery data, it clearly says so instead. It "
                "must not report a tool error. Reporting only the single top "
                "champion is correct and expected — do not require additional "
                "champions to be listed."
            ),
            min_score=0.7,
        ),
        response=response,
        name="champion_mastery_reported",
    )
```

- [ ] **Step 2: Retarget the apex rubric at `totalEntries`**

In `eval/tests/test_league.py`, replace the `test_apex_challenger` task. Without this the task silently starts asserting on a 10-entry truncated view instead of the real ladder.

```python
@task("Apex league returns a populated CHALLENGER ladder")
async def test_apex_challenger(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for the RANKED_SOLO_5x5 queue on NA1. "
        "The tool caps the entries it returns, so report the total number of "
        "players in the league (the totalEntries field), and name one player "
        "from the entries you received."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_apex_by_tier"),
        name="apex_tool_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a CHALLENGER league whose total size is well "
                "above the number of entries actually returned — a real ladder has "
                "on the order of hundreds of players, so a reported total of only "
                "about ten means the truncated list was mistaken for the whole "
                "league and must fail. It also identifies at least one entry. A "
                "PUUID, summoner id, or LP/rank is sufficient identification — Riot "
                "no longer exposes human-readable summoner names in league entries, "
                "so do not require a display name. It does not report an error or "
                "an empty league."
            ),
            min_score=0.7,
        ),
        response=response,
        name="apex_reports_true_ladder_size",
    )
```

- [ ] **Step 3: Verify the files parse**

Run: `cd eval && uv run python -c "import ast,pathlib; [ast.parse(p.read_text(encoding='utf-8')) for p in pathlib.Path('tests').glob('test_*.py')]; print('OK')"`
Expected: `OK`

These tasks only truly execute against live servers with real keys, which happens in Task 8. This step catches syntax errors early without spending tokens.

- [ ] **Step 4: Commit**

```bash
git add eval/tests/test_championmastery.py eval/tests/test_league.py
git commit -m "fix(eval): assert mastery count structurally; retarget apex rubric at totalEntries

The mastery rubric graded 'reports at most 3 entries' while the prompt
asked for one champion, so the judge penalised correct behaviour. Moves
that assertion to Expect.tools.called_with and narrows the rubric.

The apex rubric would otherwise silently assert on the newly truncated
entry list rather than the real ladder size."
```

---

## Task 5: Transport-scoped eval coverage

**Files:**
- Create: `eval/smoke.txt`
- Modify: `.github/workflows/live-eval.yml` (the `Run live evals` and `Summarize outcome` steps)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `eval/smoke.txt` — newline-delimited `file.py::func` specs, `#` comments and blank lines ignored. Task 7 documents it.

**Background for the implementer:** `mcp-eval run` accepts multiple positional path specs including pytest-style `file.py::func`, but has **no `-k`, no markers, and no tag system** — verified against `mcp_eval/runner.py` in the pinned 0.1.10 release. The subset therefore has to be explicit paths. GitHub Actions `run:` blocks on `ubuntu-latest` use bash, so `mapfile` is available.

- [ ] **Step 1: Create the smoke set**

Create `eval/smoke.txt`:

```
# Transport smoke set — run on the sse leg instead of the full suite.
#
# These four tasks prove the *transport wiring*, not the tool logic: the stdio
# leg runs the full suite against the same jars, so anything below the transport
# layer is already covered there. Keep this list small — every entry is paid for
# on every dispatch. See ADR-0017.
#
# One "file.py::function" spec per line; blank lines and # comments are ignored.

# sse handshake and tool discovery
tests/test_handshake.py::test_lists_tools

# a tool round-trip on each server (the two run on different ports)
tests/test_status.py::test_status_platform
tests/test_tft_status.py::test_tft_status_platform

# errors must surface through the transport — Spring AI's MCP layer only reports
# a generic "Error invoking method" over sse, so this is the leg's weak spot
tests/test_canaries.py::test_invalid_platform
```

- [ ] **Step 2: Verify every spec in the smoke set resolves**

Run:
```bash
cd eval && grep -vE '^\s*(#|$)' smoke.txt | while IFS= read -r spec; do
  f="${spec%%::*}"; fn="${spec##*::}"
  test -f "$f" || { echo "MISSING FILE: $f"; exit 1; }
  grep -q "def ${fn}(" "$f" || { echo "MISSING FUNC: $spec"; exit 1; }
  echo "ok: $spec"
done
```
Expected: four `ok:` lines and no `MISSING` output.

- [ ] **Step 3: Scope the workflow's run step by transport**

In `.github/workflows/live-eval.yml`, replace the `run:` body of the `Run live evals` step:

```yaml
        run: |
          # mcp-eval does NOT expand ${VAR} placeholders in the config's
          # command/args/url/env fields, so we expand them here before it reads
          # the file. Restricted to our vars so nothing else is touched.
          envsubst '${LOL_MCP_JAR} ${TFT_MCP_JAR} ${LOL_DEV_API_KEY} ${TFT_DEV_API_KEY} ${LOL_MCP_SSE_URL} ${TFT_MCP_SSE_URL}' \
            < "mcpeval.${{ matrix.transport }}.yaml" > mcpeval.yaml
          # stdio carries the full suite; sse runs only the transport smoke set,
          # since both legs otherwise pay for an identical result (ADR-0017).
          # mcp-eval has no -k/marker selection, so the subset is explicit paths.
          if [ "${{ matrix.transport }}" = "sse" ]; then
            mapfile -t SPECS < <(grep -vE '^\s*(#|$)' smoke.txt)
            echo "EVAL_SCOPE=transport smoke set (${#SPECS[@]} tasks)" >> "$GITHUB_ENV"
          else
            SPECS=(tests/)
            echo "EVAL_SCOPE=full suite" >> "$GITHUB_ENV"
          fi
          set +e
          uv run mcp-eval run "${SPECS[@]}" -v \
            --json "test-reports/${{ matrix.transport }}.json" \
            --html "test-reports/${{ matrix.transport }}.html" \
            --markdown "test-reports/${{ matrix.transport }}.md"
          echo "EVAL_EXIT=$?" >> "$GITHUB_ENV"
          set -e
```

- [ ] **Step 4: Make the job summary scope-aware**

Still in `live-eval.yml`, replace the `run:` body of the `Summarize outcome` step. Without this a green `sse` leg reads as full coverage.

```yaml
        run: |
          {
            echo "## Live eval — ${{ matrix.transport }} (${EVAL_SCOPE:-unknown scope})"
            if [ "${EVAL_EXIT:-1}" = "0" ]; then
              echo "✅ All live evals passed."
            else
              echo "❌ Live evals reported failures (exit ${EVAL_EXIT})."
              echo ""
              echo "Triage: a **CANARY** failure means Riot's live behavior may have changed — investigate the adapter's assumption before editing the test. A non-canary failure is a regression. Rate-limit / network / Riot 5xx errors are infra flakes — re-run the workflow."
            fi
            if [ "${{ matrix.transport }}" = "sse" ]; then
              echo ""
              echo "> This leg runs the **transport smoke set** (\`eval/smoke.txt\`), not the full suite. Tool-logic coverage comes from the stdio leg."
            fi
            echo ""
            echo "See the uploaded \`mcpeval-reports-${{ matrix.transport }}\` artifact (Markdown/HTML/JSON) for per-test detail."
          } >> "$GITHUB_STEP_SUMMARY"
```

- [ ] **Step 5: Verify the workflow is valid YAML**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/live-eval.yml',encoding='utf-8')); print('OK')"`
Expected: `OK`

- [ ] **Step 6: Commit**

```bash
git add eval/smoke.txt .github/workflows/live-eval.yml
git commit -m "ci(eval): run the full suite on stdio, a transport smoke set on sse

Both legs previously ran identical tasks for an identical result. The
sse leg now runs four tasks covering handshake, a round-trip per server,
and error propagation. The job summary names the scope so a green sse
leg is not mistaken for full coverage."
```

---

## Task 6: Cost report script

**Files:**
- Create: `eval/tools/report-cost.py`

**Interfaces:**
- Consumes: an mcp-eval JSON report (`eval/test-reports/<transport>.json`), whose shape is `{"decorator_tests": [{"test_name": str, "passed": bool, "metrics": {"llm_metrics": {"input_tokens": int, "output_tokens": int}, "tool_calls": [{"name": str, "result": {...}}], "iteration_count": int}}, ...]}`.
- Produces: a CLI used by Task 8 — `uv run python tools/report-cost.py <report.json>`, printing per-test and per-tool tables and a total, exiting 0.

**Background for the implementer:** **Do not use the report's own `cost_estimate` field.** It prices at a ~$0.50/MTok fallback rate, understating Haiku 4.5 by roughly 2×, and it excludes LLM-judge calls entirely. Price from raw token counts at the real rates instead.

- [ ] **Step 1: Write the script**

Create `eval/tools/report-cost.py`:

```python
#!/usr/bin/env python3
"""Summarise the token spend of a live-eval run.

Reads an mcp-eval JSON report and prints per-test and per-tool token tables plus
a priced total.

Deliberately ignores the report's own ``cost_estimate`` field: it prices at a
~$0.50/MTok fallback rate (understating Claude Haiku 4.5 by roughly 2x) and
excludes LLM-judge calls entirely. We price from raw token counts instead, so
the number moves for the same reason the bill does.

Usage:
    cd eval && uv run python tools/report-cost.py test-reports/stdio.json
    cd eval && uv run python tools/report-cost.py test-reports/stdio.json test-reports/sse.json
"""

from __future__ import annotations

import json
import sys
from collections import defaultdict
from pathlib import Path

# Claude Haiku 4.5 list rates, USD per million tokens.
INPUT_PER_MTOK = 1.00
OUTPUT_PER_MTOK = 5.00


def price(input_tokens: int, output_tokens: int) -> float:
    """Cost in USD of the given token counts at Haiku 4.5 rates."""
    return (input_tokens * INPUT_PER_MTOK + output_tokens * OUTPUT_PER_MTOK) / 1_000_000


def token_counts(test: dict) -> tuple[int, int]:
    """Input and output tokens for one test, tolerating both metric layouts."""
    metrics = test.get("metrics") or {}
    llm = metrics.get("llm_metrics") or {}
    return (
        llm.get("input_tokens", metrics.get("input_tokens", 0)) or 0,
        llm.get("output_tokens", metrics.get("output_tokens", 0)) or 0,
    )


def report(path: Path) -> tuple[int, int]:
    """Print the tables for one report file; return its (input, output) totals."""
    tests = json.loads(path.read_text(encoding="utf-8")).get("decorator_tests", [])
    if not tests:
        print(f"{path}: no decorator_tests found", file=sys.stderr)
        return 0, 0

    rows = []
    tool_sizes: dict[str, list[int]] = defaultdict(list)
    total_in = total_out = 0

    for test in tests:
        tokens_in, tokens_out = token_counts(test)
        total_in += tokens_in
        total_out += tokens_out
        metrics = test.get("metrics") or {}
        calls = metrics.get("tool_calls") or []
        rows.append(
            (
                price(tokens_in, tokens_out),
                tokens_in,
                tokens_out,
                len(calls),
                metrics.get("iteration_count"),
                bool(test.get("passed")),
                test.get("test_name", "?"),
            )
        )
        for call in calls:
            # Approximate the emitted result size in tokens. ~4 chars per token is
            # close enough to rank tools by payload weight.
            tool_sizes[call.get("name", "?")].append(len(json.dumps(call.get("result", {}))) // 4)

    print(f"\n=== {path.name} ===")
    print(
        f"{len(tests)} tasks | in {total_in:,} | out {total_out:,} "
        f"| ${price(total_in, total_out):.4f} @ Haiku 4.5 rates"
    )

    print(f"\n{'cost':>9}  {'in':>9}  {'out':>6}  {'calls':>5}  {'iter':>4}  ok  test")
    for cost, tokens_in, tokens_out, calls, iterations, passed, name in sorted(rows, reverse=True):
        print(
            f"${cost:>8.4f}  {tokens_in:>9,}  {tokens_out:>6,}  {calls:>5}  "
            f"{str(iterations or '-'):>4}  {'y' if passed else 'N':>2}  {name}"
        )

    if tool_sizes:
        print(f"\n{'tool':<42} {'n':>3}  {'avg tok':>8}  {'total tok':>10}")
        for name, sizes in sorted(tool_sizes.items(), key=lambda kv: -sum(kv[1])):
            print(f"{name:<42} {len(sizes):>3}  {sum(sizes) // len(sizes):>8,}  {sum(sizes):>10,}")

    return total_in, total_out


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2

    grand_in = grand_out = 0
    reported = 0
    for arg in argv[1:]:
        path = Path(arg)
        if not path.is_file():
            print(f"skipping missing file: {path}", file=sys.stderr)
            continue
        tokens_in, tokens_out = report(path)
        grand_in += tokens_in
        grand_out += tokens_out
        reported += 1

    if reported > 1:
        print("\n=== combined ===")
        print(
            f"in {grand_in:,} | out {grand_out:,} "
            f"| ${price(grand_in, grand_out):.4f} @ Haiku 4.5 rates"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
```

- [ ] **Step 2: Verify it runs against the captured baseline**

The baseline artifacts may not be on disk. Fetch them, then run the script:

```bash
cd eval
gh run download 30011353019 -R Muddl/riot-api-mcp-server -D /tmp/baseline
uv run python tools/report-cost.py /tmp/baseline/mcpeval-reports-stdio/eval/test-reports/stdio.json
```

Expected: a table headed `24 tasks | in 1,250,624 | out 10,246 | $1.3019 @ Haiku 4.5 rates`, with `test_match_detail_for_discovered_match` at the top and `lol_league_apex_by_tier` ranked first in the tool table at ~17,900 average tokens. If the totals do not match those numbers exactly, the script is reading the wrong field — fix it before continuing.

- [ ] **Step 3: Commit**

```bash
git add eval/tools/report-cost.py
git commit -m "feat(eval): add report-cost.py for token/cost aggregation

Prices from raw token counts at real Haiku 4.5 rates. The report's own
cost_estimate field uses a ~\$0.50/MTok fallback and omits judge calls."
```

---

## Task 7: Documentation and knowledge persistence

**Files:**
- Create: `docs/knowledge/decisions/ADR-0016-bounded-list-results.md`
- Create: `docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md`
- Modify: `docs/knowledge/gotchas.md`
- Modify: `docs/knowledge/patterns/live-eval-harness.md`
- Modify: `docs/knowledge/README.md` (the ADR index)
- Modify: `eval/README.md`
- Modify: `.claude/skills/add-live-eval/SKILL.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: nothing consumed by later tasks.

**Background for the implementer:** Read `docs/knowledge/README.md` first for the ADR template and index format, and match the heading structure of an existing ADR (e.g. `ADR-0014-non-player-keyed-tools.md`). `ADR-0015` is the highest existing number.

- [ ] **Step 1: Write ADR-0016**

Create `docs/knowledge/decisions/ADR-0016-bounded-list-results.md`, following the existing ADR template. It must record:

- **Context:** `lol_league_apex_by_tier` returned the entire ~300-entry CHALLENGER ladder — ~17,900 tokens per call. Because it lands on turn 1 of a 3–4 iteration agent chain and is re-sent every iteration, it accounted for ~58% of all live-eval input tokens (measured: run `30011353019`, 1,250,624 input tokens per transport). `lol_challenges_by_player` had the same shape: a ~150-token useful head plus a ~500-row array that was ~99% of the payload.
- **Decision:** an optional `count` param on `lol_league_apex_by_tier`, `tft_league_apex_by_tier`, and `lol_challenges_by_player`, with a **capped default of 10**. Pre-truncation sizes exposed as `totalEntries` / `totalChallenges`.
- **Why a capped default rather than an opt-in param:** an opt-in leaves the naive call — the one a real agent makes — at 18k tokens, and would have forced every eval prompt to be rewritten. Capping the default fixed the cost with zero eval-prompt churn.
- **Why the application layer:** Riot's League-V4, TFT-League-V1, and Challenges-V1 endpoints have **no server-side count parameter**, unlike Champion-Mastery-V4, where `ChampionMasteryService` pushes `count` down to the port. Same MCP-level contract, two implementation layers, chosen by whether Riot supports it upstream. Adapters stay dumb mappers and the hexagon rules hold.
- **Ordering:** Riot does not guarantee entry order, so entries are sorted before slicing (`leaguePoints` descending; challenge `percentile` ascending, nulls last) — otherwise "top N" is meaningless and a discovered eval subject would change between runs.
- **Consequences:** a behavior change to three published tools; tool names and existing param descriptions are unchanged. `tft_league_rated_ladder_by_queue` and `tft_league_by_id` remain **known-unbounded** — no eval exercises them, so capping them was out of scope; the same remedy applies if they ever matter. `tft_league_by_id` stamps `totalEntries` but neither slices nor reorders.

- [ ] **Step 2: Write ADR-0017**

Create `docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md`. It must record:

- **Context:** the `stdio` and `sse` legs ran the identical 24-task suite and produced identical outcomes (within 0.03% on tokens in run `30011353019`), paying twice for one signal. The tool logic under test is the same jars either way.
- **Decision:** `stdio` runs the full suite; `sse` runs a four-task smoke set listed in `eval/smoke.txt` — handshake/discovery, a round-trip per server, and error propagation.
- **Mechanism:** `mcp-eval run` takes positional path specs including `file.py::func` but has **no `-k`, markers, or tags** (verified against `mcp_eval/runner.py` in 0.1.10), so the subset must be explicit paths. The list lives next to the tests rather than in CI YAML so it is visible to anyone adding a test.
- **What the sse leg does not prove:** any tool-logic regression. That coverage comes from `stdio`. The job summary names the scope so a green `sse` leg is never read as full coverage.
- **Relationship to ADR-0012:** narrows the coverage matrix; the harness's purpose, non-gating status, and credential model are unchanged.

- [ ] **Step 3: Add both gotchas**

Append to `docs/knowledge/gotchas.md`, matching the file's existing entry format:

1. **mcp-eval's `cost_estimate` understates the bill by ~2× and omits judge tokens.** It prices at a ~$0.50/MTok fallback rate; Claude Haiku 4.5 is $1.00/MTok input and $5.00/MTok output. It also counts only agent-loop spans — verified by arithmetic: `test_status_platform`'s 5,975 input tokens are exactly the agent's two iterations with nothing left for a judge call. Read token counts via `eval/tools/report-cost.py`, not the cost field.
2. **Never ask an LLM judge to verify something the prompt did not request.** `test_champion_mastery_for_discovered_player` asked the agent for *"the top champion"* (singular) while its rubric graded *"reports at most 3 champion mastery entries"*. The judge sees the rubric and the response but not the prompt, so it penalised the agent for obeying instructions — failing on `stdio` and passing on `sse` purely on judge variance. Assert tool arguments structurally with `Expect.tools.called_with(...)` (subset matching, zero judge tokens) and keep rubrics to what the prompt actually asks for.

- [ ] **Step 4: Update the ADR index**

Add rows for ADR-0016 and ADR-0017 to the decisions index in `docs/knowledge/README.md`, matching the format of the existing ADR-0015 row.

- [ ] **Step 5: Update the harness pattern guide and eval README**

In `docs/knowledge/patterns/live-eval-harness.md`:
- State that `stdio` runs the full suite and `sse` runs `eval/smoke.txt`, linking ADR-0017.
- Add a "Measure the cost" section showing `cd eval && uv run python tools/report-cost.py test-reports/stdio.json`, and warn that the report's `cost_estimate` field is unreliable (link the gotcha).
- Add a row to the triage table: **stale smoke set** — a spec in `smoke.txt` no longer resolves → `mcp-eval` prints `Skipping missing path` and the leg silently runs fewer tasks; fix the spec.

In `eval/README.md`, document `smoke.txt` (what it is, that the sse leg runs it, how to add to it) and the cost-report command.

- [ ] **Step 6: Update the add-live-eval skill**

In `.claude/skills/add-live-eval/SKILL.md`, add a step near the end: after adding a scenario, consider whether it belongs in `eval/smoke.txt` — it does **only** if it proves something transport-specific (handshake, discovery, a round-trip on a newly added server, error propagation). Tool-logic scenarios do not; the `stdio` leg covers those, and every smoke entry is paid for on every dispatch.

- [ ] **Step 7: Update the CHANGELOG**

Add entries under the appropriate module sections of `CHANGELOG.md`, matching the existing format, marking the three tool changes as behavior changes:

- `lol-mcp-server` — `lol_league_apex_by_tier` and `lol_challenges_by_player` now bound their list results (top 10 by default) and expose an optional `count` param plus `totalEntries` / `totalChallenges`.
- `tft-mcp-server` — `tft_league_apex_by_tier` likewise; `tft_league_by_id` now reports `totalEntries` (unbounded, unchanged ordering).

- [ ] **Step 8: Verify links resolve**

Run: `grep -rn "ADR-0016\|ADR-0017\|smoke.txt\|report-cost.py" docs/ eval/README.md .claude/skills/add-live-eval/SKILL.md CHANGELOG.md`
Expected: references in every file listed in this task, and no reference to a path that does not exist. Spot-check that `docs/knowledge/decisions/ADR-0016-bounded-list-results.md` and `ADR-0017-transport-scoped-live-eval.md` are on disk.

- [ ] **Step 9: Commit**

```bash
git add docs/ eval/README.md .claude/skills/add-live-eval/SKILL.md CHANGELOG.md
git commit -m "docs: record bounded list results and transport-scoped eval coverage

ADR-0016 (capped defaults, service-vs-port truncation), ADR-0017
(stdio full / sse smoke), plus two gotchas: mcp-eval's cost_estimate is
understated and judge-blind, and never grade a rubric on something the
prompt did not ask for."
```

---

## Task 8: Validate the reduction against the baseline

**Files:**
- Modify: `docs/superpowers/specs/2026-07-23-live-eval-token-cost-design.md` (append measured results)

**Interfaces:**
- Consumes: `eval/tools/report-cost.py` from Task 6; the shipped changes from Tasks 1–5.
- Produces: recorded measurements closing out the spec's success criteria.

**Background for the implementer:** This task needs a live dispatch and therefore real credentials — `ANTHROPIC_API_KEY`, `LOL_DEV_API_KEY`, `TFT_DEV_API_KEY` as repository secrets. **Riot dev keys expire every 24 hours**; if the preflight step skips the run green with a notice naming a key, regenerate it and re-dispatch. Do not interpret a skipped run as a passing result.

- [ ] **Step 1: Confirm the offline gate is green before spending anything**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Do not dispatch a live run against a red build.

- [ ] **Step 2: Push the branch and dispatch the workflow**

```bash
git push -u origin HEAD
gh workflow run live-eval.yml --ref "$(git rev-parse --abbrev-ref HEAD)"
```

- [ ] **Step 3: Wait for the run and confirm it actually executed**

```bash
gh run list --workflow=live-eval.yml -L 1
gh run watch "$(gh run list --workflow=live-eval.yml -L 1 --json databaseId --jq '.[0].databaseId')"
```

Expected: both matrix legs complete. Check the job summaries — the `stdio` leg must say `full suite` and the `sse` leg `transport smoke set (4 tasks)`. If either says `unknown scope`, the `EVAL_SCOPE` export in Task 5 did not take effect.

- [ ] **Step 4: Download the artifacts and run the cost report**

```bash
RUN_ID="$(gh run list --workflow=live-eval.yml -L 1 --json databaseId --jq '.[0].databaseId')"
gh run download "$RUN_ID" -D /tmp/after
cd eval && uv run python tools/report-cost.py \
  /tmp/after/mcpeval-reports-stdio/eval/test-reports/stdio.json \
  /tmp/after/mcpeval-reports-sse/eval/test-reports/sse.json
```

- [ ] **Step 5: Check the results against the success criteria**

| Criterion | Baseline | Target |
|---|---|---|
| `stdio` input tokens | 1,250,624 | ≤ 300,000 |
| `sse` input tokens | 1,250,796 | ≤ 30,000 |
| Combined real cost | ~$2.60 | ≤ $0.35 |
| `test_champion_mastery_for_discovered_player` | failed on stdio | passes |
| Any other task | — | no **new** failures |

**Exclude `test_champion_rotation` from the pass/fail comparison.** It failed on both legs in the baseline because Riot returned unavailable — an infra flake by the harness's own triage table, not a defect. Post-change it runs on `stdio` only (it is not in the smoke set).

If a criterion misses, do not adjust the target — find out why. The likeliest causes are that a `count` default did not take effect (check the tool table in the report: `lol_league_apex_by_tier` should now average a few hundred tokens, not ~17,900), or that a test not examined here holds a large payload.

- [ ] **Step 6: Record the measured results in the spec**

Append a `## Measured results` section to `docs/superpowers/specs/2026-07-23-live-eval-token-cost-design.md` with the run ID, date, the per-leg input/output token counts, the priced total, the per-tool table for `lol_league_apex_by_tier` before and after, and a pass/fail line per success criterion. The spec's criteria table is a claim; this section is the evidence.

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/specs/2026-07-23-live-eval-token-cost-design.md
git commit -m "docs(spec): record measured live-eval cost reduction

Post-change dispatch measured against the run 30011353019 baseline."
```

---

## Notes for the implementer

**Three existing assertions will break by design**, because the services now return bounded copies rather than the port's instance. Each is replaced in the task that causes it:

| Test | Assertion | Task |
|---|---|---|
| `lol …/league/application/LeagueServiceTest#getApexLeague_delegatesToPort` | `isSameAs` | 1 |
| `tft …/league/application/LeagueServiceTest#getApexLeague_delegatesToPort`, `#getLeagueById_delegatesToPort` | `isSameAs` | 2 |
| `lol …/challenges/application/ChallengesServiceTest#getChallengesByPlayer_resolvesPlayer_thenReturnsData` | `isSameAs` | 3 |

Tool-layer tests keep `isSameAs` — they mock the service, so only the call arity changes.

**If a WireMock adapter test needs editing, stop.** Adapters must keep fetching full payloads; truncation is an application concern. An adapter test that fails is a signal the change went into the wrong layer.
