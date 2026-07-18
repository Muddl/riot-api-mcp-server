# Knowledge Base

The durable, committed memory for `riot-api-mcp-server`. It exists so that any
contributor — human or AI agent — can rehydrate the project's context fast and
persist new findings in a consistent place, instead of re-deriving decisions
from the code every time.

This README is the **single source of truth** for the hydrate/persist protocol.
`CLAUDE.md` and every committed agent point here rather than restating it.

## Contents

| Area | What lives here |
|------|-----------------|
| [`roadmap.md`](roadmap.md) | The living program plan — where the sub-projects stand, what is deferred |
| [`decisions/`](decisions/) | Architecture Decision Records (ADRs) — one decision per file |
| [`patterns/`](patterns/) | Copy-pasteable how-to guides for recurring procedures |
| [`gotchas.md`](gotchas.md) | Sharp edges and non-obvious pitfalls, newest appended at the bottom |
| [`glossary.md`](glossary.md) | Riot / League of Legends domain terms |

### Decisions (ADRs)

- [ADR-0001 — Bounded-context hexagonal architecture](decisions/ADR-0001-hexagonal.md)
- [ADR-0002 — Shared Riot HTTP client](decisions/ADR-0002-shared-riot-http-client.md)
- [ADR-0003 — WireMock + port-fake testing](decisions/ADR-0003-wiremock-testing.md)
- [ADR-0004 — ArchUnit architecture enforcement](decisions/ADR-0004-archunit-enforcement.md)
- [ADR-0005 — Committed knowledge system](decisions/ADR-0005-knowledge-system.md)
- [ADR-0006 — Monorepo of per-game MCP servers over a shared core](decisions/ADR-0006-monorepo-split.md)
- [ADR-0007 — Core hardening boundary](decisions/ADR-0007-core-hardening-boundary.md)
- [ADR-0008 — Shared player-identity resolution](decisions/ADR-0008-shared-player-identity-resolution.md)
- [ADR-0009 — MCP tool contract](decisions/ADR-0009-mcp-tool-contract.md)
- [ADR-0010 — Artifact coordinates, per-module versioning, and provenance stamping](decisions/ADR-0010-versioning-and-coordinates.md)
- [ADR-0011 — Documentation topology and the per-module doc gate](decisions/ADR-0011-doc-topology.md)
- [ADR-0012 — Live agent-driven eval harness (mcp-eval)](decisions/ADR-0012-live-eval-harness.md)
- [ADR-0013 — Remove the featured-games tool (endpoint retired by Riot)](decisions/ADR-0013-remove-featured-games.md)

### Patterns

- [Add a bounded context](patterns/add-a-bounded-context.md)
- [Add an MCP tool](patterns/add-an-mcp-tool.md)
- [Add an adapter test](patterns/add-an-adapter-test.md)
- [Run and extend the live eval harness](patterns/live-eval-harness.md)

## Hydrate / Persist protocol

### Hydrate (at the start of a task)

Before changing anything:

1. Read this `README.md`.
2. Read [`roadmap.md`](roadmap.md) — where the program stands, and what is deliberately deferred.
3. Read [`gotchas.md`](gotchas.md) — it is short and prevents the most common mistakes.
4. Read the ADRs relevant to the area you are touching (e.g. adding an outbound
   adapter → [ADR-0001](decisions/ADR-0001-hexagonal.md) and
   [ADR-0002](decisions/ADR-0002-shared-riot-http-client.md)).
5. If your task matches a pattern, follow the matching guide in [`patterns/`](patterns/).
   Unsure of a domain term? Check [`glossary.md`](glossary.md).

### Persist (at the end of a task)

Write findings back so the next person does not re-derive them. Keep every entry
**small and single-purpose**:

- **Made a new architectural decision?** Add a new ADR in `decisions/` using the
  next number (`ADR-000N-short-slug.md`) and the template below, then link it from
  this README's ADR list.
- **Established a new recurring procedure?** Add a new guide in `patterns/`, then
  link it from this README's Patterns list.
- **Hit a new pitfall?** Append a section to the bottom of [`gotchas.md`](gotchas.md).
- **Introduced a new domain term?** Add it to [`glossary.md`](glossary.md).
- **Finished a sub-project, or moved its scope?** Update [`roadmap.md`](roadmap.md). Scope lives
  there, not in the dated specs — those are snapshots and are not edited retroactively.

Do not edit an existing ADR to reverse a decision — supersede it with a new ADR
and set the old one's **Status** to `Superseded by ADR-000N`.

### ADR template

```
# ADR-000N: <title>

- **Status:** Accepted | Superseded by ADR-000M
- **Date:** YYYY-MM-DD

## Context
Why a decision was needed.

## Decision
What we chose.

## Consequences
What this makes easy, what it costs, and what to watch for.
```
