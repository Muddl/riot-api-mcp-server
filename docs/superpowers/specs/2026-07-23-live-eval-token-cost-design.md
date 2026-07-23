# Live-eval token cost reduction

- **Date:** 2026-07-23
- **Status:** Approved, ready for planning

## Goal

Cut the per-dispatch token spend of the live eval harness (`eval/`) by bounding two oversized MCP
tool payloads and scoping the `sse` leg to a transport smoke set — then **measure** the reduction
against a captured baseline.

The payload change is not an eval-only accommodation. A tool that returns ~18,000 tokens by default
is bad MCP design for any consumer; the evals merely made the cost visible.

### Falsifiable success criteria

Measured on a `workflow_dispatch` of `live-eval.yml` after the change lands:

| Criterion | Baseline | Target |
|---|---|---|
| `stdio` input tokens | 1,250,624 | ≤ 300,000 |
| `sse` input tokens | 1,250,796 | ≤ 30,000 |
| Real cost per dispatch (both legs) | ~$2.60 | ≤ $0.35 |
| Task outcomes | 24 tasks; 2 failures on `stdio`, 1 on `sse` | No **new** failures; `test_champion_mastery_for_discovered_player` now passes |

Cost is computed at Claude Haiku 4.5's real rates (**$1.00/MTok input, $5.00/MTok output**), not
from mcp-eval's `cost_estimate` field — see [Measurement](#measurement).

## Baseline

Captured from live-eval run `30011353019` (branch `fix/tft-eval-agent`, 2026-07-23), both legs:

- **1,250,624 input / 10,246 output tokens** per transport (the two legs agree within 0.03%).
- Input is **99.2%** of spend. Output is negligible; this is entirely a context-inflow problem.

Emitted tool-result volume, aggregated across all 24 tasks:

| Tool | Calls | Avg result | Total emitted |
|---|---|---|---|
| `lol_league_apex_by_tier` | 11 | ~17,900 tok | ~197k |
| `tft_league_apex_by_tier` | 5 | ~14,800 tok | ~74k |
| `lol_challenges_by_player` | 1 | ~9,700 tok | ~9.7k |
| `lol_match_by_id` / `tft_match_by_id` | 2 | ~6,000 tok | ~12k |
| all 16 other tools | 19 | ~90 tok | ~1.7k |

The apex tools return the **entire CHALLENGER ladder (~300 entries)**. Because that lands on turn 1
of a 3–4 iteration agent chain, it is re-sent on every subsequent iteration — roughly **58% of all
input tokens**. The per-test costs show it starkly: any task touching apex costs ~77k input tokens;
tasks with the same tool count and iteration depth that avoid it cost ~5.9k.

`lol_challenges_by_player` has the same shape: a ~150-token useful head (`totalPoints`,
`categoryPoints`) followed by a `challenges[]` array of ~500 rows that is ~99% of the payload.

`lol_match_by_id` is **not** in scope. Full match detail *is* that tool's contract; capping it would
gut the tool.

### Rejected lever: prompt caching

The obvious answer for repeated prompts does not apply here, for two independent reasons:

1. `mcp-agent` 0.2.6 never emits `cache_control` (verified against the published wheel — zero
   occurrences anywhere in the package). Enabling it would require patching or forking a dependency.
2. Haiku 4.5's minimum cacheable prefix is **4,096 tokens**. The fixed system + tool-schema prefix
   is ~2,800 tokens, so caching would silently never engage even if wired up.

Recorded here so it is not re-investigated.

## Part 1 — Bound the oversized payloads

Three tools gain an optional `count` parameter and a **capped default**. Tool names and existing
parameter descriptions are unchanged; only the default response size shrinks. An explicit large
`count` still returns everything, so no data becomes unreachable.

### `lol_league_apex_by_tier` / `tft_league_apex_by_tier`

- New optional `Integer count`, following the established `lol_champion_mastery_by_player` idiom.
- **Default: 10 entries.**
- Entries are sorted by `leaguePoints` **descending** before slicing. Riot does not guarantee entry
  order, so without this "top N" is meaningless and the eval's discovered subject changes run to run.
