# ADR-0011 — Documentation topology and the per-module doc gate

**Status:** Accepted (sub-project 1a, Phase 7)
**Supersedes / relates to:** [ADR-0006](ADR-0006-monorepo-split.md) (the monorepo split that created
the multi-module surface this governs), [ADR-0010](ADR-0010-versioning-and-coordinates.md) (per-module
versioning and CHANGELOGs — this ADR extends the same "each module owns its own" principle to README
and ARCHITECTURE).

## Context

The monorepo split (ADR-0006) left all prose documentation at the repository root: `README.md`,
`ARCHITECTURE.md`, `CONTRIBUTING.md`, `CHANGELOG.md` all described the whole repo. A consumer who only
wanted one module had nowhere local to look, and the root docs mixed cross-cutting facts (the hexagon,
the dependency rule, the shared HTTP client) with module-specific ones (the LoL tool table, the LoL
context list, the LoL slice exceptions). With four more servers coming, that mix guarantees drift —
five copies of the hexagon explanation, or five servers' tool tables competing at one altitude.

## Decision

**A fact lives at exactly one altitude.** Cross-cutting facts live at the repository root and are
linked from modules; module-specific facts live in the module and are never restated at root.

Concretely:

- The root keeps orientation: what the monorepo is, the module table, the dependency rule, how to
  build, and links out. The root `ARCHITECTURE.md` keeps the *shared* hexagon rationale, the
  dependency rule, the HTTP client, routing, the enforcement *mechanism*, testing strategy, and
  transports.
- **Every module — both libraries and every server — carries its own `README.md`, `ARCHITECTURE.md`,
  and `CHANGELOG.md`.** A module `ARCHITECTURE.md` links the shared hexagon at root and never
  restates it; it covers only what is specific to that module (its public API or tool surface, its
  internal contexts, its own slice exceptions).
- `docs/knowledge/` stays **shared and single-source** — it is the hydrate protocol's index, and
  fragmenting it per module would mean an agent working in one server misses a gotcha written in
  another.

The bar: **a consumer who only cares about one module can read that module's docs and stop.**

## Enforcement

`verifyModuleDocs`, a per-module Gradle task in the `buildSrc` convention plugin wired into `check`,
fails the build if a module lacks any of the three docs. This is the same "enforced, not documented"
move as `verifyRelease` (ADR-0010) and the `@McpTool` ArchUnit rule (ADR-0004): the presence of the
doc set stops being something to remember and becomes a red build. A new game server that forgets its
docs cannot go green.

The anti-duplication half ("no fact restated at the wrong altitude") is not machine-checkable and
remains a review responsibility, audited in each cycle's sanity pass.

## Consequences

- A new server module (sub-projects 2–4) must add `README.md` + `ARCHITECTURE.md` + `CHANGELOG.md` or
  `verifyModuleDocs` fails — folded into the add-a-bounded-context / new-server checklist.
- Root docs shrank; the LoL tool table, run/Docker instructions, and LoL context/slice detail moved
  into `lol-mcp-server`'s docs.
- The `docs/knowledge/` base is deliberately exempt — shared by design.
