---
name: add-live-eval
description: Add a live mcp-eval scenario (and, where relevant, a behavior canary) when adding or changing an MCP tool or context in lol-mcp-server, so every context ships live coverage. Use after add-mcp-tool / add-adapter-test.
---

# Add a live eval

When you add or change an MCP tool/context, add a live scenario to the harness under `eval/tests/`.

## Hydrate first

Read `docs/knowledge/patterns/live-eval-harness.md` and
`docs/knowledge/decisions/ADR-0012-live-eval-harness.md`.

## Steps

1. **Pick the discovery seed.** Never hardcode an account. Discover the subject inside the agent
   prompt from a broad/keyless tool:
   - player-keyed tool → seed from `lol_league_apex_by_tier` CHALLENGER (pick any player).
   - live-game (spectator) tool → seed a player from apex and assert the "in a game OR cleanly not
     in a game" invariant; there is no featured-games endpoint to force a guaranteed live subject.
2. **Write a happy-path `@task`.** One prompt that chains discovery → the new tool. Assert:
   - `Expect.tools.was_called("<tool_name>")` (deterministic), and
   - `Expect.judge.llm(rubric=..., min_score=0.7)` on an **invariant**, never an exact live value.
3. **Add a canary if the tool depends on a Riot error/edge behavior** (e.g. a not-found status, a
   404→null mapping). Assert the invariant holds and tag it CANARY in the task name.
4. **Run it locally** over stdio with your keys:
   `cd eval && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval run tests/<file>.py -v`
   (needs `ANTHROPIC_API_KEY`, `RIOT_API_KEY`, and `LOL_MCP_JAR` — see `eval/README.md`).
5. **Keep it under budget.** `max_concurrency` stays 1; avoid adding many high-fanout tests.
6. **Decide whether it belongs in `eval/smoke.txt`.** The `sse` leg runs only that small transport
   smoke set, not the full suite (ADR-0017). Add your new scenario to `smoke.txt` **only** if it
   proves something transport-specific — a handshake/discovery check, a round-trip on a newly added
   server, or error propagation over the wire. A tool-logic scenario does not belong there: the
   `stdio` leg already covers tool logic, and every entry in `smoke.txt` is paid for on every
   dispatch of the `sse` leg.

## Rules

- Invariants, not exact values. Live data shifts.
- A CANARY failure means "investigate a Riot behavior change" first, not "loosen the rubric".
- Agent-driven only — no direct-call tests here; the WireMock suite owns the deterministic contract.

See [`docs/knowledge/patterns/live-eval-harness.md`](../../../docs/knowledge/patterns/live-eval-harness.md),
[`ADR-0012`](../../../docs/knowledge/decisions/ADR-0012-live-eval-harness.md), and
[`ADR-0017`](../../../docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md).
