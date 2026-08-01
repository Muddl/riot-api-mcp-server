# Software factory — decomposition and sequencing — design

- **Date:** 2026-08-01
- **Status:** Approved (brainstorming), pending implementation plans
- **Scope:** Split the roadmap's cross-cutting software-factory track into six independently
  shippable sub-projects (F0–F5), correct the gate misconfigurations found while designing them,
  and sequence the result against a week in which the maintainer supervises from a phone. This is
  the program-level spec; F0 and F2 carry their own implementation plans, F1/F3/F4/F5 carry issues
  written against the decisions recorded here.

## Problem

The roadmap's factory track lists six not-started steps and audits this repo against the
convergent-evolution primitives: sandbox ✅, durable memory ✅, human handoff ✅, control plane ◐,
work queue ⏳, event stream ⏳. That audit is accurate about what is missing. It is wrong about two
things it treats as unbuilt or safe, and both errors point the same way — **the gate is weaker than
the roadmap believes, and the parts believed hardest are already solved.**

**The branch-protection row says "Not started." It is half-built, and the built half verifies
nothing.** Repository ruleset `8769144` ("one ruleset to rule them all") has been active since
2025-10-09 carrying `deletion`, `non_fast_forward`, and
`pull_request{required_approving_review_count: 1}` — with **no required status checks at all**, and
with `{"actor_type": "RepositoryRole", "actor_id": 5, "bypass_mode": "exempt"}`. `exempt` is
stronger than `always`: rules are not run for that actor and no bypass audit entry is created. So
the one human is invisibly exempt from the only rule that exists, and nothing requires
`Build & verify` to be green before merge. The merge button is live on a red PR.

**Its `ref_name.include` is `~ALL`, not the default branch.** Verified directly:

```
GET /repos/Muddl/riot-api-mcp-server/rules/branches/agent%2Fissue-99
→ deletion, non_fast_forward, pull_request{required_approving_review_count:1}
```

A hypothetical agent branch is governed by the same PR-required rule as `master`. Nothing has hit
this because the maintainer is exempt and `housekeeping.yml` only performs a single branch
*creation* push, which is a creation rather than an update. The first non-exempt identity to push a
**second** commit to an existing branch discovers it — which, under the design as first sketched,
would have been an unattended Monday cron.

**Review loop-back cannot be triggered by reviews.** The obvious design — on a submitted review,
re-run the agent — was checked against this repo's own history. Each inline comment posts as its own
submitted review:

```
PR #67 → 9 COMMENTED reviews from `claude`
PR #63 → 4 COMMENTED reviews from `claude`
```

Nine parallel agent runs on one PR, all pushing to one branch, most losing to `non_fast_forward`,
with a "two-round cap" exceeded before round one finishes.

**`CLAUDE.md` claims live evals run post-merge. They do not.** `live-eval.yml` is
`workflow_dispatch:` only. `gotchas.md` records three bug classes only the live eval has ever caught
— boxed-vs-primitive DTO fields, `snake_case` keys, live-vs-documented field names — every one of
which passed the offline WireMock suite. The stale claim appears in at least eight places including
two accepted ADRs.

## Key reframing: the hard parts are already built; the gate is what is broken

The roadmap frames the remaining work as capability — a work queue to construct, suspend/resume to
solve, an event stream to design. Investigating each found the capability already present:

| Believed gap | Actual state |
|---|---|
| Work queue needs a custom dispatch workflow | `claude-code-action` ships **`label_trigger`**, and `track_progress: true` forces tag mode on `issues` events — preserving a custom `prompt` while restoring GitHub context, the tracking comment, and automatic branch creation. |
| Review loop-back is blocked on **suspend/resume**, "the notable gap in current implementations" | Session state dies with the ephemeral runner and no input restores it — but tag mode **already re-derives** context every run by refetching the issue/PR body, every comment including the agent's own prior sticky comment, and the diff. Suspend/resume was never needed. |
| Event stream must be designed | `execution_file` and `display_report` already exist, and a ledger convention already exists in `.superpowers/sdd/progress.md`. What is missing is not a format but a *trustworthy* one — see F3. |

**Consequence:** the sequencing inverts. The remaining cost is not building agent capability — it is
making the gate those agents pass through mean something. So the first sub-project ships **no new
agent behaviour at all**, and is therefore the only one fully verifiable before it is depended on.

