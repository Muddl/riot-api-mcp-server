# Live Eval Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a live, agent-driven mcp-eval regression + acceptance suite for `lol-mcp-server`, run on CI against the real Riot API over both stdio and sse, closing the two manual "standing constraint" checks and adding behavior-canary coverage.

**Architecture:** A self-contained Python harness under `eval/` (uv-managed, never compiled by Gradle) drives the built server jar through mcp-eval. Tests are decorator-style `@task` agent scenarios; discovery chains run inside the agent conversation (no hardcoded accounts). A new `.github/workflows/live-eval.yml` builds the jar, runs the suite over a `[stdio, sse]` matrix post-merge, skips green when `ANTHROPIC_API_KEY` is absent, and uploads JSON/HTML/MD reports. New skill + ADR + pattern + doc updates land alongside.

**Tech Stack:** Python 3.11, `uv`, `mcpevals` (mcp-eval), Anthropic provider; the existing Java 21 / Gradle / Spring Boot 4.1 / Spring AI 2.0 server as the system-under-test; GitHub Actions.

## Global Constraints

- **The offline suite is untouched and remains the pre-merge gate.** `ci.yml`, WireMock/port-fake tests, and the "suite runs offline with no key" guarantee do not change. (spec: Global / Non-goals)
- **Agent-driven only.** No deterministic direct-tool-call test layer — the WireMock suite owns that. (spec: Decisions)
- **Token isolation.** The live-eval workflow reads **only** `ANTHROPIC_API_KEY`; it never references `CLAUDE_CODE_OAUTH_TOKEN`. `mcpeval.yaml` server `env` is set explicitly and does not inherit the OAuth token. `claude.yml` is not modified. (spec: Decisions / Secrets / Non-goals)
- **`ANTHROPIC_API_KEY` is user-provisioned and may be absent.** Absent ⇒ green skip + a `::notice::` explaining how to enable, plus diagnostic logging that distinguishes missing-key from infra-flake from regression from canary-drift. (spec: Decisions / Outcome classification)
- **Both transports.** Every run exercises stdio (proves stdout stays pure JSON) and sse. (spec: Decisions)
- **Never assert exact live values.** Assert shape/invariants only (tier present, KDA non-negative, win-rate ∈ [0,100], counts respect limits). (spec: Robustness rules)
- **Dynamic discovery, nothing hardcoded to rot.** Player subjects are found via `lol_league_apex_by_tier` CHALLENGER and `lol_spectator_featured_games`, inside the agent conversation. (spec: Test design)
- **Triggers:** `push` to `master` + `workflow_dispatch` only — never on PRs. (spec: CI workflow)
- **Rate discipline:** `max_concurrency: 1`, no deliberate 429 probe. Comfortably under the dev key's 100 req / 2 min. (spec: CI workflow)
- **Post-merge, non-blocking.** The live suite never gates a merge. (spec: Placement)
- **The seven tools** (exact names, do not alter): `lol_account_by_player`, `lol_summoner_by_player`, `lol_spectator_current_game_by_player`, `lol_spectator_featured_games`, `lol_analytics_player_matches`, `lol_league_entries_by_player`, `lol_league_apex_by_tier`.

## Deviation from spec (transparent)

The spec described a "session-scoped `conftest.py` seeding fixture." This plan implements the same **intent** (dynamic, self-seeding discovery, no hardcoded accounts) as **agent-driven multi-tool prompts** instead — the agent performs the apex/featured-games discovery within one conversation and chains into the player-specific tool. This is strictly more agent-driven, uses only verified mcp-eval API, and removes a fragile structured-extraction fixture. Rate budget is unaffected (`max_concurrency: 1`, ~a dozen tests). No `conftest.py` seeding module is created.

## File Structure

```
eval/                          # NEW — Python harness, uv-managed, not built by Gradle
  pyproject.toml               # pins mcpevals; project metadata
  mcpeval.stdio.yaml           # config for the stdio transport leg
  mcpeval.sse.yaml             # config for the sse transport leg
  .gitignore                   # ignore active mcpeval.yaml copy, reports, .venv
  README.md                    # run locally + read reports + triage outcomes
  tests/
    test_handshake.py          # LLM-free-ish connectivity + inventory sanity
    test_league.py             # apex + entries (discovery chain)
    test_summoner.py           # summoner via discovered player
    test_analytics.py          # analytics invariants
    test_spectator.py          # featured-games + current-game chain
    test_account.py            # account by Riot ID / PUUID
    test_canaries.py           # Tier-A behavior canaries + Tier-B input validation
.github/workflows/live-eval.yml  # NEW — post-merge live eval, [stdio, sse] matrix
.claude/skills/add-live-eval/SKILL.md  # NEW — project skill (path confirmed in Task 12)
docs/knowledge/decisions/ADR-0012-live-eval-harness.md   # NEW
docs/knowledge/patterns/live-eval-harness.md             # NEW
docs/knowledge/README.md       # MODIFY — link ADR-0012 + the new pattern
docs/knowledge/roadmap.md      # MODIFY — automate the two deferred items; soften constraints
docs/knowledge/gotchas.md      # MODIFY — mcp-eval sharp edges
CLAUDE.md                      # MODIFY — build/test commands + live-eval loop
README.md                      # MODIFY — mention live evals
CONTRIBUTING.md                # MODIFY — run evals locally
.gitignore                     # MODIFY (root) — if needed for eval artifacts
```

---

### Task 1: Harness skeleton — `pyproject.toml`, `.gitignore`, and a passing `mcp-eval doctor`

**Files:**
- Create: `eval/pyproject.toml`
- Create: `eval/.gitignore`
- Create: `eval/mcpeval.stdio.yaml` (minimal, filled out in Task 2)

**Interfaces:**
- Produces: the `eval/` uv project and the `mcp-eval` CLI on PATH for every later task.

- [ ] **Step 1: Write `eval/pyproject.toml`**

```toml
[project]
name = "lol-mcp-server-evals"
version = "0.1.0"
description = "Live agent-driven mcp-eval regression suite for lol-mcp-server"
requires-python = ">=3.11"
dependencies = [
    "mcpevals",
]

[tool.uv]
package = false
```

- [ ] **Step 2: Write `eval/.gitignore`**

```gitignore
# The active config is copied from mcpeval.<transport>.yaml at run time
mcpeval.yaml
# mcp-eval output
test-reports/
# uv / venv
.venv/
uv.lock
```

Note: `uv.lock` is intentionally ignored here because the harness is a dev tool, not a shipped artifact, and pinning is done by the `mcpevals` line. If reproducible CI installs later prove necessary, commit the lock in a follow-up.

- [ ] **Step 3: Create a placeholder `eval/mcpeval.stdio.yaml`** so `doctor` has something to read (replaced in Task 2)

