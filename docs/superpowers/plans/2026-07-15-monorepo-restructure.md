# Monorepo Restructure (Sub-project 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the single-module `riot-api-mcp-server` Spring Boot app into a Gradle monorepo — `riot-api-core` (library), `riot-account-core` (library), `lol-mcp-server` (Boot app) — as a pure structural refactor with no change to the MCP tool surface.

**Architecture:** Servers depend on `riot-account-core`, which depends on `riot-api-core`; nothing points back, so Gradle enforces the dependency rule at compile time. Both libraries are auto-configured (`@AutoConfiguration` + an `AutoConfiguration.imports` file), never component-scanned. Shared build config lives in a `buildSrc` convention plugin; shared ArchUnit rules and JSON fixtures live in `riot-api-core`'s test fixtures.

**Tech Stack:** Java 21, Gradle 9.6.1 (wrapper), Spring Boot 4.1.0, Spring AI 2.0.0, Lombok, JUnit 5, WireMock 3.9.2, ArchUnit 1.3.0, JaCoCo 0.8.12, Spotless 7.0.2 (palantirJavaFormat).

**Spec:** `docs/superpowers/specs/2026-07-15-monorepo-restructure-design.md`

## Global Constraints

- **`./gradlew build` must be green at every commit.** Tasks 1–4 are pure motion: any red means the move is wrong, not that a test needs updating.
- **No tool renames, no new endpoints, no behavior change** except Tasks 5–6 (dead-code deletion, transport profiles). The same **10 tools** with identical names and `@McpToolParam` descriptions must exist at the end. Task 1 installs the automated guard that proves this.
- **Libraries must not rely on `@ComponentScan`.** Library classes drop `@Component`/`@Service`; beans are declared explicitly in `@AutoConfiguration` classes with `@ConditionalOnMissingBean`.
- **`riot-account-core` contains no `@McpTool`.** ArchUnit asserts this (Task 7).
- **stdio profile must keep stdout clean.** `banner-mode: off` + `logging.pattern.console: ""`. Every tool class is `@Slf4j` and logs on each call; one stray stdout line corrupts the JSON-RPC stream.
- **Do not push a `v*` tag between Tasks 1 and 8.** The Dockerfile is knowingly broken in that window (it copies a root `./src` that no longer exists). `release.yml` only fires on tags, and `ci.yml` never builds the image, so CI stays green regardless.
- **Package roots after Task 4:** `com.wkaiser.riot.core`, `com.wkaiser.riot.account`, `com.wkaiser.riot.lol`.
- **Version stays `0.0.2-SNAPSHOT`**, group stays `com.wkaiser`.

## Deviations from the spec (deliberate, with reasons)

Recorded here so a reviewer does not flag them as plan errors:

1. **Spec says the ArchUnit slice exceptions are `spectator→summoner` and `analytics→account/summoner/match`.** Post-split, `RiotAccountService` lives in `com.wkaiser.riot.account` — outside the `com.wkaiser.riot.lol.(*)..` slice matcher — so `analytics→account` is a cross-module dependency the slice rule never sees. Correct exception list: **`spectator→summoner`, `analytics→summoner`, `analytics→match`**.
2. **Spec says "the WireMock harness" moves to test fixtures. No such harness exists** — every adapter test hand-rolls its own `setUp()`. Extracting one would be new code in a pure-refactor cycle. Only `Fixtures` (which exists) moves. Harness extraction is out of scope.
3. **Spec implies `SpectatorTestFixtures` moves to core test fixtures.** It is spectator-specific and stays in `lol-mcp-server`.
4. **`ci.yml:47`'s JUnit `report_paths` is already a glob** and needs no change. Only the JaCoCo path (`ci.yml:61`) breaks.

### Pre-flight amendments (2026-07-15, before execution)

Two defects found scanning this plan against the review rubric, both resolved by the human before Task 1 was dispatched:

5. **A vacuous test was removed from Task 1.** `McpToolInventoryTest` originally carried a second test, `every_tool_method_has_a_non_blank_name`, which mapped `Method::getName` — the *Java method* name, not the `@McpTool` name — and asserted it non-blank. It tested the wrong thing and could never fail. `tool_inventory_is_unchanged` already asserts the exact 10 tool names, which is the real guard. Deleted.
6. **Test-fixtures setup moved from Task 7 to Task 2, eliminating a mandated duplicate.** Task 3 originally `cp`-ed `Fixtures.java` into `riot-account-core` as a temporary copy for Task 7 to clean up. `Fixtures` belongs to core and Task 2 is the core-extraction task, so the `java-test-fixtures` plugin and the `Fixtures` move now happen there. Task 3 consumes it via `testFixtures(project(':riot-api-core'))`; no duplicate ever exists. Task 7 now does only what it is actually about — `HexagonRules` and the slices rewrite.

## File Structure

**Created:**
- `settings.gradle` (rewritten) — module includes
- `buildSrc/build.gradle`, `buildSrc/src/main/groovy/riot-java-conventions.gradle` — shared build config
- `riot-api-core/build.gradle` — library module
- `riot-api-core/src/main/java/com/wkaiser/riot/core/config/RiotApiAutoConfiguration.java`
- `riot-api-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `riot-api-core/src/test/java/com/wkaiser/riot/core/config/RiotApiAutoConfigurationTest.java` — new slice test
- `riot-api-core/src/testFixtures/java/com/wkaiser/riot/core/testsupport/Fixtures.java` — moved
- `riot-api-core/src/testFixtures/java/com/wkaiser/riot/core/testsupport/HexagonRules.java` — new, shared ArchUnit rules
- `riot-account-core/build.gradle`
- `riot-account-core/src/main/java/com/wkaiser/riot/account/config/RiotAccountAutoConfiguration.java`
- `riot-account-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `riot-account-core/src/test/java/com/wkaiser/riot/account/config/RiotAccountAutoConfigurationTest.java` — new slice test
- `riot-account-core/src/test/java/com/wkaiser/riot/account/architecture/AccountArchitectureTest.java` — new, asserts no `@McpTool`
- `lol-mcp-server/build.gradle`
- `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/McpToolInventoryTest.java` — new, the refactor-purity guard
- `lol-mcp-server/src/main/resources/application-stdio.yml`, `application-sse.yml`
- `docs/knowledge/decisions/ADR-0006-monorepo-split.md`

**Moved (whole-file, no logic change):** everything under `src/` → `lol-mcp-server/src/`, then `shared/*` → `riot-api-core`, `account/{domain,application,adapter/out}` → `riot-account-core`.

**Deleted:** root `build.gradle` app config, `GlobalExceptionHandler.java`, `RiotApiConfiguration.java` (superseded by `RiotApiAutoConfiguration`).

---

### Task 1: Install the refactor-purity guard, then create the monorepo skeleton

Spec migration steps 1–2. The guard goes in **first**, on the current single-module layout, so every later task moves code under its protection.

**Files:**
- Create: `src/test/java/com/wkaiser/riotapimcpserver/McpToolInventoryTest.java`
- Create: `settings.gradle` (rewrite), `buildSrc/build.gradle`, `buildSrc/src/main/groovy/riot-java-conventions.gradle`, `lol-mcp-server/build.gradle`
- Modify: `build.gradle` (root — strip to plugin declarations)
- Move: `src/` → `lol-mcp-server/src/`

**Interfaces:**
- Consumes: nothing.
- Produces: `riot-java-conventions` Gradle plugin id, applied by all later modules. `McpToolInventoryTest.EXPECTED_TOOL_NAMES` — the 10-name golden set later tasks must keep passing.

- [ ] **Step 1: Write the tool-inventory guard test**

Reflects over the four tool classes and asserts the exact `@McpTool` name set. Enumerating the classes explicitly (rather than classpath scanning) is deliberate: `@McpTool` is a method annotation, so no type filter finds it, and an explicit list fails loudly if a tool class is dropped during a move.

```java
package com.wkaiser.riotapimcpserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riotapimcpserver.account.adapter.in.mcp.RiotAccountTool;
import com.wkaiser.riotapimcpserver.analytics.adapter.in.mcp.AnalyticsTool;
import com.wkaiser.riotapimcpserver.spectator.adapter.in.mcp.LiveGameTool;
import com.wkaiser.riotapimcpserver.summoner.adapter.in.mcp.SummonerTool;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/**
 * Guards the public MCP contract across the monorepo restructure. The tool surface is
 * explicitly frozen for this cycle: the same 10 tools, same names. If this test fails
 * during a move, the move changed behavior and is wrong.
 */
class McpToolInventoryTest {

    static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
            "get_riot_account_by_riot_id",
            "get_riot_account_by_puuid",
            "get_lol_summoner_by_name",
            "get_lol_summoner_by_puuid",
            "get_lol_summoner_by_id",
            "get_current_game_by_summoner_name",
            "get_current_game_by_summoner_id",
            "get_featured_games",
            "check_if_summoner_in_game",
            "get_lol_player_match_analytics");

    @Test
    void tool_inventory_is_unchanged() {
        Set<String> actual = Stream.of(RiotAccountTool.class, AnalyticsTool.class, LiveGameTool.class, SummonerTool.class)
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
    }
}
```

- [ ] **Step 2: Run it — it must PASS immediately**

```bash
./gradlew test --tests '*McpToolInventoryTest'
```
Expected: **PASS**. This is a characterization test of existing behavior, not TDD — it should be green on the first run. If it fails, the golden set is wrong; fix the set to match reality before continuing, and note the discrepancy.

- [ ] **Step 3: Commit the guard**

```bash
git add src/test/java/com/wkaiser/riotapimcpserver/McpToolInventoryTest.java
git commit -m "test: freeze MCP tool inventory before restructure"
```

- [ ] **Step 4: Create the buildSrc convention plugin**

`buildSrc/build.gradle`:
```groovy
plugins {
	id 'groovy-gradle-plugin'
}

repositories {
	gradlePluginPortal()
	mavenCentral()
}

dependencies {
	implementation 'com.diffplug.spotless:spotless-plugin-gradle:7.0.2'
	implementation 'io.spring.gradle:dependency-management-plugin:1.1.7'
	implementation 'org.springframework.boot:spring-boot-gradle-plugin:4.1.0'
}
```