Two further consequences decide F2's shape. **The roadmap already names spec quality as the control
surface** — "vague inputs yield confident, wrong output" — and of the two things a phone can do,
reading a plan is the one it is good at while reading an 800-line diff is the one it is worst at. So
intake splits into two gated phases. And **an approval must bind to an artifact, not to a
conversation**: the reviewed plan is content-addressed by commit SHA and checked mechanically, since
an instruction to an LLM is not a mechanism.

## Design

### 0. Sub-project map

| # | Sub-project | Ships | Depends on |
|---|---|---|---|
| **F0** | Harden the gate | First, at a desk | — |
| **F1** | Machine identity (GitHub App) | At a desk | F0 |
| **F2** | Issue intake, two-phase | Deployed and canaried at a desk; *used* during the trip | F0 |
| **F3** | Machine-emitted run traces | After #71 | F0 |
| **F4** | Review loop-back | After F2 | F2 |
| **F5** | Tiered autonomy | Last | F2 |

### 1. F0 — Harden the gate

Contains no `claude-code-action` behaviour change, which is what makes it verifiable today.

**Fork PRs must be fixed before any required check exists.** For a `pull_request` from a forked
repository, `GITHUB_TOKEN` is read-only *regardless of the workflow's `permissions:` key* — the key
can only reduce. `ci.yml` runs `mikepenz/action-junit-report` (checks API) and
`madrapps/jacoco-report` (PR comment); both 403 on a fork PR, and neither carries
`continue-on-error`. So `Build & verify` fails on every outside contribution today. Making it a
required check would render outside contributions to a **public portfolio repo** permanently
unmergeable. Fix with `continue-on-error: true` on both reporting steps, or gate them on
`github.event.pull_request.head.repo.fork == false`. **This is the first task in the plan, not a
footnote.**

**Ruleset surgery, as two rulesets with identical bypass.** The scoping problem is caused by one
rule, so only that rule moves — `non_fast_forward` is wanted on agent branches precisely because
F4 pushes to live PR branches, where a force-push would erase round evidence and outdate every
inline review comment.

- **R1, `~ALL`** — `deletion`, `non_fast_forward`.
- **R2, `~DEFAULT_BRANCH`** — `pull_request{1 approval}`, `required_status_checks[Build & verify]`.

**Both keep the identical admin-`exempt` bypass.** The appealing variant gives R2
`bypass_actors: []` so that nobody merges red including the admin. It is deferred, because GitHub
documents that rules across rulesets are "aggregated" with "the most restrictive version of the rule
applies" but says **nothing about how bypass is evaluated when several rulesets apply**, and
`enforcement: "evaluate"` — the dry-run mode that would de-risk it — is **"only available with
GitHub Enterprise"** and so does not exist on this free personal repo. An undocumented behaviour,
no dry-run, a sole maintainer who is forbidden from approving his own PRs, and a week away is the
combination that must not be attempted. Keeping bypass identical on both rulesets means the
cross-ruleset question never arises.

**The residual risk is stated, not solved:** required checks bind machine identities but not the
maintainer, who can still merge red. Machine PRs — the ones running unattended — cannot. That is
the property F0 buys. Closing it for the human is a post-trip item, gated on verifying layered
bypass **on a throwaway probe repo**, never on this one.

**Configuration must be applied, or it is not configuration.** `.github/rulesets/` is not a
GitHub-recognised path; nothing reads it. Committed JSON with no enforcement is ADR-0018's exact
failure shape — artifact present, property absent. So: the apply script runs **locally, by the
maintainer, under his own credentials**, never in CI. A workflow able to rewrite rulesets could
delete the approval requirement, which is strictly stronger than approving its own work and would
violate the standing constraint. A scheduled **drift check** reads the live ruleset and fails loudly
when it differs from the committed JSON.

