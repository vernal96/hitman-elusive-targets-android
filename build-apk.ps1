$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Get-Command gradle -ErrorAction SilentlyContinue) {
    & gradle --no-daemon :app:assembleDebug
} elseif (Test-Path './gradle/wrapper/gradle-wrapper.jar') {
    & ./gradlew.bat --no-daemon :app:assembleDebug
} else {
    throw 'Gradle 8.13 is required. Install it or use the included GitHub Actions workflow.'
}
if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
Write-Output "APK: $PSScriptRoot/app/build/outputs/apk/debug/app-debug.apk"