```yaml
provider: anthropic
model: claude-haiku-4-5-20251001
mcp:
  servers:
    lol_server:
      transport: stdio
      command: java
      args: ["-version"]
```

- [ ] **Step 4: Install and verify the CLI**

Run:
```bash
cd eval && uv sync && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval doctor
```
Expected: `mcp-eval` installs; `doctor` runs and reports configuration status (it may warn about the missing `ANTHROPIC_API_KEY` — that is fine at this stage). No Python import errors.

- [ ] **Step 5: Confirm the CLI surface you will rely on later**

Run:
```bash
cd eval && uv run mcp-eval run --help && uv run mcp-eval --help
```
Expected: help text prints. **Record** the exact flags for report output (`--json`, `--html`, `--markdown`) and any `--config`/`-c` option. If the flags differ from those used in later tasks, adjust those tasks to match the real flags. This is the one place external CLI details are confirmed against reality.

- [ ] **Step 6: Commit**

```bash
git add eval/pyproject.toml eval/.gitignore eval/mcpeval.stdio.yaml
git commit -m "build(eval): scaffold uv-managed mcp-eval harness"
```

---

### Task 2: The two transport configs — `mcpeval.stdio.yaml` and `mcpeval.sse.yaml`

**Files:**
- Modify: `eval/mcpeval.stdio.yaml`
- Create: `eval/mcpeval.sse.yaml`

**Interfaces:**
- Produces: a `default_agent` named `riot_tester` connected to server `lol_server`, exposing all seven tools to tests. The jar path is read from `${LOL_MCP_JAR}`; the Riot key from `${RIOT_API_KEY}`.

- [ ] **Step 1: Write `eval/mcpeval.stdio.yaml`**

```yaml
$schema: https://raw.githubusercontent.com/lastmile-ai/mcp-eval/main/schema/mcpeval.config.schema.json
name: "lol-mcp-server live evals (stdio)"

provider: anthropic
model: claude-haiku-4-5-20251001

mcp:
  servers:
    lol_server:
      transport: stdio
      command: java
      args: ["-jar", "${LOL_MCP_JAR}", "--spring.profiles.active=stdio"]
      env:
        RIOT_API_KEY: "${RIOT_API_KEY}"

agents:
  definitions:
    - name: riot_tester
      instruction: |
        You are a precise QA agent for a League of Legends MCP server.
        Use the provided tools to answer. Prefer NA1 as the platform and
        AMERICAS as the region unless told otherwise. When a lookup fails,
        report the failure plainly instead of inventing data.
      server_names: ["lol_server"]
      max_iterations: 6
default_agent: riot_tester

judge:
  provider: anthropic
  model: claude-haiku-4-5-20251001
  min_score: 0.7
  temperature: 0.0

execution:
  max_concurrency: 1
  timeout_seconds: 300
  retry_failed: false
  fail_fast: false

reporting:
  formats: ["json", "markdown", "html"]
  output_dir: "./test-reports"
  include_traces: true
```

- [ ] **Step 2: Write `eval/mcpeval.sse.yaml`** — identical except the `lol_server` block connects over HTTP/SSE to an already-running server

```yaml
$schema: https://raw.githubusercontent.com/lastmile-ai/mcp-eval/main/schema/mcpeval.config.schema.json
name: "lol-mcp-server live evals (sse)"

provider: anthropic
model: claude-haiku-4-5-20251001

mcp:
  servers:
    lol_server:
      transport: sse
      url: "${LOL_MCP_SSE_URL}"

agents:
  definitions:
    - name: riot_tester
      instruction: |
        You are a precise QA agent for a League of Legends MCP server.
        Use the provided tools to answer. Prefer NA1 as the platform and
        AMERICAS as the region unless told otherwise. When a lookup fails,
        report the failure plainly instead of inventing data.
      server_names: ["lol_server"]
      max_iterations: 6
default_agent: riot_tester

judge:
  provider: anthropic
  model: claude-haiku-4-5-20251001
  min_score: 0.7
  temperature: 0.0

execution:
  max_concurrency: 1
  timeout_seconds: 300
  retry_failed: false
  fail_fast: false

reporting:
  formats: ["json", "markdown", "html"]
  output_dir: "./test-reports"
  include_traces: true
```

- [ ] **Step 3: Validate both configs load**

Run:
```bash
cd eval && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval doctor && cp mcpeval.sse.yaml mcpeval.yaml && uv run mcp-eval doctor
```
Expected: both report a valid config shape (a missing `ANTHROPIC_API_KEY`/unset `LOL_MCP_JAR`/`LOL_MCP_SSE_URL` warning is acceptable — you are only validating structure). If `doctor` rejects `transport: sse`, re-check Task 1 Step 5 output and use the transport keyword the installed version expects (e.g. `streamable_http`), updating this file.

- [ ] **Step 4: Commit**

```bash
git add eval/mcpeval.stdio.yaml eval/mcpeval.sse.yaml
git commit -m "build(eval): stdio and sse mcp-eval transport configs"
```

---

### Task 3: Confirm the live handshake locally over stdio (the stdout-purity check)

This task requires your own `RIOT_API_KEY` and `ANTHROPIC_API_KEY`. It is the manual acceptance that the harness actually drives the server; it produces the `test_handshake.py` file that later runs in CI.

**Files:**
- Create: `eval/tests/test_handshake.py`

**Interfaces:**
- Consumes: the `riot_tester` default agent from Task 2.
- Produces: proof the seven tools are reachable over a live transport; a smoke test other tasks build on.

- [ ] **Step 1: Write `eval/tests/test_handshake.py`**

```python
"""Connectivity + inventory smoke test.

A successful agent turn proves the MCP handshake completed over the active
transport. Over stdio this doubles as the stdout-purity check: any stray log
line on stdout corrupts the JSON-RPC stream and the session cannot connect.
"""

from mcp_eval import task, Expect


@task("Server serves and lists its tools over the active transport")
async def test_lists_tools(agent, session):
    # Asking the agent to enumerate its tools forces a connect + tools/list.
    response = await agent.generate_str(
        "List the names of every tool you have available. "
        "Return them as a plain comma-separated list."
    )
    # Invariant: the League ranked tools are present (a representative subset;
    # do not over-fit to all seven in prose, the agent may summarize).
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The response lists MCP tool names for a League of Legends "
                "server, including at least league, summoner, and spectator "
                "related tools. It must not claim to have no tools."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tools_listed",
    )
```

- [ ] **Step 2: Build the server jar**

Run (from repo root):
```bash
./gradlew :lol-mcp-server:bootJar
```
Expected: BUILD SUCCESSFUL; `lol-mcp-server/build/libs/lol-mcp-server-<version>.jar` exists (the non-`-plain` jar).

