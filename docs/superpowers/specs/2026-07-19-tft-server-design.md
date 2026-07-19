# Sub-project 2 — `tft-mcp-server`

- **Date:** 2026-07-19
- **Status:** Approved, ready for planning
- **Roadmap row:** [#2 TFT server](../../knowledge/roadmap.md#2--tft-server-)

## Goal

Stand up a complete `tft-mcp-server` covering the Teamfight Tactics (TFT) Riot API surface, built on
the shared `riot-api-core` and `riot-account-core` libraries. This is the program's **first proof
that the core generalizes to a second game**.

**Parity is measured against the Riot TFT-v1 API, not against `lol-mcp-server`.** The LoL server is
the *architectural and implementation reference* — the mini-hexagon layout, the tool-contract
conventions, the testing discipline — not a copy source. Where the TFT API offers an endpoint LoL's
server never exposed (paged tier entries, league-by-id, the Hyper Roll rated ladder), TFT exposes it.

TFT reuses LoL's platform (`br1`, `na1`, `euw1`, `kr`, …) and region (`americas`/`asia`/`europe`/`sea`)
host schemes, so **no routing abstraction is introduced** — that stays deferred to sub-project 3
(Valorant), which is the first game that forces a third scheme.

### Falsifiable success criterion (carried from 1a)

`tft-mcp-server` must ship with **zero changes to `riot-api-core` or `riot-account-core`**. If a
library change is forced, that is a *finding* about where 1a under-delivered, recorded in the
knowledge base — not absorbed silently. (Generalizing the eval harness in `eval/` is Python tooling,
not a library change, and does not count against this criterion.)

## Module & build wiring

- New Gradle module `tft-mcp-server`, package root `com.muddl.riot.tft`, added to `settings.gradle`.
- `build.gradle` mirrors `lol-mcp-server`: `riot-java-conventions` + `org.springframework.boot`
  plugins; depends on `:riot-api-core` and `:riot-account-core`; test dependencies are
  `wiremock-standalone` plus `testFixtures(project(':riot-api-core'))` and
  `testFixtures(project(':riot-account-core'))`; the same provenance-stamping `bootJar` manifest
  block (records embedded `Riot-Api-Core-Version` / `Riot-Account-Core-Version` via lazy providers).
- Initial `version = '0.1.0'`.
- Both transports ship, exactly as LoL: `stdio` (default) and an `sse` profile (message endpoint
  `/mcp/messages`, port `8080`).

## Contexts & tool surface

**6 bounded contexts**, each a mini-hexagon (`domain/`, `application/` + `application/port/`,
`adapter/in/mcp/`, `adapter/out/riot/`), except `account`, which is tool-only and delegates to
`riot-account-core`:

`account` (tool-only) · `summoner` · `league` · `match` · `status` · `analytics`

**11 MCP tools**, named on the established `<game>_<context>_<action>` contract (ADR-0009), with the
single `player` parameter (a Riot ID `GameName#TAG` or a raw PUUID) on every player-keyed tool
(ADR-0008, ADR-0014):

| # | Tool | Riot endpoint | Routing | Parameters |
|---|------|--------------|---------|------------|
| 1 | `tft_account_by_player` | account-v1 (shared, `riot-account-core`) | region (internal) | `player` |
| 2 | `tft_summoner_by_player` | tft-summoner-v1 `/summoners/by-puuid/{puuid}` | platform | `platform`, `player` |
| 3 | `tft_league_entries_by_player` | tft-league-v1 `/by-puuid/{puuid}` | platform | `platform`, `player` |
| 4 | `tft_league_apex_by_tier` | tft-league-v1 `/challenger`·`/grandmaster`·`/master` | platform | `platform`, `tier` |
| 5 | `tft_league_entries_by_tier` | tft-league-v1 `/entries/{tier}/{division}` (paged) | platform | `platform`, `tier`, `division`, `page` |
| 6 | `tft_league_by_id` | tft-league-v1 `/leagues/{leagueId}` | platform | `platform`, `leagueId` |
| 7 | `tft_league_rated_ladder_by_queue` | tft-league-v1 `/rated-ladders/{queue}/top` | platform | `platform`, `queue` |
| 8 | `tft_match_ids_by_player` | tft-match-v1 `/matches/by-puuid/{puuid}/ids` | region | `region`, `player`, `start`, `count` |
| 9 | `tft_match_by_id` | tft-match-v1 `/matches/{matchId}` | region | `region`, `matchId` |
| 10 | `tft_status_platform` | tft-status-v1 `/platform-data` | platform | `platform` |
| 11 | `tft_analytics_player_matches` | *composed* (summoner + match) | platform + region | `player`, `platform`, `region`, `matchCount` |

Notes:

- Tools **3–7** are a **superset of what `lol-mcp-server` exposes**. LoL surfaces only apex-by-tier
  and entries-by-player; TFT additionally surfaces paged tier entries (`#5`), league-by-id (`#6`),
  and the TFT-specific Hyper Roll rated ladder (`#7`), because parity is against the API.
- Deprecated `by-summoner` league variants are **skipped** — they key on the encrypted summoner ID
  that the PUUID migration is retiring (ADR-0008). The by-PUUID variants supersede them.
- Every player-keyed tool resolves `player` → PUUID through `PlayerIdentityResolver` (TTL-cached, in
  `riot-account-core`), exactly as LoL does.
- **Endpoint paths in this table are the design intent, not verified fact.** Per the program's
  standing constraint, exact TFT-v1 paths, deprecation state, and RSO gating are **verified against
  the live Riot developer portal during implementation** and captured into WireMock fixtures — never
  assumed from model knowledge or Context7. The live eval harness then re-verifies them post-merge.

## Domain / DTOs

This is where the genuinely new code lives — TFT is not a find-and-replace of LoL.

- **`match` (net-new shape).** A TFT match is `metadata` + `info`. Each participant carries
  `placement` (1–8), `level`, `gold_left`, `last_round`, `players_eliminated`,
  `total_damage_to_players`, `companion`, `traits[]` (`name`, `num_units`, `tier_current`,
  `style`), `units[]` (`character_id`, `tier`, `rarity`, `itemNames[]`), and `augments[]`. This is
  structurally unlike LoL's `Participant` and is the bulk of the modeling effort.
- **`league` (net-new).** Entry DTO carries `puuid`, `leagueId`, `queueType` (`RANKED_TFT`), `tier`,
  `rank`, `leaguePoints`, `wins`, `losses`, the boolean flags, and `miniSeries`. The **rated-ladder**
  entry is a separate DTO: `puuid`, `ratedTier`, `ratedRating`, `wins`,
  `previousUpdateLadderPosition`.
- **`summoner` (thin).** `puuid`, `profileIconId`, `revisionDate`, `summonerLevel`. The `name` /
  encrypted-`id` fields are treated as **possibly absent** under the PUUID migration; the domain does
  not depend on them.
- **`status` (thin).** `platform-data` shape: `id`, `name`, `maintenances[]`, `incidents[]`.
- **`analytics.domain.PlayerMatchAnalytics` (net-new, TFT-native stats).** Fields:
  `riotId`, `summonerLevel`, `matchCount`, `avgPlacement`, `top4Rate`, `firstPlaceRate`, `avgLevel`,
  `avgGoldLeft`, `mostPlayedTraits` (top 3 active traits), `mostPlayedUnits` (top 3 by
  `character_id`). No KDA — placement and top-4 rate are TFT's equivalents.

All DTOs follow the project convention: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` +
`@JsonIgnoreProperties(ignoreUnknown = true)`; nested `@Builder` static classes also carry
`@NoArgsConstructor @AllArgsConstructor` (see `gotchas.md`).

### Analytics behaviour

`tft_analytics_player_matches` resolves the PUUID once, reads the summoner for the summary, fetches
`matchCount` recent match IDs, then each match, and aggregates the caller's own participation:

- `avgPlacement` = mean placement across analysed games (guarded: `.average().orElse(0)`).
- `top4Rate` = share of games with `placement <= 4`; `firstPlaceRate` = share with `placement == 1`.
- `avgLevel`, `avgGoldLeft` = means of the respective participant fields.
- `mostPlayedTraits` / `mostPlayedUnits` = top-3 frequency counts over active traits / fielded units.

## Testing & gates

The full suite runs **offline with no Riot API key** (WireMock for adapters, port fakes for
services). Non-negotiable.

- **Outbound adapters → WireMock** (skill: `add-adapter-test`): assert request URL, the
  `X-RIOT-TOKEN` header, JSON → DTO parsing, and error mapping, for each of `summoner`, `league`
  (all five endpoints), `match`, and `status`.
- **Application services → in-memory port fakes.** `AnalyticsService` must cover the **zero-games**
  case (no recent matches → `matchCount: 0` summary, no divide-by-zero) and a **single-game /
  all-top-4 boundary** — the TFT analogs of LoL's zero-games and zero-death-KDA edge cases.
- **ArchUnit** (`HexagonalArchitectureTest` reusing the shared `HexagonRules` from
  `riot-api-core` test fixtures, plus a negative-control test):
  - Standard hexagon rules (inward dependency; `RestClient` only in `adapter.out.riot`; `@McpTool`
    only in `adapter.in.mcp`; ports are `*Port` interfaces in `application.port`; services in
    `application`).
  - Cross-context slice rule over `..riot.tft.(*)..` with **two deliberate composition edges**:
    `analytics → summoner` and `analytics → match`.
  - Account-domain confinement: only `..tft.analytics..` and `..tft.account..` may touch
    `..riot.account..`, **excluding** `..riot.account.identity..` (identity resolution stays open to
    every context — it returns a plain PUUID string, not a `RiotAccount`).
- **`McpToolInventoryTest`** asserts the exact **11-tool** TFT inventory.
- **JaCoCo coverage + Spotless** ride the `riot-java-conventions` plugin — the same CI gate as every
  module.
- **Live evals** (skill: `add-live-eval`): one happy-path `@task` per tool (discover the subject
  from a keyless tool — e.g. seed a player from `tft_league_apex_by_tier` CHALLENGER — then chain to
  the tool under test; assert `tools.was_called` plus an LLM-judge rubric on an *invariant*, never an
  exact live value). Add canaries where a tool depends on a Riot error/edge behaviour.

### Eval-harness multi-server generalization

The harness (`eval/`) is currently single-server and LoL-only (`mcpeval.yaml` wires exactly one
`lol_server` / `LOL_MCP_JAR`). Adding TFT forces generalizing it to N servers:

- Add a `tft_server` entry to `mcpeval.stdio.yaml` / `mcpeval.sse.yaml`, driven by a new `TFT_MCP_JAR`
  (stdio) and `TFT_MCP_SSE_URL` (sse) env var.
- Add a TFT tester agent definition (or generalize the existing one) and TFT test files under
  `eval/tests/`.
- Update `eval/README.md` and the `live-eval.yml` workflow to build and evaluate both server jars.

This is net-new Python tooling. It is **not** a library change and does not affect the falsifiable
criterion above.

## Non-goals (deferred, with a home)

- **RSO / Bearer `/me` endpoints** (`tft-summoner-v1 /summoners/me`, `accounts/me`) — `RiotApiClient`
  has no RSO support. Deferred to when a server needs it (sub-project 4 / LoR decks). YAGNI until then.
- **Generalized host-routing abstraction** — TFT reuses LoL's hosts, so it is still one data point.
  Forced by sub-project 3 (Valorant).
- **Deprecated `by-summoner` league endpoints** — retired by the PUUID migration; the by-PUUID
  variants cover the need.

## Release & knowledge persist

- Cut `tft-mcp-server 0.1.0` via the `prepare-release` skill: CHANGELOG entry, version bump,
  module-scoped tag.
- Update the knowledge base: mark roadmap **#2 Done** with a link to this spec; refresh `README.md`
  and `ARCHITECTURE.md` to describe **two** servers; add the per-module `tft-mcp-server/README.md`
  (the `verifyModuleDocs` gate requires it). Persist any new pitfall to `gotchas.md` and any new
  domain term (TFT set, trait, unit, augment, rated ladder) to `glossary.md`.
- Record the falsifiable-criterion result: did TFT land without touching either library? State it
  explicitly in the roadmap's #2 progress note.

### Standing user-owed gates (carry forward, do not claim passed)

Both require a live `RIOT_API_KEY` and a human in the loop, per the program's standing constraints:

1. **Live transport handshake** over stdio + sse (`initialize` → `tools/list` → one `tools/call`,
   asserting stdout JSON purity on stdio).
2. **Endpoint-path verification** of every TFT-v1 path against the live Riot developer portal
   (continuously re-verified post-merge by the live eval harness).
