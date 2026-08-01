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

**This will turn `ruleset-drift.yml` red the next time it runs, on purpose.** `check-drift.sh`
compares `enforcement` like any other field, so a disabled ruleset *is* drift by its definition —
the daily job will go red and @-mention `FACTORY_ALERT_ISSUE` every morning until the ruleset is
re-applied. That is correct behaviour, not a second incident: say so on the alert thread (see
"After using any rung" below) so a deliberate rollback is not re-litigated as a fresh problem at
07:17 UTC.

`enforcement: "evaluate"` (dry-run) is **GitHub Enterprise only** and is not available on this
repository — there is no "try it safely" middle setting. Disabled or active.

Re-enable by re-running `scripts/rulesets/apply-rulesets.sh` from a desk, which restores the
committed JSON exactly and is then confirmed by `ruleset-drift.yml`.

## Known limitation: `ruleset-drift.yml`'s schedule goes silent after 60 days of inactivity

GitHub disables `on.schedule` triggers on a repository with no activity for 60 days, and does so
without failing anything — the workflow simply stops being invoked, with no error, no red run, no
notification. "Factory switched off" (Rung 1 or 2 above) is strongly correlated with "repo goes
quiet", which is exactly the condition that trips this. The result is a green-by-absence safety
net: no runs, no red, no signal that branch protection has drifted.

This is a platform behavior, not a bug in `ruleset-drift.yml` or `check-drift.sh` — there is no
code fix for it in this repository. Mitigation is manual: after any extended quiet period (or
before relying on the schedule again), confirm it still fires —
`https://github.com/Muddl/riot-api-mcp-server/actions/workflows/ruleset-drift.yml` → **Run
workflow** (`workflow_dispatch`) — and re-enable it from `···` → **Enable workflow** if GitHub has
disabled it. Any commit to the repository also counts as activity and resets the 60-day clock.

A cross-job freshness assertion (e.g. a separate check that alerts if `ruleset-drift.yml` has not
run in N days) would close this gap in software rather than relying on a human to remember, but
that is out of scope for this task and is tracked as a follow-up.

## After using any rung

Say so on the alert thread (`vars.FACTORY_ALERT_ISSUE`) — an unexplained silent factory is
indistinguishable from a broken one, which is the failure mode this whole sub-project exists to
remove.
