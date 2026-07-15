# Monorepo Restructure — Extract `riot-api-core` — Design Spec

**Date:** 2026-07-15
**Status:** Approved
**Author:** Wade Kaiser (with Claude)

## Purpose

This spec covers **sub-project 0** of a larger program: turning `riot-api-mcp-server` from a
single Spring Boot application into a **monorepo of per-game MCP servers over a shared core
library**.

This sub-project is a **pure structural refactor**. It adds no Riot endpoints, no games, and
no features. Its deliverable is the module boundary that every later sub-project depends on,
plus removal of dead code and dead dependencies discovered along the way.

## Background — how we got here

The original goal was "full Riot API parity for MCP tools" across all Riot games (LoL, TFT,
Valorant, LoR) in one server. Exploring that surfaced four constraints:

1. **Scale.** Full parity is ~60–70 endpoints. One `@McpTool` per endpoint means ~65 tools in
   a single server. Tool-selection accuracy in MCP clients degrades well before that, and every
   tool's schema costs context on every request.
2. **Key gating.** Valorant's `val-match-v1` / `val-ranked-v1` and parts of LoR require a
   *production* API key tied to an approved product. A personal dev key receives 403.
3. **Routing.** `RiotApiRegionUri` / `RiotApiPlatformUri` cover LoL's two host schemes. Valorant
   uses a third (AP/BR/EU/KR/LATAM/NA/ESPORTS); account-v1 shard endpoints a fourth.
4. **Auth.** RSO endpoints (`/riot/account/v1/accounts/me`, LoR decks) need a `Bearer` token.
   `RiotApiClient` only knows `X-RIOT-TOKEN`.

An intermediate design proposed one server with `@ConditionalOnProperty` "game packs" to keep
the default tool surface small. That was rejected in favour of **separate servers per game**:
installing the TFT server *is* the opt-in, so the deployment boundary does the work the config
flag was simulating. The module boundary was the real boundary.

**Intended consumer:** a general MCP server that third parties install against their own Riot
API keys. This raises the bar on tool naming, error messages, and key-gating behaviour, and it
is the reason several decisions below go the way they do.

## Program roadmap (context only — this spec covers #0)

| # | Sub-project | Scope |
|---|---|---|
| **0** | **Monorepo restructure + extract `riot-api-core`** | **This spec.** |
| 1 | LoL server: correct + complete | PUUID migration (dead by-name paths, spectator v4→v5); add league, champion-mastery, champion, challenges, status, clash. Tool-name convention sweep. |
| 2 | TFT server | First real test of whether core generalizes. |
| 3 | Valorant server | New routing scheme; production-key gating. |
| 4 | LoR server | Smallest surface; verify it is not in maintenance before investing. |

Each gets its own spec → plan → implementation cycle.

## Current state (verified 2026-07-15)

Single Gradle project, Spring Boot 4.1 / Spring AI 2.0, Java 21. Bounded-context hexagons under
`com.wkaiser.riotapimcpserver`: `account`, `summoner`, `match`, `spectator`, `analytics`,
`shared`. Ten `@McpTool` methods across four tool classes. SSE transport on port 8080.

Findings that shape this design:

- **`GlobalExceptionHandler` is dead code.** It is `@RestControllerAdvice` returning
  `ProblemDetail` — it maps exceptions to HTTP responses for *controllers*. The application has
  no controllers, and Spring AI catches exceptions thrown inside `@McpTool` methods and converts
  them to MCP error results itself; they never reach MVC exception handling. It cannot fire today
  and has no web context to register in under stdio.
- **Two dependencies are unused.** `htmx-spring-boot` (no controllers, no templates, no resources
  beyond `application.yml`) and `spring-ai-starter-model-anthropic` (no `ChatClient`/`ChatModel`
  usage anywhere). The latter is load-bearing in the worst way: `application.yml` requires
  `${ANTHROPIC_API_KEY}` to boot, so installing this server demands an unrelated vendor's API key
  to satisfy a starter nothing uses.
- **The ArchUnit cross-context rules do not scale.** `HexagonalArchitectureTest:69-109` is a
  hand-maintained N×N matrix — five contexts, five rules, each enumerating the four it may not
  touch. Sub-project 1 takes LoL to eleven contexts.
- **Packaging assumes a single module.** `Dockerfile:16` copies `./src`, `:32` globs
  `build/libs/*.jar`, `:46` runs one jar, `:44` exposes 8080. `release.yml` publishes that image
  to GHCR.
