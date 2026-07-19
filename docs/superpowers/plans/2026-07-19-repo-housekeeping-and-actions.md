# Repository Housekeeping + Claude Code Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a reusable `/housekeeping` skill and a weekly PR-opening cron that runs it, fix the no-op PR reviewer so it posts real reviews, and cut metered `live-eval` runs on no-op merges.

**Architecture:** A project skill (`.claude/skills/housekeeping/`) carries the audit checklist as durable context; a scheduled, commit-gated GitHub workflow runs it on the flat-rate OAuth seat and opens a PR. The PR reviewer is rebuilt from the plugin-slash-command form (which emitted nothing) to the action's canonical automated-review form (direct prompt + `--allowedTools` for the comment tools) with `pull-requests: write`. `live-eval` gains a `paths-ignore` filter so docs/skill-only merges don't spend metered tokens.

**Tech Stack:** GitHub Actions, `anthropics/claude-code-action@v1`, Claude Code skills (Markdown), `gh` CLI, Bash. No application (Java) code changes.

## Global Constraints

- **Two credential buckets, kept separate** (ADR-0012): interactive + housekeeping Actions authenticate with `CLAUDE_CODE_OAUTH_TOKEN` (flat-rate seat); `live-eval` is the only consumer of `ANTHROPIC_API_KEY` (metered). Never move an interactive/housekeeping Action onto the metered key.
- **Actual tool count is 13** (unique `name = "…"` values under `lol-mcp-server/src/main`); never hardcode a count without re-deriving it from source.
- **Dated specs/plans are immutable history** — `docs/superpowers/specs/` and `docs/superpowers/plans/` are never edited or trimmed by housekeeping.
- **Offline suite stays key-free** — nothing here adds a test that needs a live key or network.
- **A green Actions run is not proof of success** — the PR reviewer fix is only "done" once a real review is observed on a test PR.
- Commit messages end with the repo's trailer:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` and the `Claude-Session:` line.
- All work lands on branch `chore/housekeeping-and-actions` (already created; the design spec is its first commit).

---

## File Structure

| File | Responsibility |
|---|---|
| `.claude/skills/housekeeping/SKILL.md` | **New.** The reusable audit checklist + procedure (2 modes). |
| `.github/workflows/housekeeping.yml` | **New.** Weekly, commit-gated run that edits, then branches + opens a PR. |
| `.github/workflows/claude-code-review.yml` | **Rewrite.** Canonical automated review that actually posts. |
| `.github/workflows/claude.yml` | **Modify.** Add `pull-requests: write` + `issues: write`. |
| `.github/workflows/live-eval.yml` | **Modify.** Add `paths-ignore` to the `push` trigger. |
| `CLAUDE.md` | **Modify (dogfood).** Fix stale tool count. |
| `docs/knowledge/README.md` | **Modify (dogfood + persist).** Add ADR-0014, and the new ADR, to the list. |
| `docs/knowledge/roadmap.md` | **Modify (persist).** Close the "Claude Code Actions integration rework" row. |
| `docs/knowledge/decisions/ADR-0015-repo-maintenance-automation.md` | **New (persist).** Record this decision. |

---

### Task 1: Author the `/housekeeping` skill

**Files:**
- Create: `.claude/skills/housekeeping/SKILL.md`

**Interfaces:**
- Produces: an invocable skill named `housekeeping`, accepting an optional `--audit-only` argument. Consumed by a human (`/housekeeping`) and by `housekeeping.yml` (Task 5).

- [ ] **Step 1: Write the skill file**

Create `.claude/skills/housekeeping/SKILL.md` with exactly this content:

```markdown
---
name: housekeeping
description: Audit and trim the repo's docs, knowledge base, roadmap, skills, and agents so they stay accurate and fit-for-purpose. Use for a periodic maintenance pass, or when doc drift is suspected. Accepts `--audit-only` to report without editing.
---

# Repository housekeeping

Keep `riot-api-mcp-server`'s durable context — docs, knowledge base, roadmap, skills, agents —
accurate, trim, and consistent with the real project. This is the reusable definition behind both
the manual `/housekeeping` pass and the weekly `housekeeping.yml` Action.

## Modes

- **`--audit-only`** — report findings as a checklist; make no edits.
- **default** — apply the fixes, keeping each change small and single-purpose.

## Hydrate first

Read `docs/knowledge/README.md` (the hydrate/persist protocol), then `roadmap.md` and `gotchas.md`,
so fixes stay consistent with recorded decisions. For any non-trivial doc rewrite, delegate the
prose to the `docs-maintainer` agent rather than restating its rules here.

## Audit checklist

Work through each item. For every finding, note the file, the drift, and the fix.

1. **Doc ↔ code drift.** Re-derive facts from source and reconcile prose in `README.md`,
   `ARCHITECTURE.md`, `CLAUDE.md`, `CONTRIBUTING.md`:
   - Tool count = unique `name = "…"` values:
     `grep -rhoE 'name = "[a-z_]+"' --include=*.java lol-mcp-server/src/main | sort -u | wc -l`
   - Tool classes = files declaring `@McpTool` under `adapter/in/mcp`:
     `grep -rl "@McpTool" --include=*.java lol-mcp-server/src/main | grep 'adapter/in/mcp' | wc -l`
   - Context/package names and transport details (`stdio`/`sse`) match the source tree.
2. **KB integrity.**
   - Every ADR on disk is linked from `docs/knowledge/README.md`:
     `comm -23 <(ls docs/knowledge/decisions/ADR-*.md | xargs -n1 basename | sort) <(grep -oE 'decisions/ADR-[0-9]{4}[^)]*\.md' docs/knowledge/README.md | sed 's|decisions/||' | sort -u)`
   - ADR numbering is contiguous; superseded ADRs carry `Superseded by ADR-00NN`.
   - Every relative Markdown link resolves (no dead targets) across `docs/knowledge/` and root docs.
3. **Roadmap freshness.** `roadmap.md` status table matches reality; dates are absolute (not "last
   week"); deferred items are still true.
4. **Skills & agents fit-for-purpose.** Each `.claude/skills/*/SKILL.md` and `.claude/agents/*.md`
   references real files, commands, and tool names; descriptions are accurate; nothing is stale or
   duplicated.
5. **CHANGELOG ↔ versions.** Module versions and `CHANGELOG.md` entries are consistent.

## Hands-off (never touch)

`docs/superpowers/specs/` and `docs/superpowers/plans/` are dated snapshots — immutable history.
Do not edit or trim them. Scope moves in `roadmap.md`, not in the dated specs.

## Finishing

- **`--audit-only`:** print the findings checklist and stop.
- **default:** apply fixes, then persist per `docs/knowledge/README.md` — a new pitfall goes to
  `gotchas.md`, a new decision becomes an ADR (and is linked from the KB README). Do not commit or
  open a PR yourself; the caller (human or workflow) owns git.
```

- [ ] **Step 2: Verify the skill is discovered and audits cleanly**

Run: `/housekeeping --audit-only`
Expected: the session loads the skill and prints a findings checklist that includes at least the
known-stale items — `CLAUDE.md` tool count says "six" (should be 13) and `docs/knowledge/README.md`
omits ADR-0014. No files are edited.

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/housekeeping/SKILL.md
git commit -m "feat(skill): add /housekeeping audit definition

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JuKKZJAwVZdj8gvxd2ujgt"
```

---

### Task 2: Dogfood the skill — fix current drift

**Files:**
- Modify: `CLAUDE.md` (lines ~11 and ~107 — the two "six" references)
- Modify: `docs/knowledge/README.md` (ADR list — add ADR-0014)

**Interfaces:**
- Consumes: the `housekeeping` skill from Task 1.
- Produces: a clean audit (a second `--audit-only` run reports no doc↔code or KB-integrity findings).

- [ ] **Step 1: Re-derive the true tool count and classes**

Run:
```bash
grep -rhoE 'name = "[a-z_]+"' --include=*.java lol-mcp-server/src/main | sort -u | wc -l
grep -rl "@McpTool" --include=*.java lol-mcp-server/src/main | grep 'adapter/in/mcp' | wc -l
```
Expected: `13` tools. Record the tool-classes number the second command prints — use it verbatim in the next step (do not assume "five").

- [ ] **Step 2: Fix the stale tool count in `CLAUDE.md`**

Line ~11 currently reads:
```
as six MCP tools across five tool classes. It is a portfolio piece — the value is the clean
```
Replace `six MCP tools across five tool classes` with `13 MCP tools across <N> tool classes`, using the class count from Step 1 for `<N>`.

Line ~107 currently reads:
```
  it is now **six** tools after `lol_spectator_featured_games` was removed
```
Replace `**six** tools` with `**13** tools`.

- [ ] **Step 3: Add ADR-0014 to the KB README ADR list**

In `docs/knowledge/README.md`, immediately after the `ADR-0013` list item, add:
```markdown
- [ADR-0014 — Non-player-keyed tools extend the tool contract](decisions/ADR-0014-non-player-keyed-tools.md)
```

- [ ] **Step 4: Re-run the audit to confirm the drift is gone**

Run: `/housekeeping --audit-only`
Expected: no findings for doc↔code tool count or the ADR-0014 KB-integrity gap. (Any remaining findings the pass surfaces should be fixed the same way before committing.)

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md docs/knowledge/README.md
git commit -m "docs: housekeeping pass — sync tool count (6->13), link ADR-0014

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JuKKZJAwVZdj8gvxd2ujgt"
```

---

### Task 3: Rebuild the PR reviewer so it posts

**Files:**
- Modify (rewrite): `.github/workflows/claude-code-review.yml`

**Interfaces:**
- Consumes: `secrets.CLAUDE_CODE_OAUTH_TOKEN` (unchanged bucket).
- Produces: a reviewer that posts a top-level summary + inline comments on every non-draft PR.

Root cause being fixed: the old config passed `/code-review:code-review <PR>` as the prompt with no comment tools and `pull-requests: read`, so Claude ran ~2 turns, buffered no comments, and could not post. The action's canonical automated-review form instructs Claude to post via `gh pr comment` / `mcp__github_inline_comment__create_inline_comment` and grants those tools.

- [ ] **Step 1: Replace the workflow with the canonical review form**

Overwrite `.github/workflows/claude-code-review.yml` with exactly:

```yaml
name: Claude Code Review

on:
  pull_request:
    types: [opened, synchronize, ready_for_review, reopened]

jobs:
  claude-review:
    # Skip drafts — they aren't ready for review and would waste subscription usage.
    if: ${{ !github.event.pull_request.draft }}
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write   # REQUIRED to post reviews/comments (was `read` — the no-op cause)
      id-token: write
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 1

      - name: Run Claude Code Review
        uses: anthropics/claude-code-action@v1
        with:
          claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
          prompt: |
            REPO: ${{ github.repository }}
            PR NUMBER: ${{ github.event.pull_request.number }}

            Review this pull request for a Spring Boot 4 / Spring AI MCP server (Java 21,
            bounded-context hexagonal architecture). Focus on:
            - Correctness and potential bugs, especially null-safety in Riot DTOs and error mapping.
            - Hexagonal boundary violations: adapter -> application -> domain (inward only);
              @McpTool only in adapter.in.mcp; RestClient only in adapter.out.riot.
            - Test coverage: WireMock for outbound adapters, in-memory port fakes for services.
            - Security: RIOT_API_KEY must never be logged or hard-coded.

            The PR branch is already checked out in the working directory.

            Use `gh pr comment` for top-level summary feedback.
            Use `mcp__github_inline_comment__create_inline_comment` (with `confirmed: true`) for
            specific line-level issues.
            Only post GitHub comments — do not submit review text as chat messages.
          claude_args: |
            --allowedTools "mcp__github_inline_comment__create_inline_comment,Bash(gh pr comment:*),Bash(gh pr diff:*),Bash(gh pr view:*)"
```

- [ ] **Step 2: Lint the YAML**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/claude-code-review.yml')); print('ok')"`
Expected: `ok`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/claude-code-review.yml
git commit -m "fix(ci): make PR reviewer actually post reviews

Replace the no-op plugin-slash-command prompt (buffered no comments, ran ~2
turns) with the canonical automated-review form: a direct review prompt that
posts via gh pr comment / inline-comment tools, granted via --allowedTools,
with pull-requests: write. Skip drafts.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JuKKZJAwVZdj8gvxd2ujgt"
```

- [ ] **Step 4: Live verification (do at PR time — see Task 8)**

The reviewer can only be proven against a real PR. Task 8's PR is that verification: confirm a Claude review (summary comment + any inline comments) appears on it. If none appears, inspect the run log for the `post-buffered-inline-comments` step and the token `PullRequests:` permission before considering this task done.

---

### Task 4: Let `@claude` post on PRs and issues

**Files:**
- Modify: `.github/workflows/claude.yml` (the `permissions:` block, ~lines 21-26)

**Interfaces:**
- Produces: `@claude` mentions can leave comments (previously read-only, so silent).

- [ ] **Step 1: Grant write scopes**

In `.github/workflows/claude.yml`, change the `permissions:` block from:
```yaml
    permissions:
      contents: read
      pull-requests: read
      issues: read
      id-token: write
      actions: read # Required for Claude to read CI results on PRs
```
to:
```yaml
    permissions:
      contents: read
      pull-requests: write   # post review comments when @claude-mentioned on a PR
      issues: write          # respond on issues when @claude-mentioned
      id-token: write
      actions: read # Required for Claude to read CI results on PRs
```

- [ ] **Step 2: Lint the YAML**

Run: `python -c "import yaml; yaml.safe_load(open('.github/workflows/claude.yml')); print('ok')"`
Expected: `ok`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/claude.yml
git commit -m "fix(ci): grant @claude workflow write scopes so it can respond

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JuKKZJAwVZdj8gvxd2ujgt"
```

---

### Task 5: Weekly housekeeping workflow → PR

**Files:**
- Create: `.github/workflows/housekeeping.yml`

**Interfaces:**
- Consumes: the `housekeeping` skill (Task 1); `secrets.CLAUDE_CODE_OAUTH_TOKEN`.
- Produces: a weekly, commit-gated run that edits the working tree, then branches, commits, and opens a PR titled `chore: weekly housekeeping (<year>-W<week>)`.

- [ ] **Step 1: Write the workflow**

Create `.github/workflows/housekeeping.yml` with exactly:

```yaml
name: Weekly Housekeeping

on:
  schedule:
    - cron: '0 9 * * 1'   # Mondays 09:00 UTC
  workflow_dispatch:        # manual runs always proceed (bypass the commit gate)

# Least-privilege; elevate only where needed.
permissions:
  contents: read

jobs:
  housekeeping:
    runs-on: ubuntu-latest
    permissions:
      contents: write        # create the housekeeping branch
      pull-requests: write   # open the PR
      id-token: write
    steps:
      - name: Checkout (full history for the commit gate)
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      # Gate: on a scheduled run, proceed only if commits landed in the last week.
      # Manual (workflow_dispatch) runs always proceed.
      - name: Commit gate
        id: gate
        run: |
          if [ "${{ github.event_name }}" = "workflow_dispatch" ]; then
            echo "proceed=true" >> "$GITHUB_OUTPUT"; exit 0
          fi
          COUNT="$(git rev-list --count --since='1 week ago' HEAD)"
          if [ "$COUNT" -gt 0 ]; then
            echo "proceed=true" >> "$GITHUB_OUTPUT"
            echo "::notice title=Housekeeping::$COUNT commit(s) in the last week — running."
          else
            echo "proceed=false" >> "$GITHUB_OUTPUT"
            echo "::notice title=Housekeeping skipped::No commits in the last week."
          fi

      - name: Run housekeeping skill (apply mode, edits only)
        if: steps.gate.outputs.proceed == 'true'
        uses: anthropics/claude-code-action@v1
        with:
          claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
          prompt: |
            Run the housekeeping skill to audit and fix drift in this repo's docs, knowledge base,
            roadmap, skills, and agents. Apply the fixes to the working tree. Do NOT commit, push,
            or open a pull request — a later workflow step owns all git operations. Never edit
            anything under docs/superpowers/specs/ or docs/superpowers/plans/ (immutable history).
          claude_args: |
            --allowedTools "Read,Edit,Write,Grep,Glob,Bash(grep:*),Bash(ls:*),Bash(comm:*),Bash(git status:*),Bash(git diff:*)"

      - name: Open a PR if the pass changed anything
        if: steps.gate.outputs.proceed == 'true'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          if [ -z "$(git status --porcelain)" ]; then
            echo "::notice title=Housekeeping::No changes to propose."
            exit 0
          fi
          STAMP="$(date -u +%Y-W%V)"
          BRANCH="chore/housekeeping-$STAMP"
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git checkout -b "$BRANCH"
          git add -A
          git commit -m "chore: weekly housekeeping ($STAMP)"
          git push -u origin "$BRANCH"
          gh pr create \
            --title "chore: weekly housekeeping ($STAMP)" \
            --body "Automated housekeeping pass (docs / KB / roadmap / skills / agents). Review the diff before merging." \
            --base master --head "$BRANCH"
```

- [ ] **Step 2: Lint the YAML**

Run: `python -c "import yaml; yaml.safe_load(open('.github/workflows/housekeeping.yml')); print('ok')"`
Expected: `ok`

- [ ] **Step 3: Static-check the gate logic locally**

Run (simulates the scheduled gate against real history):
```bash
git rev-list --count --since='1 week ago' HEAD
```
Expected: a non-negative integer. A value `> 0` means a scheduled run would proceed; `0` means it would skip. (This validates the gate command runs; the workflow itself is exercised in Task 8 via `workflow_dispatch`.)

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/housekeeping.yml
git commit -m "feat(ci): add weekly housekeeping workflow that opens a PR

Commit-gated (skips quiet weeks; manual dispatch always runs), runs the
/housekeeping skill on the flat-rate OAuth seat, then branches + opens a PR.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JuKKZJAwVZdj8gvxd2ujgt"
```

---

### Task 6: Path-filter `live-eval` to skip no-op merges

**Files:**
- Modify: `.github/workflows/live-eval.yml` (the `on: push:` block, ~lines 3-6)

**Interfaces:**
- Produces: metered live-eval no longer runs on merges that touch only docs / KB / skills (no server-behavior change). Full eval on both transports is unchanged for server/eval/workflow changes.

- [ ] **Step 1: Add `paths-ignore` to the push trigger**

In `.github/workflows/live-eval.yml`, change:
```yaml
on:
  push:
    branches: [ "master" ]
  workflow_dispatch:
```
to:
```yaml
on:
  push:
    branches: [ "master" ]
    # Docs / KB / skills / agents don't change server behavior — skip the metered
    # eval for those merges (e.g. the weekly housekeeping PR). Code, build files,
    # the eval harness, and workflow changes still trigger a full run on both transports.
    paths-ignore:
      - '**/*.md'
      - 'docs/**'
      - '.claude/**'
  workflow_dispatch:
```

- [ ] **Step 2: Lint the YAML**

Run: `python -c "import yaml; yaml.safe_load(open('.github/workflows/live-eval.yml')); print('ok')"`
Expected: `ok`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/live-eval.yml
git commit -m "ci(cost): skip metered live-eval on docs/KB/skill-only merges

paths-ignore for markdown, docs/, and .claude/ so the weekly housekeeping PR
and other doc-only merges don't spend a full metered eval. Full eval on both
transports still runs for server, build, eval-harness, and workflow changes.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JuKKZJAwVZdj8gvxd2ujgt"
```

---

### Task 7: Persist the decision (ADR + roadmap)

**Files:**
- Create: `docs/knowledge/decisions/ADR-0015-repo-maintenance-automation.md`
- Modify: `docs/knowledge/README.md` (ADR list — add ADR-0015)
- Modify: `docs/knowledge/roadmap.md` (close the "Claude Code Actions integration rework" row)

**Interfaces:**
- Consumes: the KB README ADR list already patched for ADR-0014 in Task 2.
- Produces: a linked ADR-0015 and an updated roadmap.

- [ ] **Step 1: Write the ADR**

Create `docs/knowledge/decisions/ADR-0015-repo-maintenance-automation.md`:

```markdown
# ADR-0015 — Repository maintenance automation

- **Status:** Accepted
- **Date:** 2026-07-19

## Context

Docs, KB, roadmap, skills, and agents drifted between feature cycles with no reusable procedure to
catch it (e.g. `CLAUDE.md` said "six tools" long after the surface reached 13; ADR-0014 was missing
from the KB index). Separately, the PR reviewer (`claude-code-review.yml`) ran green on every PR but
posted nothing: it used a plugin slash-command prompt with no comment tools and `pull-requests:
read`, so Claude buffered no comments and could not post — ~$0.16 of subscription usage per PR for
zero output.

## Decision

- A project skill, `/housekeeping`, is the single reusable definition for the maintenance pass
  (doc↔code drift, KB integrity, roadmap freshness, skill/agent fitness, changelog↔versions), with
  `docs/superpowers/specs|plans` treated as immutable history.
- A weekly, commit-gated `housekeeping.yml` runs that skill on the flat-rate `CLAUDE_CODE_OAUTH_TOKEN`
  seat and opens a PR — never merging unreviewed.
- The PR reviewer and `@claude` workflows are moved to the action's canonical posting form with
  `pull-requests: write` (and `issues: write` for `@claude`).
- Metered cost is scoped to `live-eval` (the only `ANTHROPIC_API_KEY` consumer): a `paths-ignore`
  filter skips no-op doc/skill merges; the full eval on both transports is retained. Prompt caching
  was evaluated and rejected for this workload (short independent Haiku conversations under the
  4096-token cacheable minimum).

## Consequences

- Maintenance is repeatable and partly automated; the weekly PR keeps drift bounded.
- The reviewer now produces real, reviewable feedback; verification requires observing a live PR, not
  a green run.
- The two credential buckets stay separated (ADR-0012): interactive/housekeeping on the flat seat,
  eval on the metered key. Moving any interactive Action onto the metered key is explicitly out of
  scope.
```

