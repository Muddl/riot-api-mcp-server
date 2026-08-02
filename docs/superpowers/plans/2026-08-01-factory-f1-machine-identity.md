# Factory F1 — Machine Identity (GitHub App) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `HOUSEKEEPING_TOKEN` (a fine-grained PAT that acts as the maintainer) with a 1-hour GitHub App installation token, so automated PRs are authored by `muddlbot[bot]` — restoring provenance, making them approvable by the only human who can approve, and giving this repo its first identity that is *not* an admin-role ruleset bypass actor.

**Architecture:** `housekeeping.yml` mints an installation token with `actions/create-github-app-token@v3` from `vars.APP_CLIENT_ID` + `secrets.APP_PRIVATE_KEY` immediately before the step that pushes and opens the PR, commits under the bot's identity, and asserts the returned `app-slug` matches the literal slug pinned in `claude-code-review.yml`'s `allowed_bots`. The credential mechanism is proven **before** merge by a throwaway probe workflow that runs on a scratch branch — because `claude-code-action` skips itself when a workflow file differs from the default branch, so the edited `housekeeping.yml` itself cannot be validated from a branch. The end-to-end acceptance (a bot-authored PR that CI builds *and* Claude actually reviews) is verified **after** merge, by dispatch.

**Tech Stack:** GitHub Actions, GitHub Apps / installation tokens, `actions/create-github-app-token@v3`, `gh` CLI, Bash, Markdown. No application (Java) code changes.

## Global Constraints

These are facts of this repo, verified on 2026-08-01. Every task's requirements implicitly include them.

- **The machine identity's exact values** — hard-coded in workflows on purpose, and asserted at runtime:
  - App name `MuddlBot`, slug **`muddlbot`**, app id **`4459262`**, owner `Muddl`, installed on `Muddl/riot-api-mcp-server` only.
  - Bot user login **`muddlbot[bot]`**, user id **`311914422`**.
  - Git identity: `muddlbot[bot] <311914422+muddlbot[bot]@users.noreply.github.com>`.
  - Granted permissions: `actions: read`, `contents: write`, `issues: write`, `metadata: read`, `pull_requests: write`. **No `workflows` key** — deliberately withheld, matching ADR-0018.
  - Credentials already in place: `vars.APP_CLIENT_ID = Iv23liXNLyIzl30taWlq`, `secrets.APP_PRIVATE_KEY`.
- **`HOUSEKEEPING_TOKEN` is deleted last, never first.** It is still the live credential until the swap is merged *and* verified post-merge (Task 7). Deleting it earlier turns a rollback into an outage.
- **A green Actions run is not proof of success.** Four green-but-did-nothing incidents are on record (`docs/knowledge/gotchas.md`). Every verification step below asserts an *intended effect* — a PR whose author is `muddlbot[bot]`, a push that is *rejected*, a review comment that exists — never a job's exit status.
- **`claude-code-action` is silently skipped when its workflow file differs from the default branch.** It records `Skipping action due to workflow validation` as an *annotation on a successful job*. This plan edits `claude-code-review.yml`, so:
  1. **The F1 PR will not be reviewed by Claude.** State this in the PR body (Task 5, Step 3) so it is not later misread as the reviewer silently failing.
  2. `allowed_bots: 'dependabot,muddlbot'` cannot take effect until it is on `master`. Any pre-merge probe PR will see the reviewer *decline* — that is the expected pre-merge result, not a defect.
- **`allowed_bots` matching strips a trailing `[bot]` and lowercases both sides** (`src/github/validation/actor.ts` in `anthropics/claude-code-action`), so `muddlbot` matches actor `muddlbot[bot]`. Keep the existing `dependabot` entry — dropping it silently disarms review on dependency PRs.
- **A GitHub App without the Workflows permission cannot push a commit that touches `.github/workflows/`.** Cut every probe branch from `master`, never from the F1 branch (which edits two workflow files) — otherwise the probe fails at the push for a reason unrelated to what is being tested.
- **Automation may propose but never approve, and never rewrites the rules.** Withholding Workflows is what makes that structural rather than asserted.
- **Two credential buckets stay separate** (ADR-0012): the factory runs on `CLAUDE_CODE_OAUTH_TOKEN`; `ANTHROPIC_API_KEY` stays scoped to `live-eval.yml`. Nothing here touches `live-eval.yml`.
- **ADRs are amended, never edited** (`docs/knowledge/README.md` persist protocol). ADR-0018 gets a dated blockquote amendment in the style ADR-0012 and ADR-0015 already use; its body is left intact.
- **`docs/superpowers/specs/` and `docs/superpowers/plans/` are immutable history.** This plan file is written once and not retroactively edited; deviations are recorded in the PR body.
- **The maintainer is an `exempt` bypass actor on both rulesets** (ADR-0019). He therefore *can* merge the F1 PR without an approval — which is necessary here, since GitHub forbids approving one's own PR. That exemption is exactly what F1 removes for machine identities.
- Commit messages end with the repo's trailers:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap`
- Work lands on branch **`agent/f1-machine-identity`** → PR A against `master`, implementing GitHub issue **#74**. A small follow-up doc-only PR B closes the loop after post-merge verification (Task 7).

---

## File Structure

| File | Responsibility |
|---|---|
| `.github/workflows/housekeeping.yml` | **Modify.** Mint an App token immediately before the PR step; retarget the fail-loud guard; assert the app slug; author commits as `muddlbot[bot]`; update the ADR-0018 comment block to point at ADR-0020. |
| `.github/workflows/claude-code-review.yml` | **Modify.** `allowed_bots: 'dependabot,muddlbot'`, in the same commit as the identity swap. |
| `.github/workflows/probe-f1-identity.yml` | **Temporary, never merged.** Lives only on scratch branch `probe/f1-trigger`. Proves: the token mints, the slug is `muddlbot`, a push and `gh pr create` as the App work, the PR author is `muddlbot[bot]`, a workflow-touching push is *rejected*, and the App sees the PR as `BLOCKED`. Deleted with its branch in Task 3. |
| `docs/knowledge/decisions/ADR-0020-machine-identity-github-app.md` | **New (persist).** The decision, its verification, and what it closes. |
| `docs/knowledge/decisions/ADR-0018-housekeeping-pr-review-gate.md` | **Amend (not edit).** Status line + dated blockquote pointing at ADR-0020. |
| `docs/knowledge/gotchas.md` | **Modify (persist).** Three new entries appended at the bottom. |
| `docs/knowledge/README.md` | **Modify (persist).** Link ADR-0020 in the ADR list. |
| `docs/knowledge/roadmap.md` | **Modify in PR B only (Task 7).** Flip the F1 row to shipped and retire the F0 caveat that F1 was blocking. |

---

### Task 1: Confirm the registered machine identity before writing anything against it

**Files:** none — read-only verification at a desk.

**Interfaces:**
- Consumes: the GitHub App registered in issue #74 tasks 1–2, and the maintainer's own `gh` credentials.
- Produces: a verified constant set — slug `muddlbot`, app id `4459262`, bot user id `311914422`, permission map without `workflows` — used verbatim by Tasks 2 and 3, plus a transcript pasted into PR A's body (Task 5, Step 3).

Everything downstream hard-codes these values, and two of them (the slug, the bot user id) are unrecoverable from the workflow at review time. Verify them once, here, rather than discovering a typo on Monday's unattended cron.

- [ ] **Step 1: Confirm the App's identity and permission set**

The App is **private** (it was briefly public on 2026-08-01 to read these values via the API, then
re-privatized). `GET /apps/{slug}` answers only for a public App, so it now returns `404` — that is
expected, not a failure. Read the values at
<https://github.com/settings/apps/muddlbot/permissions> and confirm by eye:
Repository permissions → Actions: Read-only, Contents: Read and write, Issues: Read and write,
Metadata: Read-only, Pull requests: Read and write, **Workflows: No access**.

