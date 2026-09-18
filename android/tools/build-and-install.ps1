<#
  Build a debug APK, install it without clearing data, and optionally launch it.
  From the CodePrint Android profile (guarded ADB workflows from workout-tracker and ring-set).
#>
param(
    [string]$Serial,
    [string]$PackageName = 'com.goalmaker.app.debug',
    [string]$Module = 'app',
    [switch]$Launch
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

# android/ holds the Gradle wrapper; this script lives in android/tools.
$repo = Resolve-Path (Join-Path $PSScriptRoot '..')
$gradleWrapper = Join-Path $repo 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}

$adb = Get-AdbPath
$device = Get-AuthorizedDeviceSerial -Adb $adb -RequestedSerial $Serial

& $gradleWrapper -p $repo ":${Module}:assembleDebug" --console=plain
if ($LASTEXITCODE -ne 0) {
    throw 'Gradle build failed.'
}

$apk = Join-Path $repo "$Module\build\outputs\apk\debug\$Module-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Expected APK was not produced: $apk"
}

& $adb -s $device install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw 'ADB install failed. Existing data was not intentionally cleared.'
}

if ($Launch) {
    & $adb -s $device shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Installed the APK but failed to launch $PackageName."
    }
}

Write-Host "Installed $apk on $device." -ForegroundColor Green

