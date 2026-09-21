<#
.SYNOPSIS
    Gets Docker Desktop starting again after it leaves a socket behind (docs/pitfalls.md).

.DESCRIPTION
    Docker Desktop keeps its named pipes and sockets in %LOCALAPPDATA%\Docker\run. When it is killed
    or crashes, entries such as sailor-ingest.sock and dockerInference can be left in a state Windows
    will not open again: they are listed, their attributes read, and every attempt to delete or rename
    one fails with "The file cannot be accessed by the system". The next start then dies with

        starting services: initializing Ingest server: listening on unix://...sailor-ingest.sock:
        rename ...: The file cannot be accessed by the system.

    and the only way out from user space is to leave the whole folder behind: a directory can be
    renamed even when its children cannot be touched. This script does that and nothing else, so it
    is safe to run whenever the error shows up:

      1. Close Docker Desktop, and end what is left of it.
      2. Delete what can be deleted in the run folder. If anything will not go, rename the folder to
         run.broken-<stamp> so Docker Desktop makes a clean one at the next start.
      3. Start Docker Desktop again and wait for the engine to answer.

    The renamed folders stay until a restart clears the orphans; the script removes the ones that have
    become deletable each time it runs. If the engine still does not come up after this, restart
    Windows: that is the one thing that always clears these entries.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\fix-docker.ps1
#>
[CmdletBinding()]
param(
    # How long to wait for the engine after starting Docker Desktop.
    [int] $TimeoutSeconds = 180,
    # Clear the folder without starting Docker Desktop afterwards.
    [switch] $NoStart
)

$ErrorActionPreference = 'Stop'
$runFolder = Join-Path $env:LOCALAPPDATA 'Docker\run'
$desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'

function Stop-DockerDesktop {
    $running = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -like '*docker*' }
    if (-not $running) {
        Write-Host 'Docker Desktop is not running.'
        return
    }

    foreach ($process in $running) {
        try { $null = $process.CloseMainWindow() } catch { }
    }
    Start-Sleep -Seconds 3
    foreach ($process in (Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -like '*docker*' })) {
        try { $process.Kill() } catch { }
    }
    Start-Sleep -Seconds 2
    Write-Host 'Closed Docker Desktop.'
}

# True when every entry in the folder could be removed.
function Clear-RunFolder([string] $folder) {
    if (-not (Test-Path -LiteralPath $folder)) { return $true }
    $left = @()
    foreach ($entry in [System.IO.Directory]::GetFileSystemEntries($folder)) {
        try { [System.IO.File]::Delete($entry) } catch { $left += (Split-Path $entry -Leaf) }
    }
    if ($left.Count -gt 0) { Write-Host ("Sockets Windows will not let go of: " + ($left -join ', ')) }
    return $left.Count -eq 0
}

Stop-DockerDesktop

# The folders left behind by earlier runs, whose sockets a restart may since have cleared.
foreach ($old in Get-ChildItem (Split-Path $runFolder -Parent) -Directory -Filter 'run.*' -ErrorAction SilentlyContinue) {
    if (Clear-RunFolder $old.FullName) {
        try {
            Remove-Item -LiteralPath $old.FullName -Recurse -Force
            Write-Host ("Cleared " + $old.Name + " from an earlier run.")
        } catch { }
    }
}

if (Clear-RunFolder $runFolder) {
    Write-Host 'The run folder is clean.'
} else {
    $broken = 'run.broken-' + (Get-Date -Format 'yyyyMMddHHmmss')
    Rename-Item -LiteralPath $runFolder -NewName $broken
    Write-Host ("Left the folder behind as " + $broken + "; Docker Desktop will make a clean one.")
}

if ($NoStart) { return }

if (-not (Test-Path -LiteralPath $desktop)) {
    Write-Warning "Docker Desktop is not at $desktop; start it yourself."
    return
}

Start-Process -FilePath $desktop
Write-Host 'Starting Docker Desktop, waiting for the engine...'
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    # Through cmd, so the engine's "not running yet" on stderr stays a quiet exit code.
    cmd /c 'docker ps >NUL 2>&1'
    if ($LASTEXITCODE -eq 0) {
        Write-Host 'The engine is up.'
        return
    }
    Start-Sleep -Seconds 5
}

Write-Warning 'The engine did not come up. Restart Windows: that always clears these sockets.'
exit 1
