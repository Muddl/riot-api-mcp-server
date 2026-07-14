# Phase 3: Quality Gates (ArchUnit + JaCoCo + Spotless) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire Spotless, JaCoCo, and ArchUnit into the Gradle build so a single `./gradlew build` enforces the hexagonal architecture, measures coverage against a soft floor, and fails on formatting drift — all offline, with no `RIOT_API_KEY`.

**Architecture:** Three build-time gates layer onto the post-Phase-1 bounded-context structure. Spotless (palantir-java-format) normalizes every Java file and fails the build on drift; JaCoCo produces a coverage report on `test` and verifies a conservative line-coverage floor (Lombok DTOs excluded); ArchUnit encodes Decision 1's inward-only layering, `RestClient`/`@McpTool` locality, port-interface, cross-context, and naming rules as JUnit 5 `@ArchTest` rules that analyze only production classes. All three hang off `check`/`build`, so no extra CI step is needed (CI wiring is Phase 4).

**Tech Stack:** Gradle 9.6.1, Spotless `7.0.2` + palantir-java-format `2.50.0`, JaCoCo `0.8.12`, ArchUnit `1.3.0` (`archunit-junit5`), JUnit 5, Java 21, Spring Boot 4.1.0, Spring AI BOM 2.0.0.

## Global Constraints

- Package root: `com.wkaiser.riotapimcpserver`. Contexts are **top-level**: `account`, `summoner`, `match`, `spectator`, `analytics`, `shared` (post-Phase-1 structure — the old `riot`/`riot.lol` tree no longer exists).
- Do not change versions: Java toolchain 21, Spring Boot `4.1.0`, Spring AI BOM `2.0.0`, Gradle wrapper `9.6.1`.
- Chosen gate coordinates (do not substitute without re-verifying Java 21 / Gradle 9.6.1 compatibility): Spotless plugin `com.diffplug.spotless` `7.0.2`; formatter `palantirJavaFormat('2.50.0')`; JaCoCo `toolVersion = '0.8.12'`; `com.tngtech.archunit:archunit-junit5:1.3.0`.
- WireMock `org.wiremock:wiremock-standalone:3.9.2` (`testImplementation`) was already added in Phase 1 Task 2 — **reuse it, do not re-add**.
- All three gates must run under `./gradlew build` (via the `check` lifecycle task). No new GitHub workflow is created here.
- The build must pass **offline** with **no** `RIOT_API_KEY` set (all tests are unit + WireMock + ArchUnit; none hit the live Riot API).
- After Spotless is enabled (Task 1), every subsequently created or edited `.java` file must satisfy `spotlessCheck`. Run `./gradlew spotlessApply` before committing any task that adds Java.
- Commit messages are plain Conventional Commits (`build:`/`test:`/`style:`/`chore:`) with **no** co-author trailer.
- All commands are run from **Git Bash** on Windows.
- The `@McpTool` annotation is `org.springframework.ai.mcp.annotation.McpTool` and is applied to **methods** (not types). ArchUnit rules for it use `methods()`.

---

### Task 1: Spotless — palantir-java-format wired into the build

Spotless goes **first**: enabling it and running `spotlessApply` reformats the entire source tree in one commit, so every later task builds on already-formatted code and `spotlessCheck` stays green. palantir-java-format `2.50.0` is Java 21-compatible; on JDK 16+ the formatter needs `--add-exports jdk.compiler/...` internals, which Spotless `7.0.2` supplies automatically by running the formatter in a dedicated worker — no manual JVM args required.

**Files:**
- Modify: `build.gradle` (add the `com.diffplug.spotless` plugin and a `spotless { }` block)
- Modify: (bulk, mechanical) every `.java` file under `src/` — reformatted by `spotlessApply`

**Interfaces:**
- Consumes: nothing.
- Produces: the `spotlessCheck` and `spotlessApply` Gradle tasks. `spotlessCheck` is auto-wired as a dependency of `check` by the plugin (verified in Step 4).

- [ ] **Step 1: Add the Spotless plugin**

In `build.gradle`, change the `plugins { }` block from:

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.0'
	id 'io.spring.dependency-management' version '1.1.7'
}
```

to:

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.0'
	id 'io.spring.dependency-management' version '1.1.7'
	id 'com.diffplug.spotless' version '7.0.2'
}
```

- [ ] **Step 2: Add the `spotless` configuration block**

