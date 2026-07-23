# Run and extend the live eval harness

The live suite lives in [`eval/`](../../../eval/README.md): agent-driven mcp-eval tests that drive
the built `lol-mcp-server` jar against the **real Riot API** over stdio and sse. It runs post-merge
on CI (`.github/workflows/live-eval.yml`) and never gates a merge. See
[ADR-0012](../decisions/ADR-0012-live-eval-harness.md) for why.

## Run locally

Prerequisites: `uv`, an `ANTHROPIC_API_KEY`, and **one Riot dev key per server** — each game's Riot
API is a separate product, so a LoL key returns `403` on `tft/*` and vice versa. The harness drives
both the `lol-mcp-server` and `tft-mcp-server` jars.

```bash
./gradlew :lol-mcp-server:bootJar :tft-mcp-server:bootJar
export ANTHROPIC_API_KEY=sk-ant-...
export LOL_DEV_API_KEY=RGAPI-...   # LoL-scoped dev key
export TFT_DEV_API_KEY=RGAPI-...   # TFT-scoped dev key
export LOL_MCP_JAR="$(ls lol-mcp-server/build/libs/lol-mcp-server-*.jar | grep -v plain)"
export TFT_MCP_JAR="$(ls tft-mcp-server/build/libs/tft-mcp-server-*.jar | grep -v plain)"
cd eval && uv sync
# mcp-eval does not expand ${VAR} in the config — expand our placeholders first (as CI does):
envsubst '${LOL_MCP_JAR} ${TFT_MCP_JAR} ${LOL_DEV_API_KEY} ${TFT_DEV_API_KEY}' \
  < mcpeval.stdio.yaml > mcpeval.yaml
uv run mcp-eval run tests/ -v
```

For sse: boot each server with its own key on its own port
(`RIOT_API_KEY=$LOL_DEV_API_KEY ./gradlew :lol-mcp-server:bootRun --args='--spring.profiles.active=sse'`
on 8080, and the tft server with `RIOT_API_KEY=$TFT_DEV_API_KEY` and `SERVER_PORT=8081`), export
`LOL_MCP_SSE_URL` / `TFT_MCP_SSE_URL`, add them to the `envsubst` list, and use `mcpeval.sse.yaml`.

Each server reads its key from `RIOT_API_KEY` at run time; the harness supplies the right per-game
key to each server. CI stores them as the `LOL_DEV_API_KEY` / `TFT_DEV_API_KEY` repo secrets.

## Read the reports

Reports land in `eval/test-reports/` (json/markdown/html) and are uploaded as CI artifacts. Triage a
failing run by outcome class:

| Class | Signal | Do |
|---|---|---|
| missing/expired/mis-scoped key | job skipped green + notice naming the secret | add `ANTHROPIC_API_KEY`, or regenerate the named `*_DEV_API_KEY` (403 = the product isn't approved for that game's API; dev keys expire every 24h) |
| infra-flake | rate-limit / network / Riot 5xx | re-run the workflow |
| regression | non-canary assertion failed | a change broke a tool — fix it |
| canary-drift | a `CANARY:` task failed | investigate a Riot behavior change *before* editing the test |

## Add coverage

Use the `add-live-eval` skill. Invariants not exact values; agent-driven only; discover subjects,
never hardcode them.
