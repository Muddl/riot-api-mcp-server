# Live Eval Harness — Design Spec

**Date:** 2026-07-17
**Status:** Approved
**Author:** Wade Kaiser (with Claude)

## Purpose

Add a **live, agent-driven regression and acceptance suite** for `lol-mcp-server`, run on CI against
the real Riot Games API using the [mcp-eval](https://mcp-eval.ai/) framework
([`/lastmile-ai/mcp-eval`](https://github.com/lastmile-ai/mcp-eval)).

This automates the two verification steps the program has carried as **manual, human-owed standing
constraints** since sub-project 1a — the live transport handshake and endpoint-path verification —
and adds a class of coverage the offline suite structurally cannot provide: catching **changes in
Riot's live API behavior, including undocumented behavior** our adapters depend on.

It is a **pre-1b workflow enhancement**. It does not supersede any original design goal; it enhances
the development workflow by raising confidence in changes. The offline WireMock/port-fake suite
remains the authoritative pre-merge gate, unchanged and non-negotiable.

## Why this, why now

The roadmap already records these as deferred, manual, and requiring a human with a live key:

- **Automated transport-handshake verification** — a harnessed `initialize` + `tools/list` + one
  `tools/call` over both stdio and SSE, asserting every stdout line parses as JSON (stdio stdout
  purity). "Green tests do not prove the server serves."
- **Automated endpoint-path verification** — checking adapter Riot paths against the live portal
  programmatically, so "verified against the portal" stops depending on a human opening a browser.

The user's framing — *"address the manual checks that were not able to be automated in the previous
few implementation plans"* — maps directly onto these two items. This spec closes them.

### The key insight: WireMock encodes assumptions; live negatives validate them

The offline suite tests our code against **stubs we wrote** — frozen encodings of what we *believe*
Riot does. Those tests pass forever even if Riot silently changes its error semantics. A live
**negative test is the only thing that catches** "Riot changed unknown-PUUID from `404` to `400`" or
"not-in-a-game stopped being a `404`." Negative tests therefore become **behavior canaries**: they
fail loudly when reality drifts from what our adapters bank on. This is the highest-value coverage
the harness adds.

## Decisions (settled during brainstorming)

| Decision | Choice | Rationale |
|---|---|---|
| Test mode | **Agent-driven only** (no deterministic direct-call layer) | The WireMock suite already covers the deterministic request/parse/error contract; a direct-call live layer would duplicate it. The unique value is real-path verification, real serving, and agent-usability. |
| LLM provider | **`ANTHROPIC_API_KEY`** (hard requirement) | mcp-eval's agent + LLM-judge assertions need a real provider key. The preconfigured `CLAUDE_CODE_OAUTH_TOKEN` is for `claude-code-action` and will **not** authenticate mcp-eval's Anthropic SDK. |
| Missing-key behavior | **Skip with a notice + green exit**, plus rich diagnostic logging | Keeps `master` from going permanently red before the secret is provisioned; "just works" once added. Diagnostics distinguish a missing key from a real failure. |
| Transports | **Both stdio + sse** | Matches the standing constraint that every cycle verify both, including stdio stdout purity. |
| Test subjects | **Dynamic discovery, self-seeding** | No hardcoded accounts to rot; guarantees a live subject even for spectator. |
| Negatives | **Behavior-canary layer** (Tier A) + light input-validation (Tier B) | Canaries catch undocumented Riot behavior drift; validation confirms the agent gets a usable error. |
| Trigger | **`push` to `master` + `workflow_dispatch`** (not PRs) | Post-merge regression signal; conserves the dev key's rate budget; never blocks a merge. |
| Placement | **Post-merge, non-blocking** | Live + LLM variance belongs on an informational signal, not a blocking pre-merge gate. |

## Architecture

### A Python harness beside the Java monorepo

mcp-eval is Python; the server is a built jar. The harness lives in a new top-level directory and is
never compiled by Gradle:

```
eval/                      # new — the live-eval harness (Python, uv-managed)
  mcpeval.yaml             # provider/model + the server-under-test (stdio & sse)
  pyproject.toml           # pins mcpevals; uv-locked
  uv.lock
  tests/
    conftest.py            # session-scoped "discovery seeding" fixtures
    test_league.py         # per-context agent scenarios + assertions
    test_summoner.py
    test_spectator.py
    test_analytics.py
    test_account.py
    test_canaries.py       # negative / behavior-canary layer
  README.md                # how to run locally + interpret reports
```

### How mcp-eval drives the server

Gradle builds the jar in CI; mcp-eval then launches it as the server-under-test:

- **stdio** — `mcpeval.yaml` server command is
  `java -jar <bootJar> --spring.profiles.active=stdio`, with env
  `RIOT_API_KEY=${RIOT_DEV_REG_TEST_API_KEY}`. A successful handshake here **is** the stdout-purity
  proof: any stray stdout log line corrupts the JSON-RPC stream and the session dies. No separate
  assertion is needed — connection success is the check.
- **sse** — CI boots the server on `:8080` as a background step (`--spring.profiles.active=sse`);
  mcp-eval connects over HTTP to the SSE endpoint. The exact `mcpeval.yaml` transport keys and
  connect URL (Spring AI's `/mcp/messages` message endpoint vs. the SSE stream path) are confirmed
  during implementation against the mcp-eval configuration reference.

### What this closes

- **Transport handshake** (both transports, `initialize` → `tools/list` → `tools/call`, stdout
  purity) — proven every merge.
- **Endpoint-path verification** — a wrong Riot path returns 404 → the tool call fails → the eval
  fails. Live evals therefore *continuously* verify adapter paths against real Riot, replacing the
  manual "open the portal" step.

### Relationship to the existing suite (unchanged)

- The offline WireMock/port-fake suite in `ci.yml` remains the **pre-merge gate**. It runs offline
  with no key. Non-negotiable, untouched.
- Live evals are a **post-merge regression signal**. They never block a merge and never require a
  key to build. They add confidence; they do not gate.

## Test design

### Authoring style

Python decorator/`@task` tests, **not** pure YAML datasets — dynamic discovery-seeding and
tool-chaining need real code. A **session-scoped `conftest.py` fixture** performs the discovery pass
once and shares the seeds across tests, which also conserves rate-limit budget.

### Self-seeding discovery chains

Nothing is hardcoded to rot; every subject is sourced from a broad/keyless tool that is always
live-valid:

| Seed (broad/keyless) | Yields | Feeds |
|---|---|---|
| `lol_league_apex_by_tier` CHALLENGER | a real top player's PUUID | summoner, league-entries, analytics |
| `lol_spectator_featured_games` | a PUUID **in a game right now** | spectator current-game |

If a seeding call itself fails (e.g., Riot 5xx / rate limit), the run is reported as an **infra
flake**, distinct from a test regression (see Outcome classification).

### Happy-path scenarios (one file per context)

The agent drives each tool, then the test attaches **hard (deterministic) + soft (LLM-judge)**
assertions. Example (league):

> Agent prompt: *"What rank is the player with PUUID `<seeded>` on NA1?"*
> - **Hard:** `Expect.tools.was_called("lol_league_entries_by_player")`,
>   `Expect.tools.success_rate(min_rate=1.0)` — right tool, no error.
> - **Soft:** `Expect.judge.llm("Answer states a ranked tier/division or clearly says the player is
>   unranked")` — an *invariant*, never an exact value.

Coverage spans all seven tools:
`lol_account_by_player`, `lol_summoner_by_player`, `lol_spectator_current_game_by_player`,
`lol_spectator_featured_games`, `lol_analytics_player_matches`, `lol_league_entries_by_player`,
`lol_league_apex_by_tier`.

### Robustness rules (baked in)

- **Never assert exact live values** (LP, match counts, names shift). Assert shape/invariants only.
- **Spectator "not in a game" is a valid outcome.** Seeding from featured-games guarantees a live
  subject, but rubrics still accept a well-formed "no active game" answer.
- **Analytics invariants** the judge can check regardless of data: KDA non-negative, counts respect
  the requested limit, win-rate ∈ [0, 100].

### Negative / behavior-canary layer (`tests/test_canaries.py`)

**Tier A — Riot-behavior canaries (the point of this layer).** Each guards a specific undocumented
assumption our code depends on:

| Probe | Guards the assumption | Fails when Riot… |
|---|---|---|
| Well-formed but **nonexistent PUUID** → summoner/league | unknown-PUUID ⇒ clean "not found" | flips 404↔400, or returns empty-200 |
| **Nonexistent Riot ID** (high-entropy `GameName#TAG`) → account resolve | account-v1 not-found is a 404 | changes account-not-found semantics |
| **Spectator not-in-a-game** invariant | the `404 → null` mapping in `RiotSpectatorAdapter` | returns empty-200 or a new code instead of 404 |

Because forcing "not in a game" is not deterministic, that probe asserts an **invariant, not a
state**: *spectator must return either a well-formed live game or a clean "no active game" — never a
raw/unmapped error.* It holds every run and still flips if Riot changes the signal.

**Tier B — Our-side input validation (lighter, acceptance-flavored).** Invalid platform (`ZZ9`),
bogus apex tier (`BRONZE`), malformed Riot ID (no `#`). These mostly re-cover unit tests; kept to a
couple. Their live value is confirming the **agent receives a usable error message**, not a stack
trace.

**Assertions:** a deterministic tool-error check where mcp-eval exposes one, plus an LLM-judge
invariant on the agent's answer (e.g., *"communicates the player was not found"*). A canary failure
is tagged distinctly — **"investigate Riot behavior change,"** not "your code regressed."

## CI workflow (`.github/workflows/live-eval.yml`)

- **Triggers:** `push` to `master` (post-merge) + `workflow_dispatch`. **Not** on pull requests —
  conserves the dev key's rate budget and keeps merges unblocked. An optional weekly `schedule` to
  catch Riot drift on quiet weeks is noted as an easy opt-in, **off by default**.
- **Shape:** build the jar once → `transport: [stdio, sse]` matrix → set up uv + Python 3.11 →
  `uv tool install mcpevals` → `mcp-eval run tests/`.
- **Key handling:** a guard step — no `ANTHROPIC_API_KEY` ⇒ `::notice::` + green (neutral/skipped)
  exit. Present ⇒ run.
- **Reports & diagnostics:** every run emits mcp-eval's JSON + HTML + Markdown reports, uploaded as
  **artifacts**, plus a GitHub **job summary**. The runner classifies each run's outcome (below).
- **Rate discipline:** `--max-concurrency 1`, session-scoped seeding (one discovery pass shared),
  modest test count — comfortably under the dev key's 100 req / 2 min. No deliberate 429-triggering
  probe (would burn budget; the retry path is already WireMock-tested).
- **Secrets:** `RIOT_DEV_REG_TEST_API_KEY` → the server's `RIOT_API_KEY` env; `ANTHROPIC_API_KEY` →
  mcp-eval (user-provisioned).
- **Permissions:** least-privilege per the existing `ci.yml` convention; only the scopes the report
  upload / summary steps need.

### Outcome classification (the diagnostic requirement)

Every run resolves to exactly one class, surfaced in the job summary:

| Class | Meaning | Signal |
|---|---|---|
| **missing-key** | `ANTHROPIC_API_KEY` absent | Green skip + notice to add the secret |
| **infra-flake** | Rate-limit / network / Riot 5xx during seeding or a call | Loud log, **not** a code regression; re-run |
| **regression** | Happy-path assertion failed | Real failure — the change broke a tool |
| **canary-drift** | A Tier-A canary flipped | Riot behavior changed — investigate the adapter's assumption |

## Skills & documentation

### Net-new

- **Skill `add-live-eval` (project skill)** — how to add an eval case + canary when adding a
  tool/context, so every 1b context ships its live coverage. Mirrors the existing `add-adapter-test`
  / `add-mcp-tool` skills.
- **`docs/knowledge/patterns/live-eval-harness.md`** — run locally, read the reports, triage the
  four outcome classes.
- **New ADR (`ADR-0012`)** — adopting mcp-eval for live regression: the agent-driven rationale, the
  WireMock-vs-live division of labor, the canary concept, and the post-merge/non-blocking placement.
- **`eval/README.md`** — the harness entry point.

### Updates to existing docs

- **`roadmap.md`** — record this as a pre-1b track; move the two deferred verification items from
  "manual/deferred" to "automated"; soften the two matching Standing Constraints to "automated
  post-merge; the offline suite remains the pre-merge gate."
- **`gotchas.md`** — mcp-eval sharp edges: Python-in-a-Java-repo, live nondeterminism, the rate
  budget, and the stdout-purity/stdio interplay.
- **`CLAUDE.md`** — build/test commands + the live-eval workflow in the development loop.
- **`README.md`** — mention the live-eval suite + an optional status badge.
- **`CONTRIBUTING.md`** — how to run the evals locally (uv, the `ANTHROPIC_API_KEY`, a personal
  `RIOT_API_KEY`).

## Testing (of the harness itself)

The harness's own correctness is verified by:

- **A successful CI run** over both transports with real keys — the acceptance test of the harness.
- **The missing-key path** — remove/blank `ANTHROPIC_API_KEY` locally and confirm the green skip +
  notice.
- **A deliberate canary check during development** — point a canary at a known-good PUUID to confirm
  the "no active game / not found" invariants behave, and confirm a forced-wrong path (e.g., a
  typo'd tool arg) is caught.

No change to the offline suite's guarantees: it still runs offline with no key.

## Non-goals

- **No deterministic direct-call live layer.** The WireMock suite owns the deterministic contract.
- **No change to the offline suite or the pre-merge gate.** `ci.yml` is untouched.
- **No new game, context, or tool.** This is workflow, not feature work. The five 1b contexts and
  the `match` tool remain 1b's scope.
- **No deliberate rate-limit (429) probe.** Budget-expensive; the retry path is already
  WireMock-tested.
- **No PR-triggered live runs.** Post-merge + dispatch only.
- **No proactive/token-bucket rate limiting.** Unchanged deferred item.

## Success criteria

1. A `workflow_dispatch` run (and every `master` merge) executes the eval suite over **both stdio and
   sse** against live Riot, with `ANTHROPIC_API_KEY` present, and passes.
2. Removing `ANTHROPIC_API_KEY` yields a **green skip with a clear notice**, not a red failure.
3. All **seven tools** are exercised via self-seeded discovery chains; no hardcoded accounts.
4. The **Tier-A canaries** assert Riot behavior invariants (unknown-PUUID, account-not-found,
   spectator `404 → null`) and are tagged as canary-drift on failure.
5. Reports (JSON/HTML/MD) are uploaded as artifacts and a job summary classifies the outcome.
6. The roadmap's two deferred verification items are recorded as automated; the two Standing
   Constraints are updated to reflect post-merge automation.
7. The net-new skill, pattern, ADR, and `eval/README.md` exist, and `CLAUDE.md` / `README.md` /
   `CONTRIBUTING.md` / `gotchas.md` are current.