`buildSrc/src/main/groovy/riot-java-conventions.gradle`:
```groovy
plugins {
	id 'java-library'
	id 'jacoco'
	id 'io.spring.dependency-management'
	id 'com.diffplug.spotless'
}

group = 'com.wkaiser'
version = '0.0.2-SNAPSHOT'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

configurations {
	compileOnly {
		extendsFrom annotationProcessor
	}
}

dependencyManagement {
	imports {
		mavenBom "org.springframework.boot:spring-boot-dependencies:4.1.0"
		mavenBom "org.springframework.ai:spring-ai-bom:2.0.0"
	}
}

dependencies {
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testCompileOnly 'org.projectlombok:lombok'
	testAnnotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

spotless {
	java {
		target 'src/*/java/**/*.java'
		palantirJavaFormat('2.50.0')
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}

jacoco {
	toolVersion = '0.8.12'
}

// Generated accessors and bootstrap classes are excluded so they do not distort the ratio.
def coverageExclusions = [
	'**/domain/**',
	'**/*Application.class'
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

tasks.named('test') {
	useJUnitPlatform()
	finalizedBy tasks.named('jacocoTestReport')
}
```

Note `spotless.target` widened from `src/main/java` + `src/test/java` to `src/*/java/**/*.java` so `src/testFixtures/java` (added in Task 7) is covered.

- [ ] **Step 5: Rewrite settings.gradle and the root build.gradle**

`settings.gradle`:
```groovy
rootProject.name = 'riot-api-mcp-server'

include 'riot-api-core'
include 'riot-account-core'
include 'lol-mcp-server'
```

`build.gradle` (root) — holds no code and no plugins; every module applies the convention plugin itself:
```groovy
// Root project holds no code. Shared build logic lives in buildSrc/riot-java-conventions.gradle;
// each module applies it. Module list is in settings.gradle.
```

- [ ] **Step 6: Move all sources into lol-mcp-server**

```bash
mkdir -p lol-mcp-server
git mv src lol-mcp-server/src
```

`lol-mcp-server/build.gradle` — for now this module carries every dependency the old root had, minus nothing. Extraction happens in Tasks 2–3, deletion in Task 5:
```groovy
plugins {
	id 'riot-java-conventions'
	id 'org.springframework.boot'
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'io.github.wimdeblauwe:htmx-spring-boot:5.1.0'
	implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'
	implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'

	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
	testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
}
```

- [ ] **Step 7: Verify the whole build is green**

```bash
./gradlew build
```
Expected: **BUILD SUCCESSFUL**. All existing tests — including `McpToolInventoryTest`, `ApplicationContextLoadsTest`, `HexagonalArchitectureTest` — pass unchanged from their new location. `riot-api-core` and `riot-account-core` are declared in `settings.gradle` but have no `build.gradle` yet; Gradle tolerates empty modules, and Tasks 2–3 fill them.

If `HexagonalArchitectureTest` fails here, the move dropped a class — investigate the move, do not edit the rule.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: introduce Gradle monorepo skeleton, move sources to lol-mcp-server"
```

---

### Task 2: Extract riot-api-core

Spec migration step 3. Moves `shared/*` into a library and converts it from component-scanned to auto-configured.

**Files:**
- Create: `riot-api-core/build.gradle`
- Move: `lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/shared/{http,config,enums,exception}/**` → `riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared/**` (package names unchanged until Task 4)
- Move: `RiotApiClientTest.java`, `RiotApiPropertiesTest.java` → `riot-api-core/src/test/...`
- Move: `testsupport/Fixtures.java` → `riot-api-core/src/testFixtures/java/...` (test fixtures set up here, not in Task 7 — `Fixtures` belongs to core, and Task 3's account tests need it)
- Create: `riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiAutoConfiguration.java`
- Create: `riot-api-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `riot-api-core/src/test/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiAutoConfigurationTest.java`
- Delete: `RiotApiConfiguration.java`
- Modify: `RiotApiClient.java` (drop `@Component`)
- Modify: `lol-mcp-server/build.gradle` (add core dependency)

**Interfaces:**
- Consumes: `riot-java-conventions` (Task 1).
- Produces: `RiotApiAutoConfiguration` registering a `RiotApiClient` bean; `RiotApiProperties` bound to `riot.*` with `getApiKey()`, `getRegion()`, `getBaseUrlOverride()`. `GlobalExceptionHandler` stays in `lol-mcp-server` for now — Task 5 deletes it.

- [ ] **Step 1: Create the core module build file**

`riot-api-core/build.gradle`. Test fixtures are enabled here rather than in Task 7: `Fixtures` is core's, and Task 3's account adapter test consumes it — setting it up later would force a temporary duplicate.
```groovy
plugins {
	id 'riot-java-conventions'
	id 'java-test-fixtures'
}

dependencies {
	// api: servers reference RestClient types in adapter signatures via RiotApiClient.
	api 'org.springframework:spring-web'
	api 'org.springframework.boot:spring-boot-starter'
	annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
	// No testFixtures(project(':riot-api-core')) here: the java-test-fixtures plugin already
	// puts this project's own testFixtures source set on its test classpath.
}
```

- [ ] **Step 2: Move the shared package and its tests**

Create only the PARENT of each destination. Do not `mkdir` a destination path itself: `git mv SRC DST`
renames when `DST` does not exist, but moves *into* `DST` when it does — pre-creating
`.../riotapimcpserver/shared` silently produces `shared/shared/{config,http}`.
```bash
mkdir -p riot-api-core/src/main/java/com/wkaiser/riotapimcpserver
mkdir -p riot-api-core/src/test/java/com/wkaiser/riotapimcpserver
git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/shared \
       riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared
git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/shared \
       riot-api-core/src/test/java/com/wkaiser/riotapimcpserver/shared
```

Verify the layout before moving on — this is where the nesting bug shows up:
```bash
find riot-api-core/src -type d -name shared
```
Expected: exactly `riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared` and
`riot-api-core/src/test/java/com/wkaiser/riotapimcpserver/shared`. Any `shared/shared` path means a
destination was pre-created; fix with `git mv` before continuing.

`GlobalExceptionHandler` moved with `shared/exception/`. Move it back — it is web-tier code and Task 5 deletes it:
```bash
mkdir -p lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/shared/exception
git mv riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared/exception/GlobalExceptionHandler.java \
       lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/shared/exception/GlobalExceptionHandler.java
```

Move `Fixtures` into core's **test fixtures** so both `riot-account-core` (Task 3) and `lol-mcp-server` can consume one copy. `SpectatorTestFixtures` is spectator-specific and stays in `lol-mcp-server` — do not move it (deviation 3 in this plan's header). `FixturesTest` also stays in `lol-mcp-server`: it needs a fixture JSON on the classpath, and that module keeps five of them.
```bash
mkdir -p riot-api-core/src/testFixtures/java/com/wkaiser/riotapimcpserver/testsupport
git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/testsupport/Fixtures.java \
       riot-api-core/src/testFixtures/java/com/wkaiser/riotapimcpserver/testsupport/Fixtures.java
```

`lol-mcp-server` must now consume them. Add to `lol-mcp-server/build.gradle` dependencies:
```groovy
	testImplementation testFixtures(project(':riot-api-core'))
```

- [ ] **Step 3: Drop the stereotype from RiotApiClient and delete RiotApiConfiguration**

In `riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared/http/RiotApiClient.java`, remove the `@Component` annotation and its `import org.springframework.stereotype.Component;`. Keep `@RequiredArgsConstructor` — the auto-configuration calls the generated constructor. The class declaration becomes:

```java
@RequiredArgsConstructor
public class RiotApiClient {
```

```bash
git rm riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiConfiguration.java
```

- [ ] **Step 4: Write the auto-configuration**

`riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiAutoConfiguration.java`:
```java
package com.wkaiser.riotapimcpserver.shared.config;

import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the shared Riot HTTP layer. This library is consumed as a
 * dependency, never component-scanned, so every bean is declared explicitly here rather
 * than discovered via stereotypes. {@link ConditionalOnMissingBean} lets a consuming
 * application override any of them.
 */
@AutoConfiguration
@EnableConfigurationProperties(RiotApiProperties.class)
public class RiotApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RiotApiClient riotApiClient(RiotApiProperties properties) {
        return new RiotApiClient(properties);
    }
}
```

- [ ] **Step 5: Register the auto-configuration**

`riot-api-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — one FQN, no trailing content:
```
com.wkaiser.riotapimcpserver.shared.config.RiotApiAutoConfiguration
```

- [ ] **Step 6: Write the failing slice test**

This is the test the spec added specifically because a wrong imports file fails silently in a library.

`riot-api-core/src/test/java/com/wkaiser/riotapimcpserver/shared/config/RiotApiAutoConfigurationTest.java`:
```java
package com.wkaiser.riotapimcpserver.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the library's auto-configuration actually registers what it claims. Without
 * this, a wrong AutoConfiguration.imports file fails silently here and only surfaces
 * downstream as a context-load error in whichever server needs the missing bean.
 */
class RiotApiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RiotApiAutoConfiguration.class))
            .withPropertyValues("riot.api-key=test-key-123");

    @Test
    void registers_riotApiClient_bean() {
        runner.run(context -> assertThat(context).hasSingleBean(RiotApiClient.class));
    }

    @Test
    void binds_riot_properties_from_configuration() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RiotApiProperties.class);
            assertThat(context.getBean(RiotApiProperties.class).getApiKey()).isEqualTo("test-key-123");
        });
    }

    @Test
    void region_defaults_to_americas_when_unset() {
        runner.run(context ->
                assertThat(context.getBean(RiotApiProperties.class).getRegion()).isEqualTo(RiotApiRegionUri.AMERICAS));
    }

    @Test
    void auto_configuration_is_listed_in_the_imports_file() throws Exception {
        // The tests above pass the class in explicitly, which masks the real failure mode:
        // a missing or misspelled entry in AutoConfiguration.imports compiles fine and only
        // breaks in a consuming application. Assert the file's contents directly.
        var url = getClass()
                .getClassLoader()
                .getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(url).as("AutoConfiguration.imports must be on the classpath").isNotNull();
        assertThat(new String(url.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .contains(RiotApiAutoConfiguration.class.getName());
    }
}
```