- **The package root is a server name.** Everything sits under `com.wkaiser.riotapimcpserver`,
  which becomes wrong the moment core is a library consumed by four servers.

### Riot API deprecations (informational — fixed in sub-project 1, not here)

Recorded so they are not lost, and because they are why "parity" is not purely additive:

- `RiotSummonerAdapter:22` calls `/lol/summoner/v4/summoners/by-name/{name}`. Riot decommissioned
  Riot-ID-era name lookup; the path no longer resolves. `get_lol_summoner_by_name` is therefore
  dead, and so are `get_current_game_by_summoner_name` and `check_if_summoner_in_game`, which
  route through it (`LiveGameTool:39`).
- `RiotSpectatorAdapter:29,46` calls `spectator/v4`, superseded by `spectator/v5`, which is keyed
  by **PUUID**, not `encryptedSummonerId`.
- `encryptedSummonerId` is being stripped from Riot responses in favour of PUUID. Riot's own docs
  state: *"It is recommended to use PUUID endpoints whenever possible."*

**These are explicitly out of scope for this spec.** Fixing them changes behaviour; this cycle
must not.

## Design

### Module layout

```
riot-api-mcp-server/            (root: no code; buildSrc convention plugin)
├── buildSrc/                   riot-java-conventions.gradle
├── riot-api-core/              library — HTTP, routing, errors, auto-config
├── riot-account-core/          library — account-v1 domain/service/adapter, NO tools
├── lol-mcp-server/             Spring Boot app  ─┐
├── tft-mcp-server/             (sub-project 2)   ├─ each depends on both libraries
├── val-mcp-server/             (sub-project 3)   │
└── lor-mcp-server/             (sub-project 4)  ─┘
```

Dependencies point one way: servers → `riot-account-core` → `riot-api-core`. Nothing points back.

**Gradle enforces the dependency rule at compile time.** Today "core must not depend on a game
context" is a convention policed by an ArchUnit test. After the split it is structurally
impossible: `riot-api-core` has no dependency on any server module, so violating code cannot
compile. ArchUnit's remaining job is the hexagon rule *within* each module — which is what it is
actually good at.

`api` vs `implementation` is used deliberately: `riot-api-core` exposes `RiotApiClient` and the
routing enums as `api` (servers reference them in adapter signatures) and keeps its internals
`implementation`, making the shared kernel's public surface a compile-enforced fact.

### Libraries are auto-configured, not component-scanned

Neither library relies on a server's `@ComponentScan` reaching into it. Each ships
`@AutoConfiguration` classes registered via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. A server gets
`RiotApiClient` and `RiotAccountService` as beans by declaring the dependency and nothing else.
This is the distinction between a library and a folder of classes someone happened to scan.

### `riot-api-core` contents

- `RiotApiClient` — base-URL assembly, `X-RIOT-TOKEN` header, error→exception mapping
- `RiotApiProperties` (`riot.api-key`, `riot.base-url-override`)
- `RiotApiRegionUri` / `RiotApiPlatformUri`
- `RiotApiException`
- `RiotApiAutoConfiguration` — replaces `RiotApiConfiguration`
- Test fixtures (see Testing): the WireMock harness and the reusable ArchUnit rules

**Deliberately excluded from core, with reasons:**

- **Rate limiting / 429 retry.** Needed before third parties use this, but it changes behaviour.
  Mixing a new feature into a move-every-file restructure destroys the ability to tell which one
  broke something. Own cycle.
- **Bearer/RSO auth.** Nothing on the LoL roadmap needs it. It arrives when a server needs
  `accounts/me` or LoR decks. YAGNI.
- **A generalized `RiotHost` routing abstraction.** TFT reuses LoL's platform/region hosts, so the
  pressure does not arrive until sub-project 3. This is a monorepo: when it does, changing core's
  signature and all consumers is one atomic commit, not a breaking release. Let the real
  requirement shape the abstraction rather than guessing from one data point.

### `riot-account-core`

Holds the full hexagon *except* the inbound side: `RiotAccount` (domain), `RiotAccountService` +
`RiotAccountPort` (application), `RiotAccountRiotAdapter` (outbound), and an
`AccountAutoConfiguration`. Its existing tests (`RiotAccountServiceTest`,
`InMemoryRiotAccountPort`, `RiotAccountRiotAdapterTest`) move with it, so the library is
independently verified rather than only exercised through a server.

