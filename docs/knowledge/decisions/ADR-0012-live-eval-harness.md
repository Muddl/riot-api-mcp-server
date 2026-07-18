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
