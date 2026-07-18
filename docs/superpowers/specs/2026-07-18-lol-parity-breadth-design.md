# LoL Parity — Breadth — Design Spec

**Date:** 2026-07-18
**Status:** Approved
**Author:** Wade Kaiser (with Claude)

## Purpose

This spec covers **sub-project 1b**: add the remaining five League of Legends contexts —
`champion-mastery`, `champion`, `challenges`, `status`, `clash` — and expose the existing `match`
context with its first inbound MCP tool. Every piece is built **mechanically against 1a's `league`
template**; 1b adds no new architecture, only breadth.

1a's organizing principle was *"everything a second game server would otherwise copy gets built once,
in a library."* 1b is the falsifiable test of that claim:

> **Success criterion — 1b must add all five contexts and the match tools without modifying either
> library** (`riot-api-core`, `riot-account-core`). If it needs to touch a library, 1a
> under-delivered, and that is a finding to record — not to absorb quietly.

## Scope, from the roadmap

Per [`docs/knowledge/roadmap.md`](../../knowledge/roadmap.md), 1b is *"the remaining five contexts …
built mechanically against 1a's template,"* plus *"a tool for the existing `match` context."* The
roadmap is the living source of truth; this spec is a dated snapshot of the decision at 2026-07-18
and is not edited retroactively.

## Current state (verified 2026-07-18)

`lol-mcp-server` ships **six** `@McpTool` methods across five tool classes (`RiotAccountTool`,
`SummonerTool`, `LiveGameTool`, `AnalyticsTool`, `LeagueTool`) after `lol_spectator_featured_games`
was removed ([ADR-0013](../../knowledge/decisions/ADR-0013-remove-featured-games.md)). The `league`
context is the worked reference implementation of the 1a handoff contract: a full mini-hexagon
(`domain/`, `application/` + `application/port/`, `adapter/out/riot/`, `adapter/in/mcp/`) whose
service depends on its own port and `PlayerIdentityResolver`, whose tool takes a single `player`
param and is named `<game>_<context>_<action>`.

The `match` context already has a `domain`, `MatchService`, `MatchPort`, and a WireMock-tested
`RiotMatchAdapter` — but **no inbound adapter**. `analytics` is its only consumer, so match data is
reachable today only through an analytics summary, never directly.

## The template stress 1b introduces

1a's handoff contract speaks only to **player-keyed** contexts (a single `player` param resolved to
a PUUID). 1b is the first place that assumption is stressed: three of its tools have **no player at
all**.

| Context | Keyed by | Player-keyed template fits? |
|---|---|---|
| champion-mastery | PUUID (player) | ✅ yes |
| challenges | player data (config/percentiles skipped) | ✅ yes (for the chosen surface) |
| clash | player registrations (tournaments/teams skipped) | ✅ yes (for the chosen surface) |
| champion (rotation) | nothing — platform-wide free rotation | ❌ no player |
| status | nothing — platform status | ❌ no player |
| match — `by_id` | match ID | ❌ no player |

**Decision: extend the contract** (recorded as ADR-0014, below) rather than defer the non-player
contexts or leave the template silently contradicted. A tool takes domain-appropriate params; the
single `player` param convention applies *only* when the endpoint is player-keyed. Non-player-keyed
contexts do not inject `PlayerIdentityResolver`.

## Tool inventory — 7 new tools, 13 total

