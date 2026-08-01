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

# Argv validation comes first and is non-mutating, so it is safe to run before the GITHUB_ACTIONS
# guard below — but that guard must still precede every gh api call and both apply() invocations.
# An unrecognised flag (--dryrun, --dry_run, -n, --dry-run=true, ...) must never silently fall
# through to a live apply. Nor may a stray second argument: "" --dry-run matches the empty-string
# arm below and would otherwise perform a live apply while the operator believes it is a dry run.
if [ "$#" -gt 1 ]; then
  echo "too many arguments" >&2
  exit 2
fi

case "${1:-}" in
  "") ;;
  --dry-run) DRY_RUN=true ;;
  *) echo "unknown argument: $1" >&2; exit 2 ;;
esac

if [ -n "${GITHUB_ACTIONS:-}" ]; then
  echo "REFUSING: this script must never run in GitHub Actions. See ADR-0019." >&2
  exit 1
fi

command -v gh >/dev/null || { echo "gh CLI not found" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq not found" >&2; exit 1; }
gh auth status >/dev/null || { echo "gh is not authenticated" >&2; exit 1; }

ruleset_ids_by_name() {  # prints one id per line: 0, 1, or (ambiguous) more than 1
  gh api "repos/$REPO/rulesets" --jq ".[] | select(.name == \"$1\") | .id"
}

apply() {  # apply <json-file> [known-id]
  local file="$DIR/$1" id="${2:-}" name ids count
  name="$(jq -r .name "$file")"
  if [ -z "$id" ]; then
    ids="$(ruleset_ids_by_name "$name")"
    if [ -n "$ids" ]; then
      count="$(printf '%s\n' "$ids" | wc -l)"
      if [ "$count" -ne 1 ]; then
        echo "FATAL: $count live rulesets are named \"$name\" (ambiguous, expected at most one)." >&2
        echo "IDs: $(printf '%s ' $ids)" >&2
        exit 1
      fi
      id="$ids"
    fi
  fi
  if $DRY_RUN; then
    if [ -n "$id" ]; then
      echo "would PUT $id <- $1 ($name)"
    else
      echo "would POST <- $1 ($name)"
    fi
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
