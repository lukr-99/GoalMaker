Set-StrictMode -Version Latest

function Get-AdbPath {
    if ($env:ANDROID_HOME) {
        $candidate = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw 'adb was not found. Set ANDROID_HOME or add platform-tools to PATH.'
}

function Get-AuthorizedDeviceSerial {
    param(
        [Parameter(Mandatory)]
        [string]$Adb,
        [string]$RequestedSerial
    )

    $devices = @(Get-AdbDeviceRecord -Adb $Adb)

    if ($RequestedSerial) {
        $match = @($devices | Where-Object { $_.Serial -eq $RequestedSerial })
        if ($match.Count -ne 1 -or $match[0].State -ne 'device') {
            throw "Device '$RequestedSerial' is not connected and authorized."
        }
        return $RequestedSerial
    }

    $authorized = @($devices | Where-Object { $_.State -eq 'device' })
    if ($authorized.Count -eq 0) {
        $states = ($devices | ForEach-Object { "$($_.Serial):$($_.State)" }) -join ', '
        throw "No authorized Android device found. Connected states: $states"
    }
    if ($authorized.Count -gt 1) {
        $serials = ($authorized | ForEach-Object Serial) -join ', '
        throw "Multiple devices found ($serials). Pass -Serial explicitly."
    }
    return $authorized[0].Serial
}

function Get-AdbDeviceRecord {
    param(
        [Parameter(Mandatory)]
        [string]$Adb
    )

    $rows = & $Adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() }
    if ($LASTEXITCODE -ne 0) {
        throw 'ADB failed to list devices.'
    }
    foreach ($row in $rows) {
        $columns = $row -split '\s+'
        if ($columns.Count -ge 2) {
            [pscustomobject]@{ Serial = $columns[0]; State = $columns[1] }
        }
    }
}

function Wait-AuthorizedDeviceSerial {
    param(
        [Parameter(Mandatory)]
        [string]$Adb,
        [string]$RequestedSerial,
        [ValidateRange(1, 600)]
        [int]$TimeoutSeconds = 60
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $devices = @(Get-AdbDeviceRecord -Adb $Adb)
        if ($RequestedSerial) {
            $requested = @($devices | Where-Object { $_.Serial -eq $RequestedSerial })
            if ($requested.Count -eq 1 -and $requested[0].State -eq 'device') {
                return $RequestedSerial
            }
        }
        else {
            $authorized = @($devices | Where-Object { $_.State -eq 'device' })
            if ($authorized.Count -eq 1) { return $authorized[0].Serial }
            if ($authorized.Count -gt 1) {
                $serials = ($authorized | ForEach-Object Serial) -join ', '
                throw "Multiple devices found ($serials). Pass -Serial explicitly."
            }
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    $target = if ($RequestedSerial) { " '$RequestedSerial'" } else { '' }
    throw "Timed out waiting for authorized Android device$target."
}
