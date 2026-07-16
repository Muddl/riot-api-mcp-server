# LoL Parity 1a — Plan A: Coordinates & Release Engineering

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the repo to `com.muddl` coordinates and give each module an independent version whose bump cannot drift from its CHANGELOG.

**Architecture:** Phases 0–1 of the [1a spec](../specs/2026-07-15-lol-parity-foundation-design.md). Pure motion first (ArchUnit hardening, then the rename), then release engineering: `version` leaves the convention plugin for each module's `build.gradle`, a `verifyRelease` task wired into `check` makes bump-without-changelog a red build, and server artifacts stamp the library versions they embed so "which images contain vulnerable core X?" is answerable from the registry.

**Tech Stack:** Gradle 9.6.1 (Groovy DSL, `buildSrc` convention plugin), Spring Boot 4.1.0, Spring AI 2.0.0, Java 21, JUnit 5, AssertJ, ArchUnit 1.3.0, Spotless (palantir-java-format), JaCoCo, GitHub Actions, Docker/GHCR.

## Global Constraints

- **The suite runs offline with no Riot API key.** Never add a test needing a live key or network.
- **Green at every commit.** Tasks 1–2 are pure motion: anything red means the move is wrong.
- **Java 21 toolchain**, group `com.muddl` (after Task 2), package root `com.muddl.riot.{core,account,lol}`.
- **Run Spotless before committing Java changes:** `./gradlew spotlessApply`. CI runs `spotlessCheck` inside `build`.
- **Module list:** `riot-api-core`, `riot-account-core`, `lol-mcp-server`. Dependencies point one way: server → `riot-account-core` → `riot-api-core`.
- **Target version for every module this cycle:** `0.1.0` (from `0.0.2-SNAPSHOT`).
- **Tag format:** `<module>/v<semver>`, e.g. `lol-mcp-server/v0.1.0`.
- **Do not change any `@McpTool` name or `@McpToolParam` description in this plan.** The contract sweep is Plan C. `McpToolInventoryTest` must stay green throughout Plan A.
- **Hydrate before starting:** `docs/knowledge/README.md`, `docs/knowledge/roadmap.md`, `docs/knowledge/gotchas.md`, ADR-0004 and ADR-0006.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `lol-mcp-server/src/test/java/.../architecture/ArchFixtureIllegalAccountUser.java` | Create — a deliberate violation used to prove a rule bites | 1 |
| `lol-mcp-server/src/test/java/.../architecture/HexagonalArchitectureNegativeControlTest.java` | Create — asserts each package-string rule FAILS on a violation | 1 |
| `lol-mcp-server/src/test/java/.../architecture/HexagonalArchitectureTest.java` | Modify — fully-qualified matchers → relative | 1 |
| All `src/**/*.java` (3 modules) | Modify — package root `com.wkaiser.riot` → `com.muddl.riot` | 2 |
| `buildSrc/src/main/groovy/riot-java-conventions.gradle` | Modify — `group` → `com.muddl`; drop `version`; add `verifyRelease` | 2, 3, 4 |
| `{riot-api-core,riot-account-core,lol-mcp-server}/build.gradle` | Modify — declare own `version` | 3 |
| `{riot-api-core,riot-account-core,lol-mcp-server}/CHANGELOG.md` | Create — module-scoped history | 4 |
| `CHANGELOG.md` (root) | Modify — narrow scope to repo-wide changes | 4 |
| `build.gradle` (root) | Modify — add `printModuleVersions` for CI | 5 |
| `Dockerfile` | Modify — OCI provenance labels | 5 |
| `.github/workflows/release.yml` | Modify — module-scoped tags, per-module latest | 6 |
| `docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md` | Create | 7 |
| `.claude/skills/prepare-release/SKILL.md` | Create | 7 |
| `docs/knowledge/gotchas.md` | Modify — append two gotchas | 1, 3 |

---

### Task 1: Harden ArchUnit rules against vacuous passes

The spec puts this in Phase 0 alongside the rename. It must come **first and separately**: a rule can only be proven to bite while it still points at packages that exist. Prove, then fix, then re-prove — and only then move the packages (Task 2).

`HexagonalArchitectureTest:93` is the hazard. Its *selector* (`resideOutsideOfPackages("..lol.analytics..", "..lol.account..")`) is relative and still matches; only its *condition* (`resideInAPackage("com.wkaiser.riot.account..")`) carries the fully-qualified name. After a rename the condition matches nothing, so there are zero violations and the rule passes while enforcing nothing. Its own javadoc records that it exists because of a previous silent retirement.

**Files:**
- Create: `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/ArchFixtureIllegalAccountUser.java`
- Create: `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/HexagonalArchitectureNegativeControlTest.java`
- Modify: `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/HexagonalArchitectureTest.java:66,93`
- Modify: `docs/knowledge/gotchas.md` (append)

**Interfaces:**
- Consumes: `HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_library` and `.contexts_do_not_depend_on_each_other` — package-private `static final ArchRule` fields. The negative control lives in the same package to read them.
- Produces: nothing consumed by later tasks. Task 2 renames these files' packages along with everything else.

**Why the fixture is safe to add:** `HexagonalArchitectureTest` is annotated `@AnalyzeClasses(..., importOptions = {ImportOption.DoNotIncludeTests.class, ...})`. Test-source classes are excluded from the real scan, so a deliberate violation living in `src/test` cannot fail the real rule. The negative control imports it explicitly by class, bypassing that filter.

- [ ] **Step 1: Write the violating fixture**

Create `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/ArchFixtureIllegalAccountUser.java`:

```java
package com.wkaiser.riot.lol.architecture;

import com.wkaiser.riot.account.domain.RiotAccount;

/**
 * A deliberate architecture violation, used only as a negative control by {@link
 * HexagonalArchitectureNegativeControlTest}.
 *
 * <p>It reaches into the shared account library from a package outside the allowlist, which {@code
 * only_analytics_and_the_account_tool_use_the_account_library} forbids. It lives in test sources, so
 * {@code ImportOption.DoNotIncludeTests} keeps it out of the real scan in {@link
 * HexagonalArchitectureTest} — it can never fail the production rule. The negative control imports
 * it explicitly by class, which bypasses that filter.
 *
 * <p>Do not "fix" this class. Its violation is the point: it is the only evidence that the rule
 * still fails when it should, as opposed to passing vacuously.
 */
@SuppressWarnings("unused")
class ArchFixtureIllegalAccountUser {

    /** The forbidden dependency: a non-allowlisted context referencing the account library. */
    private RiotAccount account;
}
```

- [ ] **Step 2: Write the failing negative-control test**

Create `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/HexagonalArchitectureNegativeControlTest.java`:

