# ADR-0010: Artifact coordinates, per-module versioning, and provenance stamping

- **Status:** Accepted
- **Date:** 2026-07-15

## Context

After the monorepo split ([ADR-0006](ADR-0006-monorepo-split.md)), three modules with genuinely
different reasons to change shared one version number — `version = '0.0.2-SNAPSHOT'`, hardcoded in
the `buildSrc` convention plugin. Independent versioning was not merely undone; it was impossible by
construction. A security fix in `riot-api-core` and a new endpoint in `lol-mcp-server` had no way to
be separate releases.

Two related defects sat alongside it:

- **Version and release were never reconciled.** `release.yml` derived image tags from the git tag
  via `docker/metadata-action`'s `type=semver` and never consulted the Gradle version. They were two
  unrelated numbers, which is why bumping and changelogging drifted apart in practice — there was no
  link to keep.
- **Coordinates named the wrong owner.** `group = 'com.wkaiser'` and package root
  `com.wkaiser.riot.*`, while the GitHub owner and GHCR image path are `muddl`.

## Decision

**Coordinates are `com.muddl`, group and package root together.** Java convention is that the
package root matches the group; changing one without the other trades one inconsistency for another.
Nothing was externally breaking: the libraries are unpublished, the image name already derived from
`github.repository_owner`, and the public contract is the MCP tool names.

**Each module declares its own `version` in its own `build.gradle`.** The convention plugin keeps
`group` (shared) and drops `version` (module-specific). Not a per-module `gradle.properties` —
Gradle silently ignores those (see `gotchas.md`).

**Libraries are versioned and tagged but not published.** Servers consume them via
`project(':riot-api-core')`, a source reference, which preserves ADR-0006's atomic cross-module
commit. A library's version therefore appears in no dependency declaration and bumping it changes no
resolution behaviour.

What versioning buys instead is **provenance**: each server's jar manifest and image labels record
the library versions it embeds, so *"core 0.1.0 has a flaw — which images embed it?"* is answerable
from the registry without rebuilding.

**Cross-module version reads are lazy, resolved at task-execution time, not at configuration time.**
The convention plugin is applied to a module before that module's own `version = '...'` line runs, so
anything that captures `project.version` eagerly (a plain `def` at script-configuration time) freezes
on Gradle's default `'unspecified'`. `verifyRelease`, `lol-mcp-server`'s manifest stamping (which
reads `riot-api-core` and `riot-account-core`'s versions across project boundaries), and the root's
`printModuleVersions` all read versions through `providers.provider { ... }`, resolved inside
`doLast`. The alternative tried first, `evaluationDependsOn`, was rejected: it forces eager
cross-project evaluation, makes correctness depend on `settings.gradle`'s include order — which
Gradle does not contract — and is incompatible with `--configuration-on-demand`.

**Tags are `<module>/v<semver>`.** `release.yml` parses module and version from the tag, fails if
they disagree with the module's `build.gradle`, and builds only that module. Server tags publish an
image; library tags publish release notes only.

**`verifyRelease` wired into `check`** fails the build when a module's version has no matching
`## [<version>]` heading in that module's CHANGELOG. Versions are concrete on master, so it runs on
every build, not just at release. The heading is matched without a date, so entries accumulate under
the target version during a cycle and the date is filled at tag time.

**A change is logged in the CHANGELOG of every module whose version it bumps.** The root CHANGELOG
covers only changes that bump no module — CI, build tooling, root docs.

## Consequences

**Makes easy:** a security fix in a library is its own release with its own notes. "Which images are
affected?" is a registry query. Bumping without a changelog entry is a red build rather than a thing
to remember, which is the same "enforced, not documented" move as the `@McpTool` ArchUnit rule
([ADR-0004](ADR-0004-archunit-enforcement.md)).

**Costs:** more moving parts at release — one tag per module, one changelog per module. The
`prepare-release` skill exists to carry the judgment the gate cannot.

**Watch for:**

- **A library tag ships nothing on its own.** Servers embed libraries by project reference, so a fix
  in `riot-api-core` reaches users only when each server re-releases and re-stamps its image. A
  library bump usually cascades into a server patch release. This is the most likely way to think
  something is fixed when it is not.
- **Versions agreeing is not versions being coupled.** All three modules read `0.1.0` today by
  coincidence, not by construction. Do not reintroduce a shared version because they happen to match.
- **`printModuleVersions` resolves in `doLast`** — `subprojects` at the root's configuration time
  runs before subprojects are evaluated and reads `unspecified`. The same reasoning is why
  `verifyRelease` and the manifest stamping in `lol-mcp-server/build.gradle` read versions through a
  `providers.provider { ... }` instead of a plain `def`.
