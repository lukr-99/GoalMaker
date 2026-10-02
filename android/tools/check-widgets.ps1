<#
  Build the dev app shrunk by R8 (the `minified` build type: release rules, debug key, local stack),
  install it, place every home screen widget through the launcher, and save a screenshot, the text
  each widget shows and the widget log lines. For an emulator; see docs/widgets.md.

  The app keeps its data, so sign it in and give it rows first (Settings > Developer > Sign in and
  sync, then "Sign in as dev@goalmaker.test" against `npx supabase start`). Every run places new
  copies of the widgets; the launcher puts them where there is room.

  Examples:
    .\android\tools\check-widgets.ps1 -Serial emulator-5554
    .\android\tools\check-widgets.ps1 -BuildType debug -Kinds TODAY,HABITS
    .\android\tools\check-widgets.ps1 -Apk C:\temp\app-minified.apk -Output .scratch\widgets\before
#>
param(
    [string]$Serial,
    [ValidateSet('minified', 'debug')]
    [string]$BuildType = 'minified',
    # Install this APK instead of building one.
    [string]$Apk,
    [ValidateSet('TODAY', 'HABITS', 'GOALS', 'MOTIVATION', 'QUICK_ADD')]
    [string[]]$Kinds = @('TODAY', 'HABITS', 'GOALS', 'MOTIVATION', 'QUICK_ADD'),
    [string]$Output,
    [ValidateRange(0, 120)]
    [int]$SyncSeconds = 8
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

$packageName = 'com.goalmaker.app.debug'
$pinActivity = "$packageName/com.goalmaker.app.ui.widget.WidgetPinActivity"
$androidRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$repositoryRoot = Resolve-Path (Join-Path $androidRoot '..')
if (-not $Output) {
    $Output = Join-Path $repositoryRoot ".scratch\widgets\$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}
$outputPath = [System.IO.Path]::GetFullPath($Output)
[System.IO.Directory]::CreateDirectory($outputPath) | Out-Null

# uiautomator writes UTF-8; read it as such, not in the console's code page.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$adb = Get-AdbPath
$device = Get-AuthorizedDeviceSerial -Adb $adb -RequestedSerial $Serial

# A plain function, so arguments such as -p and -n reach adb instead of PowerShell's binder.
function Invoke-Adb {
    $result = & $adb -s $device @args
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') failed." }
    $result
}

# Screenshots go to a file on the device and come back with adb pull: binary piped through
# PowerShell gets mangled.
function Save-Screenshot {
    param([Parameter(Mandatory)][string]$Name)
    $remote = "/data/local/tmp/goalmaker-$([guid]::NewGuid().ToString('N')).png"
    $target = Join-Path $outputPath "$Name.png"
    try {
        Invoke-Adb shell screencap -p $remote | Out-Null
        Invoke-Adb pull $remote $target | Out-Null
    }
    finally {
        & $adb -s $device shell rm -f $remote 2>$null | Out-Null
    }
    Write-Host "Saved $target"
}

# The screen as uiautomator sees it, one XML string (text only, so reading it through PowerShell is safe).
function Get-ScreenXml {
    $remote = '/data/local/tmp/goalmaker-ui.xml'
    & $adb -s $device shell uiautomator dump $remote 2>$null | Out-Null
    [string](& $adb -s $device exec-out cat $remote)
}

function Wait-AndTapText {
    param([Parameter(Mandatory)][string]$Text, [int]$TimeoutSeconds = 10)
    $pattern = 'text="' + [regex]::Escape($Text) + '"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $match = [regex]::Match((Get-ScreenXml), $pattern)
        if ($match.Success) {
            $x = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
            $y = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
            Invoke-Adb shell input tap $x $y | Out-Null
            return $true
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    return $false
}

# What the app's views on screen say: every text and description under the app's package.
function Save-WidgetText {
    param([Parameter(Mandatory)][string]$Name)
    $xml = Get-ScreenXml
    $lines = [regex]::Matches($xml, '<node [^>]*?text="([^"]*)"[^>]*?package="' + [regex]::Escape($packageName) + '"[^>]*?content-desc="([^"]*)"[^>]*?bounds="([^"]*)"') |
        ForEach-Object {
            $text = [System.Net.WebUtility]::HtmlDecode($_.Groups[1].Value)
            $description = [System.Net.WebUtility]::HtmlDecode($_.Groups[2].Value)
            if ($text -or $description) { "$($_.Groups[3].Value) $text $(if ($description) { "[$description]" })".Trim() }
        }
    # Windows PowerShell's HtmlDecode leaves emoji such as &#128095; alone.
    $lines = @($lines) | ForEach-Object { [regex]::Replace($_, '&#(\d+);', { param($m) [char]::ConvertFromUtf32([int]$m.Groups[1].Value) }) }
    $target = Join-Path $outputPath "$Name.txt"
    [System.IO.File]::WriteAllText($target, (@($lines) -join "`n"), [System.Text.UTF8Encoding]::new($false))
}

# R8 once folded the widget classes together and dropped the constructor Glance makes a tap
# callback with (docs/pitfalls.md). Both show in the APK's dex, so this reads it with dexdump.
function Test-WidgetDex {
    param([Parameter(Mandatory)][string]$ApkPath)
    $buildTools = Join-Path $env:ANDROID_HOME 'build-tools'
    $dexdump = Get-ChildItem -LiteralPath $buildTools -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version](($_.Name -split '-')[0]) } |
        ForEach-Object { Join-Path $_.FullName 'dexdump.exe' } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -Last 1
    if (-not $dexdump) {
        return @('dexdump was not found under ANDROID_HOME\build-tools; the dex was not checked.')
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $folder = Join-Path ([System.IO.Path]::GetTempPath()) "goalmaker-dex-$([guid]::NewGuid().ToString('N'))"
    [System.IO.Directory]::CreateDirectory($folder) | Out-Null
    $receivers = @{}
    $constructors = @{}
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
        try {
            foreach ($entry in @($zip.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' })) {
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $folder $entry.FullName))
            }
        }
        finally { $zip.Dispose() }
        foreach ($dex in Get-ChildItem -LiteralPath $folder -Filter '*.dex') {
            # A receiver's one instance field is its widget (R8 renames the field, not its type's
            # identity), so two receivers with the same field type share one widget class.
            $class = $null
            $inFields = $false
            foreach ($line in & $dexdump $dex.FullName 2>$null) {
                if ($line -match "Class descriptor\s+: 'L([^;]+);'") {
                    $class = $Matches[1].Replace('/', '.')
                    $inFields = $false
                    continue
                }
                if ($line -match '^\s+Instance fields') { $inFields = $true; continue }
                if ($line -match '^\s+(Direct|Virtual) methods') { $inFields = $false; continue }
                if ($line -match "^\s+name\s+: '<init>'" -and $class) { $constructors[$class] = $true; continue }
                if ($inFields -and $class -like '*WidgetReceiver' -and -not $receivers.ContainsKey($class) -and
                    $line -match "^\s+type\s+: '([^']+)'") {
                    $receivers[$class] = $Matches[1]
                }
            }
        }
    }
    finally {
        Remove-Item -LiteralPath $folder -Recurse -Force -ErrorAction SilentlyContinue
    }
    $problems = @()
    if ($receivers.Count -lt 5) {
        $problems += "Found $($receivers.Count) of the 5 widget receivers in the dex."
    }
    # The declared type says nothing: R8 narrows each field to the class it holds.
    $receivers.GetEnumerator() | Where-Object Value -ne 'Landroidx/glance/appwidget/GlanceAppWidget;' |
        Group-Object Value | Where-Object Count -gt 1 | ForEach-Object {
        $problems += "R8 merged the widgets of $(($_.Group | ForEach-Object Key) -join ', ') into one class ($($_.Name))."
    }
    foreach ($action in @('com.goalmaker.app.ui.widget.CompleteTaskAction', 'com.goalmaker.app.ui.widget.CheckInHabitAction')) {
        if (-not $constructors[$action]) { $problems += "$action has no constructor left for Glance to call." }
    }
    return $problems
}