- `LeagueList` gains a `totalEntries` field carrying the **pre-truncation** count (~5 tokens). Without
  it an agent would reasonably conclude NA1 has only 10 Challengers.

**Truncation happens in the application service** (`LeagueService.getApexLeague`), not the outbound
adapter. This differs from `ChampionMasteryService`, which passes `count` down to its port — Riot's
Champion-Mastery-V4 has a native `count` query parameter, whereas League-V4 and TFT-League-V1 apex
endpoints always return the whole league. Same MCP-level contract, two different implementation
layers, chosen by whether Riot supports it upstream. The adapter stays a dumb mapper and the hexagon
dependency rules are untouched.

### `lol_challenges_by_player`

- New optional `Integer count` bounding the `challenges[]` array **only**. `totalPoints` and
  `categoryPoints` always return in full — they are the summary and cost ~150 tokens.
- **Default: 10 entries**, sorted by `percentile` **ascending** (lower percentile = rarer
  achievement), so the capped view is the player's strongest challenges.
- `ChallengesPlayerData` gains `totalChallenges`, pre-truncation.
- Truncation in `ChallengesService`.

### Explicitly out of scope

`tft_league_rated_ladder_by_queue` and `tft_league_by_id` have the same unbounded shape but are
called by no eval, so capping them saves nothing against this goal. The ADR records them as
known-unbounded with the same remedy available. (`tft_league_entries_by_tier` is already page-bounded
by Riot.)

### Expected effect

Apex ~18k → ~600 tokens; challenges ~8.7k → ~250. With the per-iteration re-send multiplier, per-
transport input drops from ~1.25M to roughly **240k**.

## Part 2 — Transport-scoped eval coverage

`stdio` keeps the full 24-task suite. `sse` runs a smoke set proving the transport wiring, not the
tool logic — the two legs currently run identical tasks and produce identical results, paying twice
for one signal.

### Selection mechanism

`mcp-eval run` accepts multiple positional path specs, including pytest-style `file.py::func`. It has
**no `-k`, no markers, and no tag system** (verified against `mcp_eval/runner.py` in the pinned
0.1.10 release), so the subset must be expressed as explicit paths.

The list lives in **`eval/smoke.txt`** — newline-delimited `file.py::func` specs, `#` comments
allowed — not hardcoded in CI YAML, so selection sits where someone adding a test will see it. The
workflow feeds it to `mcp-eval` via `xargs`.

### The smoke set

Chosen for what is genuinely transport-specific: connection, discovery, a round-trip per server, and
error propagation. That last one matters most — `live-eval.yml` already notes that Spring AI's MCP
layer masks server-side exceptions over sse.

| Task | Proves | Cost |
|---|---|---|
| `test_handshake.py::test_lists_tools` | sse handshake + tool discovery | 2.9k |
| `test_status.py::test_status_platform` | LoL round-trip over sse | 5.9k |
| `test_tft_status.py::test_tft_status_platform` | TFT round-trip over sse (separate port) | 5.3k |
| `test_canaries.py::test_invalid_platform` | error surfaces through the transport | 5.9k |

≈ 20k input tokens. No test files move; all four stay where they are. None touch apex, so their cost
is stable after Part 1.

### Workflow changes

- The `[stdio, sse]` matrix is unchanged — both legs still build jars, run in parallel, and upload
  artifacts.
- Only the run step differs: `stdio` passes `tests/`; `sse` passes the specs from `smoke.txt`.
- **The job summary must name the set that ran and its task count.** Today both legs print an
  identical "✅ All live evals passed"; post-change that would let a green sse leg be mistaken for
  full coverage.

## Part 3 — Fix the `test_champion_mastery_for_discovered_player` rubric

This task failed on `stdio` and passed on `sse` in the baseline. The cause is **not** nondeterminism
— it is a prompt/rubric mismatch, and sse passed only on judge variance.

- The prompt asks for *"the top champion and its mastery point value"* — singular.
- The rubric grades *"The answer reports at most 3 champion mastery entries…"*.

