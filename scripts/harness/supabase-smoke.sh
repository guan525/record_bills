#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

property() {
  local name="$1"
  if [[ -n "${!name:-}" ]]; then
    printf '%s' "${!name}"
    return
  fi
  if [[ -f local.properties ]]; then
    awk -F= -v key="$name" '$1 == key {print substr($0, length(key) + 2)}' local.properties | tail -1
  fi
}

SUPABASE_URL="$(property SUPABASE_URL)"
SUPABASE_PUBLISHABLE_KEY="$(property SUPABASE_PUBLISHABLE_KEY)"
OWNER_KEY="${OWNER_KEY:-harness-readonly-check}"

if [[ -z "$SUPABASE_URL" || -z "$SUPABASE_PUBLISHABLE_KEY" ]]; then
  echo "Supabase smoke skipped: set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY locally." >&2
  exit 2
fi

status=$(
  curl -sS -o /tmp/auto-ledger-supabase-smoke.json -w '%{http_code}' \
    "$SUPABASE_URL/rest/v1/ledger_entries?select=id&limit=1" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
    -H "Authorization: Bearer $SUPABASE_PUBLISHABLE_KEY" \
    -H "x-owner-key: $OWNER_KEY"
)

if [[ "$status" != "200" ]]; then
  echo "Supabase smoke failed: HTTP $status" >&2
  cat /tmp/auto-ledger-supabase-smoke.json >&2
  exit 1
fi

echo "Supabase smoke passed: HTTP 200."