**Bounds and control.** `timeout-minutes` on every agent job (the action exposes no timeout of its
own; its message loop hangs until the job's). Concurrency groups — **`cancel-in-progress: false` on
`ci.yml`**, because a *cancelled* check run never satisfies a required check and would block the PR
with no visible cause; `true` on `claude-code-review.yml`, where superseded reviews are pure waste.

**A kill switch and an alert path, because neither exists.** Every agent job gates on
`if: vars.FACTORY_ENABLED == 'true'`. The escalation ladder, all reachable from a mobile browser:
flip the variable → disable the workflow from the Actions tab → revoke
`CLAUDE_CODE_OAUTH_TOKEN`, which fails every agent run closed. Alerting is an `if: failure()` step
that posts a `gh issue comment` mentioning `@Muddl`, because GitHub Mobile pushes mention
notifications and `schedule`-failure email does not cover `issues`-triggered runs. **The plan must
require testing the ladder from the actual phone before departure.**

**Two corrections carried along:** close #71 (mask the derived `AUTH_HEADER`; GitHub's redaction
matches the literal registered secret, not transforms of it), and sweep the stale "post-merge"
live-eval claim across all sites — **amending ADR-0012 and ADR-0017 rather than editing them**,
per the knowledge-base reversal rule.

**Editing `claude-code-review.yml` means that PR will not be reviewed by Claude**, because the
action refuses to run when the workflow file differs from the default-branch copy and records this
as an annotation on a *successful* job. Expected; must be stated in the PR so it is not later
misread as the reviewer silently failing.

### 2. F1 — Machine identity

Replace `HOUSEKEEPING_TOKEN` with `actions/create-github-app-token@v3`. A PAT acts as the person who
owns it, so machine PRs are currently authored by the maintainer — destroying provenance and, more
concretely, making them unapprovable by the only human who could approve them, since GitHub forbids
authors from approving their own PRs unconditionally.

The App makes the author `<slug>[bot]`, so the maintainer becomes eligible to approve — and
**approving a PR is a phone-friendly action**, which is what makes supervised autonomy legitimate
rather than a rubber stamp. It also replaces a long-lived PAT with a 1-hour token and one repo
variable plus one secret.

**The App's bot must be added to `allowed_bots` in `claude-code-review.yml` in the same commit.** The
action refuses bot-triggered runs by default; a wrong slug means the reviewer silently declines, on
a green run. Withhold the **Workflows** permission, matching ADR-0018's deliberate choice for the
PAT.

**Requires the maintainer at a desk** — a private key is not a phone artifact.

### 3. F2 — Issue intake, two-phase

The work queue. Labels are the state machine, which makes the GitHub issue list the control-plane
view and the whole thing phone-operable.

- **`agent:plan`** → hydrates the knowledge base per the repo's protocol, writes an implementation
  plan, **commits it to a branch**, and posts the path and commit SHA. Touches no other file.
- The maintainer reads it on a phone, optionally edits it through GitHub's web editor (which
  produces a new SHA), then applies **`agent:go`**.
- **`agent:go`** → checks out the approved SHA and implements *that file*.

**The approval binds to an artifact, enforced deterministically.** After the agent job, a non-agent
step runs `git diff --exit-code <approved-sha> -- <plan-path>` and fails the job if the plan moved.
Nothing here relies on instructing the model not to re-plan.

**`agent:go` opens the PR from inside the action step**, via `Bash(gh pr create:*)`, using the
action's own live GitHub App installation token — the pattern the action's shipped
`ci-failure-auto-fix.yml` example uses. This matters twice over: App tokens are not subject to the
`GITHUB_TOKEN` recursion guard, so `pull_request` events fire and `Build & verify` actually reports
(without which R2 would make every agent PR permanently unmergeable); and doing it *inside* the step
avoids the credential-revocation gotcha, since the action revokes its token as the step ends.

**Consequence shipping in the same commit:** the PR is authored by `claude[bot]`, so
`claude-code-review.yml`'s `allowed_bots: 'dependabot'` must gain that bot or the agent's own PR
goes unreviewed on a green run.

**Plan immutability is redefined as "immutable once merged to `master`."** The existing rule cannot
survive a review phase whose entire purpose is refining a plan before approval. Drafts on a branch
are mutable; merged plans are history and are never edited, with deviations recorded in the PR body
and architectural ones becoming ADRs.

**Two labels need two triggers** — `label_trigger` takes a single string, so this is two jobs gated
on `github.event.label.name`, with `issues: types: [labeled]`.

**The final task is a deliberately trivial canary issue, run end to end while the maintainer
watches.** The first real execution of this workflow is necessarily its first test.

### 4. F3 — Machine-emitted run traces

The event-stream primitive, scoped to what can be trusted and safely published.

