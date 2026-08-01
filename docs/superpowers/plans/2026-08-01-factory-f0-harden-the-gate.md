# Factory F0 — Harden the Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the merge gate mean something before anything depends on it — unblock fork PRs, pin `Build & verify` as a required status check on `master` via two rulesets with identical bypass, bound and kill-switch every agent job, add an alert path that reaches a phone, apply ruleset config from a script that never runs in CI, verify it with a scheduled drift check, close #71, and sweep the stale "post-merge live eval" claim.

**Architecture:** Repository ruleset `8769144` is split — updated in place (PUT, id preserved) into **R1** (`~ALL`: `deletion`, `non_fast_forward`) and joined by a newly created **R2** (`~DEFAULT_BRANCH`: `pull_request{1 approval}`, `required_status_checks[Build & verify]`). Both carry the identical admin-`exempt` bypass so the undocumented cross-ruleset bypass question never arises. The desired state lives as committed JSON under `.github/rulesets/`; a local-only apply script (maintainer's own credential) writes it, and a read-only scheduled workflow diffs live against committed and fails loudly. Every `claude-code-action` job gains `timeout-minutes`, `if: vars.FACTORY_ENABLED == 'true'`, and an `if: failure()` step that `@Muddl`-mentions on an issue so GitHub Mobile pushes a notification.

**Tech Stack:** GitHub Actions, GitHub repository rulesets API, `gh` CLI, `jq`, Bash, Markdown. No application (Java) code changes.

## Global Constraints

- **A green Actions run is not proof of success.** Three green-but-did-nothing incidents are on record in this repo (`gotchas.md`). Every task below asserts the *intended effect* — a check run named in a ruleset, a merge button that refuses, a drift check that goes red — never the job's exit status.
- **Editing `claude-code-review.yml` or `claude.yml` means this PR will not be reviewed by Claude.** `claude-code-action` hard-requires the workflow file to be byte-identical to the default-branch copy; dispatched from a branch that edits it, the action **skips itself** with `Skipping action due to workflow validation` recorded as an *annotation on a successful job*. Tasks 3–5 edit both files, so expect no Claude review on the F0 PR. **State this in the PR body** (Task 9 Step 2) so it is not later misread as the reviewer silently failing. See `docs/knowledge/gotchas.md`, "`claude-code-action` is silently skipped when the workflow file differs from the default branch".
- **`enforcement: "evaluate"` (ruleset dry-run) is "only available with GitHub Enterprise"** and does not exist on this free personal repo. Do not plan around it, do not add it to any JSON, do not suggest "try it in evaluate mode first".
- **Automation may propose but never approve — and never rewrites the rules.** The apply script runs **locally, under the maintainer's own credential, never in CI**. A workflow able to rewrite rulesets could delete the approval requirement, which is strictly stronger than approving its own work. CI only ever *reads* rulesets, with a read-only credential.
- **Rollback is one phone-executable command**, stated in Task 6 Step 8 and repeated in the kill-switch pattern guide:
  `gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/8769144 -f enforcement=disabled`
- **`ci.yml` is never gated on `FACTORY_ENABLED`.** It produces the required check; gating it would make every PR permanently unmergeable the moment the kill switch is flipped. The kill switch gates *agent* jobs only.
- **Two credential buckets stay separate** (ADR-0012): the factory runs on the flat `CLAUDE_CODE_OAUTH_TOKEN` seat; `ANTHROPIC_API_KEY` stays scoped to `live-eval.yml`. Nothing here touches `live-eval.yml`'s triggers or credentials.
- **ADRs are amended, never edited** (`docs/knowledge/README.md` persist protocol). ADR-0012 and ADR-0017 get blockquote amendments in the style ADR-0015 already uses; their bodies are left intact.
- **`docs/superpowers/specs/` and `docs/superpowers/plans/` are immutable history** and are excluded from the doc sweep in Task 8.
- Commit messages end with the repo's trailers:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap`
- All work lands on branch `agent/f0-harden-the-gate`, opened as a PR against `master`. Implements GitHub issue **#73** and closes **#71**.

---

## File Structure

| File | Responsibility |
|---|---|
| `.github/workflows/ci.yml` | **Modify.** Fork-PR fix on both reporting steps (Task 1); `concurrency` with `cancel-in-progress: false` (Task 3). Never kill-switched. |
| `.github/workflows/claude-code-review.yml` | **Modify.** `concurrency` `cancel-in-progress: true`, `timeout-minutes`, `FACTORY_ENABLED` gate, failure alert. |
| `.github/workflows/claude.yml` | **Modify.** `timeout-minutes`, `FACTORY_ENABLED` gate, failure alert. |
| `.github/workflows/housekeeping.yml` | **Modify.** `timeout-minutes`, `FACTORY_ENABLED` gate, `issues: write`, failure alert; `::add-mask::` for the derived auth header and the comment-fragment tidy (#71). |
| `.github/workflows/ruleset-drift.yml` | **New.** Daily read-only drift check: live rulesets vs committed JSON. Fails loudly; not kill-switched. |
| `.github/rulesets/R1-all-branches.json` | **New.** Desired state for ruleset `8769144` — `~ALL`, `deletion` + `non_fast_forward`. |
| `.github/rulesets/R2-default-branch.json` | **New.** Desired state for the new ruleset — `~DEFAULT_BRANCH`, `pull_request{1}` + `required_status_checks[Build & verify]`. |
| `scripts/rulesets/apply-rulesets.sh` | **New.** Local-only applier. Refuses to run under `GITHUB_ACTIONS`. |
| `scripts/rulesets/check-drift.sh` | **New.** Read-only comparator, shared by the workflow and by local runs. |
| `docs/knowledge/patterns/factory-kill-switch.md` | **New.** The escalation ladder + rollback commands, written to be readable and executable from a phone. |
| `docs/knowledge/decisions/ADR-0019-gate-hardening-and-ruleset-topology.md` | **New (persist).** Two rulesets, identical bypass, local-only apply, drift check. |
| `docs/knowledge/decisions/ADR-0012-live-eval-harness.md` | **Amend (not edit).** Blockquote amendment: the harness is dispatch-only, not post-merge. |
| `docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md` | **Amend (not edit).** Same correction to its "Relationship to ADR-0012" wording. |
| `docs/knowledge/README.md` | **Modify (persist).** Link ADR-0019 and the new pattern guide. |
| `docs/knowledge/roadmap.md` | **Modify (persist).** Close the F0 row; fix six stale "post-merge" claims. |
| `docs/knowledge/gotchas.md` | **Modify (persist).** Three new entries; fix two stale "post-merge" claims. |
| `CLAUDE.md`, `README.md`, `CONTRIBUTING.md` | **Modify.** Stale "post-merge" live-eval claim. |

---

### Task 1: Unblock fork PRs in `ci.yml` before any required check exists

**Files:**
- Modify: `.github/workflows/ci.yml` (the `Publish test results` step, lines 44–51; the `Publish coverage summary` step, lines 56–64)

**Interfaces:**
- Consumes: `secrets.GITHUB_TOKEN` (read-only on fork `pull_request` events — this is the constraint being worked around).
- Produces: a `Build & verify` job that reaches a `success` conclusion on a PR from a forked repository, making it safe to pin as a required status check in Task 6.

For a `pull_request` event from a **forked** repository, `GITHUB_TOKEN` is read-only *regardless of the workflow's `permissions:` key* — the key can only reduce a token's scopes, never raise them above what the event grants. `mikepenz/action-junit-report` writes a check run (checks API) and `madrapps/jacoco-report` posts a PR comment; both receive `403` and, with no `continue-on-error`, fail the job. So `Build & verify` fails on **every outside contribution today**. Pinning it as a required check before fixing this would render outside contributions to a **public portfolio repo** permanently unmergeable. This is why it is Task 1 and not a footnote.

Two mitigations are applied together: the coverage step is *skipped* on forks (it can produce nothing there, so running it is pure noise), and both steps are made non-fatal so any residual permission surprise degrades to a missing report rather than a red required check.

- [ ] **Step 1: Make the JUnit reporter non-fatal**

In `.github/workflows/ci.yml`, change:
```yaml
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
```
to:
```yaml
      # Publish JUnit results as a check run with inline annotations, even when
      # the build failed, so failing tests are visible on the PR.
      #
      # continue-on-error: for a `pull_request` from a FORK, GITHUB_TOKEN is read-only
      # regardless of this workflow's `permissions:` block (the key can only reduce), so the
      # checks-API write 403s. Reporting is signal, never the gate — `Build & verify` is a
      # required status check (see .github/rulesets/R2-default-branch.json) and must not go
      # red because a reporter could not write. The `./gradlew build` step above is the gate.
      - name: Publish test results
        if: always()
        continue-on-error: true
        uses: mikepenz/action-junit-report@v5
        with:
          report_paths: '**/build/test-results/test/TEST-*.xml'
          check_name: 'JUnit Test Results'
          annotate_only: true
          require_tests: true
```

- [ ] **Step 2: Skip the coverage comment on forks and make it non-fatal**

In the same file, change:
```yaml
      - name: Publish coverage summary
        if: github.event_name == 'pull_request'
        uses: madrapps/jacoco-report@v1.7.1
```
to:
```yaml
      # Skipped on fork PRs: the read-only fork GITHUB_TOKEN cannot post a PR comment, so this
      # can only 403. continue-on-error covers the residual case (e.g. a comment-locked PR).
      - name: Publish coverage summary
        if: github.event_name == 'pull_request' && github.event.pull_request.head.repo.fork == false
        continue-on-error: true
        uses: madrapps/jacoco-report@v1.7.1
```

- [ ] **Step 3: Lint the YAML**

Run: `python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('ok')"`
Expected: `ok`

- [ ] **Step 4: Confirm the gate itself is untouched**

Run: `grep -n "continue-on-error" .github/workflows/ci.yml`
Expected: exactly two lines, both inside the reporting steps — and **no** `continue-on-error` on `Build with Gradle Wrapper`. If the build step ever gains one, the required check becomes decorative.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "$(cat <<'EOF'
fix(ci): stop fork PRs failing Build & verify on reporter 403s

For a pull_request from a fork, GITHUB_TOKEN is read-only regardless of the
workflow's permissions: block, so mikepenz/action-junit-report (checks API) and
madrapps/jacoco-report (PR comment) both 403 and fail the job. Skip the coverage
comment on forks and mark both reporters continue-on-error so the job's
conclusion reflects ./gradlew build only.

Prerequisite for pinning "Build & verify" as a required status check (#73):
without it, every outside contribution to a public repo would be unmergeable.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap
EOF
)"
```

---

### Task 2: Confirm the check-run name from a real run

**Files:** none (evidence-gathering task; its output is consumed verbatim by Task 6)

**Interfaces:**
- Consumes: the GitHub checks API for a commit that has a completed `ci.yml` run.
- Produces: the exact string to place in `required_status_checks[].context`. A required check whose context does not match a real check-run name never turns green and blocks the PR forever with no visible cause.

The name is **not** the workflow name (`CI`) and **not** the job key (`build`) — it is the job's `name:` (`.github/workflows/ci.yml` line 16, `name: Build & verify`). Confirm it against the API rather than reading it off the YAML, because that is the string the ruleset matches.

- [ ] **Step 1: List check-run names on the current `master` head**

Run:
```bash
SHA="$(gh api repos/Muddl/riot-api-mcp-server/commits/master --jq '.sha')"
echo "sha=$SHA"
gh api "repos/Muddl/riot-api-mcp-server/commits/$SHA/check-runs" \
  --jq '.check_runs[] | "\(.name) | \(.conclusion) | app_id=\(.app.id)"'
```
Expected: two lines, one of which is exactly `Build & verify`. Observed on `18cebda3e6563d50355f1c52f1e6726fb3015add`:
```
Build & verify | null | app_id=15368
Submit dependency graph | success | app_id=15368
```
(`conclusion: null` simply means that run was still in flight when sampled; the **name** is what matters.) If the string differs by so much as the ampersand spacing, use the API's value in Task 6, not this plan's.

- [ ] **Step 2: Record the ampersand hazard**

Run: `gh api repos/Muddl/riot-api-mcp-server/commits/master/check-runs --jq '.check_runs[].name' | cat -A | grep -n 'Build'`
Expected: `Build & verify$` — a literal space-ampersand-space, no HTML entity, no trailing whitespace. Anything else means the JSON in Task 6 must be adjusted to match byte-for-byte.

- [ ] **Step 3: Decide on `integration_id`**

`required_status_checks[].integration_id` optionally pins the *app* allowed to report that context, which prevents a third party from satisfying the check with a same-named run. Step 1 printed `app_id=15368` (GitHub Actions). **This plan omits `integration_id`** from the committed JSON: it is one more value that can drift silently, and this repo grants no third party check-write access. If you choose to pin it, add `"integration_id": <value from Step 1>` to the object in Task 6 Step 2 and to nothing else — the drift check will then hold it.

No commit: this task produces evidence, not files. Carry the confirmed string `Build & verify` into Task 6.

---

### Task 3: Bound the agent jobs — concurrency and timeouts

**Files:**
- Modify: `.github/workflows/ci.yml` (insert a `concurrency:` block after the `on:` block, before the `permissions:` block at line 11)
- Modify: `.github/workflows/claude-code-review.yml` (insert `concurrency:` after the `on:` block, line 5; add `timeout-minutes` to the `claude-review` job)
- Modify: `.github/workflows/claude.yml` (add `timeout-minutes` to the `claude` job)
- Modify: `.github/workflows/housekeeping.yml` (add `timeout-minutes` to the `housekeeping` job)

**Interfaces:**
- Produces: no `ci.yml` run is ever *cancelled* by a newer one; every job that runs `claude-code-action` terminates on its own within a stated wall-clock bound.

Two facts drive opposite settings. **A cancelled check run never satisfies a required status check** — it reports `conclusion: cancelled`, which is neither success nor a pending state GitHub will wait on, so a superseded `ci.yml` run would leave the PR blocked with a stale grey check and no obvious cause. Hence `cancel-in-progress: false` on `ci.yml`. On `claude-code-review.yml` a superseded review is pure waste, so `true`. Separately, `claude-code-action` **exposes no timeout of its own** — its message loop runs until the job's `timeout-minutes` (default 360, i.e. six hours of a flat-rate seat).

- [ ] **Step 1: Serialise `ci.yml` per ref, never cancelling**

In `.github/workflows/ci.yml`, change:
```yaml
on:
  push:
    branches: [ "master" ]
  pull_request:
    branches: [ "master" ]

# Least-privilege default for the whole workflow. Individual jobs elevate only
```
to:
```yaml
on:
  push:
    branches: [ "master" ]
  pull_request:
    branches: [ "master" ]

# Queue, never cancel. `Build & verify` is a REQUIRED status check on master
# (.github/rulesets/R2-default-branch.json), and a *cancelled* check run does not satisfy a
# required check — it is neither success nor a pending state the merge box waits on. Cancelling
# a superseded run would leave the PR blocked behind a grey check with no visible cause.
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: false

# Least-privilege default for the whole workflow. Individual jobs elevate only
```

- [ ] **Step 2: Cancel superseded reviews, and bound the review job**

In `.github/workflows/claude-code-review.yml`, change:
```yaml
on:
  pull_request:
    types: [opened, synchronize, ready_for_review, reopened]

jobs:
  claude-review:
    # Skip drafts — they aren't ready for review and would waste subscription usage.
    if: ${{ !github.event.pull_request.draft }}
    runs-on: ubuntu-latest
    permissions:
```
to:
```yaml
on:
  pull_request:
    types: [opened, synchronize, ready_for_review, reopened]

# Opposite of ci.yml on purpose: a review of an older commit is waste, not signal, and this
# workflow produces no required check, so cancelling it blocks nothing.
concurrency:
  group: claude-review-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  claude-review:
    # Skip drafts — they aren't ready for review and would waste subscription usage.
    if: ${{ !github.event.pull_request.draft }}
    runs-on: ubuntu-latest
    # claude-code-action exposes no timeout of its own; its message loop runs until the job's.
    # Without this the default is 360 minutes of flat-rate seat on a wedged review.
    timeout-minutes: 20
    permissions:
```

- [ ] **Step 3: Bound the `@claude` job**

In `.github/workflows/claude.yml`, change:
```yaml
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write   # post review comments when @claude-mentioned on a PR
```
to:
```yaml
    runs-on: ubuntu-latest
    # claude-code-action exposes no timeout of its own; its message loop runs until the job's.
    timeout-minutes: 30
    permissions:
      contents: read
      pull-requests: write   # post review comments when @claude-mentioned on a PR
```

- [ ] **Step 4: Bound the housekeeping job**

In `.github/workflows/housekeeping.yml`, change:
```yaml
jobs:
  housekeeping:
    runs-on: ubuntu-latest
    permissions:
      contents: write        # create the housekeeping branch
```
to:
```yaml
jobs:
  housekeeping:
    runs-on: ubuntu-latest
    # claude-code-action exposes no timeout of its own; its message loop runs until the job's.
    # This job runs unattended on a Monday cron — an unbounded hang is the worst case.
    timeout-minutes: 30
    permissions:
      contents: write        # create the housekeeping branch
```

- [ ] **Step 5: Lint all four workflows**

Run:
```bash
for f in ci claude claude-code-review housekeeping; do
  python -c "import yaml; yaml.safe_load(open('.github/workflows/$f.yml')); print('$f ok')"
done
```
Expected:
```
ci ok
claude ok
claude-code-review ok
housekeeping ok
```

- [ ] **Step 6: Assert every `claude-code-action` job is now bounded**

Run:
```bash
grep -l "claude-code-action" .github/workflows/*.yml | while read -r f; do
  printf '%s: ' "$f"; grep -c "timeout-minutes:" "$f"
done
```
Expected: `claude-code-review.yml: 1`, `claude.yml: 1`, `housekeeping.yml: 1` — every file that uses the action has a timeout. A `0` on any line means a job can still hang for six hours.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/ci.yml .github/workflows/claude-code-review.yml .github/workflows/claude.yml .github/workflows/housekeeping.yml
git commit -m "$(cat <<'EOF'
ci: bound agent jobs with timeouts and set concurrency deliberately

ci.yml: cancel-in-progress false — a cancelled check run never satisfies a
required status check and would block the PR behind a grey check with no
visible cause. claude-code-review.yml: cancel-in-progress true, since a review
of a superseded commit is waste and produces no required check.

timeout-minutes on every job running claude-code-action: the action exposes no
timeout of its own and its message loop runs until the job's (default 360).

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap
EOF
)"
```

---

### Task 4: Add the `FACTORY_ENABLED` kill switch and document the ladder

**Files:**
- Modify: `.github/workflows/claude.yml` (the job-level `if:` expression, lines 15–19)
- Modify: `.github/workflows/claude-code-review.yml` (the job-level `if:`, now line ~16 after Task 3)
- Modify: `.github/workflows/housekeeping.yml` (add a job-level `if:` to the `housekeeping` job)
- Create: `docs/knowledge/patterns/factory-kill-switch.md`

**Interfaces:**
- Consumes: repository variable `FACTORY_ENABLED` (`vars.FACTORY_ENABLED`), created in Step 1.
- Produces: a single toggle, flippable from a mobile browser at
  `https://github.com/Muddl/riot-api-mcp-server/settings/variables/actions`, that stops every agent job while leaving `ci.yml` — and therefore the merge gate — fully operational.

`vars.*` is always a **string**; comparing to `'true'` means an unset variable, a typo, or a deleted variable all evaluate false. That is the correct fail-safe direction: **absent means off**. `ci.yml` is deliberately not gated (see Global Constraints) — the required check must keep reporting when the factory is off, or flipping the switch would make every PR unmergeable.

- [ ] **Step 1: Create the variable, on**

Run:
```bash
gh variable set FACTORY_ENABLED --repo Muddl/riot-api-mcp-server --body "true"
gh variable list --repo Muddl/riot-api-mcp-server
```
Expected: `FACTORY_ENABLED` present with value `true`. Create it **before** merging the workflow edits — a merged gate against a non-existent variable silently disables every agent job.

- [ ] **Step 2: Gate the `@claude` job**

In `.github/workflows/claude.yml`, change:
```yaml
    if: |
      (github.event_name == 'issue_comment' && contains(github.event.comment.body, '@claude')) ||
      (github.event_name == 'pull_request_review_comment' && contains(github.event.comment.body, '@claude')) ||
      (github.event_name == 'pull_request_review' && contains(github.event.review.body, '@claude')) ||
      (github.event_name == 'issues' && (contains(github.event.issue.body, '@claude') || contains(github.event.issue.title, '@claude')))
```
to:
```yaml
    # Kill switch. vars.* is always a string, so an unset, deleted, or mistyped variable
    # evaluates false — absent means OFF, which is the fail-safe direction. Flip it at
    # /settings/variables/actions from any browser, phone included.
    # See docs/knowledge/patterns/factory-kill-switch.md for the full escalation ladder.
    if: |
      vars.FACTORY_ENABLED == 'true' && (
      (github.event_name == 'issue_comment' && contains(github.event.comment.body, '@claude')) ||
      (github.event_name == 'pull_request_review_comment' && contains(github.event.comment.body, '@claude')) ||
      (github.event_name == 'pull_request_review' && contains(github.event.review.body, '@claude')) ||
      (github.event_name == 'issues' && (contains(github.event.issue.body, '@claude') || contains(github.event.issue.title, '@claude'))))
```

- [ ] **Step 3: Gate the review job**

In `.github/workflows/claude-code-review.yml`, change:
```yaml
    # Skip drafts — they aren't ready for review and would waste subscription usage.
    if: ${{ !github.event.pull_request.draft }}
```
to:
```yaml
    # Skip drafts — they aren't ready for review and would waste subscription usage.
    # vars.FACTORY_ENABLED is the kill switch; absent/mistyped means OFF (fail-safe).
    # This job produces no required check, so a skipped run blocks no merge.
    if: ${{ !github.event.pull_request.draft && vars.FACTORY_ENABLED == 'true' }}
```

- [ ] **Step 4: Gate the housekeeping job**

In `.github/workflows/housekeeping.yml`, change:
```yaml
jobs:
  housekeeping:
    runs-on: ubuntu-latest
    # claude-code-action exposes no timeout of its own; its message loop runs until the job's.
    # This job runs unattended on a Monday cron — an unbounded hang is the worst case.
    timeout-minutes: 30
```
to:
```yaml
jobs:
  housekeeping:
    # Kill switch. This is the only unattended cron in the factory, so it is the job the switch
    # exists for. Absent/mistyped variable means OFF. Gating the job (not the step) also skips
    # the git/PR steps, so a disabled factory opens no branches.
    if: vars.FACTORY_ENABLED == 'true'
    runs-on: ubuntu-latest
    # claude-code-action exposes no timeout of its own; its message loop runs until the job's.
    # This job runs unattended on a Monday cron — an unbounded hang is the worst case.
    timeout-minutes: 30
```

- [ ] **Step 5: Write the ladder as a KB pattern guide**

Create `docs/knowledge/patterns/factory-kill-switch.md` with exactly this content:

```markdown
# Stopping the factory (escalation ladder)

Every rung is reachable from a **mobile browser** with no laptop, no local checkout, and no
`gh` install. Rungs are ordered least-destructive first; each is strictly stronger than the one
above it. Use the lowest rung that solves the problem.

## Rung 1 — flip `FACTORY_ENABLED` (seconds, reversible, no side effects)

`https://github.com/Muddl/riot-api-mcp-server/settings/variables/actions` → `FACTORY_ENABLED` →
Update → set to anything other than `true` (use `false`).

Every agent job is gated on `vars.FACTORY_ENABLED == 'true'`, so this stops
`claude.yml`, `claude-code-review.yml`, and `housekeeping.yml` at the job level: they report
`skipped`, spend nothing, and create no branches.

**What keeps running on purpose:** `ci.yml` (it produces the required `Build & verify` check —
gating it would make every PR unmergeable), `ruleset-drift.yml` (a safety net must survive the
kill switch), and `live-eval.yml` (manual dispatch only, metered bucket).

Runs already **in flight** are not affected — the gate is evaluated at job start. Cancel them
from the Actions tab if that matters.

## Rung 2 — disable the workflow from the Actions tab (survives a variable being re-set)

`https://github.com/Muddl/riot-api-mcp-server/actions` → pick the workflow → `···` →
**Disable workflow**.

Stronger than rung 1 because it is not a value another automation could set back, and because it
prevents the run from being *created* rather than skipping it after creation.

## Rung 3 — revoke `CLAUDE_CODE_OAUTH_TOKEN` (fails every agent run closed)

`https://github.com/Muddl/riot-api-mcp-server/settings/secrets/actions` → delete
`CLAUDE_CODE_OAUTH_TOKEN`, then revoke it at the Claude Code account level.

Every `claude-code-action` step then fails authentication. This is the rung to use when the
concern is "the agent is doing something harmful", not "the agent is wasting money": it is the
only rung that cannot be undone by anything that already has repo write.

Does **not** touch `ANTHROPIC_API_KEY`, which belongs to `live-eval.yml` alone (ADR-0012).

## Ruleset rollback (separate axis — protection, not agents)

If a ruleset change locks the repository — e.g. a required check that never reports, so nothing
can merge — disable the ruleset rather than editing it. Copy-pasteable, and it works from the
`gh` CLI on a phone or from any shell:

```bash
# R1 (~ALL: deletion, non_fast_forward) — id is stable and known
gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/8769144 -f enforcement=disabled

# R2 (~DEFAULT_BRANCH: PR approval + Build & verify) — id resolved by name
gh api --method PUT \
  "repos/Muddl/riot-api-mcp-server/rulesets/$(gh api repos/Muddl/riot-api-mcp-server/rulesets \
    --jq '.[] | select(.name | startswith("R2")) | .id')" \
  -f enforcement=disabled
```

Browser equivalent: `https://github.com/Muddl/riot-api-mcp-server/settings/rules` → the ruleset →
Enforcement status → **Disabled** → Save.

`enforcement: "evaluate"` (dry-run) is **GitHub Enterprise only** and is not available on this
repository — there is no "try it safely" middle setting. Disabled or active.

Re-enable by re-running `scripts/rulesets/apply-rulesets.sh` from a desk, which restores the
committed JSON exactly and is then confirmed by `ruleset-drift.yml`.

## After using any rung

Say so on the alert thread (`vars.FACTORY_ALERT_ISSUE`) — an unexplained silent factory is
indistinguishable from a broken one, which is the failure mode this whole sub-project exists to
remove.
```

- [ ] **Step 6: Lint the YAML and confirm every agent job is gated**

Run:
```bash
for f in claude claude-code-review housekeeping; do
  python -c "import yaml; yaml.safe_load(open('.github/workflows/$f.yml')); print('$f ok')"
done
grep -c "FACTORY_ENABLED" .github/workflows/claude.yml .github/workflows/claude-code-review.yml .github/workflows/housekeeping.yml
grep -c "FACTORY_ENABLED" .github/workflows/ci.yml
```
Expected: three `ok` lines; each of the three agent workflows reports `1` or more; **`ci.yml` reports `0`** (`grep -c` exits 1 with no match — that zero is the intended result, not an error).

- [ ] **Step 7: Link the pattern guide from the KB README**

In `docs/knowledge/README.md`, in the `### Patterns` list, immediately after:
```markdown
- [Run and extend the live eval harness](patterns/live-eval-harness.md)
```
add:
```markdown
- [Stopping the factory (escalation ladder)](patterns/factory-kill-switch.md)
```

- [ ] **Step 8: Commit**

```bash
git add .github/workflows/claude.yml .github/workflows/claude-code-review.yml .github/workflows/housekeeping.yml docs/knowledge/patterns/factory-kill-switch.md docs/knowledge/README.md
git commit -m "$(cat <<'EOF'
feat(ci): add the FACTORY_ENABLED kill switch and document the ladder

Every agent job now gates on vars.FACTORY_ENABLED == 'true'. vars.* is a
string, so unset/deleted/mistyped evaluates false — absent means OFF. ci.yml is
deliberately NOT gated: it produces the required Build & verify check, and
gating it would make every PR unmergeable the moment the switch is flipped.

Ladder (all phone-reachable): flip the variable -> disable the workflow from
the Actions tab -> revoke CLAUDE_CODE_OAUTH_TOKEN. Recorded as a KB pattern
guide with the copy-pasteable ruleset rollback commands alongside.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap
EOF
)"
```

---

### Task 5: Add a failure alert path that reaches a phone

**Files:**
- Modify: `.github/workflows/claude-code-review.yml` (append a final step to the `claude-review` job)
- Modify: `.github/workflows/claude.yml` (append a final step to the `claude` job)
- Modify: `.github/workflows/housekeeping.yml` (append a final step to the `housekeeping` job; add `issues: write` to its `permissions:` block)

**Interfaces:**
- Consumes: repository variable `FACTORY_ALERT_ISSUE` (a long-lived tracking issue number), created in Step 1; `secrets.GITHUB_TOKEN`.
- Produces: an `@Muddl` mention comment on every agent-job failure. GitHub Mobile pushes mention notifications; `schedule`-failure email covers only scheduled workflows and never `issues`-triggered runs, so a mention is the only channel that covers both.

`if: failure()` fires when any earlier step in the same job failed — including a `timeout-minutes` kill. It does **not** fire on `cancelled()`, which is correct: a cancellation is a human act, not a fault.

- [ ] **Step 1: Create the alert tracking issue and record its number**

Run:
```bash
gh issue create --repo Muddl/riot-api-mcp-server \
  --title "Factory alerts (do not close)" \
  --body "Long-lived thread. Agent workflows post an @Muddl mention here on failure so GitHub Mobile pushes a notification. Referenced by vars.FACTORY_ALERT_ISSUE. See docs/knowledge/patterns/factory-kill-switch.md."
gh variable set FACTORY_ALERT_ISSUE --repo Muddl/riot-api-mcp-server --body "<number printed above>"
gh variable list --repo Muddl/riot-api-mcp-server
```
Expected: an issue URL is printed, and `gh variable list` shows both `FACTORY_ENABLED` and `FACTORY_ALERT_ISSUE`. Substitute the real issue number for `<number printed above>` — do not leave a placeholder.

- [ ] **Step 2: Alert from the review job**

In `.github/workflows/claude-code-review.yml`, append to the end of the `claude-review` job's `steps:` list (after the `Run Claude Code Review` step), at the same indentation as `- name: Run Claude Code Review`:
```yaml
      # An unattended failure nobody sees is the same as no gate at all. `gh pr comment` uses
      # this job's `pull-requests: write`; the @-mention is what makes GitHub Mobile push a
      # notification (schedule-failure email does not cover pull_request-triggered runs).
      # failure() deliberately excludes cancelled() — a cancellation is a human act.
      - name: Alert on failure
        if: failure()
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          gh pr comment "${{ github.event.pull_request.number }}" --body \
            "@Muddl \`Claude Code Review\` failed on this PR. Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
```

- [ ] **Step 3: Alert from the `@claude` job**

In `.github/workflows/claude.yml`, append to the end of the `claude` job's `steps:` list (after the `Run Claude Code` step), at the same indentation as `- name: Run Claude Code`:
```yaml
      # Comments back on whichever issue or PR triggered the run. `gh issue comment` works on a
      # PR number too (PRs are issues); this job already holds issues: write and
      # pull-requests: write. The @-mention is the phone-notification channel.
      - name: Alert on failure
        if: failure()
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          TARGET: ${{ github.event.issue.number || github.event.pull_request.number }}
        run: |
          gh issue comment "$TARGET" --body \
            "@Muddl \`Claude Code\` failed here. Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
```

- [ ] **Step 4: Grant `issues: write` to the housekeeping job**

In `.github/workflows/housekeeping.yml`, change:
```yaml
    permissions:
      contents: write        # create the housekeeping branch
      pull-requests: write   # open the PR
      id-token: write
```
to:
```yaml
    permissions:
      contents: write        # create the housekeeping branch
      pull-requests: write   # open the PR
      issues: write          # post the @Muddl failure alert (see the last step)
      id-token: write
```

- [ ] **Step 5: Alert from the housekeeping job**

In `.github/workflows/housekeeping.yml`, append to the end of the `housekeeping` job's `steps:` list (after the `Open a PR if the pass changed anything` step), at the same indentation as `- name: Open a PR if the pass changed anything`:
```yaml
      # This is the only unattended cron in the factory: it fires at 09:00 UTC Monday with
      # nobody watching. There is no issue or PR context here, so the alert goes to the
      # long-lived tracking issue in vars.FACTORY_ALERT_ISSUE. If that variable is unset the
      # step fails loudly rather than swallowing the alert — a silent alerter is worse than none.
      - name: Alert on failure
        if: failure()
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          ALERT_ISSUE: ${{ vars.FACTORY_ALERT_ISSUE }}
        run: |
          if [ -z "$ALERT_ISSUE" ]; then
            echo "::error title=Alerting::FACTORY_ALERT_ISSUE is unset; the failure alert had nowhere to go."
            exit 1
          fi
          gh issue comment "$ALERT_ISSUE" --body \
            "@Muddl \`Weekly Housekeeping\` failed. Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
```

- [ ] **Step 6: Lint and confirm every agent workflow alerts**

Run:
```bash
for f in claude claude-code-review housekeeping; do
  python -c "import yaml; yaml.safe_load(open('.github/workflows/$f.yml')); print('$f ok')"
done
grep -c "if: failure()" .github/workflows/claude.yml .github/workflows/claude-code-review.yml .github/workflows/housekeeping.yml
```
Expected: three `ok` lines, then `1` for each of the three workflows.

- [ ] **Step 7: Prove the mention actually notifies (do not skip — this is the whole point)**

Run:
```bash
gh issue comment "$(gh variable list --repo Muddl/riot-api-mcp-server --json name,value \
  --jq '.[] | select(.name=="FACTORY_ALERT_ISSUE") | .value')" \
  --repo Muddl/riot-api-mcp-server \
  --body "@Muddl alert-path smoke test — ignore. If this did not push to your phone, the alert path is decorative."
```
Expected: a GitHub Mobile push notification arrives on the phone within ~a minute. If it does not, check Settings → Notifications → *Participating, @mentions and custom* has **Mobile** ticked before treating any later `if: failure()` step as an alert.

- [ ] **Step 8: Commit**

```bash
git add .github/workflows/claude.yml .github/workflows/claude-code-review.yml .github/workflows/housekeeping.yml
git commit -m "$(cat <<'EOF'
feat(ci): alert @Muddl on agent-job failure

Every agent job gains an `if: failure()` step that posts a gh comment
mentioning @Muddl — on the triggering PR/issue where there is one, and on the
vars.FACTORY_ALERT_ISSUE thread for the unattended housekeeping cron.

GitHub Mobile pushes mention notifications; schedule-failure email does not
cover issues- or pull_request-triggered runs, so a mention is the only channel
that covers the whole factory. failure() excludes cancelled() on purpose.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap
EOF
)"
```

---

### Task 6: Split the ruleset, apply it locally, and verify it on a schedule

**Files:**
- Create: `.github/rulesets/R1-all-branches.json`
- Create: `.github/rulesets/R2-default-branch.json`
- Create: `scripts/rulesets/apply-rulesets.sh`
- Create: `scripts/rulesets/check-drift.sh`
- Create: `.github/workflows/ruleset-drift.yml`

**Interfaces:**
- Consumes: the confirmed check-run name `Build & verify` from Task 2; the maintainer's own `gh` credential for writes; a read-only fine-grained PAT `RULESET_READ_TOKEN` for the scheduled check.
- Produces: two active rulesets whose live configuration is asserted daily against committed JSON, and a red workflow the moment they diverge.

**Disposition of ruleset `8769144`: updated in place with `PUT`, not replaced.** Its id is stable, already referenced in the roadmap, the spec, and the rollback command, and preserving it means the rollback in `factory-kill-switch.md` needs no lookup. The `pull_request` rule is *removed* from it and re-created inside R2; `deletion`, `non_fast_forward`, the `~ALL` include, the **`refs/heads/dependabot/*` exclude**, and the `{actor_id: 5, actor_type: RepositoryRole, bypass_mode: exempt}` bypass are all preserved verbatim.

**Order matters: create R2 first, then narrow R1.** Doing it the other way opens a window in which `master` has no approval requirement at all. Both rulesets carry the **identical** admin-`exempt` bypass — GitHub documents that rules across rulesets are aggregated with "the most restrictive version of the rule applies" but documents **nothing** about how *bypass* is evaluated when several rulesets apply, and `enforcement: "evaluate"` (dry-run) is Enterprise-only. Identical bypass means that undocumented question never arises. The tighter variant (`bypass_actors: []` on R2, so nobody merges red) is deferred to a post-trip item gated on testing layered bypass on a **throwaway probe repo**, never on this one.

**Residual risk, stated rather than solved:** the maintainer remains `exempt` and can still merge red. Machine identities — the ones that run unattended — cannot. That is the property F0 buys, and it is the whole property.

- [ ] **Step 1: Commit R1's desired state**

Create `.github/rulesets/R1-all-branches.json` with exactly this content:

```json
{
  "name": "R1 — all branches: no deletion, no force-push",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [
    {
      "actor_id": 5,
      "actor_type": "RepositoryRole",
      "bypass_mode": "exempt"
    }
  ],
  "conditions": {
    "ref_name": {
      "exclude": [
        "refs/heads/dependabot/*"
      ],
      "include": [
        "~ALL"
      ]
    }
  },
  "rules": [
    {
      "type": "deletion"
    },
    {
      "type": "non_fast_forward"
    }
  ]
}
```

`non_fast_forward` is kept on `~ALL` **on purpose**: F4 pushes revision rounds to live PR branches, where a force-push would erase round evidence and outdate every inline review comment. The dependabot exclude is carried over from the live ruleset unchanged.

- [ ] **Step 2: Commit R2's desired state**

Create `.github/rulesets/R2-default-branch.json` with exactly this content:

```json
{
  "name": "R2 — default branch: PR approval + green build",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [
    {
      "actor_id": 5,
      "actor_type": "RepositoryRole",
      "bypass_mode": "exempt"
    }
  ],
  "conditions": {
    "ref_name": {
      "exclude": [],
      "include": [
        "~DEFAULT_BRANCH"
      ]
    }
  },
  "rules": [
    {
      "type": "pull_request",
      "parameters": {
        "allowed_merge_methods": [
          "merge",
          "squash",
          "rebase"
        ],
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_approving_review_count": 1,
        "required_review_thread_resolution": false
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "required_status_checks": [
          {
            "context": "Build & verify"
          }
        ],
        "strict_required_status_checks_policy": false
      }
    }
  ]
}
```

`"Build & verify"` is the string confirmed in Task 2 — byte-for-byte, ampersand and spacing included. `strict_required_status_checks_policy: false` means a PR need not be rebased onto the newest `master` before merging; `true` would force a re-run of `Build & verify` on every intervening master commit, which on a solo repo is friction without signal.

- [ ] **Step 3: Write the local-only applier**

Create `scripts/rulesets/apply-rulesets.sh` with exactly this content:

```bash
#!/usr/bin/env bash
# Apply the committed ruleset JSON to GitHub. LOCAL ONLY — never from CI.
#
# Why local: a workflow able to rewrite rulesets could delete the approval requirement, which is
# strictly stronger than approving its own work and violates the standing constraint that
# automation may propose but never approve. Ruleset writes need `administration: write`; this
# script uses the maintainer's own `gh` credential (an OAuth token with `repo` scope, or a
# fine-grained PAT with Administration: read and write). No such credential exists in Actions,
# and none is ever added.
#
# Usage:  scripts/rulesets/apply-rulesets.sh [--dry-run]
set -euo pipefail

REPO="${RULESET_REPO:-Muddl/riot-api-mcp-server}"
R1_ID=8769144                       # existing ruleset, updated in place so its id stays stable
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.github/rulesets" && pwd)"
DRY_RUN=false
[ "${1:-}" = "--dry-run" ] && DRY_RUN=true

if [ -n "${GITHUB_ACTIONS:-}" ]; then
  echo "REFUSING: this script must never run in GitHub Actions. See ADR-0019." >&2
  exit 1
fi

command -v gh >/dev/null || { echo "gh CLI not found" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq not found" >&2; exit 1; }
gh auth status >/dev/null || { echo "gh is not authenticated" >&2; exit 1; }

ruleset_id_by_name() {
  gh api "repos/$REPO/rulesets" --jq ".[] | select(.name == \"$1\") | .id" | head -n1
}

apply() {  # apply <json-file> [known-id]
  local file="$DIR/$1" id="${2:-}" name
  name="$(jq -r .name "$file")"
  [ -n "$id" ] || id="$(ruleset_id_by_name "$name")"
  if $DRY_RUN; then
    echo "would ${id:+PUT $id}${id:+ }${id:-POST} <- $1 ($name)"
    return 0
  fi
  if [ -n "$id" ]; then
    echo "PUT  $REPO/rulesets/$id  <- $1"
    gh api --method PUT "repos/$REPO/rulesets/$id" --input "$file" >/dev/null
  else
    echo "POST $REPO/rulesets      <- $1"
    gh api --method POST "repos/$REPO/rulesets" --input "$file" >/dev/null
  fi
}

# ORDER IS LOAD-BEARING. R2 carries the approval requirement that R1 is about to lose; creating
# R2 first means the default branch is never, even briefly, without one.
apply R2-default-branch.json
apply R1-all-branches.json "$R1_ID"

echo
echo "Applied. Verifying against the committed JSON:"
exec "$(dirname "${BASH_SOURCE[0]}")/check-drift.sh"
```

- [ ] **Step 4: Write the read-only drift comparator**

Create `scripts/rulesets/check-drift.sh` with exactly this content:

```bash
#!/usr/bin/env bash
# Compare the live rulesets against the committed JSON. READ ONLY — makes no writes at all.
# Runs both locally and from ruleset-drift.yml so there is exactly one implementation.
#
# `.github/rulesets/` is not a GitHub-recognised path; nothing on GitHub's side reads it. Without
# this check the committed JSON is documentation cosplaying as configuration — ADR-0018's exact
# failure shape, artifact present, property absent.
set -euo pipefail

REPO="${RULESET_REPO:-Muddl/riot-api-mcp-server}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.github/rulesets" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
status=0

# Server-generated fields that are not part of the desired state.
STRIP='del(.id, .node_id, ._links, .created_at, .updated_at, .source, .source_type, .current_user_can_bypass)'

for file in R1-all-branches.json R2-default-branch.json; do
  name="$(jq -r .name "$DIR/$file")"
  id="$(gh api "repos/$REPO/rulesets" --jq ".[] | select(.name == \"$name\") | .id" | head -n1)"
  if [ -z "$id" ]; then
    echo "::error title=Ruleset drift::No live ruleset named \"$name\". Expected from $file."
    status=1
    continue
  fi
  gh api "repos/$REPO/rulesets/$id" | jq -S "$STRIP" > "$TMP/live.json"
  jq -S . "$DIR/$file" > "$TMP/want.json"
  if diff -u "$TMP/want.json" "$TMP/live.json" > "$TMP/diff.txt"; then
    echo "ok   $file (id $id) matches live"
  else
    echo "::error title=Ruleset drift::Live ruleset $id (\"$name\") differs from $file"
    cat "$TMP/diff.txt"
    status=1
  fi
done

if [ "$status" -ne 0 ]; then
  echo
  echo "Live branch protection no longer matches the committed intent. Either someone hand-edited"
  echo "a ruleset in the UI, or the committed JSON was changed without applying it. Reconcile with"
  echo "scripts/rulesets/apply-rulesets.sh (locally, at a desk) or update the JSON to match."
fi
exit "$status"
```

- [ ] **Step 5: Write the scheduled drift workflow**

Create `.github/workflows/ruleset-drift.yml` with exactly this content:

```yaml
name: Ruleset Drift

on:
  schedule:
    - cron: '17 7 * * *'   # daily 07:17 UTC — offset off the hour to dodge cron congestion
  workflow_dispatch:

# READ ONLY, deliberately. GITHUB_TOKEN has no `administration` scope at all — the permissions
# key does not offer one — so ruleset reads use a dedicated fine-grained PAT whose ONLY grant is
# Administration: read. No credential in this repository's Actions can write a ruleset; that is
# the point (see ADR-0019).
permissions:
  contents: read

# NOT gated on vars.FACTORY_ENABLED. This is the safety net that tells you the gate is still
# there; it must keep running precisely when the factory has been switched off.
concurrency:
  group: ruleset-drift
  cancel-in-progress: false

jobs:
  drift:
    name: Ruleset drift check
    runs-on: ubuntu-latest
    timeout-minutes: 5
    permissions:
      contents: read
      issues: write    # post the @Muddl alert on drift
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Compare live rulesets against committed JSON
        env:
          GH_TOKEN: ${{ secrets.RULESET_READ_TOKEN }}
        run: |
          if [ -z "$GH_TOKEN" ]; then
            echo "::error title=Ruleset drift::RULESET_READ_TOKEN is not set. Refusing to skip green — an unverifiable gate is indistinguishable from an absent one. Create a fine-grained PAT scoped to this repo with Administration: read (and nothing else) and add it as the RULESET_READ_TOKEN secret."
            exit 1
          fi
          bash scripts/rulesets/check-drift.sh

      - name: Alert on drift
        if: failure()
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          ALERT_ISSUE: ${{ vars.FACTORY_ALERT_ISSUE }}
        run: |
          if [ -z "$ALERT_ISSUE" ]; then
            echo "::error title=Alerting::FACTORY_ALERT_ISSUE is unset; the drift alert had nowhere to go."
            exit 1
          fi
          gh issue comment "$ALERT_ISSUE" --body \
            "@Muddl **Branch protection has drifted** from \`.github/rulesets/\`. Run: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
```

- [ ] **Step 6: Create the read-only credential**

Create a **fine-grained** personal access token at
`https://github.com/settings/personal-access-tokens/new`, scoped to **only** `Muddl/riot-api-mcp-server`, with repository permission **Administration: Read-only** and nothing else. Then:
```bash
gh secret set RULESET_READ_TOKEN --repo Muddl/riot-api-mcp-server
gh secret list --repo Muddl/riot-api-mcp-server
```
Expected: `RULESET_READ_TOKEN` listed. Verify it is genuinely read-only before trusting it:
```bash
GH_TOKEN=<the new token> gh api repos/Muddl/riot-api-mcp-server/rulesets --jq '.[].name'
GH_TOKEN=<the new token> gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/8769144 -f name="probe" 2>&1 | head -3
```
Expected: the first prints ruleset names; the second **fails with 403/404**. A token that can write is the wrong token — reissue it.

- [ ] **Step 7: Apply the rulesets, then reconcile the JSON with GitHub's echo**

Run:
```bash
bash scripts/rulesets/apply-rulesets.sh --dry-run
bash scripts/rulesets/apply-rulesets.sh
```
Expected: the dry run prints one `would PUT 8769144` line and one `would POST`/`would PUT` line for R2; the real run applies both and then executes `check-drift.sh`.

The drift check will very likely report a diff on the first run: GitHub echoes back default parameters this plan deliberately did not invent (for example `required_reviewers: []` on the `pull_request` rule, or additional `required_status_checks` defaults). **This is expected and is the reconciliation step, not a failure.** For each reported difference, copy the *live* side into the committed JSON — GitHub's echo is the authority on its own normalisation — and re-run:
```bash
bash scripts/rulesets/check-drift.sh
```
Expected, once reconciled:
```
ok   R1-all-branches.json (id 8769144) matches live
ok   R2-default-branch.json (id <new id>) matches live
```
Do not relax `check-drift.sh` to a subset comparison to make this pass. An exact diff is what makes a hand-edit detectable.

- [ ] **Step 8: Confirm the rollback command works, then restore**

Run (this is the exact command in `factory-kill-switch.md`, so it must be proven, not assumed):
```bash
gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/8769144 -f enforcement=disabled
gh api repos/Muddl/riot-api-mcp-server/rulesets/8769144 --jq .enforcement
bash scripts/rulesets/check-drift.sh; echo "drift-exit=$?"
gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/8769144 -f enforcement=active
gh api repos/Muddl/riot-api-mcp-server/rulesets/8769144 --jq .enforcement
```
Expected, in order: `disabled`; then the drift check reports a diff and `drift-exit=1` (proving the check notices a hand-edit — this is the Task 9 acceptance criterion rehearsed at a desk); then `active`; then a clean `check-drift.sh`.

- [ ] **Step 9: Confirm the applier refuses to run in CI**

Run: `GITHUB_ACTIONS=true bash scripts/rulesets/apply-rulesets.sh; echo "exit=$?"`
Expected:
```
REFUSING: this script must never run in GitHub Actions. See ADR-0019.
exit=1
```

- [ ] **Step 10: Lint the workflow and shell-check the scripts**

Run:
```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/ruleset-drift.yml')); print('ok')"
bash -n scripts/rulesets/apply-rulesets.sh && echo "apply syntax ok"
bash -n scripts/rulesets/check-drift.sh && echo "drift syntax ok"
jq -e . .github/rulesets/R1-all-branches.json >/dev/null && echo "R1 json ok"
jq -e . .github/rulesets/R2-default-branch.json >/dev/null && echo "R2 json ok"
```
Expected: `ok`, `apply syntax ok`, `drift syntax ok`, `R1 json ok`, `R2 json ok`.

- [ ] **Step 11: Commit**

```bash
git add .github/rulesets/R1-all-branches.json .github/rulesets/R2-default-branch.json .github/workflows/ruleset-drift.yml
git add --chmod=+x scripts/rulesets/apply-rulesets.sh scripts/rulesets/check-drift.sh
git commit -m "$(cat <<'EOF'
feat(ci): split ruleset 8769144 in two and verify it on a schedule

R1 (~ALL, ruleset 8769144 updated in place via PUT so its id stays stable):
deletion + non_fast_forward. non_fast_forward stays on every branch because F4
pushes revision rounds to live PR branches, where a force-push erases round
evidence and outdates inline comments.

R2 (~DEFAULT_BRANCH, new): pull_request{1 approval} +
required_status_checks["Build & verify"], the name confirmed from a real
check-runs response. R2 is created before R1 is narrowed so master is never
without an approval requirement.

Both rulesets keep the IDENTICAL admin-exempt bypass. GitHub documents rule
aggregation across rulesets but documents nothing about bypass evaluation when
several apply, and enforcement:"evaluate" (dry-run) is Enterprise-only, so the
tighter bypass_actors:[] variant is deferred to a throwaway probe repo.

The apply script refuses to run under GITHUB_ACTIONS: a workflow able to
rewrite rulesets could delete the approval requirement, which is strictly
stronger than approving its own work. Writes use the maintainer's own
credential locally; ruleset-drift.yml reads with a fine-grained PAT scoped to
Administration: read and fails loudly rather than skipping green.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap
EOF
)"
```

---

### Task 7: Close #71 — mask the derived auth header and tidy the comment

**Files:**
- Modify: `.github/workflows/housekeeping.yml` (the credential block, lines 98–120 in the pre-F0 file)

**Interfaces:**
- Consumes: `secrets.HOUSEKEEPING_TOKEN` (unchanged; F1 replaces it with a GitHub App).
- Produces: `AUTH_HEADER` registered as a masked value, and a credential comment that reads as two coherent paragraphs.

GitHub's automatic log redaction matches the **literal registered secret**, not transforms of it, so the base64 of `x-access-token:$GH_TOKEN` is not masked. Nothing currently prints it, so this is defence in depth — but `ACTIONS_STEP_DEBUG` traces would show it in the clear, and this step's credential handling has already broken in two non-obvious ways.

- [ ] **Step 1: Split the dangling comment fragment and mask the header**

In `.github/workflows/housekeeping.yml`, change:
```yaml
          # Restore a clean origin, then pass the PAT as a one-shot header on the push itself. An
          # embedded `https://x-access-token:$TOKEN@...` remote would work too, but persists the
          # token in .git/config for the rest of the job; `actions/checkout` avoids exactly that by
          # using an extraheader, and this step — which exists to route around a credential footgun
          # — should hold the same bar. The URL must be credential-free or its dead inline token
          # would still be preferred over the header.
          # GITHUB_SERVER_URL rather than a literal github.com, and the extraheader config key is
          # derived from the same value so host and key cannot drift apart.
          git remote set-url origin "${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}.git"
          AUTH_HEADER="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$GH_TOKEN" | base64 -w0)"
```
to:
```yaml
          # Restore a clean origin, then pass the PAT as a one-shot header on the push itself. An
          # embedded `https://x-access-token:$TOKEN@...` remote would work too, but persists the
          # token in .git/config for the rest of the job; `actions/checkout` avoids exactly that by
          # using an extraheader, and this step — which exists to route around a credential footgun
          # — should hold the same bar. The URL must be credential-free or its dead inline token
          # would still be preferred over the header.
          #
          # The host comes from GITHUB_SERVER_URL rather than a literal github.com, and the
          # extraheader config key below is derived from that same value, so the host and the
          # config key cannot drift apart.
          git remote set-url origin "${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}.git"

          # GitHub's automatic log redaction matches the LITERAL registered secret, not transforms
          # of it, so the base64 of "x-access-token:$GH_TOKEN" is NOT masked by default. Register
          # it explicitly: nothing here prints it today, but ACTIONS_STEP_DEBUG tracing would show
          # it in the clear. Defence in depth on a step whose credential handling has already
          # broken twice. (#71)
          AUTH_HEADER="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$GH_TOKEN" | base64 -w0)"
          echo "::add-mask::$AUTH_HEADER"
```

- [ ] **Step 2: Lint the YAML**

Run: `python -c "import yaml; yaml.safe_load(open('.github/workflows/housekeeping.yml')); print('ok')"`
Expected: `ok`

- [ ] **Step 3: Confirm the mask precedes every use of the value**

Run: `grep -n 'AUTH_HEADER\|add-mask' .github/workflows/housekeeping.yml`
Expected: three lines in this order — the `AUTH_HEADER=` assignment, then `echo "::add-mask::$AUTH_HEADER"`, then the `git -c http...extraheader="$AUTH_HEADER" push` line. A mask registered *after* a use protects nothing.

- [ ] **Step 4: Verify the masking behaviour without dispatching the workflow**

The workflow cannot be validated by dispatching from a branch: `claude-code-action` skips itself when the workflow file differs from the default branch, and the run goes **vacuously green** (`gotchas.md`). Replay the shell locally instead:
```bash
GH_TOKEN="fake-token-value" bash -c '
  AUTH_HEADER="AUTHORIZATION: basic $(printf "x-access-token:%s" "$GH_TOKEN" | base64 -w0)"
  echo "::add-mask::$AUTH_HEADER"
  echo "header-length=${#AUTH_HEADER}"'
```
Expected: an `::add-mask::` line followed by `header-length=<n>` with `n > 30`. This proves the command is syntactically valid and runs before any use; the actual redaction is an Actions-runner behaviour and is confirmed in Task 9 by reading a real run's log.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/housekeeping.yml
git commit -m "$(cat <<'EOF'
chore(ci): mask the derived AUTH_HEADER and split the credential comment

GitHub's log redaction matches the literal registered secret, not transforms of
it, so the base64 of x-access-token:$GH_TOKEN was unmasked. Register it with
::add-mask:: before first use. Nothing printed it, but ACTIONS_STEP_DEBUG would
have shown it in the clear.

Also breaks the GITHUB_SERVER_URL explanation out of the credential-handover
block, where it read as a dangling fragment.

Closes #71

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap
EOF
)"
```

---

### Task 8: Sweep the stale "post-merge" live-eval claim and persist F0

**Files:**
- Modify: `CLAUDE.md` (line ~41)
- Modify: `README.md` (lines ~58 and ~61)
- Modify: `CONTRIBUTING.md` (line ~48)
- Modify: `docs/knowledge/roadmap.md` (lines ~115, ~188, ~211, ~316, ~317, ~328, ~332)
- Modify: `docs/knowledge/gotchas.md` (lines ~157 and ~192; three new entries appended at the bottom)
- Modify (**amend, never edit**): `docs/knowledge/decisions/ADR-0012-live-eval-harness.md`
- Modify (**amend, never edit**): `docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md`
- Create: `docs/knowledge/decisions/ADR-0019-gate-hardening-and-ruleset-topology.md`
- Modify: `docs/knowledge/README.md` (ADR list — add ADR-0019)

**Interfaces:**
- Consumes: the real trigger block of `.github/workflows/live-eval.yml`, which is `workflow_dispatch:` only.
- Produces: no remaining prose outside immutable history that claims the live eval runs post-merge, plus the KB record of F0's decisions.

`live-eval.yml`'s `push` trigger was removed and never replaced. `gotchas.md` records three bug classes only the live eval has ever caught — boxed-vs-primitive DTO fields, `snake_case` keys, live-vs-documented field names — every one of which passed the offline WireMock suite. So the stale claim is not cosmetic: it advertises a safety net that is not deployed.

- [ ] **Step 1: Find every site**

Run:
```bash
grep -rn "post-merge\|post merge" --include='*.md' . \
  | grep -v '^\./docs/superpowers/' \
  | grep -v '/build/'
```
Expected: matches in `CLAUDE.md`, `README.md`, `CONTRIBUTING.md`, `docs/knowledge/roadmap.md` (several), `docs/knowledge/gotchas.md`, and the two ADRs, plus `docs/knowledge/decisions/ADR-0018-housekeeping-pr-review-gate.md`. `docs/superpowers/` is excluded because dated specs and plans are immutable history. Work from this list, not from this plan's line numbers — they will have shifted.

**ADR-0018's match is correct and must be left alone:** its "post-merge dispatch can verify the pass still runs" is about dispatching `housekeeping.yml` after a merge, not about the live eval.

- [ ] **Step 2: Fix `CLAUDE.md`**

Change:
```
The above is the offline CI gate and needs no keys. A separate **live eval harness** (`eval/`,
Python + mcp-eval) runs agent-driven tests against the real Riot API post-merge over both transports
```
to:
```
The above is the offline CI gate and needs no keys. A separate **live eval harness** (`eval/`,
Python + mcp-eval) runs agent-driven tests against the real Riot API over both transports,
**dispatched manually** (`live-eval.yml` is `workflow_dispatch:` only — it does **not** run on merge)
```

- [ ] **Step 3: Fix `README.md`**

Change:
```
Beyond the offline CI gate, a post-merge [live eval harness](eval/README.md) drives the server
against the real Riot API over stdio and sse using [mcp-eval](https://mcp-eval.ai/), verifying the
transports handshake, endpoint paths resolve, and Riot's error behaviours have not drifted. It runs
post-merge only and never blocks a merge.
```
to:
```
Beyond the offline CI gate, a [live eval harness](eval/README.md) drives the server against the
real Riot API over stdio and sse using [mcp-eval](https://mcp-eval.ai/), verifying the transports
handshake, endpoint paths resolve, and Riot's error behaviours have not drifted. It is **dispatched
on demand** (Actions tab or `gh workflow run live-eval.yml`) and never blocks a merge.
```

- [ ] **Step 4: Fix `CONTRIBUTING.md`**

Change:
```
drives the server against the real Riot API with [mcp-eval](https://mcp-eval.ai/). It runs post-merge
on CI and never blocks a merge. To run it locally you need `uv`, a personal `RIOT_API_KEY`, and an
```
to:
```
drives the server against the real Riot API with [mcp-eval](https://mcp-eval.ai/). It is dispatched
on demand on CI (`workflow_dispatch`, not on merge) and never blocks a merge. To run it locally you
need `uv`, a personal `RIOT_API_KEY`, and an
```

- [ ] **Step 5: Fix the five stale sites in `docs/knowledge/roadmap.md`**

Apply each of these in place:

1. Change `(continuously re-verified post-merge by the live eval harness once its` to `(continuously re-verified by dispatched runs of the live eval harness once its`.
2. Change `plus the post-merge live eval harness ([ADR-0012](decisions/ADR-0012-live-eval-harness.md))` to `plus the dispatch-only live eval harness ([ADR-0012](decisions/ADR-0012-live-eval-harness.md))`.
3. Change `Live agent-driven evals call every tool against the real Riot API post-merge; a wrong path returns 404 and fails the eval, so paths are verified continuously rather than by a human opening the portal.` to `Live agent-driven evals call every tool against the real Riot API on each dispatched run; a wrong path returns 404 and fails the eval, so paths are verified by running the harness rather than by a human opening the portal.`
4. Change `The suite runs over both stdio and sse each post-merge run; a successful stdio session is the stdout-purity check.` to `The suite runs over both stdio and sse each dispatched run; a successful stdio session is the stdout-purity check.`
5. Both standing-constraint italics read `this is automated post-merge by the live eval harness (`eval/`), over both transports; the offline suite remains the pre-merge gate.` — change **both** occurrences to `this is automated by the live eval harness (`eval/`), over both transports, on dispatch rather than on merge; the offline suite remains the pre-merge gate.`

- [ ] **Step 6: Close the F0 roadmap row**

In `docs/knowledge/roadmap.md`, replace the row:
```markdown
| **F0** | Harden the gate | ⏳ Not started | Ruleset surgery, the fork-PR fix that must precede any required check, bounds/kill-switch/alerting, #71, and the stale-doc sweep. Ships **no new agent behaviour**, so it is the only sub-project fully verifiable before it is depended on. |
```
with:
```markdown
| **F0** | Harden the gate | ✅ Shipped | [ADR-0019](decisions/ADR-0019-gate-hardening-and-ruleset-topology.md). Ruleset `8769144` split into R1 (`~ALL`) + R2 (`~DEFAULT_BRANCH`, `Build & verify` required); fork PRs unblocked first; timeouts, `FACTORY_ENABLED` kill switch, `@Muddl` failure alerts, local-only apply script and a daily drift check. #71 closed and the stale-doc sweep done. |
```

- [ ] **Step 7: Fix the two stale sites in `docs/knowledge/gotchas.md`**

1. Change `post-merge only (`.github/workflows/live-eval.yml`), and never blocks a merge.` to `on manual dispatch only (`.github/workflows/live-eval.yml` is `workflow_dispatch:`), and never blocks a merge.`
2. Change `of failure offline instead of waiting for the post-merge live eval.` to `of failure offline instead of waiting for someone to dispatch the live eval.`

- [ ] **Step 8: Append three new gotchas**

Append to the **bottom** of `docs/knowledge/gotchas.md` (newest last, per the file's own convention):

```markdown
## `GITHUB_TOKEN` is read-only on fork PRs regardless of the `permissions:` block

For a `pull_request` event from a **forked** repository, `GITHUB_TOKEN` is issued read-only. The
workflow's `permissions:` key can only *reduce* a token's scopes, never raise them above what the
event grants — so `checks: write` and `pull-requests: write` are silently ineffective there. Any
step that writes (a check run, a PR comment, a label) gets `403` and, without `continue-on-error`,
takes the whole job red. This made `Build & verify` fail on every outside contribution while nobody
noticed, because no outside contribution had arrived.

It matters far more once a job is a **required status check**: a reporter that cannot write then
makes fork PRs permanently unmergeable. Keep reporting steps `continue-on-error: true` (or gate them
on `github.event.pull_request.head.repo.fork == false`) so a job's conclusion reflects the build, not
the reporter. The reverse is never acceptable — never put `continue-on-error` on the build step
itself, which turns the required check into decoration.

## A *cancelled* check run never satisfies a required status check

`concurrency: cancel-in-progress: true` on a workflow that produces a required check is a trap. The
superseded run reports `conclusion: cancelled`, which is neither a success nor a pending state the
merge box waits on, so the PR sits blocked behind a grey check with no error anywhere to explain it.
`ci.yml` therefore uses `cancel-in-progress: false` (queue, never cancel) while
`claude-code-review.yml` — which produces no required check — uses `true`, where a superseded review
is pure waste. Decide this per workflow by asking one question: *is this job's name pinned in a
ruleset?*

## `.github/rulesets/` is not a GitHub path, and `enforcement: "evaluate"` is Enterprise-only

Committing ruleset JSON under `.github/rulesets/` configures nothing — GitHub reads no such path.
Without something that applies it and something that verifies it, the JSON is documentation cosplaying
as configuration, which is ADR-0018's exact failure shape: artifact present, property absent. This
repo applies it with `scripts/rulesets/apply-rulesets.sh` (local only — a workflow able to rewrite
rulesets could delete the approval requirement) and verifies it daily with `ruleset-drift.yml`, which
fails loudly rather than skipping green when its read-only credential is missing.

There is also no safe middle setting to rehearse a ruleset change in: `enforcement: "evaluate"`, the
dry-run mode, is **"only available with GitHub Enterprise"** and does not exist on a free personal
repo. Rulesets are `active` or `disabled`. Rehearse risky bypass topologies on a throwaway probe
repo, never on this one — and note that GitHub documents rule *aggregation* across rulesets ("the most
restrictive version of the rule applies") while documenting **nothing** about how *bypass* is
evaluated when several rulesets match a ref. That silence is why R1 and R2 carry identical bypass.
```

- [ ] **Step 9: Amend ADR-0012 (do not edit its body)**

In `docs/knowledge/decisions/ADR-0012-live-eval-harness.md`, change:
```markdown
# ADR-0012: Live agent-driven eval harness (mcp-eval)

- **Status:** Accepted
- **Date:** 2026-07-17

## Context
```
to:
```markdown
# ADR-0012: Live agent-driven eval harness (mcp-eval)

- **Status:** Accepted (amended 2026-08-01)
- **Date:** 2026-07-17

> **Amendment (2026-08-01):** the Decision's opening line — "run post-merge on CI" — and the
> Consequences' "automated post-merge" were never true of the shipped workflow, and contradict this
> ADR's own "On-demand, non-blocking" bullet below, which is the accurate one.
> `.github/workflows/live-eval.yml` is `workflow_dispatch:` only; the `push` trigger was removed and
> never replaced. **Merges get no live coverage unless someone dispatches the workflow by hand** —
> which matters because `gotchas.md` records three bug classes only the live eval has ever caught,
> every one of which passed the offline suite. Scheduling it was considered and rejected in the
> [software-factory decomposition spec](../../superpowers/specs/2026-08-01-software-factory-decomposition-design.md):
> the preflight *skips green* on a 401/403 and Riot dev keys expire every 24 hours, so a weekly cron
> would yield one real run followed by green no-ops — reintroducing "silent failure that looks like
> success" as the mitigation for it. The coverage gap during unattended weeks is recorded honestly
> instead. Swept from all prose by F0 ([ADR-0019](ADR-0019-gate-hardening-and-ruleset-topology.md)).

## Context
```

- [ ] **Step 10: Amend ADR-0017 (do not edit its body)**

In `docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md`, change:
```markdown
# ADR-0017: Transport-scoped live eval coverage

- **Status:** Accepted
- **Date:** 2026-07-23

## Context
```
to:
```markdown
# ADR-0017: Transport-scoped live eval coverage

- **Status:** Accepted (amended 2026-08-01)
- **Date:** 2026-07-23

> **Amendment (2026-08-01):** "Relationship to ADR-0012" below describes the harness's purpose as
> "agent-driven, live-Riot, post-merge, non-gating". Read **dispatch-only** for "post-merge":
> `live-eval.yml` is `workflow_dispatch:` only and has never run on merge. Nothing else in this ADR
> changes — the coverage narrowing it decides is independent of the trigger. See
> [ADR-0012](ADR-0012-live-eval-harness.md)'s amendment and
> [ADR-0019](ADR-0019-gate-hardening-and-ruleset-topology.md).

## Context
```

- [ ] **Step 11: Write ADR-0019**

Create `docs/knowledge/decisions/ADR-0019-gate-hardening-and-ruleset-topology.md` with exactly this content:

```markdown
# ADR-0019 — Gate hardening: ruleset topology, local-only application, and factory bounds

- **Status:** Accepted
- **Date:** 2026-08-01

## Context

Repository ruleset `8769144` ("one ruleset to rule them all") had been active since 2025-10-09
carrying `deletion`, `non_fast_forward`, and `pull_request{required_approving_review_count: 1}` —
with **no required status checks at all** and with
`{"actor_type": "RepositoryRole", "actor_id": 5, "bypass_mode": "exempt"}`. `exempt` is stronger than
`always`: rules are not evaluated for that actor and no bypass audit entry is written. So the one
human was invisibly exempt from the only rule that existed, nothing required `Build & verify` to be
green before merge, and the merge button was live on a red PR. Its `ref_name.include` was `~ALL`
rather than the default branch, so a hypothetical agent branch was governed by the same
PR-required rule as `master` — unreached only because the maintainer is exempt and `housekeeping.yml`
performs a single branch *creation*.

Separately, `ci.yml` could not pass on a fork PR at all: `GITHUB_TOKEN` is read-only for
`pull_request` from a fork regardless of `permissions:`, and both reporting steps wrote through it
with no `continue-on-error`. And no agent job had a timeout, a kill switch, or a failure alert.

## Decision

- **Fork PRs are fixed before any check becomes required.** Making `Build & verify` required while it
  failed on every outside contribution would permanently block contributions to a public portfolio
  repo. Reporting steps are non-fatal; the build step never is.
- **Two rulesets, identical bypass.** R1 (`~ALL`) keeps `deletion` + `non_fast_forward` —
  `non_fast_forward` stays repo-wide because F4 pushes revision rounds to live PR branches, where a
  force-push erases round evidence and outdates every inline comment. R2 (`~DEFAULT_BRANCH`) carries
  `pull_request{1 approval}` + `required_status_checks["Build & verify"]`. `8769144` is **updated in
  place (PUT)** into R1 so its id — and therefore the rollback command — stays stable; R2 is created
  first so `master` is never momentarily without an approval requirement.
- **Bypass stays identical on both.** GitHub documents that rules across rulesets are aggregated with
  "the most restrictive version of the rule applies" but documents **nothing** about how bypass is
  evaluated when several rulesets match a ref, and `enforcement: "evaluate"` (dry-run) is
  GitHub-Enterprise-only. An undocumented behaviour, no dry-run, and a sole maintainer who cannot
  approve his own PRs is the combination not to experiment on. The tighter `bypass_actors: []`
  variant is deferred, gated on testing layered bypass on a **throwaway probe repo**.
- **Rulesets are applied locally and verified by CI, never applied by CI.** A workflow able to
  rewrite rulesets could delete the approval requirement, which is strictly stronger than approving
  its own work. Writes use the maintainer's own credential (`administration: write`) from
  `scripts/rulesets/apply-rulesets.sh`, which refuses to run under `GITHUB_ACTIONS`. `ruleset-drift.yml`
  reads with a fine-grained PAT scoped to **Administration: read** and nothing else, and fails loudly
  when that secret is absent rather than skipping green.
- **Bounds and control.** `timeout-minutes` on every job running `claude-code-action` (the action
  exposes no timeout of its own). `cancel-in-progress: false` on `ci.yml` — a cancelled check run
  never satisfies a required check — and `true` on `claude-code-review.yml`. Every agent job gates on
  `vars.FACTORY_ENABLED == 'true'`; `ci.yml` and `ruleset-drift.yml` deliberately do not. Failures
  post an `@Muddl` mention, the only channel GitHub Mobile pushes for `issues`- and
  `pull_request`-triggered runs.

## Consequences

- **Machine identities cannot merge red.** That is the property this buys, and it is bounded: the
  maintainer remains `exempt` and can still merge red. This residual risk is **stated, not solved**;
  closing it for the human is a post-trip item.
- Branch protection now has a committed, diffable definition, and a hand-edit in the GitHub UI turns a
  daily workflow red within 24 hours instead of going unnoticed indefinitely.
- Three phone-reachable rungs exist for stopping the factory
  ([pattern guide](../patterns/factory-kill-switch.md)), and a ruleset lock-out is undone by one
  copy-pasteable `gh api --method PUT ... -f enforcement=disabled`.
- Every workflow file touched here contains `claude-code-action` or is adjacent to it, so the PR that
  ships this receives **no Claude review** — the action refuses to run when the workflow file differs
  from the default-branch copy and records that as an annotation on a *successful* job.
- The live-eval "post-merge" claim is corrected everywhere outside immutable history; ADR-0012 and
  ADR-0017 are amended rather than edited, per the knowledge-base reversal rule.
```

- [ ] **Step 12: Link ADR-0019 from the KB README**

In `docs/knowledge/README.md`, immediately after the `ADR-0018` list item, add:
```markdown
- [ADR-0019 — Gate hardening: ruleset topology, local-only application, and factory bounds](decisions/ADR-0019-gate-hardening-and-ruleset-topology.md)
```

- [ ] **Step 13: Verify the sweep is complete and the KB is intact**

Run:
```bash
grep -rn "post-merge\|post merge" --include='*.md' . \
  | grep -v '^\./docs/superpowers/' \
  | grep -v '/build/' \
  | grep -v 'ADR-0018' \
  | grep -v '^\./docs/knowledge/decisions/ADR-001[27].*Amendment'
```
Expected: **no output**, apart from the amendment blockquotes in ADR-0012/ADR-0017 which quote the wrong phrasing in order to correct it. Any other hit is a site that was missed.

Run:
```bash
comm -23 <(ls docs/knowledge/decisions/ADR-*.md | xargs -n1 basename | sort) <(grep -oE 'decisions/ADR-[0-9]{4}[^)]*\.md' docs/knowledge/README.md | sed 's|decisions/||' | sort -u)
```
Expected: empty output — every ADR on disk, including ADR-0019, is linked from the KB README.

Run: `grep -c "workflow_dispatch" .github/workflows/live-eval.yml && grep -c "push:" .github/workflows/live-eval.yml`
Expected: `1` then `0` — confirming the corrected prose matches the actual trigger, i.e. the sweep asserted a fact rather than a belief.

- [ ] **Step 14: Commit**

```bash
git add CLAUDE.md README.md CONTRIBUTING.md docs/knowledge/roadmap.md docs/knowledge/gotchas.md docs/knowledge/README.md docs/knowledge/decisions/ADR-0012-live-eval-harness.md docs/knowledge/decisions/ADR-0017-transport-scoped-live-eval.md docs/knowledge/decisions/ADR-0019-gate-hardening-and-ruleset-topology.md
git commit -m "$(cat <<'EOF'
docs(kb): ADR-0019 gate hardening; sweep the stale post-merge live-eval claim

live-eval.yml is workflow_dispatch: only — the push trigger was removed and
never replaced — yet CLAUDE.md, README.md, CONTRIBUTING.md, roadmap.md,
gotchas.md and two accepted ADRs advertised post-merge coverage. That is a
safety net readers believed was deployed while three bug classes only the live
eval has ever caught went uncovered on merge.

ADR-0012 and ADR-0017 are AMENDED with dated blockquotes, never edited, per the
knowledge-base reversal rule. docs/superpowers/ is untouched (immutable
history). ADR-0019 records F0's decisions; the F0 roadmap row is closed.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap
EOF
)"
```

---

### Task 9: Verify the gate in production

**Files:** none (integration/verification task)

**Interfaces:**
- Consumes: every prior task, on branch `agent/f0-harden-the-gate`, with the rulesets already applied live in Task 6.
- Produces: falsifiable evidence for each of issue #73's acceptance criteria. **None of these is satisfied by a green Actions run** — three green-but-did-nothing incidents are on record in this repo.

- [ ] **Step 1: Confirm the offline gate still passes**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. No Java changed, but a red build here would mean the required check cannot go green and the PR is unmergeable by design.

- [ ] **Step 2: Push and open the PR, stating the no-review expectation in the body**

```bash
git push -u origin agent/f0-harden-the-gate
gh pr create --base master --head agent/f0-harden-the-gate \
  --title "F0: harden the gate (fork-PR fix, ruleset split, kill switch, alerting)" \
  --body "$(cat <<'EOF'
Implements #73 — sub-project F0 of the software factory. Closes #71.
Spec: `docs/superpowers/specs/2026-08-01-software-factory-decomposition-design.md` §1.
Plan: `docs/superpowers/plans/2026-08-01-factory-f0-harden-the-gate.md`.
Decision record: ADR-0019.

**Expect no Claude review on this PR — this is correct, not a failure.**
`claude-code-action` hard-requires the workflow file to be byte-identical to the copy on the
default branch. This PR edits `claude-code-review.yml` and `claude.yml`, so the action **skips
itself** with `Skipping action due to workflow validation`, recorded as an *annotation on a
successful job*. Do not read the missing review as the reviewer silently failing; see the
matching entry in `docs/knowledge/gotchas.md`. The reviewer resumes on the next PR, once these
files are on `master`.

**Live configuration already applied.** The rulesets were applied from a desk in Task 6 (locally,
never from CI). `master` now requires 1 approval and a green `Build & verify`. The maintainer
remains `exempt` — a stated, unsolved residual risk; machine identities cannot merge red, which is
the property F0 buys.

Rollback, executable from a phone:
`gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/8769144 -f enforcement=disabled`
EOF
)"
```

- [ ] **Step 3: Confirm `Build & verify` reports on this PR under the exact required name**

Run:
```bash
gh pr checks --watch
gh api "repos/Muddl/riot-api-mcp-server/commits/$(git rev-parse HEAD)/check-runs" \
  --jq '.check_runs[] | "\(.name) | \(.conclusion)"'
```
**Falsifiable criterion:** a line reading exactly `Build & verify | success`. If the name differs by a byte from `.github/rulesets/R2-default-branch.json`, the required check will never be satisfied and every PR is blocked — fix the JSON, re-apply, and re-run `check-drift.sh` before merging anything.

- [ ] **Step 4: Confirm a red PR cannot be merged by a non-exempt identity**

The maintainer is `exempt`, so *his* merge button proves nothing. Test the property that matters — that the rule is evaluated at all — by inspecting the merge state on a deliberately-red PR:
```bash
git checkout -b probe/red-build
printf '\nclass Boom { void x() { syntax error } }\n' >> lol-mcp-server/src/test/java/PROBE.java
git add -A && git commit -m "probe: deliberately break the build (do not merge)"
git push -u origin probe/red-build
gh pr create --base master --head probe/red-build --title "probe: red build (do not merge)" --body "Throwaway. Verifies the required status check blocks merge."
gh pr checks --watch || true
gh pr view --json mergeable,mergeStateStatus,statusCheckRollup \
  -q '{mergeable, state: .mergeStateStatus, checks: [.statusCheckRollup[] | {name, conclusion}]}'
```
**Falsifiable criterion:** `mergeStateStatus` is `BLOCKED` (not `CLEAN`, not `UNSTABLE`) and `Build & verify` shows `FAILURE`. `UNSTABLE` means checks are failing but nothing is *requiring* them — i.e. R2 did not take effect, and Task 6 must be re-run.

Then clean up: `gh pr close probe/red-build --delete-branch` and `git checkout agent/f0-harden-the-gate`.

- [ ] **Step 5: Confirm the drift check fails on a hand-edited ruleset**

From a browser (this deliberately exercises the UI path, which is how real drift happens):
`https://github.com/Muddl/riot-api-mcp-server/settings/rules` → **R2 — default branch: PR approval +
green build** → change *Required approvals* from 1 to 0 → Save. Then:
```bash
gh workflow run ruleset-drift.yml
sleep 30 && gh run list --workflow=ruleset-drift.yml --limit 1
gh run view --log-failed --workflow=ruleset-drift.yml | head -40
```
**Falsifiable criterion:** the run's conclusion is `failure`, the log contains
`::error title=Ruleset drift::`, a unified diff showing `required_approving_review_count` 1 → 0, and
an `@Muddl` comment lands on `vars.FACTORY_ALERT_ISSUE`. A *green* run here means the comparator is
normalising away the very field that changed — do not accept it.

Restore: `bash scripts/rulesets/apply-rulesets.sh`, then re-run the workflow and confirm it goes
green. A drift check that stays red after reconciliation is as useless as one that never goes red.

- [ ] **Step 6: Exercise the kill-switch ladder from the actual phone**

Do this **on the phone**, not on the laptop — the criterion is that the ladder is reachable without a
desk, and that is only proven by reaching it without one. Follow
`docs/knowledge/patterns/factory-kill-switch.md`.

1. **Rung 1.** Phone browser → `/settings/variables/actions` → set `FACTORY_ENABLED` to `false`.
   Post a comment containing `@claude ping` on any open issue.
   **Falsifiable criterion:** the `Claude Code` workflow run for that comment shows the `claude` job
   as **`skipped`**, not `success`, and no Claude reply appears. (A `success` conclusion with no
   reply is the failure this criterion exists to distinguish.)
2. **Rung 2.** Phone browser → Actions tab → `Weekly Housekeeping` → `···` → **Disable workflow**.
   **Falsifiable criterion:** the workflow is listed as disabled and `gh workflow list` (later, from
   a desk) shows `disabled_manually`. Re-enable afterwards.
3. **Rung 3 — do not execute; verify reachability only.** Phone browser →
   `/settings/secrets/actions`.
   **Falsifiable criterion:** `CLAUDE_CODE_OAUTH_TOKEN` is visible with a working Remove control on
   the phone viewport. Actually revoking it would require a desk to restore, so reachability is the
   criterion; the *effect* (every agent step fails auth) is not in doubt.
4. **Ruleset rollback.** From the phone browser: `/settings/rules` → R1 → Enforcement → Disabled →
   Save → then back to Active.
   **Falsifiable criterion:** both transitions complete on the phone viewport, and a subsequent
   `ruleset-drift.yml` dispatch is green.

Finally, restore rung 1: set `FACTORY_ENABLED` back to `true` and confirm a fresh `@claude` mention
produces a job that **runs** rather than skips. An un-restored kill switch is a silently dead factory.

- [ ] **Step 7: Confirm the fork-PR fix**

Preferred, if a fork is available: push a trivial docs-only branch to a fork of this repository and
open a PR from it.
**Falsifiable criterion:** `Build & verify` reaches `success`; the run log shows the `Publish
coverage summary` step as **skipped** (the `fork == false` guard) and, if `Publish test results`
errored, the step is annotated with a warning while the **job** conclusion stays `success` (the
`continue-on-error` path). A red `Build & verify` here means an outside contributor cannot merge and
the required check must be removed until it is fixed.

If no fork is available, this cannot be tested on this repository at all — a `pull_request` from the
same repo issues a *writable* `GITHUB_TOKEN`, so the fork code path is never exercised, and no
`workflow_dispatch` can simulate it. In that case record explicitly:

- **What was reasoned, not tested:** the guard `github.event.pull_request.head.repo.fork == false`
  evaluates false only on fork PRs, so the coverage step's behaviour on same-repo PRs is unchanged
  (confirmed by Step 3, where the step ran); and `continue-on-error: true` is a runner-level
  behaviour independent of which API returned the 403.
- **What to watch for:** the first PR from any outside contributor. If `Build & verify` goes red on
  it, disable R2's required-check rule immediately
  (`gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/<R2-id> -f enforcement=disabled`)
  rather than leaving a contributor blocked, and reopen #73.

- [ ] **Step 8: Confirm the `::add-mask::` took effect in a real run**

Run: `gh run view --log --workflow=housekeeping.yml --job "$(gh run list --workflow=housekeeping.yml --limit 1 --json databaseId --jq '.[0].databaseId')" 2>/dev/null | grep -c 'AUTHORIZATION: basic'`
Expected: `0` — the header value never appears in a log. (This is only meaningful **after** the F0 PR
merges, because `housekeeping.yml` runs the masked code only from `master`. Until then the mask is
verified by the local replay in Task 7 Step 4.)

- [ ] **Step 9: Merge, then confirm the gate held on the way through**

Approve and merge the F0 PR. Then:
```bash
bash scripts/rulesets/check-drift.sh
gh issue view 73 --json state -q .state
gh issue view 71 --json state -q .state
```
Expected: a clean drift check, and both issues `CLOSED` (#71 by the commit trailer in Task 7, #73 by
the PR body). If #73 did not auto-close, close it manually with a comment linking the four
acceptance-criterion outcomes from Steps 4–7 — the criteria are the record, not the merge.

---

## Self-Review

**Spec coverage** (spec §1, "F0 — Harden the gate"):
- "Fork PRs must be fixed before any required check exists… **This is the first task in the plan, not a footnote**" → **Task 1**, with `continue-on-error` on both reporters plus a `fork == false` guard on the coverage comment. ✅
- Confirm the check-run name before pinning it (issue #73 item 2) → **Task 2**, run against a real `check-runs` response; observed `Build & verify`. ✅
- "Ruleset surgery, as two rulesets with identical bypass" — R1 `~ALL` `deletion`+`non_fast_forward`, R2 `~DEFAULT_BRANCH` `pull_request{1}`+`required_status_checks[Build & verify]` → **Task 6** Steps 1–2, with `8769144` updated in place via PUT (stated explicitly) and R2 created first. ✅
- "Both keep the identical admin-`exempt` bypass"; `bypass_actors: []` deferred; `enforcement: "evaluate"` is Enterprise-only → **Task 6** preamble, ADR-0019, and `factory-kill-switch.md`. No JSON contains `evaluate`. ✅
- "The residual risk is stated, not solved" → **Task 6** preamble, ADR-0019 Consequences, PR body (Task 9 Step 2). ✅
- "Configuration must be applied, or it is not configuration" — local-only apply script, scheduled drift check → **Task 6** Steps 3–5, with a hard `GITHUB_ACTIONS` refusal proven in Step 9 and a loud-failure path when the read credential is absent. ✅
- "Bounds and control" — `timeout-minutes` on every agent job; `cancel-in-progress: false` on `ci.yml`, `true` on `claude-code-review.yml` → **Task 3**, with a grep assertion that every `claude-code-action` file is bounded. ✅
- "A kill switch and an alert path" — `vars.FACTORY_ENABLED == 'true'`, three-rung ladder, `if: failure()` → `gh issue comment` mentioning `@Muddl` → **Tasks 4 and 5**. ✅
- "**The plan must require testing the ladder from the actual phone before departure**" → **Task 9 Step 6**, four sub-criteria, each falsifiable, explicitly phone-only. ✅
- "Two corrections carried along" — #71's `::add-mask::` + comment tidy; the stale "post-merge" sweep with ADR-0012/0017 **amended, not edited** → **Tasks 7 and 8**, amendments in ADR-0015's blockquote style, `docs/superpowers/` excluded from the grep. ✅
- "Editing `claude-code-review.yml` means that PR will not be reviewed by Claude… must be stated in the PR" → Global Constraints, ADR-0019 Consequences, and verbatim in the PR body in **Task 9 Step 2**. ✅
- Rollback is a copy-pasteable `gh api --method PUT … -f enforcement=disabled`, phone-executable → Global Constraints, `factory-kill-switch.md`, PR body, and **proven** in **Task 6 Step 8**. ✅
- "Ruleset writes need `administration: write` — say which credential and where" → writes: maintainer's own `gh` credential, locally, `apply-rulesets.sh`; reads: `RULESET_READ_TOKEN`, fine-grained, **Administration: read only**, verified non-writable in **Task 6 Step 6**. ✅
- "A green Actions run is not proof of success" → Global Constraints; every Task 9 criterion asserts an effect (`mergeStateStatus: BLOCKED`, `skipped` not `success`, a red drift run with a diff), never an exit status. ✅

**Placeholder scan:** No TBD/TODO/FIXME. Three values are resolved at execution time and each has an exact derivation command rather than a guess: the alert issue number (**Task 5 Step 1**, `gh issue create` then `gh variable set`), R2's ruleset id (resolved by *name* in both scripts, so it is never hardcoded — and the one place it appears in prose, the rollback for R2, uses a `--jq` lookup), and GitHub's echoed ruleset defaults (**Task 6 Step 7**, reconciled from the live response rather than invented here). `integration_id` is deliberately omitted with the reason and the derivation command stated in **Task 2 Step 3**. `8769144` is a real, verified id; `actor_id: 5` / `RepositoryRole` / the `refs/heads/dependabot/*` exclude are copied verbatim from the live ruleset.

**Type/name consistency:** `Build & verify` appears identically in `ci.yml` (job `name:`), `R2-default-branch.json` (`context`), Task 2's confirmation, and Task 9's criteria — one string, byte-for-byte, ampersand included. `vars.FACTORY_ENABLED` is compared to the string `'true'` in all four places it appears (three workflows plus the pattern guide), never to a boolean. `vars.FACTORY_ALERT_ISSUE` is used identically in `housekeeping.yml` and `ruleset-drift.yml`, with the same unset-guard in both. Ruleset file names (`R1-all-branches.json`, `R2-default-branch.json`) and the `name` fields inside them match the lookups in `apply-rulesets.sh` and `check-drift.sh`, which share one `STRIP` projection so local and CI comparisons cannot diverge. Branch `agent/f0-harden-the-gate` is used consistently in Tasks 9 Steps 2, 4, and the Global Constraints. ADR-0019's filename, its KB README link, the roadmap F0 row, and the in-script `See ADR-0019` reference all agree.
