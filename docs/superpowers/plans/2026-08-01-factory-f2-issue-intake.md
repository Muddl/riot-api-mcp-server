# Factory F2 — Issue intake, two-phase (`agent:plan` → `agent:go`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the GitHub issue list this repo's work queue. A `agent:plan` label makes an agent hydrate the knowledge base and commit *one* implementation plan to a branch; the maintainer reads it on a phone and may edit it through GitHub's web editor; a `agent:go` label makes an agent implement *that exact file* and open a PR that both `ci.yml` and `claude-code-review.yml` actually run on.

**Architecture:** One workflow file, `.github/workflows/agent-intake.yml`, carrying two jobs on `issues: types: [labeled]`, fanned out on `github.event.label.name` because `label_trigger` takes a single string. Both jobs use `track_progress: true` so the action forces tag mode — restoring GitHub context, the tracking comment, and branch handling — while keeping a custom `prompt`. Every property that matters is enforced by a **non-agent** step: a pre-flight resolves the approved SHA and plan path from git (never from a model-authored comment), and a post-guard runs `git diff --exit-code <approved-sha> -- <plan-path>`. Instructing a model not to re-plan is not a mechanism. `agent:go` opens its PR from *inside* the action step so the App installation token authors it.

**Tech Stack:** GitHub Actions, `anthropics/claude-code-action@v1`, GitHub issue forms (YAML), `gh` CLI, Bash. No application (Java) code changes.

## Global Constraints

- **Depends on F0** ([`2026-08-01-factory-f0-harden-the-gate.md`](2026-08-01-factory-f0-harden-the-gate.md)). Two hard dependencies, not soft ones:
  - `vars.FACTORY_ENABLED` is introduced by F0. Until it exists and is set to `'true'`, every job here evaluates its `if:` to false and **never runs**. That is the intended fail-closed state, not a bug.
  - F0's ruleset surgery moves `pull_request{1 approval}` off `~ALL` onto `~DEFAULT_BRANCH`. Until it does, the first **second** push to an existing `agent/issue-<N>` branch is rejected by a PR-required rule on a non-default branch — which is exactly what `agent:go` does after `agent:plan` created it. Do not canary F2 before F0 has merged and been applied.
- **A green Actions run is not proof of success.** Three green-but-did-nothing incidents are on record in `gotchas.md`. Every guard in this plan asserts an *effect* — a branch exists on the remote at a known SHA, a PR number exists, a diff is empty — never a job's exit status. The canary (Task 6) is falsifiable or it is not done.
- **Automation may propose but never approve.** No `gh pr merge`, no `gh pr review`, and no Workflows permission appears anywhere in this plan's `--allowedTools`.
- **Two credential buckets, kept separate** (ADR-0012): everything here runs on `CLAUDE_CODE_OAUTH_TOKEN` (flat-rate seat). `ANTHROPIC_API_KEY` stays scoped to `live-eval.yml`.
- **Editing `claude-code-review.yml` means the PR carrying this work will not be reviewed by Claude.** The action hard-requires its workflow file to be byte-identical to the default-branch copy and records the mismatch as an *annotation on a successful job* (`gotchas.md`). Expected. Say so in the PR body so it is not later misread as the reviewer silently failing.
- **Adding a new workflow that contains `claude-code-action` cannot be validated from a branch** for the same reason — the file does not exist on `master` yet, so the action skips itself and the job goes green having done nothing. Only the *non-agent* steps are branch-testable (see Task 3, Step 4). Supplying `github_token:` to bypass the OIDC exchange is the documented escape hatch and is **not** used here: it would author the PR with `GITHUB_TOKEN`, re-triggering the recursion guard this design exists to avoid.
- Commit messages end with the repo's trailers:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap`
- All work lands on a branch off `master` (suggested: `feat/factory-f2-issue-intake`), opened as a PR referencing issue **#75**.

---

## File Structure

| File | Responsibility |
|---|---|
| *(GitHub labels — no file)* | **New.** `agent:plan` and `agent:go`: the state machine. |
| `.github/ISSUE_TEMPLATE/agent-task.yml` | **New.** Issue form for an agent-workable ticket. None exists today. |
| `.github/ISSUE_TEMPLATE/config.yml` | **New.** Keep blank issues available; point elsewhere for non-tickets. |
| `.github/workflows/agent-intake.yml` | **New.** Two jobs, `plan` and `go`, plus every non-agent guard. |
| `.github/workflows/claude-code-review.yml` | **Modify.** `allowed_bots: 'dependabot'` → `'dependabot,claude'`, in the same commit as the workflow. |
| `docs/knowledge/roadmap.md` | **Modify (persist).** Redefine plan immutability (2 sites); close the F2 row. |
| `docs/knowledge/README.md` | **Modify (persist).** Redefine plan immutability; link the new pattern. |
| `.claude/skills/housekeeping/SKILL.md` | **Modify.** Reconcile its hands-off rule with the new definition. |
| `docs/knowledge/patterns/agent-issue-intake.md` | **New (persist).** The how-to for driving the two-phase flow. |

---

### Task 1: Create the two state-machine labels

**Files:**
- None (GitHub repository configuration, applied by the maintainer with `gh`).

**Interfaces:**
- Produces: labels `agent:plan` and `agent:go`, consumed by `agent-intake.yml`'s `if:` expressions and `label_trigger` inputs (Task 3) and by the issue template's guidance text (Task 2).

The label names are the public contract of this workflow, in the same sense that `@McpTool` names are the public MCP contract. Once a label is in an `if:` expression and a `label_trigger`, renaming it silently stops the state machine — the job's `if:` goes false and the run does not appear at all. Create them exactly as written.

- [ ] **Step 1: Confirm neither label already exists**

Run:
```bash
gh label list --limit 100 | grep -E '^agent:' || echo "none"
```
Expected: `none`. (Verified at plan time — the repo carries only the nine GitHub defaults plus `dependencies` and `java`.)

- [ ] **Step 2: Create the labels**

Run exactly:
```bash
gh label create "agent:plan" \
  --color "1D76DB" \
  --description "Phase 1 — agent hydrates the KB and commits an implementation plan to a branch"

gh label create "agent:go" \
  --color "0E8A16" \
  --description "Phase 2 — agent implements the approved plan at the branch tip and opens a PR"
```

- [ ] **Step 3: Verify**

Run:
```bash
gh label list --limit 100 | grep -E '^agent:'
```
Expected: exactly two lines, `agent:go` and `agent:plan`, with the descriptions above.

- [ ] **Step 4: Commit**

Nothing to commit — labels are repository state, not files. This is deliberate and is the same class of gap F0 names for rulesets: **configuration that is not applied is not configuration.** The compensating control is Task 3's `if:` expression, which simply never matches an absent label, so a missing label fails closed rather than silently running the wrong phase.

---

### Task 2: Add issue templates for agent-workable tickets

**Files:**
- Create: `.github/ISSUE_TEMPLATE/agent-task.yml`
- Create: `.github/ISSUE_TEMPLATE/config.yml`

**Interfaces:**
- Produces: an "Agent-workable task" issue form. The body it produces is the *entire* input to the `agent:plan` prompt, which is why the form asks for an outcome and a falsifiable acceptance criterion rather than a free-text wish. The roadmap already names spec quality as the control surface: vague inputs yield confident, wrong output.
- Consumes: the labels from Task 1 (referenced in guidance text only — deliberately **not** auto-applied; see Step 1).

- [ ] **Step 1: Write the agent-task issue form**