**Both obvious implementations are rejected on the same grounds.** `show_full_output` dumps every
tool result, secrets included, into the Actions log and auto-enables under `ACTIONS_STEP_DEBUG` —
and this repository, its logs, and its artifacts are **public**. Uploading the `execution_file`
artifact is the same content class in a different container, so rejecting one while shipping the
other is incoherent. The roadmap's suggestion to "start cheap with `show_full_output`" is withdrawn.

**And a run record written by the agent about itself is the lenient-self-grader failure wearing an
event-stream costume.** What ships is machine-emitted only: a job summary carrying run metadata,
triggering label, issue number, approved plan SHA, conclusion, and duration — facts emitted by
non-agent steps and by the action's own `display_report`, whose declared default is `"false"`
despite a misleading description.

Trace retention is never enabled on `live-eval.yml`, the only workflow holding Riot keys and the
metered `ANTHROPIC_API_KEY`. **Blocked on #71.**

### 5. F4 — Review loop-back

Triggered by an explicit **`agent:revise`** label, never by `pull_request_review: submitted`.

**The two-round cap is counted from GitHub's append-only issue timeline** — `labeled` events with
`label.name == "agent:revise"`. Nothing is stored or initialised, it is correct on a first run and
after any re-run, and it survives a force-push, which is why commit-counting is the wrong answer.
Removing and re-applying the label adds events rather than erasing them, so it is tamper-evident.

Enforcement is a separate `gate` job with `permissions: issues: read`, no agent tools, that exits
non-zero above two rounds; the agent job is `needs: gate`. The agent cannot edit this, because no
agent identity holds the Workflows permission. **The gate also asserts the labeller is human**
(`github.event.sender.type == 'User'`), since the agent's own `issues: write` would otherwise let it
label itself into another round.

Blocking findings — correctness, security, would-break-on-merge — are exempt from the cap; it bounds
polish, never soundness.

### 6. F5 — Tiered autonomy

Labels marking blast radius: docs/KB, server-module code, and libraries / tool contract / workflows.

**A CI check verifies the PR's changed paths against the declared tier.** An earlier draft dropped
this on the reasoning that a `pull_request` workflow runs from the PR's own merge ref and could
modify its own check; that reasoning is withdrawn. No agent identity holds the Workflows permission,
so a machine PR cannot alter it, and a deleted or renamed check job **fails closed** because the
required-check name is pinned in the ruleset. `file_path_restriction` push rules are org-only, so a
CI check is the only path-scoping enforcement available on a free personal repo. A PR spanning two
tiers takes the maximum; there is no partial blast radius.

## Net change set

| Artifact | Change |
|---|---|
| `docs/superpowers/specs/2026-08-01-software-factory-decomposition-design.md` | **New** — this spec |
| `docs/superpowers/plans/2026-08-01-factory-f0-harden-the-gate.md` | **New** — F0 plan |
| `docs/superpowers/plans/2026-08-01-factory-f2-issue-intake.md` | **New** — F2 plan |
| `docs/knowledge/roadmap.md` | Factory track rewritten: six sub-projects, corrected branch-protection state, withdrawn suspend/resume and `show_full_output` claims |
| GitHub Issues | **New** — one per sub-project; F0 and F2 pointing at their plans |

## Standing constraints (unchanged)

- **Automation may propose but never approve.** Extended here: no workflow holds an identity that
  can *rewrite the rules either*, which is strictly stronger than approving.
- **The offline suite is the pre-merge gate** and runs with no Riot API key.
- **A green Actions run is not proof of success.** Three green-but-did-nothing incidents are on
  record; every plan here asserts the intended effect, never the job's exit status.
- **Two credential buckets stay separate.** The factory runs on the flat `CLAUDE_CODE_OAUTH_TOKEN`
  seat; `ANTHROPIC_API_KEY` stays scoped to `live-eval.yml`.

## Out of scope

- **A dark factory.** The argument is maturity, not capability.
- **Cycle-time metrics.** Velocity theater is a named failure mode.
- **Cross-run session resume.** Investigated and found unnecessary.
- **Scheduling `live-eval.yml` to cover the trip.** Its preflight *skips green* on a 401/403 and
  Riot dev keys expire every 24 hours, so a weekly schedule would yield one real run followed by
  green no-ops — reintroducing "silent failure that looks like success" as the mitigation for it.
  The coverage gap during unattended weeks is recorded honestly instead.
- **Any game sub-project.** Valorant (3) and LoR (4) are untouched.
