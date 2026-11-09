#!/usr/bin/env bash
# Daily, repository-local cleanup. Never touches Releases, source, tags, secrets or signing keys.
set -euo pipefail
REPO="${GITHUB_REPOSITORY:?missing GITHUB_REPOSITORY}"
DRY_RUN="${DRY_RUN:-false}"
NOW=$(date -u +%s)
ARTIFACT_CUTOFF=$((NOW - 86400))
RUN_CUTOFF=$((NOW - 2 * 86400))
CACHE_CUTOFF=$((NOW - 2 * 86400))
ARTIFACTS_DELETED=0
RUNS_DELETED=0
CACHES_DELETED=0
BYTES_FREED=0

delete_item() {
  local endpoint="$1" label="$2"
  if [ "$DRY_RUN" = "true" ]; then
    echo "DRY RUN: would delete $label"
  else
    gh api --method DELETE "$endpoint" >/dev/null
    echo "Deleted $label"
  fi
}

echo "## GitHub Actions storage cleanup: $REPO"
echo "Mode: $([ "$DRY_RUN" = "true" ] && echo DRY-RUN || echo LIVE)"

# 1) Delete only Actions artifacts older than one day. GitHub Releases are untouched.
while IFS=$'\t' read -r id created name size; do
  [ -n "${id:-}" ] || continue
  created_epoch=$(date -u -d "$created" +%s)
  if [ "$created_epoch" -lt "$ARTIFACT_CUTOFF" ]; then
    delete_item "repos/$REPO/actions/artifacts/$id" "artifact $name ($id)"
    ARTIFACTS_DELETED=$((ARTIFACTS_DELETED + 1))
    BYTES_FREED=$((BYTES_FREED + size))
  fi
done < <(gh api --paginate "repos/$REPO/actions/artifacts?per_page=100" --jq '.artifacts[] | select(.expired == false) | [.id,.created_at,.name,.size_in_bytes] | @tsv')

# 2) Delete only old completed failed/cancelled runs. Keep successful runs and release workflows.
while IFS=$'\t' read -r id created conclusion path; do
  [ -n "${id:-}" ] || continue
  case "$conclusion" in failure|cancelled|timed_out|startup_failure) ;; *) continue ;; esac
  case "$path" in *release*|*publish*) echo "Keeping release/publish workflow run $id"; continue ;; esac
  created_epoch=$(date -u -d "$created" +%s)
  if [ "$created_epoch" -lt "$RUN_CUTOFF" ] && [ "$id" != "${GITHUB_RUN_ID:-}" ]; then
    delete_item "repos/$REPO/actions/runs/$id" "failed/cancelled run $id"
    RUNS_DELETED=$((RUNS_DELETED + 1))
  fi
done < <(gh api --paginate "repos/$REPO/actions/runs?status=completed&per_page=100" --jq '.workflow_runs[] | [.id,.created_at,.conclusion,.path] | @tsv')

# 3) Do not touch caches while any workflow is active/queued.
ACTIVE=0
for status in in_progress queued waiting; do
  count=$(gh api "repos/$REPO/actions/runs?status=$status&per_page=100" --jq "[.workflow_runs[] | select(.id != ${GITHUB_RUN_ID:-0})] | length" 2>/dev/null || echo 1)
  ACTIVE=$((ACTIVE + count))
done

if [ "$ACTIVE" -gt 0 ]; then
  echo "Skipping cache cleanup: $ACTIVE workflow run(s) active/queued."
else
  CACHE_JSON=$(gh api --paginate "repos/$REPO/actions/caches?per_page=100" --jq '.actions_caches[] | {id,key,size:.size_in_bytes,last_accessed_at,created_at}' | jq -s '.')
  # The Telegram sticker bot stores persistent user data in bot-data-* caches.
  # Keep its newest snapshot; remove only older duplicate snapshots.
  KEEP_BOT_CACHE=$(jq -r '[.[] | select((.key // "") | startswith("bot-data-"))] | sort_by(.created_at) | last | .id // empty' <<<"$CACHE_JSON")
  while IFS=$'\t' read -r id key size last_accessed; do
    [ -n "${id:-}" ] || continue
    if [[ "$key" == bot-data-* ]] && [ "$id" = "$KEEP_BOT_CACHE" ]; then
      echo "Keeping newest persistent bot data cache $id ($key)"
      continue
    fi
    accessed_epoch=$(date -u -d "$last_accessed" +%s)
    if [ "$accessed_epoch" -lt "$CACHE_CUTOFF" ]; then
      delete_item "repos/$REPO/actions/caches/$id" "cache $key ($id)"
      CACHES_DELETED=$((CACHES_DELETED + 1))
      BYTES_FREED=$((BYTES_FREED + size))
    fi
  done < <(jq -r '.[] | [.id,.key,.size,.last_accessed_at] | @tsv' <<<"$CACHE_JSON")
fi

FREED_MB=$(awk -v b="$BYTES_FREED" 'BEGIN { printf "%.2f", b/1048576 }')
{
  echo
  echo "### Cleanup summary"
  echo "- Artifacts deleted (older than 1 day): $ARTIFACTS_DELETED"
  echo "- Failed/cancelled runs deleted (older than 2 days): $RUNS_DELETED"
  echo "- Stale caches deleted (older than 2 days, no active workflows): $CACHES_DELETED"
  echo "- Estimated artifact/cache bytes freed: $FREED_MB MB"
  echo "- Protected: Releases/assets, source code, branches/tags, secrets, signing keys, updates repositories, newest bot-data cache."
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