For the record, the API returned this while the App was public — these are the values every
downstream step hard-codes:
```json
{"slug":"muddlbot","id":4459262,"owner":"Muddl","permissions":{"actions":"read","contents":"write","issues":"write","metadata":"read","pull_requests":"write"}}
```
Note there is **no `workflows` key**.

If a `workflows` permission is present, **stop**: the App can rewrite CI, which violates the standing constraint. Remove it at that settings page (and accept the permission change on the installation) before continuing.

- [ ] **Step 2: Confirm the bot user id behind the noreply email**

Run:
```bash
gh api 'users/muddlbot%5Bbot%5D' --jq '{id, login, type}'
```
Expected:
```json
{"id":311914422,"login":"muddlbot[bot]","type":"Bot"}
```
This one works whether or not the App is public. The id is the numeric prefix in
`311914422+muddlbot[bot]@users.noreply.github.com`; GitHub only links a noreply address to the account when the id matches, so a wrong number produces commits attributed to nobody.

- [ ] **Step 3: Confirm the installation is scoped to this repository only, and the webhook is off**

Open <https://github.com/settings/installations>, click **MuddlBot** → Repository access. Expect **Only select repositories** → `Muddl/riot-api-mcp-server`, and nothing else. "All repositories" is a wider blast radius than issue #74 asked for; narrow it before continuing.

Then at <https://github.com/settings/apps/muddlbot> confirm **Webhook → Active** is unchecked and no event subscriptions are listed (issue #74, task 1). The App is a credential source, not an event source — this repo's triggers all come from Actions. The public API read taken while the App was public reported `"events": []`, consistent with that; the settings page is the authority now.

- [ ] **Step 4: Confirm the credentials are in place and the PAT is still live**

Run:
```bash
gh api repos/Muddl/riot-api-mcp-server/actions/variables --jq '.variables[] | "\(.name)=\(.value)"'
gh api repos/Muddl/riot-api-mcp-server/actions/secrets --jq '.secrets[].name'
```
Expected: `APP_CLIENT_ID=Iv23liXNLyIzl30taWlq` among the variables; both `APP_PRIVATE_KEY` **and** `HOUSEKEEPING_TOKEN` among the secrets.

`HOUSEKEEPING_TOKEN` must still be present. It is the live credential until Task 6 passes; issue #74's task 2 says to delete it, and this plan deliberately defers that to Task 7 so a failed swap is a rollback rather than an outage.

- [ ] **Step 5: Save the transcript**

Copy the output of Steps 1, 2 and 4 into a scratch file (e.g. the scratchpad, not the repo). It goes into PR A's body as evidence in Task 5, Step 3.

No commit — this task changes no files.

---

### Task 2: Swap `housekeeping.yml` onto the App token and allow the bot through the reviewer

**Files:**
- Modify: `.github/workflows/housekeeping.yml` (the `Open a PR if the pass changed anything` step, lines 70–144)
- Modify: `.github/workflows/claude-code-review.yml` (line 61, `allowed_bots`)

**Interfaces:**
- Consumes: `vars.APP_CLIENT_ID`, `secrets.APP_PRIVATE_KEY`, and the constants verified in Task 1.
- Produces: a `housekeeping` job whose PR-opening step authenticates as `muddlbot[bot]` via `steps.app-token.outputs.token`, and a `claude-code-review.yml` whose `allowed_bots` list contains `muddlbot`. Task 3 probes the mechanism these two rely on; Task 6 verifies them end to end.

Both edits ship in **one commit**. Splitting them would put a window on `master` where the bot opens PRs the reviewer refuses to review — a green run with no review, which is ADR-0018's failure shape reintroduced.

- [ ] **Step 1: Create the branch**

```bash
git checkout master
git pull --ff-only
git checkout -b agent/f1-machine-identity
```

If this plan file is not yet tracked, commit it as the branch's first commit so it lands with the
work it describes (the PR body in Task 5 links to it on `master`):

```bash
git add docs/superpowers/plans/2026-08-01-factory-f1-machine-identity.md
git commit -m "docs(plan): F1 machine identity implementation plan

Refs #74

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
```

- [ ] **Step 2: Add the token-minting step to `housekeeping.yml`**

Insert this **between** the `Run housekeeping skill (apply mode, edits only)` step (ends line 68) and the `Open a PR if the pass changed anything` step (starts line 70):

```yaml
      # Mint a 1-hour installation token for the MuddlBot GitHub App, immediately before the only
      # step that uses it — the action revokes it again in its own post-step, so the credential's
      # live window is minutes rather than the 90 days of the PAT it replaces (ADR-0020).
      #
      # Omitting `owner` and `repositories` scopes the token to the current repository, which is
      # also the only repository the App is installed on. Placed AFTER the Claude step on purpose:
      # that step can run for up to 30 minutes, and a token minted before it would have burned a
      # third of its life before the push.
      - name: Mint a GitHub App installation token
        id: app-token
        if: steps.gate.outputs.proceed == 'true'
        uses: actions/create-github-app-token@v3
        with:
          client-id: ${{ vars.APP_CLIENT_ID }}
          private-key: ${{ secrets.APP_PRIVATE_KEY }}
```

- [ ] **Step 3: Point the PR step at the App token**

In `.github/workflows/housekeeping.yml`, replace this block (lines 72–78):

```yaml
        env:
          # A PAT, deliberately not GITHUB_TOKEN. GitHub does not fire `pull_request` events for a
          # PR opened with GITHUB_TOKEN (recursion guard), so such a PR gets NO ci.yml run and NO
          # claude-code-review.yml run — it would sit there looking reviewable while nothing had
          # reviewed it. ADR-0015 says this pass opens a PR "never merging unreviewed"; honouring
          # that means the PR must be authored by a non-Actions identity. See ADR-0018.
          GH_TOKEN: ${{ secrets.HOUSEKEEPING_TOKEN }}
```

with:

```yaml
        env:
          # A GitHub App installation token, deliberately not GITHUB_TOKEN. GitHub does not fire
          # `pull_request` events for a PR opened with GITHUB_TOKEN (recursion guard), so such a PR
          # gets NO ci.yml run and NO claude-code-review.yml run — it would sit there looking
          # reviewable while nothing had reviewed it. ADR-0015 says this pass opens a PR "never
          # merging unreviewed"; honouring that means a non-Actions identity opens it (ADR-0018).
          #
          # It is an App rather than the PAT ADR-0018 shipped because a PAT acts as the person who
          # owns it: the PR was authored by Muddl, and GitHub forbids a PR author from approving
          # their own PR — so the one human who could satisfy R2's approval requirement was
          # structurally barred from satisfying it. See ADR-0020.
          GH_TOKEN: ${{ steps.app-token.outputs.token }}
          APP_SLUG: ${{ steps.app-token.outputs.app-slug }}
```

- [ ] **Step 4: Retarget the fail-loud guard and add the slug assertion**

Replace this block (lines 85–91):

```bash
          # Fail loudly rather than falling back to GITHUB_TOKEN: a silent fallback would still open
          # a PR, but one that CI and the reviewer never see — the exact failure this design exists
          # to prevent, and invisible precisely because the PR looks normal.
          if [ -z "$GH_TOKEN" ]; then
            echo "::error title=Housekeeping::HOUSEKEEPING_TOKEN secret is not set. Refusing to open a PR with GITHUB_TOKEN, which would skip CI and Claude Code Review. Create a fine-grained PAT (Contents: read/write, Pull requests: read/write) and add it as the HOUSEKEEPING_TOKEN repository secret."
            exit 1
          fi
```

with:

