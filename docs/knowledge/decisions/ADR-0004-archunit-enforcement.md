# ADR-0004: ArchUnit architecture enforcement

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

The hexagonal rules from [ADR-0001](ADR-0001-hexagonal.md) are only valuable if they
cannot silently rot. Code review alone does not reliably catch a service importing an
adapter or a stray `RestClient` outside the outbound package.

## Decision

Encode the rules as an ArchUnit test suite
(`com.tngtech.archunit:archunit-junit5`, `testImplementation`) under an `architecture/`
test package, running as part of `./gradlew test` / `build` — no separate CI workflow.
Rules:

- Layered dependency rule: `domain ⇸ application ⇸ adapter`, inward only.
- `RestClient` is referenced only within `..adapter.out.riot..`.
- `@McpTool` is present only within `..adapter.in.mcp..`.
- Ports are interfaces residing in `..application.port..`.
- No context package depends on another context's internals, **except** `analytics`.
- Naming: `*Service` in `application`, `*Tool` in `adapter.in.mcp`, `*Adapter` in
  `adapter.out.riot`, `*Port` interfaces in `application.port`.

Alongside ArchUnit, JaCoCo measures coverage (report on `test`, a conservative/soft
threshold — the signal is "coverage is visible," not an arbitrary gate) and Spotless
(`spotlessCheck` wired into `check`/`build`) fails the build on formatting drift.

## Consequences

- A dependency-rule or naming violation fails the build, so the architecture stays true
  over time — a strong portfolio signal.
- No CodeQL/SAST workflow is needed for structure; Dependabot covers CVEs.
- New contributors get immediate, precise feedback. The `check-architecture` skill runs
  this suite and interprets the failures.
- When adding a context or tool, expect ArchUnit to enforce the package/naming rules in
  [patterns/add-a-bounded-context.md](../patterns/add-a-bounded-context.md) and
  [patterns/add-an-mcp-tool.md](../patterns/add-an-mcp-tool.md).