if ($Apk) {
    $apkPath = (Resolve-Path -LiteralPath $Apk).Path
}
else {
    $task = if ($BuildType -eq 'minified') { ':app:assembleMinified' } else { ':app:assembleDebug' }
    & (Join-Path $androidRoot 'gradlew.bat') -p $androidRoot $task --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
    $apkPath = Join-Path $androidRoot "app\build\outputs\apk\$BuildType\app-$BuildType.apk"
}

# A debug build is not shrunk, and its receivers' fields keep the declared type, so only a
# minified build (or a given APK) is checked.
$dexProblems = if ($BuildType -eq 'minified' -or $Apk) { @(Test-WidgetDex -ApkPath $apkPath) } else { @() }
$dexProblems | ForEach-Object { Write-Warning $_ }
$dexReport = if ($dexProblems.Count -gt 0) {
    $dexProblems
}
elseif ($BuildType -eq 'debug' -and -not $Apk) {
    @('Not checked: a debug build is not shrunk by R8.')
}
else {
    @('The widget classes are apart, and the tap callbacks keep their constructors.')
}
[System.IO.File]::WriteAllText((Join-Path $outputPath 'dex-check.txt'), ($dexReport -join "`n"), [System.Text.UTF8Encoding]::new($false))

Invoke-Adb install -r $apkPath | Out-Null
Write-Host "Installed $apkPath on $device." -ForegroundColor Green
Invoke-Adb logcat -c | Out-Null