```bash
          # Fail loudly rather than falling back to GITHUB_TOKEN: a silent fallback would still open
          # a PR, but one that CI and the reviewer never see — the exact failure this design exists
          # to prevent, and invisible precisely because the PR looks normal.
          if [ -z "$GH_TOKEN" ]; then
            echo "::error title=Housekeeping::No GitHub App installation token was minted. Refusing to open a PR with GITHUB_TOKEN, which would skip CI and Claude Code Review. Check vars.APP_CLIENT_ID and secrets.APP_PRIVATE_KEY for the MuddlBot App (ADR-0020)."
            exit 1
          fi

          # The identity is asserted, not assumed. claude-code-review.yml's `allowed_bots` pins the
          # literal slug, and that action refuses bot-triggered runs it does not recognise by
          # RECORDING AN ANNOTATION ON A GREEN JOB. So an App swap or rename would leave every
          # machine PR unreviewed while every run stayed green — ADR-0018's failure shape exactly.
          # Convert that silence into a red job here, where it is one line and impossible to miss.
          if [ "$APP_SLUG" != "muddlbot" ]; then
            echo "::error title=Housekeeping::App slug is '$APP_SLUG', expected 'muddlbot'. claude-code-review.yml's allowed_bots pins this slug; update BOTH or the reviewer silently declines on machine PRs. See ADR-0020."
            exit 1
          fi
```

- [ ] **Step 5: Author the commit as the bot**

Replace this block (lines 100–104):

```bash
          # The commit is authored by github-actions[bot] while the push and `gh pr create` below
          # run as the PAT's identity, so the commit author and the PR's "opened by" differ on
          # purpose: event delivery keys off the credential, not the commit trailer (ADR-0018).
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
```

with:

```bash
          # Commit author and PR author are now the same identity — the App's bot user — so `git
          # log` and the PR's "opened by" agree instead of disagreeing on purpose (ADR-0020).
          # The numeric prefix is that bot user's id: `gh api 'users/muddlbot%5Bbot%5D' --jq .id`.
          # GitHub only links a noreply address to an account when the id matches, so a wrong
          # number yields commits attributed to no one. Event delivery still keys off the
          # credential, not the trailer.
          git config user.name  "muddlbot[bot]"
          git config user.email "311914422+muddlbot[bot]@users.noreply.github.com"
```

Leave the rest of the step untouched — the `git remote set-url` / `::add-mask::` / `extraheader` push dance is unrelated to *which* credential is used, and is still required because `claude-code-action` revokes the git credential on its way out (`gotchas.md`).

- [ ] **Step 6: Allow the bot through the reviewer**

In `.github/workflows/claude-code-review.yml`, replace line 61:

```yaml
          allowed_bots: 'dependabot'
```

with:

```yaml
          # `muddlbot` is this repo's machine identity (ADR-0020): housekeeping.yml opens its PRs
          # with a muddlbot[bot] installation token, and this action refuses bot-triggered runs
          # unless the bot is listed — declining with an annotation on a GREEN job, so a missing or
          # misspelled entry means machine PRs go unreviewed with nothing red to notice. Matching is
          # case-insensitive and strips a trailing `[bot]`, so `muddlbot` matches `muddlbot[bot]`.
          # Keep `dependabot`: dropping it disarms review on every dependency PR.
          allowed_bots: 'dependabot,muddlbot'
```

- [ ] **Step 7: Verify both files still parse, and that no PAT reference survives**

Run:
```bash
python -c "import yaml;[yaml.safe_load(open(f)) for f in ['.github/workflows/housekeeping.yml','.github/workflows/claude-code-review.yml']];print('YAML OK')"
grep -n "HOUSEKEEPING_TOKEN" .github/workflows/housekeeping.yml || echo "no PAT reference left (expected)"
grep -n "app-token\|muddlbot" .github/workflows/housekeeping.yml .github/workflows/claude-code-review.yml
```
Expected: `YAML OK`; `no PAT reference left (expected)`; and matches showing the `app-token` step id, both `${{ steps.app-token.outputs.* }}` references, the slug assertion, the git identity, and the `allowed_bots` line.

If `python` is unavailable, use `npx --yes js-yaml .github/workflows/housekeeping.yml > /dev/null && echo "YAML OK"`. Do not skip this — a YAML error here surfaces as a workflow that silently stops appearing in the Actions tab.

- [ ] **Step 8: Commit**

```bash
git add .github/workflows/housekeeping.yml .github/workflows/claude-code-review.yml
git commit -m "feat(ci): author machine PRs as muddlbot[bot] via a GitHub App token

Replaces the HOUSEKEEPING_TOKEN PAT with a 1-hour installation token from
actions/create-github-app-token@v3. A PAT acts as the person who owns it, so
housekeeping PRs were authored by Muddl and therefore unapprovable by the only
human who could approve them (GitHub forbids self-approval, unconditionally).

Ships allowed_bots: 'dependabot,muddlbot' in the same commit — the reviewer
refuses unrecognised bots by annotating a GREEN job, so a later commit would
leave a window where machine PRs go unreviewed with nothing red to notice.

Refs #74

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
git push -u origin agent/f1-machine-identity
```

---

### Task 3: Probe the identity end to end from a scratch branch, before merging

**Files:**
- Create (on scratch branch `probe/f1-trigger` only, **never on `agent/f1-machine-identity`**): `.github/workflows/probe-f1-identity.yml`
- Delete at the end of this task: that file, the branch, the probe PR, and the probe PR's branch.

**Interfaces:**
- Consumes: `vars.APP_CLIENT_ID`, `secrets.APP_PRIVATE_KEY`, and `master` as it stands (not the F1 branch).
- Produces: recorded evidence for PR A and for ADR-0020 — the minted `app-slug`, a PR whose `author.login` is `muddlbot[bot]`, a rejected workflow-touching push, and `mergeStateStatus: BLOCKED` as seen by the App.

**Why a probe and not just a dispatch of the edited workflow.** `claude-code-action` refuses to run when its workflow file differs from the default-branch copy and skips itself with an annotation on a *successful* job, so dispatching the edited `housekeeping.yml` from this branch proves nothing (`gotchas.md`; the F0 reviewer caught a scratch commit that nearly merged for the same reason). This probe therefore validates the *credential mechanism* the edited step depends on — mint, push, `gh pr create`, identity, permission boundary — not the edited file itself. The edited file is validated post-merge in Task 6. Say exactly this in PR A rather than implying the probe covered more than it did.

**Why the probe branches from `master`.** The App has no Workflows permission, so any push introducing a commit that touches `.github/workflows/` is rejected server-side. `agent/f1-machine-identity` edits two workflow files; a probe branch cut from it would fail at the push for a reason that has nothing to do with what is being tested.

- [ ] **Step 1: Create the scratch trigger branch**

```bash
git checkout -b probe/f1-trigger agent/f1-machine-identity
```

The probe workflow file lives here and only here. A `workflow_dispatch` workflow must exist on the default branch to be dispatchable, so the trigger is a push to this branch instead.

- [ ] **Step 2: Write the probe workflow**

Create `.github/workflows/probe-f1-identity.yml`:

```yaml
name: Probe F1 identity

# TEMPORARY — this file exists only on the scratch branch `probe/f1-trigger` and is deleted with
# that branch in Task 3 Step 9 of docs/superpowers/plans/2026-08-01-factory-f1-machine-identity.md.
# It must never reach `master`.
#
# It proves the credential mechanism that the edited housekeeping.yml depends on: the App token
# mints, its slug is the one pinned in claude-code-review.yml, a push and `gh pr create` as the App
# work, the resulting PR is authored by muddlbot[bot], a push touching .github/workflows/ is
# REJECTED, and the App — unlike every other credential on this repo — sees the PR as BLOCKED.
on:
  push:
    branches: ['probe/f1-trigger']

