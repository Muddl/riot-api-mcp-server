---
name: check-architecture
description: Run the build-time architecture and quality gates (ArchUnit rules, JaCoCo coverage, Spotless formatting) and interpret failures against the project's hexagonal rules. Use before committing structural changes or when a build fails on ArchUnit/coverage/formatting in riot-api-mcp-server.
---

# Check architecture

Operationalizes the quality gates from
`docs/knowledge/decisions/ADR-0004-archunit-enforcement.md`.

## Hydrate first

Read `docs/knowledge/decisions/ADR-0004-archunit-enforcement.md` and
`ADR-0001-hexagonal.md`.

## Run the gates

From Git Bash:

```bash
./gradlew test        # runs the ArchUnit suite (architecture/) + unit + WireMock tests
./gradlew check       # adds spotlessCheck; build also runs these
```

The JaCoCo report is written under `build/reports/jacoco/`.

## Interpret failures

- **Layered dependency violation** (`application` importing `adapter`, or `domain`
  importing anything outward): move the offending dependency inward — depend on the port,
  not the adapter.
- **`RestClient` outside `..adapter.out.riot..`**: only outbound adapters may touch
  `RestClient`; route the call through `RiotApiClient` inside an adapter.
- **`@McpTool` outside `..adapter.in.mcp..`**: move the tool into `adapter/in/mcp`.
- **Port not an interface / not in `..application.port..`**: make it an interface and
  relocate it.
- **Cross-context internals**: only `analytics` may depend on other contexts'
  application services; otherwise decouple.
- **Naming rule**: ensure `*Service` / `*Tool` / `*Adapter` / `*Port` land in their
  required packages.
- **Spotless**: run `./gradlew spotlessApply` to auto-fix formatting, then re-run.
- **Coverage**: the threshold is intentionally soft — a failure usually means a whole
  new class has no test; add one rather than lowering the bar.

## Persist

If a failure taught you a non-obvious rule interaction, append it to
`docs/knowledge/gotchas.md`.