- [ ] **Step 2: Link ADR-0015 from the KB README**

In `docs/knowledge/README.md`, immediately after the `ADR-0014` list item (added in Task 2), add:
```markdown
- [ADR-0015 — Repository maintenance automation](decisions/ADR-0015-repo-maintenance-automation.md)
```

- [ ] **Step 3: Close the roadmap's deferred row**

In `docs/knowledge/roadmap.md`, in the "Deferred, with a home" table, replace the row:
```markdown
| **Claude Code Actions integration rework** | Own effort. The repo's `claude.yml` / `claude-code-action` wiring is to be reworked for better automation writ large. Recorded as intended, not yet scheduled. Kept in a separate credential bucket (`CLAUDE_CODE_OAUTH_TOKEN`) from the live-eval harness's `ANTHROPIC_API_KEY`. |
```
with:
```markdown
| ~~Claude Code Actions integration rework~~ | **Shipped** ([ADR-0015](decisions/ADR-0015-repo-maintenance-automation.md)). The PR reviewer now posts real reviews, `@claude` can respond, and a weekly `/housekeeping` cron opens maintenance PRs — all on `CLAUDE_CODE_OAUTH_TOKEN`, separate from live-eval's `ANTHROPIC_API_KEY`. |
```

- [ ] **Step 4: Verify links resolve**