permissions:
  contents: read

jobs:
  probe:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      # Check out master, not this branch: the App cannot push commits that touch
      # .github/workflows/, and this branch edits two workflow files. persist-credentials: false
      # because `http.<url>.extraheader` is a MULTI-VALUED git config key — leaving checkout's
      # GITHUB_TOKEN header in place would send two Authorization headers on the push below.
      - name: Checkout master
        uses: actions/checkout@v4
        with:
          ref: master
          fetch-depth: 1
          persist-credentials: false

      - name: Mint a GitHub App installation token
        id: app-token
        uses: actions/create-github-app-token@v3
        with:
          client-id: ${{ vars.APP_CLIENT_ID }}
          private-key: ${{ secrets.APP_PRIVATE_KEY }}

      - name: Assert the token belongs to muddlbot
        run: |
          SLUG="${{ steps.app-token.outputs.app-slug }}"
          echo "app-slug=$SLUG installation-id=${{ steps.app-token.outputs.installation-id }}"
          if [ "$SLUG" != "muddlbot" ]; then
            echo "::error::Expected app-slug 'muddlbot', got '$SLUG'. claude-code-review.yml pins the literal slug."
            exit 1
          fi

      - name: Push a doc-only branch as the App and open a PR
        id: pr
        env:
          GH_TOKEN: ${{ steps.app-token.outputs.token }}
        run: |
          MASTER_SHA="$(git rev-parse HEAD)"
          echo "master_sha=$MASTER_SHA" >> "$GITHUB_OUTPUT"

          BRANCH="probe/f1-app-identity-${GITHUB_RUN_ID}"
          git config user.name  "muddlbot[bot]"
          git config user.email "311914422+muddlbot[bot]@users.noreply.github.com"
          git checkout -b "$BRANCH"
          printf 'Probe of F1 machine identity, run %s. Delete with the branch.\n' "$GITHUB_RUN_ID" > probe-f1.txt
          git add probe-f1.txt
          git commit -m "probe: F1 machine identity (DO NOT MERGE)"

          # Same one-shot header the real housekeeping push uses. Masked because GitHub's
          # redaction matches the literal registered secret, never a transform of it.
          AUTH_B64="$(printf 'x-access-token:%s' "$GH_TOKEN" | base64 -w0)"
          echo "::add-mask::$AUTH_B64"
          git -c http."${GITHUB_SERVER_URL}/".extraheader="AUTHORIZATION: basic $AUTH_B64" \
            push -u origin "$BRANCH"

          URL="$(gh pr create --title 'probe: F1 machine identity (DO NOT MERGE)' \
            --body 'Temporary probe for issue #74 (F1). Verifies that a GitHub App installation token can push a branch and open a PR, that the author is muddlbot[bot], and that CI and Claude Code Review both receive the pull_request event. Close without merging.' \
            --base master --head "$BRANCH")"
          echo "$URL"
          echo "number=${URL##*/}" >> "$GITHUB_OUTPUT"
          echo "branch=$BRANCH" >> "$GITHUB_OUTPUT"

      - name: Assert the PR author is the bot, not the maintainer
        env:
          GH_TOKEN: ${{ steps.app-token.outputs.token }}
        run: |
          AUTHOR="$(gh pr view "${{ steps.pr.outputs.number }}" --json author --jq .author.login)"
          echo "PR #${{ steps.pr.outputs.number }} author = $AUTHOR"
          if [ "$AUTHOR" != "muddlbot[bot]" ]; then
            echo "::error::Expected author 'muddlbot[bot]', got '$AUTHOR'. This is the whole point of F1."
            exit 1
          fi

      # The negative half of the permission boundary. ADR-0018 withheld Workflows from the PAT and
      # ADR-0020 withholds it from the App; neither was ever tested. A SUCCESSFUL push here is the
      # failure condition.
      - name: Negative test — a workflow-touching push must be rejected
        env:
          GH_TOKEN: ${{ steps.app-token.outputs.token }}
        run: |
          BRANCH="probe/f1-workflows-denied-${GITHUB_RUN_ID}"
          git checkout -B "$BRANCH" "${{ steps.pr.outputs.master_sha }}"
          printf '\n# probe: F1 negative test — this push must be REJECTED\n' >> .github/workflows/ci.yml
          git add .github/workflows/ci.yml
          git commit -m "probe: attempt a workflow edit as the App (must be rejected)"

          AUTH_B64="$(printf 'x-access-token:%s' "$GH_TOKEN" | base64 -w0)"
          echo "::add-mask::$AUTH_B64"
          set +e
          git -c http."${GITHUB_SERVER_URL}/".extraheader="AUTHORIZATION: basic $AUTH_B64" \
            push -u origin "$BRANCH" 2>push.log
          RC=$?
          set -e
          cat push.log

          if [ "$RC" -eq 0 ]; then
            echo "::error::The App pushed a change to .github/workflows/. The Workflows permission is NOT withheld — fix it at github.com/settings/apps/muddlbot/permissions before merging (ADR-0020)."
            exit 1
          fi
          grep -qi 'workflow' push.log \
            || echo "::warning::Push was rejected, but not with the expected workflows-permission message — read the log above and confirm the cause."
          echo "::notice::Workflows permission is withheld: the App cannot rewrite CI."

      # This repo's other credentials are all minted under the admin account, which is an `exempt`
      # bypass actor on both rulesets — and mergeStateStatus is computed per-viewer, so those
      # readings prove nothing (gotchas.md). The App is `actor_type: Integration` and is NOT a
      # bypass actor, so this is the first identity here whose reading is evidence.
      - name: Assert the App sees the PR as BLOCKED
        env:
          GH_TOKEN: ${{ steps.app-token.outputs.token }}
        run: |
          STATE=UNKNOWN
          for i in 1 2 3 4 5 6; do
            STATE="$(gh pr view "${{ steps.pr.outputs.number }}" --json mergeStateStatus --jq .mergeStateStatus)"
            [ "$STATE" != "UNKNOWN" ] && break
            echo "mergeStateStatus is UNKNOWN (GitHub still computing) — retry $i"
            sleep 10
          done
          echo "mergeStateStatus as muddlbot[bot] = $STATE"
          if [ "$STATE" != "BLOCKED" ]; then
            # No backticks in this message: it is a double-quoted bash string, so backticks would
            # be command substitution rather than markdown.
            echo "::error::Expected BLOCKED (R2 requires 1 approval and a green 'Build & verify'). Got '$STATE'. Do not merge F1 until this is understood. Diagnose with the two non-viewer-relative calls from gotchas.md: gh api repos/Muddl/riot-api-mcp-server/rules/branches/master --jq '.[].type' and gh api repos/Muddl/riot-api-mcp-server/rulesets/20203635 --jq .current_user_can_bypass"
            exit 1
          fi
          echo "::notice::R2 binds the App identity — the gate is not merely configured, it applies."

      - name: Summary
        if: always()
        run: |
          {
            echo "### F1 identity probe"
            echo ""
            echo "- app-slug: \`${{ steps.app-token.outputs.app-slug }}\`"
            echo "- installation-id: \`${{ steps.app-token.outputs.installation-id }}\`"
            echo "- probe PR: #${{ steps.pr.outputs.number }} (branch \`${{ steps.pr.outputs.branch }}\`)"
            echo ""
            echo "Close the PR and delete both probe branches when done."
          } >> "$GITHUB_STEP_SUMMARY"