- [ ] **Step 3: Run the handshake test over stdio with your keys**

Run (from repo root; substitute the real jar path and your keys):
```bash
export ANTHROPIC_API_KEY=sk-ant-...          # your key
export RIOT_API_KEY=RGAPI-...                # your personal dev key
export LOL_MCP_JAR="$(ls lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain)"
cd eval && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval run tests/test_handshake.py -v
```
Expected: PASS. If it fails to connect, check that the jar path resolved, that `application-stdio.yml` keeps stdout clean, and that the profile arg took effect. A connection failure here **is** the stdout-purity signal — investigate a stray stdout write before anything else (see `gotchas.md`).

- [ ] **Step 4: Commit**

```bash
git add eval/tests/test_handshake.py
git commit -m "test(eval): live handshake + tool-inventory smoke test"
```

---

### Task 4: League happy-path — discovery chain (apex → entries)

**Files:**
- Create: `eval/tests/test_league.py`

**Interfaces:**
- Consumes: the `riot_tester` agent; tools `lol_league_apex_by_tier`, `lol_league_entries_by_player`.
- Produces: the discovery-chain pattern reused by summoner/analytics tests.

- [ ] **Step 1: Write `eval/tests/test_league.py`**

```python
"""League ranked-data live evals. Subjects are discovered at runtime from the
CHALLENGER apex league, so nothing is hardcoded to rot."""

from mcp_eval import task, Expect


@task("Apex league returns a populated CHALLENGER ladder")
async def test_apex_challenger(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for the RANKED_SOLO_5x5 queue on NA1. "
        "How many entries does it contain, and name one player in it?"
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_apex_by_tier"),
        name="apex_tool_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a CHALLENGER league with a non-zero number "
                "of entries and names at least one player. It does not report an "
                "error or an empty league."
            ),
            min_score=0.7,
        ),
        response=response,
        name="apex_populated",
    )


@task("Ranked entries resolve for a discovered top player")
async def test_entries_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player from it, then look up that same player's ranked league entries. "
        "Report the player's tier and rank."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_apex_by_tier"),
        name="apex_called",
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_entries_by_player"),
        name="entries_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer states a ranked tier and division (e.g. CHALLENGER, "
                "or a numeric LP/rank) for the selected player, OR clearly states "
                "the player is unranked in that queue. It must not report a tool "
                "error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="entries_report_rank",
    )
```

- [ ] **Step 2: Run over stdio with your keys**

Run (from repo root, env from Task 3 Step 3 still set):
```bash
cd eval && uv run mcp-eval run tests/test_league.py -v
```
Expected: both tasks PASS. If the apex tool is not called, tighten the prompt; if the judge fails on a genuinely unranked pick, that is still acceptable per the rubric — re-read the report before changing anything.

- [ ] **Step 3: Commit**

```bash
git add eval/tests/test_league.py
git commit -m "test(eval): league apex + entries discovery-chain evals"
```

---

### Task 5: Summoner happy-path (via discovered player)

**Files:**
- Create: `eval/tests/test_summoner.py`

**Interfaces:**
- Consumes: `lol_league_apex_by_tier` (discovery), `lol_summoner_by_player`.

- [ ] **Step 1: Write `eval/tests/test_summoner.py`**

```python
"""Summoner live eval. The subject is discovered from the CHALLENGER ladder."""

from mcp_eval import task, Expect


@task("Summoner resolves for a discovered top player")
async def test_summoner_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player, then look up that player's summoner information on NA1. "
        "Report the summoner level."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_summoner_by_player"),
        name="summoner_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a summoner with a positive summoner level "
                "(an integer >= 1). It does not report a tool error or a missing "
                "summoner."
            ),
            min_score=0.7,
        ),
        response=response,
        name="summoner_has_level",
    )
```

- [ ] **Step 2: Run over stdio**

Run:
```bash
cd eval && uv run mcp-eval run tests/test_summoner.py -v
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add eval/tests/test_summoner.py
git commit -m "test(eval): summoner discovery-chain eval"
```

---

### Task 6: Analytics happy-path (invariants only)

**Files:**
- Create: `eval/tests/test_analytics.py`

**Interfaces:**
- Consumes: `lol_league_apex_by_tier` (discovery), `lol_analytics_player_matches`.

- [ ] **Step 1: Write `eval/tests/test_analytics.py`**

```python
"""Analytics live eval. Asserts data-shape invariants that hold regardless of
which player is discovered or how they have been playing."""

from mcp_eval import task, Expect


@task("Analytics returns coherent aggregates for a discovered player")
async def test_analytics_invariants(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player, then analyze that player's 5 most recent matches on platform NA1 "
        "and region AMERICAS. Report their average KDA and win rate as a "
        "percentage."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_analytics_player_matches"),
        name="analytics_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports an average KDA that is a non-negative number "
                "and a win rate between 0% and 100% inclusive, over at most 5 "
                "matches. If the player has no recent matches, it clearly says so. "
                "It must not report impossible values (negative KDA, win rate > "
                "100%) or a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="analytics_invariants_hold",
    )
```

- [ ] **Step 2: Run over stdio**

Run:
```bash
cd eval && uv run mcp-eval run tests/test_analytics.py -v
```
Expected: PASS (allow one retry for rate limits — analytics fans out to match-v5).

- [ ] **Step 3: Commit**

```bash
git add eval/tests/test_analytics.py
git commit -m "test(eval): analytics invariants eval"
```

---

### Task 7: Spectator happy-path (featured-games → current-game chain)

**Files:**
- Create: `eval/tests/test_spectator.py`

**Interfaces:**
- Consumes: `lol_spectator_featured_games`, `lol_spectator_current_game_by_player`.

- [ ] **Step 1: Write `eval/tests/test_spectator.py`**

```python
"""Spectator live evals. Featured games always exist and guarantee a live
in-game subject to feed the current-game tool."""

from mcp_eval import task, Expect


@task("Featured games returns a non-empty list on NA1")
async def test_featured_games(agent, session):
    response = await agent.generate_str(
        "Get the current featured games on NA1. How many are there, and name a "
        "champion being played in one of them?"
    )
    await session.assert_that(
        Expect.tools.was_called("lol_spectator_featured_games"),
        name="featured_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports one or more featured games and names at least "
                "one champion or participant. It does not report an error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="featured_non_empty",
    )


@task("Current-game resolves for a player currently in a featured game")
async def test_current_game_for_featured_player(agent, session):
    response = await agent.generate_str(
        "Get the current featured games on NA1, pick one participant from a "
        "featured game, then look up that participant's current live game on NA1. "
        "Confirm whether they are in an active game and, if so, the game mode."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_spectator_current_game_by_player"),
        name="current_game_called",
    )
    # Invariant, not a fixed state: a featured participant is usually still in a
    # game, but the tool legitimately returns "not in a game" if it just ended.
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer either describes an active live game (game mode / "
                "participants) OR clearly states the player is not currently in a "
                "game. It must NOT surface a raw error, stack trace, or unmapped "
                "failure — 'not in a game' is a valid, clean outcome."
            ),
            min_score=0.7,
        ),
        response=response,
        name="current_game_clean_outcome",
    )
```

