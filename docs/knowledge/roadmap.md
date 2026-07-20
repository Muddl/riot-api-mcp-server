# Program roadmap

The **living** plan for taking `riot-api-mcp-server` from one League of Legends server to a
monorepo of per-game MCP servers over a shared core. Read this during
[hydrate](README.md#hydrate--persist-protocol) to know where the program stands.

This file is the **source of truth for scope and sequencing**. Specs under
`docs/superpowers/specs/` are dated snapshots of a decision at a moment — they are history and are
not edited retroactively. When scope moves, it moves here.

## Status

| # | Sub-project | Status | Spec |
|---|---|---|---|
| 0 | Monorepo restructure + extract `riot-api-core` | ✅ Done | [2026-07-15](../superpowers/specs/2026-07-15-monorepo-restructure-design.md) |
| **1a** | **LoL parity — foundation** | ✅ Done | [2026-07-15](../superpowers/specs/2026-07-15-lol-parity-foundation-design.md) |
| 1b | LoL parity — breadth | ✅ Done | [2026-07-18](../superpowers/specs/2026-07-18-lol-parity-breadth-design.md) |
| 2 | TFT server | ✅ Done | [2026-07-19](../superpowers/specs/2026-07-19-tft-server-design.md) |
| 3 | Valorant server | ⏳ Not started | — |
| 4 | LoR server | ⏳ Not started | — |

## Scope

### 0 — Monorepo restructure ✅

Pure structural refactor. Extracted `riot-api-core` (HTTP, routing, errors) and `riot-account-core`
(the cross-game account context) as auto-configured libraries, with `lol-mcp-server` as the first
server. No endpoints added, no behaviour changed. See
[ADR-0006](decisions/ADR-0006-monorepo-split.md).

### 1a — LoL parity: foundation ✅

The first feature work on the new structure, and therefore the template the other four servers
inherit. Organizing principle: **everything a second game server would otherwise copy gets built
once, in a library, in this cycle.**

- Coordinates and package rename (`com.wkaiser` → `com.muddl`)
- Release engineering: per-module versioning, module-scoped tags, the version ↔ changelog gate,
  provenance stamping
- Core hardening: 429 retry honouring `Retry-After`, actionable error messages
- Shared identity: `PlayerIdentityResolver` in `riot-account-core`, TTL-cached
- LoL correctness: PUUID migration, spectator v4 → v5, removal of the three dead by-name tools
- **League** as the single exemplar context
- Tool contract sweep: `<game>_<context>_<action>`, the single `player` param
- Per-module docs and the monorepo sanity check

**Progress:** ✅ Complete. Plans A (coordinates + release engineering), B (library hardening: retry,
error taxonomy, identity resolver), C (LoL server: correctness, the League exemplar, the tool-contract
sweep), and D (per-module docs + the monorepo sanity check) all landed. Every module documents itself,
enforced by `verifyModuleDocs`; the sanity pass confirmed the convention plugin, dependency scopes,
and the absence of live `com.wkaiser` references. The handoff contract for 1b is in
[1a's spec](../superpowers/specs/2026-07-15-lol-parity-foundation-design.md#handoff-contract--what-1b-inherits).
This "Done" covers all planned implementation plus the automatable checks (unit/ArchUnit/JaCoCo/
Spotless, and `McpToolInventoryTest` asserting the seven-tool inventory). Two standing gates remain
**pending / user-owed**, carried forward rather than claimed passed: the live transport handshake
(stdio + sse, `initialize` → `tools/list` → one `tools/call`, asserting stdout JSON purity) and the
Spectator-V5 / League-V4 endpoint-path verification against the live Riot developer portal — both
require a live `RIOT_API_KEY` and a human in the loop, per the Standing constraints below.

**Split from 1b deliberately.** Sub-project 1 originally bundled correctness, six new contexts, the
contract sweep, and conventions. Sub-project 0's lesson was that mixing pure motion with behaviour
change destroys the ability to tell which one broke something; the same holds for mixing a public
contract break with wide addition. Foundation-first also means the break lands once, on a small
surface, and 1b's tools are born correct.

### 1b — LoL parity: breadth ⏳

The remaining five contexts — champion-mastery, champion, challenges, status, clash — built
mechanically against 1a's template.

Also **a tool for the existing `match` context**. Match has a domain, service, port, and
WireMock-tested outbound adapter, but no inbound adapter — `analytics` is its only consumer, so
match data is reachable only through an analytics summary and never directly. Exposing it is
exactly the mechanical add-a-tool work 1b exists to do.

**1a's success criterion is falsifiable here:** *1b must add five contexts without modifying either
library.* If 1b needs to touch `riot-api-core` or `riot-account-core`, 1a under-delivered — record
that as a finding rather than absorbing it quietly.

The handoff contract 1b works from is stated in
[1a's spec](../superpowers/specs/2026-07-15-lol-parity-foundation-design.md#handoff-contract--what-1b-inherits).

**Progress:** ✅ Complete. Five contexts added — `champion` (rotation), `status`, `championmastery`,
`challenges`, `clash` — plus the `match` context's first inbound tools (`lol_match_ids_by_player`,
`lol_match_by_id`). The tool surface grew from 6 to 13. Non-player-keyed tools extend the contract
([ADR-0014](decisions/ADR-0014-non-player-keyed-tools.md)). **1a's falsifiable criterion held:** the
five contexts and the match tools landed with no change to `riot-api-core` or `riot-account-core`.
`lol-mcp-server` released as 0.2.0.

### 2 — TFT server ✅

The first real test of whether the core generalizes to a second game. TFT reuses LoL's
platform/region host schemes, so it exercises the module template and the shared libraries without
forcing a routing abstraction.

**Progress:** ✅ Complete. `tft-mcp-server` shipped with 6 bounded contexts (`account` tool-only,
`summoner`, `league`, `match`, `status`, `analytics`) and **11** MCP tools, including a superset of
LoL's league surface (paged tier entries, league-by-id, and the Hyper Roll rated ladder) and a
TFT-native `tft_analytics_player_matches` (average placement, top-4 rate, most-played traits/units —
no KDA, since TFT has none). Full offline suite green: WireMock adapter tests for summoner, league (all
five endpoints), match, and status; port-fake service tests including `AnalyticsService`'s
zero-games and single-game/all-top-4 edge cases; `HexagonalArchitectureTest` (reusing the shared
`HexagonRules`) plus a negative control; `McpToolInventoryTest` asserting the 11-tool inventory.
Released as `tft-mcp-server 0.1.0`.

**The falsifiable criterion held.** `tft-mcp-server` shipped with **zero changes** to `riot-api-core`
or `riot-account-core` — verified across every implementation task, including the dedicated Task 8
ArchUnit/architecture review: no file under either library was modified to build the second server.
The only non-Java changes were net-new Python tooling in `eval/` (generalizing the live-eval harness
to N servers), which the spec explicitly carves out as not counting against this criterion. This is
the program's first real evidence the sub-project-0/1a core generalizes, not just a repeated claim.

Two standing gates remain **pending / user-owed**, carried forward rather than claimed passed, exactly
as for 1a/1b: the live transport handshake (stdio + sse) and TFT-v1 endpoint-path verification against
the live Riot developer portal (continuously re-verified post-merge by the live eval harness once its
TFT generalization lands) — both require a live `RIOT_API_KEY` and a human in the loop.

### 3 — Valorant server ⏳

Introduces a **third host-routing scheme** (AP/BR/EU/KR/LATAM/NA/ESPORTS) — this is what forces the
generalized routing abstraction that sub-project 0 deliberately deferred. Also the first server
needing **production-key gating**: `val-match-v1` / `val-ranked-v1` return 403 to a personal dev
key.

### 4 — LoR server ⏳

Smallest surface. **Verify LoR is not in maintenance before investing.** Parts of it need a
production key, and deck endpoints need Bearer/RSO auth, which `RiotApiClient` does not support.

## Deferred, with a home

Real and wanted, deliberately not scheduled. Recorded so they are not re-derived:

| Item | Where it lands |
|---|---|
| Proactive / token-bucket rate limiting | Own cycle, if evidence demands it. 1a ships reactive 429 retry, which is the behaviour Riot actually specifies. |
| Bearer / RSO auth | Arrives when a server needs it — `accounts/me`, or LoR decks (sub-project 4). YAGNI until then. |
| Generalized host-routing abstraction | Forced by sub-project 3. TFT reuses LoL's hosts, so one data point is not enough to design from. |
| Publishing libraries as Maven artifacts | Not planned. Libraries are versioned for provenance and consumed by project reference — see ADR-0010. |
| Aggregate coverage report | When more than one server exists. |
| ~~Claude Code Actions integration rework~~ | **Shipped** ([ADR-0015](decisions/ADR-0015-repo-maintenance-automation.md)). The PR reviewer now posts real reviews, `@claude` can respond, and a weekly `/housekeeping` cron opens maintenance PRs — all on `CLAUDE_CODE_OAUTH_TOKEN`, separate from live-eval's `ANTHROPIC_API_KEY`. |
| ~~Automated endpoint-path verification~~ | **Shipped** as part of the live eval harness (see [ADR-0012](decisions/ADR-0012-live-eval-harness.md)). Live agent-driven evals call every tool against the real Riot API post-merge; a wrong path returns 404 and fails the eval, so paths are verified continuously rather than by a human opening the portal. |
| ~~Automated transport-handshake verification~~ | **Shipped** as part of the live eval harness (see [ADR-0012](decisions/ADR-0012-live-eval-harness.md)). The suite runs over both stdio and sse each post-merge run; a successful stdio session is the stdout-purity check. |

## Standing constraints

These hold across every sub-project:

- **The suite runs offline with no Riot API key.** WireMock for outbound adapters, port fakes for
  application services. Non-negotiable.
- **Riot endpoint paths are verified against the live developer portal**, never assumed from
  Context7 or model knowledge. Sub-project 0 found Context7 returned mostly Data Dragon and
  Valorant/TFT material when asked for a structured LoL reference. *As of
  [ADR-0012](decisions/ADR-0012-live-eval-harness.md) this is automated post-merge by the live eval
  harness (`eval/`), over both transports; the offline suite remains the pre-merge gate.*
- **Green tests do not prove the server serves.** Every cycle verifies both transports with a real
  MCP handshake, including stdio's stdout purity. *As of
  [ADR-0012](decisions/ADR-0012-live-eval-harness.md) this is automated post-merge by the live eval
  harness (`eval/`), over both transports; the offline suite remains the pre-merge gate.*
- **The intended consumer is a third party** installing against their own Riot API key. That raises
  the bar on tool naming, error messages, and key-gating behaviour.
