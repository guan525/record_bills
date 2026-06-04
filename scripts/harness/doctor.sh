#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

property_is_set() {
  local name="$1"
  if [[ -n "${!name:-}" ]]; then
    return 0
  fi
  [[ -f local.properties ]] && awk -F= -v key="$name" '$1 == key && length($2) > 0 {found=1} END {exit found ? 0 : 1}' local.properties
}

property_value() {
  local name="$1"
  if [[ -n "${!name:-}" ]]; then
    printf '%s' "${!name}"
    return
  fi
  if [[ -f local.properties ]]; then
    awk -F= -v key="$name" '$1 == key {print substr($0, length(key) + 2)}' local.properties | tail -1
  fi
}

status_line() {
  printf '%-24s %s\n' "$1" "$2"
}

echo "Auto Ledger harness doctor"
echo "--------------------------"
status_line "git branch" "$(git branch --show-current 2>/dev/null || echo unknown)"
status_line "git head" "$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
project_java="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java"
if [[ -x "$project_java" ]]; then
  status_line "java" "$("$project_java" -version 2>&1 | head -n 1)"
elif command -v java >/dev/null 2>&1; then
  status_line "java" "$(java -version 2>&1 | head -n 1)"
else
  status_line "java" "missing"
fi
status_line "gradle wrapper" "$([[ -x ./gradlew ]] && echo executable || echo missing)"
status_line "android sdk" "$(property_value sdk.dir | sed 's#.*#configured#' || true)"

adb_bin="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"
if [[ -x "$adb_bin" ]]; then
  status_line "adb" "$("$adb_bin" get-state 2>/dev/null || echo no-device)"
else
  status_line "adb" "missing"
fi

if property_is_set SUPABASE_URL && property_is_set SUPABASE_PUBLISHABLE_KEY; then
  status_line "supabase config" "configured"
  supabase_url="$(property_value SUPABASE_URL)"
  supabase_host="${supabase_url#*://}"
  supabase_host="${supabase_host%%/*}"
  if [[ -n "$supabase_host" ]] && python3 - "$supabase_host" <<'PY' >/dev/null 2>&1
import socket
import sys
socket.getaddrinfo(sys.argv[1], 443)
PY
  then
    status_line "supabase dns" "ok"
  else
    status_line "supabase dns" "unresolved"
  fi
else
  status_line "supabase config" "missing"
  status_line "supabase dns" "skipped"
fi

ignored_count="$(git status --short --ignored | awk '$1 == "!!" {count++} END {print count + 0}')"
status_line "ignored local files" "$ignored_count"

echo "Doctor finished."
