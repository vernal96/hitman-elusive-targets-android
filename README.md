# Hitmaps Targets — Android/Kotlin

Native full-screen elusive-target carousel for [Hitmaps](https://hitmaps.com/). UI language: Russian. Images, names, and time windows come from the public `https://api.hitmaps.com/api/web/home` endpoint. Not affiliated with Hitmaps or IO Interactive.

## Features

- Edge-to-edge vertically cropped background target art loaded from `tileUrl`.
- Active targets in color; upcoming/expired targets in black-and-white with a dark overlay.
- Countdown to target activation (upcoming) or target expiration (active), based on UTC timestamps returned by the API.
- Endless circular horizontal swipe navigation, tappable position indicators, and target count.
- Reload every five minutes while the app is on screen; refresh button; last successful result cached for offline viewing.
- Notification opt-in through the bell icon (Android 13+ runtime notification permission).
- WorkManager updates the feed about every 15 minutes in the background, plus schedules a one-time check for the published start time of every upcoming target.
- Sends one local notification when a previously known target becomes active. Initial already-active targets do not generate spam.

## Build

Requirements: Android Studio, JDK 17+, Android SDK 36 and Gradle 8.13.

1. Open this directory in Android Studio (as a Gradle project).
2. Use an installed Gradle 8.13 (Settings → Build Tools → Gradle), or generate the standard Gradle wrapper with `gradle wrapper --gradle-version 8.13` on a machine where Gradle is installed.
3. Run `gradle :app:assembleDebug` (or select Build → Build APK(s)).
4. APK path: `app/build/outputs/apk/debug/app-debug.apk`.

**GitHub Actions**: commit this folder to a GitHub repository on branch `main` and open Actions → Build Android APK → Run workflow. Download the `hitmaps-targets-debug-apk` artifact from the finished run. This builds on GitHub's Android SDK runner without requiring any local setup.

## Notifications

The application does **not** use Firebase Cloud Messaging or a server. It generates **local Android notifications** after a scheduled WorkManager sync detects an active target. Android background scheduling and Doze mode may delay checks and thus notification delivery; it is not an exact-at-the-second push service. To offer more timely server-triggered push, deploy a backend and add Firebase Cloud Messaging.

The app uses device timezone for the display, but checks status against `Instant.now()` using the server's UTC times. Supported Android version: 8.0 (API 26) or newer.

## Known limitation

This source archive does not include the official binary Gradle wrapper JAR (binary dependency downloads are unavailable in the authoring environment). Android Studio can use an installed Gradle 8.13, or GitHub Actions uses `gradle/actions/setup-gradle` directly. `gradle wrapper` will generate the standard wrapper scripts/JAR locally.

## Source files

- `TargetsRepository.kt`: downloads and parses the JSON API response; offline cache.
- `ElusiveTarget.kt`: event model/status/countdown.
- `MainActivity.kt`: Jetpack Compose full-screen swipe interface.
- `TargetsViewModel.kt`: UI state, refresh/polling.
- `TargetAlerts.kt`: background sync, scheduling, notification channel.
- `TargetsApplication.kt`: preserves notification scheduling after reboot/app launch.

To build from a terminal with installed Gradle 8.13, run `./build-apk.sh` (Linux/macOS) or `./build-apk.ps1` (PowerShell). These build helpers exit with an error if a valid Gradle installation isn't available.
