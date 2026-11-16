#!/usr/bin/env bash
# INDRA AI - build the debug APK and install it on a connected phone.
#
# Usage:
#   bash scripts/build-and-install-android.sh            # build + install if a device is attached
#   bash scripts/build-and-install-android.sh --build    # build only
#
# Requirements: JDK 17+, Gradle 8.x, Android SDK 34 (platform + build-tools), adb on PATH.
set -euo pipefail
cd "$(dirname "$0")/.."

BUILD_ONLY=0
[ "${1:-}" = "--build" ] && BUILD_ONLY=1

fail() { echo "ERROR: $*" >&2; exit 1; }

# ---------------------------------------------------------------- prerequisites
command -v java  >/dev/null 2>&1 || fail "Java is not installed. Install JDK 17+ and set JAVA_HOME."
command -v gradle >/dev/null 2>&1 || fail "Gradle is not installed. Install Gradle 8.x (or generate a wrapper with 'gradle wrapper --gradle-version 8.7')."

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  cat >&2 <<'TXT'
ERROR: no Android SDK found.
  Set ANDROID_HOME, e.g.:
    export ANDROID_HOME=$HOME/Android/Sdk     # macOS: $HOME/Library/Android/sdk
  or create local.properties in the repository root:
    sdk.dir=/absolute/path/to/Android/Sdk
  The SDK needs: "Android SDK Platform 34", "Android SDK Build-Tools 34.x" and platform-tools (adb).
TXT
  exit 1
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  SDK_DIR=$(sed -n 's/^sdk\.dir=//p' local.properties | head -n1)
  export ANDROID_HOME="${SDK_DIR//\\/\/}"
fi
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

echo "Java:    $(java -version 2>&1 | head -n1)"
echo "Gradle:  $(gradle --version | sed -n 's/^Gradle //p' | head -n1)"
echo "SDK:     ${ANDROID_HOME}"

# -------------------------------------------------------------------- assemble
echo
echo "Building debug APK (gradle :indra-android:assembleDebug)…"
gradle :indra-android:assembleDebug --no-daemon -Pandroid

APK=$(find modules/indra-android/build/outputs/apk/debug -name "*.apk" -print -quit 2>/dev/null || true)
[ -n "$APK" ] || fail "Gradle finished but no APK was produced. Re-run with --stacktrace to see the compiler output."
echo
echo "APK: $APK ($(du -h "$APK" | cut -f1))"

[ "$BUILD_ONLY" -eq 1 ] && { echo "Build-only mode: not installing."; exit 0; }

# --------------------------------------------------------------------- install
command -v adb >/dev/null 2>&1 || fail "adb not found. Install Android platform-tools and add them to PATH, or install the APK manually."

DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [ -z "$DEVICES" ]; then
  echo
  echo "No phone detected. Enable USB debugging, connect the cable, accept the 'Allow USB debugging' prompt,"
  echo "then run: adb install -r \"$APK\""
  exit 0
fi

echo "Installing on: $DEVICES"
adb install -r "$APK"

echo "Launching INDRA AI…"
adb shell am start -n ai.indra.android.debug/ai.indra.android.SplashActivity || \
  echo "Installed, but the app could not be launched automatically - open it from your launcher."
echo
echo "Tip: on a real phone set the server to your PC's LAN address in Settings → Server."
