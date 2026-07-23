# Live eval harness

Agent-driven [mcp-eval](https://mcp-eval.ai/) tests that drive the built `lol-mcp-server` and
`tft-mcp-server` jars against the **real Riot API** over stdio and sse. Both servers are evaluated
from this one harness — `mcpeval.stdio.yaml` / `mcpeval.sse.yaml` each define a `lol_server` and a
`tft_server` MCP server plus a matching `riot_tester` / `tft_tester` agent, and each test file pins
itself to the right agent with `@with_agent(...)`. Run on demand (`workflow_dispatch`); never gates a merge. Full guide:
[`docs/knowledge/patterns/live-eval-harness.md`](../docs/knowledge/patterns/live-eval-harness.md);
rationale: [`ADR-0012`](../docs/knowledge/decisions/ADR-0012-live-eval-harness.md).

## Quick start

```bash
./gradlew :lol-mcp-server:bootJar :tft-mcp-server:bootJar   # from repo root
export ANTHROPIC_API_KEY=sk-ant-...                         # your Anthropic key
export LOL_DEV_API_KEY=RGAPI-...                            # your LoL dev key
export TFT_DEV_API_KEY=RGAPI-...                            # your TFT dev key
export LOL_MCP_JAR="$(ls ../lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain)"
export TFT_MCP_JAR="$(ls ../tft-mcp-server/build/libs/tft-mcp-server-*.jar | grep -v plain)"
uv sync
# mcp-eval does not expand ${VAR} in the config, so expand our placeholders first (as CI does):
envsubst '${LOL_MCP_JAR} ${TFT_MCP_JAR} ${LOL_DEV_API_KEY} ${TFT_DEV_API_KEY}' \
  < mcpeval.stdio.yaml > mcpeval.yaml
uv run mcp-eval run tests/ -v
```

**Per-server keys:** each game's Riot API is a separate product with its own dev key — a LoL key
returns `403` on `tft/*` endpoints and vice versa. Every MCP server therefore gets its own scoped
key (`LOL_DEV_API_KEY`, `TFT_DEV_API_KEY`, … one per server as we add games); CI stores them as the
matching repository secrets. Each server still reads its key from `RIOT_API_KEY` at run time — the
harness maps the right per-game key into each server's environment.

For sse, also export `LOL_MCP_SSE_URL` / `TFT_MCP_SSE_URL` (add them to the `envsubst` list), start
each server with its own key on its own port, and use `mcpeval.sse.yaml` — see
[`docs/knowledge/patterns/live-eval-harness.md`](../docs/knowledge/patterns/live-eval-harness.md).

`ANTHROPIC_API_KEY` is required (agent + judge). It is **separate** from `CLAUDE_CODE_OAUTH_TOKEN`,
which this harness never uses. Reports land in `test-reports/`.

## Transport scope: `smoke.txt`

CI does not run the same coverage on both legs. `stdio` runs the full suite (`tests/`); `sse` runs
only the four-task set listed in [`smoke.txt`](smoke.txt) — one spec per line
(`file.py::function_name`), `#` comments and blank lines ignored. The `sse` leg proves the transport
wiring (handshake/discovery, one tool round-trip per server, error propagation); tool-logic coverage
comes entirely from `stdio`. See
[ADR-0017](../docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md).

Add a test to `smoke.txt` only if it proves something transport-specific — a new server's handshake,
a new round-trip, error propagation. A tool-logic scenario belongs only in `tests/`; every line in
`smoke.txt` is paid for on every `sse` dispatch.

## Measure the cost

`eval/tools/report-cost.py` summarises a report's real token spend and cost, priced at Claude Haiku
4.5's actual rates rather than the report's own (understated, judge-blind)
`cost_estimate` field:

```bash
uv run python tools/report-cost.py test-reports/stdio.json
uv run python tools/report-cost.py test-reports/stdio.json test-reports/sse.json
```