The agent obeyed the prompt, reported one champion, and the judge marked it down for not listing
three. The rubric asserts on something the prompt never requested.

### Fix

Move the `count`-parameter assertion out of the LLM judge and into a structural check, then narrow
the rubric to what the prompt actually asks for:

- Add `Expect.tools.called_with("lol_champion_mastery_by_player", {"count": 3})`. `ToolCalledWith`
  does **subset** argument matching (verified in `mcp_eval/evaluators/tool_called_with.py`), so the
  other parameters need not be named. This is deterministic and costs zero judge tokens.
- Remove the "at most 3 entries" clause from the rubric. Retain: names the top champion by points,
  non-negative point value, says so clearly if the player has no mastery data, no tool error.

The prompt is left alone — it already requests the top-3 limit, which is what makes the structural
assertion meaningful.

The general lesson (for `gotchas.md`): **never ask an LLM judge to verify something the prompt did
not request.** The judge grades the response against the rubric without seeing that the prompt asked
for less.

### Not in scope

`test_champion_rotation` failed on both legs because Riot returned unavailable. That is an infra
flake by the harness's own triage table, not a defect. It must be excluded from the post-change
comparison, not "fixed". (It is not in the smoke set, so post-change it runs on `stdio` only.)

## Part 4 — Eval prompt impact

**The eval prompts do not change.** Because Part 1 caps the *default* rather than adding an opt-in
parameter, `"Get the CHALLENGER apex league… pick any one player"` works verbatim and simply receives
10 entries. This was the main reason for choosing a capped default over a purely additive parameter.

One exception. `test_apex_challenger` asks *"How many entries does it contain?"* with a rubric
accepting "a non-zero number of entries". Post-change that silently becomes an assertion about a
truncated view. Retarget the rubric at `totalEntries` so it keeps asserting the real ladder size;
otherwise the test quietly gets weaker.

## Testing

The offline suite (`./gradlew build`) remains the CI gate and stays key-free and network-free.

**Application services — in-memory port fakes.** For `LeagueService` (LoL and TFT) and
`ChallengesService`:

- default cap applied when `count` is `null`
- explicit `count` honoured
- `count` exceeding available entries returns everything, without error
- zero and negative `count` **clamp to the default** (10) rather than returning an empty list or
  throwing — assert this explicitly so the behavior is pinned
- sort order is deterministic and correct (`leaguePoints` desc; `percentile` asc)
- `totalEntries` / `totalChallenges` reflect the **pre-truncation** size
- empty and `null` collections do not throw
- the `percentile` comparator is **null-safe** — Riot omits `percentile` on some challenges, and a
  naive comparator NPEs on live data while passing on hand-built fixtures

**Outbound adapters.** The existing WireMock tests must pass **completely unchanged**. That is the
signal the layer boundary held: adapters still fetch full payloads, and truncation is a service
concern. If an adapter test needs editing, the boundary leaked and the design is wrong.

**Gates.** JaCoCo will require the new branches covered. ArchUnit is unaffected — no new
cross-context dependencies. Spotless as usual.

## Measurement

Commit `eval/tools/report-cost.py`: reads a run's report JSON and prints per-test and per-tool token
tables plus a total. Evals run regularly, so this turns a one-off investigation into a standing
capability rather than analysis to be redone by hand each time.

**It must report token counts and cost computed at real Haiku 4.5 rates.** mcp-eval's own
`cost_estimate` field is unreliable here on two counts:

