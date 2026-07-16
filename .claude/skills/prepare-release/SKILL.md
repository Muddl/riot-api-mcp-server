---
name: prepare-release
description: Cut a release for one or more modules — work out what actually changed, classify the bump, write the CHANGELOG entry, bump the version, and tag. Use after finishing a sub-project or any sizable effort in riot-api-mcp-server.
---

# Prepare a release

Operationalizes [ADR-0010](../../../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

`verifyRelease` already fails the build when a module's version has no matching CHANGELOG heading.
It cannot tell you a module *should* have been bumped and wasn't — that is judgment, and it is what
this skill is for.

## Hydrate first

Read `docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md` and
`docs/knowledge/roadmap.md`.

## 1. Find what actually changed, per module

Never assume the module you were thinking about is the only one you touched.

```bash
for m in riot-api-core riot-account-core lol-mcp-server; do
  LAST=$(git tag --list "$m/v*" | sed "s|^$m/v||" | sort -V | tail -1)
  echo "=== $m (last release: ${LAST:-none}) ==="
  if [ -n "$LAST" ]; then git log --oneline "$m/v$LAST"..HEAD -- "$m/"; else git log --oneline -- "$m/"; fi
done
```

A module with no commits since its last tag **does not get released**. Releasing it anyway produces
a version whose changelog entry has nothing true to say.

Also check repo-wide paths (`buildSrc/`, `.github/`, root docs). Changes there bump no module and
belong in the root CHANGELOG.

## 2. Classify the bump

Pre-1.0 semver, per module:

| Change | Bump |
|---|---|
| Breaking: tool renamed/removed, param changed, public API changed | **minor** (0.1.0 → 0.2.0) |
| New capability, backwards compatible | **minor** |
| Fix, security patch, internal change | **patch** (0.1.0 → 0.1.1) |

Judge each module on its own diff. Modules sharing a version number today is coincidence, not
coupling — do not bump one to match another.

## 3. Decide the cascade — the step most easily missed

**A library bump usually forces a server release.**

Servers embed libraries by project reference, so a fix in `riot-api-core` reaches nobody until each
server re-releases and re-stamps its image with the new library version. Tagging
`riot-api-core/v0.1.1` alone ships **nothing**.

So: if a library changed in a way that affects a server's behaviour, that server gets at least a
**patch** bump, and its changelog entry says which library fix it carries. If it genuinely does not
affect the server, say so out loud rather than skipping silently.

## 4. Write the entry BEFORE bumping

Order matters: bumping first gives you a red build and a temptation to write the entry to satisfy
the gate rather than to describe the change.

In `<module>/CHANGELOG.md`, under a `## [<new-version>] - YYYY-MM-DD` heading, using Keep a Changelog
sections (`Added` / `Changed` / `Fixed` / `Removed`). Mark breaking changes **Breaking:**.

Write what changed for someone consuming the module, and why — not the commit list. If a server
entry exists only because of a library fix, name the library and version.

## 5. Bump

In `<module>/build.gradle`, edit `version = '...'`. One module at a time; do not touch modules that
did not change.

## 6. Verify

```bash
./gradlew build
```

`verifyRelease` runs inside `check`. If it fails, the version and the changelog disagree — fix the
disagreement, do not work around the gate.

## 7. Commit and tag

```bash
git add <module>/build.gradle <module>/CHANGELOG.md
git commit -m "release: <module> <version>"
git tag "<module>/v<version>"
git push origin master "<module>/v<version>"
```

One tag per module. The `release.yml` `resolve` job re-checks the tag against `build.gradle` and
fails if they disagree.

## Red flags

| Thought | Reality |
|---|---|
| "Bump them all to keep the numbers aligned" | Independent versioning is the point (ADR-0010). Aligned numbers are coincidence. |
| "The core fix is tagged, we're done" | A library tag ships nothing. The server must re-release to carry it. |
| "I'll bump now and write the changelog after" | The gate will be red and the entry becomes gate-satisfying noise. Entry first. |
| "verifyRelease is failing, I'll just match the heading" | The gate is reporting real drift. Ask which is wrong — the version or the entry. |
| "This is only a refactor, no entry needed" | If it bumps a version it gets an entry. If it needs no entry, it needs no bump. |