**The library-only property is enforced, not documented:** ArchUnit asserts no `@McpTool` exists
anywhere in this module.

Each game server owns a thin `account` package containing only an inbound adapter delegating to
the shared `RiotAccountService`. This is an **asymmetric context** — a tool with no domain or
application layer of its own, because those live in the library. Any ArchUnit rule asserting
"every context is a mini-hexagon" must account for it. The asymmetry is correct: the alternative
is duplicating the account domain into four servers to preserve a shape.

**Why not the alternatives:** account-v1 in `riot-api-core` would mix domain into plumbing, which
is precisely how shared kernels rot. A standalone `riot-account-mcp-server` would force users to
install two servers to do anything. Duplicating account per server means four copies of the same
adapter and tests to keep in sync.

### Tool names — held until sub-project 1

Avoiding cross-server collisions requires `lol_account_by_riot_id` rather than
`get_riot_account_by_riot_id`, and consistency then argues for sweeping every tool onto
`<game>_<context>_<action>`. **This cycle does not rename anything.** After sub-project 0 there is
still only one server, so nothing can collide yet — the rename buys nothing today and costs the
"pure refactor" invariant. Sub-project 1 is already changing names (it deletes the dead by-name
tools), so the convention sweep belongs there.

`CLAUDE.md` states tool names are the public MCP contract and must not change without reason.
Cross-server collision is that reason, and at `0.0.2-SNAPSHOT` the project is pre-1.0, so it is
the right time to take the break — deliberately and documented, in sub-project 1.

Two conventions are recorded now and applied in sub-project 1:

- **Naming:** `<game>_<context>_<action>` (e.g. `lol_league_entries_by_puuid`).
- **Boundary identity:** every tool accepts a **Riot ID or PUUID** and resolves internally, so the
  model never has to chain `account → summoner → match` itself. That chaining is both a
  rate-limit multiplier and the most common way models flail.

### Transport

Each server ships both transports behind Spring profiles, using one dependency (the `webmvc`
starter):

- **`sse`** — today's behaviour: webmvc SSE, `/mcp/messages`, port 8080.
- **`stdio`** (default) — `spring.ai.mcp.server.stdio=true`,
  `spring.main.web-application-type=none`.

**Critical: stdio uses stdout as the JSON-RPC transport.** Spring Boot logs to stdout by default,
and every class here is `@Slf4j` logging on each tool call (`SummonerTool:30`, `LiveGameTool:36`,
et al). The first log line will interleave with protocol frames and corrupt the stream — the
client sees malformed JSON and the connection dies, with a failure mode that looks nothing like
its cause. The `stdio` profile **must** set `spring.main.banner-mode=off` and route *all* logging
to stderr or a file. This goes in `gotchas.md` as part of this cycle.

### Build and quality gates

Shared config lives in a **`buildSrc` convention plugin** (`riot-java-conventions.gradle`) applied
by each module — toolchain, Spotless, JaCoCo, and test wiring defined once rather than copied per
module.

- **ArchUnit rules move into `riot-api-core` test fixtures** as reusable `ArchRule` constants that
  each module's test class parameterizes with its own root package. The hexagon rules are not
  LoL-specific; the TFT server inherits the architecture instead of copy-pasting 130 lines.
- **The N×N cross-context matrix is replaced by slices.**
  `slices().matching("..riot.lol.(*)..").should().notDependOnEachOther()` with named exceptions
  for the two real edges (spectator→summoner, analytics→account/summoner/match). One rule replaces
  eleven, and it stays correct as contexts are added. Done now, while there are only five to
  convert.
- **Coverage is per-module**, same 0.30 LINE floor. The exclusion list generalizes from
  `**/RiotApiMcpServerApplication.class` to `**/*Application.class`. No aggregate report until
  there is more than one server to aggregate.
- **Docker/CI:** the Dockerfile parameterizes which server module it builds (build arg); `EXPOSE`
  applies only to the SSE profile; `release.yml` publishes one image per server module. CI's
  `./gradlew build` is unchanged — it fans out across modules for free.

### Testing

No new test *strategy* — the existing one (WireMock for outbound adapters, in-memory port fakes
for application services, offline with no API key) is unchanged and non-negotiable.

