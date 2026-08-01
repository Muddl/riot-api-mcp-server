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
