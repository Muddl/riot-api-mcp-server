# Changelog — `tft-mcp-server`

The Teamfight Tactics MCP server. Published as `ghcr.io/muddl/tft-mcp-server`.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the
libraries keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.2.0] - unreleased

Live-eval token cost reduction, mirroring the `lol-mcp-server` change. See
[the design spec](../docs/superpowers/specs/2026-07-23-live-eval-token-cost-design.md) and
[ADR-0016](../docs/knowledge/decisions/ADR-0016-bounded-list-results.md).

### Changed
- **Breaking:** `tft_league_apex_by_tier` now returns only the **top 10 entries by league points**
  by default (previously the entire apex ladder). An optional `count` param requests more; the
  response now stamps `totalEntries` with the pre-truncation ladder size.
- `tft_league_by_id` now stamps `totalEntries` with its entry count, for response-shape consistency
  with the apex tools. It remains deliberately **unbounded and unsorted** — no `count` param, no
  reordering.

## [0.1.0] - 2026-07-20

Sub-project 2 — the first `tft-mcp-server` release, and the program's first proof that
`riot-api-core` / `riot-account-core` generalize to a second Riot game. Built entirely on the
existing shared core: **zero changes** to either library. See
[the design spec](../docs/superpowers/specs/2026-07-19-tft-server-design.md) and
[roadmap #2](../docs/knowledge/roadmap.md#2--tft-server-).

### Added
- **Six bounded contexts** under `com.muddl.riot.tft`: `account` (thin tool-only, delegating to
  `riot-account-core`), `summoner`, `league`, `match`, `status`, and the composing `analytics`
  context — the same mini-hexagon shape as `lol-mcp-server`.
- **11 MCP tools** on the `tft_<context>_<action>` contract (ADR-0009), every player-keyed tool
  taking a single `player` param (Riot ID `GameName#TAG` or a raw PUUID), resolved via the shared
  `PlayerIdentityResolver`:
  - `tft_account_by_player` — Riot account by player.
  - `tft_summoner_by_player` — TFT summoner profile by player (TFT-Summoner-V1).
  - `tft_league_entries_by_player`, `tft_league_apex_by_tier`, `tft_league_entries_by_tier`,
    `tft_league_by_id`, `tft_league_rated_ladder_by_queue` — TFT-League-V1's full surface. This is a
    **superset** of what `lol-mcp-server` exposes: paged tier entries and the Hyper Roll rated ladder
    have no LoL equivalent, since TFT parity is measured against the Riot TFT-v1 API, not against the
    LoL tool list.
  - `tft_match_ids_by_player`, `tft_match_by_id` — TFT-Match-V1 match IDs and full match detail. The
    match domain (`placement`, `traits[]`, `units[]`, `augments[]`) is structurally net-new, not a
    relocation of any LoL DTO.
  - `tft_status_platform` — platform status and incidents (TFT-Status-V1, non-player-keyed).
  - `tft_analytics_player_matches` — TFT-native aggregated analytics (`avgPlacement`, `top4Rate`,
    `firstPlaceRate`, `avgLevel`, `avgGoldLeft`, most-played traits and units). No KDA — TFT has none;
    placement and top-4 rate are its equivalents.
- Module-local `README.md` and `ARCHITECTURE.md` (the `verifyModuleDocs` gate requires both), and
  this `CHANGELOG.md`.
- Full offline test coverage: WireMock adapter tests for `summoner`, `league` (all five endpoints),
  `match`, and `status`; port-fake service tests including `AnalyticsService`'s zero-games and
  single-game/all-top-4 edge cases; `HexagonalArchitectureTest` (reusing `riot-api-core`'s shared
  `HexagonRules`) plus a negative control; `McpToolInventoryTest` asserting the 11-tool inventory.