```

- [ ] **Step 3: Commit and push the trigger branch to fire the probe**

```bash
git add .github/workflows/probe-f1-identity.yml
git commit -m "probe: temporary F1 identity probe (not for merge)"
git push -u origin probe/f1-trigger
```

- [ ] **Step 4: Watch the probe run and confirm every assertion executed**

```bash
gh run list --workflow=probe-f1-identity.yml --limit 1
gh run watch "$(gh run list --workflow=probe-f1-identity.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
```
Expected: **success**, with these lines in the log — read them, do not accept the conclusion alone:
- `app-slug=muddlbot installation-id=<n>`
- `PR #<n> author = muddlbot[bot]`
- a push rejection containing `workflow`, then `Workflows permission is withheld`
- `mergeStateStatus as muddlbot[bot] = BLOCKED`

If the run fails at the token step, the private key or client id is wrong — re-check Task 1 Step 4 and regenerate the key at <https://github.com/settings/apps/muddlbot> if needed. If it fails at the *negative* test with `RC=0`, stop and fix the App's permissions; do not merge F1.

- [ ] **Step 5: Confirm the bot PR received the `pull_request` event**

```bash
PR=<probe PR number>
gh pr view "$PR" --json author,url --jq '{author: .author.login, url: .url}'
gh pr checks "$PR"
gh run list --workflow=ci.yml --limit 3
gh run list --workflow=claude-code-review.yml --limit 3
```
Expected: a `CI / Build & verify` run **and** a `Claude Code Review` run both exist for the probe branch. This is the ADR-0018 property — a PAT or App token fires `pull_request`, `GITHUB_TOKEN` does not — now confirmed for the App.

- [ ] **Step 6: Confirm the reviewer *declines* — and that this is the expected pre-merge result**

```bash
gh run view "$(gh run list --workflow=claude-code-review.yml --limit 1 --json databaseId --jq '.[0].databaseId')" --log | grep -i "bot\|allowed_bots\|skipping" | head -20
gh pr view "$PR" --comments | tail -20
```
Expected: the job is green and posts **no** review, because `master`'s `allowed_bots` is still `'dependabot'` and the actor is `muddlbot[bot]`. That is the pre-merge state, and it is precisely the silent-decline failure Task 2 Step 6 fixes. Record the exact message — it goes in ADR-0020 and in PR A's body as the "before" half of the evidence.

- [ ] **Step 7: Confirm the maintainer can approve a bot-authored PR**

Open the probe PR in a browser (phone is fine — this is the action that has to work from a phone all week). Confirm the **Files changed → Review changes → Approve** radio is available and not greyed out with "Pull request authors can't approve their own pull request". Do **not** submit the approval; the PR is being closed.

- [ ] **Step 8: Record the exempt-vs-App contrast**

From the desk, as the maintainer (an `exempt` actor):
```bash
gh pr view "$PR" --json mergeStateStatus,mergeable --jq '{mergeStateStatus, mergeable}'
```
Expected: **not** `BLOCKED` (`CLEAN` or `UNSTABLE`), against the App's `BLOCKED` from Step 4 — the same PR, two answers, because rules are not evaluated for an exempt actor. Save both readings; they are the evidence for the ADR-0020 consequence that closes ADR-0019's open caveat.

- [ ] **Step 9: Tear the probe down completely**

```bash
PR=<probe PR number>
gh pr close "$PR" --comment "Probe complete (F1 / #74). Closing without merging."
gh api --method DELETE "repos/Muddl/riot-api-mcp-server/git/refs/heads/probe/f1-app-identity-<run-id>"
git push origin --delete probe/f1-trigger
git checkout agent/f1-machine-identity
git branch -D probe/f1-trigger
```

Branch deletion is blocked by R1's `deletion` rule for non-exempt identities; the maintainer is exempt, so these succeed under his own credentials. The `probe/f1-workflows-denied-*` branch never existed remotely — its push was rejected, which was the point.

- [ ] **Step 10: Prove the probe cannot reach `master`**

```bash
git log --oneline master..agent/f1-machine-identity
git diff --name-only master...agent/f1-machine-identity
git ls-remote --heads origin 'probe/*'
```
Expected: exactly one commit on the F1 branch, exactly two changed files (`housekeeping.yml`, `claude-code-review.yml`), **no** `probe-f1-identity.yml` and **no** `probe-f1.txt`, and no remaining `probe/*` remote branches. F0's reviewer caught a scratch commit that would otherwise have merged; this step exists so that cannot repeat.

---

### Task 4: Persist the decision — ADR-0020, the ADR-0018 amendment, and three gotchas

**Files:**
- Create: `docs/knowledge/decisions/ADR-0020-machine-identity-github-app.md`
- Modify: `docs/knowledge/decisions/ADR-0018-housekeeping-pr-review-gate.md` (status line + amendment blockquote, body untouched)
- Modify: `docs/knowledge/gotchas.md` (append three entries)
- Modify: `docs/knowledge/README.md` (ADR index)

**Interfaces:**
- Consumes: the probe evidence from Task 3 (slug, PR author, rejected workflow push, `BLOCKED` vs the exempt reading).
- Produces: the durable record the next contributor hydrates from. `roadmap.md` is deliberately **not** touched here — it is updated in PR B (Task 7) once the claim it would make has actually been verified post-merge.

- [ ] **Step 1: Write ADR-0020**

Create `docs/knowledge/decisions/ADR-0020-machine-identity-github-app.md`:

```markdown
# ADR-0020 — The machine identity is a GitHub App, not a PAT

- **Status:** Accepted
- **Date:** 2026-08-01
- **Amends:** [ADR-0018](ADR-0018-housekeeping-pr-review-gate.md)

## Context

[ADR-0018](ADR-0018-housekeeping-pr-review-gate.md) established the right rule — a workflow-authored
PR intended for review must be opened by a **non-Actions identity**, because GitHub fires no
`pull_request` event for a PR opened with `GITHUB_TOKEN`. It shipped that rule on a fine-grained PAT
(`HOUSEKEEPING_TOKEN`) and named the App as "deferred, not rejected". Three costs came due:

- **A PAT acts as the person who owns it.** Every housekeeping PR was authored by `Muddl`, so `git
  log` and the PR list credited the maintainer with a pass he did not write.
- **That made the PR unapprovable.** GitHub forbids a PR author from approving their own pull
  request, unconditionally, and [ADR-0019](ADR-0019-gate-hardening-and-ruleset-topology.md)'s R2
  requires one approval on `master`. The only human who could approve was structurally barred from
  approving the machine's work — and *approving a PR is the one supervisory act that works well from
  a phone*, which is what makes supervised autonomy legitimate rather than a rubber stamp.
- **Every credential on this repo inherited the admin bypass.** A PAT minted under the admin account
  is an `exempt` bypass actor, so "machine identities cannot merge red" was not merely unverified but
  **unverifiable** (see `gotchas.md`, "`mergeStateStatus` is computed per-viewer").

## Decision

`housekeeping.yml` mints a **1-hour installation token** with `actions/create-github-app-token@v3`,
immediately before the step that pushes and opens the PR; the action revokes it in its own post-step.

- **App:** `MuddlBot`, slug `muddlbot`, app id `4459262`, bot user `muddlbot[bot]` (id `311914422`),
  owned by `Muddl`, installed on **this repository only**. Credentials: `vars.APP_CLIENT_ID` +
  `secrets.APP_PRIVATE_KEY`. `owner`/`repositories` are omitted so the token scopes to the current
  repository.
- **Permissions:** Contents write, Pull requests write, Issues write, Actions read, Metadata read.
  **Workflows is withheld**, matching ADR-0018's deliberate choice for the PAT: an automated pass
  maintains docs and the KB and must never be able to rewrite CI. This is now *tested* rather than
  asserted — see Consequences.
- **Commits are authored as the bot**, `muddlbot[bot] <311914422+muddlbot[bot]@users.noreply.github.com>`,
  so the commit author and the PR's "opened by" agree.
- **The slug is asserted at runtime.** `claude-code-review.yml` pins the literal slug in
  `allowed_bots`, and that action declines unrecognised bots by annotating a *green* job — so a
  rename would leave machine PRs unreviewed with nothing red to notice. The job fails loudly on a
  slug mismatch instead.
- **`allowed_bots: 'dependabot,muddlbot'` ships in the same commit** as the identity swap. A later
  commit would open a window in which the bot's PRs are refused review on green runs.
- **Fail loudly, never fall back** (ADR-0018's rule, retargeted): a missing App token exits non-zero
  rather than reaching for `GITHUB_TOKEN`, which would produce a PR no workflow ever sees.

## Consequences

- Provenance is restored and the maintainer can approve machine PRs — the supervisory act that
  actually works from a phone.
- A 90-day PAT is replaced by a token that lives for minutes. `HOUSEKEEPING_TOKEN` was deleted and
  the PAT itself revoked once the swap was verified post-merge; the App's private key is now the
  only long-lived secret, rotatable at <https://github.com/settings/apps/muddlbot>.
- **The Workflows boundary is verified, not assumed.** A probe pushed a commit touching
  `.github/workflows/ci.yml` with the App token and GitHub **rejected** it. An automated pass that
  needs to edit CI therefore fails at the push — loudly, which is the intended behaviour.
- **This is the first identity on this repo whose merge-state reading is evidence.** The App is
  `actor_type: Integration` and is not a bypass actor: on the same probe PR it reported
  `mergeStateStatus: BLOCKED` while the exempt admin account reported a non-blocked state. That
  closes the caveat ADR-0019 left open — the gate is not merely configured, it *applies* to the
  identities that run unattended. It remains true that the admin can still merge red by hand.
- **The F1 PR itself received no Claude review**, because it edits `claude-code-review.yml` and the
  action refuses to run when its workflow file differs from the default branch. Expected, stated in
  the PR body, and not evidence of a broken reviewer.
- **F2 inherits the pattern but not this identity.** `agent:go` PRs are opened from inside the
  action step with its *own* App installation token and are authored by `claude[bot]` — a different
  bot that must be added to `allowed_bots` separately.
- Known leftover scope, unchanged from ADR-0018: the `housekeeping` job still grants the ambient
  `GITHUB_TOKEN` `contents: write` and `pull-requests: write`, which nothing uses. Trimming it still
  cannot be validated before merge, so it stays for a standalone PR with a post-merge dispatch.
```