- [ ] **Step 2: Run over stdio**

Run:
```bash
cd eval && uv run mcp-eval run tests/test_spectator.py -v
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add eval/tests/test_spectator.py
git commit -m "test(eval): spectator featured + current-game chain eval"
```

---

### Task 8: Account happy-path (Riot ID and PUUID forms)

**Files:**
- Create: `eval/tests/test_account.py`

**Interfaces:**
- Consumes: `lol_account_by_player`, `lol_league_apex_by_tier` (to obtain a real player to name).

- [ ] **Step 1: Write `eval/tests/test_account.py`**

```python
"""Account live eval. Resolves a real, currently-existing player discovered from
the ladder, exercising the Riot ID -> PUUID path."""

from mcp_eval import task, Expect


@task("Account resolves for a discovered player and round-trips to a PUUID")
async def test_account_roundtrip(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1 and pick any "
        "one player. Look up that player's account information. Report their PUUID "
        "and, if available, their Riot ID (GameName#TAG)."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_account_by_player"),
        name="account_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports an account with a PUUID (a long alphanumeric "
                "identifier). It does not report that the account was not found or "
                "a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="account_resolved",
    )
```

- [ ] **Step 2: Run over stdio**

Run:
```bash
cd eval && uv run mcp-eval run tests/test_account.py -v
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add eval/tests/test_account.py
git commit -m "test(eval): account discovery + resolve eval"
```

---

### Task 9: Behavior canaries (`test_canaries.py`)

**Files:**
- Create: `eval/tests/test_canaries.py`

**Interfaces:**
- Consumes: `lol_summoner_by_player`, `lol_account_by_player`, `lol_spectator_featured_games`, `lol_spectator_current_game_by_player`, `lol_league_apex_by_tier`.
- Produces: the Tier-A/Tier-B canary set that flags Riot behavior drift.

- [ ] **Step 1: Write `eval/tests/test_canaries.py`**

```python
"""Negative / behavior-canary layer.

Tier A probes guard undocumented Riot assumptions our adapters depend on; a
failure here means "investigate a Riot behavior change", not "your code broke".
Tier B confirms our own input validation surfaces a usable error to the agent.

Do not "fix" a Tier-A failure by loosening the rubric — first confirm whether
Riot's live behavior actually changed.
"""

from mcp_eval import task, Expect

# A well-formed-looking but almost-certainly-nonexistent PUUID (78 chars).
FAKE_PUUID = "0" * 78
# A high-entropy Riot ID that should resolve to no account.
FAKE_RIOT_ID = "zzq9v7wxk2#zz999"


# --- Tier A: Riot-behavior canaries -----------------------------------------

@task("CANARY: unknown PUUID yields a clean not-found, not a crash")
async def test_unknown_puuid_summoner(agent, session):
    response = await agent.generate_str(
        f"Look up the summoner for the PUUID '{FAKE_PUUID}' on NA1. "
        "If it cannot be found, say so."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer clearly communicates that the summoner/player could "
                "not be found or that the lookup failed as a not-found. It must "
                "not fabricate summoner data and must not surface an unhandled "
                "stack trace."
            ),
            min_score=0.7,
        ),
        response=response,
        name="unknown_puuid_not_found",
    )


@task("CANARY: nonexistent Riot ID yields a clean not-found")
async def test_unknown_riot_id_account(agent, session):
    response = await agent.generate_str(
        f"Get the Riot account for the Riot ID '{FAKE_RIOT_ID}'. "
        "If no such account exists, say so."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer clearly communicates that no account was found for "
                "that Riot ID. It must not fabricate an account or surface an "
                "unhandled error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="unknown_riot_id_not_found",
    )


@task("CANARY: spectator 404->null mapping holds (never a raw error)")
async def test_spectator_not_in_game_invariant(agent, session):
    # Use a discovered ladder player, who is very often NOT currently in a game.
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player, then check whether that player is currently in a live game on "
        "NA1 using the current-game tool. Report the result."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_spectator_current_game_by_player"),
        name="current_game_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer states EITHER that the player is currently in a game "
                "(with some game detail) OR that they are not currently in a game. "
                "Both are valid. It must NOT surface a raw error, HTTP status, or "
                "stack trace — 'not in a game' must read as a clean, expected "
                "outcome."
            ),
            min_score=0.7,
        ),
        response=response,
        name="spectator_clean_no_game",
    )


# --- Tier B: our-side input validation --------------------------------------

@task("VALIDATION: invalid platform yields a usable error message")
async def test_invalid_platform(agent, session):
    response = await agent.generate_str(
        "Get the summoner for the player 'Faker#KR1' on the platform 'ZZ9'. "
        "If the platform is invalid, tell me."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer communicates that the platform value is invalid / not "
                "recognized, in a way a user could act on. It must not fabricate "
                "summoner data."
            ),
            min_score=0.7,
        ),
        response=response,
        name="invalid_platform_reported",
    )


@task("VALIDATION: malformed Riot ID yields a usable error message")
async def test_malformed_riot_id(agent, session):
    response = await agent.generate_str(
        "Get the Riot account for the player 'notariotid-no-hash'. "
        "If the identifier is malformed, tell me how it should be formatted."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer communicates that the identifier is malformed and "
                "indicates the expected GameName#TAG (or PUUID) format. It must "
                "not fabricate an account."
            ),
            min_score=0.7,
        ),
        response=response,
        name="malformed_riot_id_reported",
    )
```

- [ ] **Step 2: Run over stdio**

Run:
```bash
cd eval && uv run mcp-eval run tests/test_canaries.py -v
```
Expected: all PASS. If a Tier-A canary fails, **stop and investigate whether Riot changed a behavior** (status code, empty-vs-404) before touching the test or the adapter.

- [ ] **Step 3: Run the whole suite once over stdio as a full smoke**

Run:
```bash
cd eval && uv run mcp-eval run tests/ -v --json test-reports/stdio.json --html test-reports/stdio.html --markdown test-reports/stdio.md
```
Expected: all tasks PASS; three report files written under `eval/test-reports/`. (Adjust the report flags if Task 1 Step 5 showed different ones.)

- [ ] **Step 4: Commit**

```bash
git add eval/tests/test_canaries.py
git commit -m "test(eval): Riot behavior canaries + input-validation evals"
```

---

### Task 10: Confirm the SSE transport locally

