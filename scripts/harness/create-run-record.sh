#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

slug="${1:-manual-run}"
goal="${2:-Improve harness and product quality}"
safe_slug="$(printf '%s' "$slug" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9._-' '-' | sed -E 's/^-+|-+$//g')"
if [[ -z "$safe_slug" ]]; then
  safe_slug="manual-run"
fi

record_dir="${RUN_RECORD_DIR:-docs/agents/runs}"
mkdir -p "$record_dir"

record_date="${RUN_RECORD_DATE:-$(date +%Y-%m-%d)}"
record_path="$record_dir/${record_date}-${safe_slug}.md"

if [[ -e "$record_path" && "${OVERWRITE_RUN_RECORD:-0}" != "1" ]]; then
  echo "Run record already exists: $record_path" >&2
  echo "Set OVERWRITE_RUN_RECORD=1 to replace it." >&2
  exit 1
fi

branch="$(git branch --show-current 2>/dev/null || true)"
head="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
tracked_status="$(git status --short --untracked-files=normal)"
changed_files="$(git status --short --untracked-files=normal | sed -E 's/^...//' | sort -u)"

adb_status="not checked"
adb_bin="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"
if [[ -x "$adb_bin" ]]; then
  adb_status="$("$adb_bin" get-state 2>/dev/null || echo no-device)"
fi

supabase_status="not checked"
if [[ -n "${SUPABASE_SMOKE_RESULT:-}" ]]; then
  supabase_status="$SUPABASE_SMOKE_RESULT"
fi

{
  printf '# Agent Run Record\n\n'
  printf '## Run\n\n'
  printf -- '- Date: %s\n' "$record_date"
  printf -- '- Goal: %s\n' "$goal"
  printf -- '- Branch: %s\n' "${branch:-unknown}"
  printf -- '- Head: %s\n' "$head"
  printf -- '- Controller: Codex\n'
  printf -- '- Agents: Controller, Product, UI/UX, Frontend, Backend, Database, Test, Ops/Deploy, Docs\n\n'

  printf '## Write Scope\n\n'
  if [[ -n "$changed_files" ]]; then
    while IFS= read -r file; do
      [[ -n "$file" ]] && printf -- '- %s\n' "$file"
    done <<< "$changed_files"
  else
    printf -- '- No local changes at record creation time.\n'
  fi
  printf '\n'

  printf '## Work Summary\n\n'
  printf -- '- Product: Capture deduplication and daily-use quality improvements.\n'
  printf -- '- UI/UX: No layout change in this run unless separately noted.\n'
  printf -- '- Frontend: No Compose surface change in this run unless separately noted.\n'
  printf -- '- Backend: Repository/parser/sync behavior changes should be covered by unit tests.\n'
  printf -- '- Database: Room and Supabase schema checks must pass before commit.\n'
  printf -- '- Test: Unit tests must include behavior coverage for product changes.\n'
  printf -- '- Ops/Deploy: Secrets, build, APK metadata, and signing checks must pass.\n'
  printf -- '- Docs: Roadmap, harness, and user-facing setup docs should stay aligned.\n\n'

  printf '## Verification\n\n'
  printf '```bash\n'
  printf 'scripts/harness/run-checks.sh\n'
  printf '# Optional external checks:\n'
  printf 'scripts/harness/supabase-smoke.sh\n'
  printf 'scripts/harness/device-smoke.sh\n'
  printf '```\n\n'

  printf '## External Dependency Status\n\n'
  printf -- '- ADB state: %s\n' "$adb_status"
  printf -- '- Supabase smoke: %s\n\n' "$supabase_status"

  printf '## Git Status At Record Creation\n\n'
  printf '```text\n'
  if [[ -n "$tracked_status" ]]; then
    printf '%s\n' "$tracked_status"
  else
    printf 'clean\n'
  fi
  printf '```\n\n'

  printf '## Remaining Risks\n\n'
  printf -- '- Git history may still contain previously exposed publishable-key-like values until a separately approved history rewrite is completed.\n'
  printf -- '- Android client APKs can expose publishable client keys by design; do not treat client keys as server secrets.\n'
} > "$record_path"

echo "$record_path"