# Open the app once so it signs in and syncs before the widgets read the replica.
Invoke-Adb shell am start -n "$packageName/com.goalmaker.app.MainActivity" | Out-Null
Start-Sleep -Seconds $SyncSeconds
Save-Screenshot -Name 'app'

foreach ($kind in $Kinds) {
    Invoke-Adb shell input keyevent KEYCODE_HOME | Out-Null
    Invoke-Adb shell am start -n $pinActivity --es kind $kind | Out-Null
    if (-not (Wait-AndTapText -Text 'Add to home screen')) {
        Save-Screenshot -Name "$($kind.ToLowerInvariant())-no-sheet"
        Write-Warning "The launcher did not offer to place $kind; see the screenshot."
        continue
    }
    # The launcher now shows the page it placed the widget on; give the widget a moment to draw.
    Start-Sleep -Seconds 3
    $name = $kind.ToLowerInvariant().Replace('_', '-')
    Save-Screenshot -Name $name
    Save-WidgetText -Name $name
}

Invoke-Adb shell input keyevent KEYCODE_HOME | Out-Null
$log = & $adb -s $device logcat -d -v time GoalMakerWidget:V GlanceAppWidget:V GoalMaker:V AndroidRuntime:E '*:S'
[System.IO.File]::WriteAllText((Join-Path $outputPath 'logcat.txt'), (@($log) -join "`n"), [System.Text.UTF8Encoding]::new($false))

if ($BuildType -eq 'debug' -and -not $Apk) {
    # Only a debuggable build lets run-as read files/crash.log.
    & (Join-Path $PSScriptRoot 'pull-debug-files.ps1') -PackageName $packageName -Destination $outputPath -Serial $device -FilePattern '\.log$'
}

$errors = @($log | Where-Object { $_ -match 'Exception|Error' })
if ($errors.Count -gt 0) { Write-Warning "$($errors.Count) error line(s) in logcat.txt." }

Write-Host "Widget check saved to $outputPath." -ForegroundColor Green
if ($dexProblems.Count -gt 0) { exit 1 }