```java
package com.wkaiser.riot.lol.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Proves the package-string-dependent rules in {@link HexagonalArchitectureTest} actually bite.
 *
 * <p>A green build is not evidence for these rules, because the failure mode <em>is</em> a green
 * build: {@code only_analytics_and_the_account_tool_use_the_account_library} carries its package in
 * the rule's <em>condition</em>, so if that package moves the condition matches nothing, zero
 * violations are found, and the rule passes while guarding nothing. That is exactly how the
 * prohibition this rule replaced was silently retired once already (see the rule's javadoc).
 *
 * <p>This test is inverted on purpose: it asserts the rule FAILS when fed a violation. If it ever
 * goes green by <em>not</em> throwing, the rule has stopped enforcing anything. That also covers the
 * condition's matcher specifically — point it at a package that does not exist and this test stops
 * throwing, which is the whole failure being guarded against.
 */
class HexagonalArchitectureNegativeControlTest {

    @Test
    void account_library_rule_rejects_a_non_allowlisted_context() {
        JavaClasses violating = new ClassFileImporter().importClasses(ArchFixtureIllegalAccountUser.class);

        assertThatThrownBy(() -> HexagonalArchitectureTest.only_analytics_and_the_account_tool_use_the_account_library
                        .check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ArchFixtureIllegalAccountUser");
    }
}
```

- [ ] **Step 3: Run the negative control — it must pass NOW, before any change**

```bash
./gradlew :lol-mcp-server:test --tests '*HexagonalArchitectureNegativeControlTest*'
```

Expected: **PASS**. This is the baseline — it proves the rule bites *today*, at `com.wkaiser`. If it fails here, stop: the rule is already vacuous and that is a bigger finding than this task.

- [ ] **Step 4: Make the rules group-agnostic**

In `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/HexagonalArchitectureTest.java`, replace the fully-qualified matchers with relative ones.

At `:66`, change the slice matcher:

```java
    static final ArchRule contexts_do_not_depend_on_each_other = slices().matching("..riot.lol.(*)..")
```

At `:93`, change the condition:

```java
            .resideInAPackage("..riot.account..");
```

`..riot.account..` does not match the LoL server's own `com.muddl.riot.lol.account` package: `..` matches whole package segments, so the matcher requires a segment `riot` followed immediately by `account`, and there `riot` is followed by `lol`.

Update that rule's javadoc, which names the old package:

```java
    /**
     * Only analytics (which composes it) and this server's thin account tool may reach into the
     * shared account library.
     * <p>
     * Before the monorepo split, the account context lived under this server's package root, so the
     * cross-context matrix forbade summoner/match/spectator from touching it. Extracting it to the
     * account library moved it outside {@link #contexts_do_not_depend_on_each_other}'s matcher,
     * which silently retired those three prohibitions — nothing violated them, so nothing failed.
     * This restores the guarantee.
     * <p>
     * Matchers here are deliberately relative ({@code ..riot.account..}, not a fully-qualified
     * name). This rule's package sits in its <em>condition</em>, not its selector, so a
     * fully-qualified name would make the rule pass vacuously the moment the group changed — the
     * same silent-retirement failure it exists to prevent. {@link
     * HexagonalArchitectureNegativeControlTest} proves it still bites.
     * <p>
     * riot-account-core is a domain context, not infrastructure (that distinction is why it is its
     * own module rather than part of riot-api-core), so "any module may consume it" is not the
     * intent. Stated as deny-by-default: a context added later is forbidden until listed here.
     */
```

- [ ] **Step 5: Run the full suite — rules still bite, nothing else moved**

```bash
./gradlew :lol-mcp-server:test
```

Expected: PASS, including `HexagonalArchitectureNegativeControlTest` and `HexagonalArchitectureTest`. The relative matchers must find the same violations as the fully-qualified ones did.

- [ ] **Step 6: Append the gotcha**

Append to the bottom of `docs/knowledge/gotchas.md`:

```markdown
## ArchUnit: a fully-qualified package in a rule's *condition* passes vacuously when that package moves

`noClasses().that().<selector>().should().dependOnClassesThat().resideInAPackage("com.example.foo..")`
has two package matchers, and they fail differently:

- The **selector** (`that()`) picks which classes are checked. If it matches nothing, ArchUnit fails
  loudly — a `should()` that checked zero classes is an error.
- The **condition** (`should()`) decides what counts as a violation. If it matches nothing, there
  are simply **zero violations — and the rule passes**, green, enforcing nothing.

This bit `only_analytics_and_the_account_tool_use_the_account_library`, whose condition named
`com.wkaiser.riot.account..`. The `com.wkaiser` → `com.muddl` rename would have left it green and
useless. The rule that exists *because* a prohibition was once silently retired was itself one
rename away from silent retirement.

Two rules follow:

1. **Keep matchers relative** (`..riot.account..`, not `com.muddl.riot.account..`) so no rule has an
   opinion about the group. `@AnalyzeClasses` needs a real root, but its failure is loud.
2. **Negative-control anything package-string-dependent.** A green build cannot distinguish
   "passing" from "vacuously passing". `HexagonalArchitectureNegativeControlTest` imports a
   deliberate violation and asserts the rule *fails*. If a negative control ever goes green by not
   throwing, the rule is dead.
```

- [ ] **Step 7: Format and commit**

```bash
./gradlew spotlessApply && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

```bash
git add lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/ docs/knowledge/gotchas.md
git commit -m "test: prove the account-library ArchUnit rule bites, and make matchers group-agnostic

The rule's package sat in its condition, not its selector: the selector is
relative and keeps matching, so a group rename would leave the condition
matching nothing, zero violations found, and the rule green while enforcing
nothing. That is the same silent retirement the rule was written to fix.