- [ ] **Step 7: Run the slice test**

```bash
./gradlew :riot-api-core:test --tests '*RiotApiAutoConfigurationTest'
```
Expected: **PASS**.

- [ ] **Step 8: Wire lol-mcp-server to core**

Add to `lol-mcp-server/build.gradle` dependencies, and drop the now-transitive `spring-boot-starter-web` only if still referenced (it is — keep it):
```groovy
	implementation project(':riot-api-core')
```

- [ ] **Step 9: Verify the full build**

```bash
./gradlew build
```
Expected: **BUILD SUCCESSFUL**. `ApplicationContextLoadsTest` is the critical one — it proves auto-configuration replaced component scanning without losing a bean. If it fails with `NoSuchBeanDefinitionException: RiotApiClient`, the imports file path or FQN is wrong.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: extract riot-api-core as an auto-configured library"
```

---

### Task 3: Extract riot-account-core

Spec migration step 4. Account's domain, application, and outbound adapter become a library; its inbound tool stays in the LoL server.

**Files:**
- Create: `riot-account-core/build.gradle`
- Move: `account/{domain,application}` and `account/adapter/out` → `riot-account-core/src/main/java/...`
- Move: `RiotAccountServiceTest`, `InMemoryRiotAccountPort`, `RiotAccountRiotAdapterTest` → `riot-account-core/src/test/...`
- Move: `src/test/resources/fixtures/account.json` → `riot-account-core/src/test/resources/fixtures/account.json`
- Create: `riot-account-core/src/main/java/com/wkaiser/riotapimcpserver/account/config/RiotAccountAutoConfiguration.java`
- Create: `riot-account-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `riot-account-core/src/test/java/com/wkaiser/riotapimcpserver/account/config/RiotAccountAutoConfigurationTest.java`
- Modify: `RiotAccountService.java` (drop `@Service`), `RiotAccountRiotAdapter.java` (drop `@Component`)
- Keep in place: `account/adapter/in/mcp/RiotAccountTool.java` (LoL server)

**Interfaces:**
- Consumes: `RiotApiClient`, `RiotApiProperties` from `riot-api-core` (Task 2).
- Produces: `RiotAccountService` bean with `getAccountByRiotId(String gameName, String tagLine) -> RiotAccount` and `getAccountByPuuid(String puuid) -> RiotAccount`. Consumed by `RiotAccountTool` and `AnalyticsService` in `lol-mcp-server`.

- [ ] **Step 1: Create the module build file**

`riot-account-core/build.gradle`:
```groovy
plugins {
	id 'riot-java-conventions'
}

dependencies {
	api project(':riot-api-core')

	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
	testImplementation testFixtures(project(':riot-api-core'))
}
```

`testFixtures(project(':riot-api-core'))` supplies the `Fixtures` class that `RiotAccountRiotAdapterTest` needs. Task 2 moved it there precisely so this module can consume it without a duplicate copy.

- [ ] **Step 2: Move account's non-inbound layers, tests, and fixture**

```bash
mkdir -p riot-account-core/src/main/java/com/wkaiser/riotapimcpserver/account/adapter/out
mkdir -p riot-account-core/src/test/java/com/wkaiser/riotapimcpserver/account/adapter/out
mkdir -p riot-account-core/src/test/resources/fixtures

git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/account/domain \
       riot-account-core/src/main/java/com/wkaiser/riotapimcpserver/account/domain
git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/account/application \
       riot-account-core/src/main/java/com/wkaiser/riotapimcpserver/account/application
git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot \
       riot-account-core/src/main/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot

git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/account/application \
       riot-account-core/src/test/java/com/wkaiser/riotapimcpserver/account/application
git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot \
       riot-account-core/src/test/java/com/wkaiser/riotapimcpserver/account/adapter/out/riot

git mv lol-mcp-server/src/test/resources/fixtures/account.json \
       riot-account-core/src/test/resources/fixtures/account.json
```

`RiotAccountRiotAdapterTest` imports `com.wkaiser.riotapimcpserver.testsupport.Fixtures`. No copy or edit is needed: Task 2 moved that class into `riot-api-core`'s test fixtures, and Step 1's `testFixtures(project(':riot-api-core'))` dependency puts it on this module's test classpath under the same package name.

`Fixtures.read()` resolves `/fixtures/<name>` from the runtime classpath, so `account.json` living in **this** module's `src/test/resources` is what makes the lookup succeed here.

- [ ] **Step 3: Drop stereotypes from the library classes**

In `RiotAccountService.java`, remove `@Service` and `import org.springframework.stereotype.Service;`:
```java
@Slf4j
@RequiredArgsConstructor
public class RiotAccountService {
```

In `RiotAccountRiotAdapter.java`, remove `@Component` and `import org.springframework.stereotype.Component;`:
```java
@RequiredArgsConstructor
public class RiotAccountRiotAdapter implements RiotAccountPort {
```

- [ ] **Step 4: Write the auto-configuration**

`riot-account-core/src/main/java/com/wkaiser/riotapimcpserver/account/config/RiotAccountAutoConfiguration.java`:
```java
package com.wkaiser.riotapimcpserver.account.config;

import com.wkaiser.riotapimcpserver.account.adapter.out.riot.RiotAccountRiotAdapter;
import com.wkaiser.riotapimcpserver.account.application.RiotAccountService;
import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiAutoConfiguration;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the shared Riot account context. Consumed as a library by every
 * game server; deliberately exposes no MCP tool (each server owns its own inbound adapter,
 * so tool names can be namespaced per game without collisions).
 */
@AutoConfiguration(after = RiotApiAutoConfiguration.class)
public class RiotAccountAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RiotAccountPort riotAccountPort(RiotApiClient riotApiClient, RiotApiProperties properties) {
        return new RiotAccountRiotAdapter(riotApiClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RiotAccountService riotAccountService(RiotAccountPort riotAccountPort) {
        return new RiotAccountService(riotAccountPort);
    }
}
```

- [ ] **Step 5: Register it**

`riot-account-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.wkaiser.riotapimcpserver.account.config.RiotAccountAutoConfiguration
```

- [ ] **Step 6: Write the slice test**

`riot-account-core/src/test/java/com/wkaiser/riotapimcpserver/account/config/RiotAccountAutoConfigurationTest.java`:
```java
package com.wkaiser.riotapimcpserver.account.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riotapimcpserver.account.adapter.out.riot.RiotAccountRiotAdapter;
import com.wkaiser.riotapimcpserver.account.application.RiotAccountService;
import com.wkaiser.riotapimcpserver.account.application.port.RiotAccountPort;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RiotAccountAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(RiotApiAutoConfiguration.class, RiotAccountAutoConfiguration.class))
            .withPropertyValues("riot.api-key=test-key-123");

    @Test
    void registers_account_service_and_port() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RiotAccountService.class);
            assertThat(context).hasSingleBean(RiotAccountPort.class);
            assertThat(context.getBean(RiotAccountPort.class)).isInstanceOf(RiotAccountRiotAdapter.class);
        });
    }

    @Test
    void a_consumer_supplied_port_wins_over_the_default_adapter() {
        runner.withBean(RiotAccountPort.class, () -> new StubAccountPort()).run(context -> {
            assertThat(context).hasSingleBean(RiotAccountPort.class);
            assertThat(context.getBean(RiotAccountPort.class)).isInstanceOf(StubAccountPort.class);
        });
    }

    /** Minimal stand-in proving @ConditionalOnMissingBean lets a consumer override the adapter. */
    private static final class StubAccountPort implements RiotAccountPort {
        @Override
        public com.wkaiser.riotapimcpserver.account.domain.RiotAccount getAccountByRiotId(
                String gameName, String tagLine) {
            return null;
        }

        @Override
        public com.wkaiser.riotapimcpserver.account.domain.RiotAccount getAccountByPuuid(String puuid) {
            return null;
        }
    }
}
```

- [ ] **Step 7: Wire lol-mcp-server to account-core**

Add to `lol-mcp-server/build.gradle` dependencies:
```groovy
	implementation project(':riot-account-core')
```

- [ ] **Step 8: Verify the full build**