What changes is placement. `Fixtures`, `SpectatorTestFixtures`, and the WireMock harness move into
`riot-api-core`'s **test fixtures** via Gradle's `java-test-fixtures` plugin; servers declare
`testImplementation(testFixtures(project(':riot-api-core')))`. This is the idiomatic answer and
avoids inventing a `riot-test-support` module or copying the harness four times.

Account's tests move to `riot-account-core`. Each server keeps its own context tests.

One test type is genuinely new: each library gets an **`ApplicationContextRunner` slice test**
asserting its auto-configuration registers the beans it claims to. Without this, a wrong imports
file fails silently in the library and only surfaces downstream as a context-load error in
whichever server happens to use the missing bean — which is the failure mode auto-configuration is
notorious for.

## Migration sequence

The safety property is that **the suite passes at every commit** — meaningful only if behaviour
does not change while files move.

1. Multi-module skeleton — `settings.gradle` includes, `buildSrc` convention plugin, root holds no code
2. Move all existing code into `lol-mcp-server` unchanged, original package names intact
3. Extract `riot-api-core` — `shared/*` moves; add `RiotApiAutoConfiguration` + imports file
4. Extract `riot-account-core` — account's domain/application/outbound move; its tool stays in the LoL server
5. Package rename per module → `com.wkaiser.riot.{core,account,lol}`
6. Delete dead weight — htmx, the Anthropic starter, `${ANTHROPIC_API_KEY}`, `GlobalExceptionHandler`
7. Transport profiles — `stdio` (default) and `sse`, including the stdout-logging fix
8. ArchUnit rework — rules into core test fixtures; N×N matrix → slices
9. Docs — ARCHITECTURE, CLAUDE.md, README, CHANGELOG, ADR for the split, stdio gotcha

Steps 1–5 are pure motion: anything red means the move is wrong. Only 6–7 change behaviour, and
they sit at the end where a failure is unambiguous.

## Verification

Green tests prove the code compiles and units behave. They do not prove the server still *serves*.
Before this is called done:

- **SSE profile:** a real MCP client handshake on 8080 — list tools, call one, get a result.
- **Stdio profile:** the same handshake over stdio, confirming the stream is not corrupted. No
  unit test will catch a log line landing in stdout; this must be exercised directly.
- **Tool inventory is unchanged:** the same ten tools with the same names and parameter
  descriptions as before the restructure. This is the concrete assertion that the refactor was
  pure.

## Non-goals

Explicitly **not** in this cycle. Each is real and wanted; each is also a reason a test could fail
for something other than the restructure:

- New Riot endpoints or contexts
- The PUUID migration / spectator v5 (sub-project 1)
- Tool renames or the naming convention sweep (sub-project 1)
- Rate limiting and 429 retry (own cycle)
- Error-message quality (own cycle — see below)
- Bearer/RSO auth
- Any non-LoL game
- A generalized routing abstraction

## Follow-ups this cycle creates

- **Error-message quality.** Deleting `GlobalExceptionHandler` removes something that was
  *pretending* to cover a real gap: `RiotApiClient` throws
  `RiotApiException("Riot API error: " + rawBody, 403)` and Spring AI surfaces that raw. For a
  server strangers install with their own keys, "403 with a raw Riot JSON body" versus "Your Riot
  API key is invalid or expired — dev keys expire every 24 hours" is most of the difference
  between a good and a bad install experience. Own cycle, before sub-project 1 ships publicly.
- **Rate limiting / 429 retry.** Same reasoning.

## Risks

- **Restructure churn is wide.** Every file moves and most get renamed. Mitigated by the strict
  sequence: pure motion first, behaviour change last, green at every commit.
- **Auto-configuration is a new pattern for this repo.** If the imports file is wrong, beans go
  missing and the failure appears at runtime as a context-load error rather than a compile error.
  The libraries have no application context of their own, so `ApplicationContextLoadsTest` in the
  LoL server is the only thing that would catch it — and only for beans that server happens to
  use. Each library needs its own `ApplicationContextRunner` slice test asserting its
  auto-configuration actually registers the beans it claims to.
- **Stdio logging corruption** is silent and misleading. Mitigated by the profile config and by
  explicit stdio verification.
- **Context7 was a weak source for the Riot endpoint catalog.** Three queries against
  `/websites/developer_riotgames` returned mostly Data Dragon and Valorant/TFT material rather
  than a structured LoL reference. Endpoint paths in sub-projects 1–4 must be verified against the
  live developer portal, not assumed from either Context7 or model knowledge.