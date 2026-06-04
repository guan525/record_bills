#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

db_file="app/src/main/java/com/autobook/ledger/data/LedgerDatabase.kt"
entity_file="app/src/main/java/com/autobook/ledger/data/LedgerEntryEntity.kt"
schema_file="supabase/schema.sql"

strip_line_comments() {
  sed -E 's#//.*$##; s#--.*$##' "$1"
}

db_view="$(mktemp)"
entity_view="$(mktemp)"
schema_view="$(mktemp)"
trap 'rm -f "$db_view" "$entity_view" "$schema_view"' EXIT

strip_line_comments "$db_file" > "$db_view"
strip_line_comments "$entity_file" > "$entity_view"
strip_line_comments "$schema_file" > "$schema_view"

grep -q "version = 1" "$db_view" || {
  echo "Room schema check failed: expected LedgerDatabase version = 1." >&2
  exit 1
}

if grep -q "exportSchema = false" "$db_view"; then
  echo "Room schema warning: exportSchema=false; future migrations should enable versioned Room schema export."
fi

expected_room_fields=(
  id type status amount_cents currency occurred_at merchant title category_path account
  payment_method source_kind source_package source_app_name raw_text confidence note tags
  created_at updated_at synced_at is_deleted
)

for field in "${expected_room_fields[@]}"; do
  case "$field" in
    amount_cents|occurred_at|category_path|payment_method|source_kind|source_package|source_app_name|raw_text|created_at|updated_at|synced_at|is_deleted)
      grep -q "\"$field\"" "$entity_view" || {
        echo "Room schema check failed: missing @ColumnInfo for $field in $entity_file." >&2
        exit 1
      }
      ;;
    *)
      grep -Eq "val[[:space:]]+${field}[A-Za-z]*:|@PrimaryKey val ${field}" "$entity_view" || {
        echo "Room schema check failed: missing field $field in $entity_file." >&2
        exit 1
      }
      ;;
  esac
done

expected_remote_fields=(
  id type status amount_cents currency occurred_at merchant title category_path account
  payment_method source_kind source_package source_app_name raw_text confidence note tags
  created_at_ms updated_at_ms synced_at_ms is_deleted
)

for field in "${expected_remote_fields[@]}"; do
  grep -q "$field" "$schema_view" || {
    echo "Room/Supabase mapping check failed: missing remote field $field in $schema_file." >&2
    exit 1
  }
done

echo "Room schema check passed."
