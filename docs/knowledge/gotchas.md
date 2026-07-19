# Gotchas

Sharp edges specific to this codebase. Append new ones at the bottom, newest last, each
as its own small section (per the
[hydrate/persist protocol](README.md#hydrate--persist-protocol)).

## Lombok nested `@Builder` static classes need `@NoArgsConstructor` + `@AllArgsConstructor`

Every DTO uses `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (with
`@JsonIgnoreProperties(ignoreUnknown = true)` where the shape is a Riot response). The
**inner static classes** must carry the same four annotations. If a nested `@Builder`
class omits `@NoArgsConstructor` and `@AllArgsConstructor`, Lombok generates a
conflicting all-args constructor and the builder wiring breaks `:compileJava` /
`:compileTestJava` (the class that historically bit us is `Perks` in
`spectator/domain`). Symptom: "constructor … cannot be applied" or a missing builder
method at compile time. Fix: put all four annotations on the nested class too. Jackson
also needs the no-args constructor to deserialize.

## Spectator `404` means "not in a game" — map it to `null`, not an exception

`RiotApiClient` throws `RiotApiException` for **any** non-2xx response. The Spectator API
returns `404` when a summoner is simply not currently in a game — a normal outcome, not
an error. `RiotSpectatorAdapter.getCurrentGameInfo(...)` therefore catches
`RiotApiException`, and when `getStatusCode() == 404` returns `null`; any other status is
rethrown. This rule lives in the adapter (context-specific), **not** in the shared
`RiotApiClient` status handler. When you test it, stub a `404` and assert `null`.

## Region vs platform routing — do not mix them up

Two different host schemes, two different `RiotApiClient` entry points:

- **Region** (`RiotApiRegionUri`: `AMERICAS`, `ASIA`, `EUROPE`, `SEA`, hosts like
  `americas.api.riotgames.com`) routes **account** and **match** endpoints. Use
  `riotApiClient.regional(region)`.
- **Platform** (`RiotApiPlatformUri`: `NA1`, `EUW1`, `KR`, … hosts like
  `na1.api.riotgames.com`) routes **summoner** and **spectator** endpoints. Use
  `riotApiClient.platform(platform)`.

Calling a match endpoint on a platform host (or a summoner endpoint on a region host)
returns 404/wrong data. See [glossary](glossary.md#platform-vs-region).

## `@McpTool` discovery

MCP tools are auto-discovered by Spring AI's annotation scanner — there is no manual
registry. For a tool to appear:

- the class must be a Spring bean (`@Component`) in a scanned package under the server's own
  package root (e.g. `com.muddl.riot.lol` for `lol-mcp-server`);
- `@McpTool`/`@McpToolParam` must be imported from
  `org.springframework.ai.mcp.annotation`;
- each `@McpTool` `name` must be **unique** across the whole app (a duplicate silently
  shadows or fails discovery);
- by the architecture rules, `@McpTool` may appear **only** in `..adapter.in.mcp..`
  (ArchUnit fails the build otherwise — see
  [ADR-0004](decisions/ADR-0004-archunit-enforcement.md)).

If a new tool does not show up, check the bean annotation and the package first.

## STDIO transport: any stdout write corrupts the JSON-RPC stream

When the `stdio` profile is active, the MCP protocol stream **is** stdout. Spring Boot logs to
stdout by default and every class here is `@Slf4j` — `SummonerTool` and `LiveGameTool` log on
each tool call. One log line interleaves with protocol frames, the client sees malformed JSON,
and the connection dies with an error that points nowhere near the cause.

`application-stdio.yml` prevents this with three settings, all load-bearing:

- `spring.main.banner-mode: off` — the banner alone breaks the handshake
- `logging.pattern.console: ""` — an empty pattern disables the console appender
- `logging.file.name` — logs still need to go somewhere; default `./riot-mcp-server.log`

No unit test catches this. Verify by piping a JSON-RPC `initialize` + `tools/list` into the jar
and asserting every stdout line parses as JSON (see the sub-project 0 plan, Task 10).

The `sse` profile is unaffected — the protocol runs over HTTP, so console logging is safe.

## ArchUnit: a fully-qualified package in a rule's *condition* passes vacuously when that package moves

`noClasses().that().<selector>().should().dependOnClassesThat().resideInAPackage("com.example.foo..")`
has two package matchers, and they fail differently:

- The **selector** (`that()`) picks which classes are checked. If it matches nothing, ArchUnit fails
  loudly — a `should()` that checked zero classes is an error.
- The **condition** (`should()`) decides what counts as a violation. If it matches nothing, there
  are simply **zero violations — and the rule passes**, green, enforcing nothing.

This bit `only_analytics_and_the_account_tool_use_the_account_domain` (at the time named
`..._account_library`), whose condition named `com.wkaiser.riot.account..`. The `com.wkaiser` →
`com.muddl` rename would have left it green and useless. The rule that exists *because* a
prohibition was once silently retired was itself one rename away from silent retirement.

Two rules follow:

1. **Keep matchers relative** (`..riot.account..`, not `com.muddl.riot.account..`) so no rule has an
   opinion about the group. `@AnalyzeClasses` needs a real root, but its failure is loud.
2. **Negative-control anything package-string-dependent.** A green build cannot distinguish
   "passing" from "vacuously passing". `HexagonalArchitectureNegativeControlTest` imports a
   deliberate violation and asserts the rule *fails*. If a negative control ever goes green by not
   throwing, the rule is dead.

## Gradle ignores `gradle.properties` in a subproject directory

Gradle reads `gradle.properties` from the **root project directory** and `GRADLE_USER_HOME` — not
from subproject directories. Dropping `riot-api-core/gradle.properties` with `version=1.2.3` in it
does nothing, and Gradle says nothing: the file is silently ignored, and the module keeps whatever
version it had.

This is why per-module versions are declared in each module's `build.gradle` (see ADR-0010) rather
than in a per-module properties file, which is the more obvious-looking option and does not work.

The convention plugin sets `group` (shared) but deliberately **not** `version` (per-module). A
version is a module-specific fact, and a module-specific fact at the shared altitude is how three
modules came to share one version number by construction.

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

## An `ignoreDependency` outlives the edge it excused

When a composition edge is removed (e.g. Plan C dropped `spectator → summoner` by moving spectator to
Spectator-V5 and deleting its by-name tools), the matching `.ignoreDependency(...)` in
`contexts_do_not_depend_on_each_other` becomes **unused but harmless** — ArchUnit does not fail on an
exception that matches nothing, so a stale one passes green while overstating the real composition
graph. It will not break a build, but it misleads the next reader about which contexts actually
depend on which. Prune the exception in the same change that removes the edge; if that is deferred,
record it (Plan C deferred this to Plan D's arch audit). The honest exception list after Plan C is
`analytics → summoner` and `analytics → match` only.

## The live eval harness is Python-in-a-Java-repo and hits the real Riot API

`eval/` is a `uv`-managed Python (`mcpevals`) project — never built by Gradle, never part of the
offline `./gradlew build` gate. It drives the built server jar against the **live** Riot API on CI,
post-merge only (`.github/workflows/live-eval.yml`), and never blocks a merge. Consequences to know:

- **Needs `ANTHROPIC_API_KEY`** (agent + LLM judge). This is a *separate credential bucket* from
  `CLAUDE_CODE_OAUTH_TOKEN` (which `claude.yml` uses); the live-eval workflow must never read the
  OAuth token. Absent key ⇒ the job skips green with a notice.
- **Assert invariants, not exact values.** Live data (LP, match counts, who is in a game) shifts
  between runs. A test pinned to an exact value is guaranteed to flake.
- **A `CANARY:` failure means "investigate Riot", not "loosen the test".** Canaries guard
  undocumented Riot behaviours our adapters depend on (e.g. spectator `404 → null`).
- **Rate budget:** the dev key allows 100 req / 2 min. The suite runs serially (`max_concurrency:
  1`); do not parallelize it or add high-fanout tests casually.
- **stdio stdout purity is load-bearing here too:** if the stdio session fails to connect in CI,
  suspect a stray stdout write before anything else (see the STDIO gotcha above).

## Riot JSON with snake_case keys needs `@JsonProperty`

Most Riot LoL DTOs are camelCase and map by field name, but **LoL-Status-V4** uses `snake_case` for
some keys (`incident_severity`, `maintenance_status`). Jackson here matches exact names, so those
fields need an explicit `@JsonProperty("incident_severity")` on the camelCase Java field or they
deserialize to `null` silently — the adapter's WireMock test asserts the parsed value to catch it.
Seen in `status`'s `StatusEntry` (sub-project 1b). Do not switch on a global naming strategy for one
context; annotate the specific fields.

## Riot DTO number/boolean fields must be **boxed**, not primitives

Riot returns `null` for fields tied to systems it has retired, and a Java **primitive** field then
throws at deserialization: `Cannot map null into type int/boolean/long`
(`FAIL_ON_NULL_FOR_PRIMITIVES`). The live eval caught two in sub-project 1b — Champion-Mastery-V4's
`chestGranted` (and `tokensEarned`) went null after the 2024 mastery/chest revamp, and Champion-V3's
`maxNewPlayerLevel` came back null — both had been declared `boolean`/`int`. Offline WireMock tests
pass because our fixtures were fully populated; only live data has the nulls.

**Rule:** declare Riot DTO number/boolean fields as boxed wrappers (`Integer`/`Long`/`Boolean`), not
primitives. `String`s and `List`s are already null-safe. When you add a DTO, also add a WireMock case
that feeds `null` for the nullable-looking fields and asserts it parses — that reproduces this class
of failure offline instead of waiting for the post-merge live eval. Boxing `boolean`→`Boolean` also
renames the Lombok getter (`isX()` → `getX()`); update call sites.

## Riot's live JSON field names can differ from the developer-portal docs

Champion-V3 `/lol/platform/v3/champion-rotations` is documented as returning `freeChampionIds`,
`freeChampionIdsForNewPlayers`, `maxNewPlayerLevel` — but the **live** endpoint returns the compact
keys `sr` and `newplayer` (and no `maxNewPlayerLevel`). Riot appears to serve both shapes. A DTO
written to the documented names deserialized to all-null against live data (with
`@JsonIgnoreProperties(ignoreUnknown = true)` the real keys were silently dropped), and the offline
WireMock test passed only because its fixture had encoded the documented shape. The live eval caught
it. **Lesson:** confirm field names against a real response (`curl` with a key), not just the portal
schema; when both shapes exist, accept them with `@JsonAlias`. This is the concrete case behind the
standing "verify against the live developer portal, never assume" constraint.

## PRs opened by `GITHUB_TOKEN` do not trigger other workflows

GitHub deliberately does not fire `pull_request`/`push` workflow events for refs created or PRs
opened using the automatic `GITHUB_TOKEN` (to prevent recursive runs). So the weekly
`housekeeping.yml` PR (opened via `gh pr create` with `GITHUB_TOKEN`) will NOT get an automatic
`claude-code-review.yml` run. That is expected, not a bug. If auto-review on those PRs is ever
wanted, open them with a PAT or GitHub App token instead of `GITHUB_TOKEN`.

## A merge to `master` can occasionally trigger no Actions runs (dropped push event)

GitHub Actions very occasionally **drops the `push` event** for a merge to the default branch even
when Actions is fully operational (no incident posted). Symptoms: the merge lands (`master` moves to
the merge commit), but no `CI` / `Live Eval` run is created for that SHA, and any workflow file the
merge *added* does not register (so `gh workflow run <new>.yml` returns `HTTP 404: not found on the
default branch`). Observed once when merging the housekeeping/Actions PR: `pull_request` events kept
firing normally, so it was an isolated dropped webhook, not a repo misconfiguration. Recovery: push
any trivial follow-up commit to `master` (or merge a one-line PR) to generate a fresh push event —
that re-triggers the missing runs and registers the new workflow. Don't debug repo settings first;
confirm with `gh api "repos/<owner>/<repo>/actions/runs?head_sha=<sha>" --jq '.total_count'` (0 = the
event was dropped) and check <https://www.githubstatus.com> before assuming a config problem.