In `build.gradle`, add this block immediately **after** the `dependencyManagement { ... }` block (and before `tasks.named('test') { ... }`):

```groovy
spotless {
	java {
		target 'src/main/java/**/*.java', 'src/test/java/**/*.java'
		palantirJavaFormat('2.50.0')
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}
```

- [ ] **Step 3: Verify Spotless resolves and reports drift (before applying)**

Run:

```bash
./gradlew spotlessCheck
```

Expected: `BUILD FAILED` (task `:spotlessCheck`) with output naming files that violate the format, e.g. `The following files had format violations:` — this proves the plugin resolved and is scanning the tree. (A pristine tree could theoretically already be conformant and print `BUILD SUCCESSFUL`; either outcome is acceptable here — the next step normalizes everything regardless.)

- [ ] **Step 4: Confirm `spotlessCheck` is wired into `check`**

Run:

```bash
./gradlew check --dry-run | grep -i spotlessCheck
```

Expected: a line such as `:spotlessCheck SKIPPED` appears in the `check` task graph, confirming the Spotless plugin auto-wired `check.dependsOn spotlessCheck` (so `./gradlew build` will gate on formatting). If no line appears, add an explicit `tasks.named('check') { dependsOn 'spotlessCheck' }` to `build.gradle` and re-run.

- [ ] **Step 5: Reformat the whole tree**

Run:

```bash
./gradlew spotlessApply
```

Expected: `BUILD SUCCESSFUL`. This rewrites every `.java` file under `src/` into palantir-java-format style (4-space indentation, 120-column wrap, ordered imports, unused imports removed).

- [ ] **Step 6: Verify the tree is now clean and still builds**

Run:

```bash
./gradlew spotlessCheck
```

Expected: `BUILD SUCCESSFUL` (task `:spotlessCheck` UP-TO-DATE or passing — no violations).

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` — the reformat is purely cosmetic; all Phase 1/Phase 2 tests still pass.

- [ ] **Step 7: Commit the plugin and the reformat together**

```bash
git add build.gradle
git add -A src
git commit -m "build: add Spotless with palantir-java-format and reformat sources"
```

---

### Task 2: JaCoCo — coverage report and a soft verification floor

Apply the built-in `jacoco` plugin, generate a report on `test`, and verify a **conservative** line-coverage floor (`0.30`). The floor exists to make coverage *measured and visible*, not to block legitimate work; Lombok DTOs (`**/domain/**`) and the Spring Boot main class are excluded so generated accessors do not distort the ratio. JaCoCo `0.8.12` is the first release with full JDK 21 bytecode support and runs on Gradle 9.6.1.

**Files:**
- Modify: `build.gradle` (apply `jacoco` plugin; configure `jacoco`, `test`, `jacocoTestReport`, `jacocoTestCoverageVerification`, and `check`)

**Interfaces:**
- Consumes: the `test` task.
- Produces: `jacocoTestReport` (HTML + XML) and `jacocoTestCoverageVerification`, the latter wired into `check`.

- [ ] **Step 1: Apply the `jacoco` plugin**

In `build.gradle`, add `id 'jacoco'` to the `plugins { }` block (which now also carries Spotless from Task 1):

```groovy
plugins {
	id 'java'
	id 'jacoco'
	id 'org.springframework.boot' version '4.1.0'
	id 'io.spring.dependency-management' version '1.1.7'
	id 'com.diffplug.spotless' version '7.0.2'
}
```

- [ ] **Step 2: Add the JaCoCo configuration**

In `build.gradle`, add the following **after** the `spotless { ... }` block and **before** `tasks.named('test') { ... }`:

```groovy
jacoco {
	toolVersion = '0.8.12'
}

// Generated accessors and the bootstrap class are excluded so they do not distort the ratio.
def coverageExclusions = [
	'**/domain/**',
	'**/RiotApiMcpServerApplication.class'
]

jacocoTestReport {
	dependsOn tasks.named('test')
	reports {
		xml.required = true
		html.required = true
	}
	afterEvaluate {
		classDirectories.setFrom(files(classDirectories.files.collect {
			fileTree(dir: it, exclude: coverageExclusions)
		}))
	}
}

jacocoTestCoverageVerification {
	dependsOn tasks.named('jacocoTestReport')
	violationRules {
		rule {
			limit {
				counter = 'LINE'
				value = 'COVEREDRATIO'
				// Soft floor: presence-of-coverage signal, not an arbitrary gate.
				minimum = 0.30
			}
		}
	}
	afterEvaluate {
		classDirectories.setFrom(files(classDirectories.files.collect {
			fileTree(dir: it, exclude: coverageExclusions)
		}))
	}
}

