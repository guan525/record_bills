#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
AAPT="/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/aapt"
APKSIGNER="/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

"$AAPT" dump badging "$APK" | head -20
JAVA_HOME="$JAVA_HOME" "$APKSIGNER" verify --verbose "$APK"

