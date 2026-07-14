# Phase 4: CI Pipeline + Packaging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the CI workflow to run and surface all Phase 3 quality gates on every PR, and add container packaging that publishes a versioned image to GHCR on `v*` tags — all with least-privilege permissions and no live API keys.

**Architecture:** `ci.yml` (replacing `gradle.yml`) runs `./gradlew build` — which now gates unit + WireMock + ArchUnit tests plus JaCoCo report generation and `spotlessCheck` from Phases 2–3 — then annotates JUnit results and comments a JaCoCo coverage summary on the PR; a separate low-privilege job submits the dependency graph. A multi-stage `Dockerfile` builds the Spring Boot boot jar in a JDK stage and ships it on a slim JRE 21 runtime; `release.yml` builds that image on `v*` tags and pushes it to `ghcr.io/<owner>/riot-api-mcp-server` using the built-in `GITHUB_TOKEN`.

**Tech Stack:** GitHub Actions (`actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/*`, `docker/*-action`, `mikepenz/action-junit-report`, `madrapps/jacoco-report`), Docker multi-stage build, GitHub Container Registry (GHCR), Gradle 9.6.1 wrapper, Java 21, Spring Boot 4.1.0.

## Global Constraints

- This phase touches only `.github/workflows/`, `.github/dependabot.yml` (read-only verification), and a new repo-root `Dockerfile`. No Java source, `build.gradle`, or `application.yml` changes.
- The existing `.github/workflows/claude-code-review.yml` and `.github/workflows/claude.yml` are **left byte-for-byte untouched**. Never edit, rename, or delete them.
- `.github/dependabot.yml` remains exactly as-is (weekly Gradle updates). No CodeQL / SAST workflow is added (Dependabot covers CVEs; ArchUnit covers structure — per spec Non-Goals).
- CI triggers on `push` and `pull_request` to `master`. The release workflow triggers on `v*` tags only.
- Runner: `ubuntu-latest`. JDK for CI: Java `21`, distribution `zulu` (matches the toolchain the Gradle build downloads). Docker base images use `eclipse-temurin:21-*` (spec-permitted).
- Pin `gradle/actions/*` to the SHA already used in `gradle.yml` (`af1da67850ed9a4cedd57bfd976089dd991e2582`, v4.0.0); other first/third-party actions use their major-version tag, matching the existing repo style.
- Least privilege: the workflow-level default is `permissions: contents: read`. Each job elevates only what its actions require. The only credential used is the built-in `GITHUB_TOKEN` — no repo secrets beyond it, and no API keys anywhere in CI or the image.
- Coverage XML consumed by CI is the Phase 3 JaCoCo default: `build/reports/jacoco/test/jacocoTestReport.xml`. JUnit XML is the Gradle default: `build/test-results/test/TEST-*.xml`.
- Commands run from Git Bash on Windows. `docker build` verification requires a local Docker daemon; if unavailable, the task's fallback check is the YAML/Dockerfile lint plus a `--call check` dry parse noted in-step.
- The Riot API key must never be hard-coded, logged, or baked into the image — it is supplied at container runtime via `RIOT_API_KEY`.

---

### Task 1: Refactor `gradle.yml` into `ci.yml` with gate publishing

Replace the plain-build workflow with one that runs the full Phase 3 gate set and surfaces test + coverage results on PRs, while keeping dependency submission in its own least-privilege job.

**Files:**
- Create: `.github/workflows/ci.yml`
- Delete: `.github/workflows/gradle.yml` (via `git rm` — `ci.yml` replaces it)
- Leave untouched: `.github/workflows/claude-code-review.yml`, `.github/workflows/claude.yml`

**Interfaces:**
- Consumes: the Gradle `build` task (Phases 1–3 wired unit + WireMock + ArchUnit + JaCoCo + `spotlessCheck` into it); JUnit XML at `build/test-results/test/TEST-*.xml`; JaCoCo XML at `build/reports/jacoco/test/jacocoTestReport.xml`; the built-in `secrets.GITHUB_TOKEN`.
- Produces: a `CI` workflow with two jobs — `build` (contents: read, checks: write, pull-requests: write) and `dependency-submission` (contents: write, push-only).

- [ ] **Step 1: Create `ci.yml`**

Create `.github/workflows/ci.yml` with the complete content:

```yaml
name: CI

on:
  push:
    branches: [ "master" ]
  pull_request:
    branches: [ "master" ]

# Least-privilege default for the whole workflow. Individual jobs elevate only
# the scopes their actions need (see each job's `permissions:` block).
permissions:
  contents: read

jobs:
  build:
    name: Build & verify
    runs-on: ubuntu-latest
    permissions:
      contents: read        # actions/checkout
      checks: write         # mikepenz/action-junit-report publishes a check run
      pull-requests: write  # madrapps/jacoco-report posts a PR comment
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'zulu'

      # Configures Gradle with dependency caching for GitHub Actions.
      # See: https://github.com/gradle/actions/blob/main/setup-gradle/README.md
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582 # v4.0.0

      # `build` gates every Phase 3 quality check: unit + WireMock adapter tests,
      # the ArchUnit rule suite, JaCoCo report generation, and spotlessCheck.
      - name: Build with Gradle Wrapper
        run: ./gradlew build

      # Publish JUnit results as a check run with inline annotations, even when
      # the build failed, so failing tests are visible on the PR.
      - name: Publish test results
        if: always()
        uses: mikepenz/action-junit-report@v5
        with:
          report_paths: '**/build/test-results/test/TEST-*.xml'
          check_name: 'JUnit Test Results'
          annotate_only: true
          require_tests: true

      # Comment a JaCoCo coverage summary on the PR. The XML path is the Phase 3
      # jacocoTestReport default. Threshold is 0 here (the build already owns the
      # soft coverage gate); this step is signal, not a blocker.
      - name: Publish coverage summary
        if: github.event_name == 'pull_request'
        uses: madrapps/jacoco-report@v1.7.1
        with:
          paths: ${{ github.workspace }}/build/reports/jacoco/test/jacocoTestReport.xml
          token: ${{ secrets.GITHUB_TOKEN }}
          title: 'JaCoCo Coverage Report'
          min-coverage-overall: 0
          min-coverage-changed-files: 0

  dependency-submission:
    name: Submit dependency graph
    runs-on: ubuntu-latest
    # Only on pushes to master: submitting the graph writes to the repository,
    # and pull requests from forks cannot be granted `contents: write`.
    if: github.event_name == 'push'
    permissions:
      contents: write  # gradle/actions/dependency-submission writes the graph
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'zulu'

      # Generates and submits a dependency graph, enabling Dependabot alerts.
      # See: https://github.com/gradle/actions/blob/main/dependency-submission/README.md
      - name: Generate and submit dependency graph
        uses: gradle/actions/dependency-submission@af1da67850ed9a4cedd57bfd976089dd991e2582 # v4.0.0
```

> **Why two jobs?** `gradle/actions/dependency-submission` requires `contents: write` to push the dependency graph. Rather than grant the whole workflow write access, it is isolated in its own push-only job; the `build` job that runs on every PR keeps the minimal `contents: read` (plus the narrow `checks: write` / `pull-requests: write` the annotation actions need). This means a fork PR — which cannot receive write scopes — still runs the full gate, and the dependency graph is submitted only from trusted `push` events.

- [ ] **Step 2: Delete the old workflow**

```bash
git rm .github/workflows/gradle.yml
```

- [ ] **Step 3: Verify the old workflow is gone and the Claude workflows are intact**

```bash
test ! -f .github/workflows/gradle.yml && echo "gradle.yml removed"
ls .github/workflows/
```

Expected: prints `gradle.yml removed`, then a listing of exactly three files: `ci.yml`, `claude-code-review.yml`, `claude.yml`.

- [ ] **Step 4: Confirm the Claude workflows were not modified**

```bash
git status --porcelain .github/workflows/claude-code-review.yml .github/workflows/claude.yml
```

Expected: no output (both files unchanged).

- [ ] **Step 5: Lint the new workflow YAML**

Preferred (if `actionlint` is installed):

```bash
actionlint .github/workflows/ci.yml
```

Expected: no output, exit code 0.

Fallback (portable YAML parse) if `actionlint` is not available:

```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('ci.yml: valid yaml')"
```

Expected: `ci.yml: valid yaml`.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/ci.yml
git rm --cached .github/workflows/gradle.yml 2>/dev/null || true
git commit -m "ci: replace gradle.yml with ci.yml publishing test and coverage results"
```

---

### Task 2: Multi-stage `Dockerfile` at the repo root

Package the application as a container: a JDK stage builds the Spring Boot boot jar via the committed Gradle wrapper; a slim JRE 21 stage runs it as a non-root user and documents the required environment.

**Files:**
- Create: `Dockerfile` (repo root)

**Interfaces:**
- Consumes: the Gradle wrapper (`gradlew`, `gradle/`), `build.gradle`, `settings.gradle`, and `src/`; the `bootJar` task producing `build/libs/riot-api-mcp-server-*.jar`.
- Produces: a runnable image whose entrypoint is `java -jar /app/app.jar`, exposing `8080`, reading `RIOT_API_KEY` and `ANTHROPIC_API_KEY` from the environment.

- [ ] **Step 1: Create `Dockerfile`**

Create `Dockerfile` at the repo root with the complete content:

```dockerfile
# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot executable jar ----
# Full JDK 21 toolchain. We build with the committed Gradle wrapper so the image
# build matches local and CI builds exactly (Gradle 9.6.1, Spring Boot 4.1.0).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build scripts first so this layer caches independently of
# source-only changes.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Copy the application sources.
COPY src ./src

