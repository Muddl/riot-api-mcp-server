# LoL Parity — Foundation — Design Spec

**Date:** 2026-07-15
**Status:** Approved
**Author:** Wade Kaiser (with Claude)

## Purpose

This spec covers **sub-project 1a**: harden `riot-api-core` into a real shared kernel, bring
`lol-mcp-server` to a correct and conventional base, and give the monorepo per-module docs,
per-module versioning, and coordinates that match their owner.

It is the **first feature work on the structure sub-project 0 built**, and that framing drives
every decision below. Four more servers will be built against whatever this cycle establishes. The
organizing principle is therefore:

> **Everything a second game server would otherwise copy gets built once, in a library, in this
> cycle.**

That bar is falsifiable, and this spec states the test for it (see [Handoff
contract](#handoff-contract--what-1b-inherits)).

## Sub-project 1a / 1b split

Sub-project 0's roadmap listed sub-project 1 as: *"PUUID migration (dead by-name paths, spectator
v4→v5); add league, champion-mastery, champion, challenges, status, clash. Tool-name convention
sweep."*

That bundles four independent kinds of work: correctness (behaviour-changing, tool-removing),
breadth (six new contexts, purely additive), the tool contract sweep (a public break), and monorepo
conventions. Sub-project 0's own lesson was that mixing pure motion with behaviour change destroys
the ability to tell which one broke something — the same argument applies to mixing a contract
break with wide addition.

**The slice is foundation-first:**

| | Scope |
|---|---|
| **1a** (this spec) | Coordinates/package rename, release engineering, core hardening, shared identity resolution, LoL correctness, **one** new context (league) as the exemplar, the tool contract sweep, per-module docs and the monorepo sanity check. |
| **1b** (next spec) | The remaining five contexts — champion-mastery, champion, challenges, status, clash — plus a tool for the existing `match` context, built mechanically against 1a's template. |

**Why league is the exemplar:** it is the most-wanted LoL data (rank), it is platform-routed, it is
player-keyed so it exercises the identity resolver, and it has both per-player entries and apex
leagues — enough shape to be a real template rather than a trivial one.

**Why the contract break happens in 1a, not 1b:** renaming tools is a public break, and doing it
once across a small surface is cheaper than doing it once across a large one. 1b then adds tools
that are born correct.

This split is recorded durably in [`docs/knowledge/roadmap.md`](../../knowledge/roadmap.md), which
supersedes sub-project 0's roadmap table as the living source of truth. That table remains as a
dated snapshot and is not edited retroactively.

## Current state (verified 2026-07-15)

Gradle monorepo, Spring Boot 4.1 / Spring AI 2.0, Java 21. Three modules: `riot-api-core`,
`riot-account-core`, `lol-mcp-server`. Ten `@McpTool` methods across four tool classes. Both `stdio`
(default) and `sse` transports ship.

Findings that shape this design:

- **All documentation lives at the repo root.** `README.md`, `ARCHITECTURE.md`, `CHANGELOG.md`,
  `CONTRIBUTING.md` describe the whole repo. No module has local docs. A consumer who only wants
  `tft-mcp-server` will have nowhere local to look.
- **`version = '0.0.2-SNAPSHOT'` is hardcoded in the convention plugin**
  (`buildSrc/src/main/groovy/riot-java-conventions.gradle:11`). All three modules share one version
  *by construction*; independent versioning is currently impossible.
- **Version and changelog are not linked to the release.** `release.yml` triggers on `v*` and
  derives image tags from the **git tag** via `docker/metadata-action`'s `type=semver`. The Gradle
  version is never consulted. The image tag and the jar's version are two unrelated numbers that
  nothing reconciles.
- **The tag namespace is flat.** `v*` cannot express `riot-api-core v1.2.0` and `lol-mcp-server
  v0.3.0` as distinct releases.
- **`release.yml` tags `latest` unconditionally** (`type=raw,value=latest`), so re-releasing an
  older patch moves `latest` backwards — the stale-GHCR-tag hazard already noted in the log.
- **Errors surface raw.** `RiotApiClient` throws `RiotApiException("Riot API error: " + rawBody,
  403)` and Spring AI passes that to the model verbatim.
- **Three tools are dead.** `get_lol_summoner_by_name`, `get_current_game_by_summoner_name`,
  `check_if_summoner_in_game` — Riot decommissioned Riot-ID-era name lookup, so the underlying path
  no longer resolves.
- **Artifact coordinates do not match the owner.** `group = 'com.wkaiser'` and packages are
  `com.wkaiser.riot.*`, while the GitHub owner and the GHCR image path are `muddl`. Nothing ties
  the published image back to its coordinates by name.
- **The `match` context has no MCP tool.** It has a domain, a service, a port, and a WireMock-tested
  outbound adapter, but no inbound adapter — `analytics` is its only consumer. So match data is
  reachable only through an analytics summary, never directly. This is a genuine parity gap, and it
  is **1b's**, not 1a's: exposing it is exactly the mechanical add-a-tool work 1b exists to do, and
  1a's job is to make that work mechanical.

## Design

### Phase 0 — Coordinates and package rename (pure motion)

`group` becomes `com.muddl`, and the package root moves `com.wkaiser.riot.*` → `com.muddl.riot.*`
across all three modules.

Changing only the Gradle group would leave `group = 'com.muddl'` shipping `com.wkaiser.riot.lol`
classes. Java convention is that the package root matches the group, so a group-only change would
create a fresh instance of exactly the inconsistency this cycle's sanity pass exists to remove.

Nothing here is externally breaking: the libraries are not published, the image name derives from
`github.repository_owner` (already `muddl`), and the public MCP contract is the tool names, which
Phase 6 handles separately. This is internal churn only.

**It goes first, and it is pure motion** — the sub-project 0 rule. Anything red means the move is
wrong, not that a feature broke. It also means the convention plugin is edited once for `group` and
`version` together rather than twice. Doing it now, with one server, is the cheapest it will ever
be; after sub-projects 2–4 it is four times the work.

**ArchUnit rule validity is a named task in this phase, not an assumption.** Auditing the rules
(verified 2026-07-15) shows the shared `HexagonRules` fixture is clean — every matcher there is
relative (`..domain..`, `..adapter.out.riot..`, `..core.http..`) and survives the rename untouched.
But `HexagonalArchitectureTest` hard-codes the fully-qualified root in three places:

| Location | Rule | Behaviour if stale |
|---|---|---|
| `:26` | `@AnalyzeClasses(packages = "com.wkaiser.riot.lol")` | Imports zero classes; ArchUnit fails a `should()` that checked nothing — **loud**. |
| `:66` | `slices().matching("com.wkaiser.riot.lol.(*)..")` | Zero slices — **loud**, for the same reason. |
| `:93` | `only_analytics_and_the_account_tool_use_the_account_library` → `.resideInAPackage("com.wkaiser.riot.account..")` | **Silent.** Passes green while enforcing nothing. |

The third is the hazard. Its selector — `noClasses().that().resideOutsideOfPackages("..lol.analytics..",
"..lol.account..")` — is *relative*, so it still matches classes. Only its **condition** carries the
stale FQN. After the rename, classes depend on `com.muddl.riot.account`, the condition matches
nothing, there are zero violations, and the rule passes while guarding nothing.

That is precisely the failure this rule was written to fix. Its own javadoc records that extracting
account outside the slice matcher "silently retired those three prohibitions — nothing violated
them, so nothing failed." A rename would silently retire the rule that exists because of a silent
retirement.

Two responses, both in this phase:

1. **Make the rules group-agnostic.** Replace fully-qualified matchers with relative ones
   (`..riot.account..`, `..riot.lol.(*)..`) so the rule set has no opinion about the group at all.
   This removes the whole class of bug rather than fixing this instance of it — and it matters
   beyond the rename, because sub-projects 2–4 copy this test file. `@AnalyzeClasses` still needs a
   real root, but its failure is loud.
2. **Prove the rules still bite — a negative control.** A green build is not evidence here, because
   the failure mode *is* a green build. Each package-string-dependent rule is verified by
   confirming it still fails when fed a violation, before and after the rename. Anything that
   cannot be made to fail is not enforcing anything.

The account-library rule's negative control is worth keeping permanently rather than performing
once; the plan decides between an archived fixture and a documented manual check.

### Phase 1 — Release engineering

Independent of every other phase, and it means the rest of this cycle bumps and changelogs
correctly by construction rather than retroactively.

**Versioning model.** The convention plugin keeps `group` (now `com.muddl`, per Phase 0) and
**drops `version` entirely**. Each module declares its own `version` in its own `build.gradle`.
Independent semver per module, pre-1.0 rules (breaking → minor).

Version is declared in `build.gradle` rather than a per-module `gradle.properties` deliberately:
Gradle's handling of subproject properties files is a known sharp edge, and the failure is silent.
The plan **verifies** this rather than assuming it, and the finding is recorded in `gotchas.md`
either way.

**Divergence point.** All three modules go `0.0.2-SNAPSHOT → 0.1.0` in this cycle — a legitimate
bump for each, independently justified:

| Module | 0.1.0 because |
|---|---|
| `riot-api-core` | New coordinates; gains 429 retry and the error taxonomy; `RiotApiException`'s shape changes. |
| `riot-account-core` | New coordinates; gains `PlayerIdentityResolver`. |
| `lol-mcp-server` | New coordinates; breaks the tool contract (renames, removals, `player` param). |

They drift independently from there.

**Changelog headings accumulate during the cycle.** Version is set to `0.1.0` in this phase, so the
`## [0.1.0]` heading must exist from that point for the gate to pass — while Phases 2–7 are still
adding entries under it. That is intended: the gate matches the heading, not a release date, so
entries accumulate under the target version's heading during development and the date is filled at
tag time. This is Keep a Changelog's `Unreleased` convention with the version named up front, which
the gate requires.

**Library versions mean provenance, not resolution.** Servers consume libraries via
`project(':riot-api-core')` — a source reference. A library's version appears in no dependency
declaration and bumping it changes no resolution behaviour. Libraries are therefore **versioned and
tagged but not published**; project references stay, preserving the atomic cross-module commit that
[ADR-0006](../../knowledge/decisions/ADR-0006-monorepo-split.md) explicitly bought.

What versioning buys instead is answerability, via **provenance stamping**:

- Each server's `bootJar` manifest records its own version plus `Riot-Api-Core-Version` and
  `Riot-Account-Core-Version`, read from the project references at build time.
- The image carries matching OCI labels (`org.opencontainers.image.version` plus the embedded
  library versions), passed through as Dockerfile build args.

Net effect: *"core 0.1.0 has a flaw — which published images embed it?"* is answerable by
inspecting registry labels, without rebuilding or guessing.

**Tag namespace and release flow.** Tags become `<module>/v<semver>` (`lol-mcp-server/v0.1.0`,
`riot-api-core/v0.1.0`). `release.yml` triggers on `*/v*`, parses module and version from the tag,
and builds **only** that module. Server tags publish an image; library tags publish a GitHub
Release with changelog notes and no image.

`type=semver` cannot parse a prefixed tag, so version extraction becomes an explicit step rather
than metadata-action magic. This also lets `latest` be scoped per module and applied only to the
highest version, fixing the stale-tag hazard.

**The alignment gate.** The observed problem is that bumping and changelogging are two independent
acts of discipline. This makes them one act.

A **`verifyRelease` task, wired into `check`**, fails the build when:

- a module's `version` has no matching `## [<version>]` heading in **that module's**
  `CHANGELOG.md`; or
- on a release tag, the tag's version disagrees with that module's Gradle version.

Because versions are concrete (not `-SNAPSHOT`) on master, the first check runs on **every
`./gradlew build`**, not only on releases. Bumping without a changelog entry stops being something
to remember and becomes a red build — the same "enforced, not documented" move as the `@McpTool`
ArchUnit rule ([ADR-0004](../../knowledge/decisions/ADR-0004-archunit-enforcement.md)).

**Which changelog gets the entry.** One rule: *a change is logged in the CHANGELOG of every module
whose version it bumps.* The root CHANGELOG covers only changes that bump no module — CI, build
tooling, root docs.

Coordinates are a versioning-adjacent concern, so **ADR-0010 covers both** — the `com.muddl` move
and the versioning model — rather than spawning a separate ADR for a one-line decision.

**A `prepare-release` skill.** The gate checks that a version and a changelog heading agree. It
cannot tell that a module *should* have been bumped and wasn't — that is judgment, and it belongs
in a skill. `.claude/skills/prepare-release/SKILL.md`, matching the existing hydrate-first house
style and operationalizing ADR-0010. It encodes: diff each module against its last tag to find what
actually changed; classify the bump level under pre-1.0 rules; write the entry before bumping;
verify green; tag per module.

The judgment it must encode explicitly, because it is easy to miss: **a library bump usually
cascades into a server patch release.** If `riot-api-core` 0.1.0 → 0.1.1 fixes a security flaw, the
fix reaches users only when each server re-releases and re-stamps its image. A library tag alone
ships nothing.

### Phase 2 — Core hardening (`riot-api-core`)

No LoL code. Both items are pure plumbing, and every future server inherits them.

**429 retry/backoff** in `RiotApiClient`. Honours Riot's `Retry-After` header rather than guessing;
bounded attempts; a clear exception when exhausted.

This is deliberately **not** a rate *limiter* — no token bucket, no proactive throttle. Riot
documents `Retry-After` on 429, so reacting correctly is the behaviour with a specification behind
it. A proactive limiter needs a real design (per-method limits, shared buckets across contexts,
what to do under concurrency) and would be guessing at this stage. Own cycle if evidence demands
it.

**Error mapping.** `RiotApiException` gains a typed reason so messages are actionable:

| Status | Message |
|---|---|
| 403 | Your Riot API key is invalid or expired — development keys expire every 24 hours |
| 404 | The requested resource was not found |
| 429 | Rate limited by the Riot API |
| 503 | The Riot API is temporarily unavailable |

The raw body stays available on the exception but stops being the message. This matters because the
intended consumer is a third party installing against their own key; sub-project 0 identified this
as most of the difference between a good and a bad install experience.

**The boundary, held deliberately:** `riot-api-core` knows about **HTTP and Riot's error protocol**,
never about a game's domain. League's DTOs stay in the LoL server.
[ADR-0006](../../knowledge/decisions/ADR-0006-monorepo-split.md) warned about core becoming a junk
drawer; ADR-0007 exists to hold this line.

### Phase 3 — Shared identity (`riot-account-core`)

`PlayerIdentityResolver`: accepts `GameName#TAG` or a PUUID, returns a resolved PUUID.

Every game server needs this, and account-v1 is already cross-game and already lives here. If
resolution lived in LoL's contexts, every future server would re-implement it.

**Caching.** Backed by a bounded TTL cache. PUUIDs are stable but **Riot IDs are mutable** (players
can change `GameName#TAG`), so the cache keys on Riot ID → PUUID and the TTL bounds staleness. This
pairs directly with Phase 2: a Riot-ID-keyed tool call costs two Riot requests unless resolution is
cached, and the boundary-identity rule below makes that the common path.

Registered via the module's existing auto-configuration, so servers get it by declaring the
dependency and nothing else.

**This is the first stateful thing in the codebase.** Bounded size, TTL, an **injected clock**, and
no cross-request assumptions.

**This phase breaks an existing ArchUnit rule, and the fix must not be to delete the rule.**
`only_analytics_and_the_account_tool_use_the_account_library` denies by default: only `analytics`
and `account` may reach into `riot-account-core`. Once every player-keyed context injects
`PlayerIdentityResolver`, summoner, league, spectator, and match all violate it. Widening the
allowlist to "everyone" would technically pass and would throw away the guarantee.

The rule is right; it is drawn at the wrong granularity. It conflates two things that
`riot-account-core` holds:

- **The account domain** — `RiotAccount`, `RiotAccountService`. Genuinely a bounded context, and
  the thing the rule exists to confine. Stays deny-by-default: `analytics` and `account` only.
- **Identity resolution** — `PlayerIdentityResolver`. Deliberately cross-cutting; every context is
  *supposed* to depend on it. That is the whole point of Phase 3.

So the rule splits in two rather than widening: the domain stays confined, and the resolver is
open. To make that boundary real rather than nominal, **`PlayerIdentityResolver` returns a plain
PUUID string, not a `RiotAccount`** — otherwise every context would touch the account domain
through the resolver's return type and the confinement rule would be defeated through the back
door. Contexts that want account *data* still go through `RiotAccountService` and are still subject
to the deny-by-default list.

### Phase 4 — LoL correctness

- **Delete** `get_lol_summoner_by_name`, `get_current_game_by_summoner_name`,
  `check_if_summoner_in_game`. The underlying Riot path no longer resolves, so these are dead, not
  deprecated. Deprecating a tool that cannot work is worse than removing it.
- **Spectator v4 → v5** (PUUID-keyed). The `404 → null` rule stays in the adapter, not in
  `RiotApiClient` (see `gotchas.md`).
- **Summoner and spectator move off `encryptedSummonerId` to PUUID.** Riot is stripping it from
  responses and its own docs recommend PUUID endpoints wherever possible.

### Phase 5 — League context (the exemplar)

A full mini-hexagon under the LoL server root: `domain/`, `application/` + `application/port/`,
`adapter/out/riot/`, `adapter/in/mcp/`. Ranked entries by player, plus the apex leagues
(challenger/grandmaster/master).

This is the artifact 1b copies five times, so it is held to the pattern deliberately rather than
expediently.

**Endpoint paths are verified against the live Riot developer portal**, not against Context7 or
model knowledge. Sub-project 0 recorded that Context7 was a weak source for the Riot endpoint
catalog, and league is the first place that bites. This is a task in the plan, not an assumption.

### Phase 6 — Contract sweep

**Naming:** every tool → `<game>_<context>_<action>`, e.g. `lol_summoner_by_player`,
`lol_league_entries_by_player`, `lol_account_by_player`.

**Boundary identity:** every player-keyed tool takes a single required `player` param accepting
either `GameName#TAG` or a raw PUUID, disambiguated on the `#`, resolved internally via
`PlayerIdentityResolver`. The model never chains `account → summoner → match` itself — that
chaining is both a rate-limit multiplier and the most common way models flail.

One param rather than two optional ones (`riot_id` / `puuid`) because "exactly one of" is not
expressible in JSON Schema, so it would become a runtime check that models routinely get wrong by
filling both or neither. One param also keeps the schema small across a tool surface that 1b will
grow. An unparseable value returns an actionable error naming both accepted forms.

**No aliases.** Pre-1.0, with no public consumers, sub-project 0 already reasoned that this break
should be taken deliberately and documented. Aliases would double the tool surface to preserve
names nobody depends on.

**The resulting inventory — ten tools become seven.** This is the concrete statement of the
contract break:

| After | From | Note |
|---|---|---|
| `lol_account_by_player` | `get_riot_account_by_puuid` + `get_riot_account_by_riot_id` | Collapsed |
| `lol_summoner_by_player` | `get_lol_summoner_by_puuid` | |
| `lol_spectator_current_game_by_player` | `get_current_game_by_summoner_id` + `get_current_game_by_summoner_name` | Collapsed; `null` still means "not in a game" |
| `lol_spectator_featured_games` | `get_featured_games` | Rename only |
| `lol_analytics_player_matches` | `get_lol_player_match_analytics` | Rename only |
| `lol_league_entries_by_player` | — | New (Phase 5) |
| `lol_league_apex_by_tier` | — | New (Phase 5); challenger/grandmaster/master |
| *removed* | `get_lol_summoner_by_name` | Riot path no longer resolves |
| *removed* | `get_current_game_by_summoner_name` | Routed through the dead by-name lookup |
| *removed* | `check_if_summoner_in_game` | Routed through it too, and redundant — a null current-game answers it |
| *removed* | `get_lol_summoner_by_id` | Keyed by `encryptedSummonerId`, which Riot is stripping |

Two results worth noting. **The boundary-identity rule collapses paired tools**: once one param
accepts either form, `by_puuid` / `by_riot_id` siblings have no reason to be separate tools. And
**the surface shrinks from ten to seven while capability grows** — which is the whole argument of
ADR-0006 playing out early, since tool-selection accuracy was the reason for the monorepo in the
first place.

This phase runs **after** correctness and league, so that if it breaks something, the cause is
unambiguous.

### Phase 7 — Docs and monorepo sanity check

**Doc topology.** Root shrinks to orientation: what the monorepo is, the module table, the
dependency rule, how to build, and links out. Every module — both libraries **and** every server —
gets:

- **README.md** — what this module is, its tools (servers) or public API (libraries), how to
  run/consume it, its configuration.
- **ARCHITECTURE.md** — its contexts and their boundaries. The *shared* hexagon rationale stays at
  root and is linked, never restated.
- **CHANGELOG.md** — module-scoped, per the Phase 1 rule.

The bar: a consumer who only cares about `tft-mcp-server` can read that module's docs and stop.

**The anti-duplication rule, stated once:** *a fact lives at exactly one altitude.* Cross-cutting →
root, linked from modules. Module-specific → the module, never at root. The failure mode being
designed against is five copies of the hexagon explanation drifting independently.

**`docs/knowledge/` stays shared and single-source.** It is the hydrate protocol's index;
fragmenting it per module would mean an agent working in `tft-mcp-server` misses a gotcha written
in `lol-mcp-server`, which is exactly backwards.

**The sanity-check pass.** A review phase that leaves enforcement behind rather than a verdict:

- Every module has the full local doc set — **asserted by a test**, consistent with this repo's
  "enforced, not documented" philosophy.
- **The convention plugin holds no module-specific values.** The hardcoded `version` was an
  instance of exactly the class of bug this pass exists to catch: a module-specific fact living at
  the shared altitude. Same rule as the docs rule, applied to build config.
- The convention plugin genuinely carries the shared build config; no module has drifted into
  copy-pasted wiring.
- `api` vs `implementation` still reflects the real public surface after Phases 2–3 widened it.
- No `com.wkaiser` reference survives anywhere — sources, build files, docs, or CI.
- No root doc restates a module fact; no module doc restates a shared one.
- The ArchUnit slice rule still holds with league added, and the account-library confinement rule
  accounts for the resolver's new consumers. **The resolver is the live test of that rule:**
  `PlayerIdentityResolver` lives in `riot-account-core`, and every context that resolves a `player`
  param depends on it — so the deny-by-default list that today names only `analytics` and `account`
  must be revisited deliberately, not widened reflexively.
- No ArchUnit rule carries a fully-qualified package in its *condition* (the vacuous-pass class of
  bug found in Phase 0).

## Handoff contract — what 1b inherits

Stated explicitly so 1b is mechanical:

> A new context is one package under the server root containing `domain/`, `application/` +
> `application/port/`, `adapter/out/riot/`, and `adapter/in/mcp/`. The service depends on
> `PlayerIdentityResolver` and its own port — never on `RestClient`, never on another context's
> service. The tool takes a single `player` param and is named `<game>_<context>_<action>`. Tests
> are a WireMock adapter test plus a port-fake service test. Endpoint paths are verified against
> the live developer portal. League is the reference implementation.

**The success criterion for 1a is falsifiable:** *1b must add five contexts without modifying
either library.* If 1b needs to touch `riot-api-core` or `riot-account-core`, 1a under-delivered,
and that is a finding worth recording rather than quietly absorbing.

## Testing

The existing strategy is unchanged and non-negotiable: WireMock for outbound adapters, in-memory
port fakes for application services, **offline with no API key**. What is new:

- **Retry** (`riot-api-core`): WireMock scenarios — 429-then-200 succeeds, `Retry-After` is
  honoured, attempts are bounded, exhaustion raises a clear exception. **No real sleeps** — the
  backoff clock is injected, so the suite stays fast and deterministic.
- **Error mapping**: each status maps to its typed reason and message; the raw body remains
  reachable.
- **Resolver** (`riot-account-core`): port-fake tests for both input forms, the invalid form, cache
  hit (one Riot call across two lookups), and TTL expiry — on an injected clock.
- **League**: WireMock adapter test + port-fake service test, per the template.
- **Auto-config slice tests**: the existing `ApplicationContextRunner` tests extend to the resolver
  bean.
- **Docs presence**: a test asserting every module carries README, ARCHITECTURE, and CHANGELOG.
- **`verifyRelease`**: version ↔ changelog agreement, run as part of `check`.
- **ArchUnit negative controls** (Phase 0): each package-string-dependent rule is shown to fail when
  fed a violation. This is not a normal test — it is the only evidence that distinguishes "passing"
  from "vacuously passing", and a green build cannot supply it.

## Verification

Green tests prove the code compiles and units behave. They do not prove the server serves. Reusing
sub-project 0's bar:

- **Both transports handshake** — SSE on 8080 and stdio, listing tools and calling one. Stdio's
  stdout must remain pure JSON; no unit test catches a stray log line.
- **Tool inventory matches the Phase 6 table exactly** — seven tools, named as listed. Here the
  inventory is *expected* to change, and that table is the statement of the break; in sub-project 0
  an unchanged inventory was the proof of purity. Same assertion, opposite expected value.
- **Endpoint paths verified against the live Riot developer portal.**
- **Provenance stamping is real**: inspect a built image's labels and confirm the embedded library
  versions are recorded and correct.

## Non-goals

Each is real and wanted; each is also a reason a test could fail for something other than this
cycle:

- The five 1b contexts (champion-mastery, champion, challenges, status, clash), and a tool for the
  existing `match` context
- Token-bucket / proactive rate limiting
- Bearer/RSO auth
- Any non-LoL game
- A generalized host-routing abstraction (Valorant forces it; TFT reuses LoL's hosts)
- Publishing libraries as consumable Maven artifacts

## Risks

- **Two distinct renames land in one cycle** — the package/group rename (Phase 0) and the tool
  contract sweep (Phase 6). They are unrelated but both wide, and confusing them in a bisect would
  be easy. Mitigated by maximum separation: Phase 0 is pure motion at the very start, Phase 6 is
  behaviour at the end, and neither shares a commit with anything else.
- **The tool contract sweep is the widest behavioural churn** — every tool name and the `player`
  param change at once. Mitigated by ordering it after correctness and league, so a failure is
  unambiguous.
- **The cache is the first stateful thing in this codebase.** Bounded size, TTL, injected clock, no
  cross-request assumptions.
- **`riot-api-core` junk-drawer pressure.** ADR-0007 exists to hold the line; the sanity-check pass
  audits it.
- **Per-module versioning touches release, which is hard to test locally.** The `verifyRelease`
  gate runs on every build, but the tag-parsing path in `release.yml` only executes on a real tag.
  Exercise it via `workflow_dispatch` before trusting it.
- **Riot endpoint drift.** Sub-project 0 flagged Context7 as unreliable here. Portal verification is
  a task, not an assumption.

## Artifacts this cycle creates

| Artifact | Job |
|---|---|
| [`docs/knowledge/roadmap.md`](../../knowledge/roadmap.md) | **Living** program roadmap (0, 1a, 1b, 2, 3, 4) with status. Supersedes sub-project 0's snapshot table as source of truth; hydrated every session. |
| **ADR-0007 — Core hardening boundary** | `riot-api-core` owns HTTP and Riot's error protocol, never game domain. Retry honours `Retry-After`; why not a token bucket. |
| **ADR-0008 — Shared player-identity resolution** | Resolver in `riot-account-core`, TTL cache, why not core, why not per-context. |
| **ADR-0009 — MCP tool contract** | `<game>_<context>_<action>`, the single `player` param, the pre-1.0 break without aliases. |
| **ADR-0010 — Artifact coordinates, per-module versioning, and provenance stamping** | The `com.muddl` move and why group and package root must agree; why libraries are versioned but unpublished; the tag namespace; the changelog gate; why project references stay. |
| **`.claude/skills/prepare-release/SKILL.md`** | Operationalizes ADR-0010: identify what changed, classify the bump, write the entry, bump, verify, tag. Encodes the library→server cascade. |
| **`gotchas.md` entries** | Gradle subproject `gradle.properties` handling; the Riot-ID mutability constraint on the resolver cache; **ArchUnit rules whose *condition* carries a fully-qualified package pass vacuously when that package moves** — keep matchers relative. |
| **`patterns/add-a-bounded-context.md` update** | League becomes the named reference implementation; the handoff contract lands here. |