Matchers are now relative, and a negative control imports a deliberate
violation and asserts the rule fails — the only evidence that distinguishes
passing from vacuously passing."
```

---

### Task 2: Rename `com.wkaiser` → `com.muddl` (group and packages)

Pure motion. The Gradle group and the Java package root move together: Java convention is that the package root matches the group, and changing only one creates the inconsistency this cycle's sanity pass exists to remove. Nothing is externally breaking — the libraries are unpublished, the image name already derives from `github.repository_owner` (`muddl`), and the public MCP contract is the tool names, which Plan C handles.

Doing it now, with one server, is the cheapest it will ever be.

**Files:**
- Modify: every `.java` file under `{riot-api-core,riot-account-core,lol-mcp-server}/src/**` (package declarations, imports, and the directory tree)
- Modify: `buildSrc/src/main/groovy/riot-java-conventions.gradle:8`
- Modify: any `.imports`, `.yml`, `.md`, or workflow file naming `com.wkaiser`

**Interfaces:**
- Consumes: Task 1's relative matchers — without them this task would silently retire an ArchUnit rule.
- Produces: package root `com.muddl.riot.{core,account,lol}`; group `com.muddl`. Every later task and plan uses these.

- [ ] **Step 1: Inventory every occurrence before touching anything**

Search by **exclusion, not inclusion**. An allowlist of extensions is how a rename misses a file: this repo carries `com.wkaiser` in `additional-spring-configuration-metadata.json`, which an `--include='*.java' --include='*.gradle' --include='*.yml'` filter silently skips — and since that file is Spring Boot config metadata rather than compiled code, nothing goes red when it rots.

```bash
git grep -l 'com\.wkaiser\|com/wkaiser' -- . ':!docs/' ':!.superpowers/'
```

Expected: all three modules' sources, the convention plugin, both `AutoConfiguration.imports` files, `lol-mcp-server/src/main/resources/additional-spring-configuration-metadata.json`, and `CHANGELOG.md`. `git grep` respects `.gitignore`, so `build/` output is excluded for free.

Then the docs, which need judgment rather than substitution:

```bash
git grep -l 'com\.wkaiser' -- docs/knowledge/ CLAUDE.md README.md ARCHITECTURE.md CONTRIBUTING.md
```

Record both lists — Step 6 asserts the first reaches zero (except `CHANGELOG.md`'s released entries).

- [ ] **Step 2: Move the directory trees**

```bash
for m in riot-api-core riot-account-core lol-mcp-server; do
  for s in main test testFixtures; do
    if [ -d "$m/src/$s/java/com/wkaiser" ]; then
      mkdir -p "$m/src/$s/java/com/muddl"
      git mv "$m/src/$s/java/com/wkaiser/riot" "$m/src/$s/java/com/muddl/riot"
      rmdir "$m/src/$s/java/com/wkaiser"
    fi
  done
done
git status --short | head -40
```

Expected: renames staged for every source set that exists (`riot-api-core` has all three; `lol-mcp-server` has `main` and `test`).

- [ ] **Step 3: Rewrite the package references**

```bash
grep -rl 'com\.wkaiser' --include='*.java' --include='*.gradle' --include='*.imports' . \
  | grep -v '/build/' \
  | xargs sed -i 's/com\.wkaiser/com.muddl/g'
```

Then set the group in `buildSrc/src/main/groovy/riot-java-conventions.gradle:8`:

```groovy
group = 'com.muddl'
```

- [ ] **Step 4: Sweep the remaining non-source references**

```bash
grep -rn 'com\.wkaiser\|com/wkaiser' --include='*.yml' --include='*.md' . | grep -v '/build/'
```

Fix each hit by hand — these are docs and workflows, where surrounding prose may need to change, not just the string. Do **not** edit `docs/superpowers/specs/*.md`: dated specs are historical snapshots and are not retroactively edited (they legitimately discuss `com.wkaiser` as the state at the time).

- [ ] **Step 5: Build — pure motion means green**

```bash
./gradlew spotlessApply && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`. Anything red means the move is wrong, not that a feature broke. In particular `HexagonalArchitectureNegativeControlTest` must still pass — that is Task 1 earning its keep.

The negative control needs no edit: its fixture's `import com.wkaiser.riot.account.domain.RiotAccount` is rewritten by Step 3's `sed` like any other import, and the rule it exercises now uses relative matchers. That is the point — if a `sed` over string literals had been the only thing standing between this rename and a silently-retired rule, Task 1 would not have been necessary.

- [ ] **Step 6: Verify no reference survives**

Extension-agnostic, for the reason in Step 1 — an allowlist is how the miss happens:

```bash
git grep -l 'com\.wkaiser\|com/wkaiser' -- . ':!docs/' ':!.superpowers/' ':!CHANGELOG.md'
```

Expected: **no output**.

`CHANGELOG.md` is excluded because its *released* entries describe `com.wkaiser.riot.*` as the package root at the time they shipped — that is history and must not be rewritten. Everything under `docs/superpowers/` (dated specs and plans) is excluded for the same reason. `docs/knowledge/` is judged by hand in Step 4: some of it narrates this very rename and would become nonsense under substitution.

```bash
find . -path ./build -prune -o -type d -name wkaiser -print
```

Expected: no output — no empty directories left behind.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor!: move coordinates and package root to com.muddl

group = com.muddl and com.wkaiser.riot.* -> com.muddl.riot.*, together:
Java convention is that the package root matches the group, so moving one
without the other just trades one inconsistency for another.

Pure motion — no behaviour change. Nothing externally breaking: the
libraries are unpublished, the image name already derives from the GitHub
owner (muddl), and the MCP tool contract is untouched (McpToolInventoryTest
still green). Cheapest to do now, with one server, rather than after four."
```

---

### Task 3: Give each module its own version

`version = '0.0.2-SNAPSHOT'` is hardcoded in the convention plugin, so all three modules share one version *by construction*. Independent versioning is not merely undone — it is currently impossible.

**Files:**
- Modify: `buildSrc/src/main/groovy/riot-java-conventions.gradle:9` (delete the line)
- Modify: `riot-api-core/build.gradle`, `riot-account-core/build.gradle`, `lol-mcp-server/build.gradle`
- Modify: `docs/knowledge/gotchas.md` (append, only if Step 2 confirms the sharp edge)

**Interfaces:**
- Consumes: group `com.muddl` from Task 2.
- Produces: `project.version` == `'0.1.0'` for all three modules, declared per-module. Tasks 4, 5, and 6 read it.

- [ ] **Step 1: Verify the Gradle sharp edge before designing around it**

The spec says version goes in `build.gradle` rather than a per-module `gradle.properties`, because Gradle's handling of subproject properties files is a known sharp edge and the failure is silent. Verify rather than assume.

```bash
printf 'version=9.9.9-probe\n' > riot-api-core/gradle.properties
./gradlew -q :riot-api-core:properties --property version
```

Expected (the sharp edge is real): `version: unspecified` or `0.0.2-SNAPSHOT` — i.e. **not** `9.9.9-probe`. Gradle reads `gradle.properties` from the root project directory and `GRADLE_USER_HOME`, not from subprojects.

If it *does* print `9.9.9-probe`, the sharp edge does not apply on this Gradle version — record that and still use `build.gradle` (it keeps the version next to the module's other config), but skip the gotcha in Step 5.

```bash
rm riot-api-core/gradle.properties
```

- [ ] **Step 2: Drop `version` from the convention plugin**

In `buildSrc/src/main/groovy/riot-java-conventions.gradle`, delete line 9 (`version = '0.0.2-SNAPSHOT'`) so the block reads:

```groovy
// group is shared: every module publishes under the same coordinates. version is deliberately
// NOT here — it is per-module (see ADR-0010). A version is a module-specific fact, and a
// module-specific fact at the shared altitude is exactly how three modules ended up sharing one
// version number by construction.
group = 'com.muddl'
```

- [ ] **Step 3: Declare each module's version**

At the top of `riot-api-core/build.gradle`, immediately after the `plugins { }` block:

```groovy
// Independent of the other modules — see ADR-0010. 0.1.0: new coordinates, plus the 429 retry and
// error taxonomy landing in Plan B. Pre-1.0, so a breaking change bumps the minor.
version = '0.1.0'
```

At the top of `riot-account-core/build.gradle`, after `plugins { }`:

```groovy
// Independent of the other modules — see ADR-0010. 0.1.0: new coordinates, plus PlayerIdentityResolver
// landing in Plan B. Pre-1.0, so a breaking change bumps the minor.
version = '0.1.0'
```

At the top of `lol-mcp-server/build.gradle`, after `plugins { }`:

```groovy
// Independent of the other modules — see ADR-0010. 0.1.0: new coordinates, plus the tool contract
// break landing in Plan C. Pre-1.0, so a breaking change bumps the minor.
version = '0.1.0'
```

- [ ] **Step 4: Verify each module reports its own version**

**Do not use `-q` here.** The `properties` task logs at lifecycle level, which `-q` suppresses — you get empty output and a false read either way. (`-q` is still correct for `println`-based tasks like Task 5's `printModuleVersions`; that is a different mechanism.)

```bash
for m in riot-api-core riot-account-core lol-mcp-server; do
  echo -n "$m -> "; ./gradlew :$m:properties --property version 2>&1 | grep -i '^version:'
done
```

Expected: each prints `version: 0.1.0`. They agree today by coincidence, not by construction — Task 4 proves they can diverge.

Corroborate against the artifact names, which cannot lie about what the build actually produced:

```bash
./gradlew clean :riot-api-core:jar :lol-mcp-server:bootJar
ls riot-api-core/build/libs/ lol-mcp-server/build/libs/
```

Expected: every jar carries `-0.1.0`, and no `0.0.2-SNAPSHOT` artifact remains. The `clean` matters — without it, stale jars from the previous version linger and later tasks glob them (see Task 5).

- [ ] **Step 5: Append the gotcha (only if Step 1 confirmed it)**

Append to `docs/knowledge/gotchas.md`:

```markdown
## Gradle ignores `gradle.properties` in a subproject directory

Gradle reads `gradle.properties` from the **root project directory** and `GRADLE_USER_HOME` — not
from subproject directories. Dropping `riot-api-core/gradle.properties` with `version=1.2.3` in it
does nothing, and Gradle says nothing: the file is silently ignored, and the module keeps whatever
version it had.

This is why per-module versions are declared in each module's `build.gradle` (see ADR-0010) rather
than in a per-module properties file, which is the more obvious-looking option and does not work.

The convention plugin sets `group` (shared) but deliberately **not** `version` (per-module). A
version is a module-specific fact, and a module-specific fact at the shared altitude is how three
modules came to share one version number by construction.
```

- [ ] **Step 6: Build and commit**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

```bash
git add buildSrc riot-api-core/build.gradle riot-account-core/build.gradle lol-mcp-server/build.gradle docs/knowledge/gotchas.md
git commit -m "build: version each module independently

version was hardcoded in the convention plugin, so all three modules shared
one number by construction — independent versioning wasn't just undone, it
was impossible. group stays shared; version moves to each module.

All three land on 0.1.0 (from 0.0.2-SNAPSHOT) and diverge from here. They
agree today by coincidence, not by construction."
```

---

### Task 4: The alignment gate — version and CHANGELOG cannot drift

The observed problem: bumping and changelogging are two independent acts of discipline, so they drift. This makes them one act. Because versions are concrete (not `-SNAPSHOT`) on master, the check runs on **every** `./gradlew build`, not just releases.

**Files:**
- Modify: `buildSrc/src/main/groovy/riot-java-conventions.gradle` (add `verifyRelease`, wire into `check`)
- Create: `riot-api-core/CHANGELOG.md`, `riot-account-core/CHANGELOG.md`, `lol-mcp-server/CHANGELOG.md`
- Modify: `CHANGELOG.md` (root)

**Interfaces:**
- Consumes: `project.version` from Task 3.
- Produces: a `verifyRelease` task on every module, wired into `check`; module CHANGELOGs with a `## [0.1.0]` heading that Plans B–D append entries under.

- [ ] **Step 1: Add the `verifyRelease` task to the convention plugin**

Append to `buildSrc/src/main/groovy/riot-java-conventions.gradle`:

```groovy
// The version and the CHANGELOG are one act, not two.
//
// Bumping without a changelog entry used to be something you had to remember; here it is a red
// build. Versions are concrete (not -SNAPSHOT) on master, so this runs on every `./gradlew build`,
// not only at release time. The heading is matched without a date, so entries accumulate under the
// target version's heading during a development cycle and the date is filled in at tag time.
//
// See ADR-0010. This is the same "enforced, not documented" move as the @McpTool ArchUnit rule.
def moduleName = project.name
def moduleVersion = project.version.toString()
def changelogFile = file('CHANGELOG.md')

tasks.register('verifyRelease') {
	group = 'verification'
	description = "Fails if ${moduleName}'s version has no matching heading in its CHANGELOG.md."
	inputs.property('version', moduleVersion)
	inputs.file(changelogFile).withPropertyName('changelog')
	// No outputs: this is a pure assertion, so it runs every time rather than caching a "pass".
	outputs.upToDateWhen { false }

	doLast {
		if (!changelogFile.exists()) {
			throw new GradleException(
				"${moduleName}: CHANGELOG.md is missing. Every module owns its own changelog (ADR-0010).")
		}
		def heading = "## [${moduleVersion}]"
		if (!changelogFile.text.contains(heading)) {
			throw new GradleException(
				"${moduleName}: version is ${moduleVersion} but CHANGELOG.md has no '${heading}' heading.\n" +
				"  Add the heading and describe the change, or correct the version.\n" +
				"  A bump without an entry is the drift this gate exists to stop (ADR-0010).\n" +
				"  The /prepare-release skill walks the whole flow.")
		}
	}
}

tasks.named('check') {
	dependsOn tasks.named('verifyRelease')
}
```

- [ ] **Step 2: Run it and watch it fail — the modules have no CHANGELOGs yet**

```bash
./gradlew :riot-api-core:verifyRelease
```

Expected: **FAIL** with `riot-api-core: CHANGELOG.md is missing. Every module owns its own changelog (ADR-0010).`

This is the gate working before anything satisfies it.

- [ ] **Step 3: Create `riot-api-core/CHANGELOG.md`**

```markdown
# Changelog — `riot-api-core`

The shared Riot HTTP kernel: `RiotApiClient`, routing enums, `RiotApiProperties`, `RiotApiException`,
and the auto-configuration that registers them.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the other
modules keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.core`.
```

- [ ] **Step 4: Create `riot-account-core/CHANGELOG.md`**

```markdown
# Changelog — `riot-account-core`

The cross-game account-v1 context: `RiotAccount`, `RiotAccountService`, `RiotAccountPort`, and its
outbound adapter. Ships no `@McpTool` by design (ArchUnit-enforced) — each game server owns its own
inbound adapter so tool names can be namespaced per game.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the other
modules keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.account`.
```

- [ ] **Step 5: Create `lol-mcp-server/CHANGELOG.md`**

```markdown
# Changelog — `lol-mcp-server`

The League of Legends MCP server. Published as `ghcr.io/muddl/lol-mcp-server`.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the
libraries keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.lol`.

<!--
The MCP tool contract break (ten tools -> seven, single `player` param) lands in Plan C of
sub-project 1a and is logged here when it does. Do not pre-announce it: this file describes what
has shipped, not what is planned. The roadmap covers plans.
-->
```

- [ ] **Step 6: Narrow the root CHANGELOG's scope**

Replace the root `CHANGELOG.md` header (everything above the `## [Unreleased]` heading) with:

```markdown
# Changelog — repository

Repo-wide changes only: build tooling, CI, the module graph, and root documentation.

**Per-module history lives with the module** — [`riot-api-core`](riot-api-core/CHANGELOG.md),
[`riot-account-core`](riot-account-core/CHANGELOG.md), [`lol-mcp-server`](lol-mcp-server/CHANGELOG.md).
Each is independently versioned and tagged (`<module>/v<semver>`); see
[ADR-0010](docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

The rule: **a change is logged in the CHANGELOG of every module whose version it bumps.** This file
covers only changes that bump no module.

Entries below predate the per-module split and are kept as the repository's history to that point.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
```

Leave the existing `## [Unreleased]` block and everything under it untouched — it is sub-project 0's history and is accurate as repo-wide work.

- [ ] **Step 7: Verify the gate now passes, then prove it actually fails on drift**

```bash
./gradlew verifyRelease
```

Expected: `BUILD SUCCESSFUL` — all three modules satisfied.

Now prove the gate is not vacuous (same discipline as Task 1 — a passing gate that cannot fail is not a gate):

```bash
sed -i "s/^version = '0.1.0'$/version = '0.2.0'/" riot-api-core/build.gradle
./gradlew :riot-api-core:verifyRelease
```

Expected: **FAIL** with `riot-api-core: version is 0.2.0 but CHANGELOG.md has no '## [0.2.0]' heading.`

```bash
sed -i "s/^version = '0.2.0'$/version = '0.1.0'/" riot-api-core/build.gradle
./gradlew :riot-api-core:verifyRelease
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Full build and commit**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` — `check` now depends on `verifyRelease` in every module.

```bash
git add buildSrc CHANGELOG.md riot-api-core/CHANGELOG.md riot-account-core/CHANGELOG.md lol-mcp-server/CHANGELOG.md
git commit -m "build: gate every build on version <-> CHANGELOG agreement

Bumping and changelogging were two independent acts of discipline, so they
drifted. verifyRelease makes them one act: a version with no matching
'## [x.y.z]' heading in that module's CHANGELOG is a red build. Versions are
concrete on master, so this runs on every build, not just at release.

Each module now owns its changelog. Root narrows to repo-wide changes only:
a change is logged in the CHANGELOG of every module whose version it bumps."
```

---

### Task 5: Provenance stamping — make "which images embed vulnerable core?" answerable

Servers consume libraries via `project(':riot-api-core')` — a source reference. A library's version appears in no dependency declaration, so bumping it changes no resolution behaviour. That is a deliberate trade (it preserves ADR-0006's atomic cross-module commit), and this task buys back what it costs: the ability to answer *"core 0.1.0 has a flaw — which published images embed it?"* from the registry, without rebuilding or guessing.

**Files:**
- Modify: `lol-mcp-server/build.gradle` (bootJar manifest)
- Modify: `build.gradle` (root — add `printModuleVersions`)
- Modify: `Dockerfile` (OCI labels)

**Interfaces:**
- Consumes: `project.version` from Task 3.
- Produces: manifest attributes `Implementation-Version`, `Riot-Api-Core-Version`, `Riot-Account-Core-Version`; a `printModuleVersions` task emitting `<module>=<version>` lines; Dockerfile build args `SERVER_VERSION`, `RIOT_API_CORE_VERSION`, `RIOT_ACCOUNT_CORE_VERSION`. Task 6 passes those args from CI.

- [ ] **Step 1: Stamp the boot jar manifest**

Append to `lol-mcp-server/build.gradle`:

```groovy
// Provenance: record which library versions this jar embeds.
//
// The libraries are consumed by project reference, so their versions appear in no dependency
// declaration and nothing downstream records them. Without this, "core 0.1.0 has a flaw — which
// builds embed it?" is unanswerable. See ADR-0010.
//
// Read at configuration time into locals: settings.gradle includes the libraries before this
// module, so they are already evaluated. Step 3 verifies the real values landed rather than
// trusting that ordering.
def apiCoreVersion = project(':riot-api-core').version.toString()
def accountCoreVersion = project(':riot-account-core').version.toString()

tasks.named('bootJar') {
	manifest {
		attributes(
			'Implementation-Title': project.name,
			'Implementation-Version': project.version.toString(),
			'Riot-Api-Core-Version': apiCoreVersion,
			'Riot-Account-Core-Version': accountCoreVersion)
	}
}
```

- [ ] **Step 2: Build the jar**

```bash
./gradlew :lol-mcp-server:bootJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify the manifest carries real versions, not `unspecified`**

```bash
unzip -p lol-mcp-server/build/libs/lol-mcp-server-0.1.0.jar META-INF/MANIFEST.MF | grep -i 'Version\|Title'
```

Expected — all three concrete:

```
Implementation-Title: lol-mcp-server
Implementation-Version: 0.1.0
Riot-Api-Core-Version: 0.1.0
Riot-Account-Core-Version: 0.1.0
```

If any reads `unspecified`, cross-project evaluation ordering did not hold. Fix by adding to the top of `lol-mcp-server/build.gradle`, before the version reads:

```groovy
evaluationDependsOn(':riot-api-core')
evaluationDependsOn(':riot-account-core')
```

Then re-run Steps 2–3.

- [ ] **Step 4: Add `printModuleVersions` for CI to consume**

Replace the contents of the root `build.gradle` with:

```groovy
// Root project holds no code. Shared build logic lives in buildSrc/riot-java-conventions.gradle;
// each module applies it. Module list is in settings.gradle.

// Emits `<module>=<version>` for each module, one per line, for CI to read into step outputs
// (see .github/workflows/release.yml). Versions are per-module (ADR-0010), so a release needs to
// ask the build what they are rather than assume one number for the repo.
//
// Deliberately resolved in doLast: `subprojects` at the root's configuration time would run before
// the subprojects are evaluated, and every version would read `unspecified`.
tasks.register('printModuleVersions') {
	group = 'help'
	description = 'Prints each module version as <module>=<version>, one per line.'
	outputs.upToDateWhen { false }
	doLast {
		project.subprojects.each { sub -> println "${sub.name}=${sub.version}" }
	}
}
```

- [ ] **Step 5: Verify it prints all three**

```bash
./gradlew -q printModuleVersions
```

Expected exactly:

```
lol-mcp-server=0.1.0
riot-account-core=0.1.0
riot-api-core=0.1.0
```

No `unspecified` values. (Order follows Gradle's project ordering; CI parses by key, not position.)

- [ ] **Step 6a: Add a `.dockerignore` — the version bump made a latent bug live**

The repo has no `.dockerignore`, so `COPY riot-api-core ./riot-api-core` (`Dockerfile:20-22`) drags each module's local `build/` directory into the image context. `Dockerfile:40` then does:

```dockerfile
COPY --from=build /workspace/${SERVER_MODULE}/build/libs/*.jar app.jar
```

A glob matching more than one source against a single-file destination is an error in Docker. This was survivable while every local jar shared one version; now that Task 3 moved modules to `0.1.0`, a developer's `build/libs/` holds both `lol-mcp-server-0.0.2-SNAPSHOT.jar` and `lol-mcp-server-0.1.0.jar` (plus `-plain` variants), so the glob matches four files and the build fails. It also silently copies stale, wrong-version artifacts into a build that is supposed to compile from source.

Create `.dockerignore`:

```
# The image builds from source: every module's build/ is produced inside the build stage. Copying
# the host's build/ in is wrong twice over — it ships stale artifacts into a from-source build, and
# Dockerfile:40 globs build/libs/*.jar into a single-file destination, which fails outright once
# more than one version's jar is present locally.
**/build/
**/.gradle/
.gradle/
.git/
.github/
.superpowers/
docs/
*.md
!README.md
```

- [ ] **Step 6b: Verify the context shrank and the glob is unambiguous**

```bash
docker build --build-arg SERVER_MODULE=lol-mcp-server -t lol-mcp-server:ignore-check . 2>&1 | grep -i 'transferring context\|ERROR' | head -3
```

Expected: the context transfer is small (KBs/low MBs, not hundreds of MBs) and there is no `ERROR`. If Docker is unavailable, see the note in Step 7.

- [ ] **Step 6: Add OCI provenance labels to the Dockerfile**

In `Dockerfile`, in the **runtime stage**, after the existing `ARG SERVER_MODULE=lol-mcp-server` on line 33:

```dockerfile
# Provenance: which library versions are baked into this image. The libraries are consumed by
# project reference, so nothing else records them — without these labels, "core 0.1.0 has a flaw,
# which images embed it?" means rebuilding and guessing. See ADR-0010.
# Defaults are `dev` so a local `docker build` with no args still succeeds; CI passes real values.
ARG SERVER_VERSION=dev
ARG RIOT_API_CORE_VERSION=dev
ARG RIOT_ACCOUNT_CORE_VERSION=dev

