# Auto Ledger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and install the first Android APK for "自动账本", a local-first automatic spending ledger with detailed categories and Supabase sync.

**Architecture:** A single Android app module uses Kotlin, Jetpack Compose, Room/SQLite, a notification listener, an SMS receiver, and a small Supabase REST client. Business logic is kept in pure Kotlin parser/category/sync classes with unit tests, while Android services persist captured entries through the Room database.

**Tech Stack:** Android SDK 35, Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Jetpack Compose, Material 3, Room, coroutines, Supabase PostgREST over `HttpURLConnection`.

---

## File Structure

- `settings.gradle.kts`: Gradle plugin repositories and app module inclusion.
- `build.gradle.kts`: Root plugin declarations.
- `local.properties`: Android SDK path for this machine.
- `gradle.properties`: AndroidX, Kotlin, and build settings.
- `app/build.gradle.kts`: App plugins, Android config, dependencies, BuildConfig Supabase values.
- `app/src/main/AndroidManifest.xml`: permissions, activities, notification listener, SMS receiver.
- `app/src/main/java/com/autobook/ledger/MainActivity.kt`: Compose entry point, permission launchers, app shell.
- `app/src/main/java/com/autobook/ledger/App.kt`: Application class and database bootstrap.
- `app/src/main/java/com/autobook/ledger/data/*`: Room entities, DAO, database, repository, preferences.
- `app/src/main/java/com/autobook/ledger/domain/*`: ledger models, parser, categories, app source scanner, statistics.
- `app/src/main/java/com/autobook/ledger/capture/*`: notification listener and SMS receiver.
- `app/src/main/java/com/autobook/ledger/sync/*`: Supabase SQL setup text and sync client.
- `app/src/main/java/com/autobook/ledger/ui/*`: Compose screens and reusable components.
- `app/src/test/java/com/autobook/ledger/domain/*`: parser, category, and stats tests.
- `supabase/schema.sql`: SQL for table, indexes, RLS policy, and sync columns.
- `README.md`: build, install, permission, and Supabase setup instructions.
- `dist/RELEASE_NOTES.md`: APK delivery notes.

## Tasks

### Task 1: Bootstrap Android Project

- [ ] Create Gradle project files pinned to locally available Android/Kotlin versions.
- [ ] Create the app module and package `com.autobook.ledger`.
- [ ] Add Room, Compose, lifecycle, coroutine, and test dependencies.
- [ ] Generate a Gradle wrapper compatible with the Android Gradle Plugin.
- [ ] Run `./gradlew :app:tasks` to verify project configuration.

### Task 2: Write Parser And Category Tests First

- [ ] Add tests proving Alipay, WeChat Pay, bank SMS, refund, transfer, subscription, and promotion-ignore parsing behavior.
- [ ] Add tests proving merchant/category inference for food, transport, shopping, housing, medical, education, entertainment, travel, digital services, finance, and income.
- [ ] Run the tests and confirm they fail before production parser/category implementation.

### Task 3: Implement Core Domain Logic

- [ ] Implement `LedgerType`, `LedgerStatus`, `ParsedBill`, `BillParser`, `CategoryCatalog`, and `CategoryClassifier`.
- [ ] Keep parser output conservative: amount required, promotion texts ignored, uncertain entries marked pending.
- [ ] Run parser/category tests until they pass.

### Task 4: Implement Room Persistence

- [ ] Add `LedgerEntryEntity`, `LedgerDao`, `LedgerDatabase`, and `LedgerRepository`.
- [ ] Add repository functions for insert/update/delete, confirm/ignore, summaries, source lists, and sync state updates.
- [ ] Wire Android services and UI through repository APIs only.

### Task 5: Implement Capture Channels

- [ ] Add `AutoLedgerNotificationListener` to parse posted notifications and store pending entries.
- [ ] Add `SmsBillReceiver` to parse incoming SMS after permission is granted.
- [ ] Add installed-app scanner that lists likely spending sources without reading private app data.
- [ ] Add permission/status helpers for notification listener and SMS.

### Task 6: Implement UI

- [ ] Build Compose app shell with bottom navigation: 首页, 明细, 统计, 来源, 设置.
- [ ] 首页 shows month totals, pending count, recent records, and quick manual entry.
- [ ] 明细 supports filtering by status/source/category and confirm/ignore/edit actions.
- [ ] 统计 shows category, merchant, account, source, and monthly trend breakdowns.
- [ ] 来源 shows notification/SMS status and likely spending apps found on the phone.
- [ ] 设置 shows sync key, Supabase setup status, sync controls, export, and permission shortcuts.

### Task 7: Implement Supabase Sync

- [ ] Add SQL setup script to `supabase/schema.sql`.
- [ ] Implement sync key generation and import.
- [ ] Implement push and pull via Supabase REST API with `apikey`, `Authorization`, and `x-owner-key` headers.
- [ ] Keep all local data when cloud setup or network fails.

### Task 8: Verify, Package, And Install

- [ ] Run unit tests.
- [ ] Run debug APK build.
- [ ] Verify APK metadata with `aapt dump badging`.
- [ ] Install APK to the connected Android device with adb.
- [ ] Copy the final APK to `dist/auto-ledger-debug.apk`.
- [ ] Write release notes and a concise user handoff.