tasks.named('check') {
	dependsOn tasks.named('jacocoTestCoverageVerification')
}
```

- [ ] **Step 3: Bind report generation to `test`**

In `build.gradle`, change the existing test-configuration block from:

```groovy
tasks.named('test') {
	useJUnitPlatform()
}
```

to:

```groovy
tasks.named('test') {
	useJUnitPlatform()
	finalizedBy tasks.named('jacocoTestReport')
}
```

- [ ] **Step 4: Verify the report is produced on test**

Run:

```bash
./gradlew clean test
```

Expected: `BUILD SUCCESSFUL`.

Run:

```bash
test -f build/reports/jacoco/test/html/index.html && test -f build/reports/jacoco/test/jacocoTestReport.xml && echo "report present"
```

Expected: `report present`.

- [ ] **Step 5: Verify the coverage floor passes and is wired into `check`**

Run:

```bash
./gradlew jacocoTestCoverageVerification
```

Expected: `BUILD SUCCESSFUL` (measured line coverage of non-DTO code is well above `0.30` given the Phase 1/Phase 2 service and adapter tests).

Run:

```bash
./gradlew check --dry-run | grep -i jacocoTestCoverageVerification
```

Expected: a `:jacocoTestCoverageVerification` line appears in the `check` graph, confirming `./gradlew build` will enforce the floor.

- [ ] **Step 6: Confirm the full build is still green**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add build.gradle
git commit -m "build: add JaCoCo coverage report and soft verification floor"
```

---

### Task 3: ArchUnit — the hexagonal rule suite

Add `archunit-junit5` and encode **every** Decision 3 rule as JUnit 5 `@ArchTest` rules. ArchUnit `1.3.0` runs on the JUnit 5 Platform (already on the classpath via `spring-boot-starter-test`) and analyzes Java 21 bytecode. The suite lives in `com.wkaiser.riotapimcpserver.architecture` and analyzes **production classes only** (`ImportOption.DoNotIncludeTests`), so test scaffolding never trips a rule and the architecture suite does not analyze itself.

These rules encode invariants that the post-Phase-1 code **already satisfies**, so they pass immediately on write; Task 4 then proves they have teeth by introducing (and reverting) a deliberate violation. Two rules are refined from the spec's prose to match the built code and are called out in the Self-Review:
- **`RestClient` locality** additionally permits `..shared.http..`, because the single client factory `shared/http/RiotApiClient` is by design the one place that constructs `RestClient` (Decision 1); the outbound adapters obtain their clients from it.
- **Cross-context composition** permits `spectator -> summoner.application` in addition to `analytics -> {account,summoner,match}`: Phase 1's `LiveGameTool` (spectator) composes `SummonerService` (summoner) to resolve a summoner name to an encrypted ID before calling the Spectator API. The invariant preserved is that **no context reaches into another context's `adapter` internals** (enforced by the layered rule) and leaf providers (`account`, `match`) stay independent.

**Files:**
- Modify: `build.gradle` (add the `archunit-junit5` test dependency)
- Create: `src/test/java/com/wkaiser/riotapimcpserver/architecture/HexagonalArchitectureTest.java`

**Interfaces:**
- Consumes: the compiled production classes of all contexts.
- Produces: `HexagonalArchitectureTest` with 12 `@ArchTest` rules covering layering, `RestClient` locality, `@McpTool` locality, port interfaces, cross-context composition, and the four naming conventions.

- [ ] **Step 1: Add the ArchUnit test dependency**

In `build.gradle`, inside the `dependencies { ... }` block, add this line directly below the existing WireMock line (added in Phase 1 Task 2):

```groovy
	testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```

The relevant region of `dependencies` then reads:

```groovy
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
	testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

- [ ] **Step 2: Create the architecture rule suite**

Create `src/test/java/com/wkaiser/riotapimcpserver/architecture/HexagonalArchitectureTest.java`:

```java
package com.wkaiser.riotapimcpserver.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the bounded-context hexagon defined in Decision 1 (see ARCHITECTURE.md). Only production
 * classes are analyzed ({@link ImportOption.DoNotIncludeTests}); the architecture suite itself and all
 * other test scaffolding are excluded from analysis.
 */