LABEL org.opencontainers.image.title="${SERVER_MODULE}" \
      org.opencontainers.image.version="${SERVER_VERSION}" \
      org.opencontainers.image.source="https://github.com/Muddl/riot-api-mcp-server" \
      org.opencontainers.image.licenses="MIT" \
      com.muddl.riot.riot-api-core.version="${RIOT_API_CORE_VERSION}" \
      com.muddl.riot.riot-account-core.version="${RIOT_ACCOUNT_CORE_VERSION}"
```

- [ ] **Step 7: Build the image and verify the labels are queryable**

Requires a running Docker daemon. If `docker info` fails, do **not** skip this silently and do not block the task: report the Dockerfile change as complete with a `DONE_WITH_CONCERNS` status naming the unverified step, and the controller will run it once Docker is up. (This has happened before in this repo — see the progress ledger's Phase 6 notes.)

```bash
docker build \
  --build-arg SERVER_MODULE=lol-mcp-server \
  --build-arg SERVER_VERSION=0.1.0 \
  --build-arg RIOT_API_CORE_VERSION=0.1.0 \
  --build-arg RIOT_ACCOUNT_CORE_VERSION=0.1.0 \
  -t lol-mcp-server:provenance-check .

docker inspect lol-mcp-server:provenance-check \
  --format '{{json .Config.Labels}}' | tr ',' '\n'