Run:
```bash
comm -23 <(ls docs/knowledge/decisions/ADR-*.md | xargs -n1 basename | sort) <(grep -oE 'decisions/ADR-[0-9]{4}[^)]*\.md' docs/knowledge/README.md | sed 's|decisions/||' | sort -u)
```
Expected: empty output (every ADR file is linked from the README).

- [ ] **Step 5: Commit**

```bash
git add docs/knowledge/decisions/ADR-0015-repo-maintenance-automation.md docs/knowledge/README.md docs/knowledge/roadmap.md
git commit -m "docs(kb): ADR-0015 maintenance automation; close roadmap Actions row

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01JuKKZJAwVZdj8gvxd2ujgt"
```

---

### Task 8: Open the PR and verify Actions live

**Files:** none (integration/verification task)

**Interfaces:**
- Consumes: all prior tasks, on branch `chore/housekeeping-and-actions`.
- Produces: a merged-ready PR whose own CI run proves the reviewer posts and the eval filter behaves.

- [ ] **Step 1: Confirm the offline gate still passes**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (no Java changed, but confirm nothing regressed).

- [ ] **Step 2: Push and open the PR**

```bash
git push -u origin chore/housekeeping-and-actions
gh pr create --base master --head chore/housekeeping-and-actions \
  --title "chore: housekeeping skill + Claude Code Actions fix" \
  --body "Implements docs/superpowers/specs/2026-07-19-repo-housekeeping-and-actions-design.md — /housekeeping skill, weekly PR-opening cron, PR-reviewer fix (now posts), and a live-eval path filter."
```