Confirms the one known-unknown the spec flagged: the exact SSE URL Spring AI serves, so `mcpeval.sse.yaml` connects.

**Files:**
- Modify (if needed): `eval/mcpeval.sse.yaml`

- [ ] **Step 1: Boot the server over SSE**

Run (from repo root, with your `RIOT_API_KEY` set):
```bash
./gradlew :lol-mcp-server:bootRun --args='--spring.profiles.active=sse'
```
Expected: server starts on port 8080. In the startup log, note the MCP SSE endpoint Spring AI registers (look for an SSE / `sse` mapping and the `sse-message-endpoint: /mcp/messages` from `application.yml`).

- [ ] **Step 2: Confirm the SSE stream endpoint**

In a second shell:
```bash
curl -sN http://localhost:8080/sse | head -c 200
```
Expected: an event stream opens and emits an `endpoint` event pointing at the message endpoint. If `/sse` 404s, try the path shown in the Step 1 startup log and use that. **Record the working SSE URL.**

- [ ] **Step 3: Set `LOL_MCP_SSE_URL` and run one test over SSE**

Run (from repo root, with `ANTHROPIC_API_KEY` set, server still running):
```bash
export LOL_MCP_SSE_URL="http://localhost:8080/sse"   # or the confirmed path
cd eval && cp mcpeval.sse.yaml mcpeval.yaml && uv run mcp-eval run tests/test_handshake.py -v
```
Expected: PASS. If the transport keyword or URL shape is wrong, fix `eval/mcpeval.sse.yaml` (transport `sse` vs `streamable_http`, and the URL) until the handshake connects. Stop the `bootRun` server afterward.

- [ ] **Step 4: Commit any fixes**

```bash
git add eval/mcpeval.sse.yaml
git commit -m "build(eval): confirm sse transport url for lol-mcp-server"
```

(If no change was needed, skip the commit.)

---

### Task 11: CI workflow — `.github/workflows/live-eval.yml`

**Files:**
- Create: `.github/workflows/live-eval.yml`

**Interfaces:**
- Consumes: repo secrets `RIOT_DEV_REG_TEST_API_KEY` (required to run) and `ANTHROPIC_API_KEY` (optional; absence ⇒ skip).
- Produces: the post-merge live-eval run over both transports with uploaded reports.

- [ ] **Step 1: Write `.github/workflows/live-eval.yml`**

```yaml
name: Live Eval

on:
  push:
    branches: [ "master" ]
  workflow_dispatch:

# Least-privilege; this workflow only reads the repo and writes its own artifacts.
permissions:
  contents: read

jobs:
  live-eval:
    name: Live eval (${{ matrix.transport }})
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        transport: [ stdio, sse ]
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      # Gate on the LLM key. Absent -> skip green with an actionable notice.
      # ANTHROPIC_API_KEY is the ONLY LLM credential this workflow reads; it never
      # references CLAUDE_CODE_OAUTH_TOKEN (separate bucket, see ADR-0012).
      - name: Check for ANTHROPIC_API_KEY
        id: keycheck
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          if [ -z "$ANTHROPIC_API_KEY" ]; then
            echo "::notice title=Live evals skipped::ANTHROPIC_API_KEY is not set. Add it as a repository secret to enable the live mcp-eval suite. (This is separate from CLAUDE_CODE_OAUTH_TOKEN, which is not used here.)"
            echo "run=false" >> "$GITHUB_OUTPUT"
          else
            echo "run=true" >> "$GITHUB_OUTPUT"
          fi

      - name: Set up JDK 21
        if: steps.keycheck.outputs.run == 'true'
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'zulu'

      - name: Setup Gradle
        if: steps.keycheck.outputs.run == 'true'
        uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582 # v4.0.0

      - name: Build server jar
        if: steps.keycheck.outputs.run == 'true'
        run: ./gradlew :lol-mcp-server:bootJar

      - name: Resolve jar path
        if: steps.keycheck.outputs.run == 'true'
        run: |
          JAR="$(ls lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain | head -n1)"
          echo "LOL_MCP_JAR=$GITHUB_WORKSPACE/$JAR" >> "$GITHUB_ENV"

      - name: Set up uv
        if: steps.keycheck.outputs.run == 'true'
        uses: astral-sh/setup-uv@v5
        with:
          python-version: '3.11'

      - name: Install harness
        if: steps.keycheck.outputs.run == 'true'
        working-directory: eval
        run: uv sync

      - name: Start SSE server
        if: steps.keycheck.outputs.run == 'true' && matrix.transport == 'sse'
        env:
          RIOT_API_KEY: ${{ secrets.RIOT_DEV_REG_TEST_API_KEY }}
        run: |
          java -jar "$LOL_MCP_JAR" --spring.profiles.active=sse > sse-server.log 2>&1 &
          echo "SSE_PID=$!" >> "$GITHUB_ENV"
          echo "LOL_MCP_SSE_URL=http://localhost:8080/sse" >> "$GITHUB_ENV"
          # Wait for the port to accept connections.
          for i in $(seq 1 30); do
            if curl -s -o /dev/null http://localhost:8080/actuator/health; then
              echo "server up"; break
            fi
            sleep 2
          done

      - name: Run live evals
        if: steps.keycheck.outputs.run == 'true'
        working-directory: eval
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          RIOT_API_KEY: ${{ secrets.RIOT_DEV_REG_TEST_API_KEY }}
        run: |
          cp "mcpeval.${{ matrix.transport }}.yaml" mcpeval.yaml
          set +e
          uv run mcp-eval run tests/ -v \
            --json "test-reports/${{ matrix.transport }}.json" \
            --html "test-reports/${{ matrix.transport }}.html" \
            --markdown "test-reports/${{ matrix.transport }}.md"
          echo "EVAL_EXIT=$?" >> "$GITHUB_ENV"
          set -e

      - name: Summarize outcome
        if: steps.keycheck.outputs.run == 'true' && always()
        working-directory: eval
        run: |
          {
            echo "## Live eval — ${{ matrix.transport }}"
            if [ "${EVAL_EXIT:-1}" = "0" ]; then
              echo "✅ All live evals passed."
            else
              echo "❌ Live evals reported failures (exit ${EVAL_EXIT})."
              echo ""
              echo "Triage: a **CANARY** failure means Riot's live behavior may have changed — investigate the adapter's assumption before editing the test. A non-canary failure is a regression. Rate-limit / network / Riot 5xx errors are infra flakes — re-run the workflow."
            fi
            echo ""
            echo "See the uploaded \`mcpeval-reports-${{ matrix.transport }}\` artifact (Markdown/HTML/JSON) for per-test detail."
          } >> "$GITHUB_STEP_SUMMARY"

      - name: Upload reports
        if: steps.keycheck.outputs.run == 'true' && always()
        uses: actions/upload-artifact@v4
        with:
          name: mcpeval-reports-${{ matrix.transport }}
          path: eval/test-reports/
          if-no-files-found: warn

      - name: Stop SSE server
        if: always() && matrix.transport == 'sse' && env.SSE_PID != ''
        run: kill "$SSE_PID" || true

      - name: Fail the job on eval failure
        if: steps.keycheck.outputs.run == 'true'
        run: |
          if [ "${EVAL_EXIT:-1}" != "0" ]; then
            echo "Live evals failed — see the job summary and artifact."
            exit 1
          fi
```

