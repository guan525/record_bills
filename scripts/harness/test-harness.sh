#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
tmp_dirs=()

cleanup() {
  for dir in "${tmp_dirs[@]+"${tmp_dirs[@]}"}"; do
    rm -rf "$dir"
  done
}
trap cleanup EXIT

new_tmp_dir() {
  local dir
  dir="$(mktemp -d)"
  tmp_dirs+=("$dir")
  printf '%s\n' "$dir"
}

fail() {
  echo "Harness self-test failed: $*" >&2
  exit 1
}

copy_script() {
  local script="$1"
  local repo="$2"
  mkdir -p "$repo/scripts/harness"
  cp "$root/scripts/harness/$script" "$repo/scripts/harness/$script"
  chmod +x "$repo/scripts/harness/$script"
}

write_required_gitignore() {
  local repo="$1"
  cat > "$repo/.gitignore" <<'EOF'
local.properties
*.env
*.env.*
secrets.properties
app/secrets.properties
*.jks
*.keystore
*.apk
*.aab
*.apks
.gradle/
build/
app/build/
EOF
}

init_temp_repo() {
  local repo
  repo="$(new_tmp_dir)"
  git -C "$repo" init -q
  printf '%s\n' "$repo"
}

test_git_hygiene_requires_ignores() {
  local repo
  repo="$(init_temp_repo)"
  copy_script "check-git-hygiene.sh" "$repo"
  echo "local.properties" > "$repo/.gitignore"

  if (cd "$repo" && scripts/harness/check-git-hygiene.sh >/dev/null 2>&1); then
    fail "check-git-hygiene passed with incomplete .gitignore"
  fi
}

test_git_hygiene_blocks_tracked_local_files() {
  local repo
  repo="$(init_temp_repo)"
  copy_script "check-git-hygiene.sh" "$repo"
  write_required_gitignore "$repo"

  (cd "$repo" && scripts/harness/check-git-hygiene.sh >/dev/null) ||
    fail "check-git-hygiene failed with required ignores present"

  touch "$repo/local.properties"
  git -C "$repo" add -f local.properties

  if (cd "$repo" && scripts/harness/check-git-hygiene.sh >/dev/null 2>&1); then
    fail "check-git-hygiene passed with staged local.properties"
  fi
}

write_room_fixture() {
  local repo="$1"
  local include_synced="${2:-1}"
  mkdir -p "$repo/app/src/main/java/com/autobook/ledger/data" "$repo/supabase"
  cat > "$repo/app/src/main/java/com/autobook/ledger/data/LedgerDatabase.kt" <<'EOF'
package com.autobook.ledger.data

@Database(entities = [LedgerEntryEntity::class], version = 1, exportSchema = false)
abstract class LedgerDatabase
EOF

  cat > "$repo/app/src/main/java/com/autobook/ledger/data/LedgerEntryEntity.kt" <<'EOF'
package com.autobook.ledger.data

data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val status: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val currency: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val merchant: String,
    val title: String,
    @ColumnInfo(name = "category_path") val categoryPath: String,
    val account: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    @ColumnInfo(name = "source_app_name") val sourceAppName: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    val confidence: Int,
    val note: String,
    val tags: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
EOF
  if [[ "$include_synced" == "1" ]]; then
    cat >> "$repo/app/src/main/java/com/autobook/ledger/data/LedgerEntryEntity.kt" <<'EOF'
    @ColumnInfo(name = "synced_at") val syncedAt: Long?,
EOF
  else
    cat >> "$repo/app/src/main/java/com/autobook/ledger/data/LedgerEntryEntity.kt" <<'EOF'
    // @ColumnInfo(name = "synced_at") val syncedAt: Long?,
EOF
  fi
  cat >> "$repo/app/src/main/java/com/autobook/ledger/data/LedgerEntryEntity.kt" <<'EOF'
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
)
EOF

  cat > "$repo/supabase/schema.sql" <<'EOF'
create table public.ledger_entries (
  id text primary key,
  type text,
  status text,
  amount_cents bigint,
  currency text,
  occurred_at bigint,
  merchant text,
  title text,
  category_path text,
  account text,
  payment_method text,
  source_kind text,
  source_package text,
  source_app_name text,
  raw_text text,
  confidence integer,
  note text,
  tags text,
  created_at_ms bigint,
  updated_at_ms bigint,
  synced_at_ms bigint,
  is_deleted boolean
);
EOF
}

test_room_schema_ignores_comment_only_fields() {
  local repo
  repo="$(init_temp_repo)"
  copy_script "check-room-schema.sh" "$repo"

  write_room_fixture "$repo" 1
  (cd "$repo" && scripts/harness/check-room-schema.sh >/dev/null) ||
    fail "check-room-schema failed for a valid fixture"

  write_room_fixture "$repo" 0
  if (cd "$repo" && scripts/harness/check-room-schema.sh >/dev/null 2>&1); then
    fail "check-room-schema accepted a field that only appears in a comment"
  fi
}

test_run_record_slug_and_overwrite_guard() {
  local record_dir record_path
  record_dir="$(new_tmp_dir)"
  record_path="$(
    cd "$root" &&
      RUN_RECORD_DIR="$record_dir" \
      RUN_RECORD_DATE="2026-06-04" \
      scripts/harness/create-run-record.sh "Bad Slug!!" "Harness self-test"
  )"

  [[ "$(basename "$record_path")" == "2026-06-04-bad-slug.md" ]] ||
    fail "create-run-record did not sanitize slug as expected"
  [[ -s "$record_path" ]] ||
    fail "create-run-record did not write a record"

  if (
    cd "$root" &&
      RUN_RECORD_DIR="$record_dir" \
      RUN_RECORD_DATE="2026-06-04" \
      scripts/harness/create-run-record.sh "Bad Slug!!" "Harness self-test" >/dev/null 2>&1
  ); then
    fail "create-run-record overwrote an existing record without approval"
  fi
}

test_git_hygiene_requires_ignores
test_git_hygiene_blocks_tracked_local_files
test_room_schema_ignores_comment_only_fields
test_run_record_slug_and_overwrite_guard

echo "Harness self-tests passed."
