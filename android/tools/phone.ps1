<#
  Perform fixed, non-destructive Android device operations through serial-safe ADB calls.

  Examples:
    .\tools\phone.ps1 devices
    .\tools\phone.ps1 wait -Serial emulator-5554 -TimeoutSeconds 60
    .\tools\phone.ps1 screenshot -Output artifacts\phone.png
    .\tools\phone.ps1 launch -PackageName com.goalmaker.app.debug
    .\tools\phone.ps1 logcat -PackageName com.goalmaker.app.debug -Lines 500
#>
param(
    [Parameter(Position = 0, Mandatory)]
    [ValidateSet('devices', 'wait', 'packages', 'launch', 'screenshot', 'logcat', 'tap', 'swipe', 'key')]
    [string]$Command,
    [string]$Serial,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+$')]
    [string]$PackageName,
    [string]$Output,
    [string]$Filter,
    [ValidateRange(0, 10000)]
    [int]$X,
    [ValidateRange(0, 10000)]
    [int]$Y,
    [ValidateRange(0, 10000)]
    [int]$EndX,
    [ValidateRange(0, 10000)]
    [int]$EndY,
    [ValidateRange(50, 10000)]
    [int]$DurationMilliseconds = 300,
    [ValidatePattern('^(?:[0-9]+|KEYCODE_[A-Z0-9_]+)$')]
    [string]$KeyCode,
    [ValidateRange(1, 600)]
    [int]$TimeoutSeconds = 60,
    [ValidateRange(1, 5000)]
    [int]$Lines = 300
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

$adb = Get-AdbPath

if ($Command -eq 'devices') {
    & $adb devices -l
    if ($LASTEXITCODE -ne 0) { throw 'ADB failed to list devices.' }
    exit 0
}

if ($Command -eq 'wait') {
    $device = Wait-AuthorizedDeviceSerial -Adb $adb -RequestedSerial $Serial -TimeoutSeconds $TimeoutSeconds
    Write-Host "Device $device is connected and authorized." -ForegroundColor Green
    exit 0
}

$device = Get-AuthorizedDeviceSerial -Adb $adb -RequestedSerial $Serial

switch ($Command) {
    'packages' {
        $packages = & $adb -s $device shell pm list packages
        if ($LASTEXITCODE -ne 0) { throw 'ADB failed to list packages.' }
        $names = @($packages | ForEach-Object { $_ -replace '^package:', '' })
        if ($Filter) { $names = @($names | Select-String -SimpleMatch $Filter | ForEach-Object Line) }
        $names | Sort-Object
    }
    'launch' {
        if (-not $PackageName) { throw '-PackageName is required for launch.' }
        & $adb -s $device shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Failed to launch $PackageName." }
        Write-Host "Launched $PackageName on $device." -ForegroundColor Green
    }
    'screenshot' {
        if (-not $Output) { throw '-Output is required for screenshot.' }
        $outputPath = [System.IO.Path]::GetFullPath($Output)
        [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputPath)) | Out-Null
        $remote = "/data/local/tmp/codeprint-$([guid]::NewGuid().ToString('N')).png"
        try {
            & $adb -s $device shell screencap -p $remote
            if ($LASTEXITCODE -ne 0) { throw 'ADB failed to capture the screenshot.' }
            & $adb -s $device pull $remote $outputPath | Out-Null
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $outputPath)) {
                throw 'ADB failed to pull the screenshot.'
            }
        }
        finally {
            & $adb -s $device shell rm -f $remote 2>$null | Out-Null
        }
        Write-Host "Saved screenshot from $device to $outputPath." -ForegroundColor Green
    }
    'logcat' {
        $arguments = @('-s', $device, 'logcat', '-d', '-t', [string]$Lines)
        if ($PackageName) {
            $pidValue = (& $adb -s $device shell pidof $PackageName).Trim()
            if ($LASTEXITCODE -ne 0 -or -not $pidValue) {
                throw "$PackageName is not running on $device."
            }
            if ($pidValue -notmatch '^[0-9]+$') { throw 'ADB returned an invalid process ID.' }
            $arguments += @('--pid', $pidValue)
        }
        & $adb @arguments
        if ($LASTEXITCODE -ne 0) { throw 'ADB failed to read logcat.' }
    }
    'tap' {
        if (-not $PSBoundParameters.ContainsKey('X') -or -not $PSBoundParameters.ContainsKey('Y')) {
            throw '-X and -Y are required for tap.'
        }
        & $adb -s $device shell input tap $X $Y
        if ($LASTEXITCODE -ne 0) { throw 'ADB tap failed.' }
    }
    'swipe' {
        foreach ($coordinate in @('X', 'Y', 'EndX', 'EndY')) {
            if (-not $PSBoundParameters.ContainsKey($coordinate)) {
                throw "-$coordinate is required for swipe."
            }
        }
        & $adb -s $device shell input swipe $X $Y $EndX $EndY $DurationMilliseconds
        if ($LASTEXITCODE -ne 0) { throw 'ADB swipe failed.' }
    }
    'key' {
        if (-not $KeyCode) { throw '-KeyCode is required for key.' }
        & $adb -s $device shell input keyevent $KeyCode
        if ($LASTEXITCODE -ne 0) { throw 'ADB key event failed.' }
    }
}