- [ ] **Step 3: Verify the reviewer posts on this very PR**

Wait for the `Claude Code Review` run on the PR to finish, then:
```bash
gh pr view --json comments,reviews -q '{comments: (.comments|length), reviews: (.reviews|length)}'
```
Expected: a non-zero comment/review count, i.e. Claude left an actual review. If zero, open the run log and check the token `PullRequests:` line (must be `write`) and the `post-buffered-inline-comments` / tool-call output before declaring done. This is the falsifiable proof the no-op is fixed.

- [ ] **Step 4: Verify the eval filter behaved**

This PR touches `.github/**` (not in `paths-ignore`), so on merge `Live Eval` **should** run. Confirm after merge:
```bash
gh run list --workflow=live-eval.yml --limit 1
```
Expected: a run triggered by the merge. (A later docs-only housekeeping PR is what will be *skipped* — that is validated organically by the weekly cron.)

- [ ] **Step 5: Optionally smoke-test the housekeeping cron now**

```bash
gh workflow run housekeeping.yml
gh run list --workflow=housekeeping.yml --limit 1
```
Expected: a manual run (bypasses the commit gate) that either opens a `chore/housekeeping-<stamp>` PR or logs "No changes to propose" if the repo is already clean from Task 2.

---

## Self-Review

**Spec coverage:**
- Reusable `/housekeeping` skill (spec §1) → Task 1. ✅
- Weekly commit-gated PR-opening Action (spec §2) → Task 5. ✅
- PR reviewer fix: write perms + real posting config + live verification (spec §3) → Tasks 3, 8. ✅
- `@claude` write scopes (spec §3 table) → Task 4. ✅
- Metered-cost path filter, full×both retained, caching rejected with rationale (spec §4) → Task 6, ADR-0015 (Task 7). ✅
- Dogfood to fix current drift (spec §5) → Task 2. ✅
- Persist: ADR + close roadmap row (spec "Net change set") → Task 7. ✅
- Two-bucket constraint honored — all interactive/housekeeping Actions stay on `CLAUDE_CODE_OAUTH_TOKEN`. ✅

**Placeholder scan:** No TBD/TODO. The one deferred identifier at spec time (ADR number) is now concrete: **ADR-0015**. Tool count is concrete (**13**); tool-class count is derived in Task 2 Step 1 with an exact command rather than guessed.

**Type/name consistency:** Skill name `housekeeping` and its `--audit-only` arg are used identically in Tasks 1, 2, 5. Workflow filenames, the `chore/housekeeping-<stamp>` branch pattern, and `CLAUDE_CODE_OAUTH_TOKEN` / `ANTHROPIC_API_KEY` bucket usage are consistent across tasks. The `--allowedTools` tool names in Task 3 match the action's canonical review example verbatim.
```