```

Expected: labels present with concrete values, including `com.muddl.riot.riot-api-core.version":"0.1.0"`. This is the verification the spec asks for — the whole point is that this is answerable *without* rebuilding, so confirm it reads back off the built image.

```bash
docker rmi lol-mcp-server:provenance-check
```

- [ ] **Step 8: Commit**

```bash
git add lol-mcp-server/build.gradle build.gradle Dockerfile
git commit -m "build: stamp embedded library versions into jar manifest and image labels

Libraries are consumed by project reference, so their versions appear in no
dependency declaration — bumping one changes no resolution behaviour. That
trade is deliberate (it keeps ADR-0006's atomic cross-module commit), but it
costs the ability to answer 'core 0.1.0 has a flaw, which images embed it?'

The jar manifest and OCI labels buy that back: answerable from the registry,
without rebuilding or guessing."
```

---

### Task 6: Module-scoped release tags

`release.yml` triggers on `v*` and derives image tags from the **git tag** via `docker/metadata-action`'s `type=semver`. The Gradle version is never consulted — they are two unrelated numbers that nothing reconciles, which is the root of the drift this plan exists to fix. A flat `v*` namespace also cannot express `riot-api-core v0.2.0` and `lol-mcp-server v0.1.0` as distinct releases.

**Files:**
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: `printModuleVersions` and the Dockerfile build args from Task 5.
- Produces: releases triggered by `<module>/v<semver>`; server tags publish an image, library tags publish a GitHub Release only.

