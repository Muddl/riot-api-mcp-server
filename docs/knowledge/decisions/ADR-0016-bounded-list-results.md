# ADR-0016: Bounded list results for MCP tools

- **Status:** Accepted
- **Date:** 2026-07-23

## Context

`lol_league_apex_by_tier` returned the entire CHALLENGER ladder — roughly 300 entries, ~17,900
tokens per call. Because it typically lands on turn 1 of a 3–4 iteration agent chain and is
re-sent as context on every subsequent iteration, it accounted for ~58% of all live-eval input
tokens (measured on run `30011353019`: 1,250,624 input tokens per transport).
`lol_challenges_by_player` had the same shape: a ~150-token useful head (`totalPoints`,
`categoryPoints`) followed by a `challenges[]` array of ~500 rows that was ~99% of the payload.

Neither is an eval-only problem. A tool whose default response is ~18,000 tokens is bad MCP design
for any consumer, human or agent; the live eval only made the cost visible.

## Decision

Add an optional `count` param, with a **capped default of 10**, to:

- `lol_league_apex_by_tier`
- `tft_league_apex_by_tier`
- `lol_challenges_by_player`

Each response also stamps the pre-truncation size: `totalEntries` on the two league tools,
`totalChallenges` on challenges. A caller that received the capped list can still see the true
ladder/challenge count.

**Why a capped default rather than an opt-in param:** an opt-in leaves the naive call — the one a
real agent makes when it does not know to ask for a smaller page — at ~18k tokens, and would have
forced every existing eval prompt to be rewritten to add the param. Capping the default fixed the
cost with zero eval-prompt churn; a caller that wants more still can.

**Why the application layer, not the adapter:** Riot's League-V4 apex endpoint, TFT-League-V1's
apex endpoint, and LoL-Challenges-V1 have **no server-side count parameter** — the truncation has
to happen somewhere in our code, not Riot's. This differs from Champion-Mastery-V4, where
`ChampionMasteryService` pushes `count` down to the port because Riot's mastery endpoint *does*
accept one. Adapters stay dumb mappers either way, and the hexagon rules (`adapter → application →
domain`) hold.

**Champion mastery does *not* share this ADR's MCP-level contract — it only shares the parameter
name.** `ChampionMasteryService.getMasteryByPlayer` and `RiotChampionMasteryAdapter.getMasteryByPuuid`
forward `count` to Riot untouched (`.../top?count=" + count` when non-null, the unfiltered endpoint
otherwise):

- **No default.** A `null` count returns every mastery entry Riot has for the player — there is no
  10-entry fallback like the three tools above.
- **No zero/negative clamping.** `count` goes straight into the URL; whatever Riot does with `0` or a
  negative value is what the caller gets. The three tools this ADR governs explicitly clamp
  non-positive counts to the default (see the tests) — mastery has no equivalent guard.

This is a real inconsistency in the MCP surface, not a documentation nuance: an agent calling
`lol_champion_mastery_by_player` with no `count` gets an unbounded response, while the same shape of
call to the other three tools is capped. Aligning mastery (a default + clamping, applied in the
service so the port's pass-through stays simple) is a reasonable follow-up, but it is out of scope
for this branch — do not change the mastery code here.

**Ordering:** Riot does not guarantee entry order, so entries are sorted before slicing —
`leaguePoints` descending for the two league tools, challenge `percentile` ascending for challenges.
Two of the three sorts need a **null-safe** comparator and one does not, and the difference is the
field's declared type: TFT's `leaguePoints` is a boxed `Integer` that Riot may omit, and challenge
`percentile` is absent on challenges a player has not progressed on, so both sort nulls last; LoL's
`leaguePoints` is a primitive `int`, so `comparingInt` is safe there and no null handling is needed.
Without sorting first, "top N" is meaningless and a discovered eval subject would change between
runs of the same test.

**Open question — is "lower percentile = rarer/stronger" actually true?** The ascending-by-percentile
sort (and the original "10 strongest challenges" framing) rests on an assumption that a lower
`percentile` value means a rarer, more impressive achievement. This has **not been verified** against
a live Riot response or the developer portal — only that the comparator sorts ascending as coded; the
tests prove the mechanism, not the interpretation. If Riot's semantics are inverted, the tool returns
the player's ten *weakest* challenges instead, and every existing test would still pass. See the
gotchas entry for the open question. If it does invert, the fix is one comparator direction plus this
tool's description — no architectural change.

## Consequences

- A behavior change to three published tools: they now return fewer entries by default. Tool names
  and existing param descriptions are unchanged; `count` and the `total*` fields are additive.
  Logged in the `lol-mcp-server` and `tft-mcp-server` changelogs.
- `tft_league_rated_ladder_by_queue` and `tft_league_by_id` remain **known-unbounded** — no eval
  exercises either, so capping them was out of scope for this change. The same remedy (a capped
  `count` at whichever layer Riot's endpoint supports) applies if either ever shows up in a token
  budget. `tft_league_by_id` does stamp `totalEntries` for response-shape consistency with the apex
  tools, but it deliberately neither slices nor reorders its `entries`.
- `lol_match_by_id` / `tft_match_by_id` are explicitly out of scope: full match detail is that
  tool's contract, and capping it would gut the response it exists to provide.
