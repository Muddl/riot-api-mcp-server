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
- **Token isolation.** The workflow reads only `ANTHROPIC_API_KEY` for the LLM; it never references
  `CLAUDE_CODE_OAUTH_TOKEN` (a separate bucket for `claude-code-action`). Absent key ⇒ green skip.
- **One Riot dev key per server (multi-game).** Each Riot game is a separate developer product with
  its own key, and a key is authorized only for its game's API (a LoL key ⇒ `403` on `tft/*`). Every
  MCP server therefore gets its own scoped key — repo secrets `LOL_DEV_API_KEY`, `TFT_DEV_API_KEY`,
  one per server as games are added. The preflight probes each key against its game's status endpoint
  and skips green with a per-secret notice if any is missing/expired/mis-scoped; each server still
  reads `RIOT_API_KEY` at run time, mapped from its per-game secret. (Superseded the single shared
  `RIOT_DEV_REG_TEST_API_KEY` when `tft-mcp-server` was added.)
- **Dynamic discovery.** Subjects are found at runtime from the apex-league ladder inside the agent
  conversation — nothing hardcoded to rot. (Live-game coverage is the "not in a game" invariant:
  there is no featured-games endpoint to force a guaranteed in-game subject — see ADR-0013.)

## Consequences

- Closes the two deferred verification items and downgrades the two matching standing constraints
  from "manual" to "automated post-merge".
- Adds a Python (`uv` + `mcpevals`) dev dependency under `eval/`, separate from the Gradle build.
- Requires a provisioned `ANTHROPIC_API_KEY` and spends tokens per run; kept cheap via a small,
  serial suite on a low-cost model.
- Live tests must assert invariants, not exact values; a canary failure means "investigate Riot",
  not "the code regressed". Reruns absorb rate-limit / network flakes.