@AnalyzeClasses(
        packages = "com.wkaiser.riotapimcpserver",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String ROOT = "com.wkaiser.riotapimcpserver.";
    private static final String ACCOUNT = ROOT + "account..";
    private static final String SUMMONER = ROOT + "summoner..";
    private static final String MATCH = ROOT + "match..";
    private static final String SPECTATOR = ROOT + "spectator..";
    private static final String ANALYTICS = ROOT + "analytics..";

    // --- Rule 1: inward-only layering (adapter -> application -> domain). ---
    @ArchTest
    static final ArchRule layers_respect_inward_dependency_rule = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy("..domain..")
            .layer("Application")
            .definedBy("..application..")
            .layer("Adapter")
            .definedBy("..adapter..")
            .whereLayer("Adapter")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Adapter");

    // --- Rule 2: RestClient only in outbound Riot adapters and the shared client factory. ---
    @ArchTest
    static final ArchRule restclient_confined_to_outbound_adapters_and_shared_http = noClasses()
            .that()
            .resideOutsideOfPackages("..adapter.out.riot..", "..shared.http..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestClient");

    // --- Rule 3: @McpTool methods only in inbound MCP adapters. ---
    @ArchTest
    static final ArchRule mcp_tools_only_in_inbound_adapters = methods()
            .that()
            .areAnnotatedWith("org.springframework.ai.mcp.annotation.McpTool")
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..adapter.in.mcp..");

    // --- Rule 4: ports are interfaces residing in ..application.port.. ---
    @ArchTest
    static final ArchRule ports_are_interfaces =
            classes().that().resideInAPackage("..application.port..").should().beInterfaces();

    // --- Rule 5: cross-context composition is restricted (leaf providers stay independent). ---
    @ArchTest
    static final ArchRule account_depends_on_no_other_context = noClasses()
            .that()
            .resideInAPackage(ACCOUNT)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(SUMMONER, MATCH, SPECTATOR, ANALYTICS);

    @ArchTest
    static final ArchRule summoner_depends_on_no_other_context = noClasses()
            .that()
            .resideInAPackage(SUMMONER)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ACCOUNT, MATCH, SPECTATOR, ANALYTICS);

    @ArchTest
    static final ArchRule match_depends_on_no_other_context = noClasses()
            .that()
            .resideInAPackage(MATCH)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ACCOUNT, SUMMONER, SPECTATOR, ANALYTICS);

    // spectator composes summoner (LiveGameTool -> SummonerService); no other cross-context deps.
    @ArchTest
    static final ArchRule spectator_only_composes_summoner = noClasses()
            .that()
            .resideInAPackage(SPECTATOR)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ACCOUNT, MATCH, ANALYTICS);

    // analytics composes account/summoner/match; it must never touch spectator.
    @ArchTest
    static final ArchRule analytics_does_not_depend_on_spectator = noClasses()
            .that()
            .resideInAPackage(ANALYTICS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(SPECTATOR);

    // --- Rule 6: naming conventions bind a type to its layer. ---
    @ArchTest
    static final ArchRule services_live_in_application = classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .should()
            .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule tools_live_in_inbound_adapters = classes()
            .that()
            .haveSimpleNameEndingWith("Tool")
            .should()
            .resideInAPackage("..adapter.in.mcp..");

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot = classes()
            .that()
            .haveSimpleNameEndingWith("Adapter")
            .should()
            .resideInAPackage("..adapter.out.riot..");

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces = classes()
            .that()
            .haveSimpleNameEndingWith("Port")
            .should()
            .resideInAPackage("..application.port..")
            .andShould()
            .beInterfaces();
}
```

- [ ] **Step 3: Format the new file so `spotlessCheck` stays green**

Run:

```bash
./gradlew spotlessApply
```

Expected: `BUILD SUCCESSFUL` (idempotent for already-conformant code; normalizes the new file if needed).

- [ ] **Step 4: Run the architecture suite and confirm every rule passes**

Run:

```bash
./gradlew test --tests "com.wkaiser.riotapimcpserver.architecture.HexagonalArchitectureTest"
```

Expected: `BUILD SUCCESSFUL` — all 12 `@ArchTest` rules pass, because the post-Phase-1 structure already conforms. (If any rule fails here, the production code diverges from Decision 1 — stop and reconcile the code, not the rule.)

- [ ] **Step 5: Run the full build**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` — the architecture suite now runs as part of `test`, gated under `build`.

- [ ] **Step 6: Commit**

