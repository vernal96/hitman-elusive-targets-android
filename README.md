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

For local development, run `gradle :app:assembleDebug`, producing
`app/build/outputs/apk/debug/app-debug.apk`.

### Signed release APK (GitHub Actions)

GitHub Actions runs `gradle :app:assembleRelease`, verifies the APK signature,
and uploads `hitmaps-targets-release-apk` containing the installable
`app-release.apk`. The build requires **two repository Actions secrets**,
configured once, to ensure future builds use the same signing identity.

1. Generate a private signing key on your computer using JDK 17 or later:
   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore hitmaps-release.p12 -alias hitmaps -keyalg RSA -keysize 3072 -validity 10000
   ```
   Choose a strong keystore password and save it securely. PKCS12 uses the
   keystore password for the key password. The alias **must** be `hitmaps`.
2. Open your GitHub repository: **Settings → Secrets and variables → Actions →
   New repository secret**. Create both secrets:
   - `ANDROID_KEYSTORE_BASE64`: base64 encoding of the `hitmaps-release.p12` file.
     On Windows PowerShell, copy it directly to clipboard with:
     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\hitmaps-release.p12))) | Set-Clipboard
     ```
     On Linux: `base64 -w 0 hitmaps-release.p12`.
     On macOS: `base64 < hitmaps-release.p12 | tr -d '\n'`.
   - `ANDROID_SIGNING_PASSWORD`: the password from step 1.
3. Open **Actions → Build Android Release APK → Run workflow**.
   After success, download **hitmaps-targets-release-apk** from **Artifacts**.
   Inside the ZIP is the signed `app-release.apk`.

Keep `hitmaps-release.p12` and its password backed up privately. Never commit
the keystore to Git or share it. GitHub Actions fails clearly if secrets are
missing; it never uploads an unsigned APK disguised as a release.

For a local signed release, set environment variables
`ANDROID_SIGNING_KEYSTORE` (absolute path to the PKCS12 file) and
`ANDROID_SIGNING_PASSWORD`, then run `gradle :app:assembleRelease`.
Without those variables, Gradle may produce an *unsigned* local release APK
that cannot be installed.

**Upgrading from the previous debug APK:** Android cannot install a release
signed with a different key over a debug-signed app. Uninstall the debug APK
first (its app data will be removed), then install the release APK. Subsequent
release builds signed with the same keystore can update the installed app
without uninstalling, provided the `versionCode` is not lower than the
installed version.

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
