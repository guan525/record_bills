#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

publishable_pattern="sb_""publishable_[A-Za-z0-9_-]+"
supabase_url_pattern="[a-z0-9]{20}\\.supabase\\.co"
legacy_project_ref_pattern="juttwaujeylf""spinawzk"
deploy_key_pattern="record_bills_""github"
private_key_pattern="BEGIN (OPENSSH|RSA|DSA|EC) PRIVATE KEY"

patterns=(
  "$publishable_pattern"
  "$supabase_url_pattern"
  "$legacy_project_ref_pattern"
  "$deploy_key_pattern"
  "$private_key_pattern"
)

failed=0

scan_file() {
  local file="$1"
  [[ "$file" == "scripts/harness/check-secrets.sh" ]] && return 0
  [[ -f "$file" ]] || return 0
  for pattern in "${patterns[@]}"; do
    if grep -n -E "$pattern" "$file"; then
      echo "Secret-like value found in $file" >&2
      failed=1
    fi
  done
}

while IFS= read -r -d '' file; do
  scan_file "$file"
done < <(git ls-files -z --cached --modified --others --exclude-standard)

if [[ "${CHECK_GIT_HISTORY:-0}" == "1" ]]; then
  for pattern in "${patterns[@]}"; do
    history_hits="$(git log --all --pretty=format:'%h %ad %s' --date=short -G "$pattern" -- . ':!scripts/harness/check-secrets.sh' || true)"
    if [[ -n "$history_hits" ]]; then
      printf '%s\n' "$history_hits"
      echo
      echo "Secret-like value found in git history for pattern: $pattern" >&2
      failed=1
    fi
  done
fi

if [[ "${CHECK_APK_SECRETS:-0}" == "1" ]]; then
  while IFS= read -r -d '' apk; do
    apk_hits="$(unzip -p "$apk" 'classes*.dex' 2>/dev/null | strings | grep -E "$(IFS='|'; echo "${patterns[*]}")" || true)"
    if [[ -n "$apk_hits" ]]; then
      printf '%s\n' "$apk_hits"
      echo "Secret-like value found inside APK: $apk" >&2
      failed=1
    fi
  done < <(find app/build dist -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.apks' \) -print0 2>/dev/null || true)
fi

if [[ "$failed" -ne 0 ]]; then
  cat >&2 <<'EOF'
Secret scan failed: remove local-only values from files before committing.
For known historical leaks, rotate the exposed Supabase publishable key and run a separately approved history rewrite.
EOF
  exit 1
fi

if [[ "${CHECK_GIT_HISTORY:-0}" != "1" ]]; then
  history_warning="$(git log --all --pretty=format:%H -G "$publishable_pattern" -- . ':!scripts/harness/check-secrets.sh' | head -n 1 || true)"
  if [[ -n "$history_warning" ]]; then
    echo "Secret scan warning: git history still contains publishable-key-like values; rotate the key and plan a separate history rewrite if needed." >&2
  fi
fi

echo "Secret scan passed."