```bash
./gradlew build
```
Expected: **BUILD SUCCESSFUL**. `RiotAccountTool` and `AnalyticsService` now consume `RiotAccountService` across a module boundary. `ApplicationContextLoadsTest` proves the bean still resolves; `McpToolInventoryTest` proves the account tools survived.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: extract riot-account-core as a tool-free library"
```

---

### Task 4: Rename packages to per-module roots

Spec migration step 5. Purely mechanical; the last of the "pure motion" tasks.

**Files:** every `.java` file in all three modules, plus both `AutoConfiguration.imports` files and `HexagonalArchitectureTest`.

**Interfaces:**
- Consumes: everything from Tasks 2–3.
- Produces: final package roots `com.wkaiser.riot.core`, `com.wkaiser.riot.account`, `com.wkaiser.riot.lol`. All later tasks use these.

The mapping:

| From | To | Module |
|---|---|---|
| `com.wkaiser.riotapimcpserver.shared` | `com.wkaiser.riot.core` | riot-api-core (main + test) |
| `com.wkaiser.riotapimcpserver.testsupport` (`Fixtures`) | `com.wkaiser.riot.core.testsupport` | riot-api-core (**testFixtures**) |
| `com.wkaiser.riotapimcpserver.testsupport` (`FixturesTest`) | `com.wkaiser.riot.core.testsupport` | lol-mcp-server (test) |
| `com.wkaiser.riotapimcpserver.account` | `com.wkaiser.riot.account` | riot-account-core |
| `com.wkaiser.riotapimcpserver.account.adapter.in.mcp` | `com.wkaiser.riot.lol.account.adapter.in.mcp` | lol-mcp-server |
| `com.wkaiser.riotapimcpserver.{summoner,match,spectator,analytics}` | `com.wkaiser.riot.lol.{...}` | lol-mcp-server |
| `com.wkaiser.riotapimcpserver.RiotApiMcpServerApplication` | `com.wkaiser.riot.lol.LolMcpServerApplication` | lol-mcp-server |
| `com.wkaiser.riotapimcpserver.shared.exception.GlobalExceptionHandler` | `com.wkaiser.riot.lol.shared.exception.GlobalExceptionHandler` | lol-mcp-server (deleted in Task 5) |

- [ ] **Step 1: Move the directories**

Do this with an IDE refactor if available — it updates imports atomically. Otherwise, move directories then fix imports by search-and-replace. The `shared` → `core` rename is the only one that is not a pure prefix swap.

```bash
# riot-api-core: shared -> core (main, test, AND testFixtures — Task 2 put Fixtures in testFixtures)
mkdir -p riot-api-core/src/main/java/com/wkaiser/riot
git mv riot-api-core/src/main/java/com/wkaiser/riotapimcpserver/shared \
       riot-api-core/src/main/java/com/wkaiser/riot/core
mkdir -p riot-api-core/src/test/java/com/wkaiser/riot
git mv riot-api-core/src/test/java/com/wkaiser/riotapimcpserver/shared \
       riot-api-core/src/test/java/com/wkaiser/riot/core
mkdir -p riot-api-core/src/testFixtures/java/com/wkaiser/riot/core
git mv riot-api-core/src/testFixtures/java/com/wkaiser/riotapimcpserver/testsupport \
       riot-api-core/src/testFixtures/java/com/wkaiser/riot/core/testsupport

# riot-account-core: account -> riot.account
# NOTE: account-core has NO testsupport/ directory — the pre-flight amendment (deviation 6) moved
# Fixtures into core's testFixtures instead of duplicating it here. Do not try to move one.
mkdir -p riot-account-core/src/main/java/com/wkaiser/riot
git mv riot-account-core/src/main/java/com/wkaiser/riotapimcpserver/account \
       riot-account-core/src/main/java/com/wkaiser/riot/account
mkdir -p riot-account-core/src/test/java/com/wkaiser/riot
git mv riot-account-core/src/test/java/com/wkaiser/riotapimcpserver/account \
       riot-account-core/src/test/java/com/wkaiser/riot/account

# lol-mcp-server: contexts -> riot.lol.*
mkdir -p lol-mcp-server/src/main/java/com/wkaiser/riot/lol
for ctx in summoner match spectator analytics; do
  git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/$ctx \
         lol-mcp-server/src/main/java/com/wkaiser/riot/lol/$ctx
done
mkdir -p lol-mcp-server/src/main/java/com/wkaiser/riot/lol/account/adapter/in
git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/account/adapter/in/mcp \
       lol-mcp-server/src/main/java/com/wkaiser/riot/lol/account/adapter/in/mcp
mkdir -p lol-mcp-server/src/main/java/com/wkaiser/riot/lol/shared
git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/shared/exception \
       lol-mcp-server/src/main/java/com/wkaiser/riot/lol/shared/exception
git mv lol-mcp-server/src/main/java/com/wkaiser/riotapimcpserver/RiotApiMcpServerApplication.java \
       lol-mcp-server/src/main/java/com/wkaiser/riot/lol/LolMcpServerApplication.java

mkdir -p lol-mcp-server/src/test/java/com/wkaiser/riot/lol
for ctx in summoner match spectator analytics architecture; do
  git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/$ctx \
         lol-mcp-server/src/test/java/com/wkaiser/riot/lol/$ctx
done
# FixturesTest stays in this module (it needs a fixture JSON on the classpath, and this module keeps
# five of them) but follows Fixtures into the core.testsupport package. Its parent `riot/core` does
# not exist here yet — mkdir it, or the git mv fails with "destination directory does not exist".
mkdir -p lol-mcp-server/src/test/java/com/wkaiser/riot/core
git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/testsupport \
       lol-mcp-server/src/test/java/com/wkaiser/riot/core/testsupport
git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/ApplicationContextLoadsTest.java \
       lol-mcp-server/src/test/java/com/wkaiser/riot/lol/ApplicationContextLoadsTest.java
git mv lol-mcp-server/src/test/java/com/wkaiser/riotapimcpserver/McpToolInventoryTest.java \
       lol-mcp-server/src/test/java/com/wkaiser/riot/lol/McpToolInventoryTest.java

find . -type d -name riotapimcpserver -empty -delete
```

- [ ] **Step 2: Rewrite package and import statements**

Order matters — the most specific patterns first, so a general prefix swap does not clobber them.

```bash
FILES=$(git ls-files '*.java')
# Specific first
sed -i 's/com\.wkaiser\.riotapimcpserver\.account\.adapter\.in\.mcp/com.wkaiser.riot.lol.account.adapter.in.mcp/g' $FILES
sed -i 's/com\.wkaiser\.riotapimcpserver\.shared\.exception\.GlobalExceptionHandler/com.wkaiser.riot.lol.shared.exception.GlobalExceptionHandler/g' $FILES
sed -i 's/com\.wkaiser\.riotapimcpserver\.testsupport/com.wkaiser.riot.core.testsupport/g' $FILES
sed -i 's/com\.wkaiser\.riotapimcpserver\.shared/com.wkaiser.riot.core/g' $FILES
sed -i 's/com\.wkaiser\.riotapimcpserver\.account/com.wkaiser.riot.account/g' $FILES
for ctx in summoner match spectator analytics architecture; do
  sed -i "s/com\.wkaiser\.riotapimcpserver\.$ctx/com.wkaiser.riot.lol.$ctx/g" $FILES
