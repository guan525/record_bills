#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

manifest="app/src/main/AndroidManifest.xml"
app_file="app/src/main/java/com/autobook/ledger/AutoLedgerApp.kt"
activity_file="app/src/main/java/com/autobook/ledger/MainActivity.kt"
screen_file="app/src/main/java/com/autobook/ledger/ui/AutoLedgerAppScreen.kt"
service_file="app/src/main/java/com/autobook/ledger/capture/LedgerGuardService.kt"
coordinator_file="app/src/main/java/com/autobook/ledger/capture/AutoLedgerGuardCoordinator.kt"
worker_file="app/src/main/java/com/autobook/ledger/capture/ListenerHealthWorker.kt"
boot_receiver_file="app/src/main/java/com/autobook/ledger/capture/BootReceiver.kt"

require_pattern() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if ! grep -Eq "$pattern" "$file"; then
    echo "Android service check failed: $message" >&2
    exit 1
  fi
}

require_pattern 'android\.permission\.POST_NOTIFICATIONS' "$manifest" "POST_NOTIFICATIONS permission is missing from AndroidManifest.xml."
require_pattern 'android\.permission\.FOREGROUND_SERVICE' "$manifest" "FOREGROUND_SERVICE permission is missing from AndroidManifest.xml."
require_pattern 'android\.permission\.FOREGROUND_SERVICE_SPECIAL_USE' "$manifest" "FOREGROUND_SERVICE_SPECIAL_USE permission is missing from AndroidManifest.xml."
require_pattern 'android:foregroundServiceType="specialUse"' "$manifest" "LedgerGuardService must declare foregroundServiceType=specialUse."
require_pattern 'PROPERTY_SPECIAL_USE_FGS_SUBTYPE' "$manifest" "specialUse foreground service must declare PROPERTY_SPECIAL_USE_FGS_SUBTYPE."

require_pattern 'FOREGROUND_SERVICE_TYPE_SPECIAL_USE' "$service_file" "LedgerGuardService must call typed startForeground for Android 14+."
require_pattern 'ensureBackgroundChecks\(this\)' "$app_file" "AutoLedgerApp.onCreate must enqueue ListenerHealthWorker periodic checks."
require_pattern 'enqueueImmediateHealthCheck\(context\)' "$boot_receiver_file" "BootReceiver should reuse the guard coordinator for boot health checks."
require_pattern 'PeriodicWorkRequestBuilder<ListenerHealthWorker>' "$coordinator_file" "Guard coordinator must schedule ListenerHealthWorker periodic work."
require_pattern 'enqueueUniquePeriodicWork' "$coordinator_file" "Guard coordinator must enqueue a unique periodic health check."
require_pattern 'POST_NOTIFICATIONS' "$coordinator_file" "Guard coordinator must gate notifications on POST_NOTIFICATIONS."
require_pattern 'RequestPermission' "$activity_file" "MainActivity must request POST_NOTIFICATIONS at runtime."
require_pattern 'Manifest\.permission\.POST_NOTIFICATIONS' "$activity_file" "MainActivity must request the POST_NOTIFICATIONS permission."
require_pattern 'ensureGuardService\(this\)' "$activity_file" "MainActivity must start the guard service when the app is visible."
require_pattern 'canPostNotifications\(applicationContext\)' "$worker_file" "ListenerHealthWorker must not post recovery notifications without notification permission."
require_pattern 'Build\.MANUFACTURER\.equals\("Xiaomi", ignoreCase = true\)' "$screen_file" "Xiaomi guide must be gated by Build.MANUFACTURER == Xiaomi."
require_pattern 'RedmiK50GuideDialog' "$screen_file" "Redmi K50 anti-kill guide dialog is missing."
require_pattern 'com\.miui\.securitycenter' "$screen_file" "Xiaomi autostart settings package is missing."
require_pattern 'com\.miui\.permcenter\.autostart\.AutoStartManagementActivity' "$screen_file" "Xiaomi autostart settings component is missing."
require_pattern 'com\.miui\.powerkeeper\.ui\.HiddenAppsConfigActivity' "$screen_file" "Xiaomi battery unrestricted settings component is missing."
require_pattern 'miui\.intent\.action\.POWER_HIDE_MODE_APP_LIST' "$screen_file" "Xiaomi battery settings fallback intent is missing."
require_pattern 'CATEGORY_HOME' "$screen_file" "Xiaomi background lock guide must provide a home/recent-tasks step."
require_pattern 'xiaomi_auto_ledger_guide' "$screen_file" "Xiaomi guide must be persisted after first automatic bookkeeping enablement."

echo "Android service check passed."