# Produce the boot jar. Tests run in CI (ci.yml), not in the image build, so we
# skip them here: this keeps image builds fast and requires no RIOT_API_KEY.
RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar -x test

# ---- Stage 2: slim runtime ----
# JRE-only base — no compiler or build tooling ships in the final image.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as an unprivileged user rather than root.
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin appuser
USER appuser

# Copy only the built artifact from the build stage.
COPY --from=build /workspace/build/libs/*.jar app.jar

# Required runtime configuration (12-factor; no secrets are baked into the image):
#   RIOT_API_KEY       Riot Games API key      -> bound to riot.apiKey
#   ANTHROPIC_API_KEY  Anthropic API key       -> Spring AI model integration
# Supply them at run time, e.g.:
#   docker run --rm -p 8080:8080 \
#     -e RIOT_API_KEY=RGAPI-xxxx \
#     -e ANTHROPIC_API_KEY=sk-ant-xxxx \
#     ghcr.io/<owner>/riot-api-mcp-server:latest

# Spring Boot serves the MCP SSE endpoint (/mcp/messages) on 8080 by default.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Lint the Dockerfile syntax**

```bash
grep -q '^FROM eclipse-temurin:21-jdk AS build' Dockerfile \
  && grep -q '^FROM eclipse-temurin:21-jre AS runtime' Dockerfile \
  && grep -q 'bootJar' Dockerfile \
  && grep -q 'EXPOSE 8080' Dockerfile \
  && echo "Dockerfile structure OK"
```

Expected: `Dockerfile structure OK`.

- [ ] **Step 3: Verify the image builds locally**

With a running Docker daemon:

```bash
docker build -t riot-api-mcp-server:local .
```

Expected: build completes with `naming to docker.io/library/riot-api-mcp-server:local` (or `Successfully tagged ...`), exit code 0. Confirm the produced image contains the jar:

```bash
docker run --rm --entrypoint sh riot-api-mcp-server:local -c 'ls -1 /app/app.jar'
```

Expected: `/app/app.jar`.

> **If no Docker daemon is available** in the execution environment, the build cannot be exercised here; record that and rely on Task 3's `release.yml` (which runs `docker/build-push-action` on GitHub's runners) as the CI-side proof. Do **not** mark this task done without either a local `docker build` success or an explicit note that the daemon was unavailable.

- [ ] **Step 4: Commit**

```bash
git add Dockerfile
git commit -m "build: add multi-stage Dockerfile for containerized deployment"
```

---

### Task 3: `release.yml` — build and push the image to GHCR on `v*` tags

Publish a versioned container image to GitHub Container Registry when a `v*` tag is pushed, using only the built-in `GITHUB_TOKEN`.

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: the repo-root `Dockerfile` (Task 2); the pushed `v*` git tag; `github.actor`, `github.repository_owner`, and the built-in `secrets.GITHUB_TOKEN`.
- Produces: a `Release` workflow (permissions: `contents: read`, `packages: write`) that pushes `ghcr.io/<owner>/riot-api-mcp-server` tagged with the semver from the tag plus `latest`.

- [ ] **Step 1: Create `release.yml`**

Create `.github/workflows/release.yml` with the complete content:

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

# Pushing to GHCR needs write access to GitHub Packages; nothing else is written.
permissions:
  contents: read
  packages: write

jobs:
  publish-image:
    name: Build & publish container image
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

      # Derives image tags/labels from the pushed git tag. metadata-action always
      # lowercases the image name, so the `Muddl` owner resolves to ghcr.io/muddl/...
      # (GHCR rejects uppercase repository paths).
      - name: Extract image metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/riot-api-mcp-server
          tags: |
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=raw,value=latest

      - name: Build and push image
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ./Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

> **Tag semantics:** for a tag `v0.0.2`, `type=semver,pattern={{version}}` yields `0.0.2`, `{{major}}.{{minor}}` yields `0.0`, and `type=raw,value=latest` always adds `latest` (per spec: "semver from the tag + latest"). The owner is taken from `github.repository_owner` and lowercased by `docker/metadata-action`.

- [ ] **Step 2: Lint the release workflow YAML**

Preferred (if `actionlint` is installed):

```bash
actionlint .github/workflows/release.yml
```

Expected: no output, exit code 0.

Fallback if `actionlint` is unavailable:

```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml')); print('release.yml: valid yaml')"
```

Expected: `release.yml: valid yaml`.

- [ ] **Step 3: Verify the GHCR image reference and permissions are present**

```bash
grep -q 'ghcr.io/${{ github.repository_owner }}/riot-api-mcp-server' .github/workflows/release.yml \
  && grep -q 'packages: write' .github/workflows/release.yml \
  && grep -q "tags:\s*\[\s*'v\*'\s*\]\|- 'v\*'" .github/workflows/release.yml \
  && echo "release.yml references OK"
```

Expected: `release.yml references OK`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: add release workflow publishing container image to GHCR on tags"
```

---

### Task 4: Confirm Dependabot remains and no CodeQL is introduced

A verification-only task: assert the CVE watch (Dependabot) is untouched and no SAST workflow was added.

**Files:**
- Verify (no changes): `.github/dependabot.yml`
- Verify absent: any CodeQL workflow under `.github/workflows/`

**Interfaces:** none produced. This task only asserts invariants.

- [ ] **Step 1: Confirm `dependabot.yml` is present and unmodified**

```bash
test -f .github/dependabot.yml && echo "dependabot present"
git status --porcelain .github/dependabot.yml
```

Expected: prints `dependabot present`, then **no** second line (the file is unchanged in the working tree and index).

- [ ] **Step 2: Confirm the Gradle ecosystem entry is intact**

```bash
grep -q 'package-ecosystem: "gradle"' .github/dependabot.yml && echo "gradle ecosystem present"
```

Expected: `gradle ecosystem present`.

- [ ] **Step 3: Confirm no CodeQL / SAST workflow exists**

```bash
grep -ril 'codeql\|github/codeql-action' .github/workflows/ || echo "no codeql workflow"
```

Expected: `no codeql workflow`.

- [ ] **Step 4: Final workflow inventory**

```bash
ls .github/workflows/
```

Expected: exactly `ci.yml`, `claude-code-review.yml`, `claude.yml`, `release.yml`.

> No commit is required for this task (no files change). If any check fails, return to the responsible task before proceeding.

---

## Self-Review

- **Spec coverage (Decision 4 slice):**
  - `ci.yml` refactor of `gradle.yml`, triggers push/PR to `master`, checkout → `setup-java@v4` (21, zulu) → `gradle/actions/setup-gradle` (cache) → `./gradlew build` (unit + WireMock + ArchUnit + JaCoCo + `spotlessCheck` gate here) → JUnit PR annotations (`mikepenz/action-junit-report@v5`) → JaCoCo PR comment (`madrapps/jacoco-report@v1.7.1` pointed at `build/reports/jacoco/test/jacocoTestReport.xml`) → `gradle/actions/dependency-submission` retained — Task 1 ✓.
  - Least-privilege permissions with `dependency-submission` isolated in its own `contents: write` push-only job; `build` job scoped to `contents: read` + `checks: write` + `pull-requests: write`; rationale explained inline — Task 1 ✓.
  - `gradle.yml` deleted via `git rm` — Task 1 Steps 2–3 ✓.
  - Multi-stage `Dockerfile`: JDK build stage (`bootJar` via wrapper) → slim JRE 21 runtime, documents `RIOT_API_KEY` + `ANTHROPIC_API_KEY`, `EXPOSE 8080`, `java -jar` entrypoint; `docker build` verification — Task 2 ✓.
  - `release.yml` on `v*` tags, GHCR push via `GITHUB_TOKEN` with `packages: write` + `contents: read`, `docker/login-action` + `docker/metadata-action` (semver + latest) + `docker/build-push-action`, owner lowercased — Task 3 ✓.
  - Dependabot retained, no CodeQL — Task 4 ✓. Claude workflows untouched — asserted in Tasks 1 & 4 ✓.
- **Placeholder scan:** none. Every workflow and the Dockerfile are shown in full; every verification step gives an exact command and expected output. `<owner>` appears only inside descriptive comments/prose (resolved at runtime by `github.repository_owner`), never as an unfilled code token.
- **Type/name consistency with Phase 1 & prior phases:** JaCoCo XML path (`build/reports/jacoco/test/jacocoTestReport.xml`) is the Gradle default the Phase 3 `jacocoTestReport` task produces; JUnit XML glob (`**/build/test-results/test/TEST-*.xml`) is the Gradle default from `useJUnitPlatform()` (present in `build.gradle`). `./gradlew build` is the same green-build gate every Phase 1 task ends on, now carrying the Phase 2–3 gates. Java 21 / Spring Boot 4.1.0 / Gradle 9.6.1 wrapper and `group = com.wkaiser` / `rootProject.name = riot-api-mcp-server` are consistent with `build.gradle` and `settings.gradle`. The `gradle/actions/*` SHA pin (`af1da678…`, v4.0.0) is carried over verbatim from the original `gradle.yml`.
</content>
</invoke>