- [ ] **Step 2: Amend ADR-0018 — status line and blockquote only**

In `docs/knowledge/decisions/ADR-0018-housekeeping-pr-review-gate.md`, change line 3:

```markdown
- **Status:** Accepted
```
to:
```markdown
- **Status:** Accepted (amended by [ADR-0020](ADR-0020-machine-identity-github-app.md))
```

and insert, immediately after the `- **Amends:** [ADR-0015]...` line and before `## Context`:

```markdown

> **Amendment (2026-08-01):** the rule below — a non-Actions identity must open the PR — stands.
> Its *implementation* does not: a PAT acts as the person who owns it, so housekeeping PRs were
> authored by `Muddl` and GitHub forbids a PR author from approving their own PR, leaving the only
> human who could satisfy R2's approval requirement unable to satisfy it. The PAT was also an
> `exempt` bypass actor by inheritance, which made "machine identities cannot merge red"
> unverifiable. [ADR-0020](ADR-0020-machine-identity-github-app.md) replaces `HOUSEKEEPING_TOKEN`
> with a `muddlbot[bot]` GitHub App installation token, taking up the "natural upgrade" this ADR's
> own Consequences deferred. Read the "least privilege on the PAT" bullet as applying to the App's
> permission set, Workflows still withheld.
```

Leave the rest of the file byte-identical — the persist protocol forbids editing an ADR to reverse it.

- [ ] **Step 3: Append three gotchas**

Append to the bottom of `docs/knowledge/gotchas.md`:

```markdown
## `allowed_bots` strips `[bot]` and lowercases — but pins a literal slug you must keep in sync

`claude-code-action` refuses bot-triggered runs unless the bot's login appears in `allowed_bots`.
Matching normalises both sides — `bot.trim().toLowerCase().replace(/\[bot\]$/, "")` in
`src/github/validation/actor.ts` — so `muddlbot`, `MuddlBot` and `muddlbot[bot]` are all equivalent,
and `'*'` allows every bot (never use it on a public repo).

The trap is not the format, it is the coupling: the list holds a **literal slug**, and a refused run
declines by writing an annotation on a **green job**. Rename the App, swap it, or typo the entry, and
every machine PR silently goes unreviewed while every run stays green — ADR-0018's failure shape in a
new costume. `housekeeping.yml` therefore asserts `steps.app-token.outputs.app-slug == 'muddlbot'`
and fails the job on a mismatch, which is the only way that drift becomes visible.

## A GitHub App without the Workflows permission cannot push *any* commit touching `.github/workflows/`

The rejection is server-side and applies to the push, not to the file's final state, so it fires even
when the commit only adds a comment line. Verified 2026-08-01 by pushing a one-line edit to
`ci.yml` with the `muddlbot` installation token: rejected, exit non-zero. This is the mechanism
behind ADR-0018's and ADR-0020's "no agent identity can rewrite CI" claim — a real boundary, not a
convention.

Two practical consequences. An automated pass that legitimately needs to edit a workflow fails at the
push and must be done by hand, which is intended. And **cut probe/scratch branches for App-token
experiments from `master`, never from a feature branch that edits workflow files** — the push would
be rejected for a reason unrelated to what you are testing, and the error message points at the
workflow file rather than at your branch choice.

## The App installation is the only identity here whose `mergeStateStatus` reading is evidence

Follow-on to "`mergeStateStatus` is computed per-viewer, so an exempt account cannot falsify its own
gate". Every PAT and OAuth credential on this repo is minted under the admin account and inherits the
`RepositoryRole/5` `exempt` bypass, so their readings prove nothing. A GitHub App installation is
`actor_type: Integration` and is **not** a bypass actor.

Observed 2026-08-01 on the same probe PR, seconds apart: `muddlbot[bot]` saw `mergeStateStatus:
BLOCKED` (R2's approval requirement plus `Build & verify`), while the admin account saw a non-blocked
state. So the way to test whether a rule *applies* — rather than whether it is *configured* — is to
ask it with the App token, from inside a workflow step that already holds one. The admin can still
merge red by hand; that is the residual F0 accepted, not a regression.
```

- [ ] **Step 4: Link ADR-0020 from the knowledge-base index**

In `docs/knowledge/README.md`, append to the "Decisions (ADRs)" list, after the ADR-0019 line:

```markdown
- [ADR-0020 — The machine identity is a GitHub App, not a PAT](decisions/ADR-0020-machine-identity-github-app.md)
```

- [ ] **Step 5: Verify the links resolve and nothing else moved**

```bash
git diff --name-only
ls docs/knowledge/decisions/ADR-0020-machine-identity-github-app.md
grep -n "ADR-0020" docs/knowledge/README.md docs/knowledge/decisions/ADR-0018-housekeeping-pr-review-gate.md
git diff docs/knowledge/decisions/ADR-0018-housekeeping-pr-review-gate.md
```
Expected: four changed files (`README.md`, `ADR-0018`, `ADR-0020` new, `gotchas.md`); the ADR-0018 diff shows **only** the status line and the inserted blockquote.

- [ ] **Step 6: Commit**