- [ ] **Step 1: Replace `.github/workflows/release.yml`**

```yaml
name: Release

# Tags are module-scoped: `<module>/v<semver>`, e.g. `lol-mcp-server/v0.1.0`. Each module is
# versioned and released independently (ADR-0010), so a flat `v*` namespace cannot say which
# module a release is for.
on:
  push:
    tags:
      - '*/v*'
  workflow_dispatch:
    inputs:
      module:
        description: 'Module to release (e.g. lol-mcp-server)'
        required: true
      version:
        description: 'Version to release (e.g. 0.1.0)'
        required: true

permissions:
  contents: read
  packages: write

jobs:
  # Parses the tag, then checks it against what the build actually says. This is the second half of
  # the alignment gate: verifyRelease proves version <-> CHANGELOG agree at build time; this proves
  # the tag agrees with the version. Previously the image tag came from the git tag and the jar's
  # version came from Gradle, and nothing reconciled them.
  resolve:
    name: Resolve and verify release target
    runs-on: ubuntu-latest
    outputs:
      module: ${{ steps.parse.outputs.module }}
      version: ${{ steps.parse.outputs.version }}
      is_server: ${{ steps.parse.outputs.is_server }}
      # Index syntax, not dot access: module names contain hyphens, which a GitHub Actions
      # expression parses as subtraction (`outputs.riot-api-core` reads as riot minus api minus core).
      api_core_version: ${{ steps.versions.outputs['riot-api-core'] }}
      account_core_version: ${{ steps.versions.outputs['riot-account-core'] }}
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'zulu'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582 # v4.0.0

      # docker/metadata-action's type=semver cannot parse a prefixed tag, so extraction is explicit.
      - name: Parse tag
        id: parse
        run: |
          set -euo pipefail
          if [ "${{ github.event_name }}" = "workflow_dispatch" ]; then
            MODULE="${{ inputs.module }}"
            VERSION="${{ inputs.version }}"
          else
            REF="${GITHUB_REF#refs/tags/}"          # e.g. lol-mcp-server/v0.1.0
            MODULE="${REF%%/v*}"
            VERSION="${REF#*/v}"
          fi

          if [ -z "$MODULE" ] || [ -z "$VERSION" ]; then
            echo "::error::Could not parse module/version from '${GITHUB_REF#refs/tags/}'. Expected <module>/v<semver>."
            exit 1
          fi
          if [ ! -d "$MODULE" ]; then
            echo "::error::Module '$MODULE' is not a directory in this repo."
            exit 1
          fi

          # Only server modules produce images. A library tag is a GitHub Release and nothing more:
          # the libraries are versioned for provenance, not published as artifacts (ADR-0010).
          case "$MODULE" in
            *-mcp-server) IS_SERVER=true ;;
            *)            IS_SERVER=false ;;
          esac

          echo "module=$MODULE"     >> "$GITHUB_OUTPUT"
          echo "version=$VERSION"   >> "$GITHUB_OUTPUT"
          echo "is_server=$IS_SERVER" >> "$GITHUB_OUTPUT"
          echo "Releasing $MODULE $VERSION (server image: $IS_SERVER)"

      # Emits `<module>=<version>` lines straight into GITHUB_OUTPUT. -q is load-bearing: any other
      # Gradle output (warnings, task banners) would land in GITHUB_OUTPUT and corrupt it.
      - name: Read module versions from the build
        id: versions
        run: ./gradlew -q printModuleVersions >> "$GITHUB_OUTPUT"

      # The tag is a claim; the build is the fact. If they disagree, the tag is wrong.
      - name: Verify the tag matches the module's declared version
        run: |
          set -euo pipefail
          TAG_VERSION="${{ steps.parse.outputs.version }}"
          BUILD_VERSION="${{ steps.versions.outputs[steps.parse.outputs.module] }}"
          if [ "$TAG_VERSION" != "$BUILD_VERSION" ]; then
            echo "::error::Tag says ${{ steps.parse.outputs.module }} $TAG_VERSION, but its build.gradle says $BUILD_VERSION."
            exit 1
          fi
          echo "Tag and build agree: $TAG_VERSION"

      # verifyRelease also asserts the CHANGELOG has a matching heading.
      - name: Verify the module builds and its changelog is aligned
        run: ./gradlew :${{ steps.parse.outputs.module }}:build

  publish-image:
    name: Build & publish container image
    needs: resolve
    if: needs.resolve.outputs.is_server == 'true'
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # Tags are computed from the parsed version, not from the raw git ref: type=semver cannot
      # read a prefixed tag. `latest` is applied only when this is the newest release for THIS
      # module — previously it was unconditional, so re-releasing an older patch moved `latest`
      # backwards.
      - name: Compute image tags
        id: tags
        run: |
          set -euo pipefail
          MODULE="${{ needs.resolve.outputs.module }}"
          VERSION="${{ needs.resolve.outputs.version }}"
          IMAGE="ghcr.io/$(echo '${{ github.repository_owner }}' | tr '[:upper:]' '[:lower:]')/$MODULE"
          MINOR="${VERSION%.*}"

          TAGS="$IMAGE:$VERSION,$IMAGE:$MINOR"

          # Highest existing tag for this module, compared with sort -V.
          LATEST=$(git tag --list "$MODULE/v*" | sed "s|^$MODULE/v||" | sort -V | tail -1)
          if [ "$VERSION" = "$LATEST" ]; then
            TAGS="$TAGS,$IMAGE:latest"
            echo "$VERSION is the highest tag for $MODULE — moving :latest."
          else
            echo "$VERSION is not the highest tag for $MODULE (that is $LATEST) — leaving :latest alone."
          fi

          echo "tags=$TAGS" >> "$GITHUB_OUTPUT"

      - name: Build and push image
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ./Dockerfile
          build-args: |
            SERVER_MODULE=${{ needs.resolve.outputs.module }}
            SERVER_VERSION=${{ needs.resolve.outputs.version }}
            RIOT_API_CORE_VERSION=${{ needs.resolve.outputs.api_core_version }}
            RIOT_ACCOUNT_CORE_VERSION=${{ needs.resolve.outputs.account_core_version }}
          push: true
          tags: ${{ steps.tags.outputs.tags }}

  # Libraries are versioned and tagged but not published (ADR-0010): a library tag records that a
  # version exists and what changed in it. Note it ships nothing on its own — the fix reaches users
  # only when each server re-releases and re-stamps its image.
  publish-release-notes:
    name: Publish GitHub Release
    needs: resolve
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          name: ${{ needs.resolve.outputs.module }} ${{ needs.resolve.outputs.version }}
          body_path: ${{ needs.resolve.outputs.module }}/CHANGELOG.md
          draft: true
```

