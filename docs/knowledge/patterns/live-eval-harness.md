# Run and extend the live eval harness

The live suite lives in [`eval/`](../../../eval/README.md): agent-driven mcp-eval tests that drive
the built `lol-mcp-server` jar against the **real Riot API** over stdio and sse. It runs post-merge
on CI (`.github/workflows/live-eval.yml`) and never gates a merge. See
[ADR-0012](../decisions/ADR-0012-live-eval-harness.md) for why.

## Run locally

Prerequisites: `uv`, a personal `RIOT_API_KEY`, and an `ANTHROPIC_API_KEY`.

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