- [ ] **Step 2: Lint the workflow YAML**

Run:
```bash
python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/live-eval.yml')); print('yaml ok')"
```
Expected: `yaml ok`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/live-eval.yml
git commit -m "ci: post-merge live mcp-eval suite over stdio + sse"
```

- [ ] **Step 4: Note for verification (done at PR time, Task 15)**

The workflow only runs on `push` to `master` and `workflow_dispatch`, so it will not run on the PR. After merge (or via a manual `workflow_dispatch` from the branch once pushed), confirm a green run over both transports with the reports attached. If `ANTHROPIC_API_KEY` is not yet provisioned, confirm the run **skips green** with the notice.

---

### Task 12: New project skill — `add-live-eval`

**Files:**
- Create: `.claude/skills/add-live-eval/SKILL.md` (confirm the repo's skill directory in Step 1)

- [ ] **Step 1: Confirm where project skills live**

Run:
```bash
ls .claude/skills 2>/dev/null || ls .claude 2>/dev/null || echo "no .claude dir"
```
Expected: find the existing project-skill location (the repo already ships skills such as `add-adapter-test` / `add-mcp-tool`). Create the new skill beside them, matching their directory/frontmatter convention. If they live elsewhere, use that path.

- [ ] **Step 2: Write `add-live-eval/SKILL.md`** (adapt frontmatter to match a sibling skill exactly)

```markdown
---
name: add-live-eval
description: Add a live mcp-eval scenario (and, where relevant, a behavior canary) when adding or changing an MCP tool or context in lol-mcp-server, so every context ships live coverage. Use after add-mcp-tool / add-adapter-test.
---

# Add a live eval

When you add or change an MCP tool/context, add a live scenario to the harness under `eval/tests/`.

## Steps

1. **Pick the discovery seed.** Never hardcode an account. Discover the subject inside the agent
   prompt from a broad/keyless tool:
   - player-keyed tool → seed from `lol_league_apex_by_tier` CHALLENGER (pick any player).
   - live-game tool → seed from `lol_spectator_featured_games` (pick a participant in a game now).
2. **Write a happy-path `@task`.** One prompt that chains discovery → the new tool. Assert:
   - `Expect.tools.was_called("<tool_name>")` (deterministic), and
   - `Expect.judge.llm(rubric=..., min_score=0.7)` on an **invariant**, never an exact live value.
3. **Add a canary if the tool depends on a Riot error/edge behavior** (e.g. a not-found status, a
   404→null mapping). Assert the invariant holds and tag it CANARY in the task name.
4. **Run it locally** over stdio with your keys:
   `cd eval && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval run tests/<file>.py -v`
   (needs `ANTHROPIC_API_KEY`, `RIOT_API_KEY`, and `LOL_MCP_JAR` — see `eval/README.md`).
5. **Keep it under budget.** `max_concurrency` stays 1; avoid adding many high-fanout tests.

## Rules

- Invariants, not exact values. Live data shifts.
- A CANARY failure means "investigate a Riot behavior change" first, not "loosen the rubric".
- Agent-driven only — no direct-call tests here; the WireMock suite owns the deterministic contract.

See [`docs/knowledge/patterns/live-eval-harness.md`](../../../docs/knowledge/patterns/live-eval-harness.md)
and [`ADR-0012`](../../../docs/knowledge/decisions/ADR-0012-live-eval-harness.md).
```

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/add-live-eval/
git commit -m "docs(skill): add-live-eval project skill"
```

---

### Task 13: ADR-0012 and the pattern guide

**Files:**
- Create: `docs/knowledge/decisions/ADR-0012-live-eval-harness.md`
- Create: `docs/knowledge/patterns/live-eval-harness.md`
- Modify: `docs/knowledge/README.md`

- [ ] **Step 1: Write `docs/knowledge/decisions/ADR-0012-live-eval-harness.md`**

```markdown
# ADR-0012: Live agent-driven eval harness (mcp-eval)

- **Status:** Accepted
- **Date:** 2026-07-17

## Context

Two verification steps stayed manual and human-owed after sub-project 1a: the live transport
handshake (stdio + sse, `initialize` → `tools/list` → one `tools/call`, stdout purity) and
endpoint-path verification against the live Riot portal. The offline WireMock suite cannot cover
either — it tests our code against stubs that encode our *assumptions* about Riot, so it passes
forever even if Riot changes its live behavior.

## Decision

Adopt [mcp-eval](https://mcp-eval.ai/) as a live, **agent-driven** regression + acceptance suite,
run post-merge on CI over both transports against the real Riot API.

- **Agent-driven only.** The WireMock suite owns the deterministic request/parse/error contract; a
  direct-call live layer would duplicate it. The live suite's unique value is real-path
  verification, real serving, and agent-usability.
- **Behavior canaries.** Negative tests assert that Riot's error/edge behaviors our adapters depend
  on (unknown-PUUID not-found, account-not-found, spectator `404 → null`) still hold. These are the
  only tests that catch undocumented Riot drift.
- **Post-merge, non-blocking.** Live + LLM variance belongs on an informational signal, not a
  blocking pre-merge gate. `ci.yml` remains the pre-merge gate and stays offline.
- **Token isolation.** The workflow reads only `ANTHROPIC_API_KEY`; it never references
  `CLAUDE_CODE_OAUTH_TOKEN` (a separate bucket for `claude-code-action`). Absent key ⇒ green skip.
- **Dynamic discovery.** Subjects are found at runtime from apex-league / featured-games inside the
  agent conversation — nothing hardcoded to rot.

## Consequences

- Closes the two deferred verification items and downgrades the two matching standing constraints
  from "manual" to "automated post-merge".
- Adds a Python (`uv` + `mcpevals`) dev dependency under `eval/`, separate from the Gradle build.
- Requires a provisioned `ANTHROPIC_API_KEY` and spends tokens per run; kept cheap via a small,
  serial suite on a low-cost model.
- Live tests must assert invariants, not exact values; a canary failure means "investigate Riot",
  not "the code regressed". Reruns absorb rate-limit / network flakes.
```

- [ ] **Step 2: Write `docs/knowledge/patterns/live-eval-harness.md`**

