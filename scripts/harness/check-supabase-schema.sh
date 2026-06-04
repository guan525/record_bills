#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

schema_file="supabase/schema.sql"
client_file="app/src/main/java/com/autobook/ledger/sync/SupabaseSyncClient.kt"

required_columns=(
  id owner_key type status amount_cents currency occurred_at merchant title category_path account
  payment_method source_kind source_package source_app_name raw_text confidence note tags
  created_at_ms updated_at_ms synced_at_ms is_deleted updated_at
)

for column in "${required_columns[@]}"; do
  grep -q "$column" "$schema_file" || {
    echo "Supabase schema check failed: missing column $column." >&2
    exit 1
  }
done

grep -qi "enable row level security" "$schema_file" || {
  echo "Supabase schema check failed: RLS is not enabled." >&2
  exit 1
}

for action in select insert update; do
  grep -qi "for $action" "$schema_file" || {
    echo "Supabase schema check failed: missing $action policy." >&2
    exit 1
  }
done

grep -q "x-owner-key" "$schema_file" || {
  echo "Supabase schema check failed: policies must use x-owner-key." >&2
  exit 1
}

json_keys=$(sed -n 's/.*put("\([^"]*\)".*/\1/p; s/.*getString("\([^"]*\)".*/\1/p; s/.*getLong("\([^"]*\)".*/\1/p; s/.*optString("\([^"]*\)".*/\1/p; s/.*optInt("\([^"]*\)".*/\1/p; s/.*optLong("\([^"]*\)".*/\1/p; s/.*optBoolean("\([^"]*\)".*/\1/p' "$client_file" | sort -u)

for key in $json_keys; do
  grep -q "$key" "$schema_file" || {
    echo "Supabase schema check failed: JSON key $key is not present in $schema_file." >&2
    exit 1
  }
done

echo "Supabase schema check passed."