```bash
git add build.gradle \
        src/test/java/com/wkaiser/riotapimcpserver/architecture/HexagonalArchitectureTest.java
git commit -m "test: enforce hexagonal architecture with ArchUnit rule suite"
```

---

### Task 4: Consolidate the gates and prove ArchUnit has teeth

All three gates are now in `build.gradle` and hang off `check`. This task confirms the consolidated state, demonstrates the ArchUnit gate actually fails on a real violation (then reverts), and runs the final offline, key-less `./gradlew build`.

**Files:**
- Verify only: `build.gradle` (no new edits — a temporary, reverted edit to a source file is used to prove the gate).

**Interfaces:**
- Consumes: everything wired in Tasks 1–3.
- Produces: nothing new; a verified, green, gated build.

- [ ] **Step 1: Confirm all gate wiring is present in `build.gradle`**

Run:

```bash
grep -nE "com\.diffplug\.spotless|id 'jacoco'|palantirJavaFormat|jacocoTestCoverageVerification|archunit-junit5|wiremock-standalone" build.gradle
```

Expected: at least six matching lines — the Spotless plugin id, the `jacoco` plugin id, `palantirJavaFormat('2.50.0')`, `jacocoTestCoverageVerification`, `archunit-junit5:1.3.0`, and the pre-existing `wiremock-standalone:3.9.2`.

- [ ] **Step 2: Confirm all three gates are in the `check`/`build` graph**

Run:

```bash
./gradlew build --dry-run | grep -iE "spotlessCheck|jacocoTestCoverageVerification|:test"
```

Expected: lines for `:spotlessCheck`, `:test` (which runs the ArchUnit suite and finalizes into `:jacocoTestReport`), and `:jacocoTestCoverageVerification` all appear in the `build` task graph.

- [ ] **Step 3: Introduce a deliberate architecture violation**

Temporarily break Rule 2 (`RestClient` locality) by making an application service depend on `RestClient`. Edit `src/main/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerService.java`:

- Add this import alongside the existing imports:

```java
import org.springframework.web.client.RestClient;
```

- Add this field to the class body (directly below `private final SummonerPort summonerPort;`):

```java
    @SuppressWarnings("unused")
    private RestClient illegalDependency;
```

(The code still compiles — `RestClient` is on the classpath — so this isolates the failure to the ArchUnit gate rather than the compiler.)

- [ ] **Step 4: Observe the ArchUnit gate fail**

Run:

```bash
./gradlew test --tests "com.wkaiser.riotapimcpserver.architecture.HexagonalArchitectureTest"
```

Expected: `BUILD FAILED`. The report names the violated rule and the offending field, for example:

```
Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside outside of
packages ['..adapter.out.riot..', '..shared.http..'] should depend on classes that have
fully qualified name 'org.springframework.web.client.RestClient'' was violated (1 times):
Field <...summoner.application.SummonerService.illegalDependency> has type
<org.springframework.web.client.RestClient> ...
```

This confirms the gate has teeth: a dependency-rule breach fails the build.

- [ ] **Step 5: Revert the violation**

```bash
git checkout -- src/main/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerService.java
```

Confirm the file is clean:

```bash
git status --porcelain src/main/java/com/wkaiser/riotapimcpserver/summoner/application/SummonerService.java
```

Expected: no output (the working tree matches HEAD again).

- [ ] **Step 6: Final offline, key-less green build**

Run (with no `RIOT_API_KEY` in the environment):

```bash
unset RIOT_API_KEY
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`. The single `build` invocation runs, in one pass: unit tests + WireMock adapter tests (Phase 2) + the ArchUnit suite + `jacocoTestReport` + `jacocoTestCoverageVerification` + `spotlessCheck` — all green, with no network access to the Riot API and no key present.

- [ ] **Step 7: Commit (documentation-only or no-op)**

No source changed in this task (the violation was reverted). If Steps 1–6 produced no tracked changes, there is nothing to commit; otherwise:

```bash
git status --porcelain
```

Expected: empty. If empty, skip the commit — the gates were fully committed in Tasks 1–3.

---

## Self-Review

**Spec coverage (Phase 3 slice — Decision 3):**