done
# Application class + package-only declarations last
sed -i 's/^package com\.wkaiser\.riotapimcpserver;/package com.wkaiser.riot.lol;/' $FILES
sed -i 's/com\.wkaiser\.riotapimcpserver\.RiotApiMcpServerApplication/com.wkaiser.riot.lol.LolMcpServerApplication/g' $FILES
sed -i 's/RiotApiMcpServerApplication/LolMcpServerApplication/g' $FILES
```

- [ ] **Step 3: Update both AutoConfiguration.imports files**

`riot-api-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.wkaiser.riot.core.config.RiotApiAutoConfiguration
```
`riot-account-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.wkaiser.riot.account.config.RiotAccountAutoConfiguration
```

These are plain text and invisible to the compiler — a missed edit here compiles fine and fails only at context load. `ApplicationContextLoadsTest` and the two slice tests are what catch it.

- [ ] **Step 4: Update the ArchUnit root package**

In `lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/HexagonalArchitectureTest.java`, update the scan root and the `ROOT` constant. The `ACCOUNT` slice constant now points at the LoL server's tool-only account package:
```java
@AnalyzeClasses(packages = "com.wkaiser.riot.lol", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String ROOT = "com.wkaiser.riot.lol.";
```
Leave the rest of the rules as-is — Task 7 rewrites them. `account_depends_on_no_other_context` will now analyze only `RiotAccountTool`, which is correct and still passes.

- [ ] **Step 5: Format and build**

```bash
./gradlew spotlessApply && ./gradlew build
```
Expected: **BUILD SUCCESSFUL**. If `ApplicationContextLoadsTest` fails with a missing bean, an imports file was missed in Step 3.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: rename packages to per-module roots (riot.core/account/lol)"
```

---

### Task 5: Delete dead code and dead dependencies

Spec migration step 6. **First task that changes behavior** — isolated here so a failure is unambiguous.

**Files:**
- Delete: `lol-mcp-server/src/main/java/com/wkaiser/riot/lol/shared/exception/GlobalExceptionHandler.java`
- Modify: `lol-mcp-server/build.gradle`, `lol-mcp-server/src/main/resources/application.yml`, `ApplicationContextLoadsTest.java`, `Dockerfile`

- [ ] **Step 1: Prove GlobalExceptionHandler is unreachable before deleting it**

The spec asserts it is dead; verify rather than assume. It is `@RestControllerAdvice`, which only handles exceptions escaping a controller. Confirm there are none:
```bash
grep -rE "@(Rest)?Controller\b|@RequestMapping|RouterFunction" --include=*.java lol-mcp-server/src/main/java
```
Expected: **no output** (the `@RestControllerAdvice` on the handler itself will not match `@Controller\b`). If anything *does* match, stop — the handler is live and this deletion is wrong. Record the finding and skip Steps 2–3.

- [ ] **Step 2: Delete it**

```bash
git rm lol-mcp-server/src/main/java/com/wkaiser/riot/lol/shared/exception/GlobalExceptionHandler.java
find lol-mcp-server/src/main/java/com/wkaiser/riot/lol/shared -type d -empty -delete
```

- [ ] **Step 3: Remove the unused dependencies**

In `lol-mcp-server/build.gradle`, delete these two lines:
```groovy
	implementation 'io.github.wimdeblauwe:htmx-spring-boot:5.1.0'
	implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'
```

- [ ] **Step 4: Remove the ANTHROPIC_API_KEY requirement**

`lol-mcp-server/src/main/resources/application.yml` — the whole `spring.ai.anthropic` block goes. Result:
```yaml
spring:
  application:
    name: riot-api-mcp-server
  ai:
    mcp:
      server:
        name: riot-api-mcp-server
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages
        annotation-scanner:
          enabled: true

riot:
  apiKey: ${RIOT_API_KEY} # refreshes every day until you register your product
  region: americas
```

- [ ] **Step 5: Drop the Anthropic property from the context test**

`ApplicationContextLoadsTest` currently passes `spring.ai.anthropic.api-key=dummy-test-anthropic-api-key` and its Javadoc explains at length why. Both are now obsolete. Replace the whole file:

```java
package com.wkaiser.riot.lol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Always-on smoke test that boots the full Spring application context — no real secrets,
 * no network access. It runs on every {@code ./gradlew build} and catches dependency-injection
 * regressions (missing beans, broken {@code @ConfigurationProperties} bindings) that unit tests
 * alone would miss.
 * <p>
 * Since the monorepo split this test carries extra weight: {@code riot-api-core} and
 * {@code riot-account-core} contribute their beans via auto-configuration rather than component
 * scanning, so a broken {@code AutoConfiguration.imports} file in either library surfaces here
 * and nowhere else in this module.
 * <p>
 * {@code riot.api-key} is supplied as a dummy because {@code application.yml} binds it from the
 * {@code RIOT_API_KEY} environment variable, which is absent in CI; without a value Spring fails
 * placeholder resolution at startup. No outbound HTTP call happens during context startup.
 */
@SpringBootTest(properties = {"riot.api-key=dummy-test-riot-api-key"})
class ApplicationContextLoadsTest {

    @Test
    void contextLoads() {
        // Intentionally empty: the assertion is that the Spring application context
        // starts successfully with the full bean graph wired up.
    }
}
```

- [ ] **Step 6: Remove the Anthropic reference from the Dockerfile**

Replace `Dockerfile` lines 34–41 with:
```dockerfile
# Required runtime configuration (12-factor; no secrets are baked into the image):
#   RIOT_API_KEY  Riot Games API key -> bound to riot.apiKey
# Supply it at run time, e.g.:
#   docker run --rm -p 8080:8080 \
#     -e RIOT_API_KEY=RGAPI-xxxx \
#     ghcr.io/<owner>/lol-mcp-server:latest
```

- [ ] **Step 7: Build**

```bash
./gradlew build
```
Expected: **BUILD SUCCESSFUL**. `ApplicationContextLoadsTest` now proves the app boots with no Anthropic key at all — the concrete win of this task.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: drop dead GlobalExceptionHandler, htmx, and Anthropic starter

The app boots without ANTHROPIC_API_KEY: the Anthropic starter was never used
(no ChatClient/ChatModel anywhere) but application.yml required its key to start.
GlobalExceptionHandler was @RestControllerAdvice in an app with no controllers;
Spring AI converts @McpTool exceptions itself, so it could never fire."
```

---

### Task 6: Add stdio and sse transport profiles

Spec migration step 7. Second and last behavior change.

**Files:**
- Create: `lol-mcp-server/src/main/resources/application-stdio.yml`, `application-sse.yml`
- Modify: `lol-mcp-server/src/main/resources/application.yml`
- Modify: `docs/knowledge/gotchas.md`

- [ ] **Step 1: Set the default profile in the common config**

`lol-mcp-server/src/main/resources/application.yml` — add `spring.profiles.default`. Using `default` rather than `active` means an explicit `--spring.profiles.active=sse` overrides it cleanly:
```yaml
spring:
  application:
    name: riot-api-mcp-server
  profiles:
    default: stdio
  ai:
    mcp:
      server:
        name: riot-api-mcp-server
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages
        annotation-scanner:
          enabled: true

riot:
  apiKey: ${RIOT_API_KEY} # refreshes every day until you register your product
  region: americas
```

- [ ] **Step 2: Write the stdio profile**

`lol-mcp-server/src/main/resources/application-stdio.yml`:
```yaml
# STDIO transport: the JSON-RPC protocol stream IS stdout.
#
# Anything else written to stdout — the Spring banner, a log line from any @Slf4j class —
# interleaves with protocol frames and corrupts the stream. The client sees malformed JSON
# and drops the connection, with a failure mode that looks nothing like its cause.
# Every setting below exists to keep stdout clean; do not remove one without understanding this.
spring:
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        stdio: true

logging:
  pattern:
    console: ""  # empty pattern disables the console appender entirely
  file:
    name: ${RIOT_MCP_LOG_FILE:./riot-mcp-server.log}
```

- [ ] **Step 3: Write the sse profile**

`lol-mcp-server/src/main/resources/application-sse.yml`:
```yaml
# SSE transport: the pre-monorepo default. HTTP on 8080, messages at /mcp/messages.
# Logging to stdout is safe here — the protocol runs over HTTP, not the console.
spring:
  main:
    web-application-type: servlet
  ai:
    mcp:
      server:
        stdio: false

server:
  port: ${SERVER_PORT:8080}
```

- [ ] **Step 4: Pin the sse profile in the context test**

`ApplicationContextLoadsTest` must keep booting a servlet context (that is what it has always asserted). Add the profile to the annotation:
```java
@SpringBootTest(properties = {"riot.api-key=dummy-test-riot-api-key"})
@ActiveProfiles("sse")
class ApplicationContextLoadsTest {
```
Add `import org.springframework.test.context.ActiveProfiles;`.

- [ ] **Step 5: Build**

```bash
./gradlew build
```
Expected: **BUILD SUCCESSFUL**. Note that no unit test can catch stdout corruption — Task 10 verifies it for real.

- [ ] **Step 6: Record the gotcha**

Append to `docs/knowledge/gotchas.md`, following the file's convention (newest last, one small section):

```markdown
## STDIO transport: any stdout write corrupts the JSON-RPC stream

When the `stdio` profile is active, the MCP protocol stream **is** stdout. Spring Boot logs to
stdout by default and every class here is `@Slf4j` — `SummonerTool` and `LiveGameTool` log on
each tool call. One log line interleaves with protocol frames, the client sees malformed JSON,
and the connection dies with an error that points nowhere near the cause.

`application-stdio.yml` prevents this with three settings, all load-bearing:

- `spring.main.banner-mode: off` — the banner alone breaks the handshake
- `logging.pattern.console: ""` — an empty pattern disables the console appender
- `logging.file.name` — logs still need to go somewhere; default `./riot-mcp-server.log`

No unit test catches this. Verify by piping a JSON-RPC `initialize` + `tools/list` into the jar
and asserting every stdout line parses as JSON (see the sub-project 0 plan, Task 10).

The `sse` profile is unaffected — the protocol runs over HTTP, so console logging is safe.
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add stdio (default) and sse transport profiles"
```

---

### Task 7: Move ArchUnit rules to core test fixtures and replace the N×N matrix with slices

Spec migration step 8.

Test fixtures already exist (Task 2 enabled the plugin and moved `Fixtures`). This task adds the shared ArchUnit rules to them and rewrites the architecture tests.

**Files:**
- Modify: `riot-api-core/build.gradle` (add the ArchUnit dependency to test fixtures)
- Create: `riot-api-core/src/testFixtures/java/com/wkaiser/riot/core/testsupport/HexagonRules.java`
- Rewrite: `lol-mcp-server/.../architecture/HexagonalArchitectureTest.java`
- Create: `riot-account-core/src/test/java/com/wkaiser/riot/account/architecture/AccountArchitectureTest.java`
- Modify: `lol-mcp-server/build.gradle`

**Interfaces:**
- Consumes: final package roots from Task 4.
- Produces: `HexagonRules.LAYERS_RESPECT_INWARD_DEPENDENCY_RULE`, `.RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS`, `.MCP_TOOLS_ONLY_IN_INBOUND_ADAPTERS`, `.NO_MCP_TOOLS_AT_ALL`, `.PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES`, `.SERVICES_LIVE_IN_APPLICATION`, `.TOOLS_LIVE_IN_INBOUND_ADAPTERS`, `.ADAPTERS_LIVE_IN_OUTBOUND_RIOT` — all `public static final ArchRule`, consumed by every module's architecture test and by future game servers.

- [ ] **Step 1: Add ArchUnit to core's test fixtures**

The `java-test-fixtures` plugin is already applied (Task 2). Add the ArchUnit dependency as `testFixturesApi` so it reaches every consuming module's architecture test transitively:
```groovy
plugins {
	id 'riot-java-conventions'
	id 'java-test-fixtures'
}

dependencies {
	// api: servers reference RestClient types in adapter signatures via RiotApiClient.
	api 'org.springframework:spring-web'
	api 'org.springframework.boot:spring-boot-starter'
	annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

	// testFixturesApi (not testFixturesImplementation): HexagonRules exposes ArchRule in its
	// public signatures, so consumers need ArchUnit on their compile classpath.
	testFixturesApi 'com.tngtech.archunit:archunit-junit5:1.3.0'

	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
}
```

- [ ] **Step 2: Write the shared rule library**

`riot-api-core/src/testFixtures/java/com/wkaiser/riot/core/testsupport/HexagonRules.java`:
```java
package com.wkaiser.riot.core.testsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagon rules, shared by every module in the monorepo. These are not LoL-specific:
 * each module's architecture test declares them as {@code @ArchTest} fields and supplies its
 * own scan root via {@code @AnalyzeClasses}, so a new game server inherits the architecture
 * instead of copy-pasting it.
 * <p>
 * Cross-context rules are NOT here — they are per-module (a server's context graph is its own
 * business). See each module's architecture test for its slice rule.
 */
public final class HexagonRules {

    private static final String MCP_TOOL_ANNOTATION = "org.springframework.ai.mcp.annotation.McpTool";

    private HexagonRules() {}

    /** Inward-only layering: adapter -> application -> domain. */
    public static final ArchRule LAYERS_RESPECT_INWARD_DEPENDENCY_RULE = layeredArchitecture()
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

    /** RestClient only in outbound Riot adapters and the shared client factory. */
    public static final ArchRule RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS = noClasses()
            .that()
            .resideOutsideOfPackages("..adapter.out.riot..", "..core.http..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestClient");

    /** @McpTool methods only in inbound MCP adapters. */
    public static final ArchRule MCP_TOOLS_ONLY_IN_INBOUND_ADAPTERS = methods()
            .that()
            .areAnnotatedWith(MCP_TOOL_ANNOTATION)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..adapter.in.mcp..");

    /**
     * For library modules: no MCP tools at all. Each game server owns its own inbound adapters
     * so tool names can be namespaced per game without colliding when two servers are installed
     * into the same client.
     */
    public static final ArchRule NO_MCP_TOOLS_AT_ALL =
            noMethods().should().beAnnotatedWith(MCP_TOOL_ANNOTATION);

    /**
     * Everything in ..application.port.. is an interface. Checks the Port invariant from the
     * package side: it catches a concrete class dumped into the port package.
     * <p>
     * Deliberately kept alongside {@link #PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES}, which checks the
     * same invariant from the naming side. Neither subsumes the other — this one misses a {@code *Port}
     * declared outside the package, and that one misses a non-{@code *Port} class declared inside it.
     */
    public static final ArchRule PORTS_ARE_INTERFACES =
            classes().that().resideInAPackage("..application.port..").should().beInterfaces();

    /** Ports are interfaces named *Port residing in ..application.port.. Naming-side counterpart to
     * {@link #PORTS_ARE_INTERFACES}. */
    public static final ArchRule PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES = classes()
            .that()
            .haveSimpleNameEndingWith("Port")
            .should()
            .resideInAPackage("..application.port..")
            .andShould()
            .beInterfaces();

    public static final ArchRule SERVICES_LIVE_IN_APPLICATION =
            classes().that().haveSimpleNameEndingWith("Service").should().resideInAPackage("..application..");

    public static final ArchRule TOOLS_LIVE_IN_INBOUND_ADAPTERS =
            classes().that().haveSimpleNameEndingWith("Tool").should().resideInAPackage("..adapter.in.mcp..");

    public static final ArchRule ADAPTERS_LIVE_IN_OUTBOUND_RIOT =
            classes().that().haveSimpleNameEndingWith("Adapter").should().resideInAPackage("..adapter.out.riot..");
}
```

Note `RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS` uses `..core.http..` — the post-rename home of `RiotApiClient` (was `..shared.http..`).

- [ ] **Step 3: Rewrite the LoL architecture test**

Replaces `HexagonalArchitectureTest`'s eleven-rule hand-maintained matrix with one slice rule.

**The exception list is `spectator→summoner`, `analytics→summoner`, `analytics→match` — not what the spec says.** `analytics→account` was an intra-app dependency before the split; now `RiotAccountService` lives in `com.wkaiser.riot.account`, outside this slice matcher, so the rule never sees it.

`lol-mcp-server/src/test/java/com/wkaiser/riot/lol/architecture/HexagonalArchitectureTest.java`:
```java
package com.wkaiser.riot.lol.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.wkaiser.riot.core.testsupport.HexagonRules;

/**
 * Enforces the bounded-context hexagon for the LoL server. The layering, placement, and naming
 * rules come from {@link HexagonRules} in riot-api-core's test fixtures — they are shared with
 * every other module. Only the cross-context rule is local, because a server's context graph is
 * its own business.
 */
@AnalyzeClasses(packages = "com.wkaiser.riot.lol", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule layers_respect_inward_dependency_rule = HexagonRules.LAYERS_RESPECT_INWARD_DEPENDENCY_RULE;

    @ArchTest
    static final ArchRule restclient_confined_to_outbound_adapters =
            HexagonRules.RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule mcp_tools_only_in_inbound_adapters = HexagonRules.MCP_TOOLS_ONLY_IN_INBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule ports_are_interfaces = HexagonRules.PORTS_ARE_INTERFACES;

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces =
            HexagonRules.PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES;

    @ArchTest
    static final ArchRule services_live_in_application = HexagonRules.SERVICES_LIVE_IN_APPLICATION;

    @ArchTest
    static final ArchRule tools_live_in_inbound_adapters = HexagonRules.TOOLS_LIVE_IN_INBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot = HexagonRules.ADAPTERS_LIVE_IN_OUTBOUND_RIOT;

    /**
     * Contexts are independent except for two deliberate composition edges. This replaces the
     * hand-maintained N-by-N matrix that preceded it: one rule that stays correct as contexts are
     * added, rather than one rule per context each enumerating every other.
     * <p>
     * analytics -> account needs no exception: RiotAccountService lives in com.wkaiser.riot.account
     * (riot-account-core), outside this matcher.
     */
    @ArchTest
    static final ArchRule contexts_do_not_depend_on_each_other = slices()
            .matching("com.wkaiser.riot.lol.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(resideInAPackage("..lol.spectator.."), resideInAPackage("..lol.summoner.."))
            .ignoreDependency(resideInAPackage("..lol.analytics.."), resideInAPackage("..lol.summoner.."))
            .ignoreDependency(resideInAPackage("..lol.analytics.."), resideInAPackage("..lol.match.."));
}
```

**Resolved:** an earlier draft carried a conditional fourth `ignoreDependency(alwaysTrue(), ..lol.shared..)`
in case a `lol.shared` package survived. It did not — Task 5 deleted `GlobalExceptionHandler`, its only
member, and the directory tree with it. There is no `..lol.shared..` package, so that exception is gone
and `alwaysTrue` is not imported. Confirm with:
```bash
ls lol-mcp-server/src/main/java/com/wkaiser/riot/lol/shared 2>/dev/null && echo "UNEXPECTED — package came back; re-add the exception" || echo "absent, as expected"
```

- [ ] **Step 4: Write the account library's architecture test**

Encodes the module's contract — a library that ships no tools.

`riot-account-core/src/test/java/com/wkaiser/riot/account/architecture/AccountArchitectureTest.java`:
```java
package com.wkaiser.riot.account.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.wkaiser.riot.core.testsupport.HexagonRules;

/**
 * Architecture rules for the shared account library. The headline rule is
 * {@code no_mcp_tools_in_this_library}: account-v1 is cross-game, so exposing a tool here would
 * put an identically-named tool in every installed game server and collide inside the client.
 * Each server owns its own inbound adapter instead.
 */
@AnalyzeClasses(packages = "com.wkaiser.riot.account", importOptions = ImportOption.DoNotIncludeTests.class)
class AccountArchitectureTest {

    @ArchTest
    static final ArchRule no_mcp_tools_in_this_library = HexagonRules.NO_MCP_TOOLS_AT_ALL;

    @ArchTest
    static final ArchRule layers_respect_inward_dependency_rule = HexagonRules.LAYERS_RESPECT_INWARD_DEPENDENCY_RULE;

    @ArchTest
    static final ArchRule restclient_confined_to_outbound_adapters =
            HexagonRules.RESTCLIENT_CONFINED_TO_OUTBOUND_ADAPTERS;

    @ArchTest
    static final ArchRule ports_are_interfaces = HexagonRules.PORTS_ARE_INTERFACES;

    @ArchTest
    static final ArchRule ports_are_named_port_and_are_interfaces =
            HexagonRules.PORTS_ARE_NAMED_PORT_AND_ARE_INTERFACES;

    @ArchTest
    static final ArchRule services_live_in_application = HexagonRules.SERVICES_LIVE_IN_APPLICATION;

    @ArchTest
    static final ArchRule adapters_live_in_outbound_riot = HexagonRules.ADAPTERS_LIVE_IN_OUTBOUND_RIOT;
}
```

- [ ] **Step 5: Drop lol-mcp-server's now-redundant direct ArchUnit dependency**

The `testFixtures(project(':riot-api-core'))` line is already present (Task 2 added it). ArchUnit now arrives transitively through `testFixturesApi`, so the direct declaration is redundant — remove it. Resulting `lol-mcp-server/build.gradle` dependencies:
```groovy
dependencies {
	implementation project(':riot-api-core')
	implementation project(':riot-account-core')

	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'

	testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
	testImplementation testFixtures(project(':riot-api-core'))
}
```

- [ ] **Step 6: Prove the account no-tools rule actually bites**

A rule that passes vacuously is worse than no rule. Temporarily add a tool to the library and confirm the build fails:

```bash
cat > riot-account-core/src/main/java/com/wkaiser/riot/account/TempCanary.java <<'EOF'
package com.wkaiser.riot.account;

import org.springframework.ai.mcp.annotation.McpTool;

public class TempCanary {
    @McpTool(name = "temp_canary", description = "should be rejected by ArchUnit")
    public String canary() {
        return "nope";
    }
}
EOF
```
`riot-account-core` has no Spring AI MCP dependency, so this will not even compile — which is itself a stronger guarantee than the ArchUnit rule. Confirm:
```bash
./gradlew :riot-account-core:compileJava
```
Expected: **FAILURE**, `package org.springframework.ai.mcp.annotation does not exist`.

Record this: the no-tools property is enforced twice — by the absent dependency (compile-time) and by ArchUnit (in case a future change adds the dependency for another reason). Then remove the canary:
```bash
rm riot-account-core/src/main/java/com/wkaiser/riot/account/TempCanary.java
```

- [ ] **Step 7: Build**

```bash
./gradlew spotlessApply && ./gradlew build
```
Expected: **BUILD SUCCESSFUL**. If the slice rule fails, read the reported cycle carefully — a genuine unexpected cross-context dependency is a finding worth reporting, not something to silence with another `ignoreDependency`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "test: share ArchUnit rules via core test fixtures, replace N*N matrix with slices"
```

---

### Task 8: Rework Docker and CI for multi-module

**Files:**
- Modify: `Dockerfile`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`

- [ ] **Step 1: Parameterize the Dockerfile by server module**

Replace `Dockerfile` lines 1–32 (keeping the Task 5 comment edits below them):
```dockerfile
# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot executable jar ----
# Full JDK 21 toolchain. We build with the committed Gradle wrapper so the image
# build matches local and CI builds exactly (Gradle 9.6.1, Spring Boot 4.1.0).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Which server module to package. One image per server; the monorepo builds them all.
ARG SERVER_MODULE=lol-mcp-server

# Copy the wrapper and build scripts first so this layer caches independently of
# source-only changes. buildSrc holds the shared convention plugin every module applies.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY buildSrc ./buildSrc

# Libraries the server modules depend on, then the server sources.
COPY riot-api-core ./riot-api-core
COPY riot-account-core ./riot-account-core
COPY ${SERVER_MODULE} ./${SERVER_MODULE}

# Produce the boot jar. Tests run in CI (ci.yml), not in the image build, so we
# skip them here: this keeps image builds fast and requires no RIOT_API_KEY.
RUN chmod +x ./gradlew && ./gradlew --no-daemon :${SERVER_MODULE}:bootJar -x test

# ---- Stage 2: slim runtime ----
# JRE-only base — no compiler or build tooling ships in the final image.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

ARG SERVER_MODULE=lol-mcp-server

# Run as an unprivileged user rather than root.
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin appuser
USER appuser

# Copy only the built artifact from the build stage.
COPY --from=build /workspace/${SERVER_MODULE}/build/libs/*.jar app.jar
```

Then, after the Task 5 runtime-config comment block, replace the `EXPOSE`/`ENTRYPOINT` section:
```dockerfile
# The container runs the SSE profile: stdio transport is for local clients that spawn the
# process directly, which is not how a container is consumed. SSE serves /mcp/messages on 8080.
ENV SPRING_PROFILES_ACTIVE=sse
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Verify the image builds and runs**

```bash
docker build --build-arg SERVER_MODULE=lol-mcp-server -t lol-mcp-server:local .
docker run --rm -d -p 8080:8080 -e RIOT_API_KEY=dummy --name lol-smoke lol-mcp-server:local
sleep 15
curl -sf -o /dev/null -w '%{http_code}\n' http://localhost:8080/actuator/health
docker rm -f lol-smoke
```
Expected: `200`. This is the first proof the Dockerfile works since Task 1 broke it.

- [ ] **Step 3: Fix the JaCoCo report path in CI**

`.github/workflows/ci.yml` — `report_paths` at line 47 is already a glob and needs no change. The JaCoCo `paths` at line 61 is a single absolute path and now misses every module. Replace that step's `paths` value:
```yaml
          paths: ${{ github.workspace }}/**/build/reports/jacoco/test/jacocoTestReport.xml
```
Also update the stale comment above the step:
```yaml
      # Comment a JaCoCo coverage summary on the PR. The glob picks up every module's report.
      # Threshold is 0 here (the build already owns the soft coverage gate); this step is
      # signal, not a blocker.
```

- [ ] **Step 4: Publish one image per server module**

`.github/workflows/release.yml` — add a matrix to the job and thread the module through. Change the job definition to include:
```yaml
    strategy:
      matrix:
        server: [lol-mcp-server]
```
Then the metadata and build steps:
```yaml
      - name: Extract image metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/${{ matrix.server }}
          tags: |
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=raw,value=latest

      - name: Build and push image
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ./Dockerfile
          build-args: |
            SERVER_MODULE=${{ matrix.server }}
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

The image name changes from `riot-api-mcp-server` to `lol-mcp-server`. That is a deliberate break: the repo now produces one image per game server, and adding TFT means one line in the matrix. Note it in the CHANGELOG (Task 9).

- [ ] **Step 5: Build and commit**

```bash
./gradlew build
git add -A
git commit -m "ci: build one image per server module, fix multi-module JaCoCo path"
```

---

### Task 9: Documentation and knowledge base

Spec migration step 9. `gotchas.md` was already updated in Task 6.

**Files:**
- Create: `docs/knowledge/decisions/ADR-0006-monorepo-split.md`
- Modify: `docs/knowledge/README.md`, `CLAUDE.md`, `ARCHITECTURE.md`, `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`
- Modify (package-root drift, see below): `docs/knowledge/patterns/add-a-bounded-context.md`, `docs/knowledge/patterns/add-an-mcp-tool.md`, `docs/knowledge/patterns/add-an-adapter-test.md`, `docs/knowledge/gotchas.md`, `.claude/skills/scaffold-bounded-context/SKILL.md`, `.claude/agents/riot-context-architect.md`
- Annotate only (do NOT rewrite): `docs/knowledge/decisions/ADR-0001-hexagonal.md`, `docs/knowledge/decisions/ADR-0002-shared-riot-http-client.md`

**Package-root drift — added to this task's scope after Task 4.** Task 4's rename left 30 stale
`com.wkaiser.riotapimcpserver` references across 12 tracked files. Only 4 were in this task's
original list. Verify the full set before and after with:
```bash
git grep -ln "riotapimcpserver" -- . | grep -v '^docs/superpowers/'
```
`docs/superpowers/` (specs and plans, including this file) is **excluded deliberately** — those are
dated historical records of what was decided when, and rewriting them would falsify the record.

Two categories need different treatment:

- **Patterns, gotchas, skills, and agents are live instructions** — `add-a-bounded-context.md` alone
  has 13 stale references, and it is a copy-paste template, so a stale root there actively generates
  wrong code. The `.claude/` skill and agent files steer future agent sessions. These get a real
  find-and-replace onto `com.wkaiser.riot.{core,account,lol}`. `gotchas.md` also names
  `..shared.http..` in its ArchUnit note — Task 7 moved that rule to `..core.http..`; update it to match.
- **ADR-0001 and ADR-0002 are historical records.** `docs/knowledge/README.md`'s protocol says to
  supersede an ADR, never edit it to reverse a decision. Their decisions still stand — only the
  package root moved — so do **not** rewrite their bodies. Add a single line directly under each
  one's `**Date:**`:
  ```markdown
  - **Amended by:** [ADR-0006](ADR-0006-monorepo-split.md) — package roots are now
    `com.wkaiser.riot.{core,account,lol}`; this ADR's decision is unchanged.
  ```
  This preserves the record while stopping it from misleading a reader.

- [ ] **Step 1: Write ADR-0006**

`docs/knowledge/decisions/ADR-0006-monorepo-split.md`, using the template from `docs/knowledge/README.md`:
```markdown
# ADR-0006: Monorepo of per-game MCP servers over a shared core

- **Status:** Accepted
- **Date:** 2026-07-15

## Context

The project set out to reach full Riot API parity. Across LoL, TFT, Valorant, and LoR that is
~60–70 endpoints, and one `@McpTool` per endpoint means ~65 tools in a single server — past the
point where MCP clients pick tools reliably, and paid for in context on every request.

An intermediate design kept one server and gated tools with `@ConditionalOnProperty` "game packs"
so the default surface stayed small. The gating was simulating a boundary that already existed:
installing the TFT server *is* the opt-in. Other forces pointed the same way — Valorant needs a
third host-routing scheme and a production API key that a dev key cannot substitute for, and
neither concern should reach the LoL server.

## Decision

Restructure as a Gradle monorepo: `riot-api-core` (HTTP, routing, errors) and
`riot-account-core` (the cross-game account context) as libraries, with one Spring Boot MCP
server per game depending on both. Libraries are consumed via `@AutoConfiguration`, never
component-scanned.

`riot-account-core` deliberately ships **no** `@McpTool`. account-v1 is cross-game, so a tool
there would appear in every installed server and collide by name inside the client. Each server
owns a thin inbound adapter instead, which also lets tool names be namespaced per game.

## Consequences

**Makes easy:** each server's tool surface is naturally small, so no gating machinery. The
dependency rule is enforced by Gradle at compile time rather than by an ArchUnit test — core
cannot depend on a game context because it has no dependency on one. Valorant's key-tier
weirdness stays in the Valorant module. Adding a game is a new module, not a new set of
conditionals.

**Costs:** every file moved and the package root was renamed (`com.wkaiser.riotapimcpserver` was
a server name doing a library's job). Per-module build wiring, now centralized in a `buildSrc`
convention plugin. One Docker image per server rather than one for the repo.

**Watch for:** `riot-api-core` becoming a junk drawer. It holds plumbing only — the account
context is a separate module precisely to keep domain out of it. Deferred deliberately, because a
monorepo makes them cheap to revise later: rate limiting, error-message quality, Bearer/RSO auth,
and a generalized host-routing abstraction (Valorant forces the last one; TFT reuses LoL's hosts).

**Asymmetry to know about:** each server's `account` package holds a tool with no domain or
application layer of its own — those live in the library. That is intentional; the alternative is
duplicating the account domain per server to preserve a shape.
```

- [ ] **Step 2: Link the ADR from the knowledge index**

In `docs/knowledge/README.md`, append to the Decisions list:
```markdown
- [ADR-0006 — Monorepo of per-game MCP servers over a shared core](decisions/ADR-0006-monorepo-split.md)
```

- [ ] **Step 3: Update CLAUDE.md**

The "What this is" and "Architecture summary" sections describe a single module and are now wrong. Rewrite "Architecture summary" to:
```markdown
## Architecture summary

A Gradle monorepo. Two libraries and one server per game:

- **`riot-api-core`** (`com.wkaiser.riot.core`) — `RiotApiClient` (all HTTP/auth/error handling),
  `RiotApiProperties`, routing enums, `RiotApiException`. Auto-configured, never
  component-scanned. Its test fixtures hold the shared `HexagonRules` and `Fixtures`.
- **`riot-account-core`** (`com.wkaiser.riot.account`) — the cross-game account-v1 context.
  Domain + service + outbound adapter, **no `@McpTool`** (ArchUnit-enforced).
- **`lol-mcp-server`** (`com.wkaiser.riot.lol`) — bounded-context hexagons `summoner`, `match`,
  `spectator`, `analytics`, plus a tool-only `account` package. Per context: `domain/`,
  `application/` (+ `application/port/`), `adapter/in/mcp/`, `adapter/out/riot/`.

**Dependency rule:** servers → `riot-account-core` → `riot-api-core`, never back. Gradle enforces
this at compile time. Within a module, ArchUnit enforces `adapter → application → domain` (inward
only), `RestClient` only in `adapter.out.riot`, `@McpTool` only in `adapter.in.mcp`, and context
independence via a slice rule (exceptions: spectator→summoner, analytics→summoner, analytics→match).

**Transport:** every server ships `stdio` (default) and `sse` profiles. See `gotchas.md` before
touching stdio logging.
```
Also update the build commands section — `./gradlew build` still works, but add:
```markdown
./gradlew :lol-mcp-server:bootRun --args='--spring.profiles.active=sse'   # run the LoL server over SSE
```
And correct the "Four tool classes" line under MCP server details — the classes moved packages but the ten tool names are unchanged.

- [ ] **Step 4: Update ARCHITECTURE.md, README.md, CONTRIBUTING.md**

- `ARCHITECTURE.md` — module diagram, the compile-time vs ArchUnit enforcement split, the account asymmetry, both transports.
- `README.md` — install/run instructions per transport; **remove every `ANTHROPIC_API_KEY` reference** (it is no longer needed to boot); the image is now `ghcr.io/<owner>/lol-mcp-server`.
- `CONTRIBUTING.md` — recipes now say which module a change belongs in; ArchUnit rules come from `HexagonRules` in core's test fixtures.

- [ ] **Step 5: Update CHANGELOG.md**

Add above the `[1.1.0]` entry, per Keep a Changelog:
```markdown
## [Unreleased]

Monorepo restructure (sub-project 0). Structural only — the MCP tool surface is unchanged: the
same 10 tools with the same names, guarded by `McpToolInventoryTest`.

### Added
- **Gradle monorepo** — `riot-api-core` and `riot-account-core` (libraries) + `lol-mcp-server`
  (Boot app), with shared build logic in a `buildSrc` convention plugin.
- **Auto-configuration for both libraries** (`@AutoConfiguration` + `AutoConfiguration.imports`),
  each covered by an `ApplicationContextRunner` slice test.
- **`stdio` (default) and `sse` transport profiles.** stdio is what local MCP clients expect; see
  `docs/knowledge/gotchas.md` for why stdout must stay clean.
- **Shared ArchUnit rules** in `riot-api-core`'s test fixtures, so a new game server inherits the
  architecture. `AccountArchitectureTest` asserts the account library ships no `@McpTool`.
- **ADR-0006** documenting the split.

### Changed
- Package roots are now `com.wkaiser.riot.{core,account,lol}` — the old
  `com.wkaiser.riotapimcpserver` was a server name doing a library's job.
- The cross-context ArchUnit matrix (one rule per context, each listing every other) is now a
  single `slices()` rule that stays correct as contexts are added.
- **Breaking (packaging):** the published image is now `ghcr.io/<owner>/lol-mcp-server`, one per
  game server, built via `--build-arg SERVER_MODULE=`. Previously `riot-api-mcp-server`.
  **The old `riot-api-mcp-server` tags are not deleted**, so anyone still pulling that path keeps
  silently receiving the last pre-monorepo image rather than getting an error. Say so plainly here —
  a stale image that works is worse than one that fails, because nobody investigates it.

### Removed
- **`ANTHROPIC_API_KEY` is no longer required to start the server.** The Anthropic starter was
  never used (no `ChatClient`/`ChatModel` anywhere) but `application.yml` demanded its key at boot.
- `htmx-spring-boot` — unused; no controllers, no templates.
- `GlobalExceptionHandler` — `@RestControllerAdvice` in an app with no controllers. Spring AI
  converts `@McpTool` exceptions itself, so it could never fire.
```

- [ ] **Step 6: Clear the package-root drift and verify none remains**

Apply the two treatments described in this task's Files section: find-and-replace across the
patterns, gotchas, and `.claude/` files; a one-line `**Amended by:**` annotation on ADR-0001 and
ADR-0002 without touching their bodies.

Then prove the drift is gone:
```bash
git grep -ln "riotapimcpserver" -- . | grep -v '^docs/superpowers/'
```
Expected: **no output**. Any file still listed is either unfixed or a deliberate historical mention
you must justify in the report.

Also confirm the replacements landed on the right roots — `shared` became `core`, not a blanket
swap:
```bash
git grep -n "com.wkaiser.riot.core.http\|com.wkaiser.riot.lol\|com.wkaiser.riot.account" -- docs/knowledge .claude | head -20
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "docs: document the monorepo split (ADR-0006, CLAUDE.md, ARCHITECTURE, CHANGELOG)"
```

---

### Task 10: End-to-end verification

The spec is explicit that green tests are insufficient — they prove the code compiles and units behave, not that the server still serves. **No unit test can catch stdout corruption.**

**Files:** none modified. This task produces a verification record.

- [ ] **Step 1: Build the jar**

```bash
./gradlew clean build
JAR=$(ls lol-mcp-server/build/libs/lol-mcp-server-0.0.2-SNAPSHOT.jar)
echo "$JAR"
```
Expected: **BUILD SUCCESSFUL** and a jar path. If the glob also matches a `-plain.jar`, use the one without that suffix.

- [ ] **Step 2: Verify the stdio transport end to end**

This is the task's whole reason for existing. Every stdout line must be valid JSON — a banner or log line makes a line fail to parse.

```bash
printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  | RIOT_API_KEY=dummy-verification-key java -jar "$JAR" 2>/tmp/stdio-stderr.log >/tmp/stdio-stdout.jsonl

echo "--- every stdout line must parse as JSON ---"
while IFS= read -r line; do
  echo "$line" | jq -e . >/dev/null 2>&1 || { echo "NOT JSON: $line"; exit 1; }
done < /tmp/stdio-stdout.jsonl
echo "OK: stdout is clean JSON-RPC"
```
Expected: `OK: stdout is clean JSON-RPC`. A `NOT JSON:` line showing a banner or a log message means `application-stdio.yml` is not suppressing stdout — fix Task 6 before proceeding.

- [ ] **Step 3: Assert the 10-tool inventory over the real protocol**

`McpToolInventoryTest` proves the annotations are intact; this proves the *server actually advertises* them.

```bash
jq -r 'select(.id==2) | .result.tools[].name' /tmp/stdio-stdout.jsonl | sort > /tmp/actual-tools.txt
cat > /tmp/expected-tools.txt <<'EOF'
check_if_summoner_in_game
get_current_game_by_summoner_id
get_current_game_by_summoner_name
get_featured_games
get_lol_player_match_analytics
get_lol_summoner_by_id
get_lol_summoner_by_name
get_lol_summoner_by_puuid
get_riot_account_by_puuid
get_riot_account_by_riot_id
EOF
diff /tmp/expected-tools.txt /tmp/actual-tools.txt && echo "OK: 10 tools, names unchanged"
```
Expected: `OK: 10 tools, names unchanged`, no diff output.

- [ ] **Step 4: Verify the sse transport end to end**

```bash
RIOT_API_KEY=dummy-verification-key java -jar "$JAR" --spring.profiles.active=sse &
SSE_PID=$!
sleep 20
curl -sf -o /dev/null -w 'health: %{http_code}\n' http://localhost:8080/actuator/health
curl -sf -N -m 5 http://localhost:8080/sse | head -5
kill $SSE_PID
```
Expected: `health: 200`, and the SSE stream emits an `endpoint` event pointing at `/mcp/messages`. If the SSE endpoint path differs in Spring AI 2.0, take the health check plus a successful startup as the gate and record the actual path.

- [ ] **Step 5: Confirm logs went to the file, not the console**

```bash
ls -la ./riot-mcp-server.log && head -3 ./riot-mcp-server.log
```
Expected: the file exists and holds the startup logs — proof they were routed off stdout rather than merely silenced. Clean up: `rm -f ./riot-mcp-server.log`.

- [ ] **Step 6: Record the verification in the PR/commit**

Paste the actual output of Steps 2–5 into the PR description. Per the project's verification discipline, evidence goes before assertions: do not claim the restructure is complete without the `OK:` lines above.

- [ ] **Step 7: Final full build**

```bash
./gradlew clean build
```
Expected: **BUILD SUCCESSFUL** — all modules, ArchUnit, JaCoCo, Spotless.

---

## Plan self-review

**Spec coverage** — every spec section maps to a task:

| Spec section | Task |
|---|---|
| Module layout / dependency graph | 1 |
| Libraries auto-configured, not scanned | 2, 3 |
| `riot-api-core` contents | 2 |
| `riot-account-core`, asymmetric account context | 3 |
| Tool names held until sub-project 1 | 1 (guard), enforced throughout |
| Transport (stdio + sse, stdout trap) | 6 |
| Build/quality gates (convention plugin, slices, per-module coverage) | 1, 7 |
| Docker/CI | 8 |
| Testing (fixtures placement, slice tests) | 2, 3, 7 |
| Migration sequence steps 1–9 | 1, 2, 3, 4, 5, 6, 7, 9 |
| Verification (both transports, tool inventory) | 10 |
| Non-goals | Global Constraints |
| Follow-ups (rate limiting, error quality) | Not implemented — correct; they are out of scope by design |

**Gap found and closed:** the spec's "Build and quality gates" section covers Docker/CI but the numbered migration sequence omits it. Added as Task 8, with the knowingly-broken-Dockerfile window called out in Global Constraints.

**Placeholder scan:** no TBDs. Every code step carries complete code. Two steps are conditional on a check rather than prescriptive, deliberately: Task 5 Step 1 (verify the handler is dead before deleting) and Task 7 Step 4 (the `lol.shared` ignore only if that package survives). Both state the exact command and both outcomes.

**Type consistency:** `RiotApiProperties.getApiKey()/getRegion()/getBaseUrlOverride()` and `RiotAccountPort.getAccountByRiotId(String, String)/getAccountByPuuid(String)` match the real signatures read from source. `HexagonRules` constant names in Task 7 Step 3 match their uses in Steps 4–5. `EXPECTED_TOOL_NAMES` (Task 1) matches `/tmp/expected-tools.txt` (Task 10 Step 3) — both are the same 10 names.

**Known risk the plan does not eliminate:** Task 4's `sed`-based package rewrite is mechanical and ordered most-specific-first, but an IDE refactor is safer if available. The two `AutoConfiguration.imports` files are plain text — the compiler cannot catch a missed edit, and only `ApplicationContextLoadsTest` and the slice tests will. This is called out in Task 4 Step 3.
