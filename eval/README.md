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