| Decision 3 requirement | Where |
|---|---|
| Spotless (`com.diffplug.spotless`, palantir-java-format), `spotlessCheck` in `check`/`build`, `spotlessApply` available | Task 1 (Steps 1–7) |
| JaCoCo report on `test` | Task 2 (Steps 2–3, report bound via `finalizedBy`) |
| JaCoCo threshold configured but conservative/soft, not blocking | Task 2 (Step 2, `minimum = 0.30` line ratio, DTOs excluded) |
| ArchUnit `archunit-junit5` (`testImplementation`), `architecture/` suite | Task 3 (Steps 1–2) |
| Rule: layered domain → application → adapter, inward only | Rule 1 `layers_respect_inward_dependency_rule` |
| Rule: `RestClient` only within `..adapter.out.riot..` | Rule 2 (refined to also allow `..shared.http..` — see below) |
| Rule: `@McpTool` only within `..adapter.in.mcp..` | Rule 3 `mcp_tools_only_in_inbound_adapters` (method-level) |
| Rule: ports are interfaces in `..application.port..` | Rule 4 `ports_are_interfaces` + naming Rule `ports_are_named_port_and_are_interfaces` |
| Rule: no cross-context internals except analytics | Rule 5 (five sub-rules; refined to also allow `spectator → summoner.application` — see below) |
| Naming: `*Service`/`*Tool`/`*Adapter`/`*Port` in their packages | Rule 6 (`services_live_in_application`, `tools_live_in_inbound_adapters`, `adapters_live_in_outbound_riot`, `ports_are_named_port_and_are_interfaces`) |
| `build.gradle` gains `jacoco` + `spotless` plugins and the two test deps | Task 1 Step 1, Task 2 Step 1, Task 3 Step 1 (ArchUnit); WireMock reused from Phase 1 |
| All gates run under `./gradlew build`, offline, no key | Task 4 Step 6 |
| ArchUnit fails the build on a violation | Task 4 Steps 3–5 (teeth demonstration) |

**Placeholder scan:** none. Every `build.gradle` edit shows exact before/after Groovy; the full `HexagonalArchitectureTest.java` is given verbatim; every verification step shows an exact command and expected output. No "TBD", "similar to", or "write tests for the above".

**Type/name consistency with Phase 1:**
- Package roots (`account`/`summoner`/`match`/`spectator`/`analytics`/`shared`), sub-package shapes (`domain`, `application`, `application.port`, `adapter.in.mcp`, `adapter.out.riot`), and type suffixes (`*Service`, `*Tool`, `*Adapter`, `*Port`) exactly match Phase 1 Tasks 3–9.
- `@McpTool` FQN (`org.springframework.ai.mcp.annotation.McpTool`) verified against the current `LiveGameTool`/`RiotAccountTool` imports; it is method-scoped, so Rule 3 uses `methods()`.
- `RiotApiClient` resides in `shared.http` and is the sole constructor of `RestClient` (Phase 1 Task 2); Rule 2 therefore whitelists `..shared.http..` in addition to `..adapter.out.riot..`. The main class excluded from coverage, `RiotApiMcpServerApplication`, was confirmed to exist.
- The WireMock coordinate reused (`org.wiremock:wiremock-standalone:3.9.2`) matches Phase 1 Task 2 exactly.

**Deviations from the spec's prose (faithful to the built code, flagged intentionally):**
1. **Rule 2 whitelists `..shared.http..`.** Decision 1 makes `shared/http/RiotApiClient` the one place that builds `RestClient`; a literal "only `..adapter.out.riot..`" rule would fail on that class. Intent ("no application/domain/tool touches `RestClient`") is preserved.
2. **Rule 5 permits `spectator → summoner.application`.** Phase 1's `LiveGameTool` composes `SummonerService` to resolve a name → encrypted ID. The spec's single "except analytics" carve-out is realized as: `account`/`match` are pure providers; `summoner` is a pure provider; `spectator` additionally composes `summoner`; `analytics` composes `account`/`summoner`/`match`. Reaching into any context's **adapters** remains globally forbidden by Rule 1.

**Compatibility choices (stated per task instruction):** Spotless plugin `7.0.2` + palantir-java-format `2.50.0` (Java 21-compatible; Spotless supplies the JDK 16+ `--add-exports` internals via a worker); JaCoCo `0.8.12` (full JDK 21 bytecode support, Gradle 9.6.1); `archunit-junit5:1.3.0` (JUnit 5 Platform, Java 21 bytecode). ArchUnit `layeredArchitecture().consideringOnlyDependenciesInLayers()` and the `@AnalyzeClasses`/`@ArchTest` JUnit 5 style were confirmed against current ArchUnit docs via Context7.
