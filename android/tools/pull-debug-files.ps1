<#
  Pull selected text exports from a debuggable app. This is a diagnostic aid, not a backup system.
#>
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+$')]
    [string]$PackageName,
    [Parameter(Mandatory)]
    [string]$Destination,
    [string]$Serial,
    [string]$FilePattern = '\.(json|csv)$'
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"

$adb = Get-AdbPath
$device = Get-AuthorizedDeviceSerial -Adb $adb -RequestedSerial $Serial
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
[System.IO.Directory]::CreateDirectory($destinationPath) | Out-Null

$listing = & $adb -s $device exec-out run-as $PackageName sh -c 'ls -1 files' 2>$null
if ($LASTEXITCODE -ne 0) {
    throw 'Could not read app-private files. Confirm this is a debuggable build.'
}

$names = @($listing | ForEach-Object { $_.Trim() } | Where-Object { $_ -match $FilePattern })
foreach ($name in $names) {
    $leaf = [System.IO.Path]::GetFileName($name)
    if ($leaf -ne $name) {
        throw "Unsafe filename returned by device: $name"
    }
    $lines = & $adb -s $device exec-out run-as $PackageName cat "files/$leaf"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to pull files/$leaf"
    }
    $target = Join-Path $destinationPath $leaf
    [System.IO.File]::WriteAllText(
        $target,
        ($lines -join "`n"),
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Host "Pulled $leaf -> $target" -ForegroundColor Green
}

if ($names.Count -eq 0) {
    Write-Host 'No matching debug export files were found.' -ForegroundColor Yellow
}