Create `.github/ISSUE_TEMPLATE/agent-task.yml` with exactly this content:

```yaml
name: Agent-workable task
description: A task an agent can plan and then implement. Apply the agent:plan label when the ticket reads well.
title: "<area>: <imperative summary>"
# `labels:` is deliberately EMPTY. GitHub fires an `issues: labeled` event for labels applied at
# creation time, so presetting `agent:plan` here would start a planning run the instant the form is
# submitted — before any human had read the ticket back. The whole premise of two-phase intake is
# that a human gates each transition. Apply the label by hand, afterwards.
labels: []
body:
  - type: markdown
    attributes:
      value: |
        This form's output is the **entire input** to the planning agent. Everything it does not say,
        the agent will invent. Be concrete.

        **How this ticket moves:**
        1. Submit it. Read it back. Edit it until it is unambiguous.
        2. Apply **`agent:plan`** — an agent hydrates `docs/knowledge/` and commits one plan file to
           `agent/issue-<N>`, then comments with the path and commit SHA.
        3. Read the plan (a phone is good at this). Edit it in GitHub's web editor if needed; each
           edit produces a new commit on that branch, and the newest one is what gets implemented.
        4. Apply **`agent:go`** — the agent implements that file and opens a PR.

  - type: textarea
    id: outcome
    attributes:
      label: Outcome
      description: What is true after this ships that is not true now? One or two sentences.
      placeholder: "`lol_match_timeline` returns per-minute gold deltas instead of raw frame blobs."
    validations:
      required: true

  - type: textarea
    id: acceptance
    attributes:
      label: Falsifiable acceptance criterion
      description: >-
        A check that could fail. "It works" is not one. Prefer a command and its expected output.
      placeholder: |
        Run: ./gradlew :lol-mcp-server:test --tests '*MatchTimelineServiceTest*'
        Expected: BUILD SUCCESSFUL, including a new zero-frames case.
    validations:
      required: true

  - type: dropdown
    id: blast-radius
    attributes:
      label: Blast radius
      description: >-
        Where the change lands. Docs and KB need less ceremony than a library or the tool contract.
        F5 will turn this into an enforced tier; today it is context for the planner.
      options:
        - Docs / knowledge base only
        - Server module code (lol-mcp-server, tft-mcp-server)
        - Libraries, MCP tool contract, or workflows
    validations:
      required: true

  - type: textarea
    id: constraints
    attributes:
      label: Constraints and non-goals
      description: >-
        Anything the agent must not do, and anything adjacent that is explicitly out of scope.
        The offline suite must stay key-free and network-free; say so again here if it is at risk.
      placeholder: "Do not change existing @McpTool names or @McpToolParam descriptions."
    validations:
      required: false

  - type: textarea
    id: context
    attributes:
      label: Relevant prior art
      description: >-
        ADRs, patterns, gotchas, files, or issues the planner should read first. The agent hydrates
        the knowledge base anyway; this points it at the right corner of it.
      placeholder: "docs/knowledge/decisions/ADR-0016-bounded-list-results.md; the ordering gotcha."
    validations:
      required: false
```

- [ ] **Step 2: Write the template chooser config**

Create `.github/ISSUE_TEMPLATE/config.yml` with exactly this content:

```yaml
# Blank issues stay available. Not every ticket is agent-workable, and forcing the agent form on a
# one-line bug report would make the form's own signal worthless.
blank_issues_enabled: true
contact_links:
  - name: Riot API status and endpoint reference
    url: https://developer.riotgames.com/
    about: Riot deprecates endpoints without notice. Check the portal before filing an API-shape bug.
```

- [ ] **Step 3: Lint both files**

Run:
```bash
python -c "import yaml; yaml.safe_load(open('.github/ISSUE_TEMPLATE/agent-task.yml')); print('ok')"
python -c "import yaml; yaml.safe_load(open('.github/ISSUE_TEMPLATE/config.yml')); print('ok')"
```
Expected: `ok` twice.

- [ ] **Step 4: Verify the form renders (after this branch is pushed)**

GitHub only serves issue templates from the **default branch**, so this cannot be checked from a branch. Defer to Task 6, Step 1 — the canary issue is filed through this form, which is the verification. Record here that a schema-valid file that GitHub rejects at render time shows as the template simply not appearing in the chooser.

- [ ] **Step 5: Commit**

```bash
git add .github/ISSUE_TEMPLATE/agent-task.yml .github/ISSUE_TEMPLATE/config.yml
git commit -m "feat(intake): add an agent-workable issue form

The form's output is the entire input to the planning agent, so it asks for an
outcome, a falsifiable acceptance criterion, and a blast radius rather than free
text. Labels are deliberately not preset: GitHub fires \`labeled\` at creation
time, which would start a planning run before a human had read the ticket back.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
```

---

### Task 3: Build the two-phase intake workflow, and let the reviewer see its PRs

**Files:**
- Create: `.github/workflows/agent-intake.yml`
- Modify: `.github/workflows/claude-code-review.yml` (line 46 — `allowed_bots`)

**Interfaces:**
- Consumes: labels `agent:plan` / `agent:go` (Task 1); `secrets.CLAUDE_CODE_OAUTH_TOKEN`; `vars.FACTORY_ENABLED` (from F0).
- Produces: branch `agent/issue-<N>` carrying exactly one plan file; an authoritative issue comment holding the plan path and its commit SHA; and, after `agent:go`, an open PR authored by `claude[bot]` that `ci.yml` and `claude-code-review.yml` both run on.

Three mechanisms in this task are load-bearing and are **not** prompt instructions:

1. **The re-plan guard.** A non-agent step runs `git diff --exit-code "$APPROVED_SHA" -- "$PLAN_PATH"` after the agent step and fails the job if the plan file moved.
2. **The approved SHA and plan path are derived from git, never read from a comment.** A model-authored SHA can be wrong; `git rev-parse` cannot. The `plan` job's own guard *posts* the comment, so the agent never handles the value it would be judged against.
3. **PR creation happens inside the action step.** Both reasons are written into the YAML as comments and must survive any later edit.

- [ ] **Step 1: Write the intake workflow**

Create `.github/workflows/agent-intake.yml` with exactly this content:

