#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

required_ignores=(
  "local.properties"
  "*.env"
  "*.env.*"
  "secrets.properties"
  "app/secrets.properties"
  "*.jks"
  "*.keystore"
  "*.apk"
  "*.aab"
  "*.apks"
  ".gradle/"
  "build/"
  "app/build/"
)

for pattern in "${required_ignores[@]}"; do
  if ! grep -Fqx "$pattern" .gitignore; then
    echo "Git hygiene failed: .gitignore is missing $pattern" >&2
    exit 1
  fi
done

tracked_forbidden="$(
  git ls-files |
    grep -E '(^|/)(local\.properties|secrets\.properties|.*\.env|.*\.apk|.*\.aab|.*\.apks|.*\.jks|.*\.keystore)$|(^|/)(build|\.gradle)(/|$)' ||
    true
)"

if [[ -n "$tracked_forbidden" ]]; then
  echo "Git hygiene failed: forbidden local artifacts are tracked:" >&2
  printf '%s\n' "$tracked_forbidden" >&2
  exit 1
fi

staged_forbidden="$(
  git diff --cached --name-only |
    grep -E '(^|/)(local\.properties|secrets\.properties|.*\.env|.*\.apk|.*\.aab|.*\.apks|.*\.jks|.*\.keystore)$|(^|/)(build|\.gradle)(/|$)' ||
    true
)"

if [[ -n "$staged_forbidden" ]]; then
  echo "Git hygiene failed: forbidden local artifacts are staged:" >&2
  printf '%s\n' "$staged_forbidden" >&2
  exit 1
fi

echo "Git hygiene check passed."
