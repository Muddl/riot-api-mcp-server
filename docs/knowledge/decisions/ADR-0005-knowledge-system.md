# ADR-0005: Committed knowledge system

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

The prior `CLAUDE.md` was ~322 lines of marketing, fictional infrastructure, and dated
"recent updates" logs, loaded every session — high token cost, low signal. There was no
durable, committed place for decisions or procedures, so context was re-derived from
code each time and AI agents had nothing reliable to rehydrate from.

## Decision

Commit a knowledge system to the repo (portfolio-visible, GitHub-rendered):

- `docs/knowledge/` — `README.md` (index + hydrate/persist protocol, the single source
  of truth), `decisions/` (ADRs), `patterns/` (how-to guides), `gotchas.md`,
  `glossary.md`.
- `.claude/skills/` (committed) — `scaffold-bounded-context`, `add-mcp-tool`,
  `add-adapter-test`, `check-architecture`; each operationalizes a `patterns/` guide.
- `.claude/agents/` (committed) — a small purpose-built set:
  `riot-context-architect`, `test-author`, `docs-maintainer`; each embeds the
  hydrate/persist protocol.

The **hydrate/persist protocol** (defined once in `docs/knowledge/README.md`,
referenced by `CLAUDE.md` and every agent): hydrate by reading the README + relevant
decisions/patterns/gotchas before acting; persist by writing findings back (new
decision → new ADR; new procedure → new pattern; new pitfall → append to `gotchas.md`),
keeping entries small and single-purpose.

Git hygiene: an explicit `.superpowers/` line in the root `.gitignore`; `.claude/`
committed **except** `.claude/settings.local.json`.

## Consequences

- `CLAUDE.md` shrinks to accurate essentials and defers depth to the KB — lower
  per-session token cost, higher signal.
- Decisions and procedures are versioned with the code and visible to reviewers.
- The system only works if people persist — the protocol makes that the explicit last
  step of every task.
- Superseding, not editing, is the way to reverse a decision (keeps history honest).