```markdown
# Run and extend the live eval harness

The live suite lives in [`eval/`](../../../eval/README.md): agent-driven mcp-eval tests that drive
the built `lol-mcp-server` jar against the **real Riot API** over stdio and sse. It runs post-merge
on CI (`.github/workflows/live-eval.yml`) and never gates a merge. See
[ADR-0012](../decisions/ADR-0012-live-eval-harness.md) for why.

## Run locally

Prereqigites: `uv`, a personal `RIOT_API_KEY`, and an `ANTHROPIC_API_KEY`.

```bash
./gradlew :lol-mcp-server:bootJar
export ANTHROPIC_API_KEY=sk-ant-...
export RIOT_API_KEY=RGAPI-...
export LOL_MCP_JAR="$(ls lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain)"
cd eval && uv sync && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval run tests/ -v
```

For sse: boot `./gradlew :lol-mcp-server:bootRun --args='--spring.profiles.active=sse'` in another
shell, `export LOL_MCP_SSE_URL=http://localhost:8080/sse`, then
`cp mcpeval.sse.yaml mcpeval.yaml && uv run mcp-eval run tests/ -v`.

## Read the reports

Reports land in `eval/test-reports/` (json/markdown/html) and are uploaded as CI artifacts. Triage a
failing run by outcome class:

| Class | Signal | Do |
|---|---|---|
| missing-key | job skipped green + notice | add the `ANTHROPIC_API_KEY` secret |
| infra-flake | rate-limit / network / Riot 5xx | re-run the workflow |
| regression | non-canary assertion failed | a change broke a tool — fix it |
| canary-drift | a `CANARY:` task failed | investigate a Riot behavior change *before* editing the test |

## Add coverage

Use the `add-live-eval` skill. Invariants not exact values; agent-driven only; discover subjects,
never hardcode them.
```

- [ ] **Step 3: Link both from `docs/knowledge/README.md`** — add to the ADR list and the Patterns list

Add under the ADR list (after ADR-0011):
```markdown
- [ADR-0012 — Live agent-driven eval harness (mcp-eval)](decisions/ADR-0012-live-eval-harness.md)
```
Add under the Patterns list:
```markdown
- [Run and extend the live eval harness](patterns/live-eval-harness.md)
```

- [ ] **Step 4: Commit**

```bash
git add docs/knowledge/decisions/ADR-0012-live-eval-harness.md docs/knowledge/patterns/live-eval-harness.md docs/knowledge/README.md
git commit -m "docs(knowledge): ADR-0012 + live-eval-harness pattern"
```

---

### Task 14: Update roadmap, gotchas, CLAUDE.md, README, CONTRIBUTING, eval/README

**Files:**
- Create: `eval/README.md`
- Modify: `docs/knowledge/roadmap.md`
- Modify: `docs/knowledge/gotchas.md`
- Modify: `CLAUDE.md`
- Modify: `README.md`
- Modify: `CONTRIBUTING.md`

- [ ] **Step 1: Write `eval/README.md`**

```markdown
# Live eval harness