- [ ] **Step 2: Validate the workflow parses**

```bash
gh workflow view release.yml 2>/dev/null || python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/release.yml')); print('YAML OK')"
```

Expected: `YAML OK` (or `gh` renders the workflow).

- [ ] **Step 3: Verify the tag-parsing logic locally before trusting it in CI**

The tag path only executes on a real tag, so exercise the parser directly:

```bash
for REF in 'lol-mcp-server/v0.1.0' 'riot-api-core/v0.2.1' 'riot-account-core/v1.0.0'; do
  MODULE="${REF%%/v*}"; VERSION="${REF#*/v}"
  case "$MODULE" in *-mcp-server) IS_SERVER=true ;; *) IS_SERVER=false ;; esac
  echo "$REF -> module=$MODULE version=$VERSION is_server=$IS_SERVER dir_exists=$([ -d "$MODULE" ] && echo yes || echo no)"
done
```

Expected:

```
lol-mcp-server/v0.1.0 -> module=lol-mcp-server version=0.1.0 is_server=true dir_exists=yes
riot-api-core/v0.2.1 -> module=riot-api-core version=0.2.1 is_server=false dir_exists=yes
riot-account-core/v1.0.0 -> module=riot-account-core version=1.0.0 is_server=false dir_exists=yes
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: module-scoped release tags, and reconcile tag with build version

Tags become <module>/v<semver>. A flat v* namespace can't say which module a
release is for, now that modules version independently.

Image tags came from the git tag while the jar's version came from Gradle,
and nothing reconciled them — that gap is why bumps and changelogs drifted.
The resolve job now fails if the tag disagrees with the module's build.gradle,
and runs that module's build (which includes verifyRelease).

:latest moves only when the released version is the highest for that module;
it was previously unconditional, so re-releasing an old patch moved it back.
Library tags publish release notes but no image: libraries are versioned for
provenance, not published (ADR-0010)."
```

