#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon :app:assembleDebug
elif [[ -f gradle/wrapper/gradle-wrapper.jar && -x ./gradlew ]]; then
  ./gradlew --no-daemon :app:assembleDebug
else
  echo "Gradle 8.13 is required; install it or run this project in Android Studio." >&2
  echo "Alternatively use the included GitHub Actions workflow to build the APK." >&2
  exit 1
fi
printf 'APK: %s\n' "$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