```bash
git add docs/knowledge/decisions/ADR-0020-machine-identity-github-app.md \
        docs/knowledge/decisions/ADR-0018-housekeeping-pr-review-gate.md \
        docs/knowledge/gotchas.md docs/knowledge/README.md
git commit -m "docs(kb): ADR-0020 — the machine identity is a GitHub App, not a PAT

Amends ADR-0018: its rule (a non-Actions identity opens the PR) stands, its
PAT implementation does not. Records the probe evidence — the Workflows
permission is a real server-side boundary, and an App installation is the first
identity on this repo whose mergeStateStatus reading is not viewer-poisoned.

Refs #74

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
git push
```

---

### Task 5: Open PR A, self-review, and merge

**Files:** none changed — this task moves the branch to `master`.

**Interfaces:**
- Consumes: branch `agent/f1-machine-identity` (two commits: the workflow swap, the KB persist), plus the Task 1 transcript and Task 3 probe evidence.
- Produces: `master` carrying the App-token housekeeping step and `allowed_bots: 'dependabot,muddlbot'` — the precondition for Task 6.

- [ ] **Step 1: Confirm the branch contains exactly what it should**

```bash
git log --oneline master..agent/f1-machine-identity
git diff --stat master...agent/f1-machine-identity
```
Expected: two commits; six changed files (two workflows, four KB files); no `probe-*` anything.

- [ ] **Step 2: Confirm `Build & verify` is green on the branch**

The change is workflow- and docs-only, but `Build & verify` is a required check on `master` and the offline suite is the pre-merge gate. Once the PR is open (Step 3), `gh pr checks` must show it green before merging.

- [ ] **Step 3: Open the PR**

```bash
gh pr create --base master --head agent/f1-machine-identity \
  --title "feat(ci): machine identity — author automated PRs as muddlbot[bot] (F1)" \
  --body "$(cat <<'EOF'
Implements #74 (sub-project F1 of the [software-factory decomposition spec](../blob/master/docs/superpowers/specs/2026-08-01-software-factory-decomposition-design.md)), per [the F1 plan](../blob/master/docs/superpowers/plans/2026-08-01-factory-f1-machine-identity.md).

Replaces the `HOUSEKEEPING_TOKEN` PAT with a 1-hour GitHub App installation token
(`actions/create-github-app-token@v3`). A PAT acts as the person who owns it, so housekeeping PRs
were authored by `Muddl` — and GitHub forbids a PR author from approving their own PR, so the only
human who could satisfy R2's approval requirement was structurally barred from satisfying it.

**Expect no Claude review on this PR.** It edits `claude-code-review.yml`, and `claude-code-action`
refuses to run when its workflow file differs from the default-branch copy — it skips itself with an
annotation on a *successful* job. That is expected, not the reviewer silently failing.

### Verified before merge (probe on a scratch branch, since the edited workflow cannot be validated from a branch)

- `app-slug` = `muddlbot`, installation scoped to this repo only.
- App permissions: `actions:read, contents:write, issues:write, metadata:read, pull_requests:write` — **no `workflows`**.
- A push and `gh pr create` as the App succeed; the resulting PR's author is **`muddlbot[bot]`**.
- Both `CI` and `Claude Code Review` received the `pull_request` event on that bot-authored PR (the ADR-0018 property).
- The reviewer **declined** it — correctly, since `master`'s `allowed_bots` is still `dependabot`. That is exactly what this PR fixes.
- Negative test: a push touching `.github/workflows/ci.yml` as the App was **rejected**. The Workflows boundary is real, not asserted.
- `mergeStateStatus` on the probe PR: **`BLOCKED`** as `muddlbot[bot]`, non-blocked as the exempt admin — the first identity here whose reading is evidence. Closes the caveat ADR-0019 left open.

### Not yet verified (post-merge, Task 6)

The edited `housekeeping.yml` itself, and the reviewer actually *posting* on a bot PR — both require
`allowed_bots` to be on the default branch. A `Weekly Housekeeping` dispatch immediately after merge
verifies them. `HOUSEKEEPING_TOKEN` is deliberately left in place until that passes.

### Merging

This PR is authored by the maintainer, who cannot approve it and does not need to: he is an `exempt`
bypass actor on R2 (ADR-0019). Merge once `Build & verify` is green.

Refs #74
EOF
)"
```

- [ ] **Step 4: Self-review the diff on GitHub**

Read both workflow diffs line by line. Specifically confirm: the mint step carries
`if: steps.gate.outputs.proceed == 'true'` (without it the token is minted even on a skipped week);
`GH_TOKEN` points at `steps.app-token.outputs.token` and nothing references `secrets.HOUSEKEEPING_TOKEN`;
the git identity email is `311914422+muddlbot[bot]@users.noreply.github.com`; and `allowed_bots`
still contains `dependabot`.

- [ ] **Step 5: Merge once `Build & verify` is green**

```bash
gh pr checks <PR#>
gh pr merge <PR#> --merge --delete-branch
```

- [ ] **Step 6: Confirm the merge produced a `push` run**

```bash
git checkout master && git pull --ff-only
gh api "repos/Muddl/riot-api-mcp-server/actions/runs?head_sha=$(git rev-parse HEAD)" --jq '.total_count'
```
Expected: non-zero. `0` means GitHub dropped the push event (a known, occasional fault — see
`gotchas.md`); push a trivial follow-up commit to regenerate it before continuing, or Task 6's
dispatch may run against a stale registration.

---

### Task 6: Verify the acceptance criteria post-merge

**Files:** none — verification only. Its findings are recorded in Task 7's PR B.

**Interfaces:**
- Consumes: `master` as merged in Task 5.
- Produces: the evidence for issue #74's acceptance list — a housekeeping PR authored by `muddlbot[bot]`, with `ci.yml` *and* `claude-code-review.yml` both running, the review actually posted, and an approve control available to the maintainer.

This is the half the probe could not reach: `allowed_bots` only takes effect from the default branch.

- [ ] **Step 1: Check the week's branch is free before dispatching**

```bash
git ls-remote --heads origin 'chore/housekeeping-*'
gh pr list --state open --search "housekeeping"
```
The branch name is keyed to the ISO week (`chore/housekeeping-%Y-W%V`) and a dispatch bypasses the
commit gate, so a second run in the same week collides at `git push` or `gh pr create` and fails
loudly by design (`gotchas.md`). If a branch or PR for the current week exists, close the PR and
delete the branch first — as an exempt actor the maintainer can, R1's `deletion` rule notwithstanding.

- [ ] **Step 2: Dispatch the pass**

```bash
gh workflow run housekeeping.yml
sleep 5
gh run watch "$(gh run list --workflow=housekeeping.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
```

- [ ] **Step 3: Confirm the token step actually ran, not just that the job went green**

```bash
RUN=$(gh run list --workflow=housekeeping.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run view "$RUN" --log | grep -Ei "app-token|installation|No changes to propose|Create pull request|slug"
```
Three distinct outcomes, and the difference matters:
- **A PR was created** → continue to Step 4.
- **`No changes to propose`** → the pass found no drift, the push path was never reached, and the run
  proves nothing (this is exactly the 2026-07-19 false-green). Go to Step 7.
- **A failure at the slug assertion or the token mint** → fix before doing anything else;
  `HOUSEKEEPING_TOKEN` still exists, so reverting the merge restores the previous behaviour.

- [ ] **Step 4: Confirm the PR's author and that both workflows ran**

```bash
PR=$(gh pr list --state open --search "housekeeping" --json number --jq '.[0].number')
gh pr view "$PR" --json author,headRefName,url --jq '{author: .author.login, branch: .headRefName, url: .url}'
gh pr checks "$PR"
```
Expected: `author: muddlbot[bot]` — **not** `Muddl` — and both `CI / Build & verify` and a
`Claude Code Review` run present.

- [ ] **Step 5: Confirm the review was actually posted**

