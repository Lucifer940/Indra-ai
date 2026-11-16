#!/usr/bin/env bash
# INDRA AI - build the debug APK
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  echo "ANDROID_HOME is not set and local.properties is missing." >&2
  echo "Create local.properties with: sdk.dir=/path/to/Android/Sdk" >&2
  exit 1
fi

gradle :indra-android:assembleDebug --no-daemon
APK=$(find modules/indra-android/build/outputs/apk -name "*.apk" | head -n 1 || true)
if [ -n "$APK" ]; then
  echo
  echo "APK: $APK"
  echo "Install with: adb install -r \"$APK\""
else
  echo "Build finished but no APK was found - check the Gradle output above." >&2
  exit 1
fi
