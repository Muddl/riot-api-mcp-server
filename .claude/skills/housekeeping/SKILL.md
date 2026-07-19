---
name: housekeeping
description: Audit and trim the repo's docs, knowledge base, roadmap, skills, and agents so they stay accurate and fit-for-purpose. Use for a periodic maintenance pass, or when doc drift is suspected. Accepts `--audit-only` to report without editing.
---

# Repository housekeeping

Keep `riot-api-mcp-server`'s durable context — docs, knowledge base, roadmap, skills, agents —
accurate, trim, and consistent with the real project. This is the reusable definition behind both
the manual `/housekeeping` pass and the weekly `housekeeping.yml` Action.

## Modes

- **`--audit-only`** — report findings as a checklist; make no edits.
- **default** — apply the fixes, keeping each change small and single-purpose.

## Hydrate first

Read `docs/knowledge/README.md` (the hydrate/persist protocol), then `roadmap.md` and `gotchas.md`,
so fixes stay consistent with recorded decisions. For any non-trivial doc rewrite, delegate the
prose to the `docs-maintainer` agent rather than restating its rules here.

## Audit checklist

Work through each item. For every finding, note the file, the drift, and the fix.

1. **Doc ↔ code drift.** Re-derive facts from source and reconcile prose in `README.md`,
   `ARCHITECTURE.md`, `CLAUDE.md`, `CONTRIBUTING.md`:
   - Tool count = unique `name = "…"` values:
     `grep -rhoE 'name = "[a-z_]+"' --include=*.java lol-mcp-server/src/main | sort -u | wc -l`
   - Tool classes = files declaring `@McpTool` under `adapter/in/mcp`:
     `grep -rl "@McpTool" --include=*.java lol-mcp-server/src/main | grep 'adapter/in/mcp' | wc -l`
   - Context/package names and transport details (`stdio`/`sse`) match the source tree.
2. **KB integrity.**
   - Every ADR on disk is linked from `docs/knowledge/README.md`:
     `comm -23 <(ls docs/knowledge/decisions/ADR-*.md | xargs -n1 basename | sort) <(grep -oE 'decisions/ADR-[0-9]{4}[^)]*\.md' docs/knowledge/README.md | sed 's|decisions/||' | sort -u)`
   - ADR numbering is contiguous; superseded ADRs carry `Superseded by ADR-00NN`.
   - Every relative Markdown link resolves (no dead targets) across `docs/knowledge/` and root docs.
3. **Roadmap freshness.** `roadmap.md` status table matches reality; dates are absolute (not "last
   week"); deferred items are still true.
4. **Skills & agents fit-for-purpose.** Each `.claude/skills/*/SKILL.md` and `.claude/agents/*.md`
   references real files, commands, and tool names; descriptions are accurate; nothing is stale or
   duplicated.
5. **CHANGELOG ↔ versions.** Module versions and `CHANGELOG.md` entries are consistent.

## Hands-off (never touch)

`docs/superpowers/specs/` and `docs/superpowers/plans/` are dated snapshots — immutable history.
Do not edit or trim them. Scope moves in `roadmap.md`, not in the dated specs.

## Finishing

- **`--audit-only`:** print the findings checklist and stop.
- **default:** apply fixes, then persist per `docs/knowledge/README.md` — a new pitfall goes to
  `gotchas.md`, a new decision becomes an ADR (and is linked from the KB README). Do not commit or
  open a PR yourself; the caller (human or workflow) owns git.