Under a **pragmatic-breadth** surface (one canonical tool per context, a second only where it clearly
earns its place — consistent with `league`'s two-tool shape and CLAUDE.md's "architecture over
breadth" framing):

| # | Context | Tool | Routing | Keyed by | Notes |
|---|---|---|---|---|---|
| 1 | champion-mastery | `lol_champion_mastery_by_player` | platform | player | List of masteries sorted by points; optional `count` caps to top-N (Riot's `/top`) |
| 2 | champion | `lol_champion_rotation` | platform | — | Free-to-play champion rotation |
| 3 | challenges | `lol_challenges_by_player` | platform | player | Player challenge summary; config/percentiles skipped |
| 4 | status | `lol_status_platform` | platform | — | Platform status / incidents |
| 5 | clash | `lol_clash_by_player` | platform | player | Player's Clash team registrations; tournament/team admin skipped |
| 6 | match | `lol_match_ids_by_player` | region | player | Recent match IDs; optional `count`/`start`/`queue` |
| 7 | match | `lol_match_by_id` | region | match ID | Full match detail for one game |

That is the concrete statement of 1b's addition: the surface grows from **6 → 13** while capability
grows across five new Riot API areas.

**Deliberately skipped** (YAGNI, recorded so they are not re-derived): champion-mastery
`by-champion` and total-`score` as separate tools; challenges `config` / `percentiles` /
per-challenge leaderboards; clash `tournaments` / `teams` / `by-team` lookups.

### Naming note

`champion-mastery` and `champion` are distinct contexts. Their tools read cleanly side by side —
`lol_champion_mastery_by_player` vs `lol_champion_rotation` — with no collision. Package names carry
no hyphen; the mastery package is `championmastery`.

## Per-context build

Each context is a full mini-hexagon under `com.muddl.riot.lol.<context>` following
[`add-a-bounded-context.md`](../../knowledge/patterns/add-a-bounded-context.md). DTOs keep the house
pattern (`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown =
true)`, nested builders included — see `gotchas.md`).

- **Non-player contexts — `champion`, `status`.** No `PlayerIdentityResolver`. Platform-routed GET,
  no player param. The simplest possible shape, so they are built first and prove the non-player
  variant of the template.
- **Player-keyed contexts — `champion-mastery`, `challenges`, `clash`.** Exact `league` shape: the
  service injects the port and `PlayerIdentityResolver`, resolves the `player` param to a PUUID, then
  calls the port. Platform-routed.
- **`match` (existing context, new inbound adapter).** `MatchService` gains a
  `PlayerIdentityResolver` dependency and a `getMatchIdsByPlayer(region, player, count, start, queue)`
  method that resolves `player`→PUUID and delegates to the existing `getMatchIdsByPuuid` port method.
  `analytics` keeps its current PUUID path unchanged — it resolves identity itself and does more than
  fetch IDs, so there is no reason to reroute it. A new `MatchTool` in `match.adapter.in.mcp` exposes
  both tools; `lol_match_by_id` is region-routed and match-ID-keyed (non-player).

  This is the **live proof of 1a's resolver rule-split**: `match` now depends on
  `PlayerIdentityResolver` (open to every context) while the account *domain* stays confined to
  `analytics` and `account`. It happens with **zero** library change, which is exactly the falsifiable
  criterion being met.

**Endpoint paths are verified against the live Riot developer portal**, never assumed from Context7 or
model knowledge — a standing constraint, and a named task in the plan, not an assumption. The tool
shapes in this spec are the design; the exact paths are portal-checked at implementation time.

## ADR-0014 — Non-player-keyed tools extend the tool contract

A new ADR records the contract extension so servers 2–4 inherit it from the decision log, not by
reading code:

- A tool takes **domain-appropriate params**. The single `player` param convention
  ([ADR-0009](../../knowledge/decisions/ADR-0009-mcp-tool-contract.md)) applies **only** to
  player-keyed endpoints.
- Non-player-keyed contexts do **not** inject `PlayerIdentityResolver`.
- ADR-0009 gains a cross-link forward to ADR-0014; ADR-0014 references ADR-0009 as the base contract
  it extends.
- [`add-a-bounded-context.md`](../../knowledge/patterns/add-a-bounded-context.md) gains the
  non-player-keyed variant alongside the player-keyed reference.

## Testing

The existing strategy is unchanged and non-negotiable: WireMock for outbound adapters, in-memory port
fakes for application services, **offline with no API key**.

- **Each context** — a WireMock adapter test (assert request URL, `X-RIOT-TOKEN`, JSON→DTO parsing,
  error mapping) plus a port-fake service test. Player-keyed service tests mock the resolver;
  non-player service tests do not.
- **`match`** — the existing `RiotMatchAdapter` WireMock test already covers the outbound calls; the
  new work is a `MatchService` test for `getMatchIdsByPlayer` (resolver mocked) and the `MatchTool`.
- **`McpToolInventoryTest`** — updated to assert the **13-tool** inventory by exact name. This is the
  offline statement of the addition, the mirror of 1a's seven-tool assertion.
- **Live eval** — per the [`add-live-eval`](../../knowledge/patterns/live-eval-harness.md) skill, a
  scenario per new tool over both transports, run post-merge. Never gates a merge.

## Versioning and release

- **`lol-mcp-server` 0.1.0 → 0.2.0** — additive minor bump under pre-1.0 rules. Seven new tools, no
  breaking change.
- **No library bump.** `riot-api-core` and `riot-account-core` are untouched. "No library modified"
  is an **explicit checked deliverable** of this cycle, not an afterthought — it is the falsifiable
  criterion from 1a.
- The `verifyRelease` gate keeps the version ↔ changelog heading aligned on every build; the
  `prepare-release` skill runs at the end to classify the bump, write the entry, and tag
  `lol-mcp-server/v0.2.0`.

## Sequencing — a single ordered plan

1a was split into four plans because it mixed pure motion, behaviour change, and a public contract
break. 1b has none of that tension: it is uniform, additive, and mechanical. It is therefore **one
implementation plan**, ordered simplest → hardest so the build stays green at every commit:

1. **Non-player contexts** — `champion` (rotation), `status`. No resolver; simplest shape.
2. **Player-keyed contexts** — `champion-mastery`, `challenges`, `clash`. The `league` shape.
3. **`match`** — resolver on `MatchService`, new `MatchTool`, analytics coexistence.
4. **Contract + docs + release** — ADR-0014, `add-a-bounded-context.md` non-player variant,
   `McpToolInventoryTest` to 13, `ARCHITECTURE.md` context list, roadmap → 1b ✅, `CHANGELOG.md`,
   version bump, `prepare-release`.

TDD throughout: failing test first, green build at every commit.

## Persistence (knowledge base)

- `docs/knowledge/roadmap.md` — 1b marked ✅ Done with this spec linked.
- `lol-mcp-server/ARCHITECTURE.md` — the five new contexts added to the context list; the shared
  hexagon rationale stays at root and is linked, never restated.
- `lol-mcp-server/CHANGELOG.md` — the `## [0.2.0]` entry.
- ADR-0014 (new) + ADR-0009 cross-link.
- `add-a-bounded-context.md` — the non-player-keyed variant.
- `gotchas.md` — any DTO-shape surprise found during implementation (see Risks).

## Non-goals

Each is real and wanted; each is also a reason a test could fail for something other than this cycle:

- Challenges `config` / `percentiles` / leaderboards.
- Clash `tournaments` / `teams` / `by-team` lookups.
- Champion-mastery `by-champion` and total-`score` as separate tools.
- **Any change to `riot-api-core` or `riot-account-core`.**
- Any non-LoL game (sub-projects 2–4).
- Token-bucket / proactive rate limiting; Bearer / RSO auth; a generalized host-routing abstraction.

## Risks

- **DTO shape surprises.** `status` (locale-keyed incident content), `challenges`, and `clash` nest
  structures more than `league` did. Mitigated by the nested-builder gotcha rule and portal-verified
  paths; any surprise is appended to `gotchas.md`.
- **Hidden library pressure.** If a context reveals a genuine gap in `riot-api-core` or
  `riot-account-core`, that is the 1a finding — recorded as a finding, not absorbed silently. The
  design expects none: every context is platform- or region-routed GET, both already supported.
- **`match` / `analytics` coexistence.** Adding the resolver to `MatchService` must not disturb
  analytics' existing PUUID path. Mitigated by keeping the PUUID port method intact and adding the
  player-resolving method alongside it, covered by a service test.

## Artifacts this cycle creates

| Artifact | Job |
|---|---|
| Five new contexts under `com.muddl.riot.lol.*` | `champion-mastery`, `champion`, `challenges`, `status`, `clash` — full mini-hexagons. |
| `MatchTool` + `MatchService.getMatchIdsByPlayer` | The existing `match` context's first inbound adapter and its player-resolving entry point. |
| **ADR-0014 — Non-player-keyed tools extend the tool contract** | Domain-appropriate params; `player` applies only to player-keyed endpoints; cross-linked with ADR-0009. |
| `add-a-bounded-context.md` update | The non-player-keyed variant of the template. |
| `McpToolInventoryTest` at 13 tools | The offline statement of the addition. |
| Roadmap / ARCHITECTURE / CHANGELOG updates + `lol-mcp-server/v0.2.0` tag | 1b marked done; the server re-released with no library bump. |