```bash
gh pr view "$PR" --comments | tail -40
gh api "repos/Muddl/riot-api-mcp-server/pulls/$PR/reviews" --jq '.[] | {user: .user.login, state: .state}'
```
Expected: at least one comment or review from `claude`. An empty result on a green review run means
`allowed_bots` did not match — re-read the run log for the decline message and check the slug.
This is the acceptance criterion that the whole `same commit` requirement in Task 2 exists to protect.

- [ ] **Step 6: Confirm the maintainer can approve it**

Open the PR (phone is fine). The **Approve** option must be available. Approve and merge it if the
pass's changes are good; close it if not. Either way the identity property is now demonstrated.

- [ ] **Step 7 (fallback, only if Step 3 reported `No changes to propose`): re-run the probe against merged `master`**

The identity mechanism is unverified against the real workflow until a pass produces a diff, and the
next scheduled run is Monday 09:00 UTC — possibly unattended. Rather than wait, re-create the probe
from Task 3 against merged `master`, where `allowed_bots` now includes `muddlbot`:

```bash
git checkout master && git pull --ff-only
git checkout -b probe/f1-trigger
# recreate .github/workflows/probe-f1-identity.yml exactly as in Task 3, Step 2
git add .github/workflows/probe-f1-identity.yml
git commit -m "probe: post-merge F1 review verification (not for merge)"
git push -u origin probe/f1-trigger
```
Then repeat Task 3 Steps 4–6. This time Step 6 must show the review **posted**, not declined —
that is the acceptance criterion. Tear down exactly as in Task 3, Step 9, and record in PR B that
the housekeeping-specific path remains verified only at the next pass that produces a diff.

---

### Task 7: Retire the PAT and close out F1

**Files:**
- Modify: `docs/knowledge/roadmap.md` (F1 row; the F0 caveat that F1 was blocking)

**Interfaces:**
- Consumes: the Task 6 evidence.
- Produces: PR B — a docs-only change flipping F1 to shipped — plus a repo with no `HOUSEKEEPING_TOKEN` secret and no live housekeeping PAT.

Ordering is deliberate: the credential is deleted **after** the replacement is proven, never before.

- [ ] **Step 1: Delete the repository secret**

```bash
gh secret delete HOUSEKEEPING_TOKEN --repo Muddl/riot-api-mcp-server
gh api repos/Muddl/riot-api-mcp-server/actions/secrets --jq '.secrets[].name'
```
Expected: `HOUSEKEEPING_TOKEN` absent; `APP_PRIVATE_KEY`, `CLAUDE_CODE_OAUTH_TOKEN`,
`RULESET_READ_TOKEN`, `ANTHROPIC_API_KEY`, `LOL_DEV_API_KEY`, `TFT_DEV_API_KEY` all still present.

- [ ] **Step 2: Revoke the PAT itself**

Deleting the secret hides the credential; it does not revoke it. Go to
<https://github.com/settings/tokens?type=beta>, find the fine-grained token used for housekeeping,
and **Delete** it. Confirm it no longer appears in the list.

- [ ] **Step 3: Confirm the App is still private**

It was made public briefly on 2026-08-01 to read its permission set via the API, and re-privatized
the same day. Confirm at <https://github.com/settings/apps/muddlbot> that it still reads **private**
— a public App can be installed by anyone, which is a wider surface than a single-repo machine
identity needs. Nothing in this plan requires it public: Task 1 Step 1 reads permissions from the
settings page, and the bot-user lookup works either way.

- [ ] **Step 4: Update the roadmap on a fresh branch**

```bash
git checkout master && git pull --ff-only
git checkout -b docs/f1-shipped
```

In `docs/knowledge/roadmap.md`, replace the F1 row:

```markdown
| **F1** | Machine identity (GitHub App) | ⏳ Not started | Retires the PAT. Desk-only — a private key is not a phone artifact. |
```
with:
```markdown
| **F1** | Machine identity (GitHub App) | ✅ Shipped and verified | [ADR-0020](decisions/ADR-0020-machine-identity-github-app.md). `housekeeping.yml` mints a 1-hour installation token for the `muddlbot` App (bot user `muddlbot[bot]`, Workflows withheld) instead of a PAT, so machine PRs are authored by the bot and are **approvable by the maintainer** — GitHub forbids authors approving their own PRs. `allowed_bots: 'dependabot,muddlbot'` shipped in the same commit. `HOUSEKEEPING_TOKEN` deleted and the PAT revoked **after** post-merge verification. Two boundaries moved from asserted to tested: a push touching `.github/workflows/` as the App is **rejected**, and the App — not being a bypass actor — reads `mergeStateStatus: BLOCKED` on a PR the exempt admin reads as unblocked. |
```

In the same table, amend the **F0** row's closing caveat, replacing:

```markdown
every credential on this repo inherits the admin bypass, so "machine identities cannot merge red" stays untestable until **F1**.
```
with:
```markdown
every credential on this repo inherited the admin bypass, which made "machine identities cannot merge red" untestable — **closed by F1**: the `muddlbot` App installation is not a bypass actor and read `BLOCKED` on a PR the exempt admin read as unblocked ([ADR-0020](decisions/ADR-0020-machine-identity-github-app.md)). The admin can still merge red by hand; that residual is accepted, not fixed.
```

If the sentence has since been reworded, match its current text rather than this quotation — the
substance to preserve is: the caveat is closed for machine identities, and still open for the admin.

- [ ] **Step 5: Verify the roadmap edit is confined to those two rows**

```bash
git diff --stat
git diff docs/knowledge/roadmap.md | head -40
```
Expected: one file, two hunks.

- [ ] **Step 6: Commit, open PR B, and merge**

```bash
git add docs/knowledge/roadmap.md
git commit -m "docs(kb): F1 shipped — machine identity verified end to end

Records the post-merge verification: a housekeeping PR authored by
muddlbot[bot], built by CI and actually reviewed by Claude, approvable by the
maintainer. HOUSEKEEPING_TOKEN deleted and the PAT revoked afterwards, not
before. Closes the F0 caveat for machine identities.

Closes #74

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01SHHXD6CHRYwFbWfbt9drap"
git push -u origin docs/f1-shipped
gh pr create --base master --head docs/f1-shipped \
  --title "docs(kb): F1 shipped — machine identity verified end to end" \
  --body "Follow-up to the F1 swap. Records the post-merge evidence and retires HOUSEKEEPING_TOKEN. Closes #74."
```

This PR does **not** edit any `claude-code-action` workflow, so it gets a real Claude review — treat
its findings as blocking per the usual flow. Merge when green and reviewed.

- [ ] **Step 7: Close issue #74 with the evidence**

If the PR body's `Closes #74` did not fire, close it by hand and paste the acceptance evidence:
the bot-authored PR link, the `CI` and `Claude Code Review` run links, the posted review, and the
`BLOCKED` reading. (The `agent:plan` label was already removed on 2026-08-01 — it is inert until
F2 ships, so nothing was consuming it.)

---

## Notes carried forward

- **`agent:plan` is inert today.** It is F2's trigger (issue #75, not started). No workflow listens
  for `issues: [labeled]` or sets `label_trigger:`; `claude.yml` fires only on `issues: [opened,
  assigned]` and `@claude` mentions. Labelling #74 queued nothing.
- **F2 gains a second bot identity, not this one.** Its `agent:go` PRs are opened from inside the
  action step with the action's own App token and are authored by `claude[bot]`, which must be added
  to `allowed_bots` in F2's own commit.
- **Deliberately out of scope:** trimming the housekeeping job's now-unused ambient `GITHUB_TOKEN`
  permissions (ADR-0018 defers it to a standalone PR with a post-merge dispatch, and that reasoning
  is unchanged); giving the App any Workflows access; and any change to `live-eval.yml`.