```yaml
name: Agent Issue Intake

# The two-phase work queue (sub-project F2). Labels are the state machine, which makes the GitHub
# issue list the control-plane view and the whole flow operable from a phone:
#
#   agent:plan  -> hydrate docs/knowledge/, write ONE plan file, commit + push it to
#                  agent/issue-<N>. A non-agent step then posts the path and commit SHA.
#   (maintainer reads the plan on a phone; optional edits via GitHub's web editor land as new
#    commits on that same branch, producing a new tip SHA — drafts are meant to be edited)
#   agent:go    -> check out that branch tip, implement THAT plan file, push, and open the PR from
#                  inside the action step.
#
# ONE FILE, TWO JOBS — not two workflow files. `label_trigger` takes a single string, so the two
# phases cannot share a step; they can share a file, and should:
#   * gotchas.md records that claude-code-action skips itself (green, as an annotation) whenever its
#     workflow file differs from the default-branch copy. One file is one byte-identity surface to
#     keep in sync; two files is two, and the second one drifting is invisible.
#   * The label vocabulary, the FACTORY_ENABLED kill switch, the human-labeller assertion, the
#     branch-naming convention and the concurrency group are ONE state machine. Splitting them
#     across files lets half of it change without the other half.
#   * Jobs share no state anyway, so two files would buy no isolation to pay for that.

on:
  issues:
    types: [labeled]

# Least-privilege default for the workflow; each job elevates only the scopes its steps need.
permissions:
  contents: read

# Never let two phases — or a re-labelled issue — race on one issue's branch. Queue rather than
# cancel: a cancelled run leaves a half-written plan or a half-implemented branch behind, and this
# repo already learned that a cancelled run satisfies nothing downstream.
concurrency:
  group: agent-intake-${{ github.event.issue.number }}
  cancel-in-progress: false

jobs:
  # ---------------------------------------------------------------------------------------------
  # PHASE 1 — agent:plan
  # ---------------------------------------------------------------------------------------------
  plan:
    name: agent:plan
    # Three independent gates, all evaluated before any agent starts:
    #   1. FACTORY_ENABLED — the kill switch introduced by F0. Flipping the repository variable off
    #      from a mobile browser fails every agent job closed. If the variable does not exist yet,
    #      this expression is never 'true' and the job never runs: fail-closed by construction.
    #   2. sender.type == 'User' — asserts a human applied the label. The agent's own `issues: write`
    #      would otherwise let it label itself into the next phase.
    #   3. the label name — `label_trigger` takes a single string, so the phase fan-out lives here.
    if: >-
      vars.FACTORY_ENABLED == 'true' &&
      github.event.sender.type == 'User' &&
      github.event.label.name == 'agent:plan'
    runs-on: ubuntu-latest
    # claude-code-action exposes no timeout input of its own; its message loop runs until the job's
    # timeout kills it. This is the only bound.
    timeout-minutes: 30
    permissions:
      contents: write   # the agent pushes the plan branch from inside its own step
      issues: write     # tracking comment (track_progress) + the authoritative path/SHA comment
      id-token: write   # OIDC exchange that mints the action's App installation token
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          # Full history: the guards below diff against origin/master with a merge base.
          fetch-depth: 0

      - name: Prepare the plan branch
        id: prep
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          ISSUE_NUMBER: ${{ github.event.issue.number }}
        run: |
          set -euo pipefail
          BRANCH="agent/issue-${ISSUE_NUMBER}"

          # Refuse to re-plan an issue whose implementation has already opened a PR. Re-applying
          # agent:plan there would rewrite the plan on top of the implementation, and the phase-2
          # guard would then be comparing against a plan nobody approved.
          if [ -n "$(gh pr list --head "$BRANCH" --state open --json number --jq '.[].number')" ]; then
            echo "::error title=agent:plan::An open PR already exists for $BRANCH. Implementation has started; close that PR (or open a fresh issue) before re-planning."
            exit 1
          fi

          # Reuse the branch if it already exists — a plan still on a branch is a draft and is meant
          # to be revised. Only a MERGED plan is immutable (see docs/knowledge/README.md).
          if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
            echo "::notice title=agent:plan::Reusing existing branch $BRANCH — revising the draft plan."
            git fetch origin "$BRANCH"
            git checkout -B "$BRANCH" "origin/$BRANCH"
          else
            git checkout -b "$BRANCH"
          fi

          echo "branch=$BRANCH" >> "$GITHUB_OUTPUT"

      - name: Write the implementation plan
        uses: anthropics/claude-code-action@v1
        with:
          claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
          # Matches the label this job is gated on. The action's default is 'claude'; leaving it at
          # the default would make tag mode decline every run here, silently and green.
          label_trigger: 'agent:plan'
          # Forces tag mode on the `issues: labeled` event, which restores GitHub context and the
          # tracking comment while keeping the custom prompt below. Without it, automation mode
          # gives no visible progress on an issue the maintainer is watching from a phone.
          track_progress: true
          prompt: |
            REPO: ${{ github.repository }}
            ISSUE NUMBER: ${{ github.event.issue.number }}
            TITLE: ${{ github.event.issue.title }}
            BRANCH: ${{ steps.prep.outputs.branch }}

            You are PHASE 1 of a two-phase intake. You write a plan. You do NOT implement anything.

            1. HYDRATE FIRST, per docs/knowledge/README.md — that file is the single source of truth
               for the hydrate/persist protocol. Read it, then roadmap.md, then gotchas.md, then the
               ADRs and patterns/ guides relevant to this issue. Do not skip this because the issue
               looks small; gotchas.md exists because small-looking things were not.

            2. Write EXACTLY ONE new file:
                 docs/superpowers/plans/<YYYY-MM-DD>-issue-${{ github.event.issue.number }}-<slug>.md
               Use today's UTC date and a short kebab-case slug. Match the house style of
               docs/superpowers/plans/2026-07-19-repo-housekeeping-and-actions.md exactly: the
               agentic-workers blockquote, Goal / Architecture / Tech Stack, Global Constraints,
               a File Structure table, `### Task N:` sections separated by `---` with
               **Files:** / **Interfaces:** / `- [ ] **Step N: ...**` checkboxes, a `Run:` and
               `Expected:` pair for every verification, a commit step per task carrying both repo
               trailers, and a closing `## Self-Review`.

               Embed file content verbatim. The implementer should have to invent nothing.

            3. CHANGE NO OTHER FILE. Not source, not docs, not the knowledge base, not workflows.
               A non-agent step after you enforces this with a git diff and fails the job otherwise;
               it is a mechanism, not a request. Persisting to the knowledge base is phase 2's job,
               and your plan should say so.

            4. Commit and push to the branch above, from inside this step:
                 git add docs/superpowers/plans/
                 git commit -m "docs(plan): implementation plan for #${{ github.event.issue.number }}"
                 git push -u origin HEAD:${{ steps.prep.outputs.branch }}
               Push from HERE. A later step cannot: this action revokes its own git credentials as
               the step ends (see gotchas.md).

            5. Do NOT post the plan path or commit SHA yourself. A non-agent step reads both from
               git and posts them, because a SHA the maintainer approves must come from git rather
               than from a model's recollection of what it just did.
          claude_args: |
            --max-turns 60
            --allowedTools "Read,Write,Grep,Glob,Bash(ls:*),Bash(git status:*),Bash(git diff:*),Bash(git log:*),Bash(git rev-parse:*),Bash(git add:*),Bash(git commit:*),Bash(git push:*),Bash(gh issue view:*)"

      # ---- Non-agent guard. Everything below asserts an EFFECT, never the job's exit status. ----
      - name: Assert the plan landed, and landed alone
        id: guard
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          BRANCH: ${{ steps.prep.outputs.branch }}
          ISSUE_NUMBER: ${{ github.event.issue.number }}
        run: |
          set -euo pipefail

          CHANGED="$(git diff --name-only origin/master...HEAD)"
          COUNT="$(printf '%s\n' "$CHANGED" | grep -c . || true)"

          if [ "$COUNT" -ne 1 ]; then
            echo "::error title=agent:plan::Expected exactly 1 changed file, found $COUNT:"
            printf '%s\n' "$CHANGED"
            exit 1
          fi

          PLAN_PATH="$CHANGED"
          case "$PLAN_PATH" in
            docs/superpowers/plans/*.md) : ;;
            *)
              echo "::error title=agent:plan::The changed file is not a plan: $PLAN_PATH"
              exit 1
              ;;
          esac

          # A commit that was never pushed is the classic green-but-did-nothing shape: the local
          # diff looks perfect and the maintainer has nothing to read. Compare local HEAD against
          # what the remote actually holds.
          git fetch origin "$BRANCH"
          LOCAL_SHA="$(git rev-parse HEAD)"
          REMOTE_SHA="$(git rev-parse FETCH_HEAD)"
          if [ "$LOCAL_SHA" != "$REMOTE_SHA" ]; then
            echo "::error title=agent:plan::HEAD ($LOCAL_SHA) was not pushed to $BRANCH (remote is $REMOTE_SHA)."
            exit 1
          fi

          # The authoritative comment. Read from git, posted by a non-agent step, so the SHA the
          # maintainer approves is the SHA that exists.
          #
          # Built with printf rather than a heredoc on purpose: this whole script is a YAML block
          # scalar, every line is indented, and an unquoted heredoc terminator must sit at column 0
          # of its line. `<<-EOF` only strips TABS, which YAML forbids as indentation. printf sidesteps
          # the entire problem.
          BODY="$(printf '%s\n' \
            "### Plan ready for review" \
            "" \
            "| | |" \
            "|---|---|" \
            "| **Plan** | [\`$PLAN_PATH\`](../blob/$REMOTE_SHA/$PLAN_PATH) |" \
            "| **Branch** | \`$BRANCH\` |" \
            "| **Commit** | \`$REMOTE_SHA\` |" \
            "" \
            "Read it, and edit it in the web editor if it needs changing — a draft plan on a branch" \
            "is meant to be revised, and each edit becomes a new commit here. When it reads right," \
            "apply the **\`agent:go\`** label; phase 2 implements whatever is at this branch's tip at" \
            "that moment, and fails the job if it drifts afterwards.")"

          gh issue comment "$ISSUE_NUMBER" --body "$BODY"

  # ---------------------------------------------------------------------------------------------
  # PHASE 2 — agent:go
  # ---------------------------------------------------------------------------------------------
  go:
    name: agent:go
    if: >-
      vars.FACTORY_ENABLED == 'true' &&
      github.event.sender.type == 'User' &&
      github.event.label.name == 'agent:go'
    runs-on: ubuntu-latest
    timeout-minutes: 90
    permissions:
      contents: write        # push implementation commits from inside the action step
      issues: write          # tracking comment + failure escalation
      pull-requests: write   # `gh pr create` inside the action step needs this on the minted token
      id-token: write
    steps:
      - name: Checkout the approved branch
        uses: actions/checkout@v4
        with:
          ref: agent/issue-${{ github.event.issue.number }}
          fetch-depth: 0

      - name: Resolve the approved SHA and plan path
        id: approved
        run: |
          set -euo pipefail
          BRANCH="agent/issue-${{ github.event.issue.number }}"

          # The approved artifact is the branch tip AT THIS MOMENT — including any web-editor edits
          # the maintainer made. Resolved from git, never parsed out of an issue comment.
          APPROVED_SHA="$(git rev-parse HEAD)"

          CHANGED="$(git diff --name-only origin/master...HEAD)"
          COUNT="$(printf '%s\n' "$CHANGED" | grep -c . || true)"
          if [ "$COUNT" -ne 1 ]; then
            echo "::error title=agent:go::Expected the branch to carry exactly 1 plan file vs master, found $COUNT:"
            printf '%s\n' "$CHANGED"
            exit 1
          fi

          PLAN_PATH="$CHANGED"
          case "$PLAN_PATH" in
            docs/superpowers/plans/*.md) : ;;
            *)
              echo "::error title=agent:go::The branch's only change is not a plan: $PLAN_PATH"
              exit 1
              ;;
          esac

          echo "branch=$BRANCH"             >> "$GITHUB_OUTPUT"
          echo "sha=$APPROVED_SHA"          >> "$GITHUB_OUTPUT"
          echo "plan_path=$PLAN_PATH"       >> "$GITHUB_OUTPUT"
          echo "::notice title=agent:go::Implementing $PLAN_PATH at $APPROVED_SHA"

      - name: Implement the approved plan
        uses: anthropics/claude-code-action@v1
        with:
          claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
          label_trigger: 'agent:go'
          track_progress: true
          prompt: |
            REPO: ${{ github.repository }}
            ISSUE NUMBER: ${{ github.event.issue.number }}
            APPROVED PLAN: ${{ steps.approved.outputs.plan_path }}
            APPROVED SHA: ${{ steps.approved.outputs.sha }}
            BRANCH: ${{ steps.approved.outputs.branch }}

            You are PHASE 2. The plan above has been read and approved by a human. Implement it.

            1. Read the approved plan file first, in full. It is the specification. Hydrate the
               knowledge base per docs/knowledge/README.md for anything the plan assumes.

            2. Work the plan task by task, in order, honouring its verification steps.

            3. DO NOT EDIT THE APPROVED PLAN FILE. Not to tick its checkboxes, not to correct it,
               not to reflect what you actually did. A non-agent step after you runs
               `git diff --exit-code ${{ steps.approved.outputs.sha }} -- ${{ steps.approved.outputs.plan_path }}`
               and fails this job if it moved. If the plan turns out to be wrong, implement what you
               can, and describe the deviation in the PR body — deviations belong in the PR, and
               architectural ones become ADRs.

            4. Keep the offline gate green: `./gradlew build` must pass, with no test that needs a
               live Riot key or network access. Persist findings per the knowledge-base protocol if
               the plan calls for it.

            5. Commit, push, and OPEN THE PULL REQUEST FROM INSIDE THIS STEP:
                 git add -A
                 git commit -m "<type>(<scope>): <summary>" \
                   -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
                 git push origin HEAD:${{ steps.approved.outputs.branch }}
                 gh pr create --base master --head ${{ steps.approved.outputs.branch }} \
                   --title "<type>(<scope>): <summary>" \
                   --body "Implements ${{ steps.approved.outputs.plan_path }} at commit ${{ steps.approved.outputs.sha }}. Closes #${{ github.event.issue.number }}. <deviations, if any>"

               It MUST happen here, in this step, for two independent reasons — both structural:
                 (a) This step's credential is the action's own GitHub App installation token. App
                     tokens are NOT subject to the GITHUB_TOKEN recursion guard, so the resulting PR
                     actually fires `pull_request` events: ci.yml's `Build & verify` reports, and
                     claude-code-review.yml runs. Opened with GITHUB_TOKEN instead, the PR would get
                     neither — and with `Build & verify` a required check, a check that never
                     reports makes the PR permanently unmergeable, not merely unreviewed.
                 (b) This action revokes its installation token as this step ends. A later step in
                     this job has no working git credential at all (gotchas.md). Inside is the only
                     place both credentials exist.

            6. NEVER merge, approve, or request-review-dismissal on the PR. Automation proposes;
               a human approves. That is not negotiable and no tool here grants it.
          claude_args: |
            --max-turns 150
            --allowedTools "Read,Edit,Write,Grep,Glob,Bash(./gradlew:*),Bash(ls:*),Bash(git status:*),Bash(git diff:*),Bash(git log:*),Bash(git rev-parse:*),Bash(git add:*),Bash(git commit:*),Bash(git push:*),Bash(gh issue view:*),Bash(gh pr create:*),Bash(gh pr view:*)"

      # ---- The re-plan guard. Deterministic, non-agent, and the reason this design works. ----
      - name: Re-plan guard — the approved plan must not have moved
        env:
          APPROVED_SHA: ${{ steps.approved.outputs.sha }}
          PLAN_PATH: ${{ steps.approved.outputs.plan_path }}
        run: |
          set -euo pipefail
          if ! git diff --exit-code "$APPROVED_SHA" -- "$PLAN_PATH"; then
            echo "::error title=agent:go::$PLAN_PATH differs from the approved commit $APPROVED_SHA. The approval bound to an artifact and the artifact changed. Close the PR and re-review."
            exit 1
          fi
          echo "::notice title=agent:go::Approved plan unchanged at $APPROVED_SHA."

      - name: Assert a pull request actually exists
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          BRANCH: ${{ steps.approved.outputs.branch }}
        run: |
          set -euo pipefail
          PR="$(gh pr list --head "$BRANCH" --state open --json number --jq '.[0].number // empty')"
          if [ -z "$PR" ]; then
            echo "::error title=agent:go::No open PR for $BRANCH. The agent step exited without proposing anything — a green run here would have been the third green-but-did-nothing incident on record."
            exit 1
          fi
          echo "::notice title=agent:go::Opened PR #$PR."

  # ---------------------------------------------------------------------------------------------
  # Escalation. GitHub pushes @mention notifications to GitHub Mobile; workflow-failure email does
  # not cover `issues`-triggered runs, so a silent red run is a run nobody learns about.
  # ---------------------------------------------------------------------------------------------
  alert:
    name: Escalate a failed phase
    needs: [plan, go]
    if: always() && (needs.plan.result == 'failure' || needs.go.result == 'failure')
    runs-on: ubuntu-latest
    timeout-minutes: 5
    permissions:
      issues: write
    steps:
      - name: Comment on the issue
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          RUN_URL: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
        run: |
          gh issue comment "${{ github.event.issue.number }}" \
            --body "@Muddl the \`${{ github.event.label.name }}\` phase failed. [Run log](${RUN_URL}). Nothing was merged; the kill switch is \`FACTORY_ENABLED\`."
```

- [ ] **Step 2: Let the reviewer see `claude[bot]` PRs**

In `.github/workflows/claude-code-review.yml`, change line 46 from:
```yaml
          allowed_bots: 'dependabot'
```
to:
```yaml
          # `claude` is here because agent:go opens its PR with the action's own App installation
          # token, making claude[bot] the PR author. The action declines bot-triggered runs unless
          # the bot is listed — and it declines by going GREEN, so without this the agent's own PR
          # would sail past the reviewer looking reviewed. That is ADR-0018's failure shape exactly:
          # artifact present, property absent.
          allowed_bots: 'dependabot,claude'
```

The bot login is asserted, not assumed: `dependabot` is listed without its `[bot]` suffix, so the action compares the stripped login. Task 6, Step 4 is the falsification — if the reviewer does not run on the canary PR, read `github.event.pull_request.user.login` from the PR and use that string verbatim here.

- [ ] **Step 3: Lint both workflows**

Run:
```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/agent-intake.yml')); print('ok')"
python -c "import yaml; yaml.safe_load(open('.github/workflows/claude-code-review.yml')); print('ok')"
```
Expected: `ok` twice.

- [ ] **Step 4: Statically verify the guard logic, since the workflow itself is not branch-testable**

`gotchas.md`: `claude-code-action` refuses to run when its workflow file differs from the default-branch copy — and `agent-intake.yml` does not exist on `master` at all, so a branch dispatch would skip both agent steps and go **green having done nothing**. Only the non-agent steps can be checked here, and they can be checked directly:

```bash
# The phase-1 shape check, replayed against this very branch (which has >1 changed file, so it
# must reject) and against a synthetic single-plan case (which must accept).
git diff --name-only origin/master...HEAD | grep -c .
```
Expected: an integer **greater than 1** — proving the phase-1 guard would correctly reject a branch that touched more than the plan. (The single-file accept path is exercised for real by Task 6, Step 2.)

```bash
# The re-plan guard, replayed locally: identical content passes, a byte changed fails.
BASE="$(git rev-parse HEAD)"
git diff --exit-code "$BASE" -- docs/superpowers/plans/ && echo "GUARD-PASS"
printf '\n' >> docs/superpowers/plans/2026-08-01-factory-f2-issue-intake.md
git diff --exit-code "$BASE" -- docs/superpowers/plans/ || echo "GUARD-FAIL-AS-EXPECTED"
git checkout -- docs/superpowers/plans/2026-08-01-factory-f2-issue-intake.md
```
Expected: `GUARD-PASS` then `GUARD-FAIL-AS-EXPECTED`. This is the mechanism of the whole design, verified with no tokens spent and no reliance on a green run.

- [ ] **Step 5: Commit — both files together**

The `allowed_bots` change ships in the same commit as the workflow on purpose: a commit that adds `agent:go` without it produces PRs that go unreviewed on a green run, and that is a state this repo should never be in for even one commit.

```bash
git add .github/workflows/agent-intake.yml .github/workflows/claude-code-review.yml
git commit -m "feat(intake): two-phase agent issue intake, guarded deterministically

agent:plan commits one plan file to agent/issue-<N>; a non-agent step reads the
path and tip SHA from git and posts them. agent:go checks that branch out,
implements that file, and opens the PR from inside the action step so the App
installation token authors it — App tokens escape the GITHUB_TOKEN recursion
guard, so pull_request fires and Build & verify actually reports, and inside the
step is the only place the action's git credential is still alive.

A non-agent step then runs git diff --exit-code <approved-sha> -- <plan-path>.
Instructing the model not to re-plan is not a mechanism.

allowed_bots gains 'claude' in this same commit: the agent's PR is authored by
claude[bot], and the reviewer declines unlisted bots by going green.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
```

---

### Task 4: Redefine plan immutability as "immutable once merged to `master`"

**Files:**
- Modify: `docs/knowledge/roadmap.md` (2 sites)
- Modify: `docs/knowledge/README.md` (the Persist section)
- Modify: `.claude/skills/housekeeping/SKILL.md` (the Hands-off section)

**Interfaces:**
- Produces: a rule the `agent:plan` review phase can satisfy. The current rule cannot survive a phase whose entire purpose is refining a plan before approval — under it, the maintainer's web-editor edit is a violation.
- Consumes: nothing. Blocks nothing. But leaving it stale means the flow shipped in Task 3 contradicts the knowledge base on its first run.

- [ ] **Step 1: Find every site the rule is stated**

Run:
```bash
grep -rniE "immutable|dated snapshot|not edited retroactively" \
  --include='*.md' docs/knowledge .claude CLAUDE.md CONTRIBUTING.md README.md
```
Expected: at minimum these four sites, which are the ones this task edits —
`docs/knowledge/roadmap.md:8`, `docs/knowledge/roadmap.md:193`, `docs/knowledge/README.md:77`,
`.claude/skills/housekeeping/SKILL.md:48`. Hits inside `docs/superpowers/specs/` and
`docs/superpowers/plans/` are **not** edited: those files are merged history, which is the very rule
being restated. If the grep surfaces a fifth live site, fix it the same way and note it in the PR.

- [ ] **Step 2: Redefine the rule in `roadmap.md`'s preamble**

In `docs/knowledge/roadmap.md`, replace:
```markdown
This file is the **source of truth for scope and sequencing**. Specs under
`docs/superpowers/specs/` are dated snapshots of a decision at a moment — they are history and are
not edited retroactively. When scope moves, it moves here.
```
with:
```markdown
This file is the **source of truth for scope and sequencing**. Specs under
`docs/superpowers/specs/` are dated snapshots of a decision at a moment — they are history and are
not edited retroactively. Plans under `docs/superpowers/plans/` are history **once merged to
`master`**; a plan still on an `agent/issue-<N>` branch is a draft and is meant to be edited, which
is exactly what the `agent:plan` review phase exists to do. When scope moves, it moves here.
```

- [ ] **Step 3: Fix the Replayability row**

In `docs/knowledge/roadmap.md`, in the "Substrate already in place" table, replace:
```markdown
| Replayability | ADRs + immutable `docs/superpowers/specs|plans/` record why a change looks the way it does |
```
with:
```markdown
| Replayability | ADRs + `docs/superpowers/specs/` and **merged** `docs/superpowers/plans/` record why a change looks the way it does |
```

- [ ] **Step 4: State the rule where the protocol lives**

`docs/knowledge/README.md` is the single source of truth for the hydrate/persist protocol, so the rule belongs there in full. In its **Persist** section, immediately after the `roadmap.md` bullet and immediately before the line beginning `Do not edit an existing ADR`, insert:

```markdown
**Plans are immutable once merged to `master`.** A plan under `docs/superpowers/plans/` is a *draft*
while it lives on a branch — the two-phase `agent:plan` → `agent:go` intake exists precisely so a
plan can be refined before it is approved, and the maintainer's web-editor edits are the expected
path, not an exception. Once merged it is history: never edited, never re-ticked, never corrected
after the fact. Deviations discovered during implementation are recorded in the implementing PR's
body, and architectural ones become ADRs. See
[the issue-intake pattern](patterns/agent-issue-intake.md).

```

- [ ] **Step 5: Reconcile the housekeeping skill's hands-off rule**

The skill must not be left saying something the KB no longer says. In `.claude/skills/housekeeping/SKILL.md`, replace:
```markdown
## Hands-off (never touch)

`docs/superpowers/specs/` and `docs/superpowers/plans/` are dated snapshots — immutable history.
Do not edit or trim them. Scope moves in `roadmap.md`, not in the dated specs.
```
with:
```markdown
## Hands-off (never touch)

`docs/superpowers/specs/` and merged `docs/superpowers/plans/` are dated snapshots — immutable
history. Do not edit or trim them. Scope moves in `roadmap.md`, not in the dated specs.

A plan that has **not** merged to `master` is a draft and is editable (see the KB README). This
skill never encounters one: housekeeping runs from `master`, where every plan present is merged. So
in practice the rule above is unchanged for this skill — it is restated only so the skill and the
knowledge base cannot be read as disagreeing.
```

- [ ] **Step 6: Verify no live site still states the old rule**

Run:
```bash
grep -rn "immutable \`docs/superpowers/specs|plans/\`" --include='*.md' . ; \
grep -rniE "plans/\` are dated snapshots — immutable" --include='*.md' docs/knowledge .claude
```
Expected: no output from the first grep; from the second, no hits outside `docs/superpowers/`.

- [ ] **Step 7: Commit**

```bash
git add docs/knowledge/roadmap.md docs/knowledge/README.md .claude/skills/housekeeping/SKILL.md
git commit -m "docs(kb): plans are immutable once merged, not once written

The old rule cannot survive a review phase whose entire purpose is refining a
plan before approval — under it, the maintainer's web-editor edit during
agent:plan is a violation. Drafts on a branch are mutable; merged plans are
history, with deviations recorded in the implementing PR body and architectural
ones becoming ADRs.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
```

---

### Task 5: Persist the intake flow as a knowledge-base pattern

**Files:**
- Create: `docs/knowledge/patterns/agent-issue-intake.md`
- Modify: `docs/knowledge/README.md` (Patterns list)
- Modify: `docs/knowledge/roadmap.md` (the F2 row)

**Interfaces:**
- Consumes: everything built in Tasks 1–4.
- Produces: the repeatable procedure, per the persist half of the protocol in `docs/knowledge/README.md` — "Established a new recurring procedure? Add a new guide in `patterns/`, then link it from this README's Patterns list."

No ADR is written for F2. The architectural decision is already recorded in the merged
[2026-08-01 decomposition spec](../../superpowers/specs/2026-08-01-software-factory-decomposition-design.md),
and the next free ADR number is contended right now: F0 ships concurrently and may claim it. A
duplicate ADR number is worse than a missing one. If F2 later needs its own ADR, allocate the number
after F0 has merged.

- [ ] **Step 1: Write the pattern guide**

Create `docs/knowledge/patterns/agent-issue-intake.md` with exactly this content. **The outer fence
below is four backticks** because the guide itself contains a three-backtick block; write the file
with the inner three-backtick fences intact and no four-backtick fence at all.

````markdown
# Drive an issue through two-phase agent intake

The GitHub issue list is this repo's work queue, and labels are its state machine — which is what
makes it operable from a phone. `.github/workflows/agent-intake.yml` implements it. Sub-project F2;
see the [decomposition spec](../../superpowers/specs/2026-08-01-software-factory-decomposition-design.md).

## The flow

| Step | Who | What happens |
|---|---|---|
| 1 | Human | File the issue (the **Agent-workable task** form). Read it back; edit until unambiguous. |
| 2 | Human | Apply **`agent:plan`**. |
| 3 | Agent | Hydrates `docs/knowledge/`, writes **one** plan file, commits and pushes `agent/issue-<N>`. |
| 4 | Workflow | A non-agent step asserts exactly one changed file, asserts it reached the remote, and comments the path + tip SHA. |
| 5 | Human | Read the plan. Edit it in the web editor if needed — each edit is a new commit, and the newest tip is what gets built. |
| 6 | Human | Apply **`agent:go`**. |
| 7 | Agent | Checks out that tip, implements that file, pushes, and opens the PR **from inside the action step**. |
| 8 | Workflow | `git diff --exit-code <approved-sha> -- <plan-path>` fails the job if the plan moved; a second step fails it if no PR exists. |
| 9 | Human | Review the PR. Approve and merge, or don't. Automation never does this step. |

## Why each mechanism is shaped the way it is

- **The approval binds to an artifact, not a conversation.** The approved SHA is `git rev-parse HEAD`
  on the branch at the moment `agent:go` is applied — resolved by a non-agent step, never parsed out
  of a comment a model wrote. A SHA a model reports can be wrong; a SHA git reports cannot.
- **The re-plan guard is a diff, not an instruction.** The prompt also tells the agent not to touch
  the plan, but that is courtesy. The mechanism is
  `git diff --exit-code "$APPROVED_SHA" -- "$PLAN_PATH"`.
- **The PR is opened inside the action step.** Twice load-bearing. App installation tokens are not
  subject to the `GITHUB_TOKEN` recursion guard, so `pull_request` fires and `Build & verify`
  reports — and since that is a required check, a PR it never reports on is permanently unmergeable,
  not merely unreviewed. And the action revokes its own git credentials as the step ends
  ([gotchas.md](../gotchas.md)), so inside the step is the only place a working credential exists.
- **`allowed_bots` includes `claude`.** The PR's author is `claude[bot]`, and
  `claude-code-review.yml` declines unlisted bots by going *green*. Omitting it reproduces ADR-0018's
  failure exactly: artifact present, property absent.
- **`track_progress: true`.** Forces tag mode on the `issues: labeled` event, restoring GitHub
  context and the tracking comment while keeping the custom `prompt`. Without it there is no visible
  progress on an issue someone is watching from a phone.
- **`sender.type == 'User'`.** The agent holds `issues: write`; without this it could label itself
  into the next phase.

## Operating it

```bash
gh issue create --title "docs: ..." --body "..."     # or use the issue form in the web UI
gh issue edit <N> --add-label "agent:plan"
gh issue view <N> --comments                          # read the plan path + SHA
gh issue edit <N> --add-label "agent:go"
gh pr checks <branch-or-pr>                           # ci.yml AND claude-code-review.yml must appear
```

## When it goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| No run appears at all | `FACTORY_ENABLED` is not `'true'`, a bot applied the label, or the label name does not match | Check the repo variable; check `sender.type`; labels are `agent:plan` / `agent:go` exactly |
| `Expected exactly 1 changed file, found N` | The planning agent edited something besides its plan | Read the listed paths; re-plan on a clean branch |
| `HEAD was not pushed` | The agent committed but never pushed | Re-apply `agent:plan`; the branch is reused and the draft revised |
| `differs from the approved commit` | The implementing agent rewrote the plan | Close the PR. The approval is void — the artifact it bound to no longer exists |
| `No open PR for <branch>` | The agent step ended without proposing anything | Read the run log; this is the failure a green run would otherwise have hidden |
| Reviewer never runs on the agent's PR | Wrong bot login in `allowed_bots` | Read `github.event.pull_request.user.login` on the PR and use it verbatim |
| An open PR blocks re-planning | Implementation already started on that branch | Close the PR, or open a fresh issue |

## Kill switch

Set the repository variable `FACTORY_ENABLED` to anything but `true` and every agent job stops
matching its `if:`. Escalating further: disable the workflow from the Actions tab, then revoke
`CLAUDE_CODE_OAUTH_TOKEN`. All three are reachable from a mobile browser.
````

- [ ] **Step 2: Link the pattern from the KB README**

In `docs/knowledge/README.md`, in the **Patterns** list, immediately after the `Run and extend the live eval harness` item, add:
```markdown
- [Drive an issue through two-phase agent intake](patterns/agent-issue-intake.md)
```

- [ ] **Step 3: Close the roadmap's F2 row**

In `docs/knowledge/roadmap.md`, in the six-sub-project table, replace:
```markdown
| **F2** | Issue intake, two-phase | ⏳ Not started | `agent:plan` commits a plan and posts its SHA; `agent:go` implements *that SHA*, checked by a deterministic `git diff --exit-code`. The work-queue primitive. |
```
with:
```markdown
| **F2** | Issue intake, two-phase | ✅ Shipped | `.github/workflows/agent-intake.yml` + the [intake pattern](patterns/agent-issue-intake.md). `agent:plan` commits a plan and a non-agent step posts its SHA; `agent:go` implements *that SHA*, checked by `git diff --exit-code`. The work-queue primitive. |
```

Also update the primitives-audit row above it, replacing:
```markdown
| **Work queue** — issues, assigned | ⏳ Issues are not yet the intake path. This is the next real step. |
```
with:
```markdown
| **Work queue** — issues, assigned | ✅ Issues are the intake path: `agent:plan` / `agent:go` labels drive [`agent-intake.yml`](../../.github/workflows/agent-intake.yml). See the [intake pattern](patterns/agent-issue-intake.md). |
```

- [ ] **Step 4: Verify every pattern on disk is linked**

Run:
```bash
comm -23 \
  <(ls docs/knowledge/patterns/*.md | xargs -n1 basename | sort) \
  <(grep -oE 'patterns/[a-z0-9-]+\.md' docs/knowledge/README.md | sed 's|patterns/||' | sort -u)
```
Expected: empty output.

- [ ] **Step 5: Commit**

```bash
git add docs/knowledge/patterns/agent-issue-intake.md docs/knowledge/README.md docs/knowledge/roadmap.md
git commit -m "docs(kb): pattern for two-phase agent issue intake; close F2

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
```

---

### Task 6: Canary the whole flow end to end, watched

**Files:** none (integration/verification task)

**Interfaces:**
- Consumes: Tasks 1–5, **merged to `master`**. Nothing below can be run from a branch.
- Produces: the only evidence that any of this works.

**This workflow's first real execution is necessarily its first test.** There is no staging issue
list and no dry-run mode. Two facts make branch-based rehearsal impossible, not merely inconvenient:

1. `claude-code-action` refuses to run when its workflow file differs from the default-branch copy,
   and records the refusal as an **annotation on a successful job**. `agent-intake.yml` does not
   exist on `master` until this PR merges, so any branch dispatch skips both agent steps and reports
   green. A new workflow containing the action **cannot be validated from a branch** unless
   `github_token:` is supplied to bypass the OIDC exchange — which this design specifically must not
   do, because a `GITHUB_TOKEN`-authored PR re-triggers the recursion guard the whole `gh pr create`
   placement exists to escape.
2. GitHub serves issue templates only from the default branch, so the form in Task 2 is likewise
   unverifiable until merge.

Therefore: merge first, then run the canary **at a desk, watching**, before any real ticket is
labelled. Do not do this while unreachable.

Prerequisites, checked before starting: F0 is merged and applied (`FACTORY_ENABLED` exists and is
`'true'`; the ruleset split has been applied locally by the maintainer), and this PR is merged.

- [ ] **Step 1: File a deliberately trivial canary issue through the form**

In the web UI, choose **Agent-workable task**. Make it small enough that a wrong plan costs nothing
and a right one is unmistakable — e.g. *"docs: add a one-line glossary entry for `puuid`"*, blast
radius **Docs / knowledge base only**, acceptance criterion
`grep -c '^## puuid' docs/knowledge/glossary.md` → `1`.

Run: `gh issue list --limit 3`
**Falsifiable criterion:** the issue exists **and** the form appeared in the chooser with its three
required fields. If the form did not appear, Task 2's YAML is schema-valid but GitHub-invalid — stop
and fix it; nothing downstream is meaningful.

- [ ] **Step 2: Phase 1 — `agent:plan`**

```bash
gh issue edit <N> --add-label "agent:plan"
gh run watch "$(gh run list --workflow=agent-intake.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
gh issue view <N> --comments
```
**Falsifiable criteria, all four required:**
1. A run appears at all — proving `FACTORY_ENABLED`, `sender.type`, and the label name all matched.
   *No run* is the most likely first failure and is silent by nature.
2. The final comment names a path under `docs/superpowers/plans/` and a 40-character SHA.
3. `git fetch origin agent/issue-<N> && git rev-parse FETCH_HEAD` equals that SHA exactly.
4. `git diff --name-only origin/master...FETCH_HEAD` prints **exactly one line**, that path.

If (2) is present but (3) disagrees, the guard is broken, not the agent — that comment is posted by
a non-agent step *from* `git rev-parse`, so a mismatch means the wrong ref was read.

- [ ] **Step 3: Prove tampering fails the job — do this BEFORE the honest run**

Test the guard on a run you are willing to lose. Edit the plan file on the branch through GitHub's
web editor (add a trailing blank line — content is irrelevant), then note the branch tip **before**
labelling, then have a second edit land after the run starts. The simplest deterministic version:

```bash
# Capture the approved SHA, apply agent:go, then immediately push a change to the plan file so it
# moves out from under the running job.
git fetch origin "agent/issue-<N>"
APPROVED="$(git rev-parse FETCH_HEAD)"; echo "$APPROVED"
gh issue edit <N> --add-label "agent:go"
# ...then, while the run is in flight, edit the plan file in the GitHub web editor and commit.
gh run watch "$(gh run list --workflow=agent-intake.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
```
**Falsifiable criterion:** the `go` job's conclusion is **failure**, and the failing step is
`Re-plan guard — the approved plan must not have moved`, with the annotation naming
`$APPROVED`. A failure in any *other* step does not count — it would mean the job died before the
guard ever ran, proving nothing about the guard.

Note the honest consequence of mechanism (3): because the PR is opened *inside* the agent step, the
guard runs after it. A tampered run therefore leaves an open PR behind, failing loudly on the
**issue**, not on the PR. Close that PR by hand and record that you did. This trade-off is inherent
to opening the PR with the App token and is accepted, not overlooked.

Then reset for the honest run:
```bash
gh pr close <tampered-pr>
gh issue edit <N> --remove-label "agent:go"
```
(If the branch is now polluted, delete it and re-run Step 2.)

- [ ] **Step 4: Phase 2 — the honest `agent:go`, and the check that everything actually reports**

```bash
gh issue edit <N> --add-label "agent:go"
gh run watch "$(gh run list --workflow=agent-intake.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
PR="$(gh pr list --head "agent/issue-<N>" --state open --json number --jq '.[0].number')"; echo "$PR"
gh pr view "$PR" --json author,headRefName --jq '{author: .author.login, head: .headRefName}'
gh pr checks "$PR"
```
**Falsifiable criteria, all five required:**
1. The `go` job's conclusion is **success**.
2. `$PR` is non-empty — a PR exists. (The workflow's own `Assert a pull request actually exists`
   step covers this, but check it independently; that is the point of an external criterion.)
3. `author.login` is a bot identity, **not** `Muddl` and not `github-actions`. Record the exact
   string — Task 3, Step 2's `allowed_bots` value must match it with `[bot]` stripped.
4. `gh pr checks "$PR"` lists a row for **`Build & verify`** with a real conclusion. A PR with *no*
   row for it is the permanently-unmergeable failure the App-token placement exists to prevent, and
   it looks fine until someone tries to merge.
5. `gh pr checks "$PR"` also lists **`Claude Code Review`**, and `gh pr view "$PR" --comments` shows
   an actual review comment. A green `Claude Code Review` run with no comment means the bot login
   in `allowed_bots` is wrong — the action declines unlisted bots *green*. Fix the string, push, and
   re-verify; a green run is not the evidence here, the comment is.

- [ ] **Step 5: Confirm automation proposed and did not approve**

Run:
```bash
gh pr view "$PR" --json reviews,mergedAt --jq '{reviews: [.reviews[].author.login], merged: .mergedAt}'
```
**Falsifiable criterion:** `merged` is `null`, and no review is an APPROVED review from a machine
identity. Then merge it by hand — or close it, since the canary's content is disposable. Either way
the human did it.

- [ ] **Step 6: Exercise the kill switch from the phone**

From GitHub Mobile or a mobile browser, set `FACTORY_ENABLED` to `false`, apply `agent:plan` to a
throwaway issue, and confirm **no run appears** in the Actions tab. Set it back to `true`.

**Falsifiable criterion:** `gh run list --workflow=agent-intake.yml --limit 3` shows no new run for
that issue. "No run" is the correct observation and is easy to mistake for "I did not look hard
enough" — check the timestamps.

- [ ] **Step 7: Record the canary in the pattern guide if anything surprised you**

If any criterion above failed on the first attempt, append the symptom and cause to the pattern's
"When it goes wrong" table (or to `docs/knowledge/gotchas.md` if it is a sharp edge rather than an
operating error), and commit with both trailers. A canary that taught nothing needs no entry; one
that did must not lose it.

---

## Self-Review

**Spec coverage** (against `### 3. F2` of the decomposition spec, and issue #75):
- `agent:plan` hydrates the KB per the repo protocol, writes a plan, commits it to a branch, and the path + SHA are posted → Task 3, `plan` job. Strengthened past the spec: the comment is posted by a **non-agent** step reading `git rev-parse`, so the SHA the maintainer approves cannot be a model's recollection. ✅
- "Touches no other file" enforced, not requested → Task 3, `Assert the plan landed, and landed alone`. ✅
- Maintainer reads on a phone, edits via the web editor producing a new SHA, then applies `agent:go` → Task 3 (`Resolve the approved SHA` reads the tip at label time, so web edits are picked up by construction), documented in Task 5's pattern. ✅
- `agent:go` checks out the approved SHA and implements *that file* → Task 3, `go` job, `ref: agent/issue-<N>` + `Resolve the approved SHA`. ✅
- **Deterministic re-plan guard**, non-agent, `git diff --exit-code <sha> -- <path>` → Task 3, `Re-plan guard`; rehearsed locally in Task 3 Step 4; falsified live in Task 6 Step 3. ✅
- **PR created inside the action step** via `Bash(gh pr create:*)` on the App installation token, with **both** reasons written as YAML comments (recursion-guard/required-check, and credential revocation) → Task 3, Step 1, prompt item 5(a)/(b). ✅
- **`claude` added to `allowed_bots`, in the same commit** → Task 3, Steps 2 and 5. ✅
- `on: issues: types: [labeled]`; two jobs gated on `github.event.label.name`, **with the one-file-vs-two-files choice justified** → Task 3, Step 1 header comment (byte-identity surface, one state machine, no isolation gained). ✅
- `track_progress: true` → both jobs. ✅
- `--max-turns` explicit (60 / 150) and job `timeout-minutes` (30 / 90 / 5) → Task 3. ✅
- `if: vars.FACTORY_ENABLED == 'true'`, with the F0 dependency noted → Global Constraints + both jobs' `if:`. ✅
- `github.event.sender.type == 'User'` → both jobs' `if:`. ✅
- `--allowedTools` least-privilege and explicit → Task 3; phase 1 has no `Edit`, no `gh issue comment`, no `./gradlew`; neither phase has `gh pr merge`, `gh pr review`, or any Workflows scope. ✅
- Labels created with exact commands → Task 1. ✅
- Issue templates under `.github/ISSUE_TEMPLATE/` (none existed — verified) → Task 2. ✅
- Immutability redefined as "immutable once merged to `master`", located by grep → Task 4, four sites, grep included as Step 1. ✅
- New `patterns/` guide persisted and linked → Task 5. ✅
- Final task is a trivial watched canary with a falsifiable criterion per step, both `ci.yml` and `claude-code-review.yml` confirmed running, tampering confirmed failing, plus the first-execution and branch-validation warnings → Task 6. ✅

**Placeholder scan:** No TBD/TODO. `<N>` is the canary issue number and is unknown only until Task 6
Step 1 creates it; every other identifier is concrete. The bot login in `allowed_bots` is written as
`claude` on the evidence that the existing `dependabot` entry omits its `[bot]` suffix, and Task 6
Step 4 criterion 3 is the explicit falsification with the correction procedure stated. No ADR number
is allocated, deliberately — F0 ships concurrently and the reason is recorded in Task 5.

**Type/name consistency:** The labels `agent:plan` / `agent:go` are byte-identical across Task 1's
`gh label create`, both jobs' `if:` expressions, both `label_trigger:` inputs, the issue-form
guidance, the pattern guide, and every canary command. The branch convention `agent/issue-<N>` is
identical in the `plan` job's `prep` step, the `go` job's `checkout.ref` and `approved` step, the
pattern's troubleshooting table, and Task 6. `${{ steps.prep.outputs.branch }}`,
`${{ steps.approved.outputs.sha }}` and `${{ steps.approved.outputs.plan_path }}` are each written
by exactly one `$GITHUB_OUTPUT` line and read only under those names. The plan-path predicate
(`docs/superpowers/plans/*.md`, exactly one file vs `origin/master`) is stated identically in the
phase-1 guard and the phase-2 pre-flight. `CLAUDE_CODE_OAUTH_TOKEN` is the only Claude credential
anywhere in this plan; `ANTHROPIC_API_KEY` appears nowhere.
