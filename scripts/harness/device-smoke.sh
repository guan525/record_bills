#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

ADB="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"

"$ADB" devices -l
"$ADB" get-state | grep -q '^device$' || {
  echo "Device smoke failed: no connected Android device in device state." >&2
  exit 1
}

"$ADB" install -r "$APK"
"$ADB" shell am start -n com.autobook.ledger/.MainActivity >/dev/null
sleep 1
"$ADB" shell pidof com.autobook.ledger >/dev/null || {
  echo "Device smoke failed: com.autobook.ledger is not running." >&2
  exit 1
}

"$ADB" shell dumpsys package com.autobook.ledger | grep -E 'READ_SMS|RECEIVE_SMS|POST_NOTIFICATIONS|QUERY_ALL_PACKAGES'
echo "Device smoke passed."