1. It prices at a **~$0.50/MTok fallback rate**, understating the real Haiku 4.5 bill by roughly 2×
   (the baseline's reported `$0.6304` is actually ~$1.30).
2. It **excludes LLM-judge calls entirely** — verified: `test_status_platform`'s 5,975 input tokens
   account for exactly the agent's two iterations with nothing left over for a judge call.

### Validation procedure

1. Record the baseline (already captured above from run `30011353019`).
2. Land Parts 1–4; confirm `./gradlew build` is green.
3. Dispatch `live-eval.yml`; download both artifacts.
4. Run `report-cost.py` over each leg's JSON.
5. Compare against the success criteria table, **excluding `test_champion_rotation`** from the
   pass/fail comparison as a known infra flake.

## Documentation and knowledge persistence

Per the repo's hydrate/persist protocol:

- **ADR-0016 — bounded list results for MCP tools.** Why a capped default over an opt-in parameter;
  why apex and challenges truncate in the service while champion mastery truncates at the port; the
  `totalEntries` / `totalChallenges` convention; `tft_league_rated_ladder_by_queue` and
  `tft_league_by_id` recorded as known-unbounded.
- **ADR-0017 — transport-scoped live-eval coverage.** What the sse smoke set does and does not prove,
  and why the full suite on both transports was redundant. Cross-reference ADR-0012.
- **`gotchas.md`** — two entries:
  - mcp-eval's `cost_estimate` uses a fallback rate (understates Haiku 4.5 by ~2×) and omits judge
    tokens; read token counts, not the cost field.
  - Never ask an LLM judge to verify something the prompt did not request (the champion-mastery
    rubric mismatch).
- **`patterns/live-eval-harness.md`** and **`eval/README.md`** — `smoke.txt` and how to read a cost
  report.
- **`add-live-eval` skill** — prompt the author to consider whether a new scenario belongs in
  `smoke.txt`.
- **CHANGELOG** — the three tool behavior changes, under the affected modules.
- **CLAUDE.md** — only if the tool-surface description needs amending; tool count is unchanged.

## Measured results

Post-change dispatch: run [`30027872244`](https://github.com/Muddl/riot-api-mcp-server/actions/runs/30027872244)
on `perf/live-eval-token-cost`, 2026-07-23. Baseline: run `30011353019`. Both priced with
`eval/tools/report-cost.py` at Claude Haiku 4.5 list rates ($1.00/MTok in, $5.00/MTok out) — not the
report's own `cost_estimate` field, which understates by roughly 2×.

### Tokens and cost

| Leg | Tasks | Input | Output | Cost |
|---|---|---|---|---|
| `stdio` baseline | 24 | 1,250,624 | 10,246 | $1.3019 |
| `stdio` after | 24 | 252,266 | 10,037 | $0.3025 |
| `sse` baseline | 24 | 1,250,796 | ~10,200 | ~$1.30 |
| `sse` after | 4 | 20,825 | 648 | $0.0241 |
| **Combined after** | — | **273,091** | **10,685** | **$0.3265** |

Input tokens fell 80% on `stdio` and 98% on `sse`. The two levers are separable: the payload bounds
account for the `stdio` drop, and the transport smoke set for the rest of the `sse` drop.

### The tool that drove it

| Tool | Calls | Avg tokens/call | Total |
|---|---|---|---|
| `lol_league_apex_by_tier` baseline | 11 | 17,932 | 197,252 |
| `lol_league_apex_by_tier` after | 11 | 638 | 7,018 |

A 96% drop per call. Because the tool lands on turn 1 of a 3–4 iteration agent chain and its result
is re-sent on every subsequent iteration, the saving compounds well beyond the raw payload size.
`tft_league_apex_by_tier` (5 calls) and `lol_challenges_by_player` (1 call) now average 636 and 465
tokens respectively.

### Success criteria

| Criterion | Target | Measured | Result |
|---|---|---|---|
| `stdio` input tokens | ≤ 300,000 | 252,266 | ✅ |
| `sse` input tokens | ≤ 30,000 | 20,825 | ✅ |
| Combined real cost | ≤ $0.35 | $0.3265 | ✅ |
| `test_champion_mastery_for_discovered_player` | passes | passes on `stdio` | ✅ |
| Any other task | no **new** failures | none | ✅ |

The `sse` leg was green on all four smoke tasks, and its job summary correctly reported
`transport smoke set (4 tasks)` while `stdio` reported `full suite` — the scope wiring works.

**One failure, excluded by the criteria above:** `test_champion_rotation` failed on `stdio` because
Riot returned "The Riot API is temporarily unavailable". It failed in the baseline on both legs for
the same reason, so it is an infra flake by the harness's own triage table, not a regression. It is
not in the smoke set, so post-change it runs on `stdio` only.