Agent-driven [mcp-eval](https://mcp-eval.ai/) tests that drive the built `lol-mcp-server` jar against
the **real Riot API** over stdio and sse. Post-merge only; never gates a merge. Full guide:
[`docs/knowledge/patterns/live-eval-harness.md`](../docs/knowledge/patterns/live-eval-harness.md);
rationale: [`ADR-0012`](../docs/knowledge/decisions/ADR-0012-live-eval-harness.md).

## Quick start

```bash
./gradlew :lol-mcp-server:bootJar                     # from repo root
export ANTHROPIC_API_KEY=sk-ant-...                   # your Anthropic key
export RIOT_API_KEY=RGAPI-...                         # your personal dev key
export LOL_MCP_JAR="$(ls ../lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain)"
uv sync
cp mcpeval.stdio.yaml mcpeval.yaml
uv run mcp-eval run tests/ -v
```

`ANTHROPIC_API_KEY` is required (agent + judge). It is **separate** from `CLAUDE_CODE_OAUTH_TOKEN`,
which this harness never uses. Reports land in `test-reports/`.
```

- [ ] **Step 2: Update `docs/knowledge/roadmap.md`**

In the **Deferred, with a home** table, replace the two rows "Automated endpoint-path verification"
and "Automated transport-handshake verification" bodies so they point at the now-shipped harness. Change each row's second column to read (endpoint-path):

```markdown
| ~~Automated endpoint-path verification~~ | **Shipped** as part of the live eval harness (see ADR-0012). Live agent-driven evals call every tool against the real Riot API post-merge; a wrong path returns 404 and fails the eval, so paths are verified continuously. |
```

and (transport-handshake):

```markdown
| ~~Automated transport-handshake verification~~ | **Shipped** as part of the live eval harness (see ADR-0012). The suite runs over both stdio and sse each post-merge run; a successful stdio session is the stdout-purity check. |
```

Then in **Standing constraints**, append to the "Green tests do not prove the server serves" bullet
and the "Riot endpoint paths are verified against the live developer portal" bullet a sentence each:
```markdown
  *As of ADR-0012 this is automated post-merge by the live eval harness (`eval/`), over both
  transports; the offline suite remains the pre-merge gate.*
```

- [ ] **Step 3: Append a gotcha to `docs/knowledge/gotchas.md`**

```markdown
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
  undocumented Riot behaviors our adapters depend on (e.g. spectator `404 → null`).
- **Rate budget:** the dev key allows 100 req / 2 min. The suite runs serially (`max_concurrency:
  1`); do not parallelize it or add high-fanout tests casually.
- **stdio stdout purity is load-bearing here too:** if the stdio session fails to connect in CI,
  suspect a stray stdout write before anything else (see the STDIO gotcha above).
```

- [ ] **Step 4: Update `CLAUDE.md`** — add the live-eval commands under "Build and test commands" and a one-line note in the intro

After the existing command block in the "Build and test commands" section, add:
```markdown
The above is the offline CI gate and needs no keys. A separate **live eval harness** (`eval/`,
Python + mcp-eval) runs agent-driven tests against the real Riot API post-merge over both transports
— see [`docs/knowledge/patterns/live-eval-harness.md`](docs/knowledge/patterns/live-eval-harness.md).
It never gates a merge and needs `ANTHROPIC_API_KEY` (separate from `CLAUDE_CODE_OAUTH_TOKEN`):

```bash
cd eval && uv sync && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval run tests/ -v
```
```

- [ ] **Step 5: Update `README.md`** — add a short "Live eval" mention

Find the section that describes testing/CI and add a sentence:
```markdown
Beyond the offline CI gate, a post-merge [live eval harness](eval/README.md) drives the server
against the real Riot API over stdio and sse using [mcp-eval](https://mcp-eval.ai/), verifying the
transports handshake, endpoint paths resolve, and Riot's error behaviors have not drifted.
```
(Place it near the existing testing/CI prose; match the surrounding heading style.)

- [ ] **Step 6: Update `CONTRIBUTING.md`** — add a "Live evals (optional, key-gated)" subsection after the "Build, test, format" block

```markdown
## Live evals (optional, key-gated)

The offline suite above is the gate and needs no keys. A separate live suite in [`eval/`](eval/README.md)
drives the server against the real Riot API with [mcp-eval](https://mcp-eval.ai/). It runs post-merge
on CI and never blocks a merge. To run it locally you need `uv`, a personal `RIOT_API_KEY`, and an
`ANTHROPIC_API_KEY` (separate from any Claude Code OAuth token):

```bash
./gradlew :lol-mcp-server:bootJar
export ANTHROPIC_API_KEY=sk-ant-... RIOT_API_KEY=RGAPI-...
export LOL_MCP_JAR="$(ls lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain)"
cd eval && uv sync && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval run tests/ -v
```

When you add or change a tool, add a live scenario with the `add-live-eval` skill.
```

Also update the earlier line in **Prerequisites** that reads "No other credential (in particular, no
Anthropic key) is needed for anything." to:
```markdown
- A **Riot API key** is only needed to *run* a server or the optional [live evals](#live-evals-optional-key-gated),
  never to build or test the offline suite. The live evals additionally need an `ANTHROPIC_API_KEY`.
```

- [ ] **Step 7: Verify the offline build still passes (docs-only changes must not break anything)**

Run (from repo root):
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL — the `eval/` tree and docs are outside Gradle's source sets, so nothing changes for the JVM build.

- [ ] **Step 8: Commit**

```bash
git add eval/README.md docs/knowledge/roadmap.md docs/knowledge/gotchas.md CLAUDE.md README.md CONTRIBUTING.md
git commit -m "docs: document the live eval harness across the doc set"
```

---

### Task 15: Push, open PR, and verify

**Files:** none (integration + verification)

- [ ] **Step 1: Confirm the full offline gate is green**

Run:
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/live-eval-harness
```

- [ ] **Step 3: Open the PR**

```bash
gh pr create --base master --head feat/live-eval-harness \
  --title "Live eval harness: post-merge mcp-eval regression over stdio + sse" \
  --body "$(cat <<'EOF'
Implements the live eval harness per docs/superpowers/specs/2026-07-17-live-eval-harness-design.md.

- New `eval/` uv + mcp-eval harness: agent-driven scenarios for all seven tools via runtime discovery, plus Tier-A behavior canaries (unknown-PUUID, account-not-found, spectator 404->null) and Tier-B input validation.
- New `.github/workflows/live-eval.yml`: post-merge (+ workflow_dispatch) over a [stdio, sse] matrix; skips green when ANTHROPIC_API_KEY is absent; uploads json/html/md reports; classifies outcomes.
- Token isolation: reads only ANTHROPIC_API_KEY, never CLAUDE_CODE_OAUTH_TOKEN; claude.yml untouched.
- New `add-live-eval` skill, ADR-0012, pattern guide; roadmap/gotchas/CLAUDE/README/CONTRIBUTING updated. The two deferred verification items are now shipped; offline `ci.yml` remains the pre-merge gate.

Verification: offline `./gradlew build` green. Live suite verified locally over stdio and sse (see task log). CI live-eval runs post-merge / via workflow_dispatch.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Confirm the offline CI checks pass on the PR**

Run:
```bash
gh pr checks --watch
```
Expected: the existing `CI` workflow passes. (The `Live Eval` workflow does **not** run on the PR by design — it is push/dispatch only.)

- [ ] **Step 5: After merge (or via `workflow_dispatch` on the pushed branch), verify the live run**

- If `ANTHROPIC_API_KEY` is provisioned: confirm both matrix legs (`stdio`, `sse`) go green and the `mcpeval-reports-*` artifacts are attached, with the job summary showing all evals passed.
- If it is not yet provisioned: confirm both legs **skip green** with the "add ANTHROPIC_API_KEY" notice.

Run:
```bash
gh workflow run "Live Eval" --ref feat/live-eval-harness   # optional manual trigger
gh run watch
```

---

## Self-Review

**Spec coverage:**
- Agent-driven only → Tasks 4-9 (no direct-call layer). ✓
- ANTHROPIC_API_KEY hard requirement + missing-key green skip + diagnostics → Task 11 (keycheck, summary, outcome classes). ✓
- Token isolation (never CLAUDE_CODE_OAUTH_TOKEN) → Global Constraints, Task 11 comment + notice, ADR-0012, gotchas. ✓
- Both transports (stdio stdout purity + sse) → Tasks 3, 10, 11 matrix. ✓
- Dynamic self-seeding discovery → Tasks 4-9 (in-conversation), Deviation note. ✓
- Negative / behavior canaries (Tier A unknown-PUUID, account-not-found, spectator 404→null; Tier B validation) → Task 9. ✓
- Robustness invariants (KDA≥0, win-rate 0-100, counts) → Task 6 rubric. ✓
- CI triggers push+dispatch, not PR → Task 11. ✓
- Rate discipline max_concurrency 1, no 429 probe → Task 2 config, Global Constraints. ✓
- Reports json/html/md + artifacts + job summary → Tasks 2, 9, 11. ✓
- All seven tools exercised → account (8), summoner (5), spectator ×2 (7), analytics (6), league ×2 (4). ✓
- Skill + ADR + pattern + eval/README → Tasks 12, 13, 14. ✓
- Doc updates roadmap/gotchas/CLAUDE/README/CONTRIBUTING → Task 14; README knowledge-index → Task 13. ✓
- Offline suite untouched / still the gate → Task 14 Step 7, Task 15 Step 1/4. ✓
- Claude Code Actions rework recorded as deferred → already committed to roadmap during spec phase (5f3055a); no task needed. ✓

**Placeholder scan:** No "TBD/TODO". The two genuine external unknowns (mcp-eval report/config CLI flags; the exact Spring AI SSE URL) are resolved by concrete `--help`/`curl` verification steps (Task 1 Step 5, Task 10) with a documented default and a "use what it reports" fallback — not left as placeholders.

**Type/name consistency:** Tool names match the McpToolInventoryTest list verbatim. Env var names (`LOL_MCP_JAR`, `LOL_MCP_SSE_URL`, `RIOT_API_KEY`, `ANTHROPIC_API_KEY`), config file names (`mcpeval.stdio.yaml` / `mcpeval.sse.yaml` → copied to `mcpeval.yaml`), the agent name (`riot_tester`), and the server name (`lol_server`) are used identically across Tasks 2, 3, 10, 11, and the docs.
