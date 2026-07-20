# Live eval harness

Agent-driven [mcp-eval](https://mcp-eval.ai/) tests that drive the built `lol-mcp-server` and
`tft-mcp-server` jars against the **real Riot API** over stdio and sse. Both servers are evaluated
from this one harness — `mcpeval.stdio.yaml` / `mcpeval.sse.yaml` each define a `lol_server` and a
`tft_server` MCP server plus a matching `riot_tester` / `tft_tester` agent, and each test file pins
itself to the right agent with `@with_agent(...)`. Post-merge only; never gates a merge. Full guide:
[`docs/knowledge/patterns/live-eval-harness.md`](../docs/knowledge/patterns/live-eval-harness.md);
rationale: [`ADR-0012`](../docs/knowledge/decisions/ADR-0012-live-eval-harness.md).

## Quick start

```bash
./gradlew :lol-mcp-server:bootJar :tft-mcp-server:bootJar   # from repo root
export ANTHROPIC_API_KEY=sk-ant-...                         # your Anthropic key
export RIOT_API_KEY=RGAPI-...                               # your personal dev key
export LOL_MCP_JAR="$(ls ../lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain)"
export TFT_MCP_JAR="$(ls ../tft-mcp-server/build/libs/tft-mcp-server-*.jar | grep -v plain)"
uv sync
cp mcpeval.stdio.yaml mcpeval.yaml
uv run mcp-eval run tests/ -v
```

For sse, also export `LOL_MCP_SSE_URL` / `TFT_MCP_SSE_URL` (see
[`docs/knowledge/patterns/live-eval-harness.md`](../docs/knowledge/patterns/live-eval-harness.md))
and use `mcpeval.sse.yaml` instead.

`ANTHROPIC_API_KEY` is required (agent + judge). It is **separate** from `CLAUDE_CODE_OAUTH_TOKEN`,
which this harness never uses. Reports land in `test-reports/`.
