# ADR-0017: Transport-scoped live eval coverage

- **Status:** Accepted
- **Date:** 2026-07-23

## Context

`live-eval.yml` runs a matrix of `stdio` and `sse`, and both legs ran the identical 24-task suite.
On run `30011353019` the two legs produced identical outcomes and agreed on token spend within
0.03%. That makes sense: the same server jars, the same Riot responses, and the same tool logic sit
underneath both transports — only the wire protocol between the eval agent and the server differs.
Running the full suite on both legs pays for one signal twice.

## Decision

- The `stdio` leg keeps running the **full suite** (`eval/tests/`) — this is the leg that proves
  tool logic and catches regressions.
- The `sse` leg runs a small, explicit **transport smoke set** listed in `eval/smoke.txt`: a
  handshake/discovery check, one round-trip tool call per server (LoL and TFT each have their own
  port), and one error-propagation case. Four tasks total.

## Mechanism

`mcp-eval run` takes positional path specs, including `file.py::function_name`, but has **no `-k`,
marker, or tag selection** (verified against `mcp_eval/runner.py` in mcp-eval 0.1.10). Scoping the
`sse` leg therefore means passing it an explicit list of specs rather than filtering the full suite
by attribute. `eval/smoke.txt` holds that list — one spec per line, `#` comments and blank lines
ignored — read by the workflow and passed as positional arguments to `mcp-eval run`. It lives next
to the tests, not inline in the CI YAML, so it stays visible to anyone adding a test.

The workflow fails loudly if `smoke.txt` resolves to zero specs, rather than falling through to
`mcp-eval`'s default `test_dir` of `"tests"` — see the "stale smoke set" gotcha and the matching
triage row for why an empty smoke set is a hazard, not just a no-op.

## What the sse leg does not prove

Any tool-logic regression. That coverage comes entirely from the `stdio` leg. The job summary
names the running leg's scope (`full suite` vs `transport smoke set (N tasks)`) so a green `sse`
run is never misread as full coverage.

## Relationship to ADR-0012

This narrows the coverage matrix established by [ADR-0012](ADR-0012-live-eval-harness.md); the
harness's purpose (agent-driven, live-Riot, post-merge, non-gating), credential model
(`ANTHROPIC_API_KEY` only), and per-server dev-key handling are all unchanged.

## Consequences

- Every dispatch pays for four `sse` tasks instead of 24, cutting that leg's token spend
  proportionally (see [ADR-0016](ADR-0016-bounded-list-results.md) for the companion per-tool
  payload reduction).
- A new transport-specific behavior (e.g. a new server, a new error-propagation path) needs its own
  smoke entry; a new tool-logic scenario does not — see the `add-live-eval` skill.
- `smoke.txt` is now a second place (besides the test files themselves) that can go stale: a
  renamed test or function silently drops out of the smoke leg. See the matching gotcha and triage
  row in the harness pattern guide.