- [ ] **Step 5: Exercise the workflow before trusting it**

The tag-triggered path cannot be tested locally. Once pushed, run it via `workflow_dispatch` (the release job creates a **draft**, so this is safe):

```bash
gh workflow run release.yml -f module=lol-mcp-server -f version=0.1.0
gh run watch
```

Expected: the `resolve` job passes tag/version verification and the module build; `publish-image` runs; a draft release appears. Delete the draft afterwards:

```bash
gh release list && gh release delete 'lol-mcp-server 0.1.0' --yes
```

If `resolve` fails on version mismatch, that is the gate working — reconcile the input with `build.gradle`.

---

### Task 7: Record the decision — ADR-0010, the `prepare-release` skill, roadmap status

The gate checks that a version and a changelog heading agree. It cannot tell that a module *should* have been bumped and wasn't — that is judgment, and it belongs in a skill.

**Files:**
- Create: `docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md`
- Create: `.claude/skills/prepare-release/SKILL.md`
- Modify: `docs/knowledge/README.md` (ADR index)
- Modify: `docs/knowledge/roadmap.md` (status)

**Interfaces:**
- Consumes: everything from Tasks 2–6.
- Produces: the durable record. Plans B–D cite ADR-0010 in their changelog entries.

- [ ] **Step 1: Write ADR-0010**

Create `docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md`:

```markdown
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
  runs before subprojects are evaluated and reads `unspecified`.
```

- [ ] **Step 2: Write the `prepare-release` skill**

Create `.claude/skills/prepare-release/SKILL.md`:

```markdown
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
```

- [ ] **Step 3: Link ADR-0010 from the knowledge index**

In `docs/knowledge/README.md`, append to the ADR list:

```markdown
- [ADR-0010 — Artifact coordinates, per-module versioning, and provenance stamping](decisions/ADR-0010-versioning-and-coordinates.md)
```

- [ ] **Step 4: Update the roadmap status**

In `docs/knowledge/roadmap.md`, change the 1a status cell from `📋 Planned` to `🔨 In progress` and append to the 1a scope section:

```markdown
**Progress:** Plan A (coordinates + release engineering) complete. Plans B (libraries), C (LoL
server), D (docs + sanity check) follow.
```

- [ ] **Step 5: Verify the skill is discoverable**

```bash
ls .claude/skills/ && head -4 .claude/skills/prepare-release/SKILL.md
```

Expected: `prepare-release` alongside the existing skills, with valid frontmatter (`name`, `description`).

- [ ] **Step 6: Final full build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. This is Plan A's exit gate: three independently versioned modules, each with an aligned changelog, on `com.muddl` coordinates, with ArchUnit rules proven to bite.

- [ ] **Step 7: Commit**

```bash
git add docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md .claude/skills/prepare-release/ docs/knowledge/README.md docs/knowledge/roadmap.md
git commit -m "docs: ADR-0010 and the prepare-release skill

Records why libraries are versioned but unpublished (project references keep
ADR-0006's atomic cross-module commit; provenance stamping buys back what
that costs), the tag namespace, and the changelog gate.

The skill carries what the gate can't: which modules a body of work actually
bumped, and that a library bump usually cascades into a server patch release
— a library tag ships nothing on its own, which is the most likely way to
believe something is fixed when it isn't."
```

---

## Plan A exit criteria

- `./gradlew build` green.
- `git grep -l 'com\.wkaiser' -- . ':!docs/' ':!.superpowers/' ':!CHANGELOG.md'` returns nothing. Extension-agnostic on purpose: an `--include=` allowlist already missed `additional-spring-configuration-metadata.json` once during Task 2, and that file is not compile-validated, so a stale FQN there is silent.
- Each module reports its own `0.1.0` via `./gradlew -q printModuleVersions`.
- `verifyRelease` **demonstrated to fail** on a version/changelog mismatch, not merely observed passing.
- `HexagonalArchitectureNegativeControlTest` green — the account-library rule provably still bites after the rename.
- `McpToolInventoryTest` green and unchanged: ten tools, same names. Plan A changed no behaviour.
- A built image's labels report the embedded library versions under `docker inspect`.

## What Plan B picks up

Phases 2–3 — both libraries, no server code:

- **`riot-api-core`:** 429 retry honouring `Retry-After`, and the error taxonomy. Retry belongs in a `ClientHttpRequestInterceptor`, not the existing `defaultStatusHandler` (`RiotApiClient.java:38-42`), which throws — by the time it runs, the decision to fail is already made. The interceptor needs only the status code and `Retry-After` header, never the body, so it will not consume the stream the status handler later reads.
- **`riot-account-core`:** `PlayerIdentityResolver`, TTL-cached on an injected clock, and the ArchUnit rule split that keeps the account *domain* confined while identity resolution goes open.